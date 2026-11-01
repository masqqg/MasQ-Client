// PATH: src/main/java/dev/masq/module/impl/ChunkHunter.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import dev.masq.render.Render3D;
import dev.masq.world.SusEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ChunkHunter = ClusterBypass (amethyst, aynen korundu) + BetterSusChunk (SusEngine, 19 kanıt türü).
 * Cluster: blok ışığı 1/2/4/5 olup 4 yönde kapalı hücreler "bud adayı"; chunk'ta MinBuds'a ulaşınca işaretlenir.
 * Sus: kimsenin yakınında bulunmadığı chunk'lar. İkisi aynı chunk'ta çakışırsa altın çerçeveli "BOTH" olur.
 * Orijinal blok-durumu (deepslate) taraması scanChunk() olarak değişmeden duruyor.
 */
public final class ChunkHunter extends Module {
	public static ChunkHunter I;

	private static final Predicate<BlockState> BUDDING = st -> st.is(Blocks.BUDDING_AMETHYST);
	private static final int MIN_OPEN_SIDES = 2;

	/**
	 * 16x16 amethyst cluster pixel art. One character = one block of the chunk floor
	 * (column = +X / east, row = +Z / south). '.' is transparent, 'A'..'G' index SPRITE_PALETTE.
	 */
	private static final int[] SPRITE_PALETTE = {
		0x7658B0, 0xCC9AF1, 0x8B68CC, 0x60449B, 0xFEC9E5, 0xAF88F2, 0xFEFDD1
	};
	private static final String[] SPRITE = {
		"................",
		".......B........",
		"......BEF.......",
		".....BBECB......",
		"....BBBECCB.....",
		"....BEGGFCB.....",
		"....AFFBACA.....",
		"....ACFBACD.....",
		"EEB..ACBAAD..BEE",
		"BEEB.AAFAAD.BEEB",
		"BCGBBDACFA.BBGCB",
		"BCCEADAAFDCCBCCB",
		".BCABADACDABACB.",
		"..BAAAADCDCAAC..",
		"...CADADBCDAC...",
		"...ADDDCBDDDA..."
	};

	// ClusterBypass varsayılan renkleri (ARGB)
	private static final int SIDE = Render3D.argb(55, 170, 0, 255);
	private static final int LINE = Render3D.argb(180, 255, 80, 255);
	private static final int GROWTH_SIDE = Render3D.argb(25, 255, 150, 220);
	private static final int GROWTH_LINE = Render3D.argb(90, 255, 180, 230);
	private static final int SUS_FILL = Render3D.argb(100, 255, 0, 0);
	private static final int SUS_LINE = Render3D.argb(255, 255, 0, 0);
	private static final int BOTH_LINE = Render3D.argb(255, 255, 211, 106);

	private final Minecraft mc = Minecraft.getInstance();

	// ---- Cluster (ClusterBypass ayarları) ----
	private final Setting.Num chunkRange = add(new Setting.Num("ChunkRange", 16f, 1f, 32f, 1f));
	private final Setting.Num minimum = add(new Setting.Num("MinBuds", 17f, 1f, 200f, 1f));
	private final Setting.Num minY = add(new Setting.Num("MinY", -64f, -64f, 320f, 1f));
	private final Setting.Num maxY = add(new Setting.Num("MaxY", 64f, -64f, 320f, 1f));
	private final Setting.Num sweepTicks = add(new Setting.Num("SweepTicks", 20f, 1f, 200f, 1f));
	private final Setting.Num msPerTick = add(new Setting.Num("MsPerTick", 8f, 1f, 40f, 1f));
	private final Setting.Bool debug = add(new Setting.Bool("Debug", false));
	private final Setting.Num markerY = add(new Setting.Num("MarkerY", 64f, -64f, 320f, 1f));
	private final Setting.Bool spriteMarker = add(new Setting.Bool("SpriteMarker", true));
	private final Setting.Bool showGrowthArea = add(new Setting.Bool("ShowGrowthArea", true));
	private final Setting.Num growthRadius = add(new Setting.Num("GrowthRadius", 1f, 1f, 16f, 1f));

