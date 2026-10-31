// PATH: src/main/java/dev/masq/anim/Spring.java
package dev.masq.anim;

/** Kendi saatini kullanan yay animasyonu. Sabit alt adımlı (1/240 s) kararlı entegrasyon. */
public final class Spring {
	private static final float STEP = 1f / 240f;

	private final float k, c;
	private float value, prev, vel, target;
	private long last;

	public Spring(float stiffness, float damping) {
		this.k = stiffness;
		this.c = damping;
	}

	public static Spring snappy() { return new Spring(320f, 30f); }
	public static Spring soft() { return new Spring(150f, 17f); }
	public static Spring bouncy() { return new Spring(260f, 15f); }

	public Spring set(float t) { target = t; return this; }

	public Spring snap(float v) {
		value = prev = target = v;
		vel = 0f;
		return this;
	}

	public boolean settled() {
		return Math.abs(vel) < 0.05f && Math.abs(target - value) < 0.003f;
	}

	/** Kare başına bir kez çağır. */
	public float tick() {
		long now = System.nanoTime();
		float dt = last == 0 ? 0f : Math.min((now - last) / 1e9f, 0.05f);
		last = now;
		prev = value;
		if (settled()) {
			value = target;
			vel = 0f;
			return value;
		}
		if (dt <= 0f) return value;
		int n = (int) Math.ceil(dt / STEP);
		float h = dt / n;
		for (int i = 0; i < n; i++) {
			vel += (k * (target - value) - c * vel) * h;
			value += vel * h;
		}
		return value;
	}

	public float value() { return value; }
	public float prev() { return prev; }
	public float target() { return target; }
}
