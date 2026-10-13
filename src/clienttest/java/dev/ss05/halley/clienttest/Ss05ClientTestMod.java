package dev.ss05.halley.clienttest;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only mod: a scripted, automated in-game screenshot harness for Halley and Luna, so an effect can be looked at
 * without a human sitting at the keyboard.
 *
 * <p>Does nothing at all unless the JVM system property {@code ss05.clienttest} is set (the {@code clientTest}
 * Gradle run sets it). See {@link Harness} for the actual state machine; {@link HarnessParams} for the
 * system-property parameters it reads.
 */
@Mod(Ss05ClientTestMod.MOD_ID)
public final class Ss05ClientTestMod {
    public static final String MOD_ID = "ss05_clienttest";

    public Ss05ClientTestMod() {
        if (!HarnessParams.ACTIVE) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(Harness::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(Harness::onRenderTick);
        MinecraftForge.EVENT_BUS.addListener(Harness::onServerTick);
        Harness.log("harness armed: " + HarnessParams.describe());
    }
}
