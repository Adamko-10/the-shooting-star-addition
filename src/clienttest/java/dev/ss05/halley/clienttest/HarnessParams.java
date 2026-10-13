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

    /**
     * Views to take one more shot from each once the strike is over and every changed block has been drawn again
     * (a slow client takes a long while to redraw a few hundred thousand changed blocks), e.g. {@code aerial,low}.
     */
    static final String[] FINAL_VIEWS = System.getProperty(PREFIX + ".final", "").isBlank() ? new String[0]
        : System.getProperty(PREFIX + ".final").split(",");

    /** "flat" (the default: quick to make and to draw) or "normal" (real land: hills, water, trees). */
    static final String WORLD = System.getProperty(PREFIX + ".world", "flat").toLowerCase(Locale.ROOT);

    /** How far round the mark (in blocks) the land must have arrived before the cast (0 = don't wait). */
    static final double LAND = Double.parseDouble(System.getProperty(PREFIX + ".land", "112"));

    /** Whether the HUD stays on in the views filmed from a fixed spot (it is hidden there by default). */
    static final boolean HUD = Boolean.parseBoolean(System.getProperty(PREFIX + ".hud", "false"));

    /** {@code dx,dy,dz,lx,ly,lz}: a fixed camera at the mark plus (dx, dy, dz) looking at the mark plus (lx, ly, lz). */
    static final String CAM = System.getProperty(PREFIX + ".cam", "");

    /**
     * Where the fixed camera stands, relative to the mark (see {@link #CAM}), or null to film through the caster's own
     * eyes ("first", "third", "up").
     */
    static double[] camera() {
        return camera(VIEW);
    }

    /** The camera of a named view (see {@link #camera()}). */
    static double[] camera(String view) {
        if (!CAM.isBlank() && view.equals(VIEW)) {
            String[] parts = CAM.split(",");
            double[] out = new double[6];
            for (int i = 0; i < 6; i++) {
                out[i] = Double.parseDouble(parts[i].trim());
            }
            return out;
        }
        return switch (view.trim().toLowerCase(Locale.ROOT)) {
            // off to the side and up a little, the sky the moon falls out of in frame too
            case "wide" -> new double[] {0, 40, 180, 0, 60, 0};
            // high up and off at an angle, looking down on the crater
            case "aerial" -> new double[] {-150, 120, 170, 0, 0, 0};
            // standing on the land, looking at what it leaves from not far off
            case "low" -> new double[] {-70, 8, 200, 0, 25, 0};
            // far off and low, the whole fall in frame
            case "far" -> new double[] {0, 50, 520, 0, 160, 0};
            // close in on the rim from the side, low
            case "rim" -> new double[] {-40, 30, 115, 0, 5, 0};
            // straight down from high above
            case "top" -> new double[] {0.5, 230, 0.6, 0, 0, 0};
            default -> null;
        };
    }

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
            + " view=" + VIEW + (CAM.isBlank() ? "" : " cam=" + CAM) + " world=" + WORLD + " time=" + TIME + " distance=" + DISTANCE + " film=" + FILM;
    }
}
