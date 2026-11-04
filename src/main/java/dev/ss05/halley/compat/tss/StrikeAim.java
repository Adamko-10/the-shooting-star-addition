package dev.ss05.halley.compat.tss;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Where an operator's {@code /ssa strike ... at} aims, for the player who ran it: every ground aim of theirs answers
 * it for the skill's whole run (The Shooting Star's beams follow the caster's crosshair while they fire, so aiming
 * only the cast left the Railgun sweeping off to where the player looked, 2026-11-04). Cleared when the run is over,
 * or when that player casts from the remote.
 */
public final class StrikeAim {
    private record Aim(Vec3 at, long until) {
    }

    private static final Map<UUID, Aim> AIMS = new ConcurrentHashMap<>();

    private StrikeAim() {
    }

    @Nullable
    public static Vec3 at(ServerPlayer player) {
        Aim aim = AIMS.get(player.getUUID());
        if (aim == null) {
            return null;
        }
        if (player.serverLevel().getGameTime() > aim.until()) {
            AIMS.remove(player.getUUID(), aim);
            return null;
        }
        return aim.at();
    }

    static void set(ServerPlayer player, Vec3 at, int ticks) {
        AIMS.put(player.getUUID(), new Aim(at, player.serverLevel().getGameTime() + ticks));
    }

    public static void clear(ServerPlayer player) {
        AIMS.remove(player.getUUID());
    }
}
