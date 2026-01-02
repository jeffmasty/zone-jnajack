package judahzone.jnajack.fx;

import static judahzone.util.WavConstants.FFT_SIZE;

import java.nio.FloatBuffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import be.tarsos.dsp.util.fft.FFT;
import judahzone.api.IRProvider;
import judahzone.util.RTLogger;
import lombok.Getter;

/**
 * Jack-optimized Convolution (assumes FloatBuffer instances are NOT array-backed
 * and uses direct FloatBuffer get/put APIs for IO).
 */
public abstract class JackConvolution implements JNAEffect {

    public enum Settings { Cabinet, Wet}
    private static List<String> names = settingNames();
    private static List<String> settingNames() {
        ArrayList<String> build = new ArrayList<String>();
        for (Settings s : Settings.values())
            build.add(s.name());
        return Collections.unmodifiableList(build);
    }

    protected static IRProvider db;
    public static void setIRDB(IRProvider provider) { db = provider; }

    @Getter protected final String name = JackConvolution.class.getSimpleName();
    @Getter protected final int paramCount = Settings.values().length;
    @Override public List<String> getSettingNames() { return names; }

    // ======================================================================
    /** Wrapper around 2 Mono Convolvers */
    public static class Stereo extends JackConvolution implements RTEffect {

        private final Mono leftIR = new Mono();
        private final Mono rightIR = new Mono();

        @Override public void set(int idx, int value) {
            leftIR.set(idx, value);
            rightIR.set(idx, value);
        }

        @Override public void process(FloatBuffer l, FloatBuffer r) {
            leftIR.process(l);
            rightIR.process(r);
        }

        @Override public int get(int idx) {
            return leftIR.get(idx);
        }

    }

    // ======================================================================
    /** MONO: Convolute a selected IR against live audio */
    public static class Mono extends JackConvolution {

        protected final FFT fft = new FFT(FFT_SIZE);
        protected final FFT ifft = new FFT(FFT_SIZE);

        protected final int overlapSize = FFT_SIZE - N_FRAMES;

        // pointer to currently selected IR spectrum (from DB)
        protected float[] irFreq = new float[FFT_SIZE * 2];
        protected float wet = 0.9f;
        protected int cabinet = -1; // lazy load

        // instance working buffers (allocated once)
        protected final float[] fftInOut = new float[FFT_SIZE * 2];
        protected final float[] overlap = new float[overlapSize];
        protected final float[] work0 = new float[N_FRAMES];
        protected final float[] work1 = new float[N_FRAMES];

        @Override public void reset() {
            Arrays.fill(overlap, 0f);
        }

        @Override public void set(int idx, int value) {
            if (idx == Settings.Cabinet.ordinal()) {
                if (db == null) {
                    RTLogger.warn(this, "No IRDB set");
                    return;
                }
                if (db.size() == 0) {
                    RTLogger.warn(this, "No cabinets loaded");
                    return;
                }
                if (value < 0 || value >= db.size()) {
                    throw new InvalidParameterException("Cabinet index out of range: " + value);
                }
                cabinet = value;
                irFreq = db.get(cabinet).irFreq();
                reset();
                return;
            }

            if (idx == Settings.Wet.ordinal()) {
                // map 0..100 -> 0.0..1.0
                wet = value * 0.01f;
                if (wet > 1) wet = 1;
                if (wet < 0) wet = 0;
                return;
            }
            throw new InvalidParameterException("Unknown param index: " + idx);
        }

        @Override
        public void activate() {
            if (cabinet < 0)  // first time (DB allowed to load)
                if (db != null) // if null user gets zeros
                    set(Settings.Cabinet.ordinal(), 0);
        }

        @Override public int get(int idx) {
            if (idx == Settings.Cabinet.ordinal()) {
                return cabinet;
            }
            if (idx == Settings.Wet.ordinal()) {
                return Math.round(wet * 100f);
            }
            throw new InvalidParameterException("Unknown param index: " + idx);
        }


