package dev.ss05.halley.client.render;

import dev.ss05.halley.HalleyPlan;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Everything SS-05 draws in the world, as glow quads: the marks projected onto the land, the comet and its tails, the
 * entry fireball, the plough, the detonation and the comet heart's glow. Pure geometry from a {@link HalleyPlan} and a
 * time; the look of each shape is in the halley_glow shader.
 *
 * <p>Two batches come in: {@code world} is hidden behind terrain like anything else, {@code light} is not (only for
 * the short, blinding moments whose glare would otherwise be cut off sharply where it meets the ground).
 *
 * <p>Tuning: colours and sizes are the constants at the top; timings are {@link HalleyPlan}'s.
 */
public final class CometVisuals {
    /** Whether there is land drawn at a point (the client only has the chunks near the player). */
    public interface Land {
        boolean has(double x, double z);

        /** The first air above the ground at (x, z), or NaN where it isn't known. */
        default double surface(double x, double z) {
            return Double.NaN;
        }
    }

    // ---- Colours. -------------------------------------------------------------------------------------------------
    public static final int ICE = 0x6FE8FF;
    public static final int PALE = 0xE2FBFF;
    public static final int WHITE = 0xFFFFFF;
    /** The ion tail: thin, straight, electric blue. */
    public static final int ION = 0x4FAEFF;
    /** The dust tail: broad, curved, pale. */
    public static final int DUST = 0xC9E6FF;
    /** Heated by the atmosphere: the bow shock and the fireball trail. */
    public static final int HEAT = 0xFFD9B0;
    public static final int FROST = 0xB4F0FF;
    public static final int VAPOUR = 0x86A9C9;

    // ---- Sizes, in blocks. ----------------------------------------------------------------------------------------
    private static final double COMA = 30.0;
    private static final double NUCLEUS = 8.0;
    private static final double ION_LENGTH = 2400.0;
    private static final double DUST_LENGTH = 1600.0;
    /**
     * The smallest each part may look, in radians (sizes) or as a share of the distance (tail lengths), so it reads
     * as a great comet from kilometres away rather than as a dot.
     */
    private static final double COMA_MIN_ANGLE = 0.02;
    private static final double NUCLEUS_MIN_ANGLE = 0.006;
    private static final double GLINT_MIN_ANGLE = 0.05;
    private static final double ION_MIN_SHARE = 0.6;
    private static final double DUST_MIN_SHARE = 0.4;
    /** How far apart the trench's afterglow pieces are. */
    private static final double TRENCH_STEP = 5.0;
    /** Ticks the shock front takes to cross the land (the server's blast takes the same time). */
    private static final int BLAST_TICKS = 28;

    private final HalleyPlan plan;
    private final int minY;
    private final Vec3 ionDir;
    private final Vec3 ionDir2;
    private final Vec3 dustDir;
    private final Vec3 dustBend;
    private final double spin;
    private final double entryDistance;
    @Nullable
    private final Vec3 heartBase;
    @Nullable
    private final Vec3 heartTip;
    private final double heartSize;

    public CometVisuals(HalleyPlan plan, int minY) {
        this.plan = plan;
        this.minY = minY;
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 out = plan.side.scale(plan.sideSign);
        // Real comet tails point away from the Sun, not back along the path; pointing them up and out to the side
        // lets them sweep across the sky instead of hiding straight behind the head.
        this.ionDir = plan.incoming.scale(0.35).add(up.scale(0.75)).add(out.scale(0.55)).normalize();
        this.ionDir2 = this.ionDir.add(out.scale(0.08)).add(up.scale(-0.05)).normalize();
        this.dustDir = this.ionDir.scale(0.75).add(plan.incoming.scale(0.65)).normalize();
        this.dustBend = plan.incoming.subtract(this.ionDir).normalize().scale(260.0);
        this.spin = (plan.seed & 1023L) / 1023.0 * Math.PI;
        this.entryDistance = HalleyPlan.distanceToGo(HalleyPlan.ENTRY);

        if (plan.params.heart() && plan.params.carve()) {
            // where the server stands the heart (HalleyStrike.placeHeart): its main spike, nearly upright
            int floor = plan.craterFloor(Mth.floor(plan.target.x), Mth.floor(plan.target.z), minY);
            this.heartSize = Math.max(0.5, plan.params.craterRadius() / 56.0);
            this.heartBase = new Vec3(Mth.floor(plan.target.x) + 0.5, floor + 0.5, Mth.floor(plan.target.z) + 0.5);
            double length = 26.0 * this.heartSize;
            this.heartTip = this.heartBase.add(Math.sin(0.08) * length, Math.cos(0.08) * length, 0.0);
        } else {
            this.heartSize = 0.0;
            this.heartBase = null;
            this.heartTip = null;
        }
    }

