package dev.ss05.halley;

import net.minecraft.world.phys.Vec3;

/**
 * The sizes of one Luna moonfall, fixed when it is called down. Like {@link HalleyParams}, the server sends them to
 * every client inside the strike's effect packet (as a short int array), so the film always matches the ground the
 * server shoves up and the moon it leaves. {@code sky} is the unit vector toward where the moon stood in the caster's
 * sky when it was called (it falls from there).
 */
public record MoonParams(int moonRadius, int craterRadius, int rimHeight, int buriedPercent, double blastReach,
                         int groundY, Vec3 sky, int flags, float scale) {
    public static final int FLAG_CARVE = 1;
    public static final int FLAG_CORE = 2;

    /** Marks the int array as Luna's (Halley's uses 0x055A1E), followed by its layout version. */
    private static final int MAGIC = 0x0600A1;
    /**
     * 2: the rim height and the buried share (1 had a crater depth and a sink share in those places). 3: the scale (the
     * remote's power slider) at the end.
     */
    private static final int VERSION = 3;
    private static final int LENGTH = 13;

    /** The config's sizes, as they are (power 1). */
    public static MoonParams of(MoonConfig.Tuning tuning, int groundY, Vec3 sky) {
        return of(tuning, groundY, sky, 1.0F);
    }

    /**
     * The config's sizes times {@code scale}, the remote's power slider (0.1 to 10): the moon, the crater (so the blast,
     * a multiple of it) and the rim. The buried share stays, so the moon sits the same way in a crater as much bigger.
     */
    public static MoonParams of(MoonConfig.Tuning tuning, int groundY, Vec3 sky, float scale) {
        int flags = (tuning.carveTerrain() ? FLAG_CARVE : 0) | (tuning.moltenCore() ? FLAG_CORE : 0);
        float s = clampScale(scale);
        return new MoonParams(Math.max(1, Math.round(tuning.moonRadius() * s)), Math.max(2, Math.round(tuning.craterRadius() * s)),
            Math.round(tuning.rimHeight() * s), tuning.buriedPercent(), tuning.blastReach(), groundY, sky.normalize(), flags, s);
    }

    private static float clampScale(float scale) {
        return Float.isNaN(scale) ? 1.0F : Math.max(CastTuning.MIN_POWER, Math.min(CastTuning.MAX_POWER, scale));
    }

    public boolean carve() {
        return (this.flags & FLAG_CARVE) != 0;
    }

    /** Whether the single block of molten moon cheese is left at the moon's heart. */
    public boolean core() {
        return (this.flags & FLAG_CORE) != 0;
    }

    /** How much of the moon's height ends up below the ground, 0..0.9. */
    public double buried() {
        return this.buriedPercent / 100.0;
    }

    public int[] encode() {
        return new int[]{MAGIC, VERSION, this.moonRadius, this.craterRadius, this.rimHeight, this.buriedPercent,
            (int) Math.round(this.blastReach * 100.0), this.groundY, (int) Math.round(this.sky.x * 10000.0),
            (int) Math.round(this.sky.y * 10000.0), (int) Math.round(this.sky.z * 10000.0), this.flags,
            Math.round(this.scale * 1000.0F)};
    }

    /** Whether an effect packet's int array is one of these (and not Halley's). */
    public static boolean isMoon(int[] data) {
        return data != null && data.length >= 2 && data[0] == MAGIC;
    }

    /** Reads what {@link #encode()} wrote; anything unexpected falls back to the default sizes. */
    public static MoonParams decode(int[] data, int fallbackGround, Vec3 fallbackSky) {
        if (data == null || data.length < LENGTH || data[0] != MAGIC || data[1] != VERSION) {
            return of(MoonConfig.Tuning.DEFAULTS, fallbackGround, fallbackSky);
        }
        Vec3 sky = new Vec3(data[8] / 10000.0, data[9] / 10000.0, data[10] / 10000.0);
        if (sky.lengthSqr() < 0.25) {
            sky = fallbackSky;
        }
        // the config's own ranges, from a tenth of their smallest to ten times their largest (the power slider)
        return new MoonParams(clamp(data[2], 1, MoonConfig.MAX_MOON_RADIUS * 10),
            clamp(data[3], 2, MoonConfig.MAX_CRATER_RADIUS * 10),
            clamp(data[4], 0, MoonConfig.MAX_RIM_HEIGHT * 10), clamp(data[5], 0, 90),
            Math.max(1.0, data[6] / 100.0), data[7], sky.normalize(), data[11], clampScale(data[12] / 1000.0F));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
