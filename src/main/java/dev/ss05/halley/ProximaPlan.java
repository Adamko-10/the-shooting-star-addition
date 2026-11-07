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
    /**
     * The film's way out to Proxima Centauri and back (the approved previz, 2026-11-07; lengthened the same night: a
     * zoom out from Earth into a warp instead of a cut, no infinity loops, the lens absorbing for 3 s): the camera pulls
     * straight up off the caster's shoulder through the clouds ({@link #ASCENT}) and cuts to Earth from orbit
     * ({@link #ORBIT}), pulling back faster and faster into a warp ({@link #WARP}) that lands at Proxima Centauri in its
     * Dyson sphere ({@link #SPACE}); the iris opening ({@link #IRIS}); the lens gathering ({@link #LENS}), drawing the
     * star's power in ({@link #POWER}) and firing ({@link #BEAM}); Earth and the gate catching the shot and refocusing it
     * onto the caster's spot ({@link #EARTH}, open at {@link #GATE}). All of that is the caster's film only
     * (client/proxima/ProximaCosmos draws it as a scene of its own).
     */
    public static final int ASCENT = 32;
    public static final int ORBIT = 59;
    public static final int WARP = 100;
    public static final int SPACE = 124;
    public static final int IRIS = 172;
    public static final int LENS = 216;
    public static final int POWER = 230;
    public static final int BEAM = 290;
    public static final int EARTH = 310;
    public static final int GATE = 317;
    /** Phase 1, the flash burn: the flare's light arrives first, the sky goes white-red, exposed things burn. */
    public static final int FLASH = 350;
    /** Phase 2, the coronal front sweeping across the sky (looks only: no damage). */
    public static final int FRONT = 386;
    /** Near-silence: everyone thinks it's over. */
    public static final int HUSH = 498;
    /** Phase 3, ground zero: the bowl, the scorch, the blast. */
    public static final int IMPACT = 510;
    /** The effects' end (the aftermath fades out by then). */
    public static final int DURATION = IMPACT + 330;
    /** Ground zero's shock wave runs out over the land, burning and throwing as it goes: at the scorch's edge this long after the impact. */
    public static final int WAVE_TICKS = 130;
    /** The coronal front: a shell of plasma this big (radius, blocks), its nose this far from the mark when it appears. */
    public static final double FRONT_RADIUS = 2600.0;
    public static final double FRONT_START = 2600.0;
    /** How steeply the front comes down out of the sky (radians above the horizon). */
    private static final double FRONT_DIVE = Math.toRadians(22.0);

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

    /** How far out ground zero's shock wave has run at {@code t}: fast at first, slowing as it nears the scorch's edge. */
    public double waveRadius(double t) {
        if (t < IMPACT) {
            return 0.0;
        }
        double reach = this.params.scorchRadius() * 1.12;
        double k = Math.min(1.0, (t - IMPACT) / WAVE_TICKS);
        return reach * (1.0 - (1.0 - k) * (1.0 - k));
    }

    public double waveReach() {
        return this.params.scorchRadius() * 1.12;
    }

    /** The way the coronal front travels: down out of the sky toward the mark, from a side of the sky set by the strike. */
    public Vec3 frontDirection() {
        double a = (this.seed >>> 32 & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        return new Vec3(Math.cos(a) * Math.cos(FRONT_DIVE), -Math.sin(FRONT_DIVE), Math.sin(a) * Math.cos(FRONT_DIVE));
    }

    /** How far the front's nose is from the mark at {@code t}: {@link #FRONT_START} at {@link #FRONT}, closing faster and faster, 0 at the impact. */
    public double frontDistance(double t) {
        double k = Mth.clamp((t - FRONT) / (double) (IMPACT - FRONT), 0.0, 1.0);
        return FRONT_START * (1.0 - k) * (1.0 + 0.4 * k);
    }

    /** The centre of the front's shell at {@code t} (its nose is {@link #FRONT_RADIUS} from it, toward the mark). */
    public Vec3 frontCentre(double t) {
        return this.target.subtract(this.frontDirection().scale(FRONT_RADIUS + this.frontDistance(t)));
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
