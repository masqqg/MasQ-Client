// PATH: src/main/java/dev/masq/ui/Hud.java
package dev.masq.ui;

import dev.masq.anim.Spring;
import dev.masq.module.Module;
import dev.masq.module.ModuleManager;
import dev.masq.module.impl.ChunkHunter;
import dev.masq.module.impl.HudModule;
import dev.masq.module.impl.KillAura;
import dev.masq.render.Colors;
import dev.masq.render.Draw;
import dev.masq.render.Fx;
import dev.masq.render.Logo;
import dev.masq.render.Sprites;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Modern cam HUD + giriş animasyonu (scene.html renkleri, Inter yazı tipi). */
public final class Hud {
	private static final int H = 15;

	private static final class E {
		final Module m;
		final Spring x = Spring.snappy(), y = Spring.snappy();
		boolean init;
		E(Module m) { this.m = m; }
	}

	private static final Map<Module, E> ENTRIES = new LinkedHashMap<>();
	private static long start;
	private static boolean playing;

	private Hud() {}

	public static void onJoin() {
		HudModule h = HudModule.I;
		playing = h == null || h.intro.get();
		start = System.nanoTime();
	}

	private static float time() {
		return playing ? (System.nanoTime() - start) / 1e9f : 99f;
	}

	private static float P(float t, float a, float b) { return Math.max(0f, Math.min(1f, (t - a) / (b - a))); }
	private static float eo(float x) { return 1f - (float) Math.pow(1f - x, 3); }
	private static float eio(float x) { return x < .5f ? 4 * x * x * x : 1f - (float) Math.pow(-2 * x + 2, 3) / 2f; }

	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		HudModule h = HudModule.I;
		if (mc.options.hideGui || h == null || !h.enabled() || mc.player == null) return;
		float t = time();
		if (t > 5f) playing = false;
		int sw = g.guiWidth(), sh = g.guiHeight();

		if (t < 3.7f) backdrop(g, t, sw, sh);
		if (h.watermark.get() || t < 3.7f) brand(g, t, sw, sh, h.watermark.get());

