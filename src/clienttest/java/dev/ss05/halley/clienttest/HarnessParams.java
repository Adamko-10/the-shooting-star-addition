package dev.ss05.halley.clienttest;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Every tunable of the harness, read once from system properties (so the main session can rerun with different
 * values without recompiling - just pass more {@code -Dss05.clienttest.xxx=...} to {@code ./gradlew runClientTest}).
 */
final class HarnessParams {
    static final String PREFIX = "ss05.clienttest";

    /** Whether the harness should do anything at all. */
    static final boolean ACTIVE = Boolean.getBoolean(PREFIX);

    /** "halley" or "moon". */
    static final String SKILL = System.getProperty(PREFIX + ".skill", "halley").toLowerCase(Locale.ROOT);
    static final boolean MOON = SKILL.equals("moon");

    /** Tick offsets after the cast to screenshot at, ascending. */
    static final long[] SHOTS = parseLongs(System.getProperty(PREFIX + ".shots", "0,100,250,400,550"));

    /** "first", "third", "wide" (off to the side, high up, looking at the mark) or "up" (stay put, look up along
     * the incoming line so a falling moon/comet is centred). */
    static final String VIEW = System.getProperty(PREFIX + ".view", "first").toLowerCase(Locale.ROOT);

    /** The world's time-of-day at cast (0..24000), held fixed (doDaylightCycle false). */
    static final long TIME = Long.parseLong(System.getProperty(PREFIX + ".time", "18000"));

    /** How far in front of the player (along the ground) the mark is placed. */
    static final double DISTANCE = Double.parseDouble(System.getProperty(PREFIX + ".distance", "120"));

    /** Whether the caster's own cutscene is allowed to take over the camera. */
    static final boolean FILM = Boolean.parseBoolean(System.getProperty(PREFIX + ".film", "false"));

    private HarnessParams() {
    }

    private static long[] parseLongs(String csv) {
        String trimmed = csv.trim();
        if (trimmed.isEmpty()) {
            return new long[0];
        }
        String[] parts = trimmed.split(",");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Long.parseLong(parts[i].trim());
        }
        Arrays.sort(out);
        return out;
    }

    static String describe() {
        return "skill=" + SKILL + " shots=" + Arrays.stream(SHOTS).mapToObj(String::valueOf).collect(Collectors.joining(","))
            + " view=" + VIEW + " time=" + TIME + " distance=" + DISTANCE + " film=" + FILM;
    }
}
