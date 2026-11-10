package dev.ss05.halley.client;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * How SS-05 looks on this computer: {@code config/shooting_star_addition-client.toml}. These only change what you see,
 * never what the strike does, so every player can set them for themselves.
 */
public final class HalleyClientConfig {
    public static final ModConfigSpec SPEC;

    /** How to draw while a shader pack is on (see {@code ShaderPacks}). */
    public enum PackMode {
        /** Draw the shader-pack way whenever a shader pack is on. */
        AUTO,
        /** Always draw the shader-pack way, even without one (to compare, or if a pack isn't detected). */
        ALWAYS,
        /** Never: always use SS-05's own shaders (a shader pack then hides the comet and the sky). */
        NEVER
    }

    private static final ModConfigSpec.BooleanValue SKY;
    private static final ModConfigSpec.BooleanValue DARKEN_LAND;
    private static final ModConfigSpec.BooleanValue EXTRA_EFFECTS;
    private static final ModConfigSpec.BooleanValue WORLD_FLASHES;
    private static final ModConfigSpec.BooleanValue COMET_MARKER;
    private static final ModConfigSpec.EnumValue<PackMode> SHADER_PACKS;
    private static final ModConfigSpec.BooleanValue LUNA_MUSIC;
    private static final ModConfigSpec.BooleanValue NO_COOLDOWN;
    private static final ModConfigSpec.BooleanValue EVAC_LIFT;
    private static final ModConfigSpec.BooleanValue KEEP_MOON;
    private static final ModConfigSpec.BooleanValue EPIC_MODE;
    private static final ModConfigSpec.BooleanValue IMPERIAL;
    private static final ModConfigSpec.BooleanValue STACK_MUSIC;
    private static final ModConfigSpec.EnumValue<ImpactFrames> IMPACT_FRAMES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CONVERGENCE;
    private static final ModConfigSpec.BooleanValue CONVERGENCE_RING;
    private static final ModConfigSpec.BooleanValue CONVERGENCE_PLATE;
    private static final ModConfigSpec.BooleanValue CONVERGENCE_PLATE_OLD;
    private static final ModConfigSpec.BooleanValue MENU_PLATES;
    private static final ModConfigSpec.EnumValue<ConvergenceShake> CONVERGENCE_SHAKE;
    /** What Stellar Convergence fires out of the box: every weapon but Gravitia (which only the menu chooses). */
    private static final List<String> CONVERGENCE_DEFAULT = List.of("the_shooting_star", "gungnir", "seven_stars", "tesseract", "halley", "luna", "proxima");

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
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("How Halley and Luna look on this computer. These only change what you see.").push("looks");
        SKY = b.comment("The sky goes dark while the comet comes in (stars, an aurora, the comet lighting up the air),",
                "and hazy with ice after the impact (a halo and sun dogs round the sun).")
            .define("sky", true);
        DARKEN_LAND = b.comment("The land dims under that dark sky, so the comet's light stands out.")
            .define("darken_land", true);
        EXTRA_EFFECTS = b.comment("Fragments bursting in the sky, ice thrown out of the crater, cracks across the ground,",
                "the shock wall, the heart's beam and glittering air after the impact.")
            .define("extra_effects", true);
        WORLD_FLASHES = b.comment("The whole world lights up for an instant at touchdown and at the impact.",
                "Turn this off if flashing light bothers you.")
            .define("world_flashes", true);
        COMET_MARKER = b.comment("An arrow at the edge of the screen pointing at the comet while it's out of view.")
            .define("comet_marker", true);
        SHADER_PACKS = b.comment("Shader packs (Iris) only show what is drawn with Minecraft's own shaders, so while one is on",
                "Halley paints its comet and sky onto textures first and draws those the way the pack expects.",
                "AUTO: do that whenever a shader pack is on. ALWAYS: do it all the time. NEVER: don't (with a",
                "shader pack on, the comet and the sky then won't show).")
            .defineEnum("shader_packs", PackMode.AUTO);
        b.pop();

