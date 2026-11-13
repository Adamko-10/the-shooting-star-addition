package dev.ss05.halley;

import com.mojang.logging.LogUtils;
import dev.ss05.halley.client.HalleyClient;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import dev.ss05.halley.net.AddonNetwork;
import dev.ss05.halley.world.MoonStrike;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;

/**
 * The Shooting Star Addition: SA Series — an unofficial addon for The Shooting Star. Adds SA-01 · Halley, SA-02 · Luna,
 * SA-04 · Proxima and SA-00 · Stellar Convergence to the Stellar Remote (the code's "SS-05"/"SS-06"/"moon" names are
 * the old codenames).
 *
 * <p>Where things live:
 * <ul>
 *   <li>{@code compat/tss/}  — the ONLY code that touches The Shooting Star (and RimuLib's internals). When either
 *       updates, start there.</li>
 *   <li>{@link HalleyConfig}, {@link MoonConfig}, {@link ProximaConfig} — every number you might want to tune (written
 *       to config/shooting_star_addition-common.toml).</li>
 *   <li>{@link HalleyClientConfig} — how it looks on each player's screen and their switches in the remote's menu
 *       (config/shooting_star_addition-client.toml).</li>
 *   <li>{@link HalleyPlan}, {@link MoonPlan}, {@link ProximaPlan} — each skill's timeline and shape, shared by the server
 *       and the client film.</li>
 *   <li>{@code world/} — what the strikes do to the world (server side).</li>
 *   <li>{@code client/} — what they look and sound like (client side).</li>
 * </ul>
 *
 * <p>The server tick below runs the spells ({@code StarBridge.tick}), then the work each strike leaves behind (Luna's
 * leftover land, Proxima's land and radiation zones), then the random flares. Each of those catches its own failures
 * (one bad item is logged and dropped), so a broken strike can never stop the server.
 */
public final class HalleyAddon implements ModInitializer {
    public static final String MOD_ID = "shooting_star_addition";
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        HalleyConfig.SPEC.load();
        HalleyContent.register();
        AddonNetwork.register();

        // Halley and Luna join the Stellar Remote once The Shooting Star has registered its skills: Fabric runs main
        // entrypoints in no fixed order, so that is just before RimuLib's client starts (client/mixin/RimulibClientMixin)
        // or, on a dedicated server, in HalleyServer.

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            HalleyConfig.SPEC.load();
            StarBridge.install();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StarBridge.tick(server);
            MoonStrike.tickLingering();
            dev.ss05.halley.world.ProximaStrike.tickLingering(server);
            try {
                dev.ss05.halley.world.SolarFlares.tick(server);
            } catch (RuntimeException | LinkageError failed) {
                // random flares are a side feature: never let one stop the server
                LOG.error("A random solar flare failed", failed);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StarBridge.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            StarBridge.clear();
            MoonStrike.clearLingering();
            dev.ss05.halley.world.ProximaStrike.clearLingering();
            dev.ss05.halley.world.SolarFlares.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            warnIfDetached(handler.player);
            AddonNetwork.sendSettings(handler.player);
            NoCooldown.greet(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            NoCooldown.forget(handler.player);
            Evac.forget(handler.player);
            EpicMode.forget(handler.player);
            StellarQuartet.forget(handler.player);
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** If The Shooting Star changed under us, say so in chat instead of failing silently. */
    private static void warnIfDetached(ServerPlayer player) {
        if (!StarBridge.installed() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.literal("[SA Series] Halley and Luna couldn't attach to The Shooting Star ("
                    + StarBridge.problem() + "). The skill is disabled until the addon is updated for this version - see the log.")
                .withStyle(ChatFormatting.RED));
        }
    }
}
