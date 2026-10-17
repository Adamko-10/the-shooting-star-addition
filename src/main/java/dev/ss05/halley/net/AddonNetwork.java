package dev.ss05.halley.net;

import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The addon's own two small packets (Fabric API's networking): the server's no-cooldown settings to each player, and
 * each player's own switch back. Optional both ways (plain {@code PayloadTypeRegistry} registration, no "required
 * channel" handshake), so a game without the addon on the other side still connects.
 */
public final class AddonNetwork {
    private AddonNetwork() {
    }

    /** Registered from the main entrypoint (runs on both the client and a dedicated server). */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChoicePayload.TYPE, ChoicePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChoicePayload.TYPE, (payload, context) ->
            NoCooldown.choose(context.player(), payload.noCooldown()));
    }

    /** Registered from the client entrypoint only, so nothing client-only is loaded on a dedicated server. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) ->
            ServerSettings.received(payload.noCooldown(), payload.survival()));
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