		int y = 36;
		float ep = eo(P(t, 2.5f, 3.2f));
		String[] lab = { "FPS", "PING" };
		String[] val = { String.valueOf(mc.getFps()), ping(mc) };
		int n = 0;
		for (int i = 0; i < 2; i++) {
			float p = eo(P(t, 2.5f + i * .15f, 3.2f + i * .15f));
			chip(g, 8 + (1 - p) * -30, y, lab[i], val[i], p);
			y += H + 4;
			n++;
		}
		if (h.coords.get()) {
			LocalPlayer pl = mc.player;
			float p = eo(P(t, 2.8f, 3.5f));
			chip(g, 8 + (1 - p) * -30, y, "XYZ", pl.getBlockX() + " " + pl.getBlockY() + " " + pl.getBlockZ(), p);
			y += H + 4;
		}
		map(g, mc, y + 4, ep);
		if (h.list.get()) strip(g, h, t);
		target(g, mc);
	}

	private static String ping(Minecraft mc) {
		try {
			var c = mc.getConnection();
			var info = c == null ? null : c.getPlayerInfo(mc.player.getUUID());
			return info == null ? "-" : String.valueOf(info.getLatency());
		} catch (Throwable e) {
			return "-";
		}
	}

	private static void backdrop(GuiGraphics g, float t, int sw, int sh) {
		float a = eo(P(t, 0f, .35f)) * (1f - eio(P(t, 2.6f, 3.6f)));
		if (a <= 0.01f) return;
		g.fill(0, 0, sw, sh, Colors.a(Colors.INK, 0.92f * a));
		float k = sh / 360f;
		Fx.orb(g, sw * .1f + 60 * k * (float) Math.sin(t * .5), sh * .1f + 30 * k * (float) Math.cos(t * .4), 150 * k, Colors.EMBER, .55f * a);
		Fx.orb(g, sw * .85f + 50 * k * (float) Math.cos(t * .45), sh * .75f + 35 * k * (float) Math.sin(t * .5), 150 * k, 0xFF6A4CFF, .5f * a);
		Fx.orb(g, sw * .45f + 70 * k * (float) Math.sin(t * .35), sh * .6f + 40 * k * (float) Math.cos(t * .3), 100 * k, Colors.LILAC, .35f * a);
	}

	/** Logo + MasQ yazısı: ortada belirir, sol üste küçülür ve kalıcı watermark olur. */
	private static void brand(GuiGraphics g, float t, int sw, int sh, boolean showFinal) {
		float li = eo(P(t, .2f, 1.3f)), L = eio(P(t, 2f, 2.9f)), W = eio(P(t, 2.3f, 3.4f));
		float bigW = Fx.width("MasQ", Fx.BIG);
		float cx0 = sw / 2f, cy0 = sh / 2f - 24f;
		float lx = cx0 + (17f - cx0) * L, ly = cy0 + (17f - cy0) * L, lr = 30f + (9f - 30f) * L;

		float pill = showFinal ? W : 0f;
		if (pill > 0f) Fx.glass(g, 6, 6, 32 + bigW * .58f + 12, 22, 11, pill);

		Logo.draw(g, lx, ly, lr, li, li, (1f - li) * -40f);

		float ws = 1f + (.58f - 1f) * W;
		float wx = (sw - bigW) / 2f + (32f - (sw - bigW) / 2f) * W;
		float wy = cy0 + 40f + (17f - 17f - (cy0 + 40f) + 17f - 15f + (cy0 + 40f)) * W;
		wy = (cy0 + 40f) + ((17f - 9f) - (cy0 + 40f)) * W;
		var p = g.pose();
		p.pushMatrix();
		p.translate(wx, wy);
		p.scale(ws, ws);
		String word = "MasQ";
		for (int i = 0; i < word.length(); i++) {
			float k = eo(P(t, .8f + i * .12f, 1.6f + i * .12f));
			float ox = Fx.width(word.substring(0, i), Fx.BIG);
			Fx.textBlur(g, word.substring(i, i + 1), Fx.BIG, ox, (1f - k) * 13f, Colors.TEXT, k);
		}
		p.popMatrix();

		float sa = eo(P(t, 1.5f, 2.1f)) * (1f - W);
		if (sa > 0.02f) {
			float sww = Fx.spacedWidth("CLIENT", Fx.MED, 4f);
			Fx.spaced(g, "CLIENT", Fx.MED, (sw - sww) / 2f, wy + 30f, Colors.a(Colors.DIM, sa), 4f);
		}
	}

	private static void chip(GuiGraphics g, float x, float y, String label, String value, float p) {
		if (p <= 0.01f) return;
		int lw = (int) Fx.spacedWidth(label, Fx.MED, 1.2f), vw = Math.max(24, Fx.width(value, Fx.SEMI));
		float w = 9 + lw + 7 + vw + 9;
		Fx.glass(g, x, y, w, H, H / 2f, p);
		Fx.spaced(g, label, Fx.MED, x + 9, y + 3.5f, Colors.a(Colors.DIM, p), 1.2f);
		Fx.text(g, value, Fx.SEMI, x + w - 9 - Fx.width(value, Fx.SEMI), y + 3.5f, Colors.a(0xFFFFFFFF, p));
	}

	private static void strip(GuiGraphics g, HudModule hud, float t) {
		int sw = g.guiWidth();
		List<Module> on = new ArrayList<>();
		for (Module m : ModuleManager.all()) if (m.enabled() && m != hud) on.add(m);
		on.sort(Comparator.comparingInt((Module m) -> -rw(m)));
		for (Module m : on) ENTRIES.computeIfAbsent(m, E::new);
		float gate = eo(P(t, 3f, 3.6f));

		for (E e : ENTRIES.values()) {
			int idx = on.indexOf(e.m);
			boolean live = idx >= 0;
			int w = rw(e.m);
			if (!e.init) {
				e.init = true;
				e.x.snap(sw + 6);
				e.y.snap(8 + Math.max(idx, 0) * (H + 2));
			}
			e.x.set(live ? sw - w : sw + 6);
			if (live) e.y.set(8 + idx * (H + 2));
			e.x.tick();
			e.y.tick();
		}
		for (Iterator<E> it = ENTRIES.values().iterator(); it.hasNext(); ) {
			E e = it.next();
			if (!on.contains(e.m) && e.x.settled()) {
				it.remove();
				continue;
			}
			Module m = e.m;
			int w = rw(m);
			Draw.motion(e.x.prev(), e.y.prev(), e.x.value(), e.y.value(), gate, (px, py, a) -> {
				int cols = w / 2;
				for (int i = 0; i < cols; i++) {
					float k = i / (float) cols;
					g.fill(Math.round(px) + i * 2, Math.round(py), Math.round(px) + i * 2 + 2, Math.round(py) + H,
						Colors.a(Colors.LILAC, 0.26f * k * a));
				}
				Fx.text(g, m.name(), Fx.SEMI, px + 14, py + 3.5f, Colors.a(Colors.TEXT, a));
				String tag = m.tag().toUpperCase();
				if (!tag.isEmpty()) Fx.text(g, tag, Fx.MED, px + 14 + Fx.width(m.name(), Fx.SEMI) + 5, py + 4.5f, Colors.a(Colors.DIM, a));
			});
		}
	}

	private static int rw(Module m) {
		int w = Fx.width(m.name(), Fx.SEMI);
		String tag = m.tag();
		if (!tag.isEmpty()) w += 5 + Fx.width(tag.toUpperCase(), Fx.MED);
		return w + 14 + 8;
	}

	/** KillAura hedef kartı + dönen halka. */
	private static void target(GuiGraphics g, Minecraft mc) {
		LivingEntity e = KillAura.target;
		long age = System.currentTimeMillis() - KillAura.targetTime;
		if (e == null || age > 1500 || !e.isAlive()) return;
		float a = 1f - eo(P(age, 1100f, 1500f));
		float in = eo(P(age, 0f, 250f));
		a *= in;
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
		float rot = System.nanoTime() / 1e9f * 1.6f;
		var p = g.pose();
		p.pushMatrix();
		p.translate(cx, cy);
		p.rotate(rot);
		float s = Fx.gs();
		p.scale(1f / s, 1f / s);
		for (int i = 0; i < 12; i++) {
			g.fill(Math.round(22 * s), Math.round(-1 * s), Math.round(27 * s), Math.round(1 * s),
				Colors.a(Colors.mix(Colors.EMBER, Colors.LILAC, i / 11f), 0.9f * a));
			p.rotate((float) (Math.PI * 2 / 12));
		}
		p.popMatrix();

		float x = cx + 34, y = cy + 6, w = 112;
		Fx.glass(g, x, y, w, 30, 9, a);
		Fx.text(g, e.getName().getString(), Fx.SEMI, x + 9, y + 5, Colors.a(Colors.TEXT, a));
		String d = String.format("%.1f m", mc.player.distanceTo(e));
		Fx.text(g, d, Fx.MED, x + w - 9 - Fx.width(d, Fx.MED), y + 5, Colors.a(Colors.DIM, a));
		float hp = Math.max(0f, Math.min(1f, e.getHealth() / e.getMaxHealth()));
		Draw.roundRect(g, x + 9, y + 21, w - 18, 3, 1.5f, Colors.a(0xFFFFFFFF, 0.12f * a));
		float bw = (w - 18) * hp;
		for (int i = 0; i < 20; i++) {
			float x0 = bw * i / 20f, x1 = bw * (i + 1) / 20f;
			if (x1 - x0 < .1f) continue;
			g.fill(Math.round(x + 9 + x0), Math.round(y + 21), Math.round(x + 9 + x1 + .5f), Math.round(y + 24),
				Colors.a(Colors.mix(Colors.EMBER, Colors.LILAC, i / 19f), a));
		}
	}

	/** ChunkHunter 2D haritası: kuzey yukarı, oyuncu ortada. Lila = cluster, kızıl = sus, ikisi = karışık. */
	private static void map(GuiGraphics g, Minecraft mc, int top, float ep) {
		ChunkHunter c = ChunkHunter.I;
		LocalPlayer p = mc.player;
		if (c == null || !c.enabled() || !c.mapOn() || p == null || ep <= 0.01f) return;

		int R = Math.min(c.rangeI(), 10), cell = 3, n = 2 * R + 1, size = n * cell;
		int x0 = 14, y0 = top + 4;
		int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
		Set<Long> buds = c.budSet(), sus = c.susSet();

		Fx.glass(g, x0 - 6, y0 - 6, size + 12 + 44, size + 28, 10, ep);
		g.fill(x0, y0, x0 + size, y0 + size, Colors.a(Colors.INK2, 0.9f));

		long nearest = Long.MIN_VALUE;
		double best = Double.MAX_VALUE;
		for (int pass = 0; pass < 2; pass++) {
			for (long k : pass == 0 ? buds : sus) {
				int dx = ChunkHunter.cx(k) - pcx, dz = ChunkHunter.cz(k) - pcz;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < best) {
					best = d;
					nearest = k;
				}
				if (Math.abs(dx) > R || Math.abs(dz) > R) continue;
				boolean b = buds.contains(k), s = sus.contains(k);
				int col = b && s ? Colors.TEXT : b ? Colors.LILAC : Colors.EMBER;
				int px = x0 + (dx + R) * cell, py = y0 + (dz + R) * cell;
				g.fill(px, py, px + cell, py + cell, col);
			}
		}
		int cx = x0 + R * cell, cy = y0 + R * cell;
		g.fill(cx, cy, cx + cell, cy + cell, Colors.TEXT);

		String info = "C " + buds.size() + "  S " + sus.size();
		if (nearest != Long.MIN_VALUE) info += "  " + (int) (best * 16) + "m";
		Fx.text(g, info, Fx.MED, x0, y0 + size + 4, Colors.DIM);
		if (nearest != Long.MIN_VALUE) {
			Fx.text(g, (ChunkHunter.cx(nearest) * 16 + 8) + " " + (ChunkHunter.cz(nearest) * 16 + 8), Fx.SEMI, x0 + size + 8, y0 + 20, Colors.TEXT);
		}
		if (!buds.isEmpty()) Sprites.cluster(g, x0 + size + 8, y0 + 34, 1);
	}
}
