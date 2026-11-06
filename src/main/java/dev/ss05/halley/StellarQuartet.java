package dev.ss05.halley;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * SA-00 Stellar Convergence unlocks with The Shooting Star - Stellar Quartet (by ikun520, CurseForge 1732380) installed:
 * the skill is inspired by it, and the user doesn't want this addon to draw players away from it (2026-11-06). Stellar
 * Quartet is client-only, so each player's game says whether it has it ({@code ChoicePayload}) and the server unlocks
 * the skill for that player. Nothing of Stellar Quartet's is used: only whether it is loaded.
 *
 * <p>Only on the builds Stellar Quartet exists for (NeoForge, Forge and Fabric 1.21.1, Fabric 26.3): the 1.20.1 and
 * 1.21.11 builds set {@link #REQUIRED} false, so Convergence is open to everyone there (the user's pick).
 */
public final class StellarQuartet {
    /** Its mod id (from its own jar's neoforge.mods.toml, 1.4.0). */
    public static final String MOD_ID = "star_quartet";
    /** Whether Convergence needs it on this build. */
    public static final boolean REQUIRED = true;
    private static final Map<UUID, Boolean> HAS = new ConcurrentHashMap<>();

    private StellarQuartet() {
    }

    /** Whether this game has Stellar Quartet loaded (a wrong-loader or broken copy isn't loaded, so it doesn't count). */
    public static boolean installedHere() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /** Whether this player may fire Stellar Convergence from the remote. */
    public static boolean unlocked(ServerPlayer player) {
        return !REQUIRED || HAS.getOrDefault(player.getUUID(), false);
    }

    /** What this player's game said (as it joins, and with every other switch it sends). */
    public static void tell(ServerPlayer player, boolean has) {
        HAS.put(player.getUUID(), has);
    }

    public static void forget(ServerPlayer player) {
        HAS.remove(player.getUUID());
    }
}