    /** The middle of the comet heart (for sounds, particles and the camera), or the mark when there is no heart. */
    public Vec3 heart() {
        return this.heartBase == null ? this.plan.target : this.heartBase.add(0.0, 12.0 * this.heartSize, 0.0);
    }

    public boolean hasHeart() {
        return this.heartBase != null;
    }

    /** The top of the heart's main spike, or null when there is no heart. */
    @Nullable
    public Vec3 heartTip() {
        return this.heartTip;
    }

    /** The direction the comet's tails stream in before it enters the atmosphere. */
    public Vec3 tailDirection() {
        return this.ionDir;
    }

    /**
     * Draws the strike at time {@code t} (ticks since the button was pressed, with the partial tick).
     *
     * @param marks     the ground marks, once sampled (null before)
     * @param markRange how far from the camera the land is drawn (marks beyond it would float over nothing)
     * @param film      how much of the screen the caster's cutscene has (0..1): outside it the comet is drawn bigger,
     *                  since nobody is framing it with a long lens
     */
    public void draw(GlowBatch world, GlowBatch light, double t, @Nullable GroundMarks marks, double markRange, Land land, float film) {
        if (t < 0.0 || t > this.plan.duration) {
            return;
        }
        if (marks != null && t >= HalleyPlan.MARK && t < this.plan.impact + 4) {
            this.marks(world, t, marks, markRange, land);
        }
        if (t >= HalleyPlan.SIGHT && t <= HalleyPlan.TOUCHDOWN) {
            this.approach(world, light, t, 1.0 + 0.9 * (1.0 - film));
        }
        if (t > HalleyPlan.TOUCHDOWN) {
            this.skyTrail(world, t);
        }
        if (t >= HalleyPlan.TOUCHDOWN) {
            this.touchdown(world, light, t, land);
            this.trenchGlow(world, t, land);
        }
        if (t >= HalleyPlan.TOUCHDOWN && t <= this.plan.impact) {
            this.plough(world, light, t, land);
        }
        if (t >= this.plan.impact) {
            this.detonation(world, light, t, land);
            this.heart(world, t);
        }
    }

    // ---- Before the strike: the marks on the land. ----------------------------------------------------------------

