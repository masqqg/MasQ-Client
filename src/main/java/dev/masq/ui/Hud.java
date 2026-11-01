package dev.masq.ui;

import dev.masq.anim.Spring;
import dev.masq.module.Module;
import dev.masq.module.ModuleManager;
import dev.masq.module.impl.ChunkHunter;
import dev.masq.module.impl.HudModule;
import dev.masq.render.Colors;
import dev.masq.render.Draw;
import dev.masq.render.Sprites;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sol üstte watermark/koordinat/chunk haritası, sağda kayan modül şeridi. */
public final class Hud {
	private static final int H = 14, GAP = 3;

	private static final class E {
		final Module m;
		final Spring x = Spring.snappy(), y = Spring.snappy();
		boolean init;
		int col = Colors.EMBER;

		E(Module m) { this.m = m; }
	}

	private static final Map<Module, E> ENTRIES = new LinkedHashMap<>();
	private static final Spring INTRO = Spring.soft();

	private Hud() {}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		HudModule h = HudModule.I;
		if (mc.options.hideGui || h == null || !h.enabled()) return;
		Font f = mc.font;

		INTRO.set(1f);
		INTRO.tick();
		if (h.watermark.get()) watermark(g, f);
		if (h.coords.get() && mc.player != null) coords(g, f, mc.player);
		map(g, f, mc);
		if (h.list.get()) strip(g, f, h);
	}

	private static void strip(GuiGraphics g, Font f, HudModule hud) {
		int sw = g.guiWidth();
		List<Module> on = new ArrayList<>();
		for (Module m : ModuleManager.all()) if (m.enabled() && m != hud) on.add(m);
		on.sort(Comparator.comparingInt((Module m) -> -f.width(m.name())));
		for (Module m : on) ENTRIES.computeIfAbsent(m, E::new);

		int n = Math.max(1, on.size() - 1);
		for (E e : ENTRIES.values()) {
			int idx = on.indexOf(e.m);
			boolean live = idx >= 0;
			int w = f.width(e.m.name()) + 16;
			if (!e.init) {
				e.init = true;
				e.x.snap(sw + 4);
				e.y.snap(6 + Math.max(idx, 0) * (H + GAP));
			}
			e.x.set(live ? sw - w - 6 : sw + 4);
			if (live) {
				e.y.set(6 + idx * (H + GAP));
				e.col = Colors.mix(Colors.EMBER, Colors.LILAC, idx / (float) n);
			}
			e.x.tick();
			e.y.tick();
		}

		for (Iterator<E> it = ENTRIES.values().iterator(); it.hasNext(); ) {
			E e = it.next();
			if (!on.contains(e.m) && e.x.settled()) {
				it.remove();
				continue;
			}
			String name = e.m.name();
			int w = f.width(name) + 16, col = e.col;
			Draw.motion(e.x.prev(), e.y.prev(), e.x.value(), e.y.value(), 1f, (px, py, a) -> {
				int ix = Math.round(px), iy = Math.round(py);
				Draw.roundRect(g, ix, iy, w, H, H / 2f, Colors.a(Colors.INK, 0.82f * a));
				Draw.ring(g, ix + 7, iy + H / 2, 2, 0, Colors.a(col, a));
				g.drawString(f, name, ix + 12, iy + 3, Colors.a(Colors.TEXT, a), false);
			});
		}
	}

	private static void watermark(GuiGraphics g, Font f) {
		int w = 24 + f.width("MasQ") + 8;
		Draw.blur(INTRO.value(), 8, 8, 1f, (px, py, a) -> {
			int ix = Math.round(px), iy = Math.round(py);
			Draw.roundRect(g, ix, iy, w, 18, 9, Colors.a(Colors.INK, 0.82f * a));
			Draw.logo(g, ix + 10, iy + 9, 6, a);
			g.drawString(f, "MasQ", ix + 22, iy + 5, Colors.a(Colors.TEXT, a), false);
		});
	}

	private static void coords(GuiGraphics g, Font f, LocalPlayer p) {
		String s = p.getBlockX() + "  " + p.getBlockY() + "  " + p.getBlockZ();
		int w = f.width(s) + 14;
		Draw.blur(INTRO.value(), 8, 30, 1f, (px, py, a) -> {
			int ix = Math.round(px), iy = Math.round(py);
			Draw.roundRect(g, ix, iy, w, H, H / 2f, Colors.a(Colors.INK, 0.82f * a));
			g.drawString(f, s, ix + 7, iy + 3, Colors.a(Colors.DIM, a), false);
		});
	}

	/** ChunkHunter 2D haritası: kuzey yukarı, oyuncu ortada. Lila = cluster, kızıl = sus, ikisi = karışık. */
	private static void map(GuiGraphics g, Font f, Minecraft mc) {
		ChunkHunter c = ChunkHunter.I;
		LocalPlayer p = mc.player;
		if (c == null || !c.enabled() || !c.mapOn() || p == null) return;

		int R = Math.min(c.rangeI(), 10), cell = 3, n = 2 * R + 1, size = n * cell;
		int x0 = 11, y0 = 52;
		int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
		Set<Long> buds = c.budSet(), sus = c.susSet();

		Draw.roundRect(g, x0 - 3, y0 - 3, size + 6, size + 24, 6, Colors.a(Colors.INK, 0.78f));
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
		g.drawString(f, info, x0, y0 + size + 4, Colors.DIM, false);
		if (nearest != Long.MIN_VALUE) {
			g.drawString(f, (ChunkHunter.cx(nearest) * 16 + 8) + " " + (ChunkHunter.cz(nearest) * 16 + 8), x0 + size + 8, y0 + 20, Colors.TEXT, false);
		}
		if (!buds.isEmpty()) Sprites.cluster(g, x0 + size + 8, y0 + 34, 1);
	}
}
