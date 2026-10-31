package dev.ss05.halley.compat;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.world.Excavation;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the addon needs from The Shooting Star, in its own types only. Implemented by {@code compat/tss/TssLink}, of
 * which the release jar holds one copy per supported version of The Shooting Star; {@link StarBridge} picks one.
 */
public interface StarLink {
    /** Adds Halley and Luna to the Stellar Remote. Throws if this version of The Shooting Star doesn't let it. */
    void install() throws Exception;

    /** Runs the strikes in progress, once per server tick (The Shooting Star 1.5.0+ doesn't run them for us). */
    void tick(MinecraftServer server);

    /** Forgets every strike in progress (the server stopped). */
    void clear();

    /** Catches Halley's and Luna's effect packets on the client (see compat/tss/client/StarClientBridge). */
    void installClient();

    /** The index The Shooting Star uses for Halley in its packets. */
    int skillIndex();

    /** The index The Shooting Star uses for Luna in its packets. */
    int moonIndex();

    /** Halley's or Luna's {@code Skill} object (for the test harness). */
    Object skill(boolean moon);

    /**
     * Starts a spell and broadcasts its effect packet to nearby players, like the built-in skills. Its timeline (the
     * server's and every client's film) runs at {@code tuning.speed()}, the remote's speed slider.
     */
    void startHalley(ServerPlayer caster, Vec3 target, long seed, HalleyParams params, CastTuning tuning);

    void startMoon(ServerPlayer caster, Vec3 target, long seed, MoonParams params, CastTuning tuning);

    boolean alreadyFlying(boolean moon, ServerPlayer player);

    /** The caster's currently running strike (its origin, target and seed), or null (the test harness only). */
    StrikeInfo activeStrike(boolean moon, java.util.UUID caster);

    /** The point on the ground under the crosshair (or under a creature in it), the way the built-in skills aim. */
    Vec3 aimGround(ServerPlayer player, double reach, double fallback);

    /** The purple action-bar message the remote uses when it refuses. */
    void deny(ServerPlayer player, String message);

    /** Resistance and fire resistance, refreshed every half second while the caster's own spell runs. */
    void ward(ServerPlayer caster);

    /** Whether a spell from {@code caster} may hit this entity (not the caster, their pets, armour stands...). */
    boolean affects(Entity entity, ServerPlayer caster);

    /** Kills outright, the way the other skills' strike zones do (bosses included). */
    void erase(ServerLevel level, LivingEntity victim, ServerPlayer attacker);

    /**
     * One hit of {@code damage}, the way the other skills hit when their damage slider is set: The Shooting Star's own
     * damage type; at {@link Float#POSITIVE_INFINITY} it erases, as {@link #erase} does.
     */
    void hit(ServerLevel level, LivingEntity victim, ServerPlayer attacker, float damage);

    /** Teleports a caster standing within {@code zone} of the target out to {@code refuge}, held in the air. */
    boolean liftClear(ServerPlayer caster, Vec3 target, double zone, double refuge, int ticks);

    /** Places blocks fast (straight into the chunk sections), relights and resends the chunks. */
    void paint(ServerLevel level, Map<BlockPos, BlockState> blocks);

    /**
     * Cuts everything above a floor away, a chunk at a time, nearest {@code centre} first: the same fast carver the
     * built-in skills use. {@code floor} returns {@link dev.ss05.halley.HalleyPlan#UNTOUCHED} outside the shape.
     * {@code loader} keeps the chunks it is waiting for loaded, a few at a time (null: only chunks already loaded).
     */
    Excavation excavate(ServerLevel level, Vec3 centre, double radius, Excavation.Floor floor,
                        Excavation.Surface surface, BlockPos spared, String name, int chunksPerTick, Excavation.Loader loader);
}
