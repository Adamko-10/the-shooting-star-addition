package dev.ss05.halley.compat.tss;

import dev.ss05.halley.HalleyAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * The start of a Halley or Luna strike, sent to the players near it (The Shooting Star's own effect packet only
 * carries its own skills since 1.5.0). Sent through RimuLib's network (Fabric: {@code RimuNet}/{@code RimuNetClient}
 * work directly with vanilla's {@link CustomPacketPayload}, unlike the wrapper the other loaders' RimuLib offers), so
 * this is its own payload.
 */
public record StrikeFx(int skill, int casterId, Vec3 origin, Vec3 target, float yaw, long seed, int[] targets, int flags,
                       float power, float speed) implements CustomPacketPayload {
    /** The caster plays their films for everyone near the strike (the same bit as The Shooting Star's packet). */
    public static final int FLAG_FOR_EVERYONE = 8;

    public static final Type<StrikeFx> TYPE = new Type<>(HalleyAddon.id("strike_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrikeFx> CODEC = StreamCodec.of(StrikeFx::write, StrikeFx::read);

    @Override
    public Type<StrikeFx> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, StrikeFx s) {
        buf.writeVarInt(s.skill);
        buf.writeVarInt(s.casterId);
        writeVec3(buf, s.origin);
        writeVec3(buf, s.target);
        buf.writeFloat(s.yaw);
        buf.writeLong(s.seed);
        buf.writeVarIntArray(s.targets);
        buf.writeVarInt(s.flags);
        buf.writeFloat(s.power);
        buf.writeFloat(s.speed);
    }

    private static StrikeFx read(RegistryFriendlyByteBuf buf) {
        return new StrikeFx(buf.readVarInt(), buf.readVarInt(), readVec3(buf), readVec3(buf), buf.readFloat(),
            buf.readLong(), buf.readVarIntArray(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    /** 26.3's {@code FriendlyByteBuf} has no {@code write/readVec3}, unlike the other branches': three doubles. */
    private static void writeVec3(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x).writeDouble(v.y).writeDouble(v.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