    private void marks(GlowBatch out, double t, GroundMarks marks, double range, Land land) {
        double since = t - HalleyPlan.MARK;
        float gone = 1.0F - smooth((t - (this.plan.impact - 3)) / 3.0);
        Vec3 cam = out.camera();
        int length = this.plan.params.trenchLength();
        Vec3 towardMark = this.plan.dir.scale(-1.0);

        // the uplink's ping: a thread of light from the sky to the mark, the moment it lands
        if (since < 18.0) {
            float ping = smooth(since / 2.0) * (1.0F - smooth((since - 3.0) / 15.0));
            out.column(marks.mark, 30.0 + 260.0 * smooth(since / 5.0), 0.9, Shapes.STREAK, Shapes.ION, PALE, 0.9F * ping);
            if (land.has(marks.mark.x, marks.mark.z)) {
                out.flat(marks.mark, this.plan.dir, 4.0 + since * 3.0, 4.0 + since * 3.0, Shapes.SHOCK, 0, ICE,
                    0.7F * (1.0F - smooth(since / 14.0)));
            }
        }

        // the mark: a reticle that drops onto the crosshair and settles, and a beacon over it (flat marks all but
        // vanish when seen from far off and low down, as the caster sees them)
        if (land.has(marks.mark.x, marks.mark.z)) {
            float reticleIn = smooth(since / 3.0) * gone * fade(cam, marks.mark, range);
            double size = 7.0 * (1.0 + 2.2 * (1.0 - smooth(since / 8.0)));
            out.flat(marks.mark, towardMark, size, size, Shapes.RETICLE, 0, ICE, 0.95F * reticleIn);
            out.flat(marks.mark, towardMark, 2.2, 2.2, Shapes.GLOW, 0, PALE, 0.5F * reticleIn);
            float beat = 0.75F + 0.25F * (float) Math.sin(t * (0.25 + 0.5 * smooth((t - 150.0) / 150.0)));
            // the beacon is for seeing the mark from afar: it fades away as the camera comes close to it
            float far = reticleIn * smooth((cam.distanceTo(marks.mark) - 20.0) / 60.0);
            out.billboard(marks.mark.add(0.0, 1.2, 0.0), 3.2, Shapes.GLOW, 0, ICE, 0.8F * far * beat, 0.0);
            out.column(marks.mark, 48.0, 0.45, Shapes.STREAK, Shapes.ION, ICE, 0.55F * far * smooth((since - 6.0) / 6.0));
        }

        // the crater's outline, drawn round from the side facing the comet
        int n = marks.rim.length;
        for (int i = 0; i < n; i++) {
            double theta = marks.rimAngle[i];
            double fromFront = Math.abs(Mth.wrapDegrees(Math.toDegrees(theta))) / 180.0;
            float in = smooth((since - 2.0 - fromFront * 10.0) / 3.0);
            Vec3 a = marks.rim[i];
            if (in <= 0.0F || !land.has(a.x, a.z)) {
                continue;
            }
            Vec3 b = marks.rim[(i + 1) % n];
            float run = 0.75F + 0.25F * (float) Math.sin(theta * 3.0 - t * 0.12);
            this.segment(out, a, b, 0.72, 1.1, ICE, 0.95F * in * run * gone * fade(cam, a, range));
        }

        // the corridor: arrows pointing at the mark, flowing toward it faster and faster
        double flow = 0.025 + 0.11 * smooth((t - 150.0) / 150.0);
        double phase = (t - HalleyPlan.MARK) * flow;
        for (int i = 0; i < marks.chevrons.length; i++) {
            double along = marks.chevronAlong[i];
            Vec3 at = marks.chevrons[i];
            float in = smooth((since - 4.0 - 30.0 * along / length) / 4.0);
            float passed = this.passed(t, along);
            if (in <= 0.0F || passed <= 0.0F || !land.has(at.x, at.z)) {
                continue;
            }
            double wave = Mth.frac(along / length * 2.0 + phase);
            float pulse = (float) Math.exp(-sq((wave - 0.5) / 0.1));
            double hw = this.plan.trenchHalfWidth(along) * 0.75;
            float k = in * passed * gone * fade(cam, at, range);
            out.flat(at, towardMark, hw * 0.6, hw, Shapes.CHEVRON, 0, ICE, (0.45F + 0.55F * pulse) * k);
            // a light over each arrow, like a runway's approach lights, so the corridor reads from low down too
            out.billboard(at.add(0.0, 1.0, 0.0), 1.1 + 1.4 * pulse, Shapes.GLOW, 0, pulse > 0.5F ? PALE : ICE, (0.35F + 0.65F * pulse) * k, 0.0);
        }

        // the corridor's edges, dashed, with a light every few dashes
        for (int side = 0; side < 2; side++) {
            Vec3[] edge = side == 0 ? marks.leftEdge : marks.rightEdge;
            for (int i = 0; i + 1 < edge.length; i++) {
                double along = marks.edgeAlong[i];
                Vec3 a = edge[i];
                float in = smooth((since - 4.0 - 30.0 * along / length) / 4.0);
                float passed = this.passed(t, along);
                if (in <= 0.0F || passed <= 0.0F || !land.has(a.x, a.z)) {
                    continue;
                }
                float k = in * passed * gone * fade(cam, a, range);
                this.segment(out, a, edge[i + 1], 0.6, 0.6, ICE, 0.7F * k);
                if (i % 4 == 0) {
                    out.billboard(a.add(0.0, 0.9, 0.0), 1.0, Shapes.GLOW, 0, ICE, 0.6F * k, 0.0);
                }
            }
        }

        // the touchdown point, marked once the corridor reaches it
        float touchdownIn = smooth((since - 34.0) / 6.0) * (1.0F - smooth((t - HalleyPlan.TOUCHDOWN) / 2.0));
        if (touchdownIn > 0.0F && land.has(marks.touchdown.x, marks.touchdown.z)) {
            double r = this.plan.trenchHalfWidth(length) * 1.25 / 0.86;
            float f = touchdownIn * fade(cam, marks.touchdown, range);
            out.flat(marks.touchdown, towardMark, r, r, Shapes.RING, 1, ICE, 0.6F * f);
            out.flat(marks.touchdown, towardMark, 5.0, 5.0, Shapes.RETICLE, 0, PALE, 0.6F * f);
            // the land under the comet lights up as it comes down
            float lit = smooth((t - 262.0) / 38.0);
            out.flat(marks.touchdown, towardMark, 46.0, 46.0, Shapes.DISC, 0, ICE, 0.32F * lit * f);
        }
    }

