package dev.ss05.halley.world;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds SS-06's fallen moon out of moon cheese (holey here and there, and the one molten block at its heart), a
 * few chunks per tick, nearest the mark first, so a huge moon never freezes the server tick. Server side only. Unlike
 * {@link Excavation} (which only ever cuts blocks away above a floor), this one also places blocks, so it is its
 * own small class rather than another {@code StarBridge.excavate} shape; what to place comes straight from
 * {@link MoonPlan#inMoon}, so it is always the same sphere the client films.
 *
 * <p>Each chunk is built once, whole, nearest the mark first: every chunk written is relit and sent again to everyone
 * who has it loaded (The Shooting Star's {@code Carving.paint}), so building a chunk in two goes (its surface first, as
 * 1.5.0 did) cost twice that for nothing - the client draws its own moon a block bigger over the blocks until they are
 * all there anyway. Each column's blocks are worked out as a range of heights (no test per block), and a chunk that isn't
 * loaded is asked for through the strike's {@link Excavation.Loader} and built once it is there, never loaded on the
 * spot.
 */
public final class MoonBuild {
    private final ServerLevel level;
    private final MoonPlan plan;
    private final int chunksPerTick;
    private final Excavation.Loader loader;
    /** The most time the builder takes in one tick (at least one chunk is always built). */
    private static final long BUDGET_NANOS = 15_000_000L;
    /** How much of the moon is holey moon cheese: the cheese's eyes, scattered all through it. */
    private static final double HOLEY = 0.12;

    private final List<ChunkPos> order;
    private int next;

    public MoonBuild(ServerLevel level, MoonPlan plan, int chunksPerTick, Excavation.Loader loader) {
        this.level = level;
        this.plan = plan;
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.loader = loader;
        this.order = footprint(plan);
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
        return this.next >= this.order.size();
    }

    public void tick() {
        this.build(this.chunksPerTick, false);
    }

    /** Builds everything that is left, right now (used when the spell is cut short: this may load its chunks). */
    public void finish() {
        this.build(Integer.MAX_VALUE, true);
    }

    private void build(int count, boolean now) {
        long start = System.nanoTime();
        while (count > 0 && !this.done()) {
            if (!now && System.nanoTime() - start > BUDGET_NANOS) {
                // a big moon's chunks hold tens of thousands of blocks each: the rest wait for the next tick
                return;
            }
            ChunkPos pos = this.order.get(this.next);
            if (!now && !this.level.hasChunk(pos.x, pos.z)) {
                // wait for it (asked for once, let go of once built)
                this.loader.hold(pos);
                return;
            }
            this.buildChunk(pos);
            this.loader.release(pos);
            count--;
            this.next++;
        }
    }

    /** Whether the moon's block here is one of the cheese's holes (always the same for the same block). */
    private boolean holeyAt(int x, int y, int z) {
        return this.plan.hash(x + 7919, y - 104729, z + 1299709) < HOLEY;
    }

    /** One chunk's share of the moon, all of it. */
    private void buildChunk(ChunkPos pos) {
        double r = this.plan.params.moonRadius();
        int minY = this.level.getMinY();
        int maxY = this.level.getMaxY();
        double cy = this.plan.restCentre.y;
        BlockState cheese = HalleyContent.MOON_CHEESE.defaultBlockState();
        BlockState holey = HalleyContent.HOLEY_MOON_CHEESE.defaultBlockState();
        BlockState molten = HalleyContent.MOLTEN_MOON_CHEESE.defaultBlockState();
        boolean core = this.plan.params.core();
        Map<BlockPos, BlockState> out = new HashMap<>();

        int x0 = pos.x * 16;
        int z0 = pos.z * 16;
        for (int dx = 0; dx < 16; dx++) {
            int x = x0 + dx;
            double ex = x + 0.5 - this.plan.restCentre.x;
            for (int dz = 0; dz < 16; dz++) {
                int z = z0 + dz;
                double ez = z + 0.5 - this.plan.restCentre.z;
                double flat = ex * ex + ez * ez;
                if (flat > r * r) {
                    continue;
                }
                // the blocks whose centres lie inside the moon
                double half = Math.sqrt(r * r - flat);
                int from = Math.max(minY, Mth.ceil(cy - half - 0.5));
                int to = Math.min(maxY, Mth.floor(cy + half - 0.5));
                for (int y = from; y <= to; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    out.put(at, core && at.equals(this.plan.core) ? molten : this.holeyAt(x, y, z) ? holey : cheese);
                }
            }
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }
}
