package dev.ss05.halley;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The timeline and the geometry of one Luna moonfall.
 *
 * <p>Both sides build the same plan from the same numbers (the caster's position, the mark, the seed and the
 * {@link MoonParams}), so the server builds exactly the moon the client films. Pure maths: no world access.
 *
 * <p>Picture it: "EARTH SYSTEM SHUT DOWN". The moon, where it hangs in the night sky, shudders and cracks, glowing
 * seams of molten cheese opening across it. Then it tears loose and comes down along a straight line from there onto
 * the mark, slowly at first and then faster and faster, growing until it fills the sky. It hits the atmosphere and
 * burns, touches the ground with its lowest point exactly on the mark, and drives on down into the earth: the ground
 * heaves up round it into a jagged ring of upturned crust leaning against it, a band of molten rock glows where moon
 * and land meet, and cracks race out across the land. It comes to rest half buried. Then the dust settles.
 */
public final class MoonPlan {
    // ---- Timeline, in ticks since the button was pressed. --------------------------------------------------------
    /** The Stellar Remote's safety cover flips open. */
    public static final int ARM = 0;
    /** The button goes down: the uplink locks on. */
    public static final int PRESS = 7;
    /** "EARTH SYSTEM SHUT DOWN": the alarm, the title; the sky falls to night and starts to redden. */
    public static final int ALARM = 10;
    /** A creative-mode caster standing where the moon will land is lifted clear. */
    public static final int EVAC = 12;
    /** The moon shudders and cracks; molten seams open across it. */
    public static final int BREAK = 45;
    /** It tears loose from its place in the sky and starts to fall. */
    public static final int FALL = 85;
    /** It reaches the atmosphere: a burning shroud and a roar. */
    public static final int ENTRY = 260;
    /** Its lowest point touches the ground on the mark: the impact. */
    public static final int CONTACT = 340;
    /** It has driven down into the earth and come to rest; the crust round it has finished heaving up. */
    public static final int SETTLE = 410;
    /** How long the spell keeps running after it comes to rest (dust, debris, the molten seam cooling). */
    public static final int AFTERMATH = 270;
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

    // ---- The ground round it. --------------------------------------------------------------------------------------
    /** How many steps each column of the rim heaves up in (each a jolt: plates of crust shoved up in fits). */
    public static final int RIM_STEPS = 5;

    public static final int UNTOUCHED = HalleyPlan.UNTOUCHED;

    public final Vec3 origin;
    /** The mark, on the ground (y = the first air block above the ground). */
    public final Vec3 target;
    public final long seed;
    public final MoonParams params;
    /** Unit vector from the mark toward where the moon starts (it falls along this line, toward the mark). */
    public final Vec3 sky;
    /** The moon's centre when it is first seen, far up {@link #sky}. */
    public final Vec3 startCentre;
    /** The moon's centre at {@link #CONTACT}: its surface touches the mark. */
    public final Vec3 contactCentre;
    /** The moon's centre once it has come to rest, part buried. */
    public final Vec3 restCentre;
    /** The block that holds the moon's very centre at rest: the molten moon cheese. */
    public final BlockPos core;
    /** Unit vector, horizontal, pointing along the ground in the direction the moon was travelling. */
    public final Vec3 travel;
    /** Where the moon at rest cuts the ground: the radius of that circle round the mark (the molten seam runs along it). */
    public final double restFootprint;
    /** Where the shoved-up rim begins (the outer edge of the molten seam). */
    public final double rimInner;
    /** Where the rim has died down to the old ground again: the edge of the impact zone ({@code crater_radius}). */
    public final double rimOuter;
    private final double[] phase = new double[6];
    private final Crack[] cracks;