    /** 1 until the nucleus reaches {@code along} on its way to the mark, then quickly 0. */
    private float passed(double t, double along) {
        double when = HalleyPlan.TOUCHDOWN + (this.plan.params.trenchLength() - along) / HalleyPlan.PLOUGH_SPEED;
        return 1.0F - smooth((t - when) / 3.0);
    }

    // ---- The approach: sighting, the tails, the entry. -------------------------------------------------------------

    private void approach(GlowBatch world, GlowBatch light, double t, double boost) {
        Vec3 head = this.plan.comet(t);
        double d = head.distanceTo(world.camera());
        float on = smooth((t - HalleyPlan.SIGHT) / 30.0);
        float near = (float) Mth.clamp(1.0 - d / 5000.0, 0.0, 1.0);
        double coma = Math.max(COMA, d * COMA_MIN_ANGLE * boost);
        double nucleus = Math.max(NUCLEUS, d * NUCLEUS_MIN_ANGLE * boost);
        double glint = Math.max(COMA * 1.2, d * GLINT_MIN_ANGLE * boost);

        // the tails, which thin out once the comet is inside the atmosphere
        float tails = on * (1.0F - smooth((t - (HalleyPlan.ENTRY - 10)) / 35.0));
        if (tails > 0.003F) {
            double ion = Math.max(ION_LENGTH, d * ION_MIN_SHARE);
            double dust = Math.max(DUST_LENGTH, d * DUST_MIN_SHARE);
            Vec3 bend = this.dustBend.scale(dust / DUST_LENGTH);
            world.ribbon(head, head.add(this.ionDir.scale(ion)), Vec3.ZERO, coma * 0.6, coma * 0.6 + ion * 0.1, 24,
                Shapes.STREAK, Shapes.ION, ION, 0.9F * tails);
            world.ribbon(head, head.add(this.ionDir.scale(ion * 0.9)), Vec3.ZERO, coma * 0.25, coma * 0.25 + ion * 0.03, 24,
                Shapes.STREAK, Shapes.ION, PALE, 0.8F * tails);
            world.ribbon(head, head.add(this.ionDir2.scale(ion * 0.75)), Vec3.ZERO, coma * 0.3, coma * 0.3 + ion * 0.05, 16,
                Shapes.STREAK, Shapes.ION, ICE, 0.45F * tails);
            world.ribbon(head, head.add(this.dustDir.scale(dust)), bend, coma * 0.9, coma * 0.9 + dust * 0.2, 24,
                Shapes.STREAK, Shapes.DUST, DUST, 0.6F * tails);
        }

        // the coma and the nucleus
        world.billboard(head, coma * 2.3, Shapes.GLOW, 0, ICE, (0.6F + 0.2F * near) * on, 0.0);
        world.billboard(head, coma, Shapes.GLOW, 0, PALE, 0.9F * on, 0.0);
        world.billboard(head, nucleus, Shapes.GLOW, 0, WHITE, on, 0.0);
        world.billboard(head, glint, Shapes.GLINT, 0, PALE, (0.6F + 0.35F * near) * on, this.spin + t * 0.004);
        // a line of glare across the screen through it, as a bright light makes in a camera lens
        world.rect(head, coma * 7.0, coma * 0.24, Shapes.FLARE, 0, ION, 0.22F * on * (0.5F + 0.5F * near));

        // entry: the air in front of it turns to plasma
        double since = t - HalleyPlan.ENTRY;
        float sheath = smooth((since + 8.0) / 14.0);
        if (sheath > 0.0F) {
            Vec3 heading = this.plan.heading(t);
            double angle = world.screenAngle(head, heading);
            light.billboard(head.add(heading.scale(coma * 0.3)), coma * 1.5, Shapes.BOW, 0, HEAT, 0.55F * sheath, angle);
            light.billboard(head, coma * 1.2, Shapes.BOW, 0, PALE, 0.35F * sheath, angle);
            light.billboard(head, coma * 1.8, Shapes.GLOW, 0, HEAT, 0.4F * sheath, 0.0);
            world.rect(head, coma * 12.0, coma * 0.35, Shapes.FLARE, 0, HEAT, 0.35F * sheath);

            // the fireball's trail: the stretch of sky it has burned through since entry
            double flown = Math.max(1.0, this.entryDistance - HalleyPlan.distanceToGo(t));
            Vec3 back = head.add(this.plan.incoming.scale(flown));
            world.ribbon(head, back, Vec3.ZERO, coma * 0.9, coma * 0.9 + flown * 0.12, 24, Shapes.STREAK, Shapes.DUST, HEAT, 0.6F * sheath);
            world.ribbon(head, back, Vec3.ZERO, coma * 0.7, coma * 0.7 + flown * 0.06, 24, Shapes.STREAK, Shapes.PLASMA, PALE, 0.9F * sheath);
            world.ribbon(head, back, Vec3.ZERO, coma * 0.3, coma * 0.3 + flown * 0.025, 24, Shapes.STREAK, Shapes.ION, WHITE, 0.8F * sheath);
        }
        // the sonic boom: a ring of condensation thrown off as it goes through the sound barrier
        if (since >= 0.0 && since < 14.0) {
            double k = since / 14.0;
            light.billboard(head, coma * (1.5 + 7.0 * Math.pow(k, 0.6)), Shapes.SHOCK, 1, PALE, 0.5F * (float) Math.pow(1.0 - k, 1.5), 0.0);
        }
    }

