// PATH: src/main/java/dev/masq/render/Fx.java
package dev.masq.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Modern yazı tipi (Inter) + yumuşak şekil yardımcıları. */
public final class Fx {
	public static final int MED = 0, SEMI = 1, BIG = 2;
	private static final String[] IDS = { "inter", "inter_semi", "inter_big" };
	private static final Style[] ST = new Style[3];

	private Fx() {}

	private static Style style(int f) {
		if (ST[f] == null) ST[f] = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("masq", IDS[f])));
		return ST[f];
	}

	public static Component comp(String s, int f) {
		return Component.literal(s).withStyle(style(f));
	}

	public static float gs() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	public static int width(String s) { return width(s, MED); }

	public static int width(String s, int f) {
		return Minecraft.getInstance().font.width(comp(s, f));
	}

	public static void text(GuiGraphics g, String s, int x, int y, int col) { text(g, s, MED, x, y, col); }

	/** ClickGui eski çağrıları için (son parametre yok sayılır). */
	public static void text(GuiGraphics g, String s, int x, int y, int col, boolean shadow) { text(g, s, MED, x, y, col); }

	public static void text(GuiGraphics g, String s, int f, float x, float y, int col) {
		if ((col >>> 24) == 0) return;
		var p = g.pose();
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		p.pushMatrix();
		p.translate(x - ix, y - iy);
		g.drawString(Minecraft.getInstance().font, comp(s, f), ix, iy, col, false);
		p.popMatrix();
	}

	/** Bulanıklıkla belirme (p: 0 -> 1). */
	public static void textBlur(GuiGraphics g, String s, int f, float x, float y, int col, float p) {
		float k = p < 0f ? 0f : Math.min(p, 1f);
		if (k >= 0.98f) {
			text(g, s, f, x, y, col);
			return;
		}
		float r = (1f - k) * 3.2f;
		int c1 = Colors.a(col, k * 0.5f), c2 = Colors.a(col, k * 0.3f);
		text(g, s, f, x, y, c1);
		text(g, s, f, x + r, y, c2);
		text(g, s, f, x - r, y, c2);
		text(g, s, f, x, y + r, c2);
		text(g, s, f, x, y - r, c2);
	}

	/** Harf aralıklı yazı. Genişliği döner. */
	public static float spaced(GuiGraphics g, String s, int f, float x, float y, int col, float gap) {
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			text(g, ch, f, cx, y, col);
			cx += width(ch, f) + gap;
		}
		return cx - x - gap;
	}

	public static float spacedWidth(String s, int f, float gap) {
		float w = 0;
		for (int i = 0; i < s.length(); i++) w += width(String.valueOf(s.charAt(i)), f) + gap;
		return Math.max(0f, w - gap);
	}

	/** Cam hap: koyu taban + beyaz %7 dolgu + beyaz %14 kenar (scene.html). */
	public static void glass(GuiGraphics g, float x, float y, float w, float h, float r, float a) {
		if (a <= 0.01f) return;
		float e = 1f / gs();
		Draw.roundRect(g, x, y, w, h, r, Colors.a(0xFFFFFFFF, 0.14f * a));
		Draw.roundRect(g, x + e, y + e, w - 2 * e, h - 2 * e, Math.max(0f, r - e), Colors.a(Colors.INK, 0.62f * a));
		Draw.roundRect(g, x + e, y + e, w - 2 * e, h - 2 * e, Math.max(0f, r - e), Colors.a(0xFFFFFFFF, 0.07f * a));
	}

	/** Yumuşak ışık küresi (üst üste yarı saydam diskler). */
	public static void orb(GuiGraphics g, float cx, float cy, float rad, int col, float a) {
		int n = 8;
		for (int i = 0; i < n; i++) {
			float r = rad * (1f - i / (float) n * 0.9f);
			int step = Math.max(2, (int) (r / 40f));
			int c = Colors.a(col, a * 0.16f);
			int ir = (int) r;
			for (int dy = -ir; dy < ir; dy += step) {
				int hw = (int) Math.sqrt((double) r * r - (double) dy * dy);
				g.fill((int) cx - hw, (int) cy + dy, (int) cx + hw, (int) cy + dy + step, c);
			}
		}
	}
}
