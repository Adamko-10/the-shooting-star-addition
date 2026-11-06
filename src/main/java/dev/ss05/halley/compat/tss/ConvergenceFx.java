package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.net.RimuNet;
import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A Stellar Convergence on its way (server to the players near it): who called it, where, the game time every weapon
 * hits, whether they are spread in a ring, and each weapon's colour and aim point. Its weapons' own effect packets come as each one is
 * cast; this is what the caster's game holds their films back by and what the shared effect is drawn from. Fabric: a
 * vanilla payload, which RimuLib's network takes as it is (as {@link StrikeFx}).
 */
public record ConvergenceFx(int casterId, Vec3 target, long hitAt, boolean ring, int[] colors, Vec3[] points) implements CustomPacketPayload {
    public static final Type<ConvergenceFx> TYPE = new Type<>(HalleyAddon.id("convergence_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConvergenceFx> CODEC = StreamCodec.of(ConvergenceFx::write, ConvergenceFx::read);
    /** Players this far from the mark are told (they can see the weapons coming). */
    private static final double REACH = 1500.0;

    @Override
    public Type<ConvergenceFx> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ConvergenceFx fx) {
        buf.writeVarInt(fx.casterId);
        writeVec3(buf, fx.target);
        buf.writeLong(fx.hitAt);
        buf.writeBoolean(fx.ring);
        buf.writeVarIntArray(fx.colors);
        buf.writeVarInt(fx.points.length);
        for (Vec3 point : fx.points) {
            writeVec3(buf, point);
        }
    }

    private static ConvergenceFx read(RegistryFriendlyByteBuf buf) {
        int casterId = buf.readVarInt();
        Vec3 target = readVec3(buf);
        long hitAt = buf.readLong();
        boolean ring = buf.readBoolean();
        int[] colors = buf.readVarIntArray(32);
        Vec3[] points = new Vec3[Math.min(buf.readVarInt(), 32)];
        for (int i = 0; i < points.length; i++) {
            points[i] = readVec3(buf);
        }
        return new ConvergenceFx(casterId, target, hitAt, ring, colors, points);
    }

    /** 26.3's {@code FriendlyByteBuf} has no {@code write/readVec3} (see StrikeFx): three doubles. */
    private static void writeVec3(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x).writeDouble(v.y).writeDouble(v.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static void send(ServerPlayer caster, ConvergenceFx fx) {
        for (ServerPlayer player : caster.level().players()) {
            if ((player == caster || player.position().distanceTo(fx.target()) < REACH) && RimuNet.canSend(player, TYPE)) {
                RimuNet.send(player, fx);
            }
        }
    }
}
