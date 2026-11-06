package dev.ss05.halley.compat.tss;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

/** Where an operator's {@code /ssa strike ... at} aims the cast it is making (null: where the player looks). */
public final class StrikeAim {
    @Nullable
    private static Vec3 at;

    private StrikeAim() {
    }

    @Nullable
    public static Vec3 at() {
        return at;
    }

    static void set(@Nullable Vec3 target) {
        at = target;
    }
}
