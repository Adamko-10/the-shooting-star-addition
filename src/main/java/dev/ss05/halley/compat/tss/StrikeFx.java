package dev.ss05.halley.compat.tss;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * The start of a Halley or Luna strike, sent to the players near it (The Shooting Star's own effect packet only
 * carries its own skills since 1.5.0). Sent through RimuLib's network (RimuNet.s2c/send/canSend, RimuNetClient.receive):
 * on Fabric that is plain vanilla networking (a {@link CustomPacketPayload} of our own), unlike NeoForge's RimuPacket
 * wrapper - check with javap before assuming the same shape on another loader.
 */
public record StrikeFx(int skill, int casterId, Vec3 origin, Vec3 target, float yaw, long seed, int[] targets, int flags,
                       float power, float speed) implements CustomPacketPayload {
    /** The caster plays their films for everyone near the strike (the same bit as The Shooting Star's packet). */
    public static final int FLAG_FOR_EVERYONE = 8;

    public static final Type<StrikeFx> TYPE = new Type<>(HalleyAddon.id("strike_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrikeFx> CODEC = StreamCodec.of(
        (buf, value) -> value.write(buf), StrikeFx::read);

    @Override
    public Type<StrikeFx> type() {
        return TYPE;
    }

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
