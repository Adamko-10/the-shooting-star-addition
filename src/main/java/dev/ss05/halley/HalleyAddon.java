package dev.ss05.halley;

import com.mojang.logging.LogUtils;
import dev.ss05.halley.client.HalleyClient;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import dev.ss05.halley.net.AddonNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * The Shooting Star Addition — an unofficial addon for The Shooting Star. Adds SA-01 · Halley and SA-02 · Luna to the
 * Stellar Remote.
 *
 * <p>Where things live:
 * <ul>
 *   <li>{@code compat/tss/}  — the ONLY code that touches The Shooting Star. When that mod updates, start there.</li>
 *   <li>{@link HalleyConfig} — every number you might want to tune (also written to config/shooting_star_addition-common.toml).</li>
 *   <li>{@link HalleyClientConfig} — how it looks on each player's screen (config/shooting_star_addition-client.toml).</li>
 *   <li>{@link HalleyPlan} — the timeline and the shape of the strike, shared by the server and the client film.</li>
 *   <li>{@code world/} — what the strike does to the world (server side).</li>
 *   <li>{@code client/} — what it looks and sounds like (client side, {@link HalleyClient}).</li>
 * </ul>
 */
public final class HalleyAddon implements ModInitializer {
    public static final String MOD_ID = "shooting_star_addition";
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        HalleyConfig.SPEC.load();
        HalleyContent.register();
        AddonNetwork.register();

        // Fabric runs every mod's main entrypoint before any client one, so the skills are on the Stellar Remote before
        // The Shooting Star's client sets up the remote's key bindings: each gets its own key like the other skills.
        StarBridge.install();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            HalleyConfig.SPEC.load();
            StarBridge.install();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            warnIfDetached(handler.player);
            AddonNetwork.sendSettings(handler.player);
            NoCooldown.greet(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NoCooldown.forget(handler.player));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** If The Shooting Star changed under us, say so in chat instead of failing silently. */
    private static void warnIfDetached(ServerPlayer player) {
        if (!StarBridge.installed() && player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("[The Shooting Star Addition] Halley and Luna couldn't attach to The Shooting Star ("
                    + StarBridge.problem() + "). The skill is disabled until the addon is updated for this version - see the log.")
                .withStyle(ChatFormatting.RED));
        }
    }
}
