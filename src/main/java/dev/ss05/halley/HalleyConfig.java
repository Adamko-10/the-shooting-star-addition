package dev.ss05.halley;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Every tunable number of SS-05, in one place (SS-06's are in {@link MoonConfig}, in the same file).
 *
 * <p>Forge writes these to {@code config/shooting_star_addition-common.toml} the first time the game starts. Edit that file
 * (or change the defaults here and rebuild) - nothing else in the addon hard-codes these values.
 *
 * <p>The server's values decide everything: the strike reads them when it is fired and sends the sizes to every
 * client with the strike, so players with a different config still see the right crater.
 */
public final class HalleyConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    private static final ForgeConfigSpec.IntValue TRENCH_LENGTH;
    private static final ForgeConfigSpec.IntValue TRENCH_WIDTH;
    private static final ForgeConfigSpec.IntValue TRENCH_DEPTH;
    private static final ForgeConfigSpec.IntValue CRATER_RADIUS;
    private static final ForgeConfigSpec.IntValue CRATER_DEPTH;
    private static final ForgeConfigSpec.DoubleValue BLAST_REACH;
    private static final ForgeConfigSpec.IntValue REACH;
    private static final ForgeConfigSpec.BooleanValue CARVE_TERRAIN;
    private static final ForgeConfigSpec.BooleanValue LEAVE_COMET_HEART;
    private static final ForgeConfigSpec.BooleanValue FROST_RAYS;
    private static final ForgeConfigSpec.BooleanValue EVACUATE_CREATIVE;
    private static final ForgeConfigSpec.BooleanValue NO_COOLDOWN;
    private static final ForgeConfigSpec.BooleanValue NO_COOLDOWN_SURVIVAL;
    private static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    private static final ForgeConfigSpec.IntValue DEFAULTS_VERSION;
    private static final ForgeConfigSpec.DoubleValue MAX_POWER;
    private static final ForgeConfigSpec.DoubleValue MAX_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue MAX_SPEED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Halley: a comet called down onto your crosshair. It comes in low over the horizon in front of",
                "you, touches down beyond the mark, ploughs a trench toward you and detonates on the mark.").push("skill");
        COOLDOWN_SECONDS = b.comment("Seconds before Halley can be fired again (the other Stellar Remote skills use 60).",
                "Creative mode always uses 1 second, like the other skills.")
            .defineInRange("cooldown_seconds", 60, 1, 3600);
        REACH = b.comment("How far away (in blocks) your crosshair can place the mark.")
            .defineInRange("reach", 420, 32, 1024);
        b.pop();

        b.comment("The shape of the scar. Bigger numbers mean more blocks to cut, so very large values cost more time",
                "on the server tick the comet lands.").push("impact");
        TRENCH_LENGTH = b.comment("Length of the trench the comet ploughs before it reaches the mark, in blocks.")
            .defineInRange("trench_length", 240, 32, 640);
        TRENCH_WIDTH = b.comment("Width of the trench where it meets the crater, in blocks (it starts narrower).")
            .defineInRange("trench_width", 40, 8, 64);
        TRENCH_DEPTH = b.comment("Depth of the trench at its deepest, in blocks.")
            .defineInRange("trench_depth", 28, 2, 64);
        CRATER_RADIUS = b.comment("Radius of the crater on the mark, in blocks. Everything inside it is erased.")
            .defineInRange("crater_radius", 84, 12, 128);
        CRATER_DEPTH = b.comment("Depth of the crater, in blocks.")
            .defineInRange("crater_depth", 32, 4, 96);
        BLAST_REACH = b.comment("How far the shock wave throws, hurts and frosts things, as a multiple of the crater radius.")
            .defineInRange("blast_reach", 3.4, 1.0, 5.0);
        CHUNKS_PER_TICK = b.comment("How many chunks of the trench are cut each tick (the crater cuts twice as many). Higher finishes",
                "big strikes sooner but makes each tick heavier; lower is gentler on a busy server.")
            .defineInRange("chunks_per_tick", 12, 1, 64);
        DEFAULTS_VERSION = b.comment("Which of the addon's default sizes this file was last brought up to; the addon sets it, leave it.",
                "1.9.0 made Halley's crater 1.5x as big and its blast reach much further: a file that still held 1.8's",
                "sizes untouched was moved to the new ones once (a file with sizes of its own is left as it is).")
            .defineInRange("defaults_version", 1, 1, 99);
        b.pop();

        b.comment("What the strike leaves behind.").push("world");
        CARVE_TERRAIN = b.comment("Cut the trench and crater into the world. If false the strike is purely visual and",
                "only hurts creatures (handy on servers that protect their terrain).")
            .define("carve_terrain", true);
        LEAVE_COMET_HEART = b.comment("Leave the glowing comet heart crystal in the crater and the frozen trail down the trench.")
            .define("leave_comet_heart", true);
        FROST_RAYS = b.comment("Paint rays of snow out from the crater, like ejecta round a fresh lunar crater.")
            .define("frost_rays", true);
        b.pop();

        b.comment("Safety.").push("safety");
        EVACUATE_CREATIVE = b.comment("Lift a creative-mode caster clear if they stand inside the crater, like the other skills do.")
            .define("evacuate_creative_caster", true);
        b.pop();

        MoonConfig.define(b);
        ProximaConfig.define(b);

        b.comment("The Stellar Remote itself.").push("stellar_remote");
        NO_COOLDOWN = b.comment("Lets players switch off the cooldown of every skill on the Stellar Remote - The Shooting Star's own",
                "as well as Halley and Luna: each can be cast again straight away (Halley and Luna even while one is still",
                "coming down). Each player turns it on or off for themselves in the remote's menu (H, the C key; on by",
                "default). Be careful with this: it lets the most destructive skills be cast back to back.")
            .define("no_cooldown", false);
        NO_COOLDOWN_SURVIVAL = b.comment("Whether survival (and adventure) mode players may use no_cooldown too. If false, only",
                "creative and spectator players can.")
            .define("no_cooldown_survival", false);
        MAX_POWER = b.comment("The most Power any player can cast a skill with, as a multiple of that skill's default power",
                "(for Halley and Luna, 1 is their configured size above and 10 is the slider's end). Every skill on the remote,",
                "The Shooting Star's own as well. 0 (the default) means no limit.")
            .defineInRange("max_power", 0.0, 0.0, 10.0);
        MAX_DAMAGE = b.comment("The most Damage any skill on the remote can do with each hit. A cast with the slider at",
                "infinity (which erases whatever it hits) is held to this too. 0 (the default) means no limit.")
            .defineInRange("max_damage", 0.0, 0.0, 100000.0);
        MAX_SPEED = b.comment("The fastest Speed any skill on the remote can be cast at (the slider goes from 0.3 to 3).")
            .defineInRange("max_speed", 3.0, 0.3, 3.0);
        b.pop();
        SPEC = b.build();
    }

    private HalleyConfig() {
    }

    /** An immutable snapshot of the config, taken once per strike. Falls back to the defaults before the config loads. */
    public record Tuning(int cooldownTicks, int reach, int trenchLength, int trenchWidth, int trenchDepth,
                         int craterRadius, int craterDepth, double blastReach, boolean carveTerrain,
                         boolean leaveHeart, boolean frostRays, boolean evacuateCreative) {
        public static final Tuning DEFAULTS = new Tuning(60 * 20, 420, 240, 40, 28, 84, 32, 3.4, true, true, true, true);
    }

    /**
     * When the common config loads: a file still holding 1.8's Halley sizes untouched (trench 30 wide and 22 deep, crater
     * radius 56 and depth 24, blast reach 2.6) gets 1.9.0's bigger ones, once ({@code defaults_version} 1 -> 2). A new
     * file gets 1 too (the key's default, so an older file that lacks it reads the same), already has the new sizes and is
     * only marked.
     */
    public static void upgrade() {
        try {
            if (DEFAULTS_VERSION.get() >= 2) {
                return;
            }
            if (TRENCH_WIDTH.get() == 30 && TRENCH_DEPTH.get() == 22 && CRATER_RADIUS.get() == 56 && CRATER_DEPTH.get() == 24
                    && Math.abs(BLAST_REACH.get() - 2.6) < 1.0E-6) {
                TRENCH_WIDTH.set(TRENCH_WIDTH.getDefault());
                TRENCH_DEPTH.set(TRENCH_DEPTH.getDefault());
                CRATER_RADIUS.set(CRATER_RADIUS.getDefault());
                CRATER_DEPTH.set(CRATER_DEPTH.getDefault());
                BLAST_REACH.set(BLAST_REACH.getDefault());
                HalleyAddon.LOG.info("Halley's crater and blast moved to 1.9.0's defaults (crater radius {}, depth {}, blast reach {})",
                    CRATER_RADIUS.getDefault(), CRATER_DEPTH.getDefault(), BLAST_REACH.getDefault());
            }
            DEFAULTS_VERSION.set(2);
            SPEC.save();
        } catch (IllegalStateException notLoadedYet) {
            // nothing to bring up to date yet
        }
    }

    public static Tuning tuning() {
        if (!SPEC.isLoaded()) {
            return Tuning.DEFAULTS;
        }
        try {
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
        } catch (IllegalStateException notLoadedYet) {
            return Tuning.DEFAULTS;
        }
    }

    /**
     * Whether players may switch the Stellar Remote's cooldowns off ({@code no_cooldown}); false until the config has
     * loaded. Who may, and whether they have, is {@link NoCooldown}'s.
     */
    public static boolean noCooldown() {
        return flag(NO_COOLDOWN);
    }

    /** Whether survival players may use {@code no_cooldown} too ({@code no_cooldown_survival}). */
    public static boolean noCooldownSurvival() {
        return flag(NO_COOLDOWN_SURVIVAL);
    }

    private static boolean flag(ForgeConfigSpec.BooleanValue value) {
        if (!SPEC.isLoaded()) {
            return false;
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    /**
     * How many chunks of Halley's trench are cut each tick ({@code chunks_per_tick}); the crater cuts twice as many.
     */
    public static int chunksPerTick() {
        if (!SPEC.isLoaded()) {
            return 12;
        }
        try {
            return CHUNKS_PER_TICK.get();
        } catch (IllegalStateException notLoadedYet) {
            return 12;
        }
    }

    /**
     * The server's limits on the remote's sliders ({@code max_power}, {@code max_damage}, {@code max_speed}); 0 for power
     * or damage means no limit.
     */
    public record Limits(float power, float damage, float speed) {
        public static final Limits NONE = new Limits(0.0F, 0.0F, 3.0F);
    }

    public static Limits limits() {
        if (!SPEC.isLoaded()) {
            return Limits.NONE;
        }
        try {
            return new Limits(MAX_POWER.get().floatValue(), MAX_DAMAGE.get().floatValue(), MAX_SPEED.get().floatValue());
        } catch (IllegalStateException notLoadedYet) {
            return Limits.NONE;
        }
    }
}