    /** After touchdown: the path it came down, still burning in the sky, then a fading vapour trail. */
    private void skyTrail(GlowBatch out, double t) {
        double since = t - HalleyPlan.TOUCHDOWN;
        float bright = (float) Math.exp(-since / 45.0);
        float haze = (float) Math.exp(-since / 110.0);
        if (haze < 0.01F) {
            return;
        }
        Vec3 from = this.plan.touchdown.add(0.0, 3.0, 0.0);
        Vec3 to = this.plan.touchdown.add(this.plan.incoming.scale(this.entryDistance));
        out.ribbon(from, to, Vec3.ZERO, 9.0, 70.0, 24, Shapes.STREAK, Shapes.PLASMA, HEAT, 0.8F * bright);
        out.ribbon(from, to, Vec3.ZERO, 4.0, 30.0, 24, Shapes.STREAK, Shapes.ION, PALE, 0.7F * bright);
        out.ribbon(from, to, new Vec3(0.0, -40.0, 0.0), 20.0, 150.0, 24, Shapes.STREAK, Shapes.DUST, VAPOUR, 0.4F * haze);
    }

    // ---- Touchdown and the plough. ---------------------------------------------------------------------------------

    private void touchdown(GlowBatch world, GlowBatch light, double t, Land land) {
        double since = t - HalleyPlan.TOUCHDOWN;
        if (since > 30.0) {
            return;
        }
        Vec3 p = this.plan.touchdown;
        double width = this.plan.trenchHalfWidth(this.plan.params.trenchLength());
        double k = since / 30.0;
        // the blast where it struck: a flash, a ring racing out over the land, a spout of ice thrown up
        light.billboard(p.add(0.0, 8.0, 0.0), 70.0 * (1.0 + k), Shapes.GLOW, 0, PALE, 1.4F * (float) Math.exp(-since / 4.0), 0.0);
        light.rect(p.add(0.0, 8.0, 0.0), 900.0, 12.0, Shapes.FLARE, 0, PALE, 1.0F * (float) Math.exp(-since / 5.0));
        if (land.has(p.x, p.z)) {
            double ring = 6.0 + width * 5.0 * Math.sqrt(k);
            for (int i = 0; i < 3; i++) {
                world.flat(p.add(0.0, 0.6 + i * 3.0, 0.0), this.plan.dir, ring / 0.9, ring / 0.9, Shapes.SHOCK, i, PALE,
                    (float) Math.pow(1.0 - k, 1.5) * (i == 0 ? 0.9F : i == 1 ? 0.45F : 0.2F));
            }
        }
        world.column(p.add(0.0, -2.0, 0.0), 30.0 + 90.0 * Math.sqrt(k), width * 0.6, Shapes.STREAK, Shapes.DUST, FROST,
            0.8F * (float) Math.pow(1.0 - k, 1.2));
    }

