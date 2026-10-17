package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player's own no-cooldown switch in the Stellar Remote's menu (see {@code NoCooldown}). */
public record ChoicePayload(boolean noCooldown) implements CustomPacketPayload {
    public static final Type<ChoicePayload> TYPE = new Type<>(HalleyAddon.id("no_cooldown"));
    public static final StreamCodec<ByteBuf, ChoicePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ChoicePayload::noCooldown, ChoicePayload::new);

    @Override
    public Type<ChoicePayload> type() {
        return TYPE;
    }
}
