package dev.ss05.halley;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Who has the Stellar Remote's cooldowns off (server side). The server's config allows it ({@code no_cooldown}), for
 * creative and spectator players, and for survival players too if {@code no_cooldown_survival} says so; each player
 * then switches it on or off for themselves in the remote's menu (on until they do). The game hooks are in
 * {@code compat/tss/mixin/CastingMixin} (the remote's cooldowns), {@code world/HalleyCasting} and
 * {@code world/MoonCasting} (casting again while one is still coming down).
 */
public final class NoCooldown {
    /** Each player's own switch, as their game last told the server (players who never said have it on). */
    private static final Map<UUID, Boolean> CHOICE = new ConcurrentHashMap<>();

    private NoCooldown() {
    }

    /** Whether this player's casts skip every cooldown right now. */
    public static boolean appliesTo(ServerPlayer player) {
        return allowed(player) && CHOICE.getOrDefault(player.getUUID(), true);
    }

    /** Whether the server lets this player switch the cooldowns off (whatever their own switch says). */
    public static boolean allowed(ServerPlayer player) {
        return HalleyConfig.noCooldown() && (HalleyConfig.noCooldownSurvival() || player.isCreative() || player.isSpectator());
    }

    /** The player's switch in the remote's menu. */
    public static void choose(ServerPlayer player, boolean on) {
        CHOICE.put(player.getUUID(), on);
    }

    public static void forget(ServerPlayer player) {
        CHOICE.remove(player.getUUID());
    }

    /**
     * A line in chat as a player joins, saying what the option is and how this server has it, so it is known about
     * without opening the remote: to everyone while it is on, and to operators (who can switch it on) while it is off.
     */
    public static void greet(ServerPlayer player) {
        boolean on = HalleyConfig.noCooldown();
        if (!on && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        String who = HalleyConfig.noCooldownSurvival() ? "everyone" : "creative mode only";
        Component line = Component.literal("[The Shooting Star Addition] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(on
                ? "No cooldown is ON here (" + who + "): Stellar Remote skills can be cast again straight away. "
                    + "Switch it for yourself in the remote's menu (H) with C."
                : "No cooldown is OFF. Set no_cooldown = true (and no_cooldown_survival for survival players) in "
                    + "config/shooting_star_addition-common.toml to let players cast Stellar Remote skills back to back.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(line);
    }
}
