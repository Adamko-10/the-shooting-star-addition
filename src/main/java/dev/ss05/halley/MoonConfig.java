package dev.ss05.halley;

import dev.ss05.halley.config.ConfigFile;

/**
 * Every tunable number of SS-06 Luna (the moonfall) and of moon cheese. They live in the same file as SS-05's,
 * {@code config/shooting_star_addition-common.toml}, in the sections starting with {@code moonfall}; {@link #define}
 * is called from {@link HalleyConfig}'s static initializer to add them to the shared {@link ConfigFile}.
 *
 * <p>Like SS-05, the server's values decide everything: the moonfall reads them when it is called and sends the sizes
 * to every client with it.
 */
public final class MoonConfig {
    public static final int MIN_MOON_RADIUS = 2;
    public static final int MAX_MOON_RADIUS = 200;
    public static final int MIN_CRATER_RADIUS = 4;
    public static final int MAX_CRATER_RADIUS = 320;
    public static final int MIN_CRATER_DEPTH = 1;
    public static final int MAX_CRATER_DEPTH = 256;

    private static ConfigFile.IntValue cooldownSeconds;
    private static ConfigFile.IntValue reach;
    private static ConfigFile.BoolValue nightOnly;
    private static ConfigFile.IntValue moonRadius;
    private static ConfigFile.IntValue craterRadius;
    private static ConfigFile.IntValue craterDepth;
    private static ConfigFile.IntValue sinkPercent;
    private static ConfigFile.DoubleValue blastReach;
    private static ConfigFile.IntValue chunksPerTick;
    private static ConfigFile.BoolValue carveTerrain;
    private static ConfigFile.BoolValue moltenCore;
    private static ConfigFile.IntValue moltenHearts;
    private static ConfigFile.IntValue moltenStrength;
    private static ConfigFile.IntValue moltenMinutes;

    private MoonConfig() {
    }

    /** Adds SS-06's sections to {@code b} (the shared {@code shooting_star_addition-common.toml} spec). */
    static void define(ConfigFile b) {
        b.section("moonfall", "SS-06 Luna: at night, the Stellar Remote can shut the Earth system down and bring the moon down onto",
            "your crosshair. It leaves a moon of moon cheese half-buried in a crater, with one block of molten moon",
            "cheese at its very heart.");
        cooldownSeconds = b.defineInRange("cooldown_seconds", 120, 1, 3600,
            "Seconds before SS-06 can be fired again. Creative mode always uses 1 second, like the other skills.");
        reach = b.defineInRange("reach", 420, 32, 1024, "How far away (in blocks) your crosshair can place the mark.");
        nightOnly = b.define("night_only", true, "Only answer at night (when the moon is up). If false it can be called any time; by day the",
            "moon comes down out of the daylight sky.");

        b.section("moonfall_size", "How big it all is. Anything from a pebble to a monster works, but every block of moon and crater has to be",
            "placed or cut: a radius-200 moon is ~33 million blocks and will take the server a long while (it is",
            "built a few chunks per tick, so the game keeps running). Very big moons also stick out of the build height.");
        moonRadius = b.defineInRange("moon_radius", 32, MIN_MOON_RADIUS, MAX_MOON_RADIUS, "Radius of the moon, in blocks (the default is 64 blocks across).");
        craterRadius = b.defineInRange("crater_radius", 60, MIN_CRATER_RADIUS, MAX_CRATER_RADIUS, "Radius of the crater it digs, in blocks. Everything inside it is erased.");
        craterDepth = b.defineInRange("crater_depth", 22, MIN_CRATER_DEPTH, MAX_CRATER_DEPTH, "Depth of the crater at its centre, in blocks.");
        sinkPercent = b.defineInRange("sink_percent", 35, 0, 90, "How much of the moon's height ends up buried below the ground, in percent.");
        blastReach = b.defineInRange("blast_reach", 2.5, 1.0, 6.0, "How far the shock wave throws and hurts things, as a multiple of the crater radius.");
        chunksPerTick = b.defineInRange("chunks_per_tick", 8, 1, 64, "How many chunks of crater and moon are cut or built each tick. Higher finishes big moons",
            "sooner but makes those ticks longer.");

        b.section("moonfall_world", "What the moonfall leaves behind.");
        carveTerrain = b.define("carve_terrain", true, "Dig the crater and leave the moon in the world. If false the moonfall is purely visual and",
            "only hurts creatures (handy on servers that protect their terrain).");
        moltenCore = b.define("molten_core", true, "Put one block of molten moon cheese at the very centre of the moon.");

        b.section("moon_cheese", "Moon cheese. Ordinary moon cheese is eaten like a golden carrot. Molten moon cheese gives the effects below.");
        moltenHearts = b.defineInRange("molten_hearts", 500, 1, 512, "Hearts you have while the molten moon cheese lasts (vanilla players have 10).");
        moltenStrength = b.defineInRange("molten_strength", 255, 1, 255, "Strength level it gives (255 = Strength CCLV, the most there is).");
        moltenMinutes = b.defineInRange("molten_minutes", 10, 1, 120, "How long the molten moon cheese lasts, in minutes.");
    }

    /** An immutable snapshot of the config, taken once per moonfall (or per bite). Defaults before the config loads. */
    public record Tuning(int cooldownTicks, int reach, boolean nightOnly, int moonRadius, int craterRadius,
                         int craterDepth, int sinkPercent, double blastReach, int chunksPerTick, boolean carveTerrain,
                         boolean moltenCore, int moltenHearts, int moltenStrength, int moltenMinutes) {
        public static final Tuning DEFAULTS = new Tuning(120 * 20, 420, true, 32, 60, 22, 35, 2.5, 8, true, true, 500, 255, 10);
    }

    public static Tuning tuning() {
        if (!HalleyConfig.SPEC.isLoaded() || cooldownSeconds == null) {
            return Tuning.DEFAULTS;
        }
        return new Tuning(
            cooldownSeconds.get() * 20,
            reach.get(),
            nightOnly.get(),
            moonRadius.get(),
            craterRadius.get(),
            craterDepth.get(),
            sinkPercent.get(),
            blastReach.get(),
            chunksPerTick.get(),
            carveTerrain.get(),
            moltenCore.get(),
            moltenHearts.get(),
            moltenStrength.get(),
            moltenMinutes.get());
    }
}
