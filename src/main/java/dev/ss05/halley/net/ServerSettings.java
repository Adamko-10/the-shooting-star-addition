package dev.ss05.halley.net;

/**
 * What the server this game is connected to has set (client side; plain fields, so nothing client-only is loaded on a
 * dedicated server): whether players may switch the Stellar Remote's cooldowns off ({@code no_cooldown}) and whether
 * survival players may too ({@code no_cooldown_survival}). Unknown (a server without the addon) until it says.
 */
public final class ServerSettings {
    private static volatile boolean known;
    private static volatile boolean noCooldown;
    private static volatile boolean survival;
    /** Set by the client: what to do once the server has said (tell it the player's own switch). */
    public static volatile Runnable onReceived = () -> { };

    private ServerSettings() {
    }

    public static void received(boolean noCooldown, boolean survival) {
        ServerSettings.noCooldown = noCooldown;
        ServerSettings.survival = survival;
        ServerSettings.known = true;
        onReceived.run();
    }

    public static void forget() {
        known = false;
        noCooldown = false;
        survival = false;
    }

    /** Whether the server has said (it runs the addon). */
    public static boolean known() {
        return known;
    }

    public static boolean noCooldown() {
        return noCooldown;
    }

    public static boolean survival() {
        return survival;
    }
}
