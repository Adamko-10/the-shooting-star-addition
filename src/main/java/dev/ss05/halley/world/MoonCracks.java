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
 * The fissures SS-06 tears into the land round its crater (like Gungnir's cracks): cut along {@link MoonPlan}'s crack
 * lines, the same ones the client draws glowing, racing outward from the rim over {@link MoonPlan#CRACK_TICKS}.
 * Deepest and widest at the rim, a magma floor glowing at the bottom, scorched walls. Only loaded chunks are touched
 * (the cracks can reach past the area the strike keeps loaded, and must never make the server generate terrain).
 */
final class MoonCracks {
    private static final Palette WALL = Palette.of(Blocks.BLACKSTONE, 45, Blocks.BASALT, 35, Blocks.SMOOTH_BASALT, 20);

    private final ServerLevel level;
    private final MoonPlan plan;
    /** How far (0..1) each crack has been cut so far. */
    private double cut;

    MoonCracks(ServerLevel level, MoonPlan plan) {
        this.level = level;
        this.plan = plan;
    }

    boolean done() {
        return this.cut >= 1.0;
    }

    /** Cuts every crack out to where it has run by time {@code t}. */
    void tick(double t) {
        this.cutTo(MoonPlan.crackFront(t));
    }

    /** Cuts what is left at once (the spell was cut short). */
    void finish() {
        this.cutTo(1.0);
    }

    private void cutTo(double front) {
        if (front <= this.cut) {
            return;
        }
        Map<BlockPos, BlockState> out = new HashMap<>();
        int maxDepth = Math.max(2, Mth.ceil(this.plan.params.craterDepth() * 0.45));
        for (int i = 0; i < MoonPlan.CRACKS; i++) {
            double length = this.plan.crackLength(i);
            double step = 0.6 / Math.max(1.0, length);
            for (double along = this.cut; along <= front; along += step) {
                this.cutAt(i, along, maxDepth, out);
            }
        }
        this.cut = front;
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }

    private void cutAt(int i, double along, int maxDepth, Map<BlockPos, BlockState> out) {
        var centre = this.plan.crackPoint(i, along);
        double half = this.plan.crackHalfWidth(i, along);
        if (half < 0.35) {
            return;
        }
        int reach = Mth.ceil(half + 1.0);
        int cx = Mth.floor(centre.x);
        int cz = Mth.floor(centre.z);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                double d = Math.hypot(x + 0.5 - centre.x, z + 0.5 - centre.z);
                if (d > half + 1.0 || !this.level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (top <= this.level.getMinBuildHeight() + 4) {
                    continue;
                }
                if (d > half) {
                    // the lip: scorch the ground right at the edge of the fissure
                    this.put(out, new BlockPos(x, top, z), WALL.pick(this.plan.hash(x, top, z)));
                    continue;
                }
                double k = 1.0 - (d / half) * (d / half);
                int depth = Math.max(1, (int) Math.round(maxDepth * (1.0 - along * 0.75) * k + this.plan.hash(x, 3, z) * 1.5));
                int floor = top - depth;
                for (int y = floor + 1; y <= top; y++) {
                    this.put(out, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                this.put(out, new BlockPos(x, floor, z), this.plan.hash(x, floor, z) < 0.7
                    ? Blocks.MAGMA_BLOCK.defaultBlockState() : WALL.pick(this.plan.hash(z, floor, x)));
            }
        }
    }

    /** Never cuts into fluids, block entities (chests...) or bedrock-like blocks. */
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