        /** Convolve Add and make stereo, even if dry/inactive
         *  This implementation reads input using direct FloatBuffer.get(index)
         *  and writes output using direct FloatBuffer.put(index), avoiding any
         *  assumption that the FloatBuffer is backed by an accessible array.
         */
        public void monoToStereo(FloatBuffer mono, FloatBuffer stereo) {
            // If fully dry just copy samples from mono->stereo using direct buffer ops
            if (wet <= 0f) {
                FloatBuffer inDup = mono.duplicate();
                FloatBuffer outDup = stereo.duplicate();
                inDup.rewind();
                outDup.rewind();
                for (int i = 0; i < N_FRAMES; i++) {
                    float v = inDup.get(i);
                    outDup.put(i, v);
                }
                return;
            }
            float dryGain = 1.0f - wet;
            float wetGain = wet;

            // Read input block without disturbing caller's buffer position
            FloatBuffer inBuf = mono.duplicate();
            inBuf.rewind();
            for (int i = 0; i < N_FRAMES; i++) {
                work0[i] = inBuf.get(i);
            }

            // Prepare FFT input (real time samples in indices 0..FFT_SIZE-1)
            Arrays.fill(fftInOut, 0f);

            // Compose the overlap-save input: [previous overlap | new block]
            System.arraycopy(overlap, 0, fftInOut, 0, overlapSize);
            System.arraycopy(work0, 0, fftInOut, overlapSize, N_FRAMES);

            // Save next overlap for the following block: last (FFT_SIZE - N_FRAMES) samples of this composite input
            System.arraycopy(fftInOut, N_FRAMES, overlap, 0, overlapSize);

            // Forward FFT (in-place, produces complex interleaved in fftInOut)
            fft.forwardTransform(fftInOut);

            // Complex multiply with IR spectrum (irFreq is complex interleaved)
            for (int k = 0, idx = 0; k < FFT_SIZE; k++, idx += 2) {
                float a = fftInOut[idx];
                float b = fftInOut[idx + 1];
                float c = irFreq[idx];
                float d = irFreq[idx + 1];
                float real = a * c - b * d;
                float imag = a * d + b * c;
                fftInOut[idx] = real;
                fftInOut[idx + 1] = imag;
            }

            // Inverse FFT -> time domain (real samples in indices 0..FFT_SIZE-1)
            ifft.backwardsTransform(fftInOut);

            // Extract valid linear-convolution output: indices overlapSize .. overlapSize + N_FRAMES - 1
            for (int i = 0; i < N_FRAMES; i++) {
                float proc = fftInOut[overlapSize + i]; // processed (wet) sample
                float in = work0[i];                  // original (dry) sample
                float mixed = dryGain * in + wetGain * proc;
                work1[i] = mixed;
            }

            // Write mixed result back into buffers using direct put(index)
            FloatBuffer outBuf = mono.duplicate();
            FloatBuffer stereoOut = stereo.duplicate();
            outBuf.rewind();
            stereoOut.rewind();
            for (int i = 0; i < N_FRAMES; i++) {
                outBuf.put(i, work1[i]);
                stereoOut.put(i, work1[i]);
            }
        }

        /** Realtime Audio  Convolve Add */
        public void process(FloatBuffer mono) {
            final float dryGain = 1.0f - wet;
            final float wetGain = wet;

            // Read input block without disturbing caller's buffer position
            FloatBuffer inBuf = mono.duplicate();
            inBuf.rewind();
            for (int i = 0; i < N_FRAMES; i++) {
                work0[i] = inBuf.get(i);
            }

            // Prepare FFT input (real time samples in indices 0..FFT_SIZE-1)
            Arrays.fill(fftInOut, 0f);

            // Compose the overlap-save input: [previous overlap | new block]
            System.arraycopy(overlap, 0, fftInOut, 0, overlapSize);
            System.arraycopy(work0, 0, fftInOut, overlapSize, N_FRAMES);

            // Save next overlap for the following block: last (FFT_SIZE - N_FRAMES) samples of this composite input
            System.arraycopy(fftInOut, N_FRAMES, overlap, 0, overlapSize);

            // Forward FFT (in-place, produces complex interleaved in fftInOut)
            fft.forwardTransform(fftInOut);

            // Complex multiply with IR spectrum (irFreq is complex interleaved)
            for (int k = 0, idx = 0; k < FFT_SIZE; k++, idx += 2) {
                float a = fftInOut[idx];
                float b = fftInOut[idx + 1];
                float c = irFreq[idx];
                float d = irFreq[idx + 1];
                float real = a * c - b * d;
                float imag = a * d + b * c;
                fftInOut[idx] = real;
                fftInOut[idx + 1] = imag;
            }

            // Inverse FFT -> time domain (real samples in indices 0..FFT_SIZE-1)
            ifft.backwardsTransform(fftInOut);

            // Mix wet/dry into work1 buffer
            for (int i = 0; i < N_FRAMES; i++) {
                float proc = fftInOut[overlapSize + i]; // processed (wet) sample
                float in = work0[i];                    // original (dry) sample
                work1[i] = dryGain * in + wetGain * proc;
            }

            // Write mixed result back into mono buffer using direct put(index)
            FloatBuffer outBuf = mono.duplicate();
            outBuf.rewind();
            for (int i = 0; i < N_FRAMES; i++) {
                outBuf.put(i, work1[i]);
            }
        }
    }

    @Override public void process(FloatBuffer left, FloatBuffer right) {
        // no-op
    }

}