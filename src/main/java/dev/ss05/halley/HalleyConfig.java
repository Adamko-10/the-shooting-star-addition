package dev.ss05.halley;

import dev.ss05.halley.config.ConfigFile;

/**
 * Every tunable number of SS-05, in one place.
 *
 * <p>Written to {@code config/shooting_star_addition-common.toml} the first time the game starts. Edit that file (or
 * change the defaults here and rebuild) - nothing else in the addon hard-codes these values. The file is read when
 * the game starts and again whenever a world or server starts.
 *
 * <p>The server's values decide everything: the strike reads them when it is fired and sends the sizes to every
 * client with the strike, so players with a different config still see the right crater.
 */
public final class HalleyConfig {
    public static final ConfigFile SPEC = new ConfigFile(HalleyAddon.MOD_ID + "-common.toml");

    private static final ConfigFile.IntValue COOLDOWN_SECONDS;
    private static final ConfigFile.IntValue TRENCH_LENGTH;
    private static final ConfigFile.IntValue TRENCH_WIDTH;
    private static final ConfigFile.IntValue TRENCH_DEPTH;
    private static final ConfigFile.IntValue CRATER_RADIUS;
    private static final ConfigFile.IntValue CRATER_DEPTH;
    private static final ConfigFile.DoubleValue BLAST_REACH;
    private static final ConfigFile.IntValue REACH;
    private static final ConfigFile.BoolValue CARVE_TERRAIN;
    private static final ConfigFile.BoolValue LEAVE_COMET_HEART;
    private static final ConfigFile.BoolValue FROST_RAYS;
    private static final ConfigFile.BoolValue EVACUATE_CREATIVE;

    static {
        ConfigFile b = SPEC;

        b.section("skill", "SS-05 Halley: a comet called down onto your crosshair. It comes in low over the horizon in front of",
            "you, touches down beyond the mark, ploughs a trench toward you and detonates on the mark.");
        COOLDOWN_SECONDS = b.defineInRange("cooldown_seconds", 60, 1, 3600,
            "Seconds before SS-05 can be fired again (the other Stellar Remote skills use 60).",
            "Creative mode always uses 1 second, like the other skills.");
        REACH = b.defineInRange("reach", 420, 32, 1024, "How far away (in blocks) your crosshair can place the mark.");

        b.section("impact", "The shape of the scar. Bigger numbers mean more blocks to cut, so very large values cost more time",
            "on the server tick the comet lands.");
        TRENCH_LENGTH = b.defineInRange("trench_length", 240, 32, 640, "Length of the trench the comet ploughs before it reaches the mark, in blocks.");
        TRENCH_WIDTH = b.defineInRange("trench_width", 30, 8, 64, "Width of the trench where it meets the crater, in blocks (it starts narrower).");
        TRENCH_DEPTH = b.defineInRange("trench_depth", 22, 2, 64, "Depth of the trench at its deepest, in blocks.");
        CRATER_RADIUS = b.defineInRange("crater_radius", 56, 12, 128, "Radius of the crater on the mark, in blocks. Everything inside it is erased.");
        CRATER_DEPTH = b.defineInRange("crater_depth", 24, 4, 96, "Depth of the crater, in blocks.");
        BLAST_REACH = b.defineInRange("blast_reach", 2.6, 1.0, 5.0,
            "How far the shock wave throws, hurts and frosts things, as a multiple of the crater radius.");

        b.section("world", "What the strike leaves behind.");
        CARVE_TERRAIN = b.define("carve_terrain", true, "Cut the trench and crater into the world. If false the strike is purely visual and",
            "only hurts creatures (handy on servers that protect their terrain).");
        LEAVE_COMET_HEART = b.define("leave_comet_heart", true,
            "Leave the glowing comet heart crystal in the crater and the frozen trail down the trench.");
        FROST_RAYS = b.define("frost_rays", true, "Paint rays of snow out from the crater, like ejecta round a fresh lunar crater.");

        b.section("safety", "Safety.");
        EVACUATE_CREATIVE = b.define("evacuate_creative_caster", true,
            "Lift a creative-mode caster clear if they stand inside the crater, like the other skills do.");
    }

    private HalleyConfig() {
    }

    /** An immutable snapshot of the config, taken once per strike. Falls back to the defaults before the config loads. */
    public record Tuning(int cooldownTicks, int reach, int trenchLength, int trenchWidth, int trenchDepth,
                         int craterRadius, int craterDepth, double blastReach, boolean carveTerrain,
                         boolean leaveHeart, boolean frostRays, boolean evacuateCreative) {
        public static final Tuning DEFAULTS = new Tuning(60 * 20, 420, 240, 30, 22, 56, 24, 2.6, true, true, true, true);
    }

    public static Tuning tuning() {
        if (!SPEC.isLoaded()) {
            return Tuning.DEFAULTS;
        }
        return new Tuning(
            COOLDOWN_SECONDS.get() * 20,
            REACH.get(),
            TRENCH_LENGTH.get(),
            TRENCH_WIDTH.get(),
            TRENCH_DEPTH.get(),
            CRATER_RADIUS.get(),
            CRATER_DEPTH.get(),
            BLAST_REACH.get(),
            CARVE_TERRAIN.get(),
            LEAVE_COMET_HEART.get(),
            FROST_RAYS.get(),
            EVACUATE_CREATIVE.get());
    }
}
