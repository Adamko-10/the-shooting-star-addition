package dev.ss05.halley;

import net.minecraft.world.phys.Vec3;

/**
 * The sizes of one Luna moonfall, fixed when it is called down. Like {@link HalleyParams}, the server sends them to
 * every client inside the strike's effect packet (as a short int array), so the film always matches the ground the
 * server shoves up and the moon it leaves. {@code sky} is the unit vector toward where the moon stood in the caster's
 * sky when it was called (it falls from there).
 */
public record MoonParams(int moonRadius, int craterRadius, int rimHeight, int buriedPercent, double blastReach,
                         int groundY, Vec3 sky, int flags) {
    public static final int FLAG_CARVE = 1;
    public static final int FLAG_CORE = 2;

    /** Marks the int array as Luna's (Halley's uses 0x055A1E), followed by its layout version. */
    private static final int MAGIC = 0x0600A1;
    /** 2: the rim height and the buried share (1 had a crater depth and a sink share in those places). */
    private static final int VERSION = 2;
    private static final int LENGTH = 12;

    public static MoonParams of(MoonConfig.Tuning tuning, int groundY, Vec3 sky) {
        int flags = (tuning.carveTerrain() ? FLAG_CARVE : 0) | (tuning.moltenCore() ? FLAG_CORE : 0);
        return new MoonParams(tuning.moonRadius(), tuning.craterRadius(), tuning.rimHeight(), tuning.buriedPercent(),
            tuning.blastReach(), groundY, sky.normalize(), flags);
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
            (int) Math.round(this.sky.y * 10000.0), (int) Math.round(this.sky.z * 10000.0), this.flags};
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
        return new MoonParams(clamp(data[2], MoonConfig.MIN_MOON_RADIUS, MoonConfig.MAX_MOON_RADIUS),
            clamp(data[3], MoonConfig.MIN_CRATER_RADIUS, MoonConfig.MAX_CRATER_RADIUS),
            clamp(data[4], 0, MoonConfig.MAX_RIM_HEIGHT), clamp(data[5], 0, 90),
            Math.max(1.0, data[6] / 100.0), data[7], sky.normalize(), data[11]);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
