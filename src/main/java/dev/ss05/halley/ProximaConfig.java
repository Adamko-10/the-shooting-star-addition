package dev.ss05.halley;

import dev.ss05.halley.config.ConfigFile;

/**
 * SA-04 Proxima's tunable numbers, in the common config ({@code config/shooting_star_addition-common.toml}) in the
 * sections starting with {@code proxima}; {@link HalleyConfig} builds that file and calls {@link #define}. The server's
 * values decide: the sizes and the radiation's length ride along in the effect packet ({@link ProximaParams}).
 */
public final class ProximaConfig {
    private static ConfigFile.BoolValue enabled;
    private static ConfigFile.IntValue cooldownSeconds;
    private static ConfigFile.IntValue bowlRadius;
    private static ConfigFile.IntValue charRadius;
    private static ConfigFile.IntValue scorchRadius;
    private static ConfigFile.IntValue bowlDepth;
    private static ConfigFile.BoolValue carveTerrain;
    private static ConfigFile.BoolValue solarResidue;
    private static ConfigFile.IntValue shelterBlocks;
    private static ConfigFile.DoubleValue playerKnockback;
    private static ConfigFile.DoubleValue mobKnockback;
    private static ConfigFile.IntValue radiationMinutes;
    private static ConfigFile.BoolValue permanentRadiation;
    private static ConfigFile.DoubleValue radiationDamage;
    private static ConfigFile.BoolValue randomFlares;
    private static ConfigFile.IntValue averageMinutes;
    private static ConfigFile.IntValue minMinutes;
    private static ConfigFile.EnumValue<FlareTier> maxTier;
    private static ConfigFile.IntValue warningSeconds;
    private static ConfigFile.BoolValue flaresBurnPlants;

    /** How bad a random flare may get (see world/SolarFlares). */
    public enum FlareTier {
        /** Only the sky lights up. */
        MINOR,
        /** Its light burns: creatures under the open sky catch fire, plants burn away. No crater. */
        MAJOR,
        /** The whole strike: the flash, the coronal front, ground zero, the bowl and the scorch. */
        CATACLYSM
    }

    private ProximaConfig() {
    }

