package judahzone.jnajack;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import org.jaudiolibs.jnajack.Jack;
import org.jaudiolibs.jnajack.JackClient;
import org.jaudiolibs.jnajack.JackException;
import org.jaudiolibs.jnajack.JackOptions;
import org.jaudiolibs.jnajack.JackPort;
import org.jaudiolibs.jnajack.JackPortFlags;
import org.jaudiolibs.jnajack.JackPortType;
import org.jaudiolibs.jnajack.JackProcessCallback;
import org.jaudiolibs.jnajack.JackShutdownCallback;
import org.jaudiolibs.jnajack.JackStatus;

import judahzone.api.Ports.Request;
import judahzone.api.Ports.Wrapper;
import judahzone.util.RTLogger;
import judahzone.util.Threads;
import lombok.Getter;

/** Creators of Jack clients must manually {@link #start()} the client.
    Once started the client will connect to Jack and call lifecycle events:
    {@link #initialize()} and {@link #makeConnections()} */
public abstract class ZoneJackClient extends Thread
    implements JackProcessCallback, JackShutdownCallback {

	enum Status { NEW, INITIALISING, ACTIVE, CLOSING, TERMINATED, OVERDUBBED }
    static final EnumSet<JackOptions> OPTIONS = EnumSet.of(JackOptions.JackNoStartServer);
    static final EnumSet<JackStatus> STATUS = EnumSet.noneOf(JackStatus.class);


    protected final String clientName;
    protected final Jack jack;
    protected JackClient jackclient;
    protected final AtomicReference<Status> state =
    	new AtomicReference<>(Status.NEW);
	@Getter protected JackRequests requests;

    public ZoneJackClient(String name) throws Exception {
    	clientName = name;
    	setPriority(Thread.MAX_PRIORITY);
    	setName(name);
    	jack = Jack.getInstance();
    }

    /** Register the port on jackclient and invoke callback asynchronously. */
    protected void registerPort(Request req) throws JackException {
        JackPort port = registerPortAndReturn(req);
        if (req.callback() != null)
        	Threads.execute(() -> req.callback().registered(req,
        		new Wrapper(req.portName(), port)));
    }

    /** Register the port and return the created JackPort. */
    protected JackPort registerPortAndReturn(Request req) throws JackException {
    	JackPortType type = JackHelper.portsTypeToJack(req.type());
    	EnumSet<JackPortFlags> flags = JackHelper.portsIOToJack(req.io());
        return jackclient.registerPort(req.portName(), type, flags);
    }

	/** Jack Client created but not started. Register ports in implementation. */
	protected abstract void initialize() throws Exception;

	/** Jack Client has been started */
	protected abstract void makeConnections() throws Exception;

    /** NOTE: blocks while Jack client is initialized */
	public JackClient getJackclient() {
	    try {
	    	while (jackclient == null)
	    		Thread.sleep(10);
    	} catch (InterruptedException e) {
    		Thread.currentThread().interrupt();
    	}
    	return jackclient;
    }

	@Override
	public void run() {
        if (!state.compareAndSet(Status.NEW, Status.INITIALISING))
            throw new IllegalStateException("" + state.get());
        try {
        	jackclient = jack.openClient(clientName, OPTIONS, STATUS);
        	requests = new JackRequests(this);
            initialize();
            Thread.sleep(1);
	        if (state.compareAndSet(Status.INITIALISING, Status.ACTIVE)) {
                jackclient.setProcessCallback(this);
                jackclient.onShutdown(this);
                jackclient.activate();
                makeConnections();
		        while (state.get() == Status.ACTIVE)
		            Thread.sleep(251);
	        }
        } catch (Exception e) {
        	RTLogger.warn(this, e);
        }
        close();
	}

	public void close() {
		if (Status.TERMINATED == state.get()) return;
		state.set(Status.CLOSING);
		System.out.println("Closing Jack client " + clientName);
		if (jackclient != null) {
	        try {
	            jackclient.close();
	            state.set(Status.TERMINATED);
	            jackclient = null;
	        } catch (Throwable t) {
	        	System.err.println(t.getMessage());
	        }
		}
    }

    @Override
	public final void clientShutdown(JackClient client) {
    	System.out.println("---- " + client.getName() + " / "
    		+ this.getClass().getCanonicalName() + " disposed by Jack. ----");
    	close();
    }
}