    public MoonPlan(Vec3 origin, Vec3 target, long seed, MoonParams params) {
        this.origin = origin;
        this.target = new Vec3(target.x, params.groundY(), target.z);
        this.seed = seed;
        this.params = params;
        this.sky = clampElevation(params.sky());

        double r = params.moonRadius();
        this.startCentre = this.target.add(this.sky.scale(START_DISTANCE));
        this.contactCentre = this.target.add(this.sky.scale(r));
        this.restCentre = new Vec3(this.target.x, params.groundY() + r - 2.0 * r * params.buried(), this.target.z);
        this.core = BlockPos.containing(this.restCentre);

        Vec3 flat = new Vec3(-this.sky.x, 0.0, -this.sky.z);
        this.travel = flat.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();

        double below = params.groundY() - this.restCentre.y;
        this.restFootprint = Math.sqrt(Math.max(0.0, r * r - below * below));
        this.rimInner = this.restFootprint + seamWidth(r);
        this.rimOuter = Math.max(this.rimInner, params.craterRadius());

        long h = seed;
        for (int i = 0; i < this.phase.length; i++) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            this.phase[i] = (h >>> 11) * 0x1.0p-53 * Math.PI * 2.0;
        }
        this.cracks = this.layCracks();
    }

    /** How wide the molten seam between the moon and the rim is, in blocks. */
    public static double seamWidth(double moonRadius) {
        return Math.max(1.5, moonRadius * 0.06);
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

    /**
     * 0 at {@link #CONTACT}, 1 at {@link #SETTLE}: how far it has driven down into the earth. It keeps most of its
     * speed into the ground and then brakes hard (the crust piling up in front of it), so this eases out steeply.
     */
    public static double settle(double t) {
        if (t <= CONTACT) {
            return 0.0;
        }
        if (t >= SETTLE) {
            return 1.0;
        }
        double k = (t - CONTACT) / (SETTLE - CONTACT);
        return 1.0 - Math.pow(1.0 - k, 3.4);
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

    /**
     * The radius of the circle where the moon cuts the ground at time {@code t} (0 before it touches): the line of
     * molten rock where moon and land meet, widening as it drives in.
     */
    public double footprint(double t) {
        if (t < CONTACT) {
            return 0.0;
        }
        double r = this.params.moonRadius();
        double above = this.centre(t).y - this.params.groundY();
        return Math.sqrt(Math.max(0.0, r * r - above * above));
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
        return (float) Mth.clamp(1.0 - (t - CONTACT) / 200.0, 0.0, 1.0);
    }

    /** 0..1: how hot the molten seam (and the cracks) still glow: white hot at the impact, cooling to dark red. */
    public static float heat(double t) {
        if (t < CONTACT) {
            return 0.0F;
        }
        return (float) Math.exp(-(t - CONTACT) / 170.0);
    }

    // ---- What it leaves behind. ------------------------------------------------------------------------------------

    /** Whether a block (by its centre) lies inside the moon at rest. */
    public boolean inMoon(int x, int y, int z) {
        double r = this.params.moonRadius();
        return this.restCentre.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) <= r * r;
    }

    /** Horizontal distance from the mark to a point. */
    public double fromMark(double x, double z) {
        return Math.hypot(x - this.target.x, z - this.target.z);
    }

    /**
     * The y of the moon at rest's top surface over this column (the ground above it is cut away down to there), or
     * {@link #UNTOUCHED} if the column is outside the moon's footprint.
     */
    public int moonTop(int x, int z) {
        double r = this.params.moonRadius();
        double dx = x + 0.5 - this.restCentre.x;
        double dz = z + 0.5 - this.restCentre.z;
        double d2 = dx * dx + dz * dz;
        if (d2 > r * r) {
            return UNTOUCHED;
        }
        return Mth.floor(this.restCentre.y + Math.sqrt(r * r - d2));
    }

    /** Whether this column is in the molten seam, the band between the moon at rest and the rim. */
    public boolean inSeam(double x, double z) {
        double d = this.fromMark(x, z);
        return d >= this.restFootprint - 0.5 && d < this.rimInner;
    }

    /** Everything within this distance of the mark is erased at {@link #CONTACT}. */
    public double eraseRadius() {
        return Math.max(this.rimOuter, this.params.moonRadius()) + 2.0;
    }

    /** How far the shock wave throws and hurts things. */
    public double blastRadius() {
        return Math.max(this.rimOuter, this.params.moonRadius()) * this.params.blastReach();
    }

    // ---- The rim: the ground shoved up round the moon. --------------------------------------------------------------

    /** 0 at the rim's inner edge (against the seam), 1 at its outer edge; outside 0..1 it isn't rim. */
    public double rimK(double x, double z) {
        return (this.fromMark(x, z) - this.rimInner) / Math.max(1.0, this.rimOuter - this.rimInner);
    }

    /** How many plates of crust the rim is broken into, all round. */
    public int rimPlates() {
        return Math.max(12, (int) Math.round(Math.PI * 2.0 * this.rimInner / 7.0));
    }

    /** Which plate a point is on (0 .. {@link #rimPlates()} - 1), and how far across it (0..1) - with ragged edges. */
    private double plateCoordinate(double x, double z) {
        double angle = Math.atan2(z - this.target.z, x - this.target.x);
        double a = (angle / (Math.PI * 2.0) + 1.0) % 1.0;
        double ragged = 0.18 * Math.sin(this.fromMark(x, z) * 0.21 + this.phase[2]) / this.rimPlates();
        return (a + ragged) * this.rimPlates();
    }

    /**
     * How high the rim finally stands over the old ground at this column, in whole blocks (0 off the rim): a jagged
     * ring of upturned plates leaning against the moon, highest at its inner lip and dying away outward, with cracks
     * between the plates and a ragged spill of crust past them.
     */
    public int rimHeight(int x, int z) {
        double height = this.params.rimHeight();
        if (height <= 0.0) {
            return 0;
        }
        double k = this.rimK(x + 0.5, z + 0.5);
        if (k < 0.0 || k >= 1.0) {
            return 0;
        }
        double plate = this.plateCoordinate(x + 0.5, z + 0.5);
        int index = Math.floorMod((int) Math.floor(plate), this.rimPlates());
        double across = plate - Math.floor(plate);
        double tall = 0.6 + 0.75 * this.hash(index, 101, -index);
        double reach = 0.7 + 0.45 * this.hash(index, 103, index);
        double u = k / reach;
        // the plate's own profile: a steep lip against the moon, then a long, falling back
        double profile = u >= 1.0 ? 0.0 : smoothstep(0.0, 0.06, u) * Math.pow(1.0 - u, 1.55);
        // the cracks between plates: the edges of each plate sag into a gap
        double edge = Math.min(across, 1.0 - across);
        double gap = smoothstep(0.02, 0.16, edge);
        double h = height * tall * profile * (0.3 + 0.7 * gap);
        // past the plates: a thin, broken spill of thrown crust
        if (h < 1.0 && k < 0.92 && this.hash(x, 107, z) < 0.55 * (1.0 - k)) {
            h = 1.0;
        }
        h += (this.hash(x, 109, z) - 0.5) * 1.4;
        return Math.max(0, (int) Math.floor(h));
    }

    /**
     * 0..{@link #RIM_STEPS}: how many of its heaves this column has made at time {@code t}. The rim heaves up while the
     * moon drives in (so they end together); each column jolts up in steps, each a little before or after its
     * neighbours, so the ground reads as plates shoved up in fits rather than rising like dough.
     */
    public int rimStep(int x, int z, double t) {
        if (t < CONTACT) {
            return 0;
        }
        double rise = settle(t + 2.0);
        double jitter = (this.hash(x >> 2, 113, z >> 2) - 0.5) * 0.12 + (this.hash(x, 127, z) - 0.5) * 0.04;
        return Mth.clamp((int) Math.floor((rise + jitter + 0.04) * RIM_STEPS), 0, RIM_STEPS);
    }

    /** 0..1: how far the rim has heaved up overall at time {@code t} (for the client's dust and sound). */
    public static double rise(double t) {
        return settle(t + 2.0);
    }

    // ---- The cracks: fissures racing out across the land from the rim at the impact, branching as they go. ---------

    /** How many main cracks run out from the rim. */
    public static final int MAIN_CRACKS = 14;
    /** How long a main crack takes to race out to its full length, in ticks. */
    public static final int CRACK_TICKS = 26;

    /** One crack: where it starts, which way, how long and how wide, and when it starts running. */
    private record Crack(double x, double z, double angle, double length, double width, double delay, int parent,
                         double phase) {
    }

    private Crack[] layCracks() {
        double start = this.rimOuter * 0.88;
        double reach = Math.max(8.0, this.blastRadius() - start);
        double width = Math.max(1.0, this.params.moonRadius() * 0.06);
        java.util.List<Crack> out = new java.util.ArrayList<>();
        for (int i = 0; i < MAIN_CRACKS; i++) {
            double even = (i + 0.5) / MAIN_CRACKS * Math.PI * 2.0;
            double angle = even + (this.hash(i, 7, -i) - 0.5) * (Math.PI * 2.0 / MAIN_CRACKS) * 0.8;
            double length = reach * (0.5 + 0.5 * this.hash(i, 11, i));
            double delay = 2.0 + this.hash(i, 17, i) * 5.0;
            out.add(new Crack(this.target.x + Math.cos(angle) * start, this.target.z + Math.sin(angle) * start, angle,
                length, width * (0.75 + 0.5 * this.hash(i, 13, -i)), delay, -1, this.hash(i, 43, i) * Math.PI * 2.0));
        }
        // branches: one or two off most main cracks, splitting away at an angle and running shorter and thinner
        int mains = out.size();
        for (int i = 0; i < mains; i++) {
            Crack parent = out.get(i);
            int count = this.hash(i, 19, 3) < 0.25 ? 0 : this.hash(i, 23, 5) < 0.55 ? 1 : 2;
            for (int b = 0; b < count; b++) {
                double at = 0.18 + 0.42 * this.hash(i, 29 + b, b);
                double side = (b == 0) == (this.hash(i, 31, 0) < 0.5) ? 1.0 : -1.0;
                double turn = side * (0.35 + 0.35 * this.hash(i, 37 + b, b));
                Vec3 from = this.crackPoint(parent, at);
                double length = parent.length * (1.0 - at) * (0.3 + 0.35 * this.hash(i, 41 + b, b));
                double delay = parent.delay + CRACK_TICKS * Math.sqrt(at) * 0.9 + 1.0;
                out.add(new Crack(from.x, from.z, parent.angle + turn, length,
                    parent.width * (1.0 - at * 0.6) * 0.6, delay, i, this.hash(i, 47 + b, b) * Math.PI * 2.0));
            }
        }
        return out.toArray(new Crack[0]);
    }

    /** How many cracks there are, main ones and branches. */
    public int crackCount() {
        return this.cracks.length;
    }

    /** How long crack {@code i} is, in blocks. */
    public double crackLength(int i) {
        return this.cracks[i].length;
    }

    /** A point on crack {@code i}, {@code along} 0 at its start to 1 at its tip; it wanders from side to side. The y is
     * the mark's (the ground is found by whoever uses it). */
    public Vec3 crackPoint(int i, double along) {
        return this.crackPoint(this.cracks[i], along);
    }

    private Vec3 crackPoint(Crack c, double along) {
        double r = along * c.length;
        double wander = 0.24 * Math.sin(along * 7.0 + c.phase) + 0.1 * Math.sin(along * 19.0 + c.phase * 1.7);
        double lateral = wander * Math.min(r, 14.0 + 0.25 * r);
        double cos = Math.cos(c.angle);
        double sin = Math.sin(c.angle);
        return new Vec3(c.x + cos * r - sin * lateral, this.target.y, c.z + sin * r + cos * lateral);
    }

    /** Half the width of crack {@code i} at {@code along}, in blocks: widest at its start, tapering to a point. */
    public double crackHalfWidth(int i, double along) {
        Crack c = this.cracks[i];
        return c.width * (1.0 - along) * (1.0 - along * 0.25);
    }

    /** Whether crack {@code i} is a branch (rather than one of the main cracks out of the rim). */
    public boolean crackIsBranch(int i) {
        return this.cracks[i].parent >= 0;
    }

    /** 0..1: how far crack {@code i} has run out at time {@code t}. */
    public double crackFront(int i, double t) {
        Crack c = this.cracks[i];
        double since = t - CONTACT - c.delay;
        if (since <= 0.0) {
            return 0.0;
        }
        double ticks = CRACK_TICKS * (c.parent >= 0 ? 0.6 : 1.0);
        double k = Math.min(1.0, since / ticks);
        return 1.0 - (1.0 - k) * (1.0 - k);
    }

    /** Ticks after {@link #CONTACT} by which every crack has run out to its full length. */
    public double cracksDoneAfter() {
        double last = 0.0;
        for (Crack c : this.cracks) {
            last = Math.max(last, c.delay + CRACK_TICKS);
        }
        return last;
    }

    // ---- Debris: what the impact throws out (the client films it flying, the server leaves it where it lands). ------

    /** How many lumps of moon and crust are thrown clear. */
    public int debrisCount() {
        return 40 + (int) (this.hash(11, 0, 29) * 40.0);
    }

    /** Where thrown lump {@code i} lands (y = the mark's; the ground is found by whoever uses it). */
    public Vec3 debrisLanding(int i) {
        double angle = this.hash(i * 5 + 1, 0, 7) * Math.PI * 2.0;
        double dist = this.rimOuter * (1.02 + this.hash(i * 3 + 2, 0, 13) * 1.3);
        return new Vec3(this.target.x + Math.cos(angle) * dist, this.target.y, this.target.z + Math.sin(angle) * dist);
    }

    /** How long lump {@code i} flies before it lands, in ticks after {@link #CONTACT}. */
    public double debrisFlight(int i) {
        return 22.0 + this.hash(i, 0, 31) * 46.0;
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

    private static double smoothstep(double edge0, double edge1, double x) {
        double k = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return k * k * (3.0 - 2.0 * k);
    }
}
