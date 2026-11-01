// PATH: src/main/java/dev/masq/world/SusEngine.java
package dev.masq.world;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluids;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BetterSusChunk motoru (Mojang isimleriyle yeniden yazıldı, mantık aynı, 19 kanıt türünün hepsi var).
 * Kimsenin yakınında bulunmadığı chunk'ları işaretler: büyümesi gecikmiş (owed) bloklar, çevresindeki
 * alanı "elenmiş" yapar; en az 3 yandan elenmiş alanlarla çevrili chunk şüpheli sayılır.
 * Tarama arka plan thread'inde yapılır. Chunk yükleme olayı yerine tick'te yeni chunk'lar yoklanır.
 * Bilinen eksik: Donut'un kelp blok-güncelleme takibi mixin ister; onBlockUpdate() hazır, bağlanmadı.
 */
public final class SusEngine {
	private static final String DONUT_BRAND = "DonutFolia";
	public static final int KINDS = 19;
	public static final String[] NAMES = {
		"Kelp", "CaveVines", "WeepingVines", "Bamboo", "SugarCane", "Cactus", "Cocoa", "SweetBerries", "NetherWart",
		"Crops", "Stems", "Amethyst", "Vines", "Leaves", "GrassDirt", "Propagules", "Eyeblossoms", "Snow", "Ice"
	};
	private static final int KELP = 0, CAVE_VINES = 1, WEEPING_VINES = 2, BAMBOO = 3, SUGAR_CANE = 4, CACTUS = 5,
		COCOA = 6, BERRY = 7, NETHER_WART = 8, CROP = 9, STEM = 10, AMETHYST = 11, VINE = 12, LEAVES = 13,
		SOIL = 14, PROPAGULE = 15, EYEBLOSSOM = 16, SNOW = 17, ICE = 18;
	private static final int GROWTH_LIGHT = 9, BAMBOO_LIMIT = 16, SHORT_COLUMN = 3, KELP_MAX_AGE = 25;
	private static final int DAY_TICKS = 24000, EYEBLOSSOM_OPENS = 12600, EYEBLOSSOM_CLOSES = 23401, EYEBLOSSOM_SETTLE = 4000;
	private static final int EYEBLOSSOM_MIN = 3, ENCLOSED_SIDES = 3, REFRESH_TICKS = 20, LOADED_MARGIN = 3;
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	private record Source(boolean clears, boolean hole, int weight) {}

	private record Rec(boolean clears, int radius, boolean hole, int weight) {}

	// ayarlar (ChunkHunter her tick configure() ile günceller)
	private volatile int simDistance = 4, sensitivity = 40, holeDistance = 2;
	private volatile boolean holeFilter;
	private volatile boolean[] counted = new boolean[KINDS];

	private final Object lock = new Object();
	private final Long2ObjectOpenHashMap<Rec> records = new Long2ObjectOpenHashMap<>();
	private final Long2IntOpenHashMap clearCoverage = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap support = new Long2IntOpenHashMap();
	private final LongOpenHashSet scanned = new LongOpenHashSet();
	private final LongArrayList distant = new LongArrayList();
	private final Set<Long> pending = ConcurrentHashMap.newKeySet();
	private final Set<Long> kelpChunks = ConcurrentHashMap.newKeySet();
	private final Set<Long> seenKelp = ConcurrentHashMap.newKeySet();
	private volatile LongSet primary = LongSets.EMPTY_SET;
	private volatile int generation;
	private volatile boolean dirty;
	private ExecutorService executor;
	private ResourceKey<Level> worldKey;
	private int tickCounter, pollCounter;

	public SusEngine() {
		java.util.Arrays.fill(counted, true);
	}

	public boolean running() { return executor != null; }

	public LongSet flagged() { return LongSets.unmodifiable(primary); }

