package dev.ss05.halley.client.render;

import dev.ss05.halley.HalleyPlan;
import java.util.SplittableRandom;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The spectacle on top of {@link CometVisuals}: fragments that break off the comet as it enters the air and burst in
 * the sky, sparks shed by the fireball, ice thrown out of the crater on long arcs, cracks racing out across the land,
 * the wall of snow the shock front throws up, the heart's beam into the sky, and ice glittering in the air afterwards.
 *
 * <p>Everything is decided up front from the strike's seed, so every client sees the same fragments burst in the same
 * places. {@code HalleyFx} reads {@link #fragments} and {@link #shards} to give them their sounds and particles.
 *
 * <p>Tuning: counts and sizes are the constants at the top.
 */
public final class CometExtras {
    /** A piece of the comet: it breaks off at {@code splitAt}, drifts away from it and bursts at {@code burstAt}. */
    public record Fragment(double splitAt, double burstAt, double lead, Vec3 drift, double size) {
        public Vec3 at(HalleyPlan plan, double t) {
            double k = Mth.clamp(t, this.splitAt, this.burstAt);
            return plan.comet(Math.min(k + this.lead, HalleyPlan.TOUCHDOWN)).add(this.drift.scale(k - this.splitAt));
        }
    }

    /** A chunk of ice thrown out of the crater on a ballistic arc. */
    public record Shard(double launchAt, Vec3 launch, Vec3 velocity, double flight, double size, double spin) {
        public Vec3 at(double a) {
            return this.launch.add(this.velocity.x * a, this.velocity.y * a - 0.5 * GRAVITY * a * a, this.velocity.z * a);
        }

        public Vec3 landing() {
            return this.at(this.flight);
        }

        public double landsAt() {
            return this.launchAt + this.flight;
        }
    }

    /** A crack in the ground: which way it runs from the rim, how long it is and how it wanders. */
    private record Crack(double angle, double length, double[] bends, double width) {
    }

    // ---- Tuning. ---------------------------------------------------------------------------------------------------
    private static final int FRAGMENTS = 9;
    private static final int SPARKS = 44;
    private static final int SHARDS = 30;
    private static final int CRACKS = 13;
    private static final int CRACK_PIECES = 9;
    private static final int WALL_PIECES = 64;
    private static final int BEAM_MOTES = 10;
    /** Blocks per tick per tick, for the ice thrown out of the crater. */
    static final double GRAVITY = 0.08;
    /** Ticks the shock front takes to cross the land (the same as CometVisuals and the server). */
    private static final int BLAST_TICKS = 28;
    /** The glittering ice in the air: one glint per cell of this size round the camera, about half the cells. */
    private static final double GLITTER_CELL = 4.5;

    public final Fragment[] fragments;
    public final Shard[] shards;
    private final Crack[] cracks;
    private final double[] sparkLife;
    private final Vec3[] sparkDrift;
    private final HalleyPlan plan;

    public CometExtras(HalleyPlan plan) {
        this.plan = plan;
        SplittableRandom r = new SplittableRandom(plan.seed ^ 0x5EED_F4A6_1E57L);

        this.fragments = new Fragment[FRAGMENTS];
        for (int i = 0; i < FRAGMENTS; i++) {
            double split = HalleyPlan.ENTRY - 50.0 + r.nextDouble() * 75.0;
            double lead = -4.0 + r.nextDouble() * 6.0;
            double burst = Math.min(split + 10.0 + r.nextDouble() * 20.0, HalleyPlan.TOUCHDOWN - 4.0 - Math.max(lead, 0.0));
            // they peel away to either side and drop faster than the comet, so they're seen on their own
            double sideways = (r.nextBoolean() ? 1.0 : -1.0) * (6.0 + r.nextDouble() * 9.0);
            burst = Math.max(burst, split + 6.0);
            // ...but always burst well up in the air
            double room = plan.comet(Math.min(burst + lead, HalleyPlan.TOUCHDOWN)).y - (plan.params.touchdownGround() + 35.0);
            double down = Math.max(-4.0 + r.nextDouble() * 5.0, -Math.max(0.0, room) / (burst - split));
            Vec3 drift = plan.side.scale(sideways).add(0.0, down, 0.0);
            this.fragments[i] = new Fragment(split, burst, lead, drift, 3.0 + r.nextDouble() * 4.0);
        }

        this.sparkLife = new double[SPARKS];
        this.sparkDrift = new Vec3[SPARKS];
        for (int i = 0; i < SPARKS; i++) {
            this.sparkLife[i] = 10.0 + r.nextDouble() * 10.0;
            this.sparkDrift[i] = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian()).scale(1.2 + r.nextDouble() * 2.5);
        }

        double radius = plan.params.craterRadius();
        this.shards = new Shard[SHARDS];
        for (int i = 0; i < SHARDS; i++) {
            double angle = r.nextDouble() * Math.PI * 2.0;
            double from = Math.sqrt(r.nextDouble()) * radius * 0.35;
            Vec3 launch = plan.target.add(Math.cos(angle) * from, 2.0 + r.nextDouble() * 4.0, Math.sin(angle) * from);
            double heading = angle + r.nextGaussian() * 0.3;
            double out = 0.9 + r.nextDouble() * 1.8;
            double up = 1.6 + r.nextDouble() * 2.0;
            double drop = launch.y - (plan.target.y - 1.0);
            double flight = (up + Math.sqrt(up * up + 2.0 * GRAVITY * drop)) / GRAVITY;
            this.shards[i] = new Shard(plan.impact + r.nextDouble() * 5.0, launch, new Vec3(Math.cos(heading) * out, up, Math.sin(heading) * out),
                flight, 0.8 + r.nextDouble() * 1.6, r.nextDouble() * Math.PI);
        }

        this.cracks = new Crack[CRACKS];
        int made = 0;
        for (int i = 0; made < CRACKS && i < CRACKS * 4; i++) {
            double angle = r.nextDouble() * Math.PI * 2.0;
            // none along the trench, which comes in on that side
            if (Math.abs(Mth.wrapDegrees(Math.toDegrees(angle))) < 32.0) {
                continue;
            }
            double[] bends = new double[CRACK_PIECES];
            for (int k = 0; k < CRACK_PIECES; k++) {
                bends[k] = r.nextGaussian() * 0.32;
            }
            this.cracks[made++] = new Crack(angle, radius * (0.7 + r.nextDouble() * 1.1), bends, 0.35 + r.nextDouble() * 0.35);
        }
    }

    /**
     * Draws the extras at time {@code t}.
     *
     * @param glitter how much ice glitters in the air round the camera (the haze after the impact, 0..1)
     * @param heartTip the top of the comet heart, or null when there is none
     */
    public void draw(GlowBatch world, GlowBatch light, double t, CometVisuals.Land land, float glitter, @Nullable Vec3 heartTip) {
        if (t >= HalleyPlan.ENTRY - 50.0 && t <= HalleyPlan.TOUCHDOWN + 40.0) {
            this.fragments(world, light, t);
        }
        if (t >= HalleyPlan.ENTRY - 8.0 && t <= HalleyPlan.TOUCHDOWN + 24.0) {
            this.sparks(world, t);
        }
        if (t >= this.plan.impact) {
            this.shards(world, t);
            this.cracks(world, t, land);
            this.shockWall(world, t, land);
            if (heartTip != null) {
                this.beam(world, t, heartTip);
            }
        }
        if (glitter > 0.003F) {
            this.glitter(world, t, glitter);
        }
    }

    // ---- In the sky. -----------------------------------------------------------------------------------------------

    private void fragments(GlowBatch world, GlowBatch light, double t) {
        Vec3 camera = world.camera();
        for (int i = 0; i < this.fragments.length; i++) {
            Fragment f = this.fragments[i];
            if (t < f.splitAt) {
                continue;
            }
            if (t < f.burstAt) {
                Vec3 head = f.at(this.plan, t);
                double size = Math.max(f.size, head.distanceTo(camera) * 0.004);
                float in = CometVisuals.smooth((t - f.splitAt) / 4.0);
                Vec3 back = f.at(this.plan, Math.max(f.splitAt, t - 5.0));
                if (back.distanceToSqr(head) > 1.0) {
                    world.ribbon(head, back, Vec3.ZERO, size * 0.9, size * 0.3, 8, Shapes.STREAK, Shapes.PLASMA, CometVisuals.HEAT, 0.8F * in);
                    world.ribbon(head, back, Vec3.ZERO, size * 0.4, size * 0.1, 8, Shapes.STREAK, Shapes.ION, CometVisuals.PALE, 0.8F * in);
                }
                world.billboard(head, size * 2.2, Shapes.GLOW, 0, CometVisuals.PALE, 0.9F * in, 0.0);
                world.billboard(head, size * 4.0, Shapes.GLOW, Shapes.HAZE, CometVisuals.HEAT, 0.35F * in, 0.0);
                continue;
            }
            double a = t - f.burstAt;
            if (a > 40.0) {
                continue;
            }
            // the burst: a flash, a ring of shocked air, a line of glare across the screen, sparks raining down
            Vec3 at = f.at(this.plan, f.burstAt);
            double size = Math.max(f.size, at.distanceTo(camera) * 0.004);
            light.billboard(at, size * 16.0 * (1.0 + a * 0.05), Shapes.GLOW, 0, CometVisuals.PALE, 1.2F * (float) Math.exp(-a / 2.5), 0.0);
            light.billboard(at, size * 9.0, Shapes.GLOW, Shapes.HAZE, CometVisuals.HEAT, 0.6F * (float) Math.exp(-a / 8.0), 0.0);
            light.rect(at, size * 45.0, size * 1.1, Shapes.FLARE, 0, CometVisuals.HEAT, 0.7F * (float) Math.exp(-a / 4.0));
            double ring = size * (2.5 + 10.0 * Math.sqrt(a / 30.0));
            world.billboard(at, ring, Shapes.SHOCK, 1, CometVisuals.PALE, 0.3F * (float) Math.pow(Math.max(0.0, 1.0 - a / 30.0), 1.5), 0.0);
            double fall = size / 4.0;
            for (int s = 0; s < 8; s++) {
                double ang = s * 0.785 + i;
                Vec3 v = new Vec3(Math.cos(ang) * 1.6, 0.4 + 0.3 * Math.sin(ang * 3.0 + i), Math.sin(ang) * 1.6).scale(fall);
                Vec3 p = at.add(v.scale(a)).add(0.0, -0.035 * fall * a * a, 0.0);
                world.billboard(p, size * 0.5, Shapes.GLOW, 0, s % 2 == 0 ? CometVisuals.HEAT : CometVisuals.PALE,
                    0.8F * (float) Math.pow(Math.max(0.0, 1.0 - a / 36.0), 1.3), 0.0);
            }
        }
    }

    /** Sparks shed by the fireball as it burns down through the air, left hanging behind it. */
    private void sparks(GlowBatch world, double t) {
        Vec3 camera = world.camera();
        for (int i = 0; i < SPARKS; i++) {
            double born = HalleyPlan.ENTRY - 6.0 + i * 1.3;
            if (born > HalleyPlan.TOUCHDOWN - 1.0 || t < born) {
                continue;
            }
            double a = t - born;
            if (a > this.sparkLife[i]) {
                continue;
            }
            Vec3 at = this.plan.comet(born).add(this.sparkDrift[i].scale(a));
            double size = Math.max(1.4, at.distanceTo(camera) * 0.0022);
            float k = (float) Math.pow(1.0 - a / this.sparkLife[i], 1.4);
            world.billboard(at, size, Shapes.GLOW, 0, i % 3 == 0 ? CometVisuals.PALE : CometVisuals.HEAT, 0.9F * k, 0.0);
        }
    }

    // ---- At the impact. --------------------------------------------------------------------------------------------

    private void shards(GlowBatch world, double t) {
        for (Shard s : this.shards) {
            double a = t - s.launchAt;
            if (a < 0.0 || a > s.flight) {
                continue;
            }
            Vec3 at = s.at(a);
            float k = CometVisuals.smooth(a / 2.0) * (1.0F - CometVisuals.smooth((a - s.flight + 4.0) / 4.0));
            Vec3 back = s.at(Math.max(0.0, a - 6.0));
            if (back.distanceToSqr(at) > 0.25) {
                world.ribbon(at, back, Vec3.ZERO, s.size * 0.7, s.size * 0.15, 6, Shapes.STREAK, Shapes.DUST, CometVisuals.FROST, 0.6F * k);
            }
            world.billboard(at, s.size * 1.8, Shapes.GLOW, 0, CometVisuals.FROST, 0.95F * k, 0.0);
            world.billboard(at, s.size * 3.0, Shapes.GLINT, 0, CometVisuals.PALE, 0.45F * k, s.spin + t * 0.15);
        }
    }

    /** Glowing cracks racing out across the land from the crater's rim, cooling over a few seconds. */
    private void cracks(GlowBatch world, double t, CometVisuals.Land land) {
        double a = t - this.plan.impact;
        if (a > 140.0) {
            return;
        }
        float glow = 0.95F * (1.0F - CometVisuals.smooth((a - 25.0) / 110.0));
        double grow = CometVisuals.smooth(a / 10.0) * CRACK_PIECES;
        for (Crack c : this.cracks) {
            if (c == null) {
                continue;
            }
            Vec3 rim = this.plan.craterRim(c.angle);
            double heading = Math.atan2(rim.z - this.plan.target.z, rim.x - this.plan.target.x);
            double x = rim.x + Math.cos(heading) * 2.0;
            double z = rim.z + Math.sin(heading) * 2.0;
            double step = c.length / CRACK_PIECES;
            Vec3 prev = this.onLand(x, z, land);
            for (int k = 0; k < CRACK_PIECES && k < grow; k++) {
                heading += c.bends[k];
                double share = Math.min(1.0, grow - k);
                x += Math.cos(heading) * step * share;
                z += Math.sin(heading) * step * share;
                Vec3 next = this.onLand(x, z, land);
                if (land.has(x, z)) {
                    float taper = 1.0F - 0.6F * k / CRACK_PIECES;
                    dash(world, prev, next, c.width * taper, CometVisuals.PALE, glow);
                    dash(world, prev, next, c.width * 4.5 * taper, CometVisuals.ICE, glow * 0.3F);
                }
                prev = next;
            }
        }
    }

    /** The wall of snow and light thrown up by the shock front as it races out across the land. */
    private void shockWall(GlowBatch world, double t, CometVisuals.Land land) {
        double a = t - this.plan.impact;
        if (a > BLAST_TICKS + 12.0) {
            return;
        }
        double radius = this.plan.params.craterRadius();
        double reach = radius * this.plan.params.blastReach();
        double k = Math.pow(Mth.clamp(a / BLAST_TICKS, 0.0, 1.0), 0.6);
        double front = radius * 0.6 + (reach - radius * 0.6) * k + Math.max(0.0, a - BLAST_TICKS) * 0.6;
        double height = 8.0 + radius * 0.5 * (1.0 - CometVisuals.smooth(a / 26.0));
        float s = 0.7F * CometVisuals.smooth(a / 2.0) * (1.0F - CometVisuals.smooth((a - 4.0) / (BLAST_TICKS + 8.0)));
        Vec3 c = this.plan.target;
        Vec3 prev = null;
        for (int i = 0; i <= WALL_PIECES; i++) {
            double ang = i * Math.PI * 2.0 / WALL_PIECES;
            double x = c.x + Math.cos(ang) * front;
            double z = c.z + Math.sin(ang) * front;
            Vec3 p = land.has(x, z) ? this.onLand(x, z, land) : null;
            if (prev != null && p != null) {
                world.wall(prev.add(0.0, -1.5, 0.0), p.add(0.0, -1.5, 0.0), height, Shapes.WALL, i % 4, CometVisuals.FROST, s);
            }
            prev = p;
        }
    }

    /** After the impact the heart throws a thin beam of light up into the sky, with motes rising round it. */
    private void beam(GlowBatch world, double t, Vec3 tip) {
        double since = t - this.plan.impact;
        float on = CometVisuals.smooth((since - 14.0) / 22.0) * (1.0F - CometVisuals.smooth((t - (this.plan.duration - 44.0)) / 34.0));
        if (on <= 0.0F) {
            return;
        }
        float pulse = 0.8F + 0.2F * (float) Math.sin(t * 0.21);
        world.column(tip, 420.0, 0.9, Shapes.STREAK, Shapes.ION, CometVisuals.PALE, 0.75F * on * pulse);
        world.column(tip, 260.0, 4.5, Shapes.STREAK, Shapes.DUST, CometVisuals.ICE, 0.22F * on);
        for (int i = 0; i < BEAM_MOTES; i++) {
            double rise = (since * 0.9 + i * 31.0) % 240.0;
            double ang = since * 0.07 + i * 0.628;
            double r = 2.5 + 1.5 * Math.sin(since * 0.05 + i);
            Vec3 p = tip.add(Math.cos(ang) * r, rise, Math.sin(ang) * r);
            world.billboard(p, 0.9, Shapes.GLOW, 0, CometVisuals.PALE, 0.8F * on * (float) (1.0 - rise / 240.0), 0.0);
        }
    }

    /** Ice glittering in the air round the camera once the haze is up: tiny glints that catch the light and fall. */
    private void glitter(GlowBatch world, double t, float strength) {
        Vec3 cam = world.camera();
        int cx = Mth.floor(cam.x / GLITTER_CELL);
        int cy = Mth.floor(cam.y / GLITTER_CELL);
        int cz = Mth.floor(cam.z / GLITTER_CELL);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    long h = mix(this.plan.seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
                    if ((h & 0xFF) > 128) {
                        continue;
                    }
                    double u = (h >>> 8 & 0xFFFF) / 65535.0;
                    double v = (h >>> 24 & 0xFFFF) / 65535.0;
                    double w = (h >>> 40 & 0xFFFF) / 65535.0;
                    double fall = Mth.frac(v - t * 0.004 * (0.6 + u));
                    Vec3 p = new Vec3((x + u) * GLITTER_CELL, (y + fall) * GLITTER_CELL, (z + w) * GLITTER_CELL);
                    double d = p.distanceTo(cam);
                    if (d < 1.2) {
                        continue;
                    }
                    float near = 1.0F - CometVisuals.smooth((d - 13.0) / 8.0);
                    float twinkle = (float) Math.pow(Math.max(0.0, Math.sin(t * (0.12 + 0.2 * u) + w * 40.0)), 10.0);
                    float k = strength * near * (0.1F + 1.1F * twinkle);
                    if (k > 0.01F) {
                        world.billboard(p, 0.05 + 0.08 * w, Shapes.GLINT, 0, CometVisuals.WHITE, k, u * 6.0);
                    }
                }
            }
        }
    }

    // ---- Helpers. ---------------------------------------------------------------------------------------------------

    private Vec3 onLand(double x, double z, CometVisuals.Land land) {
        double y = land.surface(x, z);
        return new Vec3(x, (Double.isNaN(y) ? this.plan.target.y : y) + 0.09, z);
    }

    /** A glowing line lying on the ground from {@code a} to {@code b}. */
    private static void dash(GlowBatch out, Vec3 a, Vec3 b, double halfThickness, int rgb, float strength) {
        Vec3 along = b.subtract(a);
        double l = Math.sqrt(along.x * along.x + along.z * along.z);
        if (l < 1.0E-3) {
            return;
        }
        Vec3 middle = a.add(b).scale(0.5);
        out.flat(middle, new Vec3(-along.z / l, 0.0, along.x / l), halfThickness, l * 0.5 + halfThickness * 0.5, Shapes.DASH, 0, rgb, strength);
    }

    private static long mix(long h) {
        h = (h ^ h >>> 33) * 0xFF51AFD7ED558CCDL;
        h = (h ^ h >>> 33) * 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }
}