	// ---- Sus (BetterSusChunk ayarları) ----
	private final Setting.Bool sus = add(new Setting.Bool("SusChunks", true));
	private final Setting.Num simDistance = add(new Setting.Num("SimDistance", 4f, 2f, 16f, 1f));
	private final Setting.Num sensitivity = add(new Setting.Num("Sensitivity", 40f, 1f, 500f, 1f));
	private final Setting.Bool holeFilter = add(new Setting.Bool("HoleFilter", false));
	private final Setting.Num holeDistance = add(new Setting.Num("HoleDistance", 2f, 0f, 8f, 1f));
	private final Setting.Bool customFlags = add(new Setting.Bool("CustomFlags", false));
	private final Setting.Bool[] flags = new Setting.Bool[SusEngine.KINDS];

	// ---- Gösterim ----
	private final Setting.Bool map = add(new Setting.Bool("Map", true));
	private final Setting.Bool chat = add(new Setting.Bool("Chat", true));

	/** chunk key -> unique positions classified as light-data bud candidates */
	private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
	private final List<int[]> queue = new ArrayList<>();
	private final SusEngine engine = new SusEngine();
	private final Set<Long> announced = new HashSet<>();
	private volatile Set<Long> budSet = Set.of(), susSet = Set.of();
	private ClientLevel lastLevel;
	private int cursor;
	private int sinceSweep;
	private int tick;

	public ChunkHunter() {
		super("ChunkHunter", "Amethyst cluster + sus chunk finder", Category.WORLD);
		I = this;
		holeDistance.showIf(holeFilter::get);
		for (int i = 0; i < flags.length; i++) {
			flags[i] = add(new Setting.Bool(SusEngine.NAMES[i], true));
			flags[i].showIf(customFlags::get);
		}
		Render3D.add(this::render3d);
	}

	public static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
	public static int cx(long k) { return (int) (k >> 32); }
	public static int cz(long k) { return (int) k; }

	// HUD haritası bunları okur
	public Set<Long> budSet() { return budSet; }
	public Set<Long> susSet() { return susSet; }
	public boolean mapOn() { return map.get(); }
	public int rangeI() { return chunkRange.i(); }

	@Override
	public void onEnable() {
		chunks.clear();
		queue.clear();
		announced.clear();
		budSet = Set.of();
		susSet = Set.of();
		cursor = 0;
		sinceSweep = Integer.MAX_VALUE / 2;
		tick = 0;
	}

	@Override
	public void onDisable() {
		chunks.clear();
		queue.clear();
		cursor = 0;
		budSet = Set.of();
		susSet = Set.of();
		if (engine.running()) engine.stop();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;

		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			chunks.clear();
			queue.clear();
			cursor = 0;
			announced.clear();
		}

		tickSus();

		sinceSweep++;

		if (cursor >= queue.size()) {
			if (sinceSweep >= sweepTicks.i()) {
				sinceSweep = 0;
				rebuildQueue();
			}
		}

		if (cursor < queue.size()) {
			long deadline = System.nanoTime() + msPerTick.i() * 1_000_000L;
			while (cursor < queue.size() && System.nanoTime() < deadline) {
				int[] c = queue.get(cursor++);
				if (!mc.level.hasChunk(c[0], c[1])) continue;

				long k = key(c[0], c[1]);
				// Keep the original block-state scan intact; the active result now comes
				// from the separate light-data heuristic below.
				Set<BlockPos> found = scanLightData(c[0], c[1]);

				if (debug.get()) {
					Set<BlockPos> old = chunks.get(k);
					int oldSize = old == null ? 0 : old.size();
					if (found.size() != oldSize) {
						info("chunk %d,%d: bud-candidates=%d", c[0], c[1], found.size());
					}
				}

				if (found.isEmpty()) chunks.remove(k);
				else chunks.put(k, found); // replaced as a whole = no flicker
			}
		}

