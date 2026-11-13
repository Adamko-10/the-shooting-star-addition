package dev.ss05.halley.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.render.MoonRenderer;
import dev.ss05.halley.client.luna.render.MoonSphere;
import dev.ss05.halley.client.proxima.ProximaCosmos;
import dev.ss05.halley.client.proxima.ProximaFx;
import dev.ss05.halley.client.render.CometRenderer;
import dev.ss05.halley.client.render.GlowAtlas;
import dev.ss05.halley.client.render.HalleyRenderTypes;
import dev.ss05.halley.client.render.ShaderPacks;
import dev.ss05.halley.client.sky.HalleySky;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.net.ServerSettings;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/** The client side of SS-05: its shaders, its renderers (the comet and the sky), and its hook into the remote's effects. */
public final class HalleyClient {
    private HalleyClient() {
    }

    /** During mod construction. */
    public static void init(IEventBus modBus) {
        modBus.addListener(HalleyClient::registerShaders);
        MinecraftForge.EVENT_BUS.addListener(CometRenderer::render);
        MinecraftForge.EVENT_BUS.addListener(MoonRenderer::render);
        MinecraftForge.EVENT_BUS.addListener(MoonSphere::renderSolid);
        MinecraftForge.EVENT_BUS.addListener(HalleyClient::frameStart);
        // Proxima's hot bowls shimmer and hum after their strikes
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> ProximaFx.tickZones());
        // a film's panels over the finished picture: last, after The Shooting Star's own end-of-frame drawing
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.RenderTickEvent.Post.class, event -> FilmPanels.endOfFrame());
        MinecraftForge.EVENT_BUS.addListener(HalleySky::render);
        MinecraftForge.EVENT_BUS.addListener(ProximaCosmos::renderSky);
        // with a shader pack on, Proxima's sky gate is drawn here, before the GUI (Fable's review, B4)
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGuiEvent.Pre event) -> ProximaCosmos.renderSkyLate());
        MinecraftForge.EVENT_BUS.addListener(HalleySky::fog);
        // Proxima's Earth images (two 4096 maps, mipmapped) loaded as the world is joined, not mid-film (that stall made the
        // film's music and soundtrack stutter)
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ProximaCosmos.preload());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            HalleyFx.clearAll();
            MoonFx.clearAll();
            dev.ss05.halley.client.proxima.ProximaFx.clearAll();
            ConvergenceView.clearAll();
            ServerSettings.forget();
        });
        // once the server has said what it allows, tell it this player's own no-cooldown switch
        ServerSettings.onReceived = AdditionPanel::sendChoice;
    }

    /**
     * Once a frame, before anything is drawn: how this frame is drawn (with or without a shader pack), the sky, and
     * with a shader pack on, SS-05's and SS-06's light painted onto textures while their own shaders can still run.
     */
    private static void frameStart(TickEvent.RenderTickEvent.Pre event) {
        ShaderPacks.update();
        HalleySky.update(event);
        if (ShaderPacks.active() && Minecraft.getInstance().level != null
            && (!HalleyFx.active().isEmpty() || !MoonFx.active().isEmpty() || !ProximaFx.active().isEmpty()
                || ProximaFx.auroras() || ConvergenceView.any())) {
            GlowAtlas.paint(HalleyRenderTypes.shader());
            HalleySky.paint(event.getTimer().getGameTimeDeltaPartialTick(true));
        }
    }

    /** Once every mod is constructed (The Shooting Star has set up its client by then). */
    public static void afterConstruction() {
        StarBridge.installClient();
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
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), HalleyAddon.id("proxima_space"),
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), ProximaCosmos::setShader);
        } catch (IOException failed) {
            HalleyAddon.LOG.error("SA-04 Proxima's space shader didn't load, so its film's space shots will be empty", failed);
            ProximaCosmos.setShader(null);
        }
    }
}
