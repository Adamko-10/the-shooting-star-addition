package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the server's {@code no_cooldown} and {@code no_cooldown_survival} (see {@link ServerSettings}). */
public record SettingsPayload(boolean noCooldown, boolean survival) {
    public static final ResourceLocation ID = HalleyAddon.id("settings");

    public static void write(FriendlyByteBuf buf, SettingsPayload payload) {
        buf.writeBoolean(payload.noCooldown());
        buf.writeBoolean(payload.survival());
    }

    public static SettingsPayload read(FriendlyByteBuf buf) {
        return new SettingsPayload(buf.readBoolean(), buf.readBoolean());
    }
}
