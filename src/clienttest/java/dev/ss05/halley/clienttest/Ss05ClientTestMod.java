package dev.ss05.halley.clienttest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only mod: a scripted, automated in-game screenshot harness for SS-05/SS-06, so the main session can look at
 * an effect without a human sitting at the keyboard.
 *
 * <p>Does nothing at all unless the JVM system property {@code ss05.clienttest} is set (the {@code clientTest}
 * Gradle run sets it). See {@link Harness} for the actual state machine; {@link HarnessParams} for the
 * system-property parameters it reads.
 */
@Mod(Ss05ClientTestMod.MOD_ID)
public final class Ss05ClientTestMod {
    public static final String MOD_ID = "ss05_clienttest";

    public Ss05ClientTestMod(IEventBus modBus, ModContainer container, Dist dist) {
        if (!HarnessParams.ACTIVE) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, Harness::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, Harness::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Harness::onServerTick);
        Harness.log("harness armed: " + HarnessParams.describe());
    }
}
