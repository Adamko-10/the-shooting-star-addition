package dev.ss05.halley;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * SA-04 Proxima's timeline (ticks at speed 1, 20 a second) and the shape of its scorch, shared by the server (what
 * happens to the world) and every client (what is seen and heard), so both follow the same strike. Change a constant
 * here and everything moves with it. See docs/SS-07-PROXIMA.md.
 */
public final class ProximaPlan {
    /** The remote's button (as for the other skills). */
    public static final int PRESS = 7;
    /** Phase 1, the flash burn: the flare's light arrives first, the sky goes white-red, exposed things burn. */
    public static final int FLASH = 90;
    /** Phase 2, the coronal front sweeping across the sky (looks only: no damage). */
    public static final int FRONT = 100;
    /** Near-silence: everyone thinks it's over. */
    public static final int HUSH = 260;
    /** Phase 3, ground zero: the bowl, the scorch, the blast. */
    public static final int IMPACT = 270;
    /** The effects' end (the aftermath fades out by then). */
    public static final int DURATION = 600;

    public final Vec3 target;
    public final long seed;
    public final ProximaParams params;
    /** How far the scorch's edges wander in and out round the circle (so it isn't a perfect disc). */
    private final float wobbleA;
    private final float wobbleB;

    public ProximaPlan(Vec3 target, long seed, ProximaParams params) {
        this.target = target;
        this.seed = seed;
        this.params = params;
        this.wobbleA = (float) ((seed & 0xFFFF) / 65535.0 * Math.PI * 2.0);
        this.wobbleB = (float) ((seed >>> 16 & 0xFFFF) / 65535.0 * Math.PI * 2.0);
    }

    public double horizontal(double x, double z) {
        return Math.hypot(x - this.target.x, z - this.target.z);
    }

    /** The scorch's and the char's edges are ragged: this column's edge radii are these times this. */
    public double edge(double x, double z) {
        double a = Math.atan2(z - this.target.z, x - this.target.x);
        return 1.0 + 0.07 * Math.sin(3.0 * a + this.wobbleA) + 0.04 * Math.sin(7.0 * a + this.wobbleB);
    }

    /** How deep the bowl is under the ground at {@code r} blocks from the mark: {@link ProximaParams#depth} in the middle, 0 at its rim. */
    public double bowlDepth(double r) {
        double k = r / this.params.bowlRadius();
        return k >= 1.0 ? 0.0 : this.params.depth() * (1.0 - k * k);
    }

    /** A steady random number in [0, 1) for a block (materials, ragged edges). */
    public float noise(int x, int y, int z) {
        long h = Mth.getSeed(x, y, z) ^ this.seed;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
