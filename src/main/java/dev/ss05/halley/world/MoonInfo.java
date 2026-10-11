package dev.ss05.halley.world;

/**
 * How Luna is named and shown on the Stellar Remote. The menu also reads its description and key name from the
 * language file (assets/shooting_star_addition/lang/en_us.json), so wording changes go there.
 */
public final class MoonInfo {
    /** Also the icon's file name: assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/luna.png */
    public static final String ID = "luna";
    /** The platform all the remote's skills belong to. */
    public static final String TIER = "The Shooting Star";
    /** "SS-07 · Luna": the number follows The Shooting Star's own skills (see {@link SkillNumbers}). */
    public static String title() {
        return SkillNumbers.luna() + " · Luna";
    }

    /** What the remote announces when it fires: shown big across the screen, and in the action bar to others. */
    public static final String ALERT = "EARTH SYSTEM SHUT DOWN";
    /** Moon-cheese gold (SS-05 is ice-cyan). */
    public static final int COLOR = 0xFFC93C;
    /**
     * GLFW key code 89 = Y, next to the remote's U and I; vanilla leaves it free, and The Shooting Star uses J (skip /
     * toggle the cutscene) and H (the menu), so not those. (A plain number so the dedicated server never needs the
     * client's GLFW classes.) Players can rebind it in Controls or from the remote's menu.
     */
    public static final int DEFAULT_KEY = 78;

    private MoonInfo() {
    }
}
