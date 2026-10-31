package dev.ss05.halley.compat;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
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
 * How the rest of the addon reaches The Shooting Star. The code that touches it is {@code compat/tss/}, and the
 * release jar holds one copy of that per version of The Shooting Star it supports ({@code compat/v1_4_1/} and so on,
 * each mapped to that version's obfuscated names). {@link #install()} picks the copy for the version that is running
 * ({@link TssVersion}); everything else here hands over to it.
 *
 * <p>In the dev runs nothing is copied yet, so the plain {@code compat/tss/} is used.
 */
public final class StarBridge {
    /** Where the copies of compat/tss/ live in the release jar: this, then the version's {@link TssVersion#tag}. */
    public static final String VERSIONED = "dev.ss05.halley.compat.";
    /** compat/tss/ itself (dev runs only; the release jar has only the copies). */
    public static final String UNVERSIONED = "dev.ss05.halley.compat.tss";

    private static volatile StarLink link;
    private static volatile String problem = "not installed yet";

    private StarBridge() {
    }

    public static boolean installed() {
        return link != null;
    }

    public static String problem() {
        return problem;
    }

    /**
     * Adds Halley and Luna to the Stellar Remote: after the built-in skills, each with its own index, key binding,
     * menu row, tooltip line and cooldown, exactly as if they had been among them. Safe to call more than once.
     */
    public static synchronized boolean install() {
        if (link != null) {
            return true;
        }
        try {
            StarLink found = find();
            found.install();
            link = found;
            problem = "";
            return true;
        } catch (Throwable failed) {
            problem = failed.getClass().getSimpleName() + ": " + failed.getMessage();
            HalleyAddon.LOG.error("Halley and Luna couldn't attach to The Shooting Star, so the skills are disabled. "
                + "This usually means The Shooting Star was updated - see compat/tss/TssLink.java.", failed);
            return false;
        }
    }

    /** The copy of compat/tss/ built for the running version of The Shooting Star. */
    private static StarLink find() throws ReflectiveOperationException {
        String version = TssVersion.running();
        if (version == null) {
            throw new IllegalStateException("The Shooting Star isn't loaded");
        }
        ClassLoader loader = StarBridge.class.getClassLoader();
        for (String pkg : new String[] {VERSIONED + TssVersion.tag(version), UNVERSIONED}) {
            Class<?> type;
            try {
                type = Class.forName(pkg + ".TssLink", true, loader);
            } catch (ClassNotFoundException absent) {
                continue;
            }
            HalleyAddon.LOG.info("Attaching to The Shooting Star {} through {}", version, pkg);
            return (StarLink) type.getDeclaredConstructor().newInstance();
        }
        throw new IllegalStateException("this build of The Shooting Star Addition doesn't support The Shooting Star " + version);
    }

    /** Runs the strikes in progress; from the loader's server tick. Does nothing if the addon didn't attach. */
    public static void tick(MinecraftServer server) {
        if (link != null) {
            link.tick(server);
        }
    }

    /** Forgets every strike in progress; when the server stops. */
    public static void clear() {
        if (link != null) {
            link.clear();
        }
    }

    /** The client half: catches Halley's and Luna's effect packets. Does nothing if the server half didn't attach. */
    public static void installClient() {
        if (link != null) {
            link.installClient();
        }
    }

    /** The index The Shooting Star uses for Halley in its packets, or -1 when it isn't attached. */
    public static int skillIndex() {
        return link != null ? link.skillIndex() : -1;
    }

    /** The index The Shooting Star uses for Luna in its packets, or -1 when it isn't attached. */
    public static int moonIndex() {
        return link != null ? link.moonIndex() : -1;
    }

    /** Halley's or Luna's {@code Skill} object, or null when it isn't attached (for the test harness). */
    public static Object skill(boolean moon) {
        return link != null ? link.skill(moon) : null;
    }

    private static StarLink link() {
        StarLink current = link;
        if (current == null) {
            throw new IllegalStateException("The Shooting Star Addition isn't attached to The Shooting Star (" + problem + ")");
        }
        return current;
    }

    // ---- Handed over to the attached compat/tss/ (see StarLink for what each does) ---------------------------------

    public static void startHalley(ServerPlayer caster, Vec3 target, long seed, HalleyParams params, CastTuning tuning) {
        link().startHalley(caster, target, seed, params, tuning);
    }

    public static void startMoon(ServerPlayer caster, Vec3 target, long seed, MoonParams params, CastTuning tuning) {
        link().startMoon(caster, target, seed, params, tuning);
    }

    public static boolean alreadyFlying(ServerPlayer player) {
        return link().alreadyFlying(false, player);
    }

    public static boolean moonAlreadyFalling(ServerPlayer player) {
        return link().alreadyFlying(true, player);
    }

    /** The caster's currently running strike, or null (the test harness only). */
    public static StrikeInfo activeStrike(boolean moon, java.util.UUID caster) {
        return link != null ? link.activeStrike(moon, caster) : null;
    }

    public static Vec3 aimGround(ServerPlayer player, double reach, double fallback) {
        return link().aimGround(player, reach, fallback);
    }

    public static void deny(ServerPlayer player, String message) {
        link().deny(player, message);
    }

    public static void ward(ServerPlayer caster) {
        link().ward(caster);
    }

    public static boolean affects(Entity entity, ServerPlayer caster) {
        return link().affects(entity, caster);
    }

    public static void erase(ServerLevel level, LivingEntity victim, ServerPlayer attacker) {
        link().erase(level, victim, attacker);
    }

    public static void hit(ServerLevel level, LivingEntity victim, ServerPlayer attacker, float damage) {
        link().hit(level, victim, attacker, damage);
    }

    public static boolean liftClear(ServerPlayer caster, Vec3 target, double zone, double refuge, int ticks) {
        return link().liftClear(caster, target, zone, refuge, ticks);
    }

    public static void paint(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        link().paint(level, blocks);
    }

    public static Excavation excavate(ServerLevel level, Vec3 centre, double radius, Excavation.Floor floor,
                                      Excavation.Surface surface, BlockPos spared, String name, int chunksPerTick,
                                      Excavation.Loader loader) {
        return link().excavate(level, centre, radius, floor, surface, spared, name, chunksPerTick, loader);
    }
}
