package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.net.RimuPacket;
import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * The start of a Halley or Luna strike, sent to the players near it (The Shooting Star's own effect packet only
 * carries its own skills since 1.5.0). Sent through RimuLib's network, so every loader handles it the same way.
 */
public record StrikeFx(int skill, int casterId, Vec3 origin, Vec3 target, float yaw, long seed, int[] targets, int flags,
                       float power, float speed) {
    /** The caster plays their films for everyone near the strike (the same bit as The Shooting Star's packet). */
    public static final int FLAG_FOR_EVERYONE = 8;

    public static final RimuPacket<StrikeFx> PACKET = RimuPacket.of(HalleyAddon.id("strike_fx"), StrikeFx.class,
        StrikeFx::write, StrikeFx::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.skill);
        buf.writeVarInt(this.casterId);
        writeVec3(buf, this.origin);
        writeVec3(buf, this.target);
        buf.writeFloat(this.yaw);
        buf.writeLong(this.seed);
        buf.writeVarInt(this.targets.length);
        for (int t : this.targets) {
            buf.writeVarInt(t);
        }
        buf.writeVarInt(this.flags);
        buf.writeFloat(this.power);
        buf.writeFloat(this.speed);
    }

    private static StrikeFx read(FriendlyByteBuf buf) {
        int skill = buf.readVarInt();
        int casterId = buf.readVarInt();
        Vec3 origin = readVec3(buf);
        Vec3 target = readVec3(buf);
        float yaw = buf.readFloat();
        long seed = buf.readLong();
        int[] targets = new int[buf.readVarInt()];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = buf.readVarInt();
        }
        return new StrikeFx(skill, casterId, origin, target, yaw, seed, targets, buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    private static void writeVec3(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
