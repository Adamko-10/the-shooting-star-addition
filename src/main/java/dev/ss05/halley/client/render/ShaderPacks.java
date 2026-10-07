package dev.ss05.halley.client.render;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyClientConfig;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Whether a shader pack is drawing the world. While one is on, Iris only lets Minecraft's own shaders draw into it
 * (it masks out every draw made with a mod's own shader, unless the player has turned on its "allow unknown shaders"
 * option), so SS-05's comet and sky would vanish. In that mode SS-05 paints its light onto textures first (outside the
 * world pass, where its shaders still run) and draws those with Minecraft's glowing-eyes shaders, which every shader
 * pack draws: see {@link GlowAtlas} and {@code HalleySky}.
 *
 * <p>Iris is optional: it's looked up by name, through its public API.
 */
public final class ShaderPacks {
    private static boolean looked;
    @Nullable
    private static Object api;
    @Nullable
    private static Method inUse;
    /** Decided once a frame, so everything drawn in a frame is drawn the same way. */
    private static boolean thisFrame;

    private ShaderPacks() {
    }

    /** Once a frame, before anything is drawn. */
    public static void update() {
        thisFrame = switch (HalleyClientConfig.shaderPacks()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> irisPackInUse();
        };
    }

    /** True if this frame is drawn the shader-pack way. */
    public static boolean active() {
        return thisFrame;
    }

    private static boolean irisPackInUse() {
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Method get = type.getMethod("getInstance");
                inUse = type.getMethod("isShaderPackInUse");
                api = get.invoke(null);
                HalleyAddon.LOG.info("SS-05 Halley: Iris found, the comet and the sky will switch to textures while a shader pack is on");
            } catch (ClassNotFoundException absent) {
                api = null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failed) {
                HalleyAddon.LOG.warn("SS-05 Halley: couldn't ask Iris whether a shader pack is on ({}), assuming not", failed.toString());
                api = null;
            }
        }
        if (api == null || inUse == null) {
            return false;
        }
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            HalleyAddon.LOG.warn("SS-05 Halley: asking Iris about shader packs failed ({}), not asking again", failed.toString());
            api = null;
            return false;
        }
    }
}
