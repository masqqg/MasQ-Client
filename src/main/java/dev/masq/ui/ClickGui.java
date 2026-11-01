// PATH: src/main/java/dev/masq/ui/ClickGui.java
package dev.masq.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.masq.Binds;
import dev.masq.MasQClient;
import dev.masq.Sfx;
import dev.masq.anim.Easing;
import dev.masq.anim.Spring;
import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.ModuleManager;
import dev.masq.module.Setting;
import dev.masq.render.Colors;
import dev.masq.render.Draw;
import dev.masq.render.Fx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sol tık: aç/kapa. Sağ tık: ayar menüsünü aşağı aç. Orta tık: keybind. Tekerlek: kaydır.
 * Slider'da basılı tutup sürükle. Bind satırına tıkla, tuşa bas (Esc iptal, Del temizle).
 */
public final class ClickGui extends Screen {
	private static final int RH = 22, RG = 6, SR = 18, TH = 16;
	private static final int TRACK = 0xFF2A2644, DROP = 0xFF100E1C;

	private static final class Row {
		final Module m;
		final float y, h;

		Row(Module m, float y, float h) {
			this.m = m;
			this.y = y;
			this.h = h;
		}
	}

	private int W = 400, H = 310;
	private final Spring open = Spring.soft();
	private final Spring tabX = Spring.snappy(), tabW = Spring.snappy();
	private final Spring scroll = new Spring(220f, 30f);
	private final Map<Module, Spring> hover = new HashMap<>(), exp = new HashMap<>();
	private final Map<Setting<?>, Spring> anim = new HashMap<>();
	private final Set<Module> expanded = new HashSet<>();
	private List<Row> cur = new ArrayList<>();
	private Category cat = Category.values()[0];
	private boolean closing, tabInit;
	private float am = 1f, scrollT, maxScroll, dragX, dragW;
	private Module listening;
	private Setting.Num dragging;
	private int mx, my;

