package dev.ss05.halley;

/**
 * The sizes of one strike, fixed when it is fired. The server sends them to every client inside the strike's effect
 * packet (as a short int array), so the film always matches the crater the server actually cuts.
 */
public record HalleyParams(int trenchLength, int trenchWidth, int trenchDepth, int craterRadius, int craterDepth,
                           int touchdownGround, double blastReach, int flags, float scale) {
    public static final int FLAG_CARVE = 1;
    public static final int FLAG_HEART = 2;
    public static final int FLAG_RAYS = 4;

    /** Marks the int array as ours, followed by its layout version. Bump VERSION if you change the layout. */
    private static final int MAGIC = 0x055A1E;
    /** 2: the scale (the remote's power slider) at the end. */
    private static final int VERSION = 2;
    private static final int LENGTH = 11;

    /** The config's sizes, as they are (power 1). */
    public static HalleyParams of(HalleyConfig.Tuning tuning, int touchdownGround) {
        return of(tuning, touchdownGround, 1.0F);
    }

    /**
     * The config's sizes times {@code scale}, the remote's power slider (0.1 to 10): the trench, the crater and (as a
     * multiple of the crater) the blast. The comet and the film scale with it too, so the whole strike is that much
     * bigger and takes the same time.
     */
    public static HalleyParams of(HalleyConfig.Tuning tuning, int touchdownGround, float scale) {
        int flags = (tuning.carveTerrain() ? FLAG_CARVE : 0)
            | (tuning.leaveHeart() ? FLAG_HEART : 0)
            | (tuning.frostRays() ? FLAG_RAYS : 0);
        float s = clampScale(scale);
        return new HalleyParams(scaled(tuning.trenchLength(), s, 3), scaled(tuning.trenchWidth(), s, 2),
            scaled(tuning.trenchDepth(), s, 1), scaled(tuning.craterRadius(), s, 3), scaled(tuning.craterDepth(), s, 2),
            touchdownGround, tuning.blastReach(), flags, s);
    }

    private static int scaled(int size, float scale, int min) {
        return Math.max(min, Math.round(size * scale));
    }

    private static float clampScale(float scale) {
        return Float.isNaN(scale) ? 1.0F : Math.max(CastTuning.MIN_POWER, Math.min(CastTuning.MAX_POWER, scale));
    }

    public boolean carve() {
        return (this.flags & FLAG_CARVE) != 0;
    }

    public boolean heart() {
        return (this.flags & FLAG_HEART) != 0;
    }

    public boolean rays() {
        return (this.flags & FLAG_RAYS) != 0;
    }

    public int[] encode() {
        return new int[]{MAGIC, VERSION, this.trenchLength, this.trenchWidth, this.trenchDepth, this.craterRadius,
            this.craterDepth, this.touchdownGround, (int) Math.round(this.blastReach * 100.0), this.flags,
            Math.round(this.scale * 1000.0F)};
    }

    /** Reads what {@link #encode()} wrote; anything unexpected falls back to the default sizes. */
    public static HalleyParams decode(int[] data, int fallbackGround) {
        if (data == null || data.length < LENGTH || data[0] != MAGIC || data[1] != VERSION) {
            return of(HalleyConfig.Tuning.DEFAULTS, fallbackGround);
        }
        // the config's own ranges, from a tenth of their smallest to ten times their largest (the power slider)
        return new HalleyParams(clamp(data[2], 3, 6400), clamp(data[3], 2, 640), clamp(data[4], 1, 640),
            clamp(data[5], 3, 1280), clamp(data[6], 2, 960), data[7], Math.max(1.0, data[8] / 100.0), data[9],
            clampScale(data[10] / 1000.0F));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
