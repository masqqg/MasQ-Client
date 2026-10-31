// PATH: src/main/java/dev/masq/anim/Easing.java
package dev.masq.anim;

public final class Easing {
	private Easing() {}

	public static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }
	public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

	public static float outCubic(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float outBack(float t) {
		float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}
}
