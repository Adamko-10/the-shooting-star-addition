package dev.ss05.halley.net;

import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The addon's own two small packets (NeoForge's networking): the server's no-cooldown settings to each player, and
 * each player's own switch back. Optional both ways, so a game without the addon on the other side still connects.
 */
public final class AddonNetwork {
    private AddonNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, AddonNetwork::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(SettingsPayload.TYPE, SettingsPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> ServerSettings.received(payload.noCooldown(), payload.survival())));
        registrar.playToServer(ChoicePayload.TYPE, ChoicePayload.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NoCooldown.choose(player, payload.noCooldown());
            }
        }));
    }

    /** Tells a player the server's settings (as they join, and whenever the config file changes). */
    public static void sendSettings(ServerPlayer player) {
        if (player.connection.hasChannel(SettingsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new SettingsPayload(HalleyConfig.noCooldown(), HalleyConfig.noCooldownSurvival()));
        }
    }

    public static void sendSettingsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }
}
