# zone-jnajack
Jack integration for the JudahZone project


## ZoneJackClient overview

- `ZoneJackClient` is an abstract thread that opens and manages a JNAJack `JackClient`, provides lifecycle hooks (`initialize()`, `makeConnections()`), and handles port registration/connection via the nested `Requests` helper.
  - Opens a `JackClient` with options and registers a process callback.
  - Exposes `registerPort(Request)` and `registerPortAndReturn(Request)` for subclasses.
  - Provides a safe `close()` and implements `JackShutdownCallback`.


## Jack FX
 The fx package (src/main/java/judahzone/jnajack/fx) contains DSPs that run inside the JNAJack process callback. These DSPs are lightweight, realtime-aware, and designed to be called from the JACK audio thread. They do minimal allocations and expose parameter setters so the GUI or host can update values asynchronously. Main classes:

	•  ChannelStrip — combines per-channel FX (gain, EQ, filters, pan, sends) and manages processing order.
	•  JackGain — simple gain stage.
	•  JackEQ — multi-band EQ wrapper that configures cascaded filters.
	•  JackFilter, JackMonoFilter, JackBiquad — filter primitives and biquad helper implementations.
	•  JackDelay — delay/echo line with feedback and wet/dry controls.
	•  JackChorus — chorus modulation effect.
	•  JackOverdrive — distortion/drive algorithms (several styles).
	•  JackCompressor — dynamics compression stage.
	•  JackReverb, JackFreeverb — reverb implementations (Freeverb + wrapper).
	•  JackConvolution — simple impulse-response based cab/IR convolution.
	•  JNATime — helper for converting between jack frames and time values.
	•  JNAEffect — base/utility class for FloatBuffer effect units and parameter handling.


## Build and checkout notes

	•  The project is multi-module and uses meta-zone as the parent aggregator. Recommended workflow:
	•  Clone the parent aggregator (contains zone-jnajack as a module):
	
	git clone https://github.com/jeffmasty/meta-zone.git
	cd meta-zone
	
	•  Build everything (recommended):
	
	mvn clean package
	
	•  	Build only zone-jnajack (from the parent directory):
	
	mvn -pl zone-jnajack -am clean package
	
## Dependencies

	•  You do not need to manually checkout jnajack unless you want to modify or rebuild it. Maven will download jnajack and jna from configured repositories as defined in the parent pom.
	•  If you want to build jnajack yourself, clone the upstream repo (org.jaudiolibs/jnajack) and build it separately, then install to your local repo. This is optional.

Runtime prerequisites (Linux)

	•  JACK server (jackd/jackd2) must be running. Install via your distribution (for example sudo apt install jackd2 qjackctl).
	•  For full JudahZone features you may also need a2jmidid, fluidsynth and relevant soundfonts.
	•  Native libraries used by jnajack / jna must be present; usually installing JACK and standard ALSA packages is enough.

Reference

	•  See the main application README in the project root for how to start the app and additional runtime notes.	
	