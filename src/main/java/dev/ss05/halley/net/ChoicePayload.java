package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player's own switches in the Stellar Remote's menu that the server acts on: no cooldown (see
 * {@code NoCooldown}), where the evac takes them (true: straight up; see {@code Evac}) and Luna's Epic mode ({@code EpicMode}); and whether their game has Stellar Quartet ({@code StellarQuartet}).
 */
public record ChoicePayload(boolean noCooldown, boolean evacLift, boolean epic, boolean quartet) implements CustomPacketPayload {
    // Fabric has no channel versions: a new name for each new layout (main: registrar version 4), so a game with an
    // older addon on the other side just doesn't send it
    public static final Type<ChoicePayload> TYPE = new Type<>(HalleyAddon.id("choice_v4"));
    public static final StreamCodec<ByteBuf, ChoicePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ChoicePayload::noCooldown, ByteBufCodecs.BOOL, ChoicePayload::evacLift, ByteBufCodecs.BOOL,
        ChoicePayload::epic, ByteBufCodecs.BOOL, ChoicePayload::quartet, ChoicePayload::new);

    @Override
    public Type<ChoicePayload> type() {
        return TYPE;
    }
}
