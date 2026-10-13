package dev.ss05.halley.world;

/**
 * The codenames this addon's skills carry on the Stellar Remote and in their HUDs. The Shooting Star numbers its own
 * skills SS-01, SS-02 and so on, and every release may add one; this addon's skills have a series of their own, SA
 * (Shooting star Addition), so they can never clash with a built-in skill, whatever The Shooting Star adds. A new
 * skill of this addon takes the next SA number.
 */
public final class Codenames {
    /** Halley, the comet. */
    public static final String HALLEY = "SA-01";
    /** Luna, the moonfall. */
    public static final String LUNA = "SA-02";

    private Codenames() {
    }
}
