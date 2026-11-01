// PATH: src/main/java/dev/masq/world/ChunkScanner.java
package dev.masq.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Ortak chunk tarayıcı. Chunk listesi, mesafe sıralaması ve tick başına zaman bütçesi
 * tek yerde; her aktif geçiş (ChunkHunter, ClearRender...) chunk'ı bir kez dolaşırken kendi işini yapar.
 * Geçişler kayıt sırasıyla çalışır: tespit önce, dünyayı değiştiren (ClearRender) sonra.
 */
public final class ChunkScanner {
	public interface Pass {
		boolean active();
		int range();
		int sweepTicks();
		int budgetMs();
		void visit(ClientLevel level, LevelChunk chunk, int cx, int cz);
		default void prune(int pcx, int pcz, int range) {}
		default void onReset() {}
	}

	private static final List<Pass> PASSES = new ArrayList<>();
	private static final List<int[]> QUEUE = new ArrayList<>();
	private static int cursor;
	private static int since = 1 << 20;
	private static ClientLevel last;

	private ChunkScanner() {}

	public static void register(Pass p) { PASSES.add(p); }

	public static void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		LocalPlayer pl = mc.player;
		if (lvl == null || pl == null) {
			QUEUE.clear();
			cursor = 0;
			return;
		}
		if (lvl != last) {
			last = lvl;
			QUEUE.clear();
			cursor = 0;
			since = 1 << 20;
			for (Pass p : PASSES) p.onReset();
		}

		List<Pass> act = new ArrayList<>();
		int range = 0, ticks = Integer.MAX_VALUE, budget = 0;
		for (Pass p : PASSES) {
			if (!p.active()) continue;
			act.add(p);
			range = Math.max(range, p.range());
			ticks = Math.min(ticks, p.sweepTicks());
			budget = Math.max(budget, p.budgetMs());
		}
		if (act.isEmpty()) {
			QUEUE.clear();
			cursor = 0;
			return;
		}

		since++;
		if (cursor >= QUEUE.size()) {
			if (since < ticks) return;
			since = 0;
			int pcx = pl.getBlockX() >> 4, pcz = pl.getBlockZ() >> 4;
			QUEUE.clear();
			cursor = 0;
			for (int dx = -range; dx <= range; dx++) {
				for (int dz = -range; dz <= range; dz++) {
					if (lvl.hasChunk(pcx + dx, pcz + dz)) QUEUE.add(new int[] { pcx + dx, pcz + dz, dx * dx + dz * dz });
				}
			}
			QUEUE.sort((a, b) -> Integer.compare(a[2], b[2]));
			for (Pass p : act) p.prune(pcx, pcz, range);
			if (QUEUE.isEmpty()) return;
		}

		long deadline = System.nanoTime() + budget * 1_000_000L;
		while (cursor < QUEUE.size() && System.nanoTime() < deadline) {
			int[] c = QUEUE.get(cursor++);
			if (!lvl.hasChunk(c[0], c[1])) continue;
			LevelChunk chunk = lvl.getChunk(c[0], c[1]);
			for (Pass p : act) {
				if (c[2] <= p.range() * p.range()) p.visit(lvl, chunk, c[0], c[1]);
			}
		}
	}
}