    private void plough(GlowBatch world, GlowBatch light, double t, Land land) {
        Vec3 head = this.plan.comet(t);
        double along = this.plan.frontAlong(t);
        Vec3 heading = this.plan.heading(t);
        double angle = light.screenAngle(head, heading);
        float flicker = 0.9F + 0.1F * (float) Math.sin(t * 2.7);

        // the nucleus, ploughing: seen over the trench's walls, but not through the caster standing in front of it
        double pull = 45.0;
        world.billboardPulled(head, pull, 24.0, Shapes.GLOW, 0, ICE, 0.55F * flicker, 0.0);
        world.billboardPulled(head, pull, 10.0, Shapes.GLOW, 0, PALE, 0.9F * flicker, 0.0);
        world.billboardPulled(head, pull, 34.0, Shapes.GLINT, 0, PALE, 0.6F, this.spin + t * 0.05);
        world.billboardPulled(head.add(heading.scale(4.0)), pull, 13.0, Shapes.BOW, 0, PALE, 0.4F, angle);

        // its wake back down the trench
        double behind = Math.min(this.plan.ploughed(t) + 10.0, 110.0);
        double tailAlong = Math.min(along + behind, this.plan.params.trenchLength() + 10.0);
        Vec3 tailGround = this.plan.groundPoint(tailAlong);
        Vec3 tail = new Vec3(tailGround.x, head.y + 4.0, tailGround.z);
        world.ribbon(head, tail, new Vec3(0.0, 6.0, 0.0), 16.0, 34.0, 16, Shapes.STREAK, Shapes.PLASMA, PALE, 0.85F);
        world.ribbon(head, tail, Vec3.ZERO, 7.0, 12.0, 16, Shapes.STREAK, Shapes.ION, ICE, 0.9F);

        // the ground lit round it
        if (land.has(head.x, head.z)) {
            double hw = this.plan.trenchHalfWidth(along);
            Vec3 ground = new Vec3(head.x, this.floorY(along), head.z);
            world.flat(ground, this.plan.dir, hw * 1.7, hw * 1.7, Shapes.DISC, 0, ICE, 0.45F);
            world.flat(ground.add(0.0, 1.5, 0.0), this.plan.dir, hw * 2.6, hw * 2.6, Shapes.GLOW, Shapes.HAZE, PALE, 0.25F);
        }
    }

    /** The trench floor, glowing where the nucleus scored it, cooling behind it. */
    private void trenchGlow(GlowBatch out, double t, Land land) {
        if (t > this.plan.impact + 130.0) {
            return;
        }
        float cool = 1.0F - smooth((t - (this.plan.impact + 50.0)) / 80.0);
        int length = this.plan.params.trenchLength();
        double head = this.plan.frontAlong(Math.min(t, this.plan.impact));
        for (double a = length; a > head; a -= TRENCH_STEP) {
            double middle = a - TRENCH_STEP * 0.5;
            double age = t - (HalleyPlan.TOUCHDOWN + (length - middle) / HalleyPlan.PLOUGH_SPEED);
            Vec3 p = this.plan.groundPoint(middle);
            if (age < 0.0 || !land.has(p.x, p.z)) {
                continue;
            }
            float glow = (float) (0.85 * Math.exp(-age / 26.0) + 0.2 * Math.exp(-age / 160.0)) * cool;
            Vec3 at = new Vec3(p.x, this.floorY(middle) + 0.08, p.z);
            double hw = this.plan.trenchHalfWidth(middle);
            this.dash(out, at, this.plan.dir, TRENCH_STEP * 0.7, hw * 0.42, ICE, glow);
        }
    }

