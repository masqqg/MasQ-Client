// PATH: src/main/java/dev/masq/render/Draw.java
package dev.masq.render;

import net.minecraft.client.gui.GuiGraphics;

public final class Draw {
	private Draw() {}

	public interface Painter {
		void paint(float x, float y, float alpha);
	}

	/** Fiziksel piksel çözünürlüğünde yumuşak yuvarlak dikdörtgen (r = h/2 ise hap). */
	public static void roundRect(GuiGraphics g, float fx, float fy, float fw, float fh, float fr, int col) {
		if ((col >>> 24) == 0 || fw <= 0 || fh <= 0) return;
		float s = Fx.gs();
		int x = Math.round(fx * s), y = Math.round(fy * s), w = Math.round(fw * s), h = Math.round(fh * s);
		if (w <= 0 || h <= 0) return;
		int r = Math.max(0, Math.min(Math.round(fr * s), Math.min(w, h) / 2));
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		if (r == 0) {
			g.fill(x, y, x + w, y + h, col);
		} else {
			int mid = h - 2 * r;
			if (mid > 0) g.fill(x, y + r, x + w, y + r + mid, col);
			for (int i = 0; i < r; i++) {
				float d = r - i - 0.5f;
				float inset = r - (float) Math.sqrt(r * r - d * d);
				int f = (int) inset;
				int fringe = Colors.a(col, 1f - (inset - f));
				int top = y + i, bot = y + h - 1 - i;
				int l = x + f + 1, rr = x + w - f - 1;
				if (l < rr) {
					g.fill(l, top, rr, top + 1, col);
					g.fill(l, bot, rr, bot + 1, col);
				}
				g.fill(x + f, top, x + f + 1, top + 1, fringe);
				g.fill(rr, top, rr + 1, top + 1, fringe);
				g.fill(x + f, bot, x + f + 1, bot + 1, fringe);
				g.fill(rr, bot, rr + 1, bot + 1, fringe);
			}
		}
		pose.popMatrix();
	}

	/** ri = 0 verilirse dolu daire. */
	public static void ring(GuiGraphics g, int cx, int cy, int ro, int ri, int col) {
		float s = Fx.gs();
		int ox = Math.round(cx * s), oy = Math.round(cy * s), o = Math.round(ro * s), in = Math.round(ri * s);
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		for (int dy = -o; dy <= o; dy++) {
			int wo = (int) Math.round(Math.sqrt((double) o * o - (double) dy * dy));
			int y = oy + dy;
			if (Math.abs(dy) < in) {
				int wi = (int) Math.round(Math.sqrt((double) in * in - (double) dy * dy));
				g.fill(ox - wo, y, ox - wi, y + 1, col);
				g.fill(ox + wi, y, ox + wo, y + 1, col);
			} else {
				g.fill(ox - wo, y, ox + wo, y + 1, col);
			}
		}
		pose.popMatrix();
	}

	/** Halka Q + maske gözü. */
	public static void logo(GuiGraphics g, float cx, float cy, float r, float a) {
		Logo.draw(g, cx, cy, r, a);
	}

	/** Hareket bulanıklığı: önceki konumdan şimdiki konuma ara örnekler (hayalet kopyalar). */
	public static void motion(float px, float py, float cx, float cy, float alpha, Painter p) {
		float dx = cx - px, dy = cy - py;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		if (dist < 0.6f) {
			p.paint(cx, cy, alpha);
			return;
		}
		int n = Math.min(10, (int) (dist / 1.5f) + 2);
		for (int i = 0; i < n; i++) {
			float t = (i + 1f) / n;
			float a = i == n - 1 ? alpha : alpha * 0.22f * t;
			p.paint(px + dx * t, py + dy * t, a);
		}
	}

	/** Bulanıklıkla giriş: p 0 -> 1, 5 örnekli kutu bulanıklığı yaklaşığı. */
	public static void blur(float p, float x, float y, float alpha, Painter painter) {
		float k = p < 0f ? 0f : Math.min(p, 1f);
		if (k >= 0.98f) {
			painter.paint(x, y, alpha);
			return;
		}
		float r = (1f - k) * 7f, a = alpha * k;
		painter.paint(x, y, a * 0.5f);
		painter.paint(x + r, y, a * 0.3f);
		painter.paint(x - r, y, a * 0.3f);
		painter.paint(x, y + r, a * 0.3f);
		painter.paint(x, y - r, a * 0.3f);
	}
}
