package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.api.NetApi;
import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A Stellar Convergence on its way (server to the players near it): who called it, where, the game time every weapon
 * hits, whether they are spread in a ring, and each weapon's colour and aim point. Its weapons' own effect packets come as each one is
 * cast; this is what the caster's game holds their films back by and what the shared effect is drawn from.
 */
public record ConvergenceFx(int casterId, Vec3 target, long hitAt, boolean ring, int[] colors, Vec3[] points)
    implements CustomPacketPayload {
    public static final Type<ConvergenceFx> TYPE = new Type<>(HalleyAddon.id("convergence_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConvergenceFx> CODEC = StreamCodec.of(
        (buf, value) -> value.write(buf), ConvergenceFx::read);
    /** Players this far from the mark are told (they can see the weapons coming). */
    private static final double REACH = 1500.0;

    @Override
    public Type<ConvergenceFx> type() {
        return TYPE;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.casterId);
        buf.writeVec3(this.target);
        buf.writeLong(this.hitAt);
        buf.writeBoolean(this.ring);
        buf.writeVarIntArray(this.colors);
        buf.writeVarInt(this.points.length);
        for (Vec3 point : this.points) {
            buf.writeVec3(point);
        }
    }

    private static ConvergenceFx read(FriendlyByteBuf buf) {
        int casterId = buf.readVarInt();
        Vec3 target = buf.readVec3();
        long hitAt = buf.readLong();
        boolean ring = buf.readBoolean();
        int[] colors = buf.readVarIntArray(32);
        Vec3[] points = new Vec3[Math.min(buf.readVarInt(), 32)];
        for (int i = 0; i < points.length; i++) {
            points[i] = buf.readVec3();
        }
        return new ConvergenceFx(casterId, target, hitAt, ring, colors, points);
    }

    static void send(ServerPlayer caster, ConvergenceFx fx) {
        for (ServerPlayer player : caster.level().players()) {
            if ((player == caster || player.position().distanceTo(fx.target()) < REACH) && NetApi.canSend(player, TYPE)) {
                NetApi.send(player, fx);
            }
        }
    }
}
