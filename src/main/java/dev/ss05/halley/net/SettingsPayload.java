package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the server's {@code no_cooldown} and {@code no_cooldown_survival} (see {@link ServerSettings}). */
public record SettingsPayload(boolean noCooldown, boolean survival) implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(HalleyAddon.id("settings"));
    public static final StreamCodec<ByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SettingsPayload::noCooldown, ByteBufCodecs.BOOL, SettingsPayload::survival, SettingsPayload::new);

    @Override
    public Type<SettingsPayload> type() {
        return TYPE;
    }
}
