package judahzone.jnajack;

import static org.jaudiolibs.jnajack.JackPortFlags.JackPortIsInput;
import static org.jaudiolibs.jnajack.JackPortFlags.JackPortIsOutput;

import java.util.ArrayList;
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

import judahzone.util.RTLogger;
import judahzone.util.Threads;

/**Creators of Jack clients must manually {@link #start()} the client.  Once started the client will
 * connect to Jack and call lifecycle events: {@link #initialize()} and {@link #makeConnections()} */
public abstract class ZoneJackClient extends Thread implements JackProcessCallback, JackShutdownCallback  {

	enum Status { NEW, INITIALISING, ACTIVE, CLOSING, TERMINATED, OVERDUBBED ;}
    static final EnumSet<JackOptions> OPTIONS = EnumSet.of(JackOptions.JackNoStartServer);
    static final EnumSet<JackStatus> STATUS = EnumSet.noneOf(JackStatus.class);
	public static final EnumSet<JackPortFlags> OUTS = EnumSet.of(JackPortIsOutput);
	public static final EnumSet<JackPortFlags> INS = EnumSet.of(JackPortIsInput);

    public static record Connect(PortBack callback, JackPort ours, String regEx,
    		JackPortType type, EnumSet<JackPortFlags> inOut) {};
	public static record Request(PortBack callback, String portName, JackPortType type, JackPortFlags inOut) {}
    public static interface PortBack {
    	void ready(Request req, JackPort port); }

	public class Requests extends ArrayList<Object> {

    	public void process() {
    		if (isEmpty())
    			return;
    		Object o = getFirst();
    		try {
	    		if (o instanceof Request req) {
	    			registerPort(req);
		    		remove(o);
	    		}
	    		else if (o instanceof Connect con) {
	    			for (String name :  Jack.getInstance().getPorts(ZoneJackClient.this.jackclient, con.regEx, con.type, con.inOut)) {
	    				if (name.endsWith("left")) {
	    					if (con.ours.getShortName().endsWith("_R"))
	    						continue;
	    					connect(name, con.ours.getName(), con);
	    				}
	    				else if (name.endsWith("right")) {
	    					if (con.ours.getShortName().endsWith("_L"))
	    						continue;
	    					connect(name, con.ours.getName(), con);
	    				}
	    				else
	    					connect(con.ours.getName(), name, con);
	    			}
	        		remove(o);
	    		}
			} catch (JackException e) { RTLogger.warn(o.toString(), e); }
    	}

    	private void connect(String source, String destination, Connect con) throws JackException {
    		RTLogger.debug(this, "connecting " + source + " to " + destination);
			Jack.getInstance().connect(ZoneJackClient.this.jackclient, source, destination);
			Threads.execute(()->con.callback.ready(null, con.ours));
			removeFirst();

    	}
    }

    protected final String clientName;
    protected final Jack jack;
    protected JackClient jackclient;
    protected final AtomicReference<Status> state = new AtomicReference<>(Status.NEW);
	protected final Requests requests = new Requests();

    public ZoneJackClient(String name) throws Exception {
    	clientName = name;
    	setPriority(Thread.MAX_PRIORITY);
    	setName(name);
    	jack = Jack.getInstance();
    }

    /**
     * Default implementation registers the port on the jackclient and schedules the
     * req.callback().ready(req, port) to run asynchronously. Subclasses that need
     * the JackPort synchronously (to add it to local lists) can call
     * {@link #registerPortAndReturn(Request)} instead.
     */
    protected void registerPort(Request req) throws JackException {
        JackPort port = registerPortAndReturn(req);
        Threads.execute(() -> req.callback().ready(req, port));
    }

    /**
     * Register the port and return the created JackPort. This does not call the
     * callback; caller should invoke the callback (synchronously or asynchronously)
     * after doing any subclass-specific bookkeeping (for example, adding to
     * inPorts/outPorts lists).
     */
    protected JackPort registerPortAndReturn(Request req) throws JackException {
        return jackclient.registerPort(req.portName(), req.type(), req.inOut());
    }

	/** Jack Client created but not started. Register ports in implementation. */
	protected abstract void initialize() throws Exception;
	/** Jack Client has been started */
	protected abstract void makeConnections() throws JackException;

    /** NOTE: blocks while Midi jack client is initialized */
	public JackClient getJackclient() {
	    try {
	    	while (jackclient == null) // wait for initialization
	    		Thread.sleep(10);
    	} catch(Throwable t) {System.err.println(t.getMessage());}
    	return jackclient;
    }

	public Requests getRequests() { return requests; }

	/** Create a Thread to run our client. All clients require a Thread to run in. */
	@Override public void run() {
        if (!state.compareAndSet(Status.NEW, Status.INITIALISING)) {
            throw new IllegalStateException("" + state.get());
        }
        try {
        	jackclient = jack.openClient(clientName, OPTIONS, STATUS);
            initialize();
	        if (state.compareAndSet(Status.INITIALISING, Status.ACTIVE)) {
	                jackclient.setProcessCallback(this);
	                jackclient.onShutdown(this);
	                jackclient.activate();
	                makeConnections();
		            while (state.get() == Status.ACTIVE) {
		                Thread.sleep(251); // @TODO switch to wait()
		            }
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
		if (jackclient != null)
	        try {
	            jackclient.close();
	            state.set(Status.TERMINATED);
	            jackclient = null;
	        } catch (Throwable t) {System.err.println(t.getMessage());}
    }

    @Override
	public final void clientShutdown(JackClient client) {
    	System.out.println("---- " + client.getName() + " / " + this.getClass().getCanonicalName() + " disposed by Jack. ----");
    	close();
    }

}