		if (++tick >= 20) {
			tick = 0;
			refreshSets();
		}
	}

	private void tickSus() {
		if (!sus.get()) {
			if (engine.running()) engine.stop();
			susSet = Set.of();
			return;
		}
		boolean[] kinds = new boolean[SusEngine.KINDS];
		for (int i = 0; i < kinds.length; i++) kinds[i] = !customFlags.get() || flags[i].get();
		engine.configure(simDistance.i(), sensitivity.i(), holeFilter.get(), holeDistance.i(), kinds);
		if (!engine.running()) engine.start();
		engine.tick(mc.level, mc.player);
	}

	/** Gösterim kümeleri (HUD haritası + sohbet bildirimi). */
	private void refreshSets() {
		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		int r = chunkRange.i();

		Set<Long> b = new HashSet<>();
		for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
			long k = e.getKey();
			if (Math.abs(cx(k) - pcx) > r || Math.abs(cz(k) - pcz) > r) continue;
			if (e.getValue().size() >= minimum.i()) b.add(k);
		}
		Set<Long> s = new HashSet<>();
		if (sus.get()) for (long k : engine.flagged()) s.add(k);
		budSet = b;
		susSet = s;

		if (chat.get()) {
			for (long k : b) announce(k, s.contains(k) ? "Cluster+Sus" : "Cluster", 0);
			for (long k : s) if (!b.contains(k)) announce(k, "Sus chunk", 1);
		}
	}

	private void announce(long k, String label, int salt) {
		if (!announced.add(k * 2 + salt)) return;
		int x = cx(k) * 16 + 8, z = cz(k) * 16 + 8;
		int d = (int) Math.sqrt(Math.pow(x - mc.player.getX(), 2) + Math.pow(z - mc.player.getZ(), 2));
		info("%s %d %d (%dm)", label, x, z, d);
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

		chunks.keySet().removeIf(k -> {
			int cx = (int) (k >> 32);
			int cz = (int) (long) k;
			return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !mc.level.hasChunk(cx, cz);
		});
	}

	/**
	 * Separate light-data heuristic. Amethyst growth blocks emit block-light levels
	 * 1, 2, 4 and 5. A position with one of those levels is counted only when all
	 * four horizontal neighbors are occluding (closed), matching the requested
	 * four-direction shell check. This is a heuristic, not proof of a real bud:
	 * overlapping light and server-side light masking can cause false results.
	 */
	private Set<BlockPos> scanLightData(int cx, int cz) {
		Set<BlockPos> buds = new HashSet<>();
		int lo = Math.max(minY.i(), mc.level.getMinY());
		int hi = Math.min(maxY.i(), mc.level.getMaxY() - 1);
		int bx = cx << 4;
		int bz = cz << 4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

		for (int y = lo; y <= hi; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					pos.set(bx + x, y, bz + z);
					int light = mc.level.getBrightness(LightLayer.BLOCK, pos);
					if (light != 1 && light != 2 && light != 4 && light != 5) continue;

					boolean closed = true;
					for (Direction dir : horizontal) {
						BlockPos neighbor = pos.relative(dir);
						if (!mc.level.hasChunkAt(neighbor)
							|| !mc.level.getBlockState(neighbor).canOcclude()) {
							closed = false;
							break;
						}
					}

					if (closed) buds.add(pos.immutable());
				}
			}
		}
		return buds;
	}

	@SuppressWarnings("unused") // orijinal yöntem referans için duruyor
	private Set<BlockPos> scanChunk(int cx, int cz) {
		Set<BlockPos> deepslate = new HashSet<>();
		LevelChunk chunk = mc.level.getChunk(cx, cz);
		int lo = Math.max(minY.i(), mc.level.getMinY());
		int hi = Math.min(maxY.i(), mc.level.getMaxY() - 1);
		int minSection = mc.level.getMinY() >> 4;
		int bx = cx << 4;
		int bz = cz << 4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < chunk.getSectionsCount(); i++) {
			int secMin = (minSection + i) << 4;
			int secMax = secMin + 15;
			if (secMax < lo || secMin > hi) continue;

			LevelChunkSection section = chunk.getSection(i);
			if (section.hasOnlyAir() || !section.maybeHas(BUDDING)) continue;

			int yStart = Math.max(secMin, lo);
			int yEnd = Math.min(secMax, hi);

			for (int y = yStart; y <= yEnd; y++) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						BlockState st = section.getBlockState(x, y - secMin, z);
						if (!st.is(Blocks.BUDDING_AMETHYST)) continue;

						pos.set(bx + x, y, bz + z);
						for (Direction dir : Direction.values()) {
							BlockPos n = pos.relative(dir);
							if (n.getY() < lo || n.getY() > hi) continue;
							if (!mc.level.hasChunkAt(n)) continue;
							if (!mc.level.getBlockState(n).is(Blocks.DEEPSLATE)) continue;

							BlockPos imm = n.immutable();
							// skip deepslate that's fully boxed in (likely anti-cheat filler,
							// not part of the real geode shell) - needs at least 2 open faces
							if (!deepslate.contains(imm) && countOpenSides(imm) < MIN_OPEN_SIDES) continue;
							deepslate.add(imm);
						}
					}
				}
			}
		}
		return deepslate;
	}

	/** Number of the 6 faces of this block that are NOT solid (air, amethyst bud, etc). */
	private int countOpenSides(BlockPos pos) {
		int open = 0;
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			if (!mc.level.hasChunkAt(n)) continue;
			if (!mc.level.getBlockState(n).canOcclude()) open++;
		}
		return open;
	}

	// ---- 3D çizim (ClusterBypass onRender + sus karoları) ----

	private void render3d() {
		if (!enabled() || mc.level == null || mc.player == null) return;
		Set<Long> sus = susSet;
		if (chunks.isEmpty() && sus.isEmpty()) return;

		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		int r = chunkRange.i();
		double y = markerY.i();
		int min = minimum.i();

		// draw the light-pink growth squares first, so the purple marker draws on top
		if (showGrowthArea.get()) {
			int gr = growthRadius.i();
			Set<Long> drawn = new HashSet<>();

			for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
				if (e.getValue().size() < min) continue;

				long k = e.getKey();
				int cx = cx(k);
				int cz = cz(k);
				if (Math.abs(cx - pcx) > r + gr || Math.abs(cz - pcz) > r + gr) continue;

				for (int dx = -gr; dx <= gr; dx++) {
					for (int dz = -gr; dz <= gr; dz++) {
						int ncx = cx + dx;
						int ncz = cz + dz;
						if (!drawn.add(key(ncx, ncz))) continue; // already drawn for another nearby cluster

						double x0 = ncx * 16.0, z0 = ncz * 16.0;
						Render3D.box(x0, y - 0.05, z0, x0 + 16.0, y - 0.05 + 0.3, z0 + 16.0, GROWTH_SIDE);
						Render3D.edges(x0, y - 0.05, z0, x0 + 16.0, y - 0.05 + 0.3, z0 + 16.0, 0.06, GROWTH_LINE);
					}
				}
			}
		}

		for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
			long k = e.getKey();
			int cx = cx(k);
			int cz = cz(k);

			if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;
			if (e.getValue().size() < min) continue;

			double x0 = cx * 16.0, z0 = cz * 16.0;
			if (spriteMarker.get()) {
				// keep the chunk border visible, then fill it with the pixel art
				Render3D.edges(x0, y, z0, x0 + 16.0, y + 0.5, z0 + 16.0, 0.08, LINE);
				renderSprite(cx, cz, y);
			} else {
				Render3D.box(x0, y, z0, x0 + 16.0, y + 0.5, z0 + 16.0, SIDE);
				Render3D.edges(x0, y, z0, x0 + 16.0, y + 0.5, z0 + 16.0, 0.08, LINE);
			}
			if (sus.contains(k)) Render3D.edges(x0, y, z0, x0 + 16.0, y + 4.0, z0 + 16.0, 0.15, BOTH_LINE);
		}

		int sr = mc.options.getEffectiveRenderDistance() + 2;
		for (long k : sus) {
			int cx = cx(k), cz = cz(k);
			if (Math.abs(cx - pcx) > sr || Math.abs(cz - pcz) > sr) continue;
			double x0 = cx * 16.0, z0 = cz * 16.0;
			Render3D.box(x0, y, z0, x0 + 16.0, y + 0.3, z0 + 16.0, SUS_FILL);
			Render3D.edges(x0, y, z0, x0 + 16.0, y + 0.3, z0 + 16.0, 0.08, SUS_LINE);
		}
	}

	/** Draws the pixel art as 16x16 colored 1x0.5x1 slabs covering the chunk floor. */
	private void renderSprite(int cx, int cz, double y) {
		for (int row = 0; row < SPRITE.length; row++) {
			String line = SPRITE[row];
			for (int col = 0; col < line.length(); col++) {
				char ch = line.charAt(col);
				if (ch == '.') continue;

				int c = 0xFF000000 | SPRITE_PALETTE[ch - 'A'];
				double x = cx * 16.0 + col;
				double z = cz * 16.0 + row;
				Render3D.slab(x, y, z, x + 1.0, y + 0.5, z + 1.0, c);
			}
		}
	}
}
