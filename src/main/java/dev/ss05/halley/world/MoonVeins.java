package dev.ss05.halley.world;

import dev.ss05.halley.MoonPlan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The veins of molten rock across Luna's crater floor ({@link MoonPlan#crackIsVein}), as the columns they cut: worked
 * out once at the impact, then read by the crater's own carver as it digs each column (so they are part of the floor,
 * never a second pass over it). Only the veins' columns are kept (a few thousand at most).
 */
final class MoonVeins {
    /** Depth below the floor + 1 for a vein column, or {@link #LIP} for the scorched lip beside one. */
    private final Long2IntOpenHashMap columns = new Long2IntOpenHashMap();
    private static final int LIP = -1;
    private static final Palette WALL = Palette.of(Blocks.BLACKSTONE, 45, Blocks.BASALT, 35, Blocks.SMOOTH_BASALT, 20);
    private final MoonPlan plan;

    MoonVeins(MoonPlan plan) {
        this.plan = plan;
        int maxDepth = Math.max(3, Mth.ceil(plan.params.moonRadius() * 0.11));
        for (int i = 0; i < plan.crackCount(); i++) {
            if (!plan.crackIsVein(i)) {
                continue;
            }
            double step = 0.6 / Math.max(1.0, plan.crackLength(i));
            for (double along = 0.0; along <= 1.0; along += step) {
                Vec3 centre = plan.crackPoint(i, along);
                double half = plan.crackHalfWidth(i, along);
                if (half < 0.35) {
                    continue;
                }
                int reach = Mth.ceil(half + 1.5);
                int cx = Mth.floor(centre.x);
                int cz = Mth.floor(centre.z);
                for (int x = cx - reach; x <= cx + reach; x++) {
                    for (int z = cz - reach; z <= cz + reach; z++) {
                        double d = Math.hypot(x + 0.5 - centre.x, z + 0.5 - centre.z);
                        if (d > half + 1.5) {
                            continue;
                        }
                        long key = key(x, z);
                        int was = this.columns.getOrDefault(key, 0);
                        if (d > half) {
                            if (was == 0) {
                                this.columns.put(key, LIP);
                            }
                            continue;
                        }
                        double k = 1.0 - (d / half) * (d / half);
                        int depth = Math.max(1, (int) Math.round(maxDepth * (1.0 - along * 0.7) * k + plan.hash(x, 3, z) * 1.5));
                        if (depth + 1 > was) {
                            this.columns.put(key, depth + 1);
                        }
                    }
                }
            }
        }
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    /** How much deeper than the floor a vein cuts this column (0 where there is none). */
    int depth(int x, int z) {
        return Math.max(0, this.columns.get(key(x, z)) - 1);
    }

    /** The vein's own floor or lip block for this column's new top, or null where there is no vein. */
    BlockState surface(int x, int y, int z) {
        int v = this.columns.get(key(x, z));
        if (v == 0) {
            return null;
        }
        if (v == LIP) {
            return WALL.pick(this.plan.hash(x, y, z));
        }
        double roll = this.plan.hash(x, y, z);
        return v - 1 >= 3 && roll < 0.4 ? Blocks.LAVA.defaultBlockState()
            : roll < 0.8 ? Blocks.MAGMA_BLOCK.defaultBlockState() : WALL.pick(this.plan.hash(z, y, x));
    }
}
