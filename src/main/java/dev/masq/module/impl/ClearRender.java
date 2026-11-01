// PATH: src/main/java/dev/masq/module/impl/ClearRender.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sweeps ALL loaded chunks about once per second (nearest first) and fills every
 * completely sealed air pocket of at most "max-size" blocks with stone
 * (deepslate under Y 0). Client side only.
 * Sections without any air are skipped, and a time budget per tick keeps it fast
 * without freezing the game.
 * (Orijinal ClearRender mantığı birebir; sadece Meteor ayar/olay API'si MasQ'unkiyle değişti.)
 */
public class ClearRender extends Module {
	private final Minecraft mc = Minecraft.getInstance();

	private final Setting.Num chunkRange = add(new Setting.Num("ChunkRange", 32f, 2f, 32f, 1f));
	private final Setting.Num sweepTicks = add(new Setting.Num("SweepTicks", 20f, 1f, 200f, 1f));
	private final Setting.Num msPerTick = add(new Setting.Num("MsPerTick", 20f, 1f, 45f, 1f));
	private final Setting.Num maxSize = add(new Setting.Num("MaxSize", 5f, 1f, 32f, 1f));
	private final Setting.Num minY = add(new Setting.Num("MinY", -64f, -64f, 320f, 1f));
	private final Setting.Num maxY = add(new Setting.Num("MaxY", 64f, -64f, 320f, 1f));
	private final Setting.Bool debug = add(new Setting.Bool("Debug", false));

	private final List<int[]> queue = new ArrayList<>();
	private int cursor;
	private int sinceSweep;
	private int filledThisSweep;

	public ClearRender() {
		super("ClearRender", "Fills small sealed air pockets in all loaded chunks with stone on your client.", Category.WORLD);
	}

	@Override
	public void onEnable() {
		queue.clear();
		cursor = 0;
		sinceSweep = Integer.MAX_VALUE / 2;
		filledThisSweep = 0;
	}

	@Override
	public void onDisable() {
		queue.clear();
		cursor = 0;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;

		sinceSweep++;

		// sweep finished: start the next one once the interval has passed
		if (cursor >= queue.size()) {
			if (sinceSweep < sweepTicks.i()) return;

			if (debug.get() && filledThisSweep > 0) info("sweep filled %d blocks", filledThisSweep);
			filledThisSweep = 0;
			sinceSweep = 0;
			rebuildQueue();
			if (queue.isEmpty()) return;
		}

		long deadline = System.nanoTime() + msPerTick.i() * 1_000_000L;
		while (cursor < queue.size() && System.nanoTime() < deadline) {
			int[] c = queue.get(cursor++);
			if (!mc.level.hasChunk(c[0], c[1])) continue;
			filledThisSweep += processChunk(c[0], c[1]);
		}
	}

	private void rebuildQueue() {
		queue.clear();
		cursor = 0;

		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		int r = chunkRange.i();

		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int cx = pcx + dx;
				int cz = pcz + dz;
				if (mc.level.hasChunk(cx, cz)) queue.add(new int[]{cx, cz, dx * dx + dz * dz});
			}
		}

		queue.sort((a, b) -> Integer.compare(a[2], b[2]));
	}

	private int processChunk(int cx, int cz) {
		LevelChunk chunk = mc.level.getChunk(cx, cz);
		int lo = Math.max(minY.i(), mc.level.getMinY());
		int hi = Math.min(maxY.i(), mc.level.getMaxY() - 1);
		int max = maxSize.i();
		int bx = cx << 4;
		int bz = cz << 4;
		int minSection = mc.level.getMinY() >> 4;

		Set<Long> visited = new HashSet<>();
		Set<Long> queued = new HashSet<>();
		List<BlockPos> toFill = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < chunk.getSectionsCount(); i++) {
			int secMin = (minSection + i) << 4;
			int secMax = secMin + 15;
			if (secMax < lo || secMin > hi) continue;

			LevelChunkSection section = chunk.getSection(i);
			// fully air = big open space, no air at all = nothing to fill
			if (section.hasOnlyAir() || !section.maybeHas(st -> st.isAir())) continue;

			int yStart = Math.max(secMin, lo);
			int yEnd = Math.min(secMax, hi);

			for (int y = yStart; y <= yEnd; y++) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						pos.set(bx + x, y, bz + z);
						long key = pos.asLong();
						if (visited.contains(key)) continue;
						if (!chunk.getBlockState(pos).isAir()) continue;

						List<BlockPos> pocket = floodFill(pos.immutable(), max, visited);
						if (pocket == null) continue;

						for (BlockPos p : pocket) {
							if (queued.add(p.asLong())) toFill.add(p);
						}
					}
				}
			}
		}

		for (BlockPos p : toFill) {
			Block block = fillBlockFor(p);
			mc.level.setBlock(p, block.defaultBlockState(),
				Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}

		return toFill.size();
	}

	/** Netherrack in the Nether, stone/deepslate everywhere else (deepslate under Y 0). */
	private Block fillBlockFor(BlockPos p) {
		if (mc.level.dimension() == Level.NETHER) return Blocks.NETHERRACK;
		return p.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
	}

	/**
	 * Returns the pocket if it is sealed and small enough, otherwise null.
	 * A pocket is "open" if it grows past max or touches an unloaded chunk.
	 */
	private List<BlockPos> floodFill(BlockPos start, int max, Set<Long> visited) {
		List<BlockPos> region = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start.asLong());
		boolean open = false;

		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			region.add(p);

			if (region.size() > max) {
				open = true;
				break;
			}

			for (Direction dir : Direction.values()) {
				BlockPos n = p.relative(dir);
				long key = n.asLong();
				if (seen.contains(key)) continue;

				// above or below the world counts as a wall
				if (n.getY() < mc.level.getMinY() || n.getY() >= mc.level.getMaxY()) continue;

				if (!mc.level.hasChunkAt(n)) {
					open = true;
					break;
				}

				if (mc.level.getBlockState(n).isAir()) {
					seen.add(key);
					queue.add(n);
				}
			}

			if (open) break;
		}

		visited.addAll(seen);
		return open ? null : region;
	}
}
