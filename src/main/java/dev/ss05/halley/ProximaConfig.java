package dev.ss05.halley;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SA-04 Proxima's tunable numbers, in the common config ({@code config/shooting_star_addition-common.toml}) in the
 * sections starting with {@code proxima}; {@link HalleyConfig} builds that file and calls {@link #define}. The server's
 * values decide: the sizes and the radiation's length ride along in the effect packet ({@link ProximaParams}).
 */
public final class ProximaConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.IntValue cooldownSeconds;
    private static ModConfigSpec.IntValue bowlRadius;
    private static ModConfigSpec.IntValue charRadius;
    private static ModConfigSpec.IntValue scorchRadius;
    private static ModConfigSpec.IntValue bowlDepth;
    private static ModConfigSpec.BooleanValue carveTerrain;
    private static ModConfigSpec.BooleanValue solarResidue;
    private static ModConfigSpec.IntValue shelterBlocks;
    private static ModConfigSpec.DoubleValue playerKnockback;
    private static ModConfigSpec.DoubleValue mobKnockback;
    private static ModConfigSpec.IntValue radiationMinutes;
    private static ModConfigSpec.BooleanValue permanentRadiation;
    private static ModConfigSpec.DoubleValue radiationDamage;

    private ProximaConfig() {
    }

    static void define(ModConfigSpec.Builder b) {
        b.comment("SA-04 Proxima: a red dwarf in its Dyson sphere fires a solar flare, then a coronal mass ejection, at your",
                "crosshair: a wide, shallow scorch with a glassy bowl in the middle.").push("proxima");
        enabled = b.comment("Allow Proxima at all. If false the remote refuses it.")
            .define("enabled", true);
        cooldownSeconds = b.comment("Seconds before Proxima can be fired again. Creative mode always uses 1 second, like the other skills.")
            .defineInRange("cooldown_seconds", 60, 1, 3600);
        b.pop();

        b.comment("How big it is at power 1 (the remote's power slider scales every radius, and the bowl's depth by its square",
                "root). Only loaded land is changed; the rest as it loads.").push("proxima_size");
        bowlRadius = b.comment("Radius of the glassy bowl at ground zero, in blocks. Everything inside it is erased.")
            .defineInRange("bowl_radius", ProximaParams.BOWL, 4, 400);
        charRadius = b.comment("Out to here trunks char and the ground turns to blackstone and basalt; creatures are badly hurt and set alight.")
            .defineInRange("char_radius", ProximaParams.CHAR, 8, 1000);
        scorchRadius = b.comment("Out to here leaves, grass, flowers, snow and crops burn away and the topsoil scorches; creatures",
                "are hurt and staggered.")
            .defineInRange("scorch_radius", ProximaParams.SCORCH, 12, 2000);
        bowlDepth = b.comment("How deep the bowl is in its middle, in blocks.")
            .defineInRange("bowl_depth", ProximaParams.DEPTH, 1, 48);
        b.pop();

        b.comment("What Proxima does to the world and to creatures.").push("proxima_world");
        carveTerrain = b.comment("Change the land (the bowl, the scorch, the ash). If false Proxima is purely visual and only",
                "hurts creatures.")
            .define("carve_terrain", true);
        solarResidue = b.comment("Leave Solar Residue here and there in the bowl's glassy floor.")
            .define("solar_residue", true);
        shelterBlocks = b.comment("Solid blocks over a creature's head that shelter it from the flash and the blast (glass doesn't count).")
            .defineInRange("shelter_blocks", 3, 1, 32);
        playerKnockback = b.comment("The most the blast pushes a player (survival and adventure only; never the caster), in blocks",
                "per tick. 0 = never.")
            .defineInRange("player_knockback", 0.6, 0.0, 4.0);
        mobKnockback = b.comment("How hard the blast throws mobs and items, as a multiple of the usual. 0 = never.")
            .defineInRange("mob_knockback", 1.0, 0.0, 4.0);
        radiationMinutes = b.comment("How long the bowl stays dangerous at power 1, in minutes (longer at higher power); it fades",
                "out over that time. 0 = no radiation.")
            .defineInRange("radiation_minutes", 10, 0, 1440);
        permanentRadiation = b.comment("Keep the bowl dangerous for good (it never fades; kept across restarts). Only players who",
                "saw the strike see its shimmer.")
            .define("permanent_radiation", false);
        radiationDamage = b.comment("Damage per second in the middle of a fresh bowl (less toward its edge and as it fades).")
            .defineInRange("radiation_damage", 3.0, 0.0, 100.0);
        b.pop();
    }

    /** A snapshot of the config, taken once per cast. Defaults before the config loads. */
    public record Tuning(boolean enabled, int cooldownTicks, int bowlRadius, int charRadius, int scorchRadius, int bowlDepth,
                         boolean carveTerrain, boolean solarResidue, int shelterBlocks, double playerKnockback,
                         double mobKnockback, int radiationMinutes, boolean permanentRadiation, double radiationDamage) {
        public static final Tuning DEFAULTS = new Tuning(true, 60 * 20, ProximaParams.BOWL, ProximaParams.CHAR,
            ProximaParams.SCORCH, ProximaParams.DEPTH, true, true, 3, 0.6, 1.0, 10, false, 3.0);
    }

    public static Tuning tuning() {
        if (!HalleyConfig.SPEC.isLoaded() || enabled == null) {
            return Tuning.DEFAULTS;
        }
        try {
            return new Tuning(enabled.get(), cooldownSeconds.get() * 20, bowlRadius.get(), charRadius.get(), scorchRadius.get(),
                bowlDepth.get(), carveTerrain.get(), solarResidue.get(), shelterBlocks.get(), playerKnockback.get(),
                mobKnockback.get(), radiationMinutes.get(), permanentRadiation.get(), radiationDamage.get());
        } catch (IllegalStateException notLoadedYet) {
            return Tuning.DEFAULTS;
        }
    }
}