        b.comment("Your switches in the Stellar Remote's menu (H). They are saved here when you flip them there.").push("stellar_remote");
        LUNA_MUSIC = b.comment("Play the skills' music: Luna's as the moon comes down, Proxima's as the flare comes in (M in the remote's menu).")
            .define("luna_music", true);
        NO_COOLDOWN = b.comment("Cast without cooldowns where the server allows it (its no_cooldown setting; C in the remote's",
                "menu). Has no effect on a server that doesn't allow it.")
            .define("no_cooldown", true);
        EVAC_LIFT = b.comment("In creative, standing where your own strike lands: true lifts you straight up above it, false",
                "takes you out to the crater's edge (onto land that is loaded, else up). E in the remote's menu.")
            .define("evac_lift", true);
        KEEP_MOON = b.comment("Luna's moon model stays where it landed instead of crumbling into the block moon, until you switch",
                "this off or leave the world (for filming). L in the remote's menu.")
            .define("keep_moon", false);
        EPIC_MODE = b.comment("Luna's Epic mode: the moon you call down falls slower and more ominously (24 s from the alarm to the",
                "impact instead of 16; everyone watching sees it), with the long cut of her music. G in the remote's menu.")
            .define("epic_mode", false);
        IMPERIAL = b.comment("Distances in the films' readouts (altitude, the flare's front, the shock wave): false for kilometres",
                "and metres, true for miles and feet. The - key in the remote's menu.")
            .define("imperial_units", false);
        STACK_MUSIC = b.comment("Strikes cast one after another each keep their own music playing (true), or the newest one's music",
                "takes over (false). The = key in the remote's menu.")
            .define("stack_music", false);
        IMPACT_FRAMES = b.comment("Halley's and Luna's impact frames (the screen flashing to negative at an impact): FULL, SOFT",
                "(fainter) or OFF. F in the remote's menu.")
            .defineEnum("impact_frames", ImpactFrames.FULL);
        CONVERGENCE = b.comment("SA-00 Stellar Convergence: the remote's skills it fires, by id (ticked with 1-9 on its plate in the",
                "remote's menu). Each fires with its own sliders.")
            .defineListAllowEmpty("convergence", CONVERGENCE_DEFAULT, () -> "", entry -> entry instanceof String);
        CONVERGENCE_RING = b.comment("Stellar Convergence: true spreads its weapons in a ring round the crosshair, false puts them all on",
                "it. R in the remote's menu.")
            .define("convergence_ring", false);
        CONVERGENCE_PLATE = b.comment("Stellar Convergence's plate in the remote's menu, shown while it is the selected skill (false: just its",
                "name). V in the remote's menu.")
            .define("convergence_plate", true);
        CONVERGENCE_PLATE_OLD = b.comment("Stellar Convergence's plate in its old look: always shown in the remote's menu, at the addon plate's",
                "size (false: the smaller one, shown while it is the selected skill). T in the remote's menu.")
            .define("convergence_plate_old", false);
        CONVERGENCE_SHAKE = b.comment("The screen shake while a Stellar Convergence is on its way and for 20 s after it hits: CAPPED (no",
                "stronger than one big weapon's, though every weapon shakes), FULL (every weapon's added up) or OFF. X in the remote's menu.")
            .defineEnum("convergence_shake", ConvergenceShake.CAPPED);
        MENU_PLATES = b.comment("The addon's plates in the remote's menu (its settings, and Stellar Convergence's): false hides them and",
                "their keys. The settings above still apply; change them in this file.")
            .define("menu_plates", true);
        b.pop();
        SPEC = b.build();
    }

    private HalleyClientConfig() {
    }

    public static boolean sky() {
        return get(SKY);
    }

    public static boolean darkenLand() {
        return get(DARKEN_LAND);
    }

    public static boolean extraEffects() {
        return get(EXTRA_EFFECTS);
    }

    public static boolean worldFlashes() {
        return get(WORLD_FLASHES);
    }

    public static boolean cometMarker() {
        return get(COMET_MARKER);
    }

    public static boolean lunaMusic() {
        return get(LUNA_MUSIC);
    }

    public static void setLunaMusic(boolean on) {
        set(LUNA_MUSIC, on);
    }

    /** Your own no-cooldown switch (it only counts where the server allows no-cooldown). */
    public static boolean noCooldown() {
        return get(NO_COOLDOWN);
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

    /** Your units for the films' readouts: miles and feet (true) or kilometres and metres. */
    public static boolean imperial() {
        return get(IMPERIAL);
    }

    public static void setImperial(boolean on) {
        set(IMPERIAL, on);
    }

    /** Whether strikes' music stacks (each keeps playing) or the newest takes over. */
    public static boolean stackMusic() {
        return get(STACK_MUSIC);
    }

    public static void setStackMusic(boolean on) {
        set(STACK_MUSIC, on);
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
    /** The addon's plates (and their keys) in the remote's menu; false: hidden (menu_plates). */
    public static boolean menuPlates() {
        return get(MENU_PLATES);
    }

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

    /** Sets and saves a switch (kept for this session if the config isn't loaded). */
    private static <T> void set(ModConfigSpec.ConfigValue<T> value, T on) {
        if (!SPEC.isLoaded()) {
            return;
        }
        try {
            value.set(on);
            value.save();
        } catch (IllegalStateException notLoadedYet) {
            // ignore: it stays at its default
        }
    }

    public static PackMode shaderPacks() {
        if (!SPEC.isLoaded()) {
            return PackMode.AUTO;
        }
        try {
            return SHADER_PACKS.get();
        } catch (IllegalStateException notLoadedYet) {
            return PackMode.AUTO;
        }
    }

    /** The setting's default until the config has loaded (keep_moon's is off). */
    private static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        if (!SPEC.isLoaded()) {
            return value.getDefault();
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }
}
