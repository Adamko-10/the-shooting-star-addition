package dev.ss05.halley.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * How SS-05 looks on this computer: {@code config/shooting_star_addition-client.toml}. These only change what you see,
 * never what the strike does, so every player can set them for themselves.
 */
public final class HalleyClientConfig {
    public static final ForgeConfigSpec SPEC;

    /** How to draw while a shader pack is on (see {@code ShaderPacks}). */
    public enum PackMode {
        /** Draw the shader-pack way whenever a shader pack is on. */
        AUTO,
        /** Always draw the shader-pack way, even without one (to compare, or if a pack isn't detected). */
        ALWAYS,
        /** Never: always use SS-05's own shaders (a shader pack then hides the comet and the sky). */
        NEVER
    }

    private static final ForgeConfigSpec.BooleanValue SKY;
    private static final ForgeConfigSpec.BooleanValue DARKEN_LAND;
    private static final ForgeConfigSpec.BooleanValue EXTRA_EFFECTS;
    private static final ForgeConfigSpec.BooleanValue WORLD_FLASHES;
    private static final ForgeConfigSpec.BooleanValue COMET_MARKER;
    private static final ForgeConfigSpec.EnumValue<PackMode> SHADER_PACKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("How SS-05 Halley looks on this computer. These only change what you see.").push("looks");
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
                "SS-05 paints its comet and sky onto textures first and draws those the way the pack expects.",
                "AUTO: do that whenever a shader pack is on. ALWAYS: do it all the time. NEVER: don't (with a",
                "shader pack on, the comet and the sky then won't show).")
            .defineEnum("shader_packs", PackMode.AUTO);
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

    /** The default (true) until the config has loaded. */
    private static boolean get(ForgeConfigSpec.BooleanValue value) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
