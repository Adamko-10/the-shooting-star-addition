package dev.ss05.halley.world;

/**
 * How SS-05 is named and shown on the Stellar Remote. The menu also reads its description and key name from the
 * language file (assets/shooting_star_addition/lang/en_us.json), so wording changes go there.
 */
public final class HalleyInfo {
    /** Also the icon's file name: assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/halley.png */
    public static final String ID = "halley";
    /** The platform all the remote's skills belong to. */
    public static final String TIER = "The Shooting Star";
    public static final String TITLE = "SS-05 · Halley";
    /** Ice-cyan: the other three are red (SS-01), ember (SS-03) and violet (SS-04). */
    public static final int COLOR = 0x6FE8FF;
    /**
     * GLFW key code 79 = O, next to the other skills' U, I and K. (A plain number so the dedicated server never
     * needs the client's GLFW classes.) Players can rebind it in Controls or from the remote's menu.
     */
    public static final int DEFAULT_KEY = 79;

    private HalleyInfo() {
    }
}
