package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the player's own no-cooldown switch in the Stellar Remote's menu (see {@code NoCooldown}). */
public record ChoicePayload(boolean noCooldown) {
    public static final ResourceLocation ID = HalleyAddon.id("no_cooldown");

    public static void write(FriendlyByteBuf buf, ChoicePayload payload) {
        buf.writeBoolean(payload.noCooldown());
    }

    public static ChoicePayload read(FriendlyByteBuf buf) {
        return new ChoicePayload(buf.readBoolean());
    }
}
