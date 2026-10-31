// PATH: src/main/java/dev/masq/render/Colors.java
package dev.masq.render;

public final class Colors {
	public static final int INK = 0xFF0A0912;
	public static final int INK2 = 0xFF171528;
	public static final int EMBER = 0xFFFF6A3D;
	public static final int LILAC = 0xFFB79CFF;
	public static final int TEXT = 0xFFF2EEFC;
	public static final int DIM = 0xFF8E88A8;

	private Colors() {}

	/** ARGB alfasını m ile çarpar. */
	public static int a(int c, float m) {
		float k = m < 0f ? 0f : Math.min(m, 1f);
		int al = Math.round(((c >>> 24) & 255) * k);
		return (al << 24) | (c & 0xFFFFFF);
	}

	public static int mix(int x, int y, float t) {
		float k = t < 0f ? 0f : Math.min(t, 1f);
		int r = Math.round(((x >> 16) & 255) * (1 - k) + ((y >> 16) & 255) * k);
		int g = Math.round(((x >> 8) & 255) * (1 - k) + ((y >> 8) & 255) * k);
		int b = Math.round((x & 255) * (1 - k) + (y & 255) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
