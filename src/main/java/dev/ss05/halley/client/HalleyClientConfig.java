package dev.ss05.halley.client;

import java.util.List;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.config.ConfigFile;

/**
 * How SS-05 looks on this computer: {@code config/shooting_star_addition-client.toml}. These only change what you see,
 * never what the strike does, so every player can set them for themselves. Read when the game starts and whenever
 * you join a world.
 */
public final class HalleyClientConfig {
    public static final ConfigFile SPEC = new ConfigFile(HalleyAddon.MOD_ID + "-client.toml");

    /** How to draw while a shader pack is on (see {@code ShaderPacks}). */
    public enum PackMode {
        /** Draw the shader-pack way whenever a shader pack is on. */
        AUTO,
        /** Always draw the shader-pack way, even without one (to compare, or if a pack isn't detected). */
        ALWAYS,
        /** Never: always use SS-05's own shaders (a shader pack then hides the comet and the sky). */
        NEVER
    }

    private static final ConfigFile.BoolValue SKY;
    private static final ConfigFile.BoolValue DARKEN_LAND;
    private static final ConfigFile.BoolValue EXTRA_EFFECTS;
    private static final ConfigFile.BoolValue WORLD_FLASHES;
    private static final ConfigFile.BoolValue COMET_MARKER;
    private static final ConfigFile.EnumValue<PackMode> SHADER_PACKS;
    private static final ConfigFile.BoolValue LUNA_MUSIC;
    private static final ConfigFile.BoolValue NO_COOLDOWN;
    private static final ConfigFile.BoolValue EVAC_LIFT;
    private static final ConfigFile.BoolValue KEEP_MOON;
    private static final ConfigFile.BoolValue EPIC_MODE;
    private static final ConfigFile.EnumValue<ImpactFrames> IMPACT_FRAMES;
    private static final ConfigFile.StringListValue CONVERGENCE;
    private static final ConfigFile.BoolValue CONVERGENCE_RING;
    private static final ConfigFile.BoolValue CONVERGENCE_PLATE;
    private static final ConfigFile.BoolValue CONVERGENCE_PLATE_OLD;
    private static final ConfigFile.EnumValue<ConvergenceShake> CONVERGENCE_SHAKE;
    /** What Stellar Convergence fires out of the box: every weapon but Gravitia (which only the menu chooses). */
    private static final List<String> CONVERGENCE_DEFAULT = List.of("the_shooting_star", "gungnir", "seven_stars", "tesseract", "halley", "luna");

    /** How strong Halley's and Luna's impact frames are (F on the addon's plate cycles them). */
    public enum ImpactFrames {
        FULL(1.0F), SOFT(0.35F), OFF(0.0F);

        public final float strength;

        ImpactFrames(float strength) {
            this.strength = strength;
        }

        public ImpactFrames next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    /** The screen shake while a Stellar Convergence hits (X on its plate cycles it; the user, 2026-11-05). */
    public enum ConvergenceShake {
        CAPPED, FULL, OFF;

        public ConvergenceShake next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    static {
        ConfigFile b = SPEC;
        b.section("looks", "How Halley and Luna look on this computer. These only change what you see.");
        SKY = b.define("sky", true, "The sky goes dark while the comet comes in (stars, an aurora, the comet lighting up the air),",
            "and hazy with ice after the impact (a halo and sun dogs round the sun).");
        DARKEN_LAND = b.define("darken_land", true, "The land dims under that dark sky, so the comet's light stands out.");
        EXTRA_EFFECTS = b.define("extra_effects", true,
            "Fragments bursting in the sky, ice thrown out of the crater, cracks across the ground,",
            "the shock wall, the heart's beam and glittering air after the impact.");
        WORLD_FLASHES = b.define("world_flashes", true, "The whole world lights up for an instant at touchdown and at the impact.",
            "Turn this off if flashing light bothers you.");
        COMET_MARKER = b.define("comet_marker", true, "An arrow at the edge of the screen pointing at the comet while it's out of view.");
        SHADER_PACKS = b.defineEnum("shader_packs", PackMode.AUTO,
            "Shader packs (Iris) only show what is drawn with Minecraft's own shaders, so while one is on",
            "Halley paints its comet and sky onto textures first and draws those the way the pack expects.",
            "AUTO: do that whenever a shader pack is on. ALWAYS: do it all the time. NEVER: don't (with a",
            "shader pack on, the comet and the sky then won't show).");

        b.section("stellar_remote", "Your switches in the Stellar Remote's menu (H). They are saved here when you flip them there.");
        LUNA_MUSIC = b.define("luna_music", true, "Play Luna's music as the moon comes down (M in the remote's menu).");
        NO_COOLDOWN = b.define("no_cooldown", true, "Cast without cooldowns where the server allows it (its no_cooldown setting; C in the remote's",
                "menu). Has no effect on a server that doesn't allow it.");
        EVAC_LIFT = b.define("evac_lift", true, "In creative, standing where your own strike lands: true lifts you straight up above it, false",
                "takes you out to the crater's edge (onto land that is loaded, else up). E in the remote's menu.");
        KEEP_MOON = b.define("keep_moon", false, "Luna's moon model stays where it landed instead of crumbling into the block moon, until you switch",
                "this off or leave the world (for filming). L in the remote's menu.");
        EPIC_MODE = b.define("epic_mode", false, "Luna's Epic mode: the moon you call down falls slower and more ominously (24 s from the alarm to the",
                "impact instead of 16; everyone watching sees it), with the long cut of her music. G in the remote's menu.");
        IMPACT_FRAMES = b.defineEnum("impact_frames", ImpactFrames.FULL, "Halley's and Luna's impact frames (the screen flashing to negative at an impact): FULL, SOFT",
                "(fainter) or OFF. F in the remote's menu.");
        CONVERGENCE = b.defineList("convergence", CONVERGENCE_DEFAULT, "SA-00 Stellar Convergence: the remote's skills it fires, by id (ticked with 1-9 on its plate in the",
                "remote's menu). Each fires with its own sliders.");
        CONVERGENCE_RING = b.define("convergence_ring", false, "Stellar Convergence: true spreads its weapons in a ring round the crosshair, false puts them all on",
                "it. R in the remote's menu.");
        CONVERGENCE_PLATE = b.define("convergence_plate", true, "Stellar Convergence's plate in the remote's menu, shown while it is the selected skill (false: just its",
                "name). V in the remote's menu.");
        CONVERGENCE_PLATE_OLD = b.define("convergence_plate_old", false, "Stellar Convergence's plate in its old look: always shown in the remote's menu, at the addon plate's",
                "size (false: the smaller one, shown while it is the selected skill). T in the remote's menu.");
        CONVERGENCE_SHAKE = b.defineEnum("convergence_shake", ConvergenceShake.CAPPED, "The screen shake while a Stellar Convergence is on its way and for 20 s after it hits: CAPPED (no",
                "stronger than one big weapon's, though every weapon shakes), FULL (every weapon's added up) or OFF. X in the remote's menu.");
    }

