package judahzone.jnajack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jaudiolibs.jnajack.Jack;
import org.jaudiolibs.jnajack.JackClient;
import org.jaudiolibs.jnajack.JackPort;
import org.jaudiolibs.jnajack.JackPortFlags;
import org.jaudiolibs.jnajack.JackPortType;

import judahzone.api.AudioEngine;
import judahzone.api.AudioEngine.Connect;
import judahzone.api.AudioEngine.IO;
import judahzone.api.AudioEngine.PortData;
import judahzone.api.AudioEngine.Query;
import judahzone.api.AudioEngine.Request;
import judahzone.api.AudioEngine.Type;
import judahzone.api.AudioEngine.Wrapper;
import judahzone.util.RTLogger;
import judahzone.util.Threads;

/** Delegates to audio and MIDI ZoneJackClients; queries both clients
    asynchronously and aggregates results via Consumer.accept(...). */
public class JackHelper implements AudioEngine.Provider, AudioEngine.PortData {

    public static final JackPortFlags OUT = JackPortFlags.JackPortIsOutput;
    public static final JackPortFlags IN = JackPortFlags.JackPortIsInput;
	public static final EnumSet<JackPortFlags> OUTS = EnumSet.of(OUT);
	public static final EnumSet<JackPortFlags> INS = EnumSet.of(IN);

	private final ZoneJackClient midiClient;
	private final ZoneJackClient audioClient;

	List<String> audio = Collections.synchronizedList(new ArrayList<>());
	List<String> midi = Collections.synchronizedList(new ArrayList<>());
	CountDownLatch latch;

	public JackHelper(ZoneJackClient midiClient, ZoneJackClient audioClient) {
		this.midiClient = midiClient;
		this.audioClient = audioClient;
	}

	/** Query both clients and aggregate audio + MIDI results. */
	@Override
	public void query(PortData consumer) {
		latch = new CountDownLatch(2);
		Threads.execute(() -> {

			audioClient.getRequests().add(new Query(AudioEngine.Type.AUDIO, AudioEngine.IO.OUT, this));
			midiClient.getRequests().add(new Query(AudioEngine.Type.MIDI, AudioEngine.IO.IN, this));

			try {
				boolean ok = latch.await(1000, TimeUnit.MILLISECONDS);
				if (!ok)
					System.err.println("JackHelper: timeout waiting for port queries");
				consumer.queried(List.copyOf(audio), List.copyOf(midi));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				consumer.queried(List.copyOf(audio), List.copyOf(midi));
			}
		});
	}

	/** Route register request to appropriate client. */
	@Override
	public void register(Request req) {
		ZoneJackClient target =
			req.type() == AudioEngine.Type.MIDI ? midiClient : audioClient;
		target.getRequests().add(req);
	}

	/** Route unregister request to appropriate client. */
	@Override
	public void unregister(Request req, Wrapper port) {
		JackPort p = (JackPort)port.port();


		ZoneJackClient target =
			req.type() == AudioEngine.Type.MIDI ? midiClient : audioClient;
		target.getRequests().add(p);
	}

	/** Route connect request to appropriate client. */
	@Override
	public void connect(Connect con) {
		ZoneJackClient target =
			con.type() == AudioEngine.Type.MIDI ? midiClient : audioClient;
		target.getRequests().add(con);
	}

	/** Convert Ports.Type to JackPortType. */
	static JackPortType portsTypeToJack(AudioEngine.Type type) {
		return type == AudioEngine.Type.AUDIO ? JackPortType.AUDIO : JackPortType.MIDI;
	}

	/** Convert Ports.IO to JackPortFlags. */
	static EnumSet<JackPortFlags> portsIOToJack(AudioEngine.IO dir) {
		return dir == AudioEngine.IO.IN ? INS : OUTS;
	}

	@Override
	public void queried(List<String> audioPorts, List<String> midiPorts) {
		if (audioPorts != null) {
			audio.addAll(audioPorts);
		}
		if (midiPorts != null) {
			midi.addAll(midiPorts);
		}
		latch.countDown();
	}

	// untested
	public void flush() {
		JackRequests audio = audioClient.getRequests();
		for (Object o : audio)
			audio.process(o);
		audio.clear();

		JackRequests midi = midiClient.getRequests();
		for	(Object o : midi)
			midi.process(o);
		midi.clear();
	}

	// SYNC
	@Override
	public Wrapper registerNow(Type type, IO io, String portName) throws Exception {

    	JackPortType t = portsTypeToJack(type);
    	EnumSet<JackPortFlags> flags = portsIOToJack(io);

		if (type == Type.AUDIO) {
	    	JackPort result = audioClient.getJackclient().registerPort(portName, t, flags);
	    	return new Wrapper(result.getShortName(), result);
		}
		// Type.MIDI
		JackPort result = midiClient.getJackclient().registerPort(portName, t, flags);
		return new Wrapper(result.getShortName(), result);

	}

	@Override
	public void connectNow(Object port, Type type, String portName) throws Exception {
		JackPort jack = (JackPort)port;
    	JackClient jackclient = type == Type.AUDIO ? audioClient.getJackclient() : midiClient.getJackclient();
    	if (type == Type.MIDI) {
    			RTLogger.debug(this, "connect midi ... " + jack.getName() + " to " + portName);
    			Jack.getInstance().connect(jackclient, jack.getName(), portName);
    	}
		else {
	    	RTLogger.debug(this, "connect audio ... " + portName + " to " + jack.getName());
			Jack.getInstance().connect(jackclient, portName, jack.getName());
		}
	}


}
