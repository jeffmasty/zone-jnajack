package judahzone.jnajack;

import static judahzone.util.Constants.LEFT;
import static judahzone.util.Constants.RIGHT;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import judahzone.api.PlayAudio;
import judahzone.util.AudioTools;
import judahzone.util.Constants;
import judahzone.util.Recording;
import lombok.Getter;
import lombok.Setter;

@Getter
public class BasicPlayer implements PlayAudio {

	protected final AtomicInteger tapeCounter = new AtomicInteger(0);
	protected boolean playing;
	protected Recording recording = new Recording();
	protected File file;
	@Setter protected float env = 0.125f;
	@Setter protected Type type = Type.ONE_SHOT;

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
	}

	@Override public final void setRecording(Recording sample) { // TODO deleted MainFrame.update(this);
		rewind();
		recording = sample;
	}

	public void process(float[] outLeft, float[] outRight) { // duplicate in Sample w/ gain + MainFrame.update
		if (!playing) return;

		int frame = tapeCounter.getAndIncrement();
		if (frame + 1 >= recording.size()) {
			tapeCounter.set(0);
			if (type == Type.ONE_SHOT) {
				playing = false;
			}
		}
		if (!playing)
			return;

		float[][] buf = recording.get(frame);

		AudioTools.mix(buf[LEFT], env, outLeft);
		AudioTools.mix(buf[RIGHT], env, outRight);
	}


}