	public void configure(int sim, int sens, boolean holes, int holeDist, boolean[] kinds) {
		if (sim != simDistance || holes != holeFilter || !java.util.Arrays.equals(kinds, counted)) dirty = true;
		simDistance = sim;
		sensitivity = sens;
		holeFilter = holes;
		holeDistance = holeDist;
		counted = kinds.clone();
	}

	public void start() {
		resetState();
		executor = Executors.newSingleThreadExecutor(task -> {
			Thread t = new Thread(task, "MasQ Sus Chunk");
			t.setDaemon(true);
			return t;
		});
		ClientLevel level = Minecraft.getInstance().level;
		worldKey = level == null ? null : level.dimension();
		dirty = false;
		rescanLoadedChunks();
	}

	public void stop() {
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		resetState();
		worldKey = null;
	}

	private void resetState() {
		synchronized (lock) {
			++generation;
			clearCoverage.clear();
			support.clear();
			records.clear();
			scanned.clear();
			pending.clear();
			kelpChunks.clear();
			seenKelp.clear();
		}
		primary = LongSets.EMPTY_SET;
		tickCounter = 0;
	}

	private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
	private static int kx(long k) { return (int) (k >> 32); }
	private static int kz(long k) { return (int) k; }

	private void rescanLoadedChunks() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (executor != null && mc.player != null && level != null) {
			resetState();
			int radius = mc.options.getEffectiveRenderDistance() + LOADED_MARGIN;
			int cx0 = mc.player.getBlockX() >> 4, cz0 = mc.player.getBlockZ() >> 4;
			for (int x = cx0 - radius; x <= cx0 + radius; ++x) {
				for (int z = cz0 - radius; z <= cz0 + radius; ++z) {
					if (level.hasChunk(x, z)) scheduleScan(key(x, z));
				}
			}
		}
	}

	/** Chunk paketi olayı yerine: yeni yüklenen, henüz taranmamış chunk'ları bulup sıraya at. */
	private void pollNewChunks(Minecraft mc, ClientLevel level) {
		int radius = mc.options.getEffectiveRenderDistance() + LOADED_MARGIN;
		int cx0 = mc.player.getBlockX() >> 4, cz0 = mc.player.getBlockZ() >> 4;
		for (int x = cx0 - radius; x <= cx0 + radius; ++x) {
			for (int z = cz0 - radius; z <= cz0 + radius; ++z) {
				long k = key(x, z);
				if (!level.hasChunk(x, z) || pending.contains(k)) continue;
				boolean known;
				synchronized (lock) { known = scanned.contains(k); }
				if (!known) scheduleScan(k);
			}
		}
	}

	private void scheduleScan(long key) {
		ExecutorService worker = executor;
		if (worker != null && !worker.isShutdown() && pending.add(key)) {
			int expected = generation;
			worker.submit(() -> runScan(key, expected));
		}
	}

	private void runScan(long key, int expected) {
		pending.remove(key);
		if (expected != generation || executor == null) return;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return;
		int cx = kx(key), cz = kz(key);
		if (!level.hasChunk(cx, cz)) return;
		LevelChunk chunk = level.getChunk(cx, cz);
		if (chunk != null) rescan(level, key, chunk, expected);
	}

	private void rescan(ClientLevel level, long key, LevelChunk chunk, int expected) {
		Rec old = removeRecord(key);
		Source fresh = scanChunk(level, key, chunk);
		synchronized (lock) {
			if (expected != generation) return;
			Rec merged = new Rec(fresh.clears() || old != null && old.clears(), simDistance,
				fresh.hole() || old != null && old.hole(), Math.max(fresh.weight(), old == null ? 0 : old.weight()));
			scanned.add(key);
			records.put(key, merged);
			if (!merged.clears()) return;
			int cx = kx(key), cz = kz(key), reach = 2 * merged.radius() + 2;
			for (int dx = -merged.radius(); dx <= merged.radius(); ++dx) {
				for (int dz = -merged.radius(); dz <= merged.radius(); ++dz) clearCoverage.addTo(key(cx + dx, cz + dz), 1);
			}
			for (int dx = -reach; dx <= reach; ++dx) {
				for (int dz = -reach; dz <= reach; ++dz) support.addTo(key(cx + dx, cz + dz), merged.weight());
			}
		}
	}

	private Rec removeRecord(long key) {
		synchronized (lock) {
			Rec old = records.remove(key);
			scanned.remove(key);
			if (old != null && old.clears()) {
				int cx = kx(key), cz = kz(key), reach = 2 * old.radius() + 2;
				for (int dx = -old.radius(); dx <= old.radius(); ++dx) {
					for (int dz = -old.radius(); dz <= old.radius(); ++dz) subtract(clearCoverage, key(cx + dx, cz + dz), 1);
				}
				for (int dx = -reach; dx <= reach; ++dx) {
					for (int dz = -reach; dz <= reach; ++dz) subtract(support, key(cx + dx, cz + dz), old.weight());
				}
			}
			return old;
		}
	}

	private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
		if (!map.containsKey(key)) return;
		int value = map.get(key);
		if (value <= amount) map.remove(key); else map.put(key, value - amount);
	}

	// ---- chunk tarama ----

	private Source scanChunk(ClientLevel level, long key, LevelChunk chunk) {
		boolean donut = donutServer();
		boolean[] counts = counted;
		int count = donut && counts[KELP] && kelpChunks.contains(key) ? 1 : 0;
		BlockReader blocks = new BlockReader(level);
		int minX = kx(key) << 4, minZ = kz(key) << 4;
		Boolean bloomsOpen = eyeblossomsOpen(level);
		int lateBlooms = 0, settledBlooms = 0;
		LevelChunkSection[] sections = chunk.getSections();

		for (int i = 0; i < sections.length; ++i) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> kindOf(st, counts) >= 0)) continue;
			int baseY = chunk.getMinY() + (i << 4);
			for (int y = 0; y < 16; ++y) {
				for (int z = 0; z < 16; ++z) {
					for (int x = 0; x < 16; ++x) {
						BlockState state = section.getBlockState(x, y, z);
						int kind = kindOf(state, counts);
						if (kind < 0) continue;
						if (kind == PROPAGULE) {
							if (state.getValue(BlockStateProperties.AGE_4) < 4) ++count;
						} else if (kind == EYEBLOSSOM) {
							if (bloomsOpen != null) {
								if (state.is(Blocks.OPEN_EYEBLOSSOM) == bloomsOpen) ++settledBlooms; else ++lateBlooms;
							}
						} else if (owed(blocks, minX + x, baseY + y, minZ + z, state, kind, donut)) {
							++count;
						}
					}
				}
			}
		}
		if (settledBlooms == 0 && lateBlooms >= EYEBLOSSOM_MIN) count += lateBlooms;

		if ((counts[SNOW] || counts[ICE]) && chunk.hasPrimedHeightmap(Heightmap.Types.MOTION_BLOCKING)) {
			Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
			int seaLevel = level.getSeaLevel();
			for (int z = 0; z < 16; ++z) {
				for (int x = 0; x < 16; ++x) {
					int y = heightmap.getFirstAvailable(x, z);
					if (surfaceOwed(level, blocks, minX + x, y, minZ + z, seaLevel, donut, counts)) ++count;
				}
			}
		}

		boolean hole = holeFilter && hasHole(level, minX, minZ);
		return new Source(count > 0, hole, count);
	}

	private static int kindOf(BlockState state, boolean[] counts) {
		int kind = classify(state);
		return kind >= 0 && counts[kind] ? kind : -1;
	}

	private static int classify(BlockState state) {
		Block b = state.getBlock();
		if (b == Blocks.KELP) return KELP;
		if (b == Blocks.CAVE_VINES) return CAVE_VINES;
		if (b == Blocks.WEEPING_VINES) return WEEPING_VINES;
		if (b == Blocks.BAMBOO) return BAMBOO;
		if (b == Blocks.SUGAR_CANE) return SUGAR_CANE;
		if (b == Blocks.CACTUS) return CACTUS;
		if (b == Blocks.COCOA) return COCOA;
		if (b == Blocks.SWEET_BERRY_BUSH) return BERRY;
		if (b == Blocks.NETHER_WART) return NETHER_WART;
		if (b == Blocks.PUMPKIN_STEM || b == Blocks.MELON_STEM) return STEM;
		if (b == Blocks.VINE) return VINE;
		if (b == Blocks.BUDDING_AMETHYST || b == Blocks.AMETHYST_CLUSTER || isBud(state)) return AMETHYST;
		if (b == Blocks.MANGROVE_PROPAGULE) return state.getValue(BlockStateProperties.HANGING) ? PROPAGULE : -1;
		if (b == Blocks.OPEN_EYEBLOSSOM || b == Blocks.CLOSED_EYEBLOSSOM) return EYEBLOSSOM;
		if (b instanceof CropBlock) return CROP;
		if (b instanceof LeavesBlock) return LEAVES;
		return isGrass(state) || state.is(Blocks.DIRT) ? SOIL : -1;
	}

	private static Boolean eyeblossomsOpen(ClientLevel level) {
		if (level.dimension() != Level.OVERWORLD || level.dimensionType().hasFixedTime()) return null;
		int time = Math.floorMod(level.getDayTime(), DAY_TICKS);
		boolean open = time >= EYEBLOSSOM_OPENS && time < EYEBLOSSOM_CLOSES;
		int since = open ? time - EYEBLOSSOM_OPENS : Math.floorMod(time - EYEBLOSSOM_CLOSES, DAY_TICKS);
		return since >= EYEBLOSSOM_SETTLE ? open : null;
	}

	private static boolean owed(BlockReader b, int x, int y, int z, BlockState state, int kind, boolean donut) {
		if (kind == SOIL) return soilOwed(b, x, y, z, state);
		if (donut) {
			if (kind == KELP) return b.state(x, y + 1, z).is(Blocks.WATER) && column(b, x, y, z, Blocks.KELP_PLANT, SHORT_COLUMN) < SHORT_COLUMN;
			if (kind == AMETHYST) return isBud(state);
			if (kind == CAVE_VINES) return false;
		}
		return growthOwed(b, x, y, z, state);
	}

	private static boolean growthOwed(BlockReader b, int x, int y, int z, BlockState s) {
		Block block = s.getBlock();
		if (block == Blocks.KELP) return s.getValue(BlockStateProperties.AGE_25) < KELP_MAX_AGE && b.state(x, y + 1, z).is(Blocks.WATER);
		if (block == Blocks.CAVE_VINES || block == Blocks.WEEPING_VINES) {
			return s.getValue(BlockStateProperties.AGE_25) < KELP_MAX_AGE && b.state(x, y - 1, z).isAir();
		}
		if (block == Blocks.BAMBOO) {
			return s.getValue(BlockStateProperties.STAGE) == 0 && b.state(x, y + 1, z).isAir()
				&& bambooBelow(b, x, y, z) + 1 < BAMBOO_LIMIT && b.light(x, y + 1, z) >= GROWTH_LIGHT;
		}
		if (block == Blocks.SUGAR_CANE) return b.state(x, y + 1, z).isAir() && column(b, x, y, z, Blocks.SUGAR_CANE, SHORT_COLUMN) < SHORT_COLUMN;
		if (block == Blocks.CACTUS) {
			return b.state(x, y + 1, z).isAir()
				&& (s.getValue(BlockStateProperties.AGE_15) < 15 || column(b, x, y, z, Blocks.CACTUS, SHORT_COLUMN) < SHORT_COLUMN);
		}
		if (block == Blocks.COCOA) return s.getValue(BlockStateProperties.AGE_2) < 2;
		if (block == Blocks.NETHER_WART) return s.getValue(BlockStateProperties.AGE_3) < 3;
		if (block == Blocks.SWEET_BERRY_BUSH) return s.getValue(BlockStateProperties.AGE_3) < 3 && b.light(x, y + 1, z) >= GROWTH_LIGHT;
		if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
			return s.getValue(BlockStateProperties.AGE_7) < 7 && b.light(x, y, z) >= GROWTH_LIGHT;
		}
		if (block instanceof CropBlock crop) return !crop.isMaxAge(s) && b.light(x, y, z) >= GROWTH_LIGHT;
		if (block == Blocks.VINE) {
			return b.state(x, y - 1, z).isAir() && (s.getValue(BlockStateProperties.NORTH) || s.getValue(BlockStateProperties.EAST)
				|| s.getValue(BlockStateProperties.SOUTH) || s.getValue(BlockStateProperties.WEST));
		}
		if (!(block instanceof LeavesBlock)) return amethystOwed(b, x, y, z, s);
		return s.hasProperty(BlockStateProperties.DISTANCE) && s.hasProperty(BlockStateProperties.PERSISTENT)
			&& s.getValue(BlockStateProperties.DISTANCE) == 7 && !s.getValue(BlockStateProperties.PERSISTENT);
	}

	private static boolean amethystOwed(BlockReader b, int x, int y, int z, BlockState state) {
		if (state.is(Blocks.BUDDING_AMETHYST)) {
			for (Direction d : DIRECTIONS) {
				BlockState near = b.state(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
				if (near.isAir()) return true;
				if (near.is(Blocks.WATER) && near.getFluidState().getAmount() == 8) return true;
			}
			return false;
		}
		if (isBud(state) && state.hasProperty(BlockStateProperties.FACING)) {
			Direction f = state.getValue(BlockStateProperties.FACING);
			return b.state(x - f.getStepX(), y - f.getStepY(), z - f.getStepZ()).is(Blocks.BUDDING_AMETHYST);
		}
		return false;
	}

	private static int column(BlockReader b, int x, int y, int z, Block target, int limit) {
		int length = 1;
		while (length < limit && b.state(x, y - length, z).is(target)) ++length;
		return length;
	}

	private static int bambooBelow(BlockReader b, int x, int y, int z) {
		int below = 0;
		while (below < BAMBOO_LIMIT && b.state(x, y - below - 1, z).is(Blocks.BAMBOO)) ++below;
		return below;
	}

	private static boolean soilOwed(BlockReader b, int x, int y, int z, BlockState state) {
		BlockState upper = b.state(x, y + 1, z);
		if (isGrass(state)) return smothers(upper);
		if (!state.is(Blocks.DIRT) || smothers(upper) || upper.getFluidState().is(FluidTags.WATER)) return false;
		for (int dx = -1; dx <= 1; ++dx) {
			for (int dz = -1; dz <= 1; ++dz) {
				for (int ny = y - 1; ny <= y + 3; ++ny) {
					if ((dx != 0 || dz != 0 || ny != y) && isGrass(b.state(x + dx, ny, z + dz))
						&& !smothers(b.state(x + dx, ny + 1, z + dz)) && b.light(x + dx, ny + 1, z + dz) >= GROWTH_LIGHT) return true;
				}
			}
		}
		return false;
	}

	private static boolean smothers(BlockState upper) {
		if (upper.is(Blocks.SNOW) && upper.getValue(SnowLayerBlock.LAYERS) == 1) return false;
		if (upper.getFluidState().getAmount() == 8) return true;
		return LightEngine.getLightBlockInto(Blocks.GRASS_BLOCK.defaultBlockState(), upper, Direction.UP, upper.getLightBlock()) >= 15;
	}

	private static boolean surfaceOwed(ClientLevel level, BlockReader b, int x, int y, int z, int seaLevel, boolean donut, boolean[] counts) {
		boolean snow = counts[SNOW] && (!donut || !b.state(x, y - 1, z).is(Blocks.DEEPSLATE));
		return snow && snowOwed(level, b, x, y, z, seaLevel) || counts[ICE] && iceOwed(level, b, x, y, z, seaLevel);
	}

	private static boolean snowOwed(ClientLevel level, BlockReader b, int x, int y, int z, int seaLevel) {
		if (level.isOutsideBuildHeight(y) || !b.state(x, y, z).isAir()) return false;
		if (!holdsSnow(level, b.state(x, y - 1, z), new BlockPos(x, y - 1, z))) return false;
		BlockPos pos = new BlockPos(x, y, z);
		return level.getBiome(pos).value().getPrecipitationAt(pos, seaLevel) == Biome.Precipitation.SNOW && calmLight(level, b, x, y, z);
	}

	private static boolean holdsSnow(ClientLevel level, BlockState below, BlockPos pos) {
		if (below.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) return false;
		if (below.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) return true;
		if (below.is(Blocks.SNOW) && below.getValue(SnowLayerBlock.LAYERS) == 8) return true;
		return Block.isFaceFull(below.getCollisionShape(level, pos), Direction.UP);
	}

	private static boolean iceOwed(ClientLevel level, BlockReader b, int x, int y, int z, int seaLevel) {
		int water = y - 1;
		if (level.isOutsideBuildHeight(water)) return false;
		BlockState state = b.state(x, water, z);
		if (!(state.getBlock() instanceof LiquidBlock) || state.getFluidState().getType() != Fluids.WATER) return false;
		if (!(level.hasChunk(x - 1 >> 4, z >> 4) && level.hasChunk(x + 1 >> 4, z >> 4)
			&& level.hasChunk(x >> 4, z - 1 >> 4) && level.hasChunk(x >> 4, z + 1 >> 4))) return false;
		if (isWater(b, x - 1, water, z) && isWater(b, x + 1, water, z) && isWater(b, x, water, z - 1) && isWater(b, x, water, z + 1)) return false;
		return level.getBiome(new BlockPos(x, y, z)).value().warmEnoughToRain(new BlockPos(x, water, z), seaLevel) ? false : calmLight(level, b, x, water, z);
	}

	private static boolean isWater(BlockReader b, int x, int y, int z) {
		return b.state(x, y, z).getFluidState().is(FluidTags.WATER);
	}

	private static boolean calmLight(ClientLevel level, BlockReader b, int x, int y, int z) {
		int radius = 5;
		for (int cx = x - radius >> 4; cx <= x + radius >> 4; ++cx) {
			for (int cz = z - radius >> 4; cz <= z + radius >> 4; ++cz) {
				if (!level.hasChunk(cx, cz)) return false;
			}
		}
		for (int dx = -radius; dx <= radius; ++dx) {
			int restY = radius - Math.abs(dx);
			for (int dy = -restY; dy <= restY; ++dy) {
				int restZ = restY - Math.abs(dy);
				for (int dz = -restZ; dz <= restZ; ++dz) {
					int emission = b.state(x + dx, y + dy, z + dz).getLightEmission();
					if (emission != 0 && emission - Math.abs(dx) - Math.abs(dy) - Math.abs(dz) >= 10) return false;
				}
			}
		}
		return true;
	}

	// ---- delik (hole) filtresi ----

	private static boolean hasHole(ClientLevel level, int minX, int minZ) {
		BlockReader b = new BlockReader(level);
		int bottom = level.getMinY();
		int top = bottom + level.getHeight();
		for (int x = minX; x < minX + 16; ++x) {
			for (int z = minZ; z < minZ + 16; ++z) {
				int single = 0, alongX = 0, alongZ = 0;
				for (int y = bottom; y < top; ++y) {
					if (singleShaft(level, b, x, y, z)) ++single;
					else {
						if (shaftEnds(level, b, x, y, z, single)) return true;
						single = 0;
					}
					if (wideShaft(level, b, x, y, z, 1, 0)) ++alongX;
					else {
						if (shaftEnds(level, b, x, y, z, alongX)) return true;
						alongX = 0;
					}
					if (wideShaft(level, b, x, y, z, 0, 1)) ++alongZ;
					else {
						if (shaftEnds(level, b, x, y, z, alongZ)) return true;
						alongZ = 0;
					}
				}
			}
		}
		return false;
	}

	private static boolean singleShaft(ClientLevel l, BlockReader b, int x, int y, int z) {
		return open(l, b, x, y, z) && !open(l, b, x - 1, y, z) && !open(l, b, x + 1, y, z) && !open(l, b, x, y, z - 1) && !open(l, b, x, y, z + 1);
	}

	private static boolean wideShaft(ClientLevel l, BlockReader b, int x, int y, int z, int stepX, int stepZ) {
		for (int i = 0; i < 3; ++i) if (!open(l, b, x + stepX * i, y, z + stepZ * i)) return false;
		if (open(l, b, x - stepX, y, z - stepZ) || open(l, b, x + stepX * 3, y, z + stepZ * 3)) return false;
		for (int i = 0; i < 3; ++i) {
			int wx = x + stepX * i, wz = z + stepZ * i;
			if (open(l, b, wx - stepZ, y, wz - stepX) || open(l, b, wx + stepZ, y, wz + stepX)) return false;
		}
		return true;
	}

	private static boolean shaftEnds(ClientLevel l, BlockReader b, int x, int y, int z, int run) {
		return run >= 2 && open(l, b, x, y, z) && !open(l, b, x, y - run - 1, z);
	}

	private static boolean open(ClientLevel l, BlockReader b, int x, int y, int z) {
		return b.state(x, y, z).getCollisionShape(l, BlockPos.ZERO).isEmpty();
	}

	private static boolean isBud(BlockState state) {
		Block b = state.getBlock();
		return b == Blocks.SMALL_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD;
	}

	private static boolean isGrass(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM);
	}

	private static boolean donutServer() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) return false;
		String brand = connection.serverBrand();
		return brand != null && brand.contains(DONUT_BRAND);
	}

	/** Mixin ile ClientboundBlockUpdatePacket işlenirken çağrılırsa Donut kelp takibi çalışır (şimdilik bağlı değil). */
	public void onBlockUpdate(BlockPos pos, BlockState newState) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !counted[KELP] || !newState.is(Blocks.KELP) || !donutServer()) return;
		int age = newState.getValue(BlockStateProperties.AGE_25);
		if (seenKelp.add(pos.asLong()) && age > 0 && age < KELP_MAX_AGE && level.getBlockState(pos.above()).is(Blocks.WATER)) {
			long k = key(pos.getX() >> 4, pos.getZ() >> 4);
			if (kelpChunks.add(k)) scheduleScan(k);
		}
	}

	// ---- ana thread tick ----

	public void tick(ClientLevel level, LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		ResourceKey<Level> dimension = level.dimension();
		if (dimension != worldKey) {
			resetState();
			worldKey = dimension;
		}
		if (dirty) {
			dirty = false;
			rescanLoadedChunks();
		}
		if (++pollCounter >= 10) {
			pollCounter = 0;
			pollNewChunks(mc, level);
		}
		pruneDistant(mc, player);
		if (++tickCounter >= REFRESH_TICKS) {
			tickCounter = 0;
			rebuildDisplaySets(level);
		}
	}

	private void pruneDistant(Minecraft mc, LocalPlayer player) {
		int reach = mc.options.getEffectiveRenderDistance() + 2 * simDistance + 2;
		int px = player.getBlockX() >> 4, pz = player.getBlockZ() >> 4;
		synchronized (lock) {
			distant.clear();
			LongIterator keys = records.keySet().iterator();
			while (keys.hasNext()) {
				long k = keys.nextLong();
				if (Math.abs(kx(k) - px) > reach || Math.abs(kz(k) - pz) > reach) distant.add(k);
			}
			for (int i = 0; i < distant.size(); ++i) removeRecord(distant.getLong(i));
		}
		kelpChunks.removeIf(k -> Math.abs(kx(k) - px) > reach || Math.abs(kz(k) - pz) > reach);
		seenKelp.removeIf(p -> Math.abs((BlockPos.getX(p) >> 4) - px) > reach || Math.abs((BlockPos.getZ(p) >> 4) - pz) > reach);
	}

	private void rebuildDisplaySets(ClientLevel level) {
		boolean holes = holeFilter;
		int threshold = sensitivity, holeReach = holeDistance, reach = 2 * simDistance + 2;
		LongOpenHashSet next = new LongOpenHashSet();
		synchronized (lock) {
			LongIterator keys = scanned.iterator();
			while (keys.hasNext()) {
				long k = keys.nextLong();
				int cx = kx(k), cz = kz(k);
				if (level.hasChunk(cx, cz) && !clearCoverage.containsKey(k) && (!holes || !holeNearby(cx, cz, holeReach))
					&& support.get(k) >= threshold && coveredSides(cx, cz, reach) >= ENCLOSED_SIDES) next.add(k);
			}
		}
		primary = next;
	}

	private boolean holeNearby(int cx, int cz, int distance) {
		for (int dx = -distance; dx <= distance; ++dx) {
			for (int dz = -distance; dz <= distance; ++dz) {
				Rec r = records.get(key(cx + dx, cz + dz));
				if (r != null && r.hole()) return true;
			}
		}
		return false;
	}

	private int coveredSides(int cx, int cz, int reach) {
		int sides = 0;
		for (int[] s : SIDES) if (stripCleared(cx, cz, s[0], s[1], reach)) ++sides;
		return sides;
	}

	private boolean stripCleared(int cx, int cz, int dx, int dz, int reach) {
		for (int depth = 1; depth <= reach; ++depth) {
			for (int lateral = -reach; lateral <= reach; ++lateral) {
				if (clearCoverage.containsKey(key(cx + dx * depth + dz * lateral, cz + dz * depth + dx * lateral))) return true;
			}
		}
		return false;
	}

	// ---- güvenli blok okuyucu (chunk önbellekli, yüklenmemiş = void air) ----

	private static final class BlockReader {
		private final ClientLevel level;
		private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		private LevelChunk chunk;
		private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;

		private BlockReader(ClientLevel level) { this.level = level; }

		private BlockState state(int x, int y, int z) {
			if (level.isOutsideBuildHeight(y)) return Blocks.VOID_AIR.defaultBlockState();
			LevelChunk at = chunkAt(x >> 4, z >> 4);
			if (at == null) return Blocks.VOID_AIR.defaultBlockState();
			LevelChunkSection section = at.getSections()[at.getSectionIndex(y)];
			return section == null ? Blocks.VOID_AIR.defaultBlockState() : section.getBlockState(x & 15, y & 15, z & 15);
		}

		private boolean emptySection(int x, int y, int z) {
			LevelChunk at = chunkAt(x >> 4, z >> 4);
			if (at == null) return true;
			LevelChunkSection section = at.getSections()[at.getSectionIndex(y)];
			return section == null || section.hasOnlyAir();
		}

		private LevelChunk chunkAt(int cx, int cz) {
			if (chunk != null && chunkX == cx && chunkZ == cz) return chunk;
			LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
			if (loaded != null) {
				chunk = loaded;
				chunkX = cx;
				chunkZ = cz;
			}
			return loaded;
		}

		private int light(int x, int y, int z) {
			mp.set(x, y, z);
			int value = Math.max(level.getBrightness(LightLayer.BLOCK, mp), level.getBrightness(LightLayer.SKY, mp));
			if (value < 15 && level.dimensionType().hasSkyLight()) return openSky(x, y, z) ? 15 : value;
			return value;
		}

		private boolean openSky(int x, int y, int z) {
			int top = level.getMaxY();
			for (int ny = y + 1; ny <= top; ++ny) {
				if (emptySection(x, ny, z)) ny |= 15;
				else if (state(x, ny, z).getLightBlock() > 0) return false;
			}
			return true;
		}
	}
}
