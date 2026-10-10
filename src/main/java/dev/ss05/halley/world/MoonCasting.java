package dev.ss05.halley.world;

import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.MoonSpell;
import dev.ss05.halley.compat.StarBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.EasingType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** What happens when SS-06's button is pressed (server side). The cooldown is handled by the remote itself. */
public final class MoonCasting {
    /**
     * The same easing {@code Timelines.OVERWORLD_DAY} gives its {@code SUN_ANGLE}/{@code MOON_ANGLE} tracks
     * ({@code EasingType.symmetricCubicBezier(0.362F, 0.241F)}), replicated here because the server has no
     * {@code EnvironmentAttributeProbe} to sample those tracks with (see {@link #timeOfDay}).
     */
    private static final EasingType SKY_ANGLE_EASE = EasingType.symmetricCubicBezier(0.362F, 0.241F);

    private MoonCasting() {
    }

    /** Returns false (and tells the player why) if the moon can't be called right now. */
    public static boolean cast(ServerPlayer player) {
        if (StarBridge.alreadyFlying(StarBridge.MOON, player)) {
            StarBridge.deny(player, MoonInfo.TITLE + ": the moon is already falling");
            return false;
        }
        ServerLevel level = player.level();
        MoonConfig.Tuning tuning = MoonConfig.tuning();
        if (level.dimensionType().hasFixedTime()) {
            StarBridge.deny(player, MoonInfo.TITLE + ": there is no moon here");
            return false;
        }
        if (tuning.nightOnly() && !isNight(level)) {
            StarBridge.deny(player, MoonInfo.TITLE + " only answers at night");
            return false;
        }
        Vec3 target = StarBridge.aimGround(player, tuning.reach(), 260.0);
        Vec3 sky = MoonPlan.skyDirection(timeOfDay(level));
        long seed = player.getRandom().nextLong();
        StarBridge.start(new MoonSpell(player, target, seed, MoonParams.of(tuning, Mth.floor(target.y), sky)));
        return true;
    }

    /**
     * 26.3 replaced the old single day/night clock with named, datapack-driven {@code WorldClock}s and time
     * markers; there's no {@code isNight()}/{@code getTimeOfDay(partial)} left to call. This reads the
     * dimension's own clock ({@code Level.getDefaultClockTime()}) and falls back to vanilla's long-standing
     * 24000-tick day (night from tick 13000 to 23000), which is what every default world clock still uses.
     */
    private static boolean isNight(ServerLevel level) {
        return dayTicks(level) >= 13000L && dayTicks(level) < 23000L;
    }

    /**
     * 0..1 fraction of the sun's own rotation (not a plain tick count), for {@link MoonPlan#skyDirection}: the moon
     * starts exactly where 26.3 draws the vanilla moon only if this matches {@code SUN_ANGLE}'s own eased track as
     * closely as {@code getTimeOfDay} matched it on every version before 26.3 (see the class doc on
     * {@link #SKY_ANGLE_EASE}: a plain {@code dayTicks / 24000.0} here would leave the moon early or late most of
     * the night, since that track is not linear in ticks - and neither was {@code getTimeOfDay}). {@code Timelines}
     * keys {@code SUN_ANGLE}'s track to {@code NOON} (tick 6000, sun straight up, angle 0/360), the same reference
     * point used below.
     */
    private static float timeOfDay(ServerLevel level) {
        long ticks = Math.floorMod(dayTicks(level) - 6000L, 24000L);
        return SKY_ANGLE_EASE.apply(ticks / 24000.0F);
    }

    private static long dayTicks(ServerLevel level) {
        return Math.floorMod(level.getDefaultClockTime(), 24000L);
    }
}
