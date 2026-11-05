package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player's own switches in the Stellar Remote's menu that the server acts on: no cooldown (see
 * {@code NoCooldown}), where the evac takes them (true: straight up; see {@code Evac}) and Luna's Epic mode ({@code EpicMode}).
 */
public record ChoicePayload(boolean noCooldown, boolean evacLift, boolean epic) implements CustomPacketPayload {
    public static final Type<ChoicePayload> TYPE = new Type<>(HalleyAddon.id("no_cooldown"));
    public static final StreamCodec<ByteBuf, ChoicePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ChoicePayload::noCooldown, ByteBufCodecs.BOOL, ChoicePayload::evacLift, ByteBufCodecs.BOOL,
        ChoicePayload::epic, ChoicePayload::new);

    @Override
    public Type<ChoicePayload> type() {
        return TYPE;
    }
}