	public ClickGui() {
		super(Component.literal("MasQ"));
		open.snap(0f);
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void onClose() { closing = true; }

	private int px() { return (width - W) / 2; }
	private int py() { return (height - H) / 2; }
	private int top() { return py() + 66; }
	private int areaH() { return H - 66 - 30; }
	private int c(int col) { return Colors.a(col, am); }

	private Spring exp(Module m) { return exp.computeIfAbsent(m, k -> new Spring(220f, 30f)); }

	private static float target(Setting<?> s) {
		if (s instanceof Setting.Bool b) return b.get() ? 1f : 0f;
		if (s instanceof Setting.Num n) return n.frac();
		return 0f;
	}

	private Spring anim(Setting<?> s) { return anim.computeIfAbsent(s, k -> Spring.snappy().snap(target(k))); }

	private int[] tabRect(int i) {
		Category[] cs = Category.values();
		int x = px() + 16;
		for (int j = 0; j < i; j++) x += Fx.width(cs[j].label()) + 16 + 4;
		return new int[] { x, Fx.width(cs[i].label()) + 16 };
	}

	private static boolean inside(double x, double y, double rx, double ry, double rw, double rh) {
		return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
	}

	private static String fmt(Setting.Num n) {
		return n.step >= 1f ? Integer.toString(n.i()) : String.format("%.2f", n.f());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		W = Math.min(400, width - 20);
		H = Math.min(310, height - 20);
		mx = mouseX;
		my = mouseY;
		open.set(closing ? 0f : 1f);
		open.tick();

		int[] t = tabRect(cat.ordinal());
		if (!tabInit) {
			tabInit = true;
			tabX.snap(t[0]);
			tabW.snap(t[1]);
		}
		tabX.set(t[0]);
		tabW.set(t[1]);
		tabX.tick();
		tabW.tick();

		float o = Easing.clamp01(open.value());

		// satır yerleşimi + animasyon tick'leri (kare başına bir kez)
		float y = top() - scroll.value();
		List<Row> rows = new ArrayList<>();
		for (Module m : ModuleManager.byCategory(cat)) {
			Spring ex = exp(m);
			ex.set(expanded.contains(m) ? 1f : 0f);
			ex.tick();
			m.fill.tick();
			float e = Easing.clamp01(ex.value());
			float h = RH + e * ((m.visible().size() + 1) * SR + 8);
			rows.add(new Row(m, y, h));
			y += h + RG;
			if (e > 0.01f) {
				for (Setting<?> s : m.visible()) {
					Spring a = anim(s);
					a.set(target(s));
					a.tick();
				}
			}
		}
		maxScroll = Math.max(0f, (y - RG) - (top() - scroll.value()) - areaH());
		scrollT = Math.max(0f, Math.min(scrollT, maxScroll));
		scroll.set(scrollT);
		scroll.tick();
		cur = rows;

		for (Row r : rows) {
			Spring hv = hover.computeIfAbsent(r.m, k -> Spring.snappy());
			boolean hit = o > 0.9f && my >= top() && my <= top() + areaH() && inside(mx, my, px() + 16, r.y, W - 32, RH);
			hv.set(hit ? 1f : 0f);
			hv.tick();
		}

		if (closing && open.value() < 0.03f) {
			MasQClient.saveConfig();
			this.minecraft.setScreen(null);
			return;
		}

		g.fill(0, 0, width, height, Colors.a(Colors.INK, 0.62f * o));
		float s = 0.9f + 0.1f * open.value();
		if (o >= 0.98f) {
			layer(g, 0, 0, 1f, s);
		} else {
			float r = (1f - o) * 7f;
			layer(g, 0, 0, o * 0.55f, s);
			layer(g, r, 0, o * 0.3f, s);
			layer(g, -r, 0, o * 0.3f, s);
			layer(g, 0, r, o * 0.3f, s);
			layer(g, 0, -r, o * 0.3f, s);
		}
	}

	private void layer(GuiGraphics g, float ox, float oy, float a, float s) {
		float cx = px() + W / 2f, cy = py() + H / 2f;
		var p = g.pose();
		p.pushMatrix();
		p.translate(cx + ox, cy + oy);
		p.scale(s, s);
		p.translate(-cx, -cy);
		am = a;
		paintGui(g);
		p.popMatrix();
	}

	private void paintGui(GuiGraphics g) {
		int x = px(), y = py();
		float o = Easing.clamp01(open.value());

		Draw.roundRect(g, x - 1, y - 1, W + 2, H + 2, 15, c(Colors.a(Colors.LILAC, 0.25f)));
		Draw.roundRect(g, x, y, W, H, 14, c(Colors.a(Colors.INK, 0.95f)));

		Draw.logo(g, x + 28, y + 26, 10, am);
		Fx.text(g, "MasQ", x + 46, y + 21, c(Colors.TEXT), false);
		String v = "1.21.11";
		Fx.text(g, v, x + W - 16 - Fx.width(v), y + 21, c(Colors.DIM), false);

		Draw.roundRect(g, tabX.value(), y + 42, tabW.value(), TH, TH / 2f, c(Colors.a(Colors.LILAC, 0.22f)));
		Category[] cs = Category.values();
		for (int i = 0; i < cs.length; i++) {
			int[] r = tabRect(i);
			Fx.text(g, cs[i].label(), r[0] + 8, y + 42 + (TH - 9) / 2 + 1, c(cs[i] == cat ? Colors.TEXT : Colors.DIM), false);
		}

		int rx0 = x + 16, rw = W - 32;
		g.enableScissor(x + 8, top() - 2, x + W - 8, top() + areaH());
		if (cur.isEmpty()) {
			String e = "No modules yet";
			Fx.text(g, e, x + (W - Fx.width(e)) / 2, top() + 8, c(Colors.DIM), false);
		}
		for (int i = 0; i < cur.size(); i++) {
			Row r = cur.get(i);
			if (r.y + r.h < top() - 6 || r.y > top() + areaH() + 6) continue;
			paintRow(g, r, i, o, rx0, rw);
		}
		g.disableScissor();

		if (maxScroll > 1f) {
			int th = Math.max(16, (int) (areaH() * areaH() / (areaH() + maxScroll)));
			int ty = top() + (int) ((areaH() - th) * (scroll.value() / maxScroll));
			Draw.roundRect(g, x + W - 9, ty, 3, th, 1.5f, c(Colors.a(Colors.LILAC, 0.5f)));
		}

		String hint = listening != null ? "Press a key to bind  (Esc cancel, Del clear)"
			: "LMB toggle   RMB settings   MMB bind   Esc close";
		Fx.text(g, hint, x + 16, y + H - 20, c(Colors.a(listening != null ? Colors.EMBER : Colors.DIM, 0.8f)), false);
	}

	private void paintRow(GuiGraphics g, Row r, int i, float o, int rx0, int rw) {
		Module m = r.m;
		float t = Easing.outCubic(Easing.clamp01((o - 0.2f - i * 0.06f) / 0.45f));
		float lift = hover.get(m).value() * 1.5f;
		float rx = rx0, ry = r.y - lift + (1f - t) * 10f;
		float e = Easing.clamp01(exp(m).value());

		if (e > 0.02f) {
			Draw.roundRect(g, rx + 6, ry + RH - 6, rw - 12, (r.h - RH) + 4, 8, c(Colors.a(DROP, t * e)));
		}
		Draw.roundRect(g, rx, ry, rw, RH, RH / 2f, c(Colors.a(Colors.INK2, t)));
		float f = Easing.clamp01(m.fill.value());
		if (f > 0.01f) Draw.roundRect(g, rx, ry, Math.max(RH, rw * f), RH, RH / 2f, c(Colors.a(Colors.EMBER, 0.92f * t)));

		int ty = Math.round(ry) + (RH - 9) / 2 + 1;
		Fx.text(g, m.name(), Math.round(rx) + 12, ty, c(Colors.a(Colors.TEXT, t)), false);
		String key = Binds.label(m);
		String right = (key.isEmpty() ? "" : "[" + key + "]  ") + (m.enabled() ? "ON" : "OFF");
		Fx.text(g, right, Math.round(rx + rw) - 12 - Fx.width(right), ty,
			c(Colors.a(m.enabled() ? Colors.TEXT : Colors.DIM, t)), false);

		if (e <= 0.05f) return;
		List<Setting<?>> ss = m.visible();
		float sx = rx + 16, sw = rw - 32, a = t * e;
		for (int j = 0; j <= ss.size(); j++) {
			float sy = ry + RH + 4 + j * SR;
			if (sy + SR - 1 > ry + r.h) break;
			int ly = Math.round(sy) + 5;
			if (j == ss.size()) {
				Fx.text(g, "Bind", Math.round(sx), ly, c(Colors.a(Colors.TEXT, a)), false);
				String kv = listening == m ? "press a key..." : key.isEmpty() ? "None" : key;
				Fx.text(g, kv, Math.round(sx + sw) - Fx.width(kv), ly,
					c(Colors.a(listening == m ? Colors.EMBER : Colors.LILAC, a)), false);
				break;
			}
			Setting<?> s = ss.get(j);
			if (s instanceof Setting.Bool b) {
				Fx.text(g, b.name(), Math.round(sx), ly, c(Colors.a(Colors.TEXT, a)), false);
				float k = Easing.clamp01(anim(b).value());
				Draw.roundRect(g, sx + sw - 22, sy + 4, 22, 10, 5, c(Colors.a(Colors.mix(TRACK, Colors.EMBER, k), a)));
				Draw.ring(g, Math.round(sx + sw - 22 + 5 + k * 12), Math.round(sy + 9), 4, 0, c(Colors.a(Colors.TEXT, a)));
			} else if (s instanceof Setting.Num n) {
				Fx.text(g, n.name(), Math.round(sx), Math.round(sy) + 1, c(Colors.a(Colors.TEXT, a)), false);
				String val = fmt(n);
				Fx.text(g, val, Math.round(sx + sw) - Fx.width(val), Math.round(sy) + 1, c(Colors.a(Colors.LILAC, a)), false);
				float fy = sy + SR - 6, fw = sw * Easing.clamp01(anim(n).value());
				Draw.roundRect(g, sx, fy, sw, 3, 1.5f, c(Colors.a(TRACK, a)));
				Draw.roundRect(g, sx, fy, Math.max(3f, fw), 3, 1.5f, c(Colors.a(Colors.EMBER, a)));
				Draw.ring(g, Math.round(sx + fw), Math.round(fy) + 1, 3, 0, c(Colors.a(Colors.TEXT, a)));
			} else if (s instanceof Setting.Mode md) {
				Fx.text(g, md.name(), Math.round(sx), ly, c(Colors.a(Colors.TEXT, a)), false);
				String val = md.get();
				Fx.text(g, val, Math.round(sx + sw) - Fx.width(val), ly, c(Colors.a(Colors.LILAC, a)), false);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (closing || open.value() < 0.9f) return super.mouseClicked(e, doubleClick);
		if (listening != null) {
			listening = null;
			return true;
		}
		double x = e.x(), y = e.y();
		int btn = e.button();

		if (btn == 0) {
			Category[] cs = Category.values();
			for (int i = 0; i < cs.length; i++) {
				int[] r = tabRect(i);
				if (inside(x, y, r[0], py() + 42, r[1], TH)) {
					cat = cs[i];
					scrollT = 0f;
					Sfx.tick(1.1f);
					return true;
				}
			}
		}
		if (y < top() || y > top() + areaH()) return super.mouseClicked(e, doubleClick);

		float rx = px() + 16, rw = W - 32;
		for (Row r : cur) {
			Module m = r.m;
			if (inside(x, y, rx, r.y, rw, RH)) {
				if (btn == 0) {
					m.toggle();
				} else if (btn == 1) {
					if (!expanded.remove(m)) expanded.add(m);
					Sfx.tick(1.0f);
				} else if (btn == 2) {
					listening = m;
					Sfx.tick(1.3f);
				}
				return true;
			}
			if (btn != 0 || !expanded.contains(m) || exp(m).value() < 0.5f) continue;
			List<Setting<?>> ss = m.visible();
			for (int j = 0; j <= ss.size(); j++) {
				if (!inside(x, y, rx + 16, r.y + RH + 4 + j * SR, rw - 32, SR)) continue;
				if (j == ss.size()) {
					listening = m;
					Sfx.tick(1.3f);
				} else {
					Setting<?> s = ss.get(j);
					if (s instanceof Setting.Bool b) {
						b.toggle();
						Sfx.tick(b.get() ? 1.5f : 0.9f);
					} else if (s instanceof Setting.Num n) {
						dragging = n;
						dragX = rx + 16;
						dragW = rw - 32;
						n.setFrac((float) ((x - dragX) / dragW));
					} else if (s instanceof Setting.Mode md) {
						md.next();
						Sfx.tick(1.1f);
					}
				}
				return true;
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging != null) {
			dragging.setFrac((float) ((e.x() - dragX) / dragW));
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = null;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scrollT = Math.max(0f, Math.min(maxScroll, scrollT - (float) scrollY * 30f));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (listening != null) {
			int k = e.key();
			if (k == InputConstants.KEY_DELETE || k == InputConstants.KEY_BACKSPACE) Binds.set(listening, -1);
			else if (k != InputConstants.KEY_ESCAPE) Binds.set(listening, k);
			listening = null;
			Sfx.tick(1.3f);
			return true;
		}
		return super.keyPressed(e);
	}
}
