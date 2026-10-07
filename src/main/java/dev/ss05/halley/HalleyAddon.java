package dev.ss05.halley;

import com.mojang.logging.LogUtils;
import dev.ss05.halley.client.HalleyClient;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;

/**
 * The Shooting Star Addition — an unofficial addon for The Shooting Star. Adds SS-05 · Halley to the Stellar Remote.
 *
 * <p>Where things live:
 * <ul>
 *   <li>{@code compat/}  — the ONLY code that touches The Shooting Star. When that mod updates, start there.</li>
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

        // Fabric runs every mod's main entrypoint before any client one, so SS-05 is on the Stellar Remote before
        // The Shooting Star's client sets up the remote's key bindings: it gets its own key like the other skills.
        StarBridge.install();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            HalleyConfig.SPEC.load();
            StarBridge.install();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> warnIfDetached(handler.player));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** If The Shooting Star changed under us, say so in chat instead of failing silently. */
    private static void warnIfDetached(ServerPlayer player) {
        if (!StarBridge.installed() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.literal("[The Shooting Star Addition] SS-05 Halley couldn't attach to The Shooting Star ("
                    + StarBridge.problem() + "). The skill is disabled until the addon is updated for this version - see the log.")
                .withStyle(ChatFormatting.RED));
        }
    }
}
