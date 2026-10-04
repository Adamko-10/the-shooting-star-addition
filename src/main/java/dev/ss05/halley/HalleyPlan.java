package dev.ss05.halley;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The timeline and the geometry of one SS-05 strike.
 *
 * <p>Both sides build the same plan from the same numbers (the caster's position, the mark, the seed and the
 * {@link HalleyParams}), so the server cuts exactly the trench the client films. Pure maths: no world access.
 *
 * <p>Picture it from the caster: the mark is in front of you. The comet comes in low over the horizon beyond the mark
 * (a little off to one side), touches the ground {@code trenchLength} blocks past the mark, then ploughs back along
 * the line toward you and detonates on the mark.
 */
public final class HalleyPlan {
    // ---- Timeline, in ticks since the button was pressed. --------------------------------------------------------
    // Changing these also changes when sounds play; the countdown sound is cut to end exactly at TOUCHDOWN.
    /** The Stellar Remote's safety cover flips open. */
    public static final int ARM = 0;
    /** The button goes down. */
    public static final int PRESS = 7;
    /** The mark lands on the crosshair and the approach corridor is projected onto the land. */
    public static final int MARK = 9;
    /** The countdown starts. */
    public static final int COUNTDOWN = 29;
    /** The comet becomes visible, low over the horizon. */
    public static final int SIGHT = 30;
    /** A creative-mode caster standing in the crater is lifted clear. */
    public static final int EVAC = 31;
    /** It hits the atmosphere: plasma sheath, sonic boom. */
    public static final int ENTRY = 250;
    /** It touches the ground at the far end of the corridor. */
    public static final int TOUCHDOWN = 300;
    /** How fast the nucleus ploughs along the trench, in blocks per tick. */
    public static final double PLOUGH_SPEED = 8.0;
    /** How long the spell keeps running after the detonation (the remote's other skills run ~200 ticks past impact). */
    public static final int AFTERMATH = 196;

    // ---- The approach. ---------------------------------------------------------------------------------------------
    /** How far along its path the comet is when it is first sighted, in blocks. */
    public static final double START_DISTANCE = 4600.0;
    /** How steeply it comes down, in degrees above the horizon. */
    public static final double DESCENT_DEGREES = 13.5;
    /** How far off the line of the trench it comes in from, in degrees (left or right, picked by the seed). */
    public static final double SIDE_DEGREES = 26.0;
    /** Bigger = the comet hangs far away longer and then rushes in at the end. */
    public static final double APPROACH_EASE = 2.35;

    public static final int UNTOUCHED = Integer.MIN_VALUE;

    public final Vec3 origin;
    public final Vec3 target;
    public final long seed;
    public final HalleyParams params;
    /** Unit vector, horizontal: from the caster toward the mark (and on, toward the touchdown point). */
    public final Vec3 dir;
    /** Unit vector, horizontal, perpendicular to {@link #dir}. */
    public final Vec3 side;
    /** Where the comet first touches the ground. */
    public final Vec3 touchdown;
    /** Unit vector pointing from the touchdown point back up along the comet's path into the sky. */
    public final Vec3 incoming;
    /** +1 or -1: which side of the trench the comet comes in from. */
    public final int sideSign;
    public final int impact;
    public final int duration;
    private final double[] phase = new double[6];

