package dev.ss05.halley.world;

import net.minecraft.world.level.block.state.BlockState;

/** A shape being cut out of the world a few chunks per tick (backed by The Shooting Star's carver). */
public interface Excavation {
    /** Cut the chunks whose nearest point lies within {@code front} blocks of the centre. */
    void tick(double front);

    default void tick() {
        this.tick(Double.MAX_VALUE);
    }

    boolean done();

    /** Cut everything that is left, now (used when the spell is cut short). */
    void finish();

    @FunctionalInterface
    interface Floor {
        /** The y of the new top block of this column, or HalleyPlan.UNTOUCHED to leave the column alone. */
        int floor(int x, int z);
    }

    @FunctionalInterface
    interface Surface {
        /** What the new top block should become, or null to keep it. */
        BlockState at(int x, int y, int z, BlockState was);
    }
}
