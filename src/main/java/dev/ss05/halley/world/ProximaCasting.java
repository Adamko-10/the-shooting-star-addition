package dev.ss05.halley.world;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import dev.ss05.halley.ProximaConfig;
import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.compat.StarBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** What happens when SA-04's button is pressed (server side). The cooldown is handled by the remote itself (see NoCooldown). */
public final class ProximaCasting {
    private ProximaCasting() {
    }

    /** Returns false (and tells the player why) if the flare can't be called right now. {@code cast} is the remote's sliders. */
    public static boolean cast(ServerPlayer player, CastTuning cast) {
        if (!ProximaConfig.tuning().enabled()) {
            StarBridge.deny(player, ProximaInfo.title() + ": switched off on this server");
            return false;
        }
        // (with no cooldown, another may be called while one is still on its way)
        if (!NoCooldown.appliesTo(player) && !NoCooldown.casting() && StarBridge.proximaRunning(player)) {
            StarBridge.deny(player, ProximaInfo.title() + ": the flare is already on its way");
            return false;
        }
        // a flare comes down out of the sky: none in a world with a roof (the Nether), none onto a mark deep under the land
        // (its bowl would be cut into the roof or the surface far above, while the mark itself is erased)
        if (player.serverLevel().dimensionType().hasCeiling()) {
            StarBridge.deny(player, ProximaInfo.title() + ": there is no sky here");
            return false;
        }
        Vec3 target = StarBridge.aimGround(player, HalleyConfig.tuning().reach(), 260.0);
        if (ProximaStrike.groundAt(player.serverLevel(), (int) Math.floor(target.x), (int) Math.floor(target.z)) > target.y + 6.0) {
            StarBridge.deny(player, ProximaInfo.title() + ": the flare needs open sky over the mark");
            return false;
        }
        StarBridge.startProxima(player, target, player.getRandom().nextLong(), ProximaParams.of(cast.power()), cast);
        return true;
    }
}
