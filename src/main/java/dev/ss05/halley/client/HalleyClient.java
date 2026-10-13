package dev.ss05.halley.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.render.MoonRenderer;
import dev.ss05.halley.client.luna.render.MoonSphere;
import dev.ss05.halley.client.render.CometRenderer;
import dev.ss05.halley.client.render.GlowAtlas;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import dev.ss05.halley.client.render.ShaderPacks;
import dev.ss05.halley.client.render.WorldFrame;
import dev.ss05.halley.client.sky.HalleySky;
import dev.ss05.halley.compat.StarBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * The client side of SA-01 and SA-02: their shaders, their renderers (the comet, the moon, the sky), their config, and
 * the hook into the remote's effects. The hooks into Minecraft's own drawing are in {@code compat/mixin}.
 */
public final class HalleyClient implements ClientModInitializer {
    /** Whether the glow has been drawn after the particles this frame (see {@link #afterParticles}). */
    private static boolean glowDrawn;

    @Override
    public void onInitializeClient() {
        HalleyClientConfig.SPEC.load();
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(HalleyAddon.id("halley_glow"), DefaultVertexFormat.POSITION_TEX_COLOR, HalleyRenderTypes::setShader);
            } catch (Exception failed) {
                // the skill still works, it just isn't drawn
                HalleyAddon.LOG.error("Halley's glow shader didn't load, so the comet won't be drawn", failed);
                HalleyRenderTypes.setShader(null);
            }
            try {
                context.register(HalleyAddon.id("halley_sky"), DefaultVertexFormat.POSITION, HalleySky::setShader);
            } catch (Exception failed) {
                HalleyAddon.LOG.error("Halley's sky shader didn't load, so the sky won't change during a strike", failed);
                HalleySky.setShader(null);
            }
        });
        // the moon sphere among the entities, solid and depth-tested, so the glow drawn later tests against its depth
        WorldRenderEvents.AFTER_ENTITIES.register(context -> MoonSphere.renderSolid(frame(context)));
        // the glow goes right after the world's particles (compat/mixin); should that hook be missing (another mod
        // reworking the level renderer), at the very end of the world instead
        WorldRenderEvents.LAST.register(context -> {
            if (!glowDrawn) {
                drawGlow(frame(context));
            }
            glowDrawn = false;
        });
        // once every mod's client has started, The Shooting Star's effect handling is in place to be hooked
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> StarBridge.installClient());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> HalleyClientConfig.SPEC.load());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HalleyFx.clearAll();
            MoonFx.clearAll();
        });
    }

    private static WorldFrame frame(WorldRenderContext context) {
        return new WorldFrame(context.camera(), context.projectionMatrix(), context.positionMatrix(),
            context.tickCounter().getGameTimeDeltaPartialTick(true));
    }

    /**
     * From compat/mixin, once a frame before anything is drawn: how this frame is drawn (with or without a shader
     * pack), the sky, and with a shader pack on, the comet's and the moon's light painted onto textures while their own
     * shaders can still run.
     */
    public static void frameStart(DeltaTracker deltaTracker) {
        ShaderPacks.update();
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        HalleySky.update(partial);
        if (ShaderPacks.active() && Minecraft.getInstance().level != null
            && (!HalleyFx.active().isEmpty() || !MoonFx.active().isEmpty())) {
            GlowAtlas.paint(HalleyRenderTypes.shader());
            HalleySky.paint(partial);
        }
    }

    /** From compat/mixin, right after the vanilla sky: the strike's sky dome. */
    public static void afterSky(WorldFrame frame) {
        HalleySky.render(frame);
    }

    /** From compat/mixin, right after the world's particles (so the glow sorts with them, also under Fabulous). */
    public static void afterParticles(WorldFrame frame) {
        glowDrawn = true;
        drawGlow(frame);
    }

    private static void drawGlow(WorldFrame frame) {
        CometRenderer.render(frame);
        MoonRenderer.render(frame);
    }
}
