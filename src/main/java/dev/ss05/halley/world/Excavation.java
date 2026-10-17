package dev.ss05.halley.world;

import net.minecraft.world.level.ChunkPos;
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

    /**
     * Leaves what is left to be cut as its chunks load, after the spell (The Shooting Star's lingering carvings, ticked
     * by its spell engine): for a carving that only cuts loaded land, so none is ever loaded for it.
     */
    void linger();

    /**
     * Keeps the chunks an excavation is waiting for loaded, a few at a time, and lets each go once it is cut (The
     * Shooting Star's "streaming" carver: a big strike never loads its whole area at once).
     */
    interface Loader {
        void hold(ChunkPos pos);

        void release(ChunkPos pos);
    }

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
