package judahzone.jnajack.fx;

import java.nio.FloatBuffer;
import java.security.InvalidParameterException;

import judahzone.jnajack.fx.JNAEffect.RTEffect;
import judahzone.util.Constants;
import lombok.Getter;
import lombok.Setter;

@Getter public class JackGain implements RTEffect {

public enum Settings {VOLUME, PAN};

public static final int VOLUME = 0;
public static final int PAN = 1;

private final String name = "JackGain";
private final int paramCount = 2;
private float gain = 0.5f;
private float stereo = 0.5f;
@Setter private float preamp = 1f;

/** pan/balance */
public boolean isActive() {
    return stereo < 0.49f || stereo > 0.51f;
}

/** pan/balance */
public void setActive(boolean active) {
    if (!active) stereo = 0.5f;
}

@Override public int get(int idx) {
    if (idx == VOLUME)
        return (int) (gain * 100);
    if (idx == PAN)
        return (int) (stereo * 100);
    throw new InvalidParameterException("idx " + idx);
}

@Override public void set(int idx, int value) {
    if (idx == VOLUME)
        setGain(value * 0.01f);
    else if (idx == PAN)
        setPan(value * 0.01f);
    else throw new InvalidParameterException("idx " + idx);
}

public void setGain(float g) {
    gain = g < 0 ? 0 : g > 1 ? 1 : g;
}
public void setPan(float p) {
    stereo = p < 0 ? 0 : p > 1 ? 1 : p;
}

public float getLeft() {
    if (stereo < 0.5f) // towards left, half log increase
        return (1 + (0.5f - stereo) * 0.2f) * preamp;
    return 2 * (1 - stereo) * preamp;
}

public float getRight() {
    if (stereo > 0.5)
        return (1 + (stereo - 0.5f) * 0.2f) * preamp;
    return 2 * stereo * preamp;
}

/**
 * FloatBuffer-only legacy path. Mirrors original semantics: uses buffer limit() to
 * determine frames and performs linear ramp smoothing in-place.
 */
@Override
public void process(FloatBuffer left, FloatBuffer right) {
    left.rewind();
    if (right == null) {
        // Mono: apply combined ramp for preamp * gain
        float targetPre = getLeft(); // in mono, you can use getLeft() or compute a mono pan-law
        float targetPost = gain;
        int n = left.limit();
        if (n == 0) return;
        float stepPre = (targetPre - preCurrentL) / n;
        float stepPost = (targetPost - postCurrent) / n;
        float curPre = preCurrentL;
        float curPost = postCurrent;
        for (int i = 0; i < n; i++) {
            float m = curPre * curPost;
            left.put(i, left.get(i) * m);
            curPre += stepPre;
            curPost += stepPost;
        }
        preCurrentL = targetPre;
        preCurrentR = targetPre;
        postCurrent = targetPost;
        return;
    }

    right.rewind();

    // stereo
    float targetPreL = getLeft();   // uses pan law * preamp
    float targetPreR = getRight();
    float targetPost = gain;

    int n = Math.min(left.limit(), right.limit());
    if (n == 0) return;
    float stepPreL = (targetPreL - preCurrentL) / n;
    float stepPreR = (targetPreR - preCurrentR) / n;
    float stepPost = (targetPost - postCurrent) / n;

    float curPreL = preCurrentL;
    float curPreR = preCurrentR;
    float curPost = postCurrent;

    for (int i = 0; i < n; i++) {
        float mL = curPreL * curPost;
        float mR = curPreR * curPost;
        left.put(i, left.get(i) * mL);
        right.put(i, right.get(i) * mR);
        curPreL += stepPreL;
        curPreR += stepPreR;
        curPost += stepPost;
    }

    preCurrentL = targetPreL;
    preCurrentR = targetPreR;
    postCurrent = targetPost;
}

public void processMono(FloatBuffer mono) {
    mono.rewind();
    float precompute = preamp * gain;
    int n = mono.limit();
    for (int z = 0; z < n; z++)
        mono.put(z, mono.get(z) * precompute);
}

/** Last effective left/right gains used in preamp() (preamp * pan). */
private float preCurrentL = 1f;
private float preCurrentR = 1f;

/** Last effective post-fader gain used in post(). */
private float postCurrent = 1f;

/** preamp and panning, with smoothing, stereo only */
public void preamp(FloatBuffer left, FloatBuffer right) {
    float targetL = getLeft();
    float targetR = getRight();

    ramp(left,  Constants.bufSize(), preCurrentL, targetL);
    ramp(right, Constants.bufSize(), preCurrentR, targetR);

    preCurrentL = targetL;
    preCurrentR = targetR;
}

/** gain only, with smoothing, stereo only */
public void post(FloatBuffer left, FloatBuffer right) {
    float target = gain;

    ramp(left,  Constants.bufSize(), postCurrent, target);
    ramp(right, Constants.bufSize(), postCurrent, target);

    postCurrent = target;
}

// apply a linear ramp from start→end over N_FRAMES samples
private static void ramp(FloatBuffer buf, int frames, float startGain, float endGain) {
    if (frames <= 0 || buf == null) {
        return;
    }
    buf.rewind();
    float step = (endGain - startGain) / frames;
    float g = startGain;

    // FloatBuffer-only legacy path: do NOT check hasArray(), always use get/put
    for (int i = 0; i < frames && i < buf.limit(); i++) {
        float s = buf.get(i);
        buf.put(i, s * g);
        g += step;
    }
}

@Override
public void reset() {
    gain = 0.5f;
    stereo = 0.5f;
    preamp = 1f;
    preCurrentL = 1f;
    preCurrentR = 1f;
    postCurrent = 1f;
}

}