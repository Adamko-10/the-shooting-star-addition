package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The addon's own two small packets (Forge's SimpleChannel): the server's no-cooldown settings to each player, and
 * each player's own switch back. Optional both ways, so a game without the addon on the other side still connects.
 */
public final class AddonNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HalleyAddon.id("main"),
        () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));

    private AddonNetwork() {
    }

    public static void register(IEventBus modBus) {
        CHANNEL.messageBuilder(SettingsPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SettingsPayload::write)
            .decoder(SettingsPayload::new)
            .consumerMainThread((payload, context) -> {
                ServerSettings.received(payload.noCooldown(), payload.survival());
                context.get().setPacketHandled(true);
            })
            .add();
        CHANNEL.messageBuilder(ChoicePayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ChoicePayload::write)
            .decoder(ChoicePayload::new)
            .consumerMainThread((payload, context) -> {
                ServerPlayer player = context.get().getSender();
                if (player != null) {
                    NoCooldown.choose(player, payload.noCooldown());
                }
                context.get().setPacketHandled(true);
            })
            .add();
    }

    /** Tells a player the server's settings (as they join, and whenever the config file changes). */
    public static void sendSettings(ServerPlayer player) {
        if (CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SettingsPayload(HalleyConfig.noCooldown(), HalleyConfig.noCooldownSurvival()));
        }
    }

    public static void sendSettingsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }

    /** Whether this game's connection to the server has the addon's channel open (used before sending it something). */
    public static boolean remotePresent(net.minecraft.network.Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    /** Client side: this player's own switch, to the server (see {@code AdditionPanel.sendChoice}). */
    public static void sendChoice(ChoicePayload payload) {
        CHANNEL.sendToServer(payload);
    }
}
