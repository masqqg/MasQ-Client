// PATH: src/main/java/dev/masq/render/Logo.java
package dev.masq.render;

import dev.masq.anim.Easing;
import dev.masq.anim.Spring;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** MasQ logosu: halka Q, içinde kırmızı lensli göz. Göz kırpar ve sağa sola bakar. */
public final class Logo {
	private static final Spring LID = Spring.snappy().snap(1f);
	private static final Spring LOOK = Spring.soft().snap(0f);
	private static final float[] SPOTS = { -1f, -0.55f, 0f, 0.55f, 1f };
	private static final Random RNG = new Random();
	private static long nextBlink, reopen, nextLook;

	private Logo() {}

	private static void update() {
		long now = System.currentTimeMillis();
		if (now >= nextBlink) {
			LID.set(0f);
			reopen = now + 120;
			nextBlink = now + 2200 + RNG.nextInt(3500);
		}
		if (reopen != 0 && now >= reopen) {
			LID.set(1f);
			reopen = 0;
		}
		if (now >= nextLook) {
			LOOK.set(SPOTS[RNG.nextInt(SPOTS.length)]);
			nextLook = now + 800 + RNG.nextInt(1800);
		}
		LID.tick();
		LOOK.tick();
	}

	public static void draw(GuiGraphics g, int cx, int cy, int r, float a) {
		update();
		int ember = Colors.a(Colors.EMBER, a), lilac = Colors.a(Colors.LILAC, a);
		Draw.ring(g, cx, cy, r, Math.max(1, r - Math.max(2, r / 4)), ember);
		int x0 = cx + (int) (r * 0.55f), y0 = cy + (int) (r * 0.55f);
		for (int k = 0; k <= r / 2 + 1; k++) g.fill(x0 + k, y0 + k, x0 + k + 2, y0 + k + 2, lilac);
		eye(g, cx, cy, Math.max(3, (int) (r * 0.62f)), Math.max(2, (int) (r * 0.36f)), a);
	}

	private static void eye(GuiGraphics g, int cx, int cy, int hw, int hh, float a) {
		float open = Easing.clamp01(LID.value());
		float look = Math.max(-1f, Math.min(1f, LOOK.value()));
		int sclera = Colors.a(Colors.TEXT, a), red = Colors.a(0xFFFF2236, a);
		int pupil = Colors.a(0xFF1A0508, a), lid = Colors.a(Colors.LILAC, a);
		int ir = Math.max(2, hh);
		int ox = Math.round(look * (hw - ir) * 0.85f);
		int pr = Math.max(1, ir / 2);

		for (int dx = -hw; dx <= hw; dx++) {
			float u = dx / (float) hw;
			int half = Math.round(hh * open * (1f - u * u));
			int x = cx + dx;
			if (half <= 0) {
				g.fill(x, cy, x + 1, cy + 1, lid);
				continue;
			}
			g.fill(x, cy - half, x + 1, cy + half + 1, sclera);
			int du = dx - ox;
			if (Math.abs(du) <= ir) {
				int ih = Math.min(half, (int) Math.round(Math.sqrt(ir * ir - du * du)));
				g.fill(x, cy - ih, x + 1, cy + ih + 1, red);
				if (Math.abs(du) <= pr) {
					int ph = Math.min(ih, (int) Math.round(Math.sqrt(pr * pr - du * du)));
					g.fill(x, cy - ph, x + 1, cy + ph + 1, pupil);
				}
			}
		}
	}
}
