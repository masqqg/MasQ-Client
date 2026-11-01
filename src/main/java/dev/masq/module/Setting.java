// PATH: src/main/java/dev/masq/module/Setting.java
package dev.masq.module;

public abstract class Setting<T> {
	private final String name;
	protected T value;

	protected Setting(String name, T def) {
		this.name = name;
		this.value = def;
	}

	private java.util.function.BooleanSupplier vis = () -> true;

	/** Bu ayar sadece koşul doğruyken GUI'de görünür. */
	public Setting<T> showIf(java.util.function.BooleanSupplier c) {
		vis = c;
		return this;
	}

	public boolean visible() { return vis.getAsBoolean(); }
	public String name() { return name; }
	public T get() { return value; }
	public void set(T v) { value = v; }
	public abstract String ser();
	public abstract void de(String s);

	public static final class Bool extends Setting<Boolean> {
		public Bool(String n, boolean d) { super(n, d); }
		public void toggle() { value = !value; }
		public String ser() { return Boolean.toString(value); }
		public void de(String s) { value = Boolean.parseBoolean(s); }
	}

	public static final class Num extends Setting<Float> {
		public final float min, max, step;

		public Num(String n, float d, float min, float max, float step) {
			super(n, d);
			this.min = min;
			this.max = max;
			this.step = step;
		}

		@Override
		public void set(Float v) {
			float c = Math.max(min, Math.min(max, v));
			float s = Math.round((c - min) / step) * step + min;
			value = Math.max(min, Math.min(max, s));
		}

		public float f() { return value; }
		public int i() { return Math.round(value); }
		public float frac() { return (value - min) / (max - min); }
		public void setFrac(float f) { set(min + Math.max(0f, Math.min(1f, f)) * (max - min)); }
		public String ser() { return Float.toString(value); }
		public void de(String s) { set(Float.parseFloat(s)); }
	}

	public static final class Mode extends Setting<String> {
		public final String[] modes;

		public Mode(String n, String d, String... modes) {
			super(n, d);
			this.modes = modes;
		}

		public int idx() {
			for (int i = 0; i < modes.length; i++) if (modes[i].equals(value)) return i;
			return 0;
		}

		public void next() { value = modes[(idx() + 1) % modes.length]; }
		public boolean is(String s) { return value.equals(s); }
		public String ser() { return value; }

		public void de(String s) {
			for (String m : modes) if (m.equals(s)) value = m;
		}
	}
}
