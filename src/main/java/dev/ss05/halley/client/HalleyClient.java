package dev.ss05.halley.client;

import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.render.MoonRenderer;
import dev.ss05.halley.client.luna.render.MoonSphere;
import dev.ss05.halley.client.render.CometRenderer;
import dev.ss05.halley.client.render.GlowAtlas;
import dev.ss05.halley.client.render.ShaderPacks;
import dev.ss05.halley.client.sky.HalleySky;
import dev.ss05.halley.compat.client.StarClientBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * The client side of SS-05 and SS-06: their renderers (the comet, the moon, the sky), their config, and the hook
 * into the remote's effects. The hooks into Minecraft's own drawing are in {@code client/mixin}.
 */
public final class HalleyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HalleyClientConfig.SPEC.load();
        // the moon sphere first, solid and depth-tested, so the glow drawn after it tests against its depth
        LevelRenderEvents.END_MAIN.register(MoonSphere::renderSolid);
        LevelRenderEvents.END_MAIN.register(CometRenderer::render);
        LevelRenderEvents.END_MAIN.register(MoonRenderer::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            HalleySky.submitForShaderPack(context);
            CometRenderer.submitForShaderPack(context);
            MoonSphere.submitForShaderPack(context);
            MoonRenderer.submitForShaderPack(context);
        });
        // once every mod's client has started, The Shooting Star's effect handling is in place to be hooked
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> StarClientBridge.install());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> HalleyClientConfig.SPEC.load());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HalleyFx.clearAll();
            MoonFx.clearAll();
            CometRenderer.release();
            MoonRenderer.release();
            MoonSphere.release();
            HalleySky.release();
        });
    }

    /**
     * From client/mixin, once a frame before Minecraft gathers up what it is about to draw: how this frame is drawn
     * (with or without a shader pack), and the sky. With a shader pack on, SS-05's and SS-06's light and sky are
     * painted onto textures now, while their own shaders can still run.
     */
    public static void frameStart(DeltaTracker deltaTracker) {
        ShaderPacks.update();
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        HalleySky.update(partial);
        if (ShaderPacks.active() && Minecraft.getInstance().level != null
            && (!HalleyFx.active().isEmpty() || !MoonFx.active().isEmpty())) {
            GlowAtlas.paint();
            HalleySky.paint(partial);
        }
    }
}
