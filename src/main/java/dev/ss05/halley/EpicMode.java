package dev.ss05.halley;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Luna's Epic mode: a slower, more ominous fall ({@link MoonPlan#EPIC_FALL}) that everyone watching sees, with the long
 * cut of her music. Each caster's own switch (Z in the remote's menu on 26.3), as their game last told the server; off by default.
 */
public final class EpicMode {
    private static final Map<UUID, Boolean> ON = new ConcurrentHashMap<>();

    private EpicMode() {
    }

    public static boolean on(ServerPlayer player) {
        return ON.getOrDefault(player.getUUID(), false);
    }

    public static void choose(ServerPlayer player, boolean on) {
        ON.put(player.getUUID(), on);
    }

    public static void forget(ServerPlayer player) {
        ON.remove(player.getUUID());
    }
}
