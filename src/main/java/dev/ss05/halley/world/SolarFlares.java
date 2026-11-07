package dev.ss05.halley.world;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.ProximaConfig;
import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.compat.StarBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Random solar flares (docs/SS-07-PROXIMA.md stage 5; {@code [proxima_flares]} in the common config, off by default):
 * now and then, with nobody casting it, Proxima flares near a player in the overworld. Each is announced in chat first
 * (a space weather alert, {@code warning_seconds} ahead), then runs as a Proxima strike that starts at the flash, has no
 * caster and no film: minor (the sky lights up), major (its light burns creatures under the open sky and the plants
 * round it) or cataclysm (the whole strike). Operators can call one with {@code /ssa flare <tier>}.
 */
public final class SolarFlares {
    /** A flare announced and on its way. */
    private record Pending(ServerLevel level, UUID near, Vec3 target, int tier, float power, long at) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** A natural cataclysm's hits: lethal to the unarmoured, not an erasure. */
    private static final float DAMAGE = 60.0F;
    /** The overworld game time of the last flare announced (-1: none yet). */
    private static long last = -1L;

    private SolarFlares() {
    }

    /** From the server tick: fires the flares that are due; once a minute, maybe announces a new one. */
    public static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            PENDING.removeIf(pending -> {
                if (pending.level().getGameTime() < pending.at()) {
                    return false;
                }
                fire(pending);
                return true;
            });
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        ProximaConfig.Tuning config = ProximaConfig.tuning();
        if (!config.randomFlares() || now % 1200L != 0L || !PENDING.isEmpty() || !StarBridge.installed()) {
            return;
        }
        if (last >= 0L && now - last < config.minMinutes() * 1200L || RANDOM.nextInt(config.averageMinutes()) != 0) {
            return;
        }
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (!players.isEmpty()) {
            schedule(players.get(RANDOM.nextInt(players.size())), tier(config.maxTier()), config.warningSeconds());
        }
    }

    public static void clear() {
        PENDING.clear();
        last = -1L;
    }

    /** Most flares are minor, some major, few cataclysms (as many of each as {@code max} allows). */
    private static int tier(ProximaConfig.FlareTier max) {
        int[] weights = {70, 25, 5};
        int total = 0;
        for (int i = 0; i <= max.ordinal(); i++) {
            total += weights[i];
        }
        int pick = RANDOM.nextInt(total);
        for (int i = 0; i <= max.ordinal(); i++) {
            pick -= weights[i];
            if (pick < 0) {
                return ProximaParams.MINOR + i;
            }
        }
        return ProximaParams.MINOR;
    }

    /**
     * Announces a flare of this tier ({@link ProximaParams#MINOR} to {@link ProximaParams#CATACLYSM}) near this player,
     * {@code seconds} from now, to everyone in their world: a minor one anywhere in their sky, a major one close by (its
     * light falls on them), a cataclysm far enough off that its bowl misses them but its shock wave doesn't.
     */
    public static void schedule(ServerPlayer near, int tier, int seconds) {
        ServerLevel level = near.level();
        float power = switch (tier) {
            case ProximaParams.MINOR -> 0.2F + 0.3F * RANDOM.nextFloat();
            case ProximaParams.MAJOR -> 0.5F + 0.7F * RANDOM.nextFloat();
            default -> 1.0F + RANDOM.nextFloat();
        };
        double distance = switch (tier) {
            case ProximaParams.MINOR -> 100.0 + 300.0 * RANDOM.nextDouble();
            case ProximaParams.MAJOR -> 20.0 + 60.0 * RANDOM.nextDouble();
            default -> 150.0 + 120.0 * RANDOM.nextDouble();
        };
        double a = RANDOM.nextDouble() * Math.PI * 2.0;
        double x = near.getX() + Math.cos(a) * distance;
        double z = near.getZ() + Math.sin(a) * distance;
        Vec3 target = new Vec3(x, ground(level, x, z, near.getY()), z);
        PENDING.add(new Pending(level, near.getUUID(), target, tier, power, level.getGameTime() + seconds * 20L));
        last = level.getGameTime();
        HalleyAddon.LOG.info("Solar flare announced: tier {} ({}-class) at {}, {} near {}, in {} s", tier, ProximaParams.flareClass(power),
            (int) target.x, (int) target.z, near.getGameProfile().name(), seconds);
        Component alert = alert(tier, power, target, seconds);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(alert);
        }
    }

    private static Component alert(int tier, float power, Vec3 target, int seconds) {
        // "a C2.1", "an M4.7", "an X1.3"
        String cls = (ProximaParams.flareClass(power).startsWith("C") ? "a " : "an ") + ProximaParams.flareClass(power);
        String where = String.format(Locale.ROOT, "%d, %d", (int) target.x, (int) target.z);
        return switch (tier) {
            case ProximaParams.MINOR -> Component.literal("Space weather: " + cls + "-class solar flare will light up the sky in "
                + seconds + " s.").withStyle(ChatFormatting.YELLOW);
            case ProximaParams.MAJOR -> Component.literal("Space weather alert: " + cls + "-class solar flare strikes near " + where
                + " in " + seconds + " s. Its light burns: get under " + ProximaConfig.tuning().shelterBlocks()
                + " solid blocks!").withStyle(ChatFormatting.GOLD);
            default -> Component.literal("SPACE WEATHER EMERGENCY: " + cls + "-class flare and a coronal mass ejection hit "
                + where + " in " + seconds + " s. Take shelter deep underground or get far away!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        };
    }

    private static void fire(Pending pending) {
        ServerLevel level = pending.level();
        // the effect packet needs someone to stand in for a caster: the player it was announced near, or anyone in that world
        ServerPlayer near = level.getServer().getPlayerList().getPlayer(pending.near());
        if (near == null || near.level() != level) {
            near = level.players().stream().filter(p -> !p.isSpectator()).findFirst().orElse(null);
        }
        if (near == null || !StarBridge.installed()) {
            return;
        }
        float power = pending.power();
        HalleyAddon.LOG.info("Solar flare: tier {} at {}, {}", pending.tier(), (int) pending.target().x, (int) pending.target().z);
        StarBridge.startFlare(near, pending.target(), RANDOM.nextLong(), ProximaParams.flare(power, pending.tier()),
            new CastTuning(power, DAMAGE, 1.0F));
    }

    /** The ground's height there if that land is loaded, else {@code fallback} (never loads land). */
    private static double ground(ServerLevel level, double x, double z, double fallback) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }
}