    /** The top of the trench floor on its centre line, as the server cuts it. */
    private double floorY(double along) {
        Vec3 p = this.plan.groundPoint(along);
        if (!this.plan.params.carve()) {
            return p.y;
        }
        int floor = this.plan.trenchFloor(Mth.floor(p.x), Mth.floor(p.z), this.minY);
        return floor == HalleyPlan.UNTOUCHED ? this.plan.trenchFloorY(along) : floor + 1.0;
    }

    // ---- The detonation. ------------------------------------------------------------------------------------------

    private void detonation(GlowBatch world, GlowBatch light, double t, Land land) {
        double a = t - this.plan.impact;
        double radius = this.plan.params.craterRadius();
        Vec3 c = this.plan.target;

        // the flash, and the fireball of vaporised ice
        if (a < 40.0) {
            light.billboard(c.add(0.0, radius * 0.3, 0.0), radius * 3.0, Shapes.GLOW, 0, PALE, 1.6F * (float) Math.exp(-a / 4.0), 0.0);
            light.rect(c.add(0.0, radius * 0.3, 0.0), radius * 26.0, radius * 0.32, Shapes.FLARE, 0, PALE, 1.3F * (float) Math.exp(-a / 6.0));
            double ball = radius * (0.35 + 0.95 * (1.0 - Math.exp(-a / 5.0)));
            Vec3 middle = c.add(0.0, ball * 0.35, 0.0);
            light.billboard(middle, ball, Shapes.DISC, 0, PALE, 0.9F * (float) Math.exp(-a / 9.0), 0.0);
            light.billboard(middle, ball * 1.6, Shapes.GLOW, 0, ICE, 0.8F * (float) Math.exp(-a / 14.0), 0.0);
        }

        // the plume: ice vapour thrown straight up, spreading into a glowing cloud over the crater as it cools
        if (a < 200.0) {
            double height = radius * (1.2 + 4.8 * (1.0 - Math.exp(-a / 9.0)));
            world.column(c.add(0.0, -2.0, 0.0), height, radius * (0.22 + 0.25 * (1.0 - Math.exp(-a / 20.0))),
                Shapes.STREAK, Shapes.DUST, FROST, 0.9F * (float) Math.exp(-a / 60.0));
            world.column(c, height * 0.8, radius * 0.1, Shapes.STREAK, Shapes.ION, PALE, 0.9F * (float) Math.exp(-a / 18.0));
            float cloud = smooth((a - 4.0) / 20.0) * (float) Math.exp(-Math.max(0.0, a - 30.0) / 70.0);
            for (int i = 0; i < 5; i++) {
                double ang = this.spin * 3.0 + i * 1.2566;
                double spread = radius * (0.25 + 0.55 * (1.0 - Math.exp(-a / 40.0)));
                Vec3 puff = c.add(Math.cos(ang) * spread, radius * (0.45 + 0.12 * i) + a * 0.12, Math.sin(ang) * spread);
                world.billboard(puff, radius * (0.45 + 0.003 * a), Shapes.GLOW, Shapes.HAZE, i % 2 == 0 ? FROST : PALE, 0.12F * cloud, 0.0);
            }
        }

        if (!land.has(c.x, c.z)) {
            return;
        }
        // the shock front over the land, keeping pace with the server's blast
        double reach = radius * this.plan.params.blastReach();
        if (a < BLAST_TICKS + 14.0) {
            double k = Math.pow(Mth.clamp(a / BLAST_TICKS, 0.0, 1.0), 0.6);
            double front = radius + (reach - radius) * k + Math.max(0.0, a - BLAST_TICKS) * 0.6;
            float s = (1.0F - smooth((a - 6.0) / (BLAST_TICKS + 8.0))) * smooth(a / 1.5);
            for (int i = 0; i < 3; i++) {
                world.flat(c.add(0.0, 0.7 + i * 3.0, 0.0), this.plan.dir, front / 0.9, front / 0.9, Shapes.SHOCK, i, PALE,
                    s * (i == 0 ? 1.0F : i == 1 ? 0.35F : 0.15F));
            }
            // a first, faster ring at the crater's edge
            double rim = radius * (0.3 + 0.8 * Math.sqrt(Mth.clamp(a / 6.0, 0.0, 1.0)));
            world.flat(c.add(0.0, 1.0, 0.0), this.plan.dir, rim / 0.9, rim / 0.9, Shapes.SHOCK, 2, WHITE,
                0.8F * (1.0F - smooth(a / 10.0)));
        }

        // frost settling round the rim
        float frost = smooth((a - 4.0) / 18.0) * (1.0F - 0.6F * smooth((a - 60.0) / 120.0))
            * (1.0F - smooth((t - (this.plan.duration - 24.0)) / 24.0));
        double r = radius * 1.12 / 0.86;
        world.flat(c.add(0.0, 0.6, 0.0), this.plan.dir, r, r, Shapes.RING, 2, FROST, 0.5F * frost);
    }

