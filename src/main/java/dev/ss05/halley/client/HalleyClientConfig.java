package dev.ss05.halley.client;

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

    static {
        ConfigFile b = SPEC;
        b.section("looks", "How SS-05 Halley looks on this computer. These only change what you see.");
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
            "SS-05 paints its comet and sky onto textures first and draws those the way the pack expects.",
            "AUTO: do that whenever a shader pack is on. ALWAYS: do it all the time. NEVER: don't (with a",
            "shader pack on, the comet and the sky then won't show).");
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

    public static PackMode shaderPacks() {
        return SHADER_PACKS.get();
    }
}