    public HalleyPlan(Vec3 origin, Vec3 target, float casterYaw, long seed, HalleyParams params) {
        this.origin = origin;
        this.target = target;
        this.seed = seed;
        this.params = params;

        Vec3 flat = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
        if (flat.lengthSqr() < 1.0) {
            double yaw = Math.toRadians(casterYaw);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        this.dir = flat.normalize();
        this.side = new Vec3(-this.dir.z, 0.0, this.dir.x);
        this.touchdown = new Vec3(target.x + this.dir.x * params.trenchLength(), params.touchdownGround(),
            target.z + this.dir.z * params.trenchLength());
        this.sideSign = ((seed >>> 7) & 1L) == 0L ? 1 : -1;

        double sideRad = Math.toRadians(SIDE_DEGREES) * this.sideSign;
        Vec3 across = this.dir.scale(Math.cos(sideRad)).add(this.side.scale(Math.sin(sideRad)));
        double descent = Math.toRadians(DESCENT_DEGREES);
        this.incoming = new Vec3(across.x * Math.cos(descent), Math.sin(descent), across.z * Math.cos(descent)).normalize();

        this.impact = TOUCHDOWN + ploughTicks(params.trenchLength());
        this.duration = this.impact + AFTERMATH;
        for (int i = 0; i < this.phase.length; i++) {
            this.phase[i] = Math.floorMod(seed >> (i * 9), 6283L) / 1000.0;
        }
    }

    public static int ploughTicks(int trenchLength) {
        return Math.max(1, (int) Math.ceil(trenchLength / PLOUGH_SPEED));
    }

    /** Duration of a strike with the given trench length (used for the skill's displayed duration). */
    public static int durationFor(int trenchLength) {
        return TOUCHDOWN + ploughTicks(trenchLength) + AFTERMATH;
    }

    // ---- Where the comet is. -------------------------------------------------------------------------------------

    /** 0..1 progress of the approach, from sighting to touchdown. */
    public static double approach(double t) {
        return Mth.clamp((t - SIGHT) / (double) (TOUCHDOWN - SIGHT), 0.0, 1.0);
    }

    /** Distance still to fly before touchdown, along the path, at time t (ticks). */
    public static double distanceToGo(double t) {
        return START_DISTANCE * (1.0 - Math.pow(approach(t), APPROACH_EASE));
    }

    /** How far the nucleus has ploughed from the touchdown point toward the mark (0..trenchLength). */
    public double ploughed(double t) {
        return Mth.clamp((t - TOUCHDOWN) * PLOUGH_SPEED, 0.0, this.params.trenchLength());
    }

    /** Distance from the mark toward the touchdown point of the ploughing nucleus at time t. */
    public double frontAlong(double t) {
        return this.params.trenchLength() - this.ploughed(t);
    }

    /** The nucleus' position at time t (ticks since the button was pressed). */
    public Vec3 comet(double t) {
        if (t <= TOUCHDOWN) {
            return this.touchdown.add(this.incoming.scale(distanceToGo(t)));
        }
        double along = this.frontAlong(t);
        Vec3 ground = this.groundPoint(along);
        double sunk = this.params.carve() ? this.trenchDepth(along) * 0.35 : 0.0;
        return ground.add(0.0, 3.0 - sunk, 0.0);
    }

    /** Unit direction the nucleus is moving in at time t. */
    public Vec3 heading(double t) {
        return t <= TOUCHDOWN ? this.incoming.scale(-1.0) : this.dir.scale(-1.0);
    }

    /** Speed in blocks per tick at time t. */
    public double speed(double t) {
        if (t > TOUCHDOWN) {
            return t <= this.impact ? PLOUGH_SPEED : 0.0;
        }
        double k = approach(t);
        return START_DISTANCE * APPROACH_EASE * Math.pow(k, APPROACH_EASE - 1.0) / (TOUCHDOWN - SIGHT);
    }

    // ---- The trench. ---------------------------------------------------------------------------------------------

    /** The ground line (top of the surface + 1) at {@code along} blocks from the mark toward the touchdown point. */
    public double groundLine(double along) {
        double k = Mth.clamp(along / this.params.trenchLength(), 0.0, 1.0);
        return Mth.lerp(k, this.target.y, this.params.touchdownGround());
    }

    public Vec3 groundPoint(double along) {
        return new Vec3(this.target.x + this.dir.x * along, this.groundLine(along), this.target.z + this.dir.z * along);
    }

    /** 0 at the touchdown point, 1 at the mark. */
    private double progress(double along) {
        return 1.0 - Mth.clamp(along / this.params.trenchLength(), 0.0, 1.0);
    }

    public double trenchHalfWidth(double along) {
        double s = this.progress(along);
        return this.params.trenchWidth() * 0.5 * (0.38 + 0.62 * Math.sqrt(s));
    }

    public double trenchDepth(double along) {
        double s = this.progress(along);
        double k = Mth.clamp(s / 0.55, 0.0, 1.0);
        return this.params.trenchDepth() * (0.22 + 0.78 * k * k * (3.0 - 2.0 * k));
    }

    /** Distance from the mark along the trench line, and across it, for a world point. */
    public double along(double x, double z) {
        return (x - this.target.x) * this.dir.x + (z - this.target.z) * this.dir.z;
    }

    public double across(double x, double z) {
        return (x - this.target.x) * this.side.x + (z - this.target.z) * this.side.z;
    }

    /**
     * How far inside the trench a point is: 0 on the centre line, 1 on the wall, more than 1 outside.
     * Points before the mark or past the touchdown point's rounded end are outside.
     */
    public double trenchCross(double x, double z) {
        double along = this.along(x, z);
        double lat = this.across(x, z);
        int length = this.params.trenchLength();
        if (along < 0.0) {
            return Double.MAX_VALUE;
        }
        if (along > length) {
            double hw = this.trenchHalfWidth(length);
            return Math.hypot(along - length, lat) / hw;
        }
        double hw = this.trenchHalfWidth(along) * (1.0 + 0.07 * Math.sin(along * 0.11 + this.phase[0])
            + 0.04 * Math.sin(along * 0.29 + this.phase[1]));
        return Math.abs(lat) / hw;
    }

    /** The new top block of a column inside the trench, or {@link #UNTOUCHED}. */
    public int trenchFloor(int x, int z, int minY) {
        double cx = x + 0.5;
        double cz = z + 0.5;
        double cross = this.trenchCross(cx, cz);
        if (cross > 1.0) {
            return UNTOUCHED;
        }
        double along = Mth.clamp(this.along(cx, cz), 0.0, this.params.trenchLength());
        double lat = this.across(cx, cz);
        double base = this.groundLine(along) - 1.0;
        double bowl = Math.pow(Math.max(0.0, 1.0 - cross * cross), 0.65);
        // shallow grooves along the line of travel, scored by the nucleus
        double grooves = 1.1 * (0.5 + 0.5 * Math.sin(lat * 0.9 + this.phase[2])) * bowl;
        double floor = base - this.trenchDepth(along) * bowl - grooves;
        return Math.max(Mth.floor(floor), minY + 2);
    }

    // ---- The crater. ---------------------------------------------------------------------------------------------

    /** 0 at the centre of the crater, 1 on its rim. A little longer along the line of travel, as grazing impacts are. */
    public double craterK(double x, double z) {
        double a = this.along(x, z) / 1.18;
        double l = this.across(x, z);
        double angle = Math.atan2(l, a);
        double edge = this.params.craterRadius() * (1.0 + 0.05 * Math.sin(5.0 * angle + this.phase[3])
            + 0.025 * Math.sin(11.0 * angle + this.phase[4]));
        return Math.sqrt(a * a + l * l) / edge;
    }

    /** A point on the crater's rim, {@code theta} radians round it (0 = toward the touchdown point). */
    public Vec3 craterRim(double theta) {
        double edge = this.params.craterRadius() * (1.0 + 0.05 * Math.sin(5.0 * theta + this.phase[3])
            + 0.025 * Math.sin(11.0 * theta + this.phase[4]));
        double along = 1.18 * edge * Math.cos(theta);
        double across = edge * Math.sin(theta);
        return new Vec3(this.target.x + this.dir.x * along + this.side.x * across, this.target.y,
            this.target.z + this.dir.z * along + this.side.z * across);
    }

    /** The y of the trench floor's centre line (one above the top block), as cut. */
    public double trenchFloorY(double along) {
        double base = this.groundLine(along) - 1.0;
        return this.params.carve() ? base - this.trenchDepth(along) + 1.0 : base + 1.0;
    }

    public int craterFloor(int x, int z, int minY) {
        double k = this.craterK(x + 0.5, z + 0.5);
        if (k > 1.0) {
            return UNTOUCHED;
        }
        double depth = this.params.craterDepth() * (1.0 - k * k) * (1.0 - 0.3 * k * k);
        return Math.max(Mth.floor(this.target.y - 1.0 - depth), minY + 2);
    }

    /** Whether a point is anywhere in the scar (crater, trench or the touchdown end). */
    public boolean inScar(double x, double z, double margin) {
        double r = this.params.craterRadius() + margin;
        if (this.craterK(x, z) * this.params.craterRadius() <= r) {
            return true;
        }
        double along = this.along(x, z);
        if (along < -margin || along > this.params.trenchLength() + this.trenchHalfWidth(this.params.trenchLength()) + margin) {
            return false;
        }
        double hw = this.trenchHalfWidth(Mth.clamp(along, 0.0, this.params.trenchLength()));
        return this.trenchCross(x, z) * hw <= hw + margin;
    }

    // ---- Shared randomness. ----------------------------------------------------------------------------------------

    /** A stable 0..1 hash of a block column, the same on both sides. */
    public double hash(int x, int z) {
        long h = this.seed ^ (long) x * -7046029254386353131L ^ (long) z * -4417276706812531889L;
        h = (h ^ h >>> 31) * -4658895280553007687L;
        h ^= h >>> 29;
        return (double) (h >>> 11) / (double) (1L << 53);
    }

    public double phase(int i) {
        return this.phase[Math.floorMod(i, this.phase.length)];
    }
}
