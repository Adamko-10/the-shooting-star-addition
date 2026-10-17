package dev.ss05.halley.world;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What Luna does to the land round its crater, chunk by chunk (server side): the slabs of crust buckled up against the
 * moon, the crater's rim heaved up (its inner wall cut down to the crater's floor first), the cracks racing out across
 * the land, and the lumps of moon and crust thrown out onto it.
 *
 * <p>Like The Shooting Star's own carvings it never loads land for this: chunks that are loaded while the strike runs
 * are worked on as it happens (the rim heaving up in jolts while the moon drives in, {@link MoonPlan#rimStep}; the
 * cracks racing out, {@link MoonPlan#crackFront}), and any other chunk of it is done in one go when it next loads, while
 * the server runs ({@link #tickLingering}). Each chunk keeps only what it needs while it is being worked on: no table of
 * the whole area.
 *
 * <p>A lifted column is the old column pushed up: its own surface block stays on top and the layers under it show in
 * its sides, scorched black and molten against the moon, singed on the crater's inner wall. Whatever stood on the
 * ground there (plants, trees, buildings) is gone.
 */
final class MoonLand {
    /** Nearest the moon, the crust is burnt and partly molten. */
    private static final Palette SCORCHED = Palette.of(Blocks.BLACKSTONE, 30, Blocks.BASALT, 24, Blocks.MAGMA_BLOCK, 18,
        Blocks.COBBLED_DEEPSLATE, 14, Blocks.COARSE_DIRT, 14);
    /** What a lifted column is made of where the old column had nothing to give (a cave, water...). */
    private static final Palette FILL = Palette.of(Blocks.STONE, 40, Blocks.COBBLESTONE, 20, Blocks.TUFF, 15,
        Blocks.ANDESITE, 15, Blocks.GRAVEL, 10);
    /** The crater's floor up the rim's inner wall: blasted earth and gravel. */
    private static final Palette WALL_FLOOR = Palette.of(Blocks.COARSE_DIRT, 30, Blocks.GRAVEL, 20, Blocks.TUFF, 12,
        Blocks.DIRT, 18, Blocks.COBBLESTONE, 10, Blocks.BLACKSTONE, 10);
    private static final Palette CRACK_WALL = Palette.of(Blocks.BLACKSTONE, 45, Blocks.BASALT, 35, Blocks.SMOOTH_BASALT, 20);
    /** Lumps of crust thrown out with the moon cheese. */
    private static final Palette CRUST_LUMP = Palette.of(Blocks.BLACKSTONE, 35, Blocks.BASALT, 25, Blocks.COBBLED_DEEPSLATE, 25,
        Blocks.MAGMA_BLOCK, 15);

    /** Strikes whose land still has chunks to do after their spell has ended (worked on as those chunks load). */
    private static final List<MoonLand> LINGERING = new ArrayList<>();
    /** How many waiting chunks a lingering strike looks at each tick, and the most it works on. */
    private static final int LOOK_PER_TICK = 48;
    private static final int WORK_PER_TICK = 2;

    private final ServerLevel level;
    private final MoonPlan plan;
    private final int chunksPerTick;
    /** Every chunk with something to do, and what. */
    private final Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
    /** The same chunks, nearest the mark first (the order they are started in). */
    private final List<Chunk> ordered = new ArrayList<>();
    /** The chunks still to finish that weren't loaded (or were let go of) while the strike ran. */
    private final LongArrayList waiting = new LongArrayList();
    private int look;
    private boolean ended;

    /** One crack sample: crack {@code crack} at {@code along} (0 at its start, 1 at its tip). */
    private record Sample(int crack, double along) {
    }

    /** What one chunk has to do, and how far it has got. */
    private static final class Chunk {
        final ChunkPos pos;
        /** Whether the rim (or the slabs against the moon) reach into it. */
        boolean rim;
        /** Its rim columns (x & 15 | (z & 15) << 4) and their final heights, laid out when it is started. */
        int[] columns = new int[0];
        int[] heights = new int[0];
        final List<Sample> samples = new ArrayList<>();
        final List<Integer> debris = new ArrayList<>();
        // the columns as surveyed (once the chunk is started)
        int[] base;
        int[] placed;
        BlockState[][] strata;
        boolean[] cut;
        boolean started;
        boolean done;

        Chunk(ChunkPos pos) {
            this.pos = pos;
        }
    }

    MoonLand(ServerLevel level, MoonPlan plan, int chunksPerTick) {
        this.level = level;
        this.plan = plan;
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.layRim();
        this.layCracks();
        this.layDebris();
        this.ordered.addAll(this.chunks.values());
        this.ordered.sort(java.util.Comparator.comparingDouble(c -> this.nearest(c.pos.x, c.pos.z)));
    }

    // ---- Laying it out (cheap: a few numbers per chunk, nothing per block). -----------------------------------------

    private Chunk chunk(int cx, int cz) {
        return this.chunks.computeIfAbsent(ChunkPos.asLong(cx, cz), key -> new Chunk(new ChunkPos(cx, cz)));
    }

    /** Marks the chunks the rim's annulus or the slabs' ring touch (their columns are laid out as each is started). */
    private void layRim() {
        MoonPlan p = this.plan;
        double outer = p.rimOuter + 1.0;
        double slabsOut = p.floorInner + p.plateReach() * 1.15 + 1.0;
        double slabsIn = p.floorInner - 1.0;
        int cx0 = Mth.floor(p.target.x - outer) >> 4;
        int cx1 = Mth.floor(p.target.x + outer) >> 4;
        int cz0 = Mth.floor(p.target.z - outer) >> 4;
        int cz1 = Mth.floor(p.target.z + outer) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                double near = this.nearest(cx, cz);
                double far = this.farthest(cx, cz);
                // the inner wall is cut down to the crater's floor even with no rim at all
                boolean rim = far >= p.rimInner - 1.0 && near <= (p.params.rimHeight() > 0 ? outer : p.rimCrest + 1.0);
                boolean slabs = p.params.rimHeight() > 0 && far >= slabsIn && near <= slabsOut;
                if (rim || slabs) {
                    this.chunk(cx, cz).rim = true;
                }
            }
        }
    }

    /**
     * The chunk's rim columns: every column the rim or the slabs lift ({@link MoonPlan#rimHeight}), and every column of
     * the rim's inner wall (part of the crater, cut down to its floor even where nothing rises on it).
     */
    private void layColumns(Chunk c) {
        if (!c.rim) {
            return;
        }
        MoonPlan p = this.plan;
        int[] cols = new int[256];
        int[] heights = new int[256];
        int n = 0;
        for (int i = 0; i < 256; i++) {
            int x = c.pos.x * 16 + (i & 15);
            int z = c.pos.z * 16 + (i >> 4);
            int h = p.rimHeight(x, z);
            double d = p.fromMark(x + 0.5, z + 0.5);
            if (h > 0 || d >= p.rimInner && d < p.rimCrest) {
                cols[n] = i;
                heights[n] = h;
                n++;
            }
        }
        c.columns = java.util.Arrays.copyOf(cols, n);
        c.heights = java.util.Arrays.copyOf(heights, n);
    }

    /** The main cracks and their branches (the veins across the crater's floor are cut with the floor itself). */
    private void layCracks() {
        MoonPlan p = this.plan;
        for (int i = 0; i < p.crackCount(); i++) {
            if (p.crackIsVein(i)) {
                continue;
            }
            double step = 0.6 / Math.max(1.0, p.crackLength(i));
            for (double along = 0.0; along <= 1.0; along += step) {
                Vec3 at = p.crackPoint(i, along);
                double half = p.crackHalfWidth(i, along);
                if (half < 0.35) {
                    continue;
                }
                double reach = half + 1.5;
                int cx0 = Mth.floor(at.x - reach) >> 4;
                int cx1 = Mth.floor(at.x + reach) >> 4;
                int cz0 = Mth.floor(at.z - reach) >> 4;
                int cz1 = Mth.floor(at.z + reach) >> 4;
                Sample sample = new Sample(i, along);
                for (int cx = cx0; cx <= cx1; cx++) {
                    for (int cz = cz0; cz <= cz1; cz++) {
                        this.chunk(cx, cz).samples.add(sample);
                    }
                }
            }
        }
    }

    private void layDebris() {
        if (!this.plan.params.carve()) {
            return;
        }
        for (int i = 0; i < this.plan.debrisCount(); i++) {
            Vec3 land = this.plan.debrisLanding(i);
            this.chunk(Mth.floor(land.x) >> 4, Mth.floor(land.z) >> 4).debris.add(i);
        }
    }

    private double nearest(int cx, int cz) {
        double nx = Mth.clamp(this.plan.target.x, cx * 16.0, cx * 16.0 + 16.0);
        double nz = Mth.clamp(this.plan.target.z, cz * 16.0, cz * 16.0 + 16.0);
        return Math.hypot(nx - this.plan.target.x, nz - this.plan.target.z);
    }

    private double farthest(int cx, int cz) {
        double fx = Math.max(Math.abs(cx * 16.0 - this.plan.target.x), Math.abs(cx * 16.0 + 16.0 - this.plan.target.x));
        double fz = Math.max(Math.abs(cz * 16.0 - this.plan.target.z), Math.abs(cz * 16.0 + 16.0 - this.plan.target.z));
        return Math.hypot(fx, fz);
    }

    // ---- While the strike runs. ------------------------------------------------------------------------------------

    /**
     * Works on every loaded chunk as far as it has got by time {@code t} (the rim heaving, the cracks running out); the
     * others wait for {@link #tickLingering}. Starts once the crater round the moon is dug.
     */
    void tick(double t) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        boolean debrisTime = MoonPlan.rise(t) >= 1.0;
        // a few chunks are started (surveyed) each tick, nearest the mark first, so no one tick does them all
        int starts = 0;
        for (Chunk c : this.ordered) {
            if (c.done) {
                continue;
            }
            if (!this.loaded(c.pos)) {
                // not loaded (or let go of while it was being worked on): done as it loads, after the strike
                continue;
            }
            if (!c.started) {
                if (starts >= this.chunksPerTick) {
                    continue;
                }
                this.start(c, out);
                starts++;
            }
            boolean rimUp = this.raise(c, t, out);
            // where the rim rises, its cracks are cut once it is up (or it would bury them again)
            boolean cracked = (rimUp || c.columns.length == 0) && this.cutCracks(c, t, out);
            if (rimUp && cracked && debrisTime) {
                this.debris(c, out);
                c.done = true;
            }
        }
        this.paint(out);
    }

    /** Whether every chunk loaded now has been finished (the rest wait for {@link #tickLingering}). */
    boolean loadedDone() {
        for (Chunk c : this.chunks.values()) {
            if (!c.done && this.loaded(c.pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The strike's spell is over: every loaded chunk is finished now, and the rest are left to be done as they load
     * (if there are any, this joins the lingering strikes).
     */
    void end() {
        if (this.ended) {
            return;
        }
        this.ended = true;
        Map<BlockPos, BlockState> out = new HashMap<>();
        this.waiting.clear();
        for (Chunk c : this.chunks.values()) {
            if (c.done) {
                continue;
            }
            if (this.loaded(c.pos)) {
                this.complete(c, out);
            } else {
                this.waiting.add(c.pos.toLong());
            }
        }
        this.paint(out);
        if (!this.waiting.isEmpty()) {
            LINGERING.add(this);
        }
    }

    /** Finishes a chunk in one go: all its rim, all its cracks, its debris. */
    private void complete(Chunk c, Map<BlockPos, BlockState> out) {
        if (!c.started) {
            this.start(c, out);
        }
        this.raise(c, Double.MAX_VALUE, out);
        this.cutCracks(c, Double.MAX_VALUE, out);
        this.debris(c, out);
        c.done = true;
    }

    // ---- After it: as land loads. ----------------------------------------------------------------------------------

    /** Every server tick: lingering strikes finish the chunks of theirs that have loaded since, a few at a time. */
    static void tickLingering() {
        for (Iterator<MoonLand> it = LINGERING.iterator(); it.hasNext(); ) {
            MoonLand land = it.next();
            if (land.lingerTick()) {
                it.remove();
            }
        }
    }

    /** Forgets every lingering strike (the server is stopping). */
    static void clearLingering() {
        LINGERING.clear();
    }

    /** True once nothing of it is left to do. */
    private boolean lingerTick() {
        Map<BlockPos, BlockState> out = new HashMap<>();
        int worked = 0;
        for (int i = 0; i < LOOK_PER_TICK && !this.waiting.isEmpty() && worked < Math.max(WORK_PER_TICK, this.chunksPerTick / 2); i++) {
            if (this.look >= this.waiting.size()) {
                this.look = 0;
            }
            long key = this.waiting.getLong(this.look);
            Chunk c = this.chunks.get(key);
            if (c == null || c.done) {
                this.remove(this.look);
                continue;
            }
            if (this.loaded(c.pos)) {
                this.complete(c, out);
                this.remove(this.look);
                worked++;
            } else {
                this.look++;
            }
        }
        this.paint(out);
        return this.waiting.isEmpty();
    }

    private void remove(int index) {
        int last = this.waiting.size() - 1;
        this.waiting.set(index, this.waiting.getLong(last));
        this.waiting.removeLong(last);
    }

    // ---- One chunk. ------------------------------------------------------------------------------------------------

    private boolean loaded(ChunkPos pos) {
        return this.level.hasChunk(pos.x, pos.z);
    }

    /** Surveys the chunk's rim columns: their ground, what stood on it (cleared), their layers. */
    private void start(Chunk c, Map<BlockPos, BlockState> out) {
        c.started = true;
        this.layColumns(c);
        int n = c.columns.length;
        c.base = new int[n];
        c.placed = new int[n];
        c.strata = new BlockState[n][];
        c.cut = new boolean[c.samples.size()];
        for (int i = 0; i < n; i++) {
            int x = c.pos.x * 16 + (c.columns[i] & 15);
            int z = c.pos.z * 16 + (c.columns[i] >> 4);
            if (!this.survey(c, i, x, z, out)) {
                c.placed[i] = c.heights[i];
            }
        }
    }

    /**
     * Finds the column's old ground (under any plants, trees, snow, water or buildings), clears what stood on it - and
     * on the rim's inner wall, which is part of the crater, everything down to the crater's floor - and remembers its
     * layers. False if there is no ground to lift (the void, bedrock right at the top...).
     */
    private boolean survey(Chunk c, int i, int x, int z, Map<BlockPos, BlockState> out) {
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int ground = groundAt(this.level, x, z);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        double d = this.plan.fromMark(x + 0.5, z + 0.5);
        boolean wall = d >= this.plan.rimInner && d < this.plan.rimCrest;
        int bottom = this.level.getMinBuildHeight() + 1;
        int base = wall ? Math.max(bottom, Math.min(ground, this.plan.floorY(d, ground))) : ground;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = base + 1; y <= Math.max(top, ground); y++) {
            BlockState was = this.level.getBlockState(at.set(x, y, z));
            // what stood on the ground is swept away (fluids are left: the lifted crust takes their place where it
            // rises), and on the crater's side of the crest the ground itself, down to the crater's floor
            if (!was.isAir() && (wall || was.getFluidState().isEmpty()) && was.getDestroySpeed(this.level, at) >= 0.0F) {
                out.put(at.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
        int height = c.heights[i];
        BlockState[] strata = new BlockState[height + 1];
        for (int k = 0; k <= height; k++) {
            BlockState was = this.level.getBlockState(at.set(x, base - k, z));
            strata[k] = isGround(this.level, was) && was.getDestroySpeed(this.level, at) >= 0.0F ? was : null;
        }
        if (wall) {
            strata[0] = WALL_FLOOR.pick(this.plan.hash(z * 5 - 1, base, x * 3 + 7));
            if (base > bottom) {
                out.put(new BlockPos(x, base, z), strata[0]);
            }
        } else if (strata[0] != null && strata[0].is(Blocks.SNOW_BLOCK)) {
            strata[0] = Blocks.DIRT.defaultBlockState();
        }
        c.base[i] = base;
        c.strata[i] = strata;
        return true;
    }

    /** Heaves the chunk's columns up to where they have got by {@code t}; true once all are fully up. */
    private boolean raise(Chunk c, double t, Map<BlockPos, BlockState> out) {
        boolean all = true;
        for (int i = 0; i < c.columns.length; i++) {
            int height = c.heights[i];
            if (c.placed[i] >= height) {
                continue;
            }
            int x = c.pos.x * 16 + (c.columns[i] & 15);
            int z = c.pos.z * 16 + (c.columns[i] >> 4);
            int step = t == Double.MAX_VALUE ? MoonPlan.RIM_STEPS : this.plan.rimStep(x, z, t);
            int want = step >= MoonPlan.RIM_STEPS ? height : Math.round(height * step / (float) MoonPlan.RIM_STEPS);
            if (want > c.placed[i]) {
                this.lift(c, i, x, z, want, out);
            }
            all &= c.placed[i] >= height;
        }
        return all;
    }

    /** Lifts one column to {@code want} blocks over its base. */
    private void lift(Chunk c, int i, int x, int z, int want, Map<BlockPos, BlockState> out) {
        int base = c.base[i];
        int placed = c.placed[i];
        int height = c.heights[i];
        // the old surface block rides up on top; every block under it lands as the layer it will finally be there (the
        // column's deep layers lowest), including the one that was the top until now
        if (placed > 0) {
            out.put(new BlockPos(x, base + placed, z), this.material(c, i, x, z, height - placed, base + placed));
        }
        for (int j = placed + 1; j < want; j++) {
            out.put(new BlockPos(x, base + j, z), this.material(c, i, x, z, height - j, base + j));
        }
        out.put(new BlockPos(x, base + want, z), this.material(c, i, x, z, 0, base + want));
        c.placed[i] = want;
    }

    /** What lands at height {@code y} of a lifted column, from the old layer {@code k} blocks under its surface. */
    private BlockState material(Chunk c, int i, int x, int z, int k, int y) {
        // the slabs against the moon are burnt black and glowing (their tops the most), the crater's inner wall singed
        double scorch = this.plan.scorch(x + 0.5, z + 0.5);
        if (this.plan.hash(x, y * 3 + 131, z) < scorch * (k == 0 ? 1.0 : 0.75)) {
            return SCORCHED.pick(this.plan.hash(x, y, z));
        }
        BlockState[] strata = c.strata[i];
        BlockState layer = k >= 0 && k < strata.length ? strata[k] : null;
        if (layer == null) {
            return FILL.pick(this.plan.hash(z, y, x));
        }
        // torn ground: the surface of a heaved plate is broken up here and there
        if (k == 0 && layer.is(Blocks.GRASS_BLOCK) && this.plan.hash(x, 137, z) < 0.25) {
            return this.plan.hash(z, 139, x) < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
        }
        return layer;
    }

    /** Cuts the chunk's share of every crack out as far as it has run by {@code t}; true once all of it is cut. */
    private boolean cutCracks(Chunk c, double t, Map<BlockPos, BlockState> out) {
        boolean all = true;
        for (int s = 0; s < c.samples.size(); s++) {
            if (c.cut[s]) {
                continue;
            }
            Sample sample = c.samples.get(s);
            double front = t == Double.MAX_VALUE ? 1.0 : this.plan.crackFront(sample.crack(), t);
            if (sample.along() > front) {
                all = false;
                continue;
            }
            this.cutAt(c, sample, out);
            c.cut[s] = true;
        }
        return all;
    }

    /** One crack sample's cut, within this chunk: a fissure with a magma floor (lava where it's deep), scorched lips. */
    private void cutAt(Chunk c, Sample sample, Map<BlockPos, BlockState> out) {
        MoonPlan p = this.plan;
        int i = sample.crack();
        double along = sample.along();
        Vec3 centre = p.crackPoint(i, along);
        double half = p.crackHalfWidth(i, along);
        int maxDepth = Math.max(3, Mth.ceil(p.params.moonRadius() * (p.crackIsBranch(i) ? 0.11 : 0.18)));
        int reach = Mth.ceil(half + 1.5);
        int cx = Mth.floor(centre.x);
        int cz = Mth.floor(centre.z);
        int x0 = Math.max(cx - reach, c.pos.getMinBlockX());
        int x1 = Math.min(cx + reach, c.pos.getMaxBlockX());
        int z0 = Math.max(cz - reach, c.pos.getMinBlockZ());
        int z1 = Math.min(cz + reach, c.pos.getMaxBlockZ());
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double d = Math.hypot(x + 0.5 - centre.x, z + 0.5 - centre.z);
                if (d > half + 1.5) {
                    continue;
                }
                int top = this.topAt(x, z, out);
                if (top < this.level.getMinBuildHeight()) {
                    continue;
                }
                if (d > half) {
                    // the lip: the ground at the edge of the fissure is scorched, and here and there buckled up a block
                    this.put(out, new BlockPos(x, top, z), CRACK_WALL.pick(p.hash(x, top, z)));
                    if (d < half + 1.0 && p.hash(x, 7, z) < 0.3) {
                        this.put(out, new BlockPos(x, top + 1, z), CRACK_WALL.pick(p.hash(z, top, x)));
                    }
                    continue;
                }
                double k = 1.0 - (d / half) * (d / half);
                int depth = Math.max(1, (int) Math.round(maxDepth * (1.0 - along * 0.7) * k + p.hash(x, 3, z) * 1.5));
                int floor = top - depth;
                for (int y = floor + 1; y <= top; y++) {
                    this.put(out, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                // lava only pools where the fissure is deep enough to hold it
                double roll = p.hash(x, floor, z);
                BlockState bottom = depth >= 3 && roll < 0.4 ? Blocks.LAVA.defaultBlockState()
                    : roll < 0.8 ? Blocks.MAGMA_BLOCK.defaultBlockState() : CRACK_WALL.pick(p.hash(z, floor, x));
                this.put(out, new BlockPos(x, floor, z), bottom);
            }
        }
    }

    /** The top solid block of a column, counting what is about to be painted this tick (the rim may just have risen). */
    private int topAt(int x, int z, Map<BlockPos, BlockState> out) {
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int min = this.level.getMinBuildHeight();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top, z);
        // down through what is being cleared this tick, then up through what is being raised
        for (BlockState planned = out.get(at); top >= min && planned != null && planned.isAir(); planned = out.get(at.setY(--top))) {
            // (cleared)
        }
        at.setY(top + 1);
        while (true) {
            BlockState next = out.get(at);
            if (next == null || next.isAir()) {
                break;
            }
            top++;
            at.setY(top + 1);
        }
        return top;
    }

    /** Never cuts into fluids, block entities (chests...) or unbreakable blocks. */
    private void put(Map<BlockPos, BlockState> out, BlockPos at, BlockState state) {
        BlockState planned = out.get(at);
        if (planned != null && !planned.isAir()) {
            out.put(at, state);
            return;
        }
        if (planned != null) {
            return;
        }
        BlockState was = this.level.getBlockState(at);
        if (!was.getFluidState().isEmpty() || was.hasBlockEntity() || was.getDestroySpeed(this.level, at) < 0.0F) {
            return;
        }
        out.put(at, state);
    }

    /** Moon cheese and lumps of crust thrown clear onto the land round the rim, where the client saw them land. */
    private void debris(Chunk c, Map<BlockPos, BlockState> out) {
        if (c.debris.isEmpty()) {
            return;
        }
        BlockState cheese = HalleyContent.MOON_CHEESE.defaultBlockState();
        BlockState holey = HalleyContent.HOLEY_MOON_CHEESE.defaultBlockState();
        for (int i : c.debris) {
            Vec3 land = this.plan.debrisLanding(i);
            int x = Mth.floor(land.x);
            int z = Mth.floor(land.z);
            int y = this.topAt(x, z, out) + 1;
            BlockPos top = new BlockPos(x, y, z);
            BlockState below = out.getOrDefault(top.below(), this.level.getBlockState(top.below()));
            if (!below.getFluidState().isEmpty() || below.is(BlockTags.LEAVES)
                    || !below.isCollisionShapeFullBlock(this.level, top.below()) || !this.level.getBlockState(top).isAir()) {
                continue;
            }
            BlockState lump = this.plan.hash(i, 5, 3) < 0.6 ? (this.plan.hash(i, 19, 4) < 0.2 ? holey : cheese)
                : CRUST_LUMP.pick(this.plan.hash(i, 9, 1));
            out.put(top, lump);
            // the bigger lumps: a second block beside or on top (in this chunk)
            if (this.plan.hash(i, 13, 7) < 0.35) {
                BlockPos second = this.plan.hash(i, 17, 2) < 0.5 ? top.above() : top.east();
                if (second.getX() >> 4 == c.pos.x) {
                    out.put(second, lump);
                }
            }
        }
    }

    private void paint(Map<BlockPos, BlockState> out) {
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }

    // ---- The land itself. ------------------------------------------------------------------------------------------

    /**
     * The y of the land's own top block in this column, under any plants, trees, snow, water or buildings
     * ({@code Integer.MIN_VALUE} if there is none above the bottom of the world). The chunk must be loaded.
     */
    static int groundAt(ServerLevel level, int x, int z) {
        int min = level.getMinBuildHeight();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        while (y >= min && !isGround(level, level.getBlockState(at.set(x, y, z)))) {
            y--;
        }
        // (a superflat world's ground is only a few blocks over the bottom of the world: that is ground too)
        return y < min ? Integer.MIN_VALUE : y;
    }

    /** Whether a block is part of the land itself (as opposed to what grows or was built on it). */
    static boolean isGround(ServerLevel level, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL) || state.hasBlockEntity()) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, BlockPos.ZERO) && state.canOcclude();
    }
}
