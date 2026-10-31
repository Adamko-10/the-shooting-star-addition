package dev.ss05.halley.compat;

import net.minecraft.world.phys.Vec3;

/** A caster's currently running strike, as the server tracks it (for the test harness). */
public record StrikeInfo(Vec3 origin, Vec3 target, long seed) {
}
