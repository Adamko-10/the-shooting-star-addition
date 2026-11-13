package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The crater round SS-06's fallen moon, and the moon itself out of moon cheese (holey here and there, the one molten
 * block at its heart), a chunk at a time, nearest the mark first, so a huge moon never freezes the server tick. Server
 * side only. What to cut comes from the strike's floor (MoonStrike's crater: down to the moon's top over the moon, to
 * the crater's floor round it) and what to place from {@link MoonPlan#inMoon}, so it is always the same sphere the
 * client films.
 *
 * <p><b>Only where land is loaded</b> (the user, 2026-11-04): it never loads land. A loaded chunk is cut from the impact
 * on and has its moon built from SETTLE (both at once if it loads later); a chunk that isn't loaded waits, and after the
 * spell is done as it loads ({@link #tickLingering}), like MoonLand's rim and cracks and The Shooting Star's lingering
 * carvings. So a 10x moonfall generates nothing (it used to load ~4000 chunks round the mark, most of them new land, a
 * minute's work). Cutting and building in the same chunks, by the same hand, keeps them in order: the moon's bulge over
 * the crater floor is never cut away by a crater cut after it.
 *
 * <p>The way The Shooting Star builds its own big strikes (RimuLib's section sink): a chunk's sections are copied on the
 * server thread, with its floors and new top blocks worked out there (they read the land), then cut and filled on a
 * few builder threads (whole sections above the floor just start empty) and swapped back in whole; the chunks swapped
 * in each tick are relit and sent together. A copy of a chunk written meanwhile is thrown away and made again.
 */
public final class MoonBuild {
    /** The most time the server thread spends on it in one tick. */
    private static final long BUDGET_NANOS = 15_000_000L;
    /** How much of the moon is holey moon cheese: the cheese's eyes, scattered all through it. */
    private static final double HOLEY = 0.12;
    /** The builder threads: a few, so the server and world generation keep cores of their own. */
    private static final int BUILDERS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
    private static final ExecutorService BUILD = Executors.newFixedThreadPool(BUILDERS, task -> {
        Thread thread = new Thread(task, "Luna moon builder");
        thread.setDaemon(true);
        return thread;
    });
    /** Chunks copied and being worked on at once, at most: enough that the server thread never waits on the builders. */
    private static final int IN_FLIGHT = 32;
    /** Strikes with chunks still to do after their spell (done as those chunks load). */
    private static final List<MoonBuild> LINGERING = new ArrayList<>();

    private final ServerLevel level;
    private final MoonPlan plan;
    /** The crater's floor and its new top blocks: they read the land, so only on the server thread. */
    private final Excavation.Floor floor;
    private final Excavation.Surface surface;
    @Nullable
    private final BlockPos spared;
    private final int maxY;
    private final int moonFrom;
    private final int moonTo;
    private final Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
    /** The same chunks, nearest the mark first. */
    private final List<Chunk> order = new ArrayList<>();
    /** Chunks copied and being worked on, oldest first: swapped in in that order. */
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    /** From SETTLE on the moon is built too (the client plays it ploughing in until then). */
    private boolean moonTime;
    /** For the log line once the loaded land is done. */
    private long begun = -1L;
    private long worstTick;
    private boolean logged;

    /** One chunk: whether the crater and the moon reach into it, and what of them is done. */
    private static final class Chunk {
        final ChunkPos pos;
        final boolean moon;
        boolean cut;
        boolean built;
        boolean busy;

        Chunk(ChunkPos pos, boolean core, boolean moon) {
            this.pos = pos;
            this.moon = moon;
            this.cut = !core;
            this.built = !moon;
        }
    }

    private record Job(Chunk chunk, boolean cut, boolean moon, Excavation.Sink sink, CompletableFuture<Void> work) {
    }

    /** Made at the impact: the crater out to {@code coreRadius} from the mark, and the moon. */
    public MoonBuild(ServerLevel level, MoonPlan plan, double coreRadius, Excavation.Floor floor, Excavation.Surface surface,
                     @Nullable BlockPos spared) {
        this.level = level;
        this.plan = plan;
        this.floor = floor;
        this.surface = surface;
        this.spared = spared;
        this.maxY = level.getMaxY();
        double r = plan.params.moonRadius();
        this.moonFrom = Math.max(level.getMinY(), Mth.floor(plan.restCentre.y - r - 1.0));
        this.moonTo = Math.min(this.maxY, Mth.ceil(plan.restCentre.y + r + 1.0));
        double reach = Math.max(coreRadius, Math.hypot(plan.restCentre.x - plan.target.x, plan.restCentre.z - plan.target.z) + r);
        int cx0 = Mth.floor(plan.target.x - reach) >> 4;
        int cx1 = Mth.floor(plan.target.x + reach) >> 4;
        int cz0 = Mth.floor(plan.target.z - reach) >> 4;
        int cz1 = Mth.floor(plan.target.z + reach) >> 4;
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                boolean core = touches(x, z, plan.target.x, plan.target.z, coreRadius);
                boolean moon = touches(x, z, plan.restCentre.x, plan.restCentre.z, r);
                if (core || moon) {
                    Chunk c = new Chunk(new ChunkPos(x, z), core, moon);
                    this.chunks.put(c.pos.pack(), c);
                    this.order.add(c);
                }
            }
        }
        this.order.sort(Comparator.comparingDouble(c -> sqrDistToMark(c.pos, plan)));
    }

    /** Whether chunk (x, z)'s square comes within {@code radius} of (cx, cz). */
    private static boolean touches(int x, int z, double cx, double cz, double radius) {
        double nx = Mth.clamp(cx, x * 16.0, x * 16.0 + 15.0);
        double nz = Mth.clamp(cz, z * 16.0, z * 16.0 + 15.0);
        return (nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= radius * radius;
    }

    private static double sqrDistToMark(ChunkPos pos, MoonPlan plan) {
        double dx = pos.x() * 16.0 + 8.0 - plan.target.x;
        double dz = pos.z() * 16.0 + 8.0 - plan.target.z;
        return dx * dx + dz * dz;
    }

    /** SETTLE: the moon is at rest, so from now on it is built too. */
    public void moon() {
        this.moonTime = true;
    }

    public boolean settled() {
        return this.moonTime;
    }

    /** Whether MoonLand may work in this chunk: the crater round the moon is cut in it (or doesn't reach it). */
    public boolean ready(ChunkPos pos) {
        Chunk c = this.chunks.get(pos.pack());
        return c == null || c.cut;
    }

    /** Whether everything is done in every chunk loaded now (the moon too once it is at rest). */
    public boolean loadedDone() {
        if (!this.jobs.isEmpty()) {
            return false;
        }
        for (Chunk c : this.order) {
            if ((!c.cut || this.moonTime && !c.built) && this.level.hasChunk(c.pos.x(), c.pos.z())) {
                return false;
            }
        }
        return true;
    }

    /** Whether all of it is done, everywhere. */
    public boolean done() {
        if (!this.moonTime || !this.jobs.isEmpty()) {
            return false;
        }
        for (Chunk c : this.order) {
            if (!c.cut || !c.built) {
                return false;
            }
        }
        return true;
    }

    /** The spell is over: the moon is at rest, and what isn't loaded is done as it loads. */
    public void end() {
        this.moonTime = true;
        if (!this.done() && !LINGERING.contains(this)) {
            LINGERING.add(this);
        }
    }

    /** Every server tick: past moonfalls finish the chunks of theirs that have loaded since. */
    public static void tickLingering() {
        for (Iterator<MoonBuild> it = LINGERING.iterator(); it.hasNext(); ) {
            MoonBuild build = it.next();
            try {
                build.tick();
            } catch (RuntimeException | LinkageError failed) {
                // outside any spell's safety net: drop it rather than let the error stop the server every tick
                dev.ss05.halley.HalleyAddon.LOG.error("A past moonfall's leftover building failed and was dropped", failed);
                it.remove();
                continue;
            }
            if (build.done()) {
                it.remove();
            }
        }
    }

    /** Forgets every lingering moonfall (the server is stopping). */
    public static void clearLingering() {
        LINGERING.clear();
    }

    public void tick() {
        long start = System.nanoTime();
        this.begun = this.begun < 0L ? start : this.begun;
        List<ChunkPos> swapped = new ArrayList<>();
        // swap in what the builders have finished, oldest first
        while (!this.jobs.isEmpty() && this.jobs.peek().work().isDone() && System.nanoTime() - start < BUDGET_NANOS) {
            Job job = this.jobs.poll();
            job.chunk().busy = false;
            try {
                job.work().join();
            } catch (CompletionException failed) {
                HalleyAddon.LOG.error("Luna: the crater and moon in chunk {} failed, leaving it as it is", job.chunk().pos, failed);
                job.sink().release();
                job.chunk().cut = true;
                job.chunk().built = true;
                continue;
            }
            if (job.sink().stale()) {
                // written since it was copied (the rim, the slabs, the crater further out): copied again below
                job.sink().release();
                continue;
            }
            job.sink().apply();
            job.chunk().cut |= job.cut();
            job.chunk().built |= job.moon();
            swapped.add(job.chunk().pos);
        }
        // start the loaded chunks with something left to do, nearest the mark first
        for (Chunk c : this.order) {
            if (this.jobs.size() >= IN_FLIGHT || System.nanoTime() - start > BUDGET_NANOS) {
                break;
            }
            if (c.busy || c.cut && (c.built || !this.moonTime) || !this.level.hasChunk(c.pos.x(), c.pos.z())) {
                continue;
            }
            Job job = this.start(c);
            if (job != null) {
                this.jobs.add(job);
            }
        }
        if (!swapped.isEmpty()) {
            StarBridge.relight(this.level, swapped);
        }
        long end = System.nanoTime();
        this.worstTick = Math.max(this.worstTick, end - start);
        if (!this.logged && this.moonTime && this.loadedDone()) {
            this.logged = true;
            int waiting = 0;
            for (Chunk c : this.order) {
                waiting += c.cut && c.built ? 0 : 1;
            }
            HalleyAddon.LOG.info("Luna: the crater round the moon and the moon are done where land is loaded: {} chunks in {} s "
                + "(worst tick {} ms); {} wait for their land to load", this.order.size() - waiting,
                Math.round((end - this.begun) / 1.0E8) / 10.0, this.worstTick / 1_000_000L, waiting);
        }
    }

    /**
     * Copies a loaded chunk to cut its crater and/or build its moon on a builder thread. Its floors and new top blocks
     * are worked out here, on the server thread, as they read the land. Null if there was nothing to do in it after all.
     */
    @Nullable
    private Job start(Chunk c) {
        boolean cut = !c.cut;
        boolean moon = this.moonTime && !c.built;
        int[] floors = null;
        BlockState[] tops = null;
        int fromY = moon ? this.moonFrom : Integer.MAX_VALUE;
        int clearFrom = Integer.MAX_VALUE;
        if (cut) {
            floors = new int[256];
            tops = new BlockState[256];
            int columns = 0;
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 256; i++) {
                int x = c.pos.getMinBlockX() + (i & 15);
                int z = c.pos.getMinBlockZ() + (i >> 4);
                int f = this.spares(x, z) ? MoonPlan.UNTOUCHED : this.floor.floor(x, z);
                floors[i] = f;
                if (f == MoonPlan.UNTOUCHED || f > this.maxY) {
                    floors[i] = MoonPlan.UNTOUCHED;
                    continue;
                }
                columns++;
                lowest = Math.min(lowest, f);
                highest = Math.max(highest, f);
                BlockState was = this.level.getBlockState(at.set(x, f, z));
                tops[i] = was.isAir() ? null : this.surface.at(x, f, z, was);
            }
            if (columns == 0 && !moon) {
                c.cut = true;
                return null;
            }
            fromY = Math.min(fromY, lowest);
            if (columns == 256) {
                // every column is cut: whole sections above the highest floor just start empty
                int min = this.level.getMinY();
                clearFrom = min + ((highest + 1 - min + 15) >> 4 << 4);
            }
        }
        int toY = cut ? this.maxY : this.moonTo;
        fromY = Math.max(this.level.getMinY(), Math.min(fromY, toY));
        // protected blocks are put back where they were, after the cut and the moon (so no section may start empty)
        LevelChunk live = this.level.getChunkSource().getChunkNow(c.pos.x(), c.pos.z());
        Long2ObjectMap<BlockState> kept = live == null ? null : Protected.scan(live);
        Excavation.Sink sink = StarBridge.sink(this.level, c.pos, fromY, toY, kept == null ? clearFrom : Integer.MAX_VALUE);
        c.busy = true;
        int[] f = floors;
        BlockState[] t = tops;
        int lo = fromY;
        return new Job(c, cut, moon, sink, CompletableFuture.runAsync(() -> {
            sink.prepare();
            if (cut) {
                this.cutColumns(c.pos, sink, f, t, toY);
            }
            if (moon) {
                this.fillMoon(c.pos, sink);
            }
            if (kept != null) {
                for (Long2ObjectMap.Entry<BlockState> e : kept.long2ObjectEntrySet()) {
                    int y = BlockPos.getY(e.getLongKey());
                    if (y >= lo && y <= toY) {
                        sink.put(BlockPos.getX(e.getLongKey()), y, BlockPos.getZ(e.getLongKey()), e.getValue());
                    }
                }
            }
        }, BUILD));
    }

    /** Whether the caster's own footing or another mod's shield spares this column (as the carvings have it). */
    private boolean spares(int x, int z) {
        return this.spared != null && Math.abs(x - this.spared.getX()) <= 2 && Math.abs(z - this.spared.getZ()) <= 2
            || StarBridge.spares(this.level, x, z);
    }

    /** The crater, into a chunk's copy (a builder thread): each column emptied above its floor, its new top block set. */
    private void cutColumns(ChunkPos pos, Excavation.Sink sink, int[] floors, BlockState[] tops, int toY) {
        for (int i = 0; i < 256; i++) {
            int f = floors[i];
            if (f == MoonPlan.UNTOUCHED) {
                continue;
            }
            int x = pos.getMinBlockX() + (i & 15);
            int z = pos.getMinBlockZ() + (i >> 4);
            sink.clear(x, z, f + 1, toY);
            if (tops[i] != null) {
                sink.put(x, f, z, tops[i]);
            }
        }
    }

    /** Whether the moon's block here is one of the cheese's holes (always the same for the same block). */
    private boolean holeyAt(int x, int y, int z) {
        return this.plan.hash(x + 7919, y - 104729, z + 1299709) < HOLEY;
    }

    /** One chunk's share of the moon, all of it, into its copy (a builder thread: only plan maths and the copy). */
    private void fillMoon(ChunkPos pos, Excavation.Sink sink) {
        double r = this.plan.params.moonRadius();
        double cy = this.plan.restCentre.y;
        BlockState cheese = HalleyContent.MOON_CHEESE.defaultBlockState();
        BlockState holey = HalleyContent.HOLEY_MOON_CHEESE.defaultBlockState();
        BlockState molten = HalleyContent.MOLTEN_MOON_CHEESE.defaultBlockState();
        BlockPos core = this.plan.params.core() ? this.plan.core : null;

        int x0 = pos.x() * 16;
        int z0 = pos.z() * 16;
        for (int dx = 0; dx < 16; dx++) {
            int x = x0 + dx;
            double ex = x + 0.5 - this.plan.restCentre.x;
            for (int dz = 0; dz < 16; dz++) {
                int z = z0 + dz;
                double ez = z + 0.5 - this.plan.restCentre.z;
                double flat = ex * ex + ez * ez;
                if (flat > r * r || StarBridge.spares(this.level, x, z)) {
                    continue;
                }
                // the blocks whose centres lie inside the moon
                double half = Math.sqrt(r * r - flat);
                int from = Math.max(this.moonFrom, Mth.ceil(cy - half - 0.5));
                int to = Math.min(this.moonTo, Mth.floor(cy + half - 0.5));
                for (int y = from; y <= to; y++) {
                    boolean heart = core != null && x == core.getX() && y == core.getY() && z == core.getZ();
                    sink.put(x, y, z, heart ? molten : this.holeyAt(x, y, z) ? holey : cheese);
                }
            }
        }
    }
}
