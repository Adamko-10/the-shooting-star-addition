package dev.ss05.halley;

/**
 * The sizes of one strike, fixed when it is fired. The server sends them to every client inside the strike's effect
 * packet (as a short int array), so the film always matches the crater the server actually cuts.
 */
public record HalleyParams(int trenchLength, int trenchWidth, int trenchDepth, int craterRadius, int craterDepth,
                           int touchdownGround, double blastReach, int flags) {
    public static final int FLAG_CARVE = 1;
    public static final int FLAG_HEART = 2;
    public static final int FLAG_RAYS = 4;

    /** Marks the int array as ours, followed by its layout version. Bump VERSION if you change the layout. */
    private static final int MAGIC = 0x055A1E;
    private static final int VERSION = 1;
    private static final int LENGTH = 10;

    public static HalleyParams of(HalleyConfig.Tuning tuning, int touchdownGround) {
        int flags = (tuning.carveTerrain() ? FLAG_CARVE : 0)
            | (tuning.leaveHeart() ? FLAG_HEART : 0)
            | (tuning.frostRays() ? FLAG_RAYS : 0);
        return new HalleyParams(tuning.trenchLength(), tuning.trenchWidth(), tuning.trenchDepth(), tuning.craterRadius(),
            tuning.craterDepth(), touchdownGround, tuning.blastReach(), flags);
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
            this.craterDepth, this.touchdownGround, (int) Math.round(this.blastReach * 100.0), this.flags};
    }

    /** Reads what {@link #encode()} wrote; anything unexpected falls back to the default sizes. */
    public static HalleyParams decode(int[] data, int fallbackGround) {
        if (data == null || data.length < LENGTH || data[0] != MAGIC || data[1] != VERSION) {
            return of(HalleyConfig.Tuning.DEFAULTS, fallbackGround);
        }
        return new HalleyParams(clamp(data[2], 32, 640), clamp(data[3], 8, 64), clamp(data[4], 2, 64),
            clamp(data[5], 12, 128), clamp(data[6], 4, 96), data[7], Math.max(1.0, data[8] / 100.0), data[9]);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
