package dev.ss05.halley;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a creative caster standing where their strike lands is taken (the evac): straight up above it, or out to the
 * crater's edge. Each player's own switch (E in the remote's menu), as their game last told the server; up by default.
 */
public final class Evac {
    private static final Map<UUID, Boolean> LIFT = new ConcurrentHashMap<>();

    private Evac() {
    }

    /** True: straight up above the strike; false: out to the edge (onto land that is loaded, else up). */
    public static boolean lifts(ServerPlayer player) {
        return LIFT.getOrDefault(player.getUUID(), true);
    }

    public static void choose(ServerPlayer player, boolean lift) {
        LIFT.put(player.getUUID(), lift);
    }

    public static void forget(ServerPlayer player) {
        LIFT.remove(player.getUUID());
    }
}
