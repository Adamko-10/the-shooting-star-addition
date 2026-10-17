package dev.ss05.halley.net;

import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The addon's own two small packets (Fabric's networking): the server's no-cooldown settings to each player, and
 * each player's own switch back. Optional both ways, so a game without the addon on the other side still connects.
 * The client's own receiver (for {@link SettingsPayload}) is registered from {@code client/HalleyClient} instead, so
 * nothing client-only loads on a dedicated server.
 */
public final class AddonNetwork {
    private AddonNetwork() {
    }

    /** Registers both payload types and the server's receiver for the player's switch. */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChoicePayload.TYPE, ChoicePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChoicePayload.TYPE,
            (payload, context) -> NoCooldown.choose(context.player(), payload.noCooldown()));
    }

    /** Tells a player the server's settings (as they join, and whenever the config file changes). */
    public static void sendSettings(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SettingsPayload.TYPE)) {
            ServerPlayNetworking.send(player, new SettingsPayload(HalleyConfig.noCooldown(), HalleyConfig.noCooldownSurvival()));
        }
    }

    public static void sendSettingsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }
}
