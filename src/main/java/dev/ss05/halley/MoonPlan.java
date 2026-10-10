package dev.ss05.halley;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The timeline and the geometry of one SS-06 moonfall.
 *
 * <p>Both sides build the same plan from the same numbers (the caster's position, the mark, the seed and the
 * {@link MoonParams}), so the server builds exactly the moon the client films. Pure maths: no world access.
 *
 * <p>Picture it: "EARTH SYSTEM SHUT DOWN". The moon, where it hangs in the night sky, shudders and cracks, glowing
 * seams of molten cheese opening across it. Then it leaves its place and comes down along a straight line from there
 * onto the mark, slowly at first and then faster and faster, growing until it fills the sky. It hits the atmosphere and
 * burns, touches the ground with its lowest point exactly on the mark, ploughs on down into the crater it digs and comes
 * to rest there, part buried. Then the dust settles.
 */
public final class MoonPlan {
    // ---- Timeline, in ticks since the button was pressed. --------------------------------------------------------
    /** The Stellar Remote's safety cover flips open. */
    public static final int ARM = 0;
    /** The button goes down. */
    public static final int PRESS = 7;
    /** "EARTH SYSTEM SHUT DOWN": the alarm, the title; the sky falls to night (like SS-05) and starts to redden. */
    public static final int ALARM = 10;
    /** A creative-mode caster standing in the crater is lifted clear. */
    public static final int EVAC = 12;
    /** The moon shudders and cracks; molten seams open across it. */
    public static final int BREAK = 60;
    /** It leaves its place in the sky and starts to fall. */
    public static final int FALL = 120;
    /** It reaches the atmosphere: a burning shroud and a roar. */
    public static final int ENTRY = 380;
    /** Its lowest point touches the ground on the mark: the impact. */
    public static final int CONTACT = 480;
    /** It has ploughed into the crater it dug and come to rest. */
    public static final int SETTLE = 540;
    /** How long the spell keeps running after it comes to rest (dust, debris, the sky clearing). */
    public static final int AFTERMATH = 300;
    public static final int DURATION = SETTLE + AFTERMATH;

    // ---- The fall. -----------------------------------------------------------------------------------------------
    /** How far away the moon starts, in blocks (only its direction and apparent size matter; it is drawn pulled in). */
    public static final double START_DISTANCE = 3200.0;
    /** Bigger = the moon hangs far away longer and then rushes in at the end. */
    public static final double FALL_EASE = 2.6;
    /** The lowest it may start, in degrees above the horizon (if the moon is lower than that, it starts from there). */
    public static final double MIN_ELEVATION_DEGREES = 18.0;
    /** How far the moon turns about its own axis over the whole fall, in turns. */
    public static final double SPIN_TURNS = 0.35;

    public static final int UNTOUCHED = HalleyPlan.UNTOUCHED;

    public final Vec3 origin;
    /** The mark, on the ground (y = the first air block above the ground, like SS-05's mark). */
    public final Vec3 target;
    public final long seed;
    public final MoonParams params;
    /** Unit vector from the mark toward where the moon starts (it falls along this line, toward the mark). */
    public final Vec3 sky;
    /** The moon's centre when it is first seen, far up {@link #sky}. */
    public final Vec3 startCentre;
    /** The moon's centre at {@link #CONTACT}: its surface touches the mark. */
    public final Vec3 contactCentre;
    /** The moon's centre once it has come to rest in the crater. */
    public final Vec3 restCentre;
    /** The block that holds the moon's very centre at rest: the molten moon cheese. */
    public final BlockPos core;
    /** Unit vector, horizontal, pointing along the ground in the direction the moon was travelling. */
    public final Vec3 travel;
    private final double[] phase = new double[6];

