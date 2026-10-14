package dev.ss05.halley.world;

/**
 * How Halley is named and shown on the Stellar Remote. The menu also reads its description and key name from the
 * language file (assets/shooting_star_addition/lang/en_us.json), so wording changes go there.
 */
public final class HalleyInfo {
    /** Also the icon's file name: assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/halley.png */
    public static final String ID = "halley";
    /** The platform all the remote's skills belong to. */
    public static final String TIER = "The Shooting Star";
    /** "SA-01 · Halley" (see {@link Codenames}). */
    public static String title() {
        return Codenames.HALLEY + " · Halley";
    }

    /** Ice-cyan: the other three are red (SS-01), ember (SS-03) and violet (SS-04). */
    public static final int COLOR = 0x6FE8FF;
    /**
     * GLFW key code 71 = G (Minecraft 26.3 puts its own Friends key on O, and The Shooting Star has H, J, U, I, K and Y). (A plain number so the dedicated server never
     * needs the client's GLFW classes.) Players can rebind it in Controls or from the remote's menu.
     */
    public static final int DEFAULT_KEY = 71;

    private HalleyInfo() {
    }
}
