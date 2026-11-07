package dev.ss05.halley.world;

/**
 * How Proxima is named and shown on the Stellar Remote. The menu also reads its description and key name from the
 * language file (assets/shooting_star_addition/lang/en_us.json), so wording changes go there.
 */
public final class ProximaInfo {
    /** Also the icon's file name: assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/proxima.png */
    public static final String ID = "proxima";
    /** The platform all the remote's skills belong to. */
    public static final String TIER = "The Shooting Star";
    /** Flare crimson (Halley is ice-cyan, Luna moon-cheese gold). */
    public static final int COLOR = 0xE8294C;
    /**
     * GLFW key code 90 = Z: free in vanilla and The Shooting Star, not one of the plates' keys in the remote's menu, and
     * not P (The Shooting Star - Stellar Quartet's). Players can rebind it in Controls or from the remote's menu.
     */
    public static final int DEFAULT_KEY = 90;

    /** "SA-04 · Proxima" (see {@link Codenames}). */
    public static String title() {
        return Codenames.PROXIMA + " · Proxima";
    }

    private ProximaInfo() {
    }
}
