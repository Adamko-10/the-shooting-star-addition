package dev.ss05.halley.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.render.CometRenderer;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import dev.ss05.halley.compat.client.StarClientBridge;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client side of SS-05: its shader, its renderer, and its hook into the remote's spell effects. */
public final class HalleyClient {
    private HalleyClient() {
    }

    /** During mod construction. */
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterShadersEvent.class, HalleyClient::registerShaders);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, CometRenderer::render);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> HalleyFx.clearAll());
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
    }
}
