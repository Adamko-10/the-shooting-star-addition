package dev.ss05.halley.net;

import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The addon's own two small packets (the old Fabric networking API, plain {@link net.minecraft.resources.ResourceLocation}
 * channels): the server's no-cooldown settings to each player, and each player's own switch back. Optional both ways,
 * so a game without the addon on the other side still connects ({@code canSend} is checked before every send).
 *
 * <p>The client's receiver (for {@link SettingsPayload}) is registered separately, from the client entrypoint
 * ({@code HalleyClient}), so nothing client-only loads on a dedicated server.
 */
public final class AddonNetwork {
    private AddonNetwork() {
    }

    /** The server's receiver for a player's own switch (safe on a dedicated server). */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ChoicePayload.ID, (server, player, handler, buf, responseSender) -> {
            ChoicePayload payload = ChoicePayload.read(buf);
            server.execute(() -> NoCooldown.choose(player, payload.noCooldown()));
        });
    }

    /** Tells a player the server's settings (as they join, and whenever the config file changes). */
    public static void sendSettings(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SettingsPayload.ID)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            SettingsPayload.write(buf, new SettingsPayload(HalleyConfig.noCooldown(), HalleyConfig.noCooldownSurvival()));
            ServerPlayNetworking.send(player, SettingsPayload.ID, buf);
        }
    }

    public static void sendSettingsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }
}
