package dev.ss05.halley.net;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server: no cooldown, evac lift, Epic mode, and whether Stellar Quartet is installed. */
public record ChoicePayload(boolean noCooldown, boolean evacLift, boolean epic, boolean quartet) {
    // The old byte-buf API has no protocol negotiation: an older addon's one-boolean packet needs its own channel.
    public static final ResourceLocation ID = HalleyAddon.id("no_cooldown_v4");

    public static void write(FriendlyByteBuf buf, ChoicePayload payload) {
        buf.writeBoolean(payload.noCooldown());
        buf.writeBoolean(payload.evacLift());
        buf.writeBoolean(payload.epic());
        buf.writeBoolean(payload.quartet());
    }

    public static ChoicePayload read(FriendlyByteBuf buf) {
        return new ChoicePayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }
}