    static void define(ConfigFile b) {
        b.section("proxima", "SA-04 Proxima: a red dwarf in its Dyson sphere fires a solar flare, then a coronal mass ejection, at your",
                "crosshair: a wide, shallow scorch with a glassy bowl in the middle.");
        enabled = b.define("enabled", true, "Allow Proxima at all. If false the remote refuses it.");
        cooldownSeconds = b.defineInRange("cooldown_seconds", 60, 1, 3600, "Seconds before Proxima can be fired again. Creative mode always uses 1 second, like the other skills.");

        b.section("proxima_size", "How big it is at power 1 (the remote's power slider scales every radius, and the bowl's depth by its square",
                "root). Only loaded land is changed; the rest as it loads.");
        bowlRadius = b.defineInRange("bowl_radius", ProximaParams.BOWL, 4, 400, "Radius of the glassy bowl at ground zero, in blocks. Everything inside it is erased.");
        charRadius = b.defineInRange("char_radius", ProximaParams.CHAR, 8, 1000, "Out to here trunks char and the ground turns to blackstone and basalt; creatures are badly hurt and set alight.");
        scorchRadius = b.defineInRange("scorch_radius", ProximaParams.SCORCH, 12, 2000, "Out to here leaves, grass, flowers, snow and crops burn away and the topsoil scorches; creatures",
                "are hurt and staggered.");
        bowlDepth = b.defineInRange("bowl_depth", ProximaParams.DEPTH, 1, 48, "How deep the bowl is in its middle, in blocks.");

        b.section("proxima_world", "What Proxima does to the world and to creatures.");
        carveTerrain = b.define("carve_terrain", true, "Change the land (the bowl, the scorch, the ash). If false Proxima is purely visual and only",
                "hurts creatures.");
        solarResidue = b.define("solar_residue", true, "Leave Solar Residue here and there in the bowl's glassy floor.");
        shelterBlocks = b.defineInRange("shelter_blocks", 3, 1, 32, "Solid blocks over a creature's head that shelter it from the flash and the blast (glass doesn't count).");
        playerKnockback = b.defineInRange("player_knockback", 0.6, 0.0, 4.0, "The most the blast pushes a player (survival and adventure only; never the caster), in blocks",
                "per tick. 0 = never.");
        mobKnockback = b.defineInRange("mob_knockback", 1.0, 0.0, 4.0, "How hard the blast throws mobs and items, as a multiple of the usual. 0 = never.");
        radiationMinutes = b.defineInRange("radiation_minutes", 10, 0, 1440, "How long the bowl stays dangerous at power 1, in minutes (longer at higher power); it fades",
                "out over that time. 0 = no radiation.");
        permanentRadiation = b.define("permanent_radiation", false, "Keep the bowl dangerous for good (it never fades; kept across restarts). Only players who",
                "saw the strike see its shimmer.");
        radiationDamage = b.defineInRange("radiation_damage", 3.0, 0.0, 100.0, "Damage per second in the middle of a fresh bowl (less toward its edge and as it fades).");

        b.section("proxima_flares", "Random solar flares: now and then, with nobody casting it, Proxima flares near a player in the overworld.",
                "Each is announced in chat first (a space weather alert), so players can take cover. Three tiers: minor (only",
                "the sky lights up), major (its light burns creatures under the open sky and the plants round it) and",
                "cataclysm (the whole strike, crater and all: only if max_tier allows it).");
        randomFlares = b.define("random_flares", false, "Let random flares happen at all (off by default).");
        averageMinutes = b.defineInRange("average_minutes", 120, 5, 10080, "On average one flare every this many minutes (while players are in the overworld).");
        minMinutes = b.defineInRange("min_minutes_between", 30, 1, 10080, "Never two flares less than this many minutes apart.");
        maxTier = b.defineEnum("max_tier", FlareTier.MAJOR, "The worst a random flare may be: MINOR, MAJOR or CATACLYSM. Most are minor, some major, few cataclysms.");
        warningSeconds = b.defineInRange("warning_seconds", 60, 5, 600, "Seconds between the alert in chat and the flare.");
        flaresBurnPlants = b.define("major_burns_plants", true, "A major flare burns the plants away round where it falls (leaves, grass, flowers, crops). Off: it",
                "only burns creatures. carve_terrain = false turns this off too.");
    }

    /** A snapshot of the config, taken once per cast. Defaults before the config loads. */
    public record Tuning(boolean enabled, int cooldownTicks, int bowlRadius, int charRadius, int scorchRadius, int bowlDepth,
                         boolean carveTerrain, boolean solarResidue, int shelterBlocks, double playerKnockback,
                         double mobKnockback, int radiationMinutes, boolean permanentRadiation, double radiationDamage,
                         boolean randomFlares, int averageMinutes, int minMinutes, FlareTier maxTier, int warningSeconds,
                         boolean flaresBurnPlants) {
        public static final Tuning DEFAULTS = new Tuning(true, 60 * 20, ProximaParams.BOWL, ProximaParams.CHAR,
            ProximaParams.SCORCH, ProximaParams.DEPTH, true, true, 3, 0.6, 1.0, 10, false, 3.0, false, 120, 30, FlareTier.MAJOR,
            60, true);
    }

    public static Tuning tuning() {
        if (!HalleyConfig.SPEC.isLoaded() || enabled == null) {
            return Tuning.DEFAULTS;
        }
        try {
            return new Tuning(enabled.get(), cooldownSeconds.get() * 20, bowlRadius.get(), charRadius.get(), scorchRadius.get(),
                bowlDepth.get(), carveTerrain.get(), solarResidue.get(), shelterBlocks.get(), playerKnockback.get(),
                mobKnockback.get(), radiationMinutes.get(), permanentRadiation.get(), radiationDamage.get(), randomFlares.get(),
                averageMinutes.get(), minMinutes.get(), maxTier.get(), warningSeconds.get(), flaresBurnPlants.get());
        } catch (IllegalStateException notLoadedYet) {
            return Tuning.DEFAULTS;
        }
    }
}
