package judahzone.jnajack.fx;

public interface JNATime extends JNAEffect {

	static String[] TYPE = {"1/8", "1/4", "3/8", "1/2"};

	static int indexOf(String type) {
		for (int i = 0; i < TYPE.length; i++)
			if (TYPE[i].equals(type))
				return i;
		return 0; // fail
	}

	void setType(String type);
	String getType();

	void setSync(boolean sync);
	boolean isSync();
	void sync(float unit);

}