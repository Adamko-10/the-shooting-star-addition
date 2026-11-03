package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds SS-06's fallen moon out of moon cheese (holey here and there, and the one molten block at its heart), a
 * chunk at a time, nearest the mark first, so a huge moon never freezes the server tick. Server side only. Unlike
 * {@link Excavation} (which only ever cuts blocks away above a floor), this one also places blocks, so it is its
 * own small class rather than another {@code StarBridge.excavate} shape; what to place comes straight from
 * {@link MoonPlan#inMoon}, so it is always the same sphere the client films.
 *
 * <p>The way The Shooting Star builds its own big strikes (RimuLib's section sink): each chunk's sections are copied on
 * the server thread, filled with the moon on a few builder threads, and swapped back into the chunk whole, then the
 * chunks swapped in that tick are relit and sent together. The server thread only copies and swaps (a 10x moon has
 * ~1000 times the blocks: building it block by block on the server thread froze it for seconds, 2026-11-03). A copy
 * of a chunk written meanwhile is thrown away and made again. Each chunk is built once, whole (the client draws its own
 * moon a block bigger over the blocks until they are all there); one that isn't loaded is asked for through the
 * strike's {@link Excavation.Loader} and built once it is there, never loaded on the spot.
 */
public final class MoonBuild {
    /** The most time the server thread spends on the moon in one tick (at least one chunk always gets done). */
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
    /** Chunks copied and being filled at once, at most. */
    private static final int IN_FLIGHT = BUILDERS * 3;

    private final ServerLevel level;
    private final MoonPlan plan;
    private final int chunksPerTick;
    private final Excavation.Loader loader;
    private final int fromY;
    private final int toY;

    private final List<ChunkPos> order;
    private int next;
    /** Chunks copied and being filled, oldest first: swapped in in that order. */
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();

    private record Job(ChunkPos pos, Excavation.Sink sink, CompletableFuture<Void> fill) {
    }

    public MoonBuild(ServerLevel level, MoonPlan plan, int chunksPerTick, Excavation.Loader loader) {
        this.level = level;
        this.plan = plan;
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.loader = loader;
        this.order = footprint(plan);
        double r = plan.params.moonRadius();
        this.fromY = Math.max(level.getMinBuildHeight(), Mth.floor(plan.restCentre.y - r - 1.0));
        this.toY = Math.min(level.getMaxBuildHeight() - 1, Mth.ceil(plan.restCentre.y + r + 1.0));
    }

    /** Every chunk whose square touches the moon's horizontal footprint around {@code restCentre}, nearest the mark first. */
    private static List<ChunkPos> footprint(MoonPlan plan) {
        double r = plan.params.moonRadius();
        double cx = plan.restCentre.x;
        double cz = plan.restCentre.z;
        int cx0 = Mth.floor(cx - r) >> 4;
        int cx1 = Mth.floor(cx + r) >> 4;
        int cz0 = Mth.floor(cz - r) >> 4;
        int cz1 = Mth.floor(cz + r) >> 4;
        List<ChunkPos> all = new ArrayList<>();
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                double nx = Mth.clamp(cx, x * 16.0, x * 16.0 + 15.0);
                double nz = Mth.clamp(cz, z * 16.0, z * 16.0 + 15.0);
                if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= r * r) {
                    all.add(new ChunkPos(x, z));
                }
            }
        }
        all.sort(Comparator.comparingDouble(pos -> sqrDistToMark(pos, plan)));
        return all;
    }

    private static double sqrDistToMark(ChunkPos pos, MoonPlan plan) {
        double dx = pos.x * 16.0 + 8.0 - plan.target.x;
        double dz = pos.z * 16.0 + 8.0 - plan.target.z;
        return dx * dx + dz * dz;
    }

    public boolean done() {
        return this.next >= this.order.size() && this.jobs.isEmpty();
    }

    public void tick() {
        this.build(false);
    }

    /** Builds everything that is left, right now (used when the spell is cut short: this may load its chunks). */
    public void finish() {
        this.build(true);
    }

    private void build(boolean now) {
        long start = System.nanoTime();
        List<ChunkPos> built = new ArrayList<>();
        int started = 0;
        while (!this.done()) {
            Job job = this.jobs.peek();
            if (job != null && (now || job.fill().isDone())) {
                this.jobs.poll();
                try {
                    job.fill().join();
                } catch (CompletionException failed) {
                    HalleyAddon.LOG.error("Luna: building the moon in chunk {} failed, leaving it out", job.pos(), failed);
                    job.sink().release();
                    this.loader.release(job.pos());
                    continue;
                }
                if (job.sink().stale()) {
                    // written since it was copied (the rim, the cracks): copy it again
                    job.sink().release();
                    this.jobs.addFirst(this.start(job.pos(), now));
                    continue;
                }
                job.sink().apply();
                this.loader.release(job.pos());
                built.add(job.pos());
            } else {
                if (this.next >= this.order.size()
                        || !now && (this.jobs.size() >= IN_FLIGHT || started >= this.chunksPerTick)) {
                    break;
                }
                ChunkPos pos = this.order.get(this.next);
                if (!now && !this.level.hasChunk(pos.x, pos.z)) {
                    // wait for it (asked for once, let go of once built)
                    this.loader.hold(pos);
                    break;
                }
                this.jobs.add(this.start(pos, now));
                this.next++;
                started++;
            }
            if (!now && System.nanoTime() - start > BUDGET_NANOS) {
                break;
            }
        }
        if (!built.isEmpty()) {
            StarBridge.relight(this.level, built);
        }
    }

    /** Copies one chunk and fills the copy on a builder thread (on this one when {@code now}). */
    private Job start(ChunkPos pos, boolean now) {
        if (now) {
            this.level.getChunk(pos.x, pos.z);
        }
        // kept loaded until its sections are swapped in
        this.loader.hold(pos);
        Excavation.Sink sink = StarBridge.sink(this.level, pos, this.fromY, this.toY);
        if (now) {
            this.fill(pos, sink);
            return new Job(pos, sink, CompletableFuture.completedFuture(null));
        }
        return new Job(pos, sink, CompletableFuture.runAsync(() -> this.fill(pos, sink), BUILD));
    }

    /** Whether the moon's block here is one of the cheese's holes (always the same for the same block). */
    private boolean holeyAt(int x, int y, int z) {
        return this.plan.hash(x + 7919, y - 104729, z + 1299709) < HOLEY;
    }

    /** One chunk's share of the moon, all of it, into its copy (a builder thread: only plan maths and the copy). */
    private void fill(ChunkPos pos, Excavation.Sink sink) {
        sink.prepare();
        double r = this.plan.params.moonRadius();
        double cy = this.plan.restCentre.y;
        BlockState cheese = HalleyContent.MOON_CHEESE.get().defaultBlockState();
        BlockState holey = HalleyContent.HOLEY_MOON_CHEESE.get().defaultBlockState();
        BlockState molten = HalleyContent.MOLTEN_MOON_CHEESE.get().defaultBlockState();
        BlockPos core = this.plan.params.core() ? this.plan.core : null;

        int x0 = pos.x * 16;
        int z0 = pos.z * 16;
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
                int from = Math.max(this.fromY, Mth.ceil(cy - half - 0.5));
                int to = Math.min(this.toY, Mth.floor(cy + half - 0.5));
                for (int y = from; y <= to; y++) {
                    boolean heart = core != null && x == core.getX() && y == core.getY() && z == core.getZ();
                    sink.put(x, y, z, heart ? molten : this.holeyAt(x, y, z) ? holey : cheese);
                }
            }
        }
    }
}
