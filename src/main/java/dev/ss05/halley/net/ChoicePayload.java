package dev.ss05.halley.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client to server: the player's own switches in the Stellar Remote's menu that the server acts on: no cooldown (see
 * {@code NoCooldown}), where the evac takes them (true: straight up; see {@code Evac}) and Luna's Epic mode ({@code EpicMode}); and whether their game has Stellar Quartet ({@code StellarQuartet}).
 */
public record ChoicePayload(boolean noCooldown, boolean evacLift, boolean epic, boolean quartet) {
    public ChoicePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(noCooldown);
        buf.writeBoolean(evacLift);
        buf.writeBoolean(epic);
        buf.writeBoolean(quartet);
    }
}
