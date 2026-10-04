package dev.ss05.halley.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A weighted list of blocks to paint a surface with. Weights don't need to add up to anything; a {@code null} block
 * means "leave the block that was there".
 */
public final class Palette {
    private final BlockState[] states;
    private final double[] upTo;

    private Palette(BlockState[] states, double[] upTo) {
        this.states = states;
        this.upTo = upTo;
    }

    /** Pairs of (block or null, weight). */
    public static Palette of(Object... blockThenWeight) {
        int n = blockThenWeight.length / 2;
        BlockState[] states = new BlockState[n];
        double[] upTo = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            Object block = blockThenWeight[i * 2];
            states[i] = block == null ? null : ((Block) block).defaultBlockState();
            total += ((Number) blockThenWeight[i * 2 + 1]).doubleValue();
            upTo[i] = total;
        }
        for (int i = 0; i < n; i++) {
            upTo[i] /= total;
        }
        return new Palette(states, upTo);
    }

    /** {@code roll} in 0..1. */
    public BlockState pick(double roll) {
        for (int i = 0; i < this.upTo.length; i++) {
            if (roll < this.upTo[i]) {
                return this.states[i];
            }
        }
        return this.states[this.states.length - 1];
    }
}
