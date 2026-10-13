package dev.ss05.halley.clienttest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Dev-only mod: a scripted, automated in-game screenshot harness for Halley and Luna, so an effect can be looked at
 * without a human sitting at the keyboard.
 *
 * <p>Does nothing at all unless the JVM system property {@code ss05.clienttest} is set (the {@code clientTest}
 * Gradle run sets it). See {@link Harness} for the actual state machine; {@link HarnessParams} for the
 * system-property parameters it reads. Its frame hooks are a mixin ({@code mixin/GameRendererFrameMixin}).
 */
public final class Ss05ClientTestMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!HarnessParams.ACTIVE) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> Harness.onClientTick());
        ServerTickEvents.END_SERVER_TICK.register(Harness::onServerTick);
        Harness.log("harness armed: " + HarnessParams.describe());
    }
}
