package judahzone.jnajack;

import static judahzone.jnajack.JackHelper.portsIOToJack;
import static judahzone.jnajack.JackHelper.portsTypeToJack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jaudiolibs.jnajack.Jack;
import org.jaudiolibs.jnajack.JackClient;
import org.jaudiolibs.jnajack.JackException;
import org.jaudiolibs.jnajack.JackPort;
import org.jaudiolibs.jnajack.JackPortFlags;
import org.jaudiolibs.jnajack.JackPortType;

import judahzone.api.AudioEngine.Connect;
import judahzone.api.AudioEngine.Query;
import judahzone.api.AudioEngine.Request;
import judahzone.api.AudioEngine.Type;
import judahzone.util.RTLogger;
import judahzone.util.Threads;

public class JackRequests extends ArrayList<Object> {

	private final ZoneJackClient zone;
	private final JackClient jackclient;

	public JackRequests(ZoneJackClient zone) {
		this.zone = zone;
		this.jackclient = zone.getJackclient();
	}

	public void process(Object o) {
		try {
    		if (o instanceof Request req) {
    			zone.registerPort(req);
    		}
    		else if (o instanceof Connect con) {
//    			JackPortType type = portsTypeToJack(con.type());
//    			EnumSet<JackPortFlags> flags = portsIOToJack(con.io());
    			connect(((JackPort)con.localPort().port()).getName(), con.regEx(), con);
//
//    			String[] regex =  Jack.getInstance().getPorts(jackclient, con.regEx(), type, flags);
//    			if (regex.length == 0)
//    				RTLogger.warn(this, "Nothing: " + con.regEx() + " -- " + con);
//    			else
//	    			for (String name : regex)
//	    				connect(con.localPort().name(), name, con);
    		} else if (o instanceof Query query) {
    			JackPortType type = portsTypeToJack(query.type());
    			EnumSet<JackPortFlags> flags = portsIOToJack(query.dir());
    			List<String> ports = new ArrayList<>();

    			for (String name : Jack.getInstance().getPorts(jackclient, null, type, flags)) {
    				ports.add(name);
    			}
    			Threads.execute(()-> query.callback().queried(
						query.type() == judahzone.api.AudioEngine.Type.AUDIO ? ports : null,
						query.type() == judahzone.api.AudioEngine.Type.MIDI ? ports : null));

    		} else if (o instanceof JackPort p) { // unregister
    			jackclient.unregisterPort(p);
    		}
		} catch (JackException e) {
			RTLogger.warn(o.toString(), e);
		}
	}

	public void process() {
		if (isEmpty())
			return;
		Object o = getFirst();
		process(o);
		removeFirst();
	}

	private void connect(String source, String destination, Connect con)
			throws JackException {
		RTLogger.debug(this, "connecting " + source + " to " + destination);

		if (con.type() == Type.MIDI)
			Jack.getInstance().connect(jackclient, source, destination);
		else
			Jack.getInstance().connect(jackclient, destination, source);
		if (con.callback() != null)
			Threads.execute(() -> con.callback().connected(con));
	}

}
