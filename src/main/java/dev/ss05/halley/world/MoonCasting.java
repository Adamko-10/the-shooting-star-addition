package dev.ss05.halley.world;

import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.MoonSpell;
import dev.ss05.halley.compat.StarBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** What happens when SS-06's button is pressed (server side). The cooldown is handled by the remote itself. */
public final class MoonCasting {
    private MoonCasting() {
    }

    /** Returns false (and tells the player why) if the moon can't be called right now. */
    public static boolean cast(ServerPlayer player) {
        if (StarBridge.alreadyFlying(StarBridge.MOON, player)) {
            StarBridge.deny(player, MoonInfo.TITLE + ": the moon is already falling");
            return false;
        }
        ServerLevel level = player.serverLevel();
        MoonConfig.Tuning tuning = MoonConfig.tuning();
        if (level.dimensionType().hasFixedTime()) {
            StarBridge.deny(player, MoonInfo.TITLE + ": there is no moon here");
            return false;
        }
        if (tuning.nightOnly() && !level.isNight()) {
            StarBridge.deny(player, MoonInfo.TITLE + " only answers at night");
            return false;
        }
        Vec3 target = StarBridge.aimGround(player, tuning.reach(), 260.0);
        Vec3 sky = MoonPlan.skyDirection(level.getTimeOfDay(1.0F));
        long seed = player.getRandom().nextLong();
        StarBridge.start(new MoonSpell(player, target, seed, MoonParams.of(tuning, Mth.floor(target.y), sky)));
        return true;
    }
}
