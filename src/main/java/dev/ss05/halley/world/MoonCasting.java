package dev.ss05.halley.world;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.NoCooldown;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** What happens when SS-06's button is pressed (server side). The cooldown is handled by the remote itself (see NoCooldown). */
public final class MoonCasting {
    private MoonCasting() {
    }

    /**
     * Returns false (and tells the player why) if the moon can't be called right now. {@code cast} is the remote's
     * sliders: the moon's and the crater's sizes are the config's times its power, and it runs at its speed.
     */
    public static boolean cast(ServerPlayer player, CastTuning cast) {
        // (with no cooldown, another may be called down while one is still falling)
        if (!NoCooldown.appliesTo(player) && StarBridge.moonAlreadyFalling(player)) {
            StarBridge.deny(player, MoonInfo.title() + ": the moon is already falling");
            return false;
        }
        ServerLevel level = player.serverLevel();
        MoonConfig.Tuning tuning = MoonConfig.tuning();
        if (level.dimensionType().hasFixedTime()) {
            StarBridge.deny(player, MoonInfo.title() + ": there is no moon here");
            return false;
        }
        if (tuning.nightOnly() && !level.isNight()) {
            StarBridge.deny(player, MoonInfo.title() + " only answers at night");
            return false;
        }
        Vec3 target = StarBridge.aimGround(player, tuning.reach(), 260.0);
        // By night it falls from where the moon hangs. By day (the sky falls to night for the strike, like SS-05's) it
        // falls from where the moon would hang twelve hours on, i.e. where the sun is now.
        float timeOfDay = level.getTimeOfDay(1.0F);
        Vec3 sky = MoonPlan.skyDirection(level.isNight() ? timeOfDay : timeOfDay + 0.5F);
        long seed = player.getRandom().nextLong();
        StarBridge.startMoon(player, target, seed, MoonParams.of(tuning, Mth.floor(target.y), sky, cast.power()), cast);
        return true;
    }
}
