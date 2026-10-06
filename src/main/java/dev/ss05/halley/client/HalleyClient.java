package dev.ss05.halley.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.render.CometRenderer;
import dev.ss05.halley.client.render.GlowAtlas;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import dev.ss05.halley.client.render.ShaderPacks;
import dev.ss05.halley.client.sky.HalleySky;
import dev.ss05.halley.compat.client.StarClientBridge;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client side of SS-05: its shaders, its renderers (the comet and the sky), and its hook into the remote's effects. */
public final class HalleyClient {
    private HalleyClient() {
    }

    /** During mod construction. */
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterShadersEvent.class, HalleyClient::registerShaders);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, CometRenderer::render);
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, HalleyClient::frameStart);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, HalleySky::render);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFogColor.class, HalleySky::fog);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> HalleyFx.clearAll());
    }

    /**
     * Once a frame, before anything is drawn: how this frame is drawn (with or without a shader pack), the sky, and
     * with a shader pack on, SS-05's light painted onto textures while its own shaders can still run.
     */
    private static void frameStart(RenderFrameEvent.Pre event) {
        ShaderPacks.update();
        HalleySky.update(event);
        if (ShaderPacks.active() && Minecraft.getInstance().level != null && !HalleyFx.active().isEmpty()) {
            GlowAtlas.paint(HalleyRenderTypes.shader());
            HalleySky.paint(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }

    /** Once every mod is constructed (The Shooting Star has set up its client by then). */
    public static void afterConstruction() {
        StarClientBridge.install();
    }

    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), HalleyAddon.id("halley_glow"),
                DefaultVertexFormat.POSITION_TEX_COLOR), HalleyRenderTypes::setShader);
        } catch (IOException failed) {
            // the skill still works, it just isn't drawn
            HalleyAddon.LOG.error("SS-05 Halley's glow shader didn't load, so the comet won't be drawn", failed);
            HalleyRenderTypes.setShader(null);
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), HalleyAddon.id("halley_sky"),
                DefaultVertexFormat.POSITION), HalleySky::setShader);
        } catch (IOException failed) {
            HalleyAddon.LOG.error("SS-05 Halley's sky shader didn't load, so the sky won't change during a strike", failed);
            HalleySky.setShader(null);
        }
    }
}
