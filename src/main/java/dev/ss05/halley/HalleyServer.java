package dev.ss05.halley;

import dev.ss05.halley.compat.StarBridge;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * On a dedicated server, Halley and Luna join the Stellar Remote here: Fabric runs this after every mod's main
 * entrypoint, so The Shooting Star has registered its skills by then (on a client, compat/mixin/RimulibClientMixin).
 */
public final class HalleyServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        StarBridge.install();
    }
}
