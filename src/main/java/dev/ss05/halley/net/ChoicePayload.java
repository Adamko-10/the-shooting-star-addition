package dev.ss05.halley.net;

import net.minecraft.network.FriendlyByteBuf;

/** Client to server: the player's own no-cooldown switch in the Stellar Remote's menu (see {@code NoCooldown}). */
public record ChoicePayload(boolean noCooldown) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(noCooldown);
    }

    public static ChoicePayload read(FriendlyByteBuf buf) {
        return new ChoicePayload(buf.readBoolean());
    }
}