    public MoonPlan(Vec3 origin, Vec3 target, long seed, MoonParams params) {
        this.origin = origin;
        this.target = new Vec3(target.x, params.groundY(), target.z);
        this.seed = seed;
        this.params = params;
        this.sky = clampElevation(params.sky());

        double r = params.moonRadius();
        this.startCentre = this.target.add(this.sky.scale(START_DISTANCE));
        this.contactCentre = this.target.add(this.sky.scale(r));
        this.restCentre = new Vec3(this.target.x, params.groundY() + r - 2.0 * r * params.sink(), this.target.z);
        this.core = BlockPos.containing(this.restCentre);

        Vec3 flat = new Vec3(-this.sky.x, 0.0, -this.sky.z);
        this.travel = flat.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();

        long h = seed;
        for (int i = 0; i < this.phase.length; i++) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            this.phase[i] = (h >>> 11) * 0x1.0p-53 * Math.PI * 2.0;
        }
    }

    /**
     * Where the moon is in the sky at this time of day ({@code LevelTimeAccess.getTimeOfDay}, 0..1): the same
     * direction the vanilla sky draws it in (rotated -90 degrees about Y, then by the time of day about X).
     */
    public static Vec3 skyDirection(float timeOfDay) {
        double angle = timeOfDay * Math.PI * 2.0;
        return new Vec3(Math.sin(angle), -Math.cos(angle), 0.0);
    }

    /** The direction lifted to at least {@link #MIN_ELEVATION_DEGREES} above the horizon, keeping its bearing. */
    public static Vec3 clampElevation(Vec3 direction) {
        Vec3 d = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : direction.normalize();
        double min = Math.sin(Math.toRadians(MIN_ELEVATION_DEGREES));
        if (d.y >= min) {
            return d;
        }
        Vec3 flat = new Vec3(d.x, 0.0, d.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
        double c = Math.cos(Math.toRadians(MIN_ELEVATION_DEGREES));
        return new Vec3(flat.x * c, min, flat.z * c);
    }

    // ---- The moon's motion. --------------------------------------------------------------------------------------

    /** 0 while it hangs in the sky, rising to 1 at {@link #CONTACT}: how much of the way down it has come. */
    public static double fall(double t) {
        if (t <= FALL) {
            return 0.0;
        }
        if (t >= CONTACT) {
            return 1.0;
        }
        return Math.pow((t - FALL) / (CONTACT - FALL), FALL_EASE);
    }

    /** 0 at {@link #CONTACT}, 1 at {@link #SETTLE}: how far it has ploughed down into the crater (eases out). */
    public static double settle(double t) {
        if (t <= CONTACT) {
            return 0.0;
        }
        if (t >= SETTLE) {
            return 1.0;
        }
        double k = (t - CONTACT) / (SETTLE - CONTACT);
        return 1.0 - (1.0 - k) * (1.0 - k) * (1.0 - k);
    }

    /** The moon's centre at time {@code t} (ticks since the button, fractional for smooth frames). */
    public Vec3 centre(double t) {
        if (t < CONTACT) {
            return this.startCentre.lerp(this.contactCentre, fall(t));
        }
        return this.contactCentre.lerp(this.restCentre, settle(t));
    }

    /** Blocks per tick it is moving at time {@code t}. */
    public double speed(double t) {
        return this.centre(t + 0.5).distanceTo(this.centre(t - 0.5));
    }

    /** How far the moon's surface still is from the ground on the mark, in blocks (0 from {@link #CONTACT} on). */
    public double distanceToGo(double t) {
        return t >= CONTACT ? 0.0 : this.centre(t).distanceTo(this.contactCentre);
    }

    /** The moon's turn about its own axis, in radians (the axis is horizontal, across its line of travel). */
    public double spin(double t) {
        double k = Mth.clamp((t - BREAK) / (SETTLE - BREAK), 0.0, 1.0);
        return k * k * SPIN_TURNS * Math.PI * 2.0;
    }

    /** 0..1: how far the molten seams have opened (from {@link #BREAK}; fully open by {@link #FALL}). */
    public static float crack(double t) {
        return (float) Mth.clamp((t - BREAK) / (FALL - BREAK), 0.0, 1.0);
    }

    /** 0..1: the burning shroud of atmospheric entry (builds from {@link #ENTRY}, fades after it comes to rest). */
    public static float burn(double t) {
        if (t < ENTRY - 30) {
            return 0.0F;
        }
        if (t < CONTACT) {
            return (float) Mth.clamp((t - (ENTRY - 30)) / (CONTACT - (ENTRY - 30)), 0.0, 1.0);
        }
        return (float) Mth.clamp(1.0 - (t - CONTACT) / 120.0, 0.0, 1.0);
    }

    /** 0..1: how hard the ground shakes under it (rumbling as it nears, the impact, then dying away). */
    public float quake(double t) {
        if (t < FALL) {
            return 0.0F;
        }
        if (t < CONTACT) {
            return (float) (0.35 * fall(t));
        }
        return (float) Mth.clamp(1.0 - (t - CONTACT) / 160.0, 0.0, 1.0);
    }

    // ---- What it leaves behind. ------------------------------------------------------------------------------------

    /** Whether a block (by its centre) lies inside the moon at rest. */
    public boolean inMoon(int x, int y, int z) {
        double r = this.params.moonRadius();
        return this.restCentre.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) <= r * r;
    }

    /** 0 at the centre of the crater, 1 on its rim; the rim wobbles a little, the same on both sides. */
    public double craterK(double x, double z) {
        double dx = x - this.target.x;
        double dz = z - this.target.z;
        double angle = Math.atan2(dz, dx);
        double edge = this.params.craterRadius() * (1.0 + 0.04 * Math.sin(5.0 * angle + this.phase[0])
            + 0.02 * Math.sin(13.0 * angle + this.phase[1]));
        return Math.sqrt(dx * dx + dz * dz) / edge;
    }

    /** The y of the new top block of this column of the crater, or {@link #UNTOUCHED} outside it. */
    public int craterFloor(int x, int z, int minY) {
        double k = this.craterK(x + 0.5, z + 0.5);
        if (k > 1.0) {
            return UNTOUCHED;
        }
        double depth = this.params.craterDepth() * (1.0 - k * k) * (1.0 - 0.25 * k * k);
        return Math.max(Mth.floor(this.target.y - 1.0 - depth), minY + 2);
    }

    /** Everything within this distance of the mark is erased at {@link #CONTACT}. */
    public double eraseRadius() {
        return Math.max(this.params.craterRadius(), this.params.moonRadius()) + 2.0;
    }

    /** How far the shock wave throws and hurts things. */
    public double blastRadius() {
        return this.params.craterRadius() * this.params.blastReach();
    }

    // ---- The cracks (like Gungnir's): fissures racing out across the land from the crater at the impact. -------------

    /** How many cracks run out from the crater. */
    public static final int CRACKS = 16;
    /** How long they take to race out to their full length after {@link #CONTACT}, in ticks. */
    public static final int CRACK_TICKS = 24;

    /** The bearing crack {@code i} leaves the crater at, in radians (spread round, a little irregular). */
    public double crackAngle(int i) {
        double even = (i + 0.5) / CRACKS * Math.PI * 2.0;
        return even + (this.hash(i, 7, -i) - 0.5) * (Math.PI * 2.0 / CRACKS) * 0.8;
    }

    /** How far crack {@code i} runs beyond the crater rim, in blocks (out to about the shock wave's reach). */
    public double crackLength(int i) {
        return (this.blastRadius() - this.params.craterRadius()) * (0.55 + 0.45 * this.hash(i, 11, i));
    }

    /**
     * A point on crack {@code i}, {@code along} 0 at the crater rim to 1 at its tip; it wanders from side to side.
     * The y is the mark's (the ground is found by whoever uses it).
     */
    public Vec3 crackPoint(int i, double along) {
        double angle = this.crackAngle(i);
        double r = this.params.craterRadius() + along * this.crackLength(i);
        double wander = 0.22 * Math.sin(along * 7.0 + this.phase(i)) + 0.09 * Math.sin(along * 19.0 + this.phase(i + 3));
        double a = angle + wander * (0.35 + 0.65 * along) * this.params.craterRadius() / Math.max(r, 1.0);
        return new Vec3(this.target.x + Math.cos(a) * r, this.target.y, this.target.z + Math.sin(a) * r);
    }

    /** Half the width of crack {@code i} at {@code along}, in blocks: widest at the rim, tapering to a point. */
    public double crackHalfWidth(int i, double along) {
        double base = Math.max(1.0, this.params.craterRadius() * 0.035) * (0.7 + 0.6 * this.hash(i, 13, -i));
        return base * (1.0 - along) * (1.0 - along * 0.3);
    }

    /** 0..1: how far the cracks have run out at time {@code t} (they race out from {@link #CONTACT}). */
    public static double crackFront(double t) {
        if (t <= CONTACT) {
            return 0.0;
        }
        double k = Math.min(1.0, (t - CONTACT) / CRACK_TICKS);
        return 1.0 - (1.0 - k) * (1.0 - k);
    }

    // ---- Shared randomness. ----------------------------------------------------------------------------------------

    /** A stable 0..1 hash of a block, the same on both sides. */
    public double hash(int x, int y, int z) {
        long h = this.seed ^ (long) x * -7046029254386353131L ^ (long) z * -4417276706812531889L ^ (long) y * 0x2545F4914F6CDD1DL;
        h = (h ^ h >>> 31) * -4658895280553007687L;
        h ^= h >>> 29;
        return (double) (h >>> 11) / (double) (1L << 53);
    }

    public double phase(int i) {
        return this.phase[Math.floorMod(i, this.phase.length)];
    }
}
