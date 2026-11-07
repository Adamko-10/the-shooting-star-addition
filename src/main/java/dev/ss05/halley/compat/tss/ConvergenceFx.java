package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.api.NetApi;
import cyou.rimuru.rimulib.net.RimuPacket;
import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A Stellar Convergence on its way (server to the players near it): who called it, where, the game time every weapon
 * hits, whether they are spread in a ring, and each weapon's colour and aim point. Its weapons' own effect packets come as each one is
 * cast; this is what the caster's game holds their films back by and what the shared effect is drawn from.
 */
public record ConvergenceFx(int casterId, Vec3 target, long hitAt, boolean ring, int[] colors, Vec3[] points) {
    public static final RimuPacket<ConvergenceFx> PACKET = NetApi.packet(HalleyAddon.id("convergence_fx"), ConvergenceFx.class,
        ConvergenceFx::write, ConvergenceFx::read);
    /** Players this far from the mark are told (they can see the weapons coming). */
    private static final double REACH = 1500.0;

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.casterId);
        writeVec(buf, this.target);
        buf.writeLong(this.hitAt);
        buf.writeBoolean(this.ring);
        buf.writeVarIntArray(this.colors);
        buf.writeVarInt(this.points.length);
        for (Vec3 point : this.points) {
            writeVec(buf, point);
        }
    }

    private static ConvergenceFx read(FriendlyByteBuf buf) {
        int casterId = buf.readVarInt();
        Vec3 target = readVec(buf);
        long hitAt = buf.readLong();
        boolean ring = buf.readBoolean();
        int[] colors = buf.readVarIntArray(32);
        Vec3[] points = new Vec3[Math.min(buf.readVarInt(), 32)];
        for (int i = 0; i < points.length; i++) {
            points[i] = readVec(buf);
        }
        return new ConvergenceFx(casterId, target, hitAt, ring, colors, points);
    }

    /** Minecraft 1.20.1's FriendlyByteBuf has no writeVec3/readVec3. */
    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static void send(ServerPlayer caster, ConvergenceFx fx) {
        for (ServerPlayer player : caster.serverLevel().players()) {
            if ((player == caster || player.position().distanceTo(fx.target()) < REACH) && NetApi.canSend(player, PACKET)) {
                NetApi.send(player, fx);
            }
        }
    }
}
