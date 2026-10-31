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
        buf.writeVec3(this.origin);
        buf.writeVec3(this.target);
        buf.writeFloat(this.yaw);
        buf.writeLong(this.seed);
        buf.writeVarIntArray(this.targets);
        buf.writeVarInt(this.flags);
        buf.writeFloat(this.power);
        buf.writeFloat(this.speed);
    }

    private static StrikeFx read(FriendlyByteBuf buf) {
        return new StrikeFx(buf.readVarInt(), buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readFloat(),
            buf.readLong(), buf.readVarIntArray(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }
}
