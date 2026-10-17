package dev.ss05.halley.net;

import net.minecraft.network.FriendlyByteBuf;

/** Server to client: the server's {@code no_cooldown} and {@code no_cooldown_survival} (see {@link ServerSettings}). */
public record SettingsPayload(boolean noCooldown, boolean survival) {
    public SettingsPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(noCooldown);
        buf.writeBoolean(survival);
    }
}