    private HalleyClientConfig() {
    }

    public static boolean sky() {
        return SKY.get();
    }

    public static boolean darkenLand() {
        return DARKEN_LAND.get();
    }

    public static boolean extraEffects() {
        return EXTRA_EFFECTS.get();
    }

    public static boolean worldFlashes() {
        return WORLD_FLASHES.get();
    }

    public static boolean cometMarker() {
        return COMET_MARKER.get();
    }

    public static boolean lunaMusic() {
        return LUNA_MUSIC.get();
    }

    public static void setLunaMusic(boolean on) {
        SPEC.set(LUNA_MUSIC, on);
    }

    /** Your own no-cooldown switch (it only counts where the server allows no-cooldown). */
    public static boolean noCooldown() {
        return NO_COOLDOWN.get();
    }

    public static void setNoCooldown(boolean on) {
        set(NO_COOLDOWN, on);
    }

    /** Your evac switch: true lifts you straight up, false takes you to the crater's edge. */
    public static boolean evacLift() {
        return get(EVAC_LIFT);
    }

    public static void setEvacLift(boolean on) {
        set(EVAC_LIFT, on);
    }

    /** Your Epic mode switch: the moons you call down fall slower (see EpicMode). */
    public static boolean epicMode() {
        return get(EPIC_MODE);
    }

    public static void setEpicMode(boolean on) {
        set(EPIC_MODE, on);
    }

    /** Your keep-the-moon switch: Luna's moon model stays after it lands. */
    public static boolean keepMoon() {
        return get(KEEP_MOON);
    }

    public static void setKeepMoon(boolean on) {
        set(KEEP_MOON, on);
    }

    public static ImpactFrames impactFrames() {
        return get(IMPACT_FRAMES);
    }

    public static void setImpactFrames(ImpactFrames frames) {
        set(IMPACT_FRAMES, frames);
    }

    /** Stellar Convergence's ticked weapons, by skill id. */
    public static List<String> convergence() {
        return List.copyOf(get(CONVERGENCE));
    }

    public static void setConvergence(List<String> ids) {
        set(CONVERGENCE, List.copyOf(ids));
    }

    /** Stellar Convergence: in a ring round the crosshair (true) or all on it. */
    public static boolean convergenceRing() {
        return get(CONVERGENCE_RING);
    }

    public static void setConvergenceRing(boolean on) {
        set(CONVERGENCE_RING, on);
    }

    /** Stellar Convergence's plate shown in full (true) or folded to its name. */
    public static boolean convergencePlate() {
        return get(CONVERGENCE_PLATE);
    }

    public static void setConvergencePlate(boolean on) {
        set(CONVERGENCE_PLATE, on);
    }

    /** Stellar Convergence's plate in its old look (always shown, bigger) instead of the new one. */
    public static boolean convergencePlateOld() {
        return get(CONVERGENCE_PLATE_OLD);
    }

    public static void setConvergencePlateOld(boolean on) {
        set(CONVERGENCE_PLATE_OLD, on);
    }

    public static ConvergenceShake convergenceShake() {
        return get(CONVERGENCE_SHAKE);
    }

    public static void setConvergenceShake(ConvergenceShake shake) {
        set(CONVERGENCE_SHAKE, shake);
    }

    private static <T> void set(ConfigFile.Value<T> value, T on) {
        SPEC.set(value, on);
    }

    public static PackMode shaderPacks() {
        return SHADER_PACKS.get();
    }

    private static <T> T get(ConfigFile.Value<T> value) {
        return value.get();
    }
}
