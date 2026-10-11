package dev.ss05.halley.world;

/**
 * The SS numbers this addon's two skills carry on the Stellar Remote. The Shooting Star's own skills always keep
 * theirs: Halley and Luna take the next two free numbers after the highest of them, worked out when compat/StarBridge
 * attaches. The Shooting Star 1.4.0 runs from SS-01 to SS-05 (Tesseract), so Halley is SS-06 and Luna SS-07, and if a
 * later version adds an SS-06 of its own, they move up again by themselves.
 */
public final class SkillNumbers {
    /** Halley's number; Luna's is the one after it. Until StarBridge has looked, The Shooting Star 1.4.0's. */
    private static volatile int halley = 6;

    private SkillNumbers() {
    }

    /** From compat/StarBridge: the highest SS number among The Shooting Star's own skills. */
    public static void after(int highestBuiltIn) {
        halley = highestBuiltIn + 1;
    }

    /** "SS-06" (with The Shooting Star 1.4.0). */
    public static String halley() {
        return code(halley);
    }

    /** "SS-07" (with The Shooting Star 1.4.0). */
    public static String luna() {
        return code(halley + 1);
    }

    private static String code(int number) {
        return String.format("SS-%02d", number);
    }
}
