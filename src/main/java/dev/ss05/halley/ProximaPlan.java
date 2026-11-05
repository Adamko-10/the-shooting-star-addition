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
     * The film's space intro (2026-11-05: the timeline moved 60 ticks later for it, so the impact lands with Halley's and
     * Luna's at 330): the camera rises into a sky gone to space; the Dyson sphere's iris opens on the star; two plasma
     * loops twist into an infinity sign, flare and collapse; the panels fold into a lens and focus the flare into a beam;
     * a gate in the sky catches it. Everything is seen by anyone looking at that part of the sky, not only in the film.
     */
    public static final int SPACE = 30;
    public static final int IRIS = 60;
    public static final int LOOPS = 100;
    public static final int COLLAPSE = 118;
    public static final int LENS = 125;
    public static final int BEAM = 135;
    public static final int GATE = 145;
    /** Phase 1, the flash burn: the flare's light arrives first, the sky goes white-red, exposed things burn. */
    public static final int FLASH = 150;
    /** Phase 2, the coronal front sweeping across the sky (looks only: no damage). */
    public static final int FRONT = 160;
    /** Near-silence: everyone thinks it's over. */
    public static final int HUSH = 320;
    /** Phase 3, ground zero: the bowl, the scorch, the blast. */
    public static final int IMPACT = 330;
    /** The effects' end (the aftermath fades out by then). */
    public static final int DURATION = 660;
    /** The star in the intro: its radius and how far off it is drawn (far parts are pulled in, so only the angle matters). */
    public static final double STAR_RADIUS = 1400.0;
    public static final double STAR_DISTANCE = 20000.0;
    /** Ground zero's shock wave runs out over the land, burning and throwing as it goes: at the scorch's edge this long after the impact. */
    public static final int WAVE_TICKS = 70;
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

    /** Where the star hangs in the intro: high in the sky on the side the front will come from. */
    public Vec3 starDirection() {
        Vec3 front = this.frontDirection();
        Vec3 from = new Vec3(-front.x, 0.0, -front.z).normalize();
        double up = Math.toRadians(50.0);
        return from.scale(Math.cos(up)).add(0.0, Math.sin(up), 0.0);
    }

    public Vec3 starCentre() {
        return this.target.add(this.starDirection().scale(STAR_DISTANCE));
    }

    /** The gate that catches the beam and spreads it into the front: where the front's nose starts. */
    public Vec3 gate() {
        return this.target.subtract(this.frontDirection().scale(FRONT_START));
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
