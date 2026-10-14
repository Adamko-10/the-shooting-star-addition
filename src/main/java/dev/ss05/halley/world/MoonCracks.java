package dev.ss05.halley.world;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The fissures Luna tears into the land round the crater (like Gungnir's cracks), and the veins of molten rock across
 * its floor: cut along {@link MoonPlan}'s crack lines, the same ones the client draws glowing, racing outward and
 * branching as they go. Deepest and widest where they leave the rim; a floor of magma with lava pooled in the deep
 * stretches, scorched walls and a buckled, burnt lip.
 * Only loaded chunks are touched (the cracks can reach past the area the strike keeps loaded, and must never make the
 * server generate terrain).
 */
final class MoonCracks {
    private static final Palette WALL = Palette.of(Blocks.BLACKSTONE, 45, Blocks.BASALT, 35, Blocks.SMOOTH_BASALT, 20);

    private final ServerLevel level;
    private final MoonPlan plan;
    /** How far (0..1) each crack has been cut so far. */
    private final double[] cut;
    private boolean done;

    MoonCracks(ServerLevel level, MoonPlan plan) {
        this.level = level;
        this.plan = plan;
        this.cut = new double[plan.crackCount()];
    }

    boolean done() {
        return this.done;
    }

    /** Cuts every crack out to where it has run by time {@code t}. */
    void tick(double t) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        boolean all = true;
        for (int i = 0; i < this.cut.length; i++) {
            this.cutTo(i, this.plan.crackFront(i, t), out);
            all &= this.cut[i] >= 1.0;
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
        this.done = all;
    }

    /** Cuts what is left at once (the spell was cut short). */
    void finish() {
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (int i = 0; i < this.cut.length; i++) {
            this.cutTo(i, 1.0, out);
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
        this.done = true;
    }

    private void cutTo(int i, double front, Map<BlockPos, BlockState> out) {
        if (front <= this.cut[i]) {
            return;
        }
        // (the veins across the crater's floor are shallow, like the branches)
        boolean slight = this.plan.crackIsBranch(i) || this.plan.crackIsVein(i);
        int maxDepth = Math.max(3, Mth.ceil(this.plan.params.moonRadius() * (slight ? 0.11 : 0.18)));
        double step = 0.6 / Math.max(1.0, this.plan.crackLength(i));
        for (double along = this.cut[i]; along <= front; along += step) {
            this.cutAt(i, along, maxDepth, out);
        }
        this.cut[i] = front;
    }

    private void cutAt(int i, double along, int maxDepth, Map<BlockPos, BlockState> out) {
        var centre = this.plan.crackPoint(i, along);
        double half = this.plan.crackHalfWidth(i, along);
        if (half < 0.35) {
            return;
        }
        int reach = Mth.ceil(half + 1.5);
        int cx = Mth.floor(centre.x);
        int cz = Mth.floor(centre.z);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                double d = Math.hypot(x + 0.5 - centre.x, z + 0.5 - centre.z);
                if (d > half + 1.5 || !this.level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (top < this.level.getMinBuildHeight()) {
                    continue;
                }
                if (d > half) {
                    // the lip: the ground at the edge of the fissure is scorched, and here and there buckled up a block
                    this.put(out, new BlockPos(x, top, z), WALL.pick(this.plan.hash(x, top, z)));
                    if (d < half + 1.0 && this.plan.hash(x, 7, z) < 0.3) {
                        this.put(out, new BlockPos(x, top + 1, z), WALL.pick(this.plan.hash(z, top, x)));
                    }
                    continue;
                }
                double k = 1.0 - (d / half) * (d / half);
                int depth = Math.max(1, (int) Math.round(maxDepth * (1.0 - along * 0.7) * k + this.plan.hash(x, 3, z) * 1.5));
                int floor = top - depth;
                for (int y = floor + 1; y <= top; y++) {
                    this.put(out, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                // lava only pools where the fissure is deep enough to hold it
                double roll = this.plan.hash(x, floor, z);
                BlockState bottom = depth >= 3 && roll < 0.4 ? Blocks.LAVA.defaultBlockState()
                    : roll < 0.8 ? Blocks.MAGMA_BLOCK.defaultBlockState() : WALL.pick(this.plan.hash(z, floor, x));
                this.put(out, new BlockPos(x, floor, z), bottom);
            }
        }
    }

    /** Never cuts into fluids, block entities (chests...) or unbreakable blocks. */
    private void put(Map<BlockPos, BlockState> out, BlockPos at, BlockState state) {
        if (out.containsKey(at)) {
            return;
        }
        BlockState was = this.level.getBlockState(at);
        if (!was.getFluidState().isEmpty() || was.hasBlockEntity() || was.getDestroySpeed(this.level, at) < 0.0F) {
            return;
        }
        out.put(at, state);
    }
}
