package dev.ss05.halley;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every tunable number of SS-06 Luna (the moonfall) and of moon cheese. They live in the same file as SS-05's,
 * {@code config/shooting_star_addition-common.toml}, in the sections starting with {@code moonfall}; {@link HalleyConfig}
 * builds that file and calls {@link #define} for these sections.
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

    private static ModConfigSpec.IntValue cooldownSeconds;
    private static ModConfigSpec.IntValue reach;
    private static ModConfigSpec.BooleanValue nightOnly;
    private static ModConfigSpec.IntValue moonRadius;
    private static ModConfigSpec.IntValue craterRadius;
    private static ModConfigSpec.IntValue craterDepth;
    private static ModConfigSpec.IntValue sinkPercent;
    private static ModConfigSpec.DoubleValue blastReach;
    private static ModConfigSpec.IntValue chunksPerTick;
    private static ModConfigSpec.BooleanValue carveTerrain;
    private static ModConfigSpec.BooleanValue moltenCore;
    private static ModConfigSpec.IntValue moltenHearts;
    private static ModConfigSpec.IntValue moltenStrength;
    private static ModConfigSpec.IntValue moltenMinutes;

    private MoonConfig() {
    }

    static void define(ModConfigSpec.Builder b) {
        b.comment("SS-06 Luna: at night, the Stellar Remote can shut the Earth system down and bring the moon down onto",
                "your crosshair. It leaves a moon of moon cheese half-buried in a crater, with one block of molten moon",
                "cheese at its very heart.").push("moonfall");
        cooldownSeconds = b.comment("Seconds before SS-06 can be fired again. Creative mode always uses 1 second, like the other skills.")
            .defineInRange("cooldown_seconds", 120, 1, 3600);
        reach = b.comment("How far away (in blocks) your crosshair can place the mark.")
            .defineInRange("reach", 420, 32, 1024);
        nightOnly = b.comment("Only answer at night. By default (false) it can be called any time: like SS-05, the sky falls",
                "to night for the strike (only what players see; the world's real time is untouched).")
            .define("require_night", false);
        b.pop();

        b.comment("How big it all is. Anything from a pebble to a monster works, but every block of moon and crater has to be",
                "placed or cut: a radius-200 moon is ~33 million blocks and will take the server a long while (it is",
                "built a few chunks per tick, so the game keeps running). Very big moons also stick out of the build height.").push("moonfall_size");
        moonRadius = b.comment("Radius of the moon, in blocks (the default is 96 blocks across).")
            .defineInRange("moon_radius", 48, MIN_MOON_RADIUS, MAX_MOON_RADIUS);
        craterRadius = b.comment("Radius of the crater it digs, in blocks. Everything inside it is erased.")
            .defineInRange("crater_radius", 90, MIN_CRATER_RADIUS, MAX_CRATER_RADIUS);
        craterDepth = b.comment("Depth of the crater at its centre, in blocks.")
            .defineInRange("crater_depth", 30, MIN_CRATER_DEPTH, MAX_CRATER_DEPTH);
        sinkPercent = b.comment("How much of the moon's height ends up buried below the ground, in percent.")
            .defineInRange("sink_percent", 35, 0, 90);
        blastReach = b.comment("How far the shock wave throws and hurts things, as a multiple of the crater radius.")
            .defineInRange("blast_reach", 3.0, 1.0, 6.0);
        chunksPerTick = b.comment("How many chunks of crater and moon are cut or built each tick. Higher finishes big moons",
                "sooner but makes those ticks longer.")
            .defineInRange("chunks_per_tick", 8, 1, 64);
        b.pop();

        b.comment("What the moonfall leaves behind.").push("moonfall_world");
        carveTerrain = b.comment("Dig the crater and leave the moon in the world. If false the moonfall is purely visual and",
                "only hurts creatures (handy on servers that protect their terrain).")
            .define("carve_terrain", true);
        moltenCore = b.comment("Put one block of molten moon cheese at the very centre of the moon.")
            .define("molten_core", true);
        b.pop();

        b.comment("Moon cheese. Ordinary moon cheese is eaten like a golden carrot. Molten moon cheese gives the effects below.")
            .push("moon_cheese");
        moltenHearts = b.comment("Hearts you have while the molten moon cheese lasts (vanilla players have 10).")
            .defineInRange("molten_hearts", 500, 1, 512);
        moltenStrength = b.comment("Strength level it gives (255 = Strength CCLV, the most there is).")
            .defineInRange("molten_strength", 255, 1, 255);
        moltenMinutes = b.comment("How long the molten moon cheese lasts, in minutes.")
            .defineInRange("molten_minutes", 10, 1, 120);
        b.pop();
    }

    /** An immutable snapshot of the config, taken once per moonfall (or per bite). Defaults before the config loads. */
    public record Tuning(int cooldownTicks, int reach, boolean nightOnly, int moonRadius, int craterRadius,
                         int craterDepth, int sinkPercent, double blastReach, int chunksPerTick, boolean carveTerrain,
                         boolean moltenCore, int moltenHearts, int moltenStrength, int moltenMinutes) {
        public static final Tuning DEFAULTS = new Tuning(120 * 20, 420, false, 48, 90, 30, 35, 3.0, 8, true, true, 500, 255, 10);
    }

    public static Tuning tuning() {
        if (!HalleyConfig.SPEC.isLoaded() || cooldownSeconds == null) {
            return Tuning.DEFAULTS;
        }
        try {
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
        } catch (IllegalStateException notLoadedYet) {
            return Tuning.DEFAULTS;
        }
    }
}
