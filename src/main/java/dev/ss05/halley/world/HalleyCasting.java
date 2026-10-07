package dev.ss05.halley.world;

import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.compat.HalleySpell;
import dev.ss05.halley.compat.StarBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** What happens when the button is pressed (server side). The cooldown is handled by the remote itself. */
public final class HalleyCasting {
    private HalleyCasting() {
    }

    /** Returns false (and tells the player why) if the comet can't be called right now. */
    public static boolean cast(ServerPlayer player) {
        if (StarBridge.alreadyFlying(player)) {
            StarBridge.deny(player, HalleyInfo.TITLE + " is already on its way");
            return false;
        }
        HalleyConfig.Tuning tuning = HalleyConfig.tuning();
        Vec3 target = StarBridge.aimGround(player, tuning.reach(), 260.0);

        // Where it will touch down only depends on the mark and the trench length, so find the ground there now
        // (this loads that one chunk if needed, like the built-in skills do for the mark itself).
        HalleyPlan probe = new HalleyPlan(player.position(), target, player.getYRot(), 0L, HalleyParams.of(tuning, Mth.floor(target.y)));
        int ground = surface(player.level(), probe.touchdown.x, probe.touchdown.z, Mth.floor(target.y));

        long seed = player.getRandom().nextLong();
        StarBridge.start(new HalleySpell(player, target, seed, HalleyParams.of(tuning, ground)));
        return true;
    }

    /** First air block above the ground (same convention as the mark's y). */
    static int surface(ServerLevel level, double x, double z, int fallback) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.isInWorldBounds(new BlockPos(bx, level.getMinY(), bz))) {
            return fallback;
        }
        level.getChunk(bx >> 4, bz >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return y <= level.getMinY() + 1 ? fallback : y;
    }
}
