package dev.ss05.halley.world;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground Luna heaves up as it drives into the earth: the slabs of crust buckled up against the moon over the
 * crater's floor, and the crater's rim with the ground thrown out past it. Every such column ({@link MoonPlan#rimHeight})
 * is lifted, in a few jolts ({@link MoonPlan#rimStep}), while the moon sinks. A lifted column is the old column (or
 * the crater's floor) pushed up: its own surface block stays on top and the layers under it show in its sides,
 * scorched black and molten against the moon, singed on the crater's inner wall. Whatever stood on the ground there
 * (plants, trees, buildings) is gone. Server side only, once the crater is dug; only loaded chunks are touched (the
 * strike keeps the impact zone loaded).
 */
final class MoonRim {
    /** Nearest the moon, the crust is burnt and partly molten. */
    private static final Palette SCORCHED = Palette.of(Blocks.BLACKSTONE, 30, Blocks.BASALT, 24, Blocks.MAGMA_BLOCK, 18,
        Blocks.COBBLED_DEEPSLATE, 14, Blocks.COARSE_DIRT, 14);
    /** What a lifted column is made of where the old column had nothing to give (a cave, water...). */
    private static final Palette FILL = Palette.of(Blocks.STONE, 40, Blocks.COBBLESTONE, 20, Blocks.TUFF, 15,
        Blocks.ANDESITE, 15, Blocks.GRAVEL, 10);

    private final ServerLevel level;
    private final MoonPlan plan;
    private final List<Column> columns = new ArrayList<>();
    private boolean done;

    private static final class Column {
        final int x;
        final int z;
        final int height;
        /** The old ground's top block's y, or Integer.MIN_VALUE until the column has been looked at. */
        int ground = Integer.MIN_VALUE;
        /** How many blocks have been raised so far. */
        int placed;
        /** The old column, top down: strata[0] is the old surface block, strata[k] the block k below it. */
        BlockState[] strata;

        Column(int x, int z, int height) {
            this.x = x;
            this.z = z;
            this.height = height;
        }
    }

    MoonRim(ServerLevel level, MoonPlan plan) {
        this.level = level;
        this.plan = plan;
        int r = Mth.ceil(plan.rimOuter) + 1;
        int cx = Mth.floor(plan.target.x);
        int cz = Mth.floor(plan.target.z);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                int h = plan.rimHeight(x, z);
                if (h > 0) {
                    this.columns.add(new Column(x, z, h));
                }
            }
        }
        this.done = this.columns.isEmpty();
    }

    boolean done() {
        return this.done;
    }

    /** Heaves every column up to where it has got by time {@code t}. */
    void tick(double t) {
        if (this.done) {
            return;
        }
        Map<BlockPos, BlockState> out = new HashMap<>();
        boolean all = true;
        for (Column c : this.columns) {
            int step = this.plan.rimStep(c.x, c.z, t);
            int want = step >= MoonPlan.RIM_STEPS ? c.height : Math.round(c.height * step / (float) MoonPlan.RIM_STEPS);
            if (want > c.placed && !this.raise(c, want, out)) {
                all = false;
            }
            if (c.placed < c.height) {
                all = false;
            }
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
        this.done = all;
    }

    /** Raises everything that is left at once (the spell was cut short). */
    void finish() {
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (Column c : this.columns) {
            if (c.placed < c.height) {
                this.raise(c, c.height, out);
            }
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
        this.done = true;
    }

    /** Lifts one column to {@code want} blocks over its old ground; false if its chunk isn't loaded (yet). */
    private boolean raise(Column c, int want, Map<BlockPos, BlockState> out) {
        if (!this.level.hasChunk(c.x >> 4, c.z >> 4)) {
            return false;
        }
        if (c.ground == Integer.MIN_VALUE && !this.survey(c, out)) {
            c.placed = c.height;
            return true;
        }
        // the old surface block rides up on top; every block under it lands as the layer it will finally be there (the
        // column's deep layers lowest), including the one that was the top until now
        if (c.placed > 0) {
            out.put(new BlockPos(c.x, c.ground + c.placed, c.z), this.material(c, c.height - c.placed, c.ground + c.placed));
        }
        for (int j = c.placed + 1; j < want; j++) {
            out.put(new BlockPos(c.x, c.ground + j, c.z), this.material(c, c.height - j, c.ground + j));
        }
        out.put(new BlockPos(c.x, c.ground + want, c.z), this.material(c, 0, c.ground + want));
        c.placed = want;
        return true;
    }

    /**
     * Finds the column's old ground (under any plants, trees, snow, water or buildings), clears what stood on it and
     * remembers its layers. False if there is no ground to lift (the void, bedrock right at the top...).
     */
    private boolean survey(Column c, Map<BlockPos, BlockState> out) {
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.x, c.z) - 1;
        int y = groundAt(this.level, c.x, c.z);
        if (y == Integer.MIN_VALUE) {
            return false;
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        c.ground = y;
        // what stood on the ground is swept away (fluids are left: the lifted crust takes their place where it rises)
        for (int above = y + 1; above <= top; above++) {
            BlockState was = this.level.getBlockState(at.set(c.x, above, c.z));
            if (!was.isAir() && was.getFluidState().isEmpty() && was.getDestroySpeed(this.level, at) >= 0.0F) {
                out.put(at.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
        c.strata = new BlockState[c.height + 1];
        for (int k = 0; k <= c.height; k++) {
            BlockState was = this.level.getBlockState(at.set(c.x, y - k, c.z));
            c.strata[k] = isGround(this.level, was) && was.getDestroySpeed(this.level, at) >= 0.0F
                ? was : null;
        }
        if (c.strata[0] != null && c.strata[0].is(Blocks.SNOW_BLOCK)) {
            c.strata[0] = Blocks.DIRT.defaultBlockState();
        }
        return true;
    }

    /** What lands at height {@code y} of a lifted column, from the old layer {@code k} blocks under its surface. */
    private BlockState material(Column c, int k, int y) {
        // the slabs against the moon are burnt black and glowing (their tops the most), the crater's inner wall singed
        double scorch = this.plan.scorch(c.x + 0.5, c.z + 0.5);
        if (this.plan.hash(c.x, y * 3 + 131, c.z) < scorch * (k == 0 ? 1.0 : 0.75)) {
            return SCORCHED.pick(this.plan.hash(c.x, y, c.z));
        }
        BlockState layer = k >= 0 && k < c.strata.length ? c.strata[k] : null;
        if (layer == null) {
            return FILL.pick(this.plan.hash(c.z, y, c.x));
        }
        // torn ground: the surface of a heaved plate is broken up here and there
        if (k == 0 && layer.is(Blocks.GRASS_BLOCK) && this.plan.hash(c.x, 137, c.z) < 0.25) {
            return this.plan.hash(c.z, 139, c.x) < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
        }
        return layer;
    }

    /**
     * The y of the land's own top block in this column, under any plants, trees, snow, water or buildings
     * ({@code Integer.MIN_VALUE} if there is none above the bottom of the world). The chunk must be loaded.
     */
    static int groundAt(ServerLevel level, int x, int z) {
        int min = level.getMinY();
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
