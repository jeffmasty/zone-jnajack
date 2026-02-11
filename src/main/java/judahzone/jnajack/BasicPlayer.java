package judahzone.jnajack;

import static judahzone.util.Constants.LEFT;
import static judahzone.util.Constants.RIGHT;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import judahzone.api.PlayAudio;
import judahzone.api.Played;
import judahzone.data.Asset;
import judahzone.data.Recording;
import judahzone.util.AudioTools;
import judahzone.util.Constants;
import lombok.Getter;
import lombok.Setter;

@Getter
public class BasicPlayer implements PlayAudio {

	private static final int N_FRAMES = Constants.bufSize();

	protected final AtomicInteger tapeCounter = new AtomicInteger(0);
	@Getter protected volatile boolean playing;              // make visibility safe across threads
	protected Recording recording = new Recording();
	protected Asset asset;
	protected File file;
	@Setter protected float env = 0.5f;
	@Setter protected Type type = Type.ONE_SHOT;
	@Setter protected Played played;

	@Override public void play(boolean onOrOff) {
	    this.playing = onOrOff;
	}

	public final void clear() {
		playing = false;
        setRecording(null);
        file = null;
    }

	@Override public final String toString() {
		if (file == null)
			return "---";
		return file.getName().replace(".wav", "");
	}

	@Override public final int getLength() {
		return recording.size();
	}

	@Override public final float seconds() {
		return getLength() / Constants.fps();
	}

	@Override public final void rewind() {
		tapeCounter.set(0);
		if (played != null)
			played.setHead(0);
	}

	@Override public final void setRecording(Asset asset) { // TODO deleted MainFrame.update(this);
		rewind();
		recording = asset.recording();
		this.asset = asset;
	}

	@Override public final void setSample(long sample) {
	    // sample is absolute sample index; convert to frame index safely
	    if (recording == null || recording.size() == 0) {
	        tapeCounter.set(0);
	        if (played != null) played.setHead(0);
	        return;
	    }

	    long frameIdx = sample / N_FRAMES;
	    int frames = recording.size();
	    if (frameIdx < 0) frameIdx = 0;
	    if (frameIdx >= frames) frameIdx = frames - 1;

	    tapeCounter.set((int) frameIdx);

	    // Notify UI/player immediately of the new head (in sample units)
	    if (played != null) {
	        long sampleFrame = ((long) tapeCounter.get()) * N_FRAMES;
	        played.setHead(sampleFrame);
	    }
	}

	public void process(float[] outLeft, float[] outRight) {
	    if (!playing) return;

	    Recording localRec = recording;
	    if (localRec == null) return;
	    int frames = localRec.size();
	    if (frames == 0) return;

	    int frame = tapeCounter.getAndIncrement();

	    // If we've run past the end, reset and respect ONE_SHOT/LOOP semantics.
	    if (frame >= frames) {
	    	rewind();
	        if (type == Type.ONE_SHOT) {
	            playing = false;
	            if (played != null) {
	            	played.playState();
	            }
	            return;
	        }
	        frame = 0;
	    }

	    // Mix the requested frame
	    float[][] buf = localRec.get(frame);
	    if (buf != null) {
	        AudioTools.mix(buf[LEFT], env, outLeft);
	        AudioTools.mix(buf[RIGHT], env, outRight);
	    }

	    // After playing the frame, if it was the last frame prepare wrapping/stopping
	    if (frame + 1 >= frames) {
	        tapeCounter.set(0);
	        if (type == Type.ONE_SHOT) {
	            playing = false;
	            if (played != null)
	            	played.playState();
	        }
	    }

	    // Update head display using the current tapeCounter (in samples)
	    if (played != null)
	        played.setHead(((long) frame) * N_FRAMES);

	}

}
