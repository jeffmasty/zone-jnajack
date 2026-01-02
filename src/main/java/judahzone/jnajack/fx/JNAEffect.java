package judahzone.jnajack.fx;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import judahzone.util.Constants;

/** JNA uses FloatBuffers rather than float[] */
public interface JNAEffect {

	int SAMPLE_RATE = Constants.sampleRate();
	int N_FRAMES = Constants.bufSize();

    String getName();

    default void reset() {}

    default void activate() {}

    int getParamCount();

    /**@param idx parameter setting to change
     * @param new value scaled from 0 to 100 */
    void set(int idx, int value);

    /**@return value of setting idx scaled from 0 to 100 */
    int get(int idx);

    /** do the work
     * @param right null for mono effect */
    void process(FloatBuffer left, FloatBuffer right);

    public interface RTEffect extends JNAEffect { }

    static final ConcurrentMap<Class<?>, java.util.List<String>> SETTINGS_CACHE =
            new ConcurrentHashMap<>();

    default List<String> getSettingNames() {
        return SETTINGS_CACHE.computeIfAbsent(this.getClass(), cls -> {
            for (Class<?> c : cls.getDeclaredClasses()) {
                if (c.isEnum() && "Settings".equals(c.getSimpleName())) {
                    Object[] consts = c.getEnumConstants();
                    List<String> names = new ArrayList<>(consts.length);
                    for (Object o : consts) names.add(o.toString());
                    return Collections.unmodifiableList(names);
                }
            }
            List<String> fallback = new ArrayList<>(getParamCount());
            for (int i = 0; i < getParamCount(); i++) fallback.add("param" + i);
            return Collections.unmodifiableList(fallback);
        });
    }


}