    /** The comet heart, left standing in the crater, glowing. */
    private void heart(GlowBatch out, double t) {
        if (this.heartBase == null || this.heartTip == null) {
            return;
        }
        double since = t - this.plan.impact;
        float on = smooth((since - 2.0) / 16.0) * (1.0F - smooth((t - (this.plan.duration - 30.0)) / 30.0));
        if (on <= 0.0F) {
            return;
        }
        float pulse = 0.5F + 0.5F * (float) Math.sin(t * 0.21);
        double s = this.heartSize;
        Vec3 middle = this.heartBase.add(0.0, 13.0 * s, 0.0);
        // the crystal glows by itself (its blocks are lit); this is the haze of light round it
        out.billboard(middle, 30.0 * s, Shapes.GLOW, Shapes.HAZE, ICE, (0.3F + 0.12F * pulse) * on, 0.0);
        out.billboard(middle, 64.0 * s, Shapes.GLOW, Shapes.HAZE, ION, 0.16F * on, 0.0);
        out.billboard(this.heartTip, 16.0 * s, Shapes.GLINT, 0, PALE, (0.2F + 0.2F * pulse) * on, this.spin + t * 0.01);
        out.flat(this.heartBase.add(0.0, 0.6, 0.0), this.plan.dir, 26.0 * s, 26.0 * s, Shapes.DISC, 0, ICE, 0.16F * on);
    }

    // ---- Helpers. -------------------------------------------------------------------------------------------------

    /** A soft bar lying on the ground along {@code direction} (the DASH shape is long across its uv x). */
    private void dash(GlowBatch out, Vec3 centre, Vec3 direction, double halfLength, double halfThickness, int rgb, float strength) {
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        double l = flat.length();
        if (l < 1.0E-6) {
            return;
        }
        Vec3 d = flat.scale(1.0 / l);
        out.flat(centre, new Vec3(-d.z, 0.0, d.x), halfThickness, halfLength, Shapes.DASH, 0, rgb, strength);
    }

    /** A dash from {@code a} toward {@code b}, covering {@code fill} of the way (the rest is the gap to the next). */
    private void segment(GlowBatch out, Vec3 a, Vec3 b, double fill, double halfThickness, int rgb, float strength) {
        Vec3 along = b.subtract(a);
        Vec3 middle = a.add(b).scale(0.5);
        this.dash(out, middle, along, along.horizontalDistance() * fill * 0.5, halfThickness, rgb, strength);
    }

    /** Ground marks fade out where the land itself stops being drawn. */
    private static float fade(Vec3 camera, Vec3 at, double range) {
        return 1.0F - smooth((camera.distanceTo(at) - range) / 48.0);
    }

    public static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }

    private static double sq(double x) {
        return x * x;
    }
}
