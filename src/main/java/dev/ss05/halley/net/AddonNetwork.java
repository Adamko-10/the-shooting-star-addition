package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * The addon's own two small packets (Forge's networking): the server's no-cooldown settings to each player, and
 * each player's own switch back. Optional both ways, so a game without the addon on the other side still connects.
 */
public final class AddonNetwork {
    private static SimpleChannel CHANNEL;

    private AddonNetwork() {
    }

    public static void register(IEventBus modBus) {
        CHANNEL = ChannelBuilder.named(HalleyAddon.id("addon"))
            .networkProtocolVersion(1)
            .optional()
            .simpleChannel();

        CHANNEL.messageBuilder(SettingsPayload.class, NetworkProtocol.PLAY)
            .direction(PacketFlow.CLIENTBOUND)
            .encoder(SettingsPayload::write)
            .decoder(SettingsPayload::read)
            .consumerMainThread((payload, context) -> ServerSettings.received(payload.noCooldown(), payload.survival()))
            .add();

        CHANNEL.messageBuilder(ChoicePayload.class, NetworkProtocol.PLAY)
            .direction(PacketFlow.SERVERBOUND)
            .encoder(ChoicePayload::write)
            .decoder(ChoicePayload::read)
            .consumerMainThread((payload, context) -> {
                if (context.getSender() instanceof ServerPlayer player) {
                    NoCooldown.choose(player, payload.noCooldown());
                }
            })
            .add();
    }

    /** Tells a player the server's settings (as they join, and whenever the config file changes). */
    public static void sendSettings(ServerPlayer player) {
        if (CHANNEL.isRemotePresent(player.connection.getConnection())) {
            CHANNEL.send(new SettingsPayload(HalleyConfig.noCooldown(), HalleyConfig.noCooldownSurvival()), PacketDistributor.PLAYER.with(player));
        }
    }

    public static void sendSettingsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }

    /** Tells the server this player's own no-cooldown switch (client side only; see client/AdditionPanel). */
    public static void sendChoice(boolean noCooldown) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && CHANNEL.isRemotePresent(connection.getConnection())) {
            CHANNEL.send(new ChoicePayload(noCooldown), PacketDistributor.SERVER.noArg());
        }
    }
}
