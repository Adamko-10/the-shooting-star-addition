package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * SS-06's damage type (the shock wave outside the crater; inside it, things are erased the way the other skills
 * erase). Defined in {@code data/shooting_star_addition/damage_type/luna_impact.json}.
 */
public final class MoonDamage {
    public static final ResourceKey<DamageType> IMPACT = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("luna_impact"));

    private MoonDamage() {
    }
}
