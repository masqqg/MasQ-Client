// PATH: src/main/java/dev/masq/render/Logo.java
package dev.masq.render;

import dev.masq.anim.Easing;
import dev.masq.anim.Spring;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** MasQ logosu: gradyan halka Q, içinde kırmızı lensli göz (kırpar, sağa sola bakar). */
public final class Logo {
	private static final Spring LID = Spring.snappy().snap(1f);
	private static final Spring LOOK = Spring.soft().snap(0f);
	private static final float[] SPOTS = { -1f, -0.55f, 0f, 0.55f, 1f };
	private static final Random RNG = new Random();
	private static long nextBlink, reopen, nextLook;
	private static long lastTick;

	private Logo() {}

	private static void update() {
		long now = System.currentTimeMillis();
		if (now == lastTick) return;
		lastTick = now;
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

	public static void draw(GuiGraphics g, float cx, float cy, float r, float a) {
		draw(g, cx, cy, r, a, 1f, 0f);
	}

	/** prog: halkanın çizilme oranı (0..1), rot: derece. r = dış yarıçap (GUI px). */
	public static void draw(GuiGraphics g, float cx, float cy, float r, float a, float prog, float rot) {
		if (a <= 0.01f) return;
		update();
		float s = Fx.gs();
		float u = r * s / 47.5f;
		var p = g.pose();
		p.pushMatrix();
		p.scale(1f / s, 1f / s);
		p.translate(cx * s, cy * s);

		p.pushMatrix();
		p.rotate((float) Math.toRadians(rot));
		ring(g, u, a, prog);
		tail(g, u, a, prog);
		p.popMatrix();

		eye(g, u, a * Easing.clamp01(prog * 1.4f));
		p.popMatrix();
	}

	private static int grad(float x, float y, float rad, float a) {
		float t = 0.5f + (x + y) / (2.83f * rad);
		return Colors.a(Colors.mix(Colors.EMBER, Colors.LILAC, t), a);
	}

	private static void ring(GuiGraphics g, float u, float a, float prog) {
		float rad = 44f * u, th = 7f * u;
		int n = 96, cnt = (int) Math.ceil(n * Easing.clamp01(prog));
		float step = (float) (Math.PI * 2 / n);
		float len = rad * step + 1.5f;
		var p = g.pose();
		p.pushMatrix();
		p.rotate((float) Math.toRadians(-70));
		for (int k = 0; k < cnt; k++) {
			float ang = (float) Math.toRadians(-70) + k * step;
			int col = grad((float) Math.cos(ang) * rad, (float) Math.sin(ang) * rad, rad, a);
			g.fill(Math.round(rad - th / 2), Math.round(-len / 2), Math.round(rad + th / 2), Math.round(len / 2), col);
			p.rotate(step);
		}
		p.popMatrix();
	}

	private static void tail(GuiGraphics g, float u, float a, float prog) {
		if (prog < 0.3f) return;
		float ta = a * Easing.clamp01((prog - 0.3f) / 0.7f);
		var p = g.pose();
		p.pushMatrix();
		p.translate(30f * u, 34f * u);
		p.rotate((float) (Math.PI / 4));
		float len = 39.6f * u, th = 7f * u;
		int segs = 4;
		for (int i = 0; i < segs; i++) {
			float x0 = -len / 2 + len * i / segs, x1 = -len / 2 + len * (i + 1) / segs + 1f;
			g.fill(Math.round(x0), Math.round(-th / 2), Math.round(x1), Math.round(th / 2),
				Colors.a(Colors.mix(Colors.EMBER, Colors.LILAC, 0.55f + 0.45f * (i + 0.5f) / segs), ta));
		}
		p.popMatrix();
	}

	private static void eye(GuiGraphics g, float u, float a) {
		if (a <= 0.01f) return;
		float open = Easing.clamp01(LID.value());
		float look = Math.max(-1f, Math.min(1f, LOOK.value()));
		int hw = Math.max(3, Math.round(25f * u)), hh = Math.max(2, Math.round(14f * u));
		int sclera = Colors.a(Colors.TEXT, a), red = Colors.a(0xFFFF2236, a);
		int pupil = Colors.a(0xFF1A0508, a), lid = Colors.a(Colors.LILAC, a);
		int ir = Math.max(2, Math.round(hh * 0.92f));
		int ox = Math.round(look * (hw - ir) * 0.85f);
		int pr = Math.max(1, ir / 2);
		for (int dx = -hw; dx <= hw; dx++) {
			float k = dx / (float) hw;
			int half = Math.round(hh * open * (1f - k * k));
			if (half <= 0) {
				g.fill(dx, 0, dx + 1, Math.max(1, Math.round(u * 1.5f)), lid);
				continue;
			}
			g.fill(dx, -half, dx + 1, half + 1, sclera);
			int du = dx - ox;
			if (Math.abs(du) <= ir) {
				int ih = Math.min(half, (int) Math.round(Math.sqrt((double) ir * ir - (double) du * du)));
				g.fill(dx, -ih, dx + 1, ih + 1, red);
				if (Math.abs(du) <= pr) {
					int ph = Math.min(ih, (int) Math.round(Math.sqrt((double) pr * pr - (double) du * du)));
					g.fill(dx, -ph, dx + 1, ph + 1, pupil);
				}
			}
		}
	}
}
