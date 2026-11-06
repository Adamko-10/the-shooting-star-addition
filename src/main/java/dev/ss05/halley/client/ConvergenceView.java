package dev.ss05.halley.client;

import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.Shapes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * SA-00 · Stellar Convergence's own effect, outside any film (the user: "def want"): a sigil over the mark with a
 * segment in each weapon's colour, lit one by one as the weapons are called; in a ring, each weapon's own mark tied to
 * it; lights in every weapon's colour spiralling down onto it in the last seconds; and at the hit, a flash and one
 * shared shock ring in all their colours. Drawn with the comet's glow shapes ({@code CometRenderer}). compat/tss starts
 * one when the server says a convergence is on its way.
 */
public final class ConvergenceView {
    /** Starlight: every weapon's colour mixed toward white (the skill's own colour). */
    private static final int STARLIGHT = 0xE6DCFF;
    /** The last seconds before the hit, when the lights spiral in. */
    private static final double SPIRAL = 60.0;
    /** How long the shock ring runs after the hit. */
    private static final double SHOCK = 60.0;
    private static final List<ConvergenceView> ACTIVE = new ArrayList<>();

    private final Vec3 target;
    private final long startAt;
    private final long hitAt;
    private final int[] colors;
    private final Vec3[] points;
    private final boolean ring;
    private final double radius;

    private ConvergenceView(Vec3 target, long startAt, long hitAt, int[] colors, Vec3[] points, boolean ring) {
        this.target = target.add(0.0, 0.08, 0.0);
        this.startAt = startAt;
        this.hitAt = hitAt;
        this.colors = colors.length == 0 ? new int[]{STARLIGHT} : colors;
        this.points = points;
        this.ring = ring;
        double out = 0.0;
        for (Vec3 p : points) {
            out = Math.max(out, Math.hypot(p.x - target.x, p.z - target.z));
        }
        this.radius = ring ? out + 12.0 : 24.0;
    }

    /** A convergence on its way: everything hits at game time {@code hitAt}. */
    public static void start(Vec3 target, long now, long hitAt, int[] colors, Vec3[] points, boolean ring) {
        ACTIVE.add(new ConvergenceView(target, now, hitAt, colors, points, ring));
    }

    /** The running ones, dropping those that are over ({@code now}: the game time). */
    public static List<ConvergenceView> active(long now) {
        ACTIVE.removeIf(view -> now > view.hitAt + SHOCK + 20 || now < view.startAt - 20);
        return ACTIVE;
    }

    public static boolean any() {
        return !ACTIVE.isEmpty();
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    /** Draws it at game time {@code t} (with the partial tick). */
    public void render(GlowBatch out, double t) {
        double since = t - this.startAt;
        double toHit = this.hitAt - t;
        double after = t - this.hitAt;
        if (after < 4.0) {
            this.sigil(out, since, toHit);
        }
        if (toHit < SPIRAL && after < 2.0) {
            this.spiral(out, toHit);
        }
        if (after >= 0.0) {
            this.shock(out, after);
        }
    }

    private void sigil(GlowBatch out, double since, double toHit) {
        int n = this.colors.length;
        float in = smooth(since / 20.0) * (1.0F - smooth((-toHit) / 4.0));
        // drawn in toward the mark and brighter in the last second
        float close = smooth((20.0 - toHit) / 20.0);
        double r = this.radius * (1.0 - 0.45 * close);
        float bright = 0.55F + 0.45F * close;
        Vec3 axis = new Vec3(1.0, 0.0, 0.0);
        out.flat(this.target, axis, r, r, Shapes.RING, 0, STARLIGHT, 0.5F * in * bright);
        out.flat(this.target, axis, r * 0.9, r * 0.9, Shapes.RING, 1, STARLIGHT, 0.35F * in * bright);
        // a segment of the circle per weapon, lit one after another over the wait, turning slowly
        double spin = since * 0.006;
        double lightEvery = Math.max(4.0, Math.min(20.0, (this.hitAt - this.startAt - 40.0) / n));
        for (int k = 0; k < n; k++) {
            float lit = smooth((since - 6.0 - k * lightEvery) / 10.0) * in;
            if (lit <= 0.0F) {
                continue;
            }
            double from = spin + Math.PI * 2.0 * k / n;
            double span = Math.PI * 2.0 / n * 0.82;
            int dashes = Math.max(3, 24 / n);
            for (int j = 0; j < dashes; j++) {
                double a0 = from + span * j / dashes;
                double a1 = from + span * (j + 0.8) / dashes;
                Vec3 p0 = this.onCircle(a0, r * 0.8);
                Vec3 p1 = this.onCircle(a1, r * 0.8);
                dash(out, p0, p1, 0.55 + 0.25 * close, this.colors[k], 0.95F * lit * bright);
            }
        }
        // the mark, and a light over it beating faster as the moment comes
        out.flat(this.target, axis, 6.0, 6.0, Shapes.RETICLE, 0, 0xFFFFFF, 0.85F * in);
        float beat = 0.7F + 0.3F * (float) Math.sin(since * (0.2 + 0.8 * close));
        out.billboard(this.target.add(0.0, 1.5, 0.0), 2.5 + 2.5 * close, Shapes.GLOW, 0, STARLIGHT, 0.7F * in * beat, 0.0);
        // in a ring: each weapon's own mark, tied to the middle
        if (this.ring) {
            for (int k = 0; k < Math.min(n, this.points.length); k++) {
                float lit = smooth((since - 6.0 - k * lightEvery) / 10.0) * in;
                Vec3 p = this.points[k].add(0.0, 0.08, 0.0);
                out.flat(p, axis, 5.0, 5.0, Shapes.RETICLE, 0, this.colors[k], 0.9F * lit);
                int steps = 8;
                for (int j = 0; j < steps; j++) {
                    Vec3 a = this.target.lerp(p, (double) j / steps);
                    Vec3 b = this.target.lerp(p, (j + 0.6) / steps);
                    dash(out, a, b, 0.35, this.colors[k], 0.6F * lit);
                }
            }
        }
    }

    /** Lights in each weapon's colour spiralling down out of the sky onto the mark (or onto its own spot, in a ring). */
    private void spiral(GlowBatch out, double toHit) {
        double p = Mth.clamp(toHit / SPIRAL, 0.0, 1.0);
        float in = smooth((SPIRAL - toHit) / 10.0);
        int n = this.colors.length;
        for (int k = 0; k < n; k++) {
            Vec3 home = this.ring && k < this.points.length ? this.points[k] : this.target;
            for (int j = 0; j < 3; j++) {
                double angle = Math.PI * 2.0 * (k + j / 3.0) / n + (1.0 - p) * Math.PI * 4.0;
                double r = this.radius * 1.6 * p + 1.0;
                Vec3 at = home.add(Math.cos(angle) * r, 4.0 + 110.0 * p * p, Math.sin(angle) * r);
                out.billboard(at, 1.4 + 2.2 * (1.0 - p), Shapes.GLINT, 0, this.colors[k], 0.95F * in, angle);
                out.billboard(at, 3.5, Shapes.GLOW, Shapes.HAZE, this.colors[k], 0.35F * in, 0.0);
            }
        }
        // a column of starlight standing up out of the mark
        out.column(this.target, 40.0 + 220.0 * (1.0 - p), 0.5 + 1.2 * (1.0 - p), Shapes.STREAK, Shapes.ION, STARLIGHT, 0.7F * in);
    }

    /** At the hit: a flash, and one shock ring rolling out in all the weapons' colours. */
    private void shock(GlowBatch out, double after) {
        float flash = 1.0F - smooth(after / 8.0);
        out.billboard(this.target.add(0.0, 3.0, 0.0), 30.0 * (1.0 + after * 0.3), Shapes.GLOW, 0, 0xFFFFFF, flash, 0.0);
        if (after > SHOCK) {
            return;
        }
        float k = 0.9F * (1.0F - smooth(after / (SHOCK - 10.0)));
        double r = 8.0 + after * 5.0;
        out.flat(this.target, new Vec3(1.0, 0.0, 0.0), r * 0.92, r * 0.92, Shapes.SHOCK, 0, STARLIGHT, 0.5F * k);
        int pieces = 64;
        int n = this.colors.length;
        for (int j = 0; j < pieces; j++) {
            double a0 = Math.PI * 2.0 * j / pieces;
            double a1 = Math.PI * 2.0 * (j + 1) / pieces;
            // round the ring the colours run from one weapon's to the next
            double along = (double) j / pieces * n;
            int c = mix(this.colors[(int) along % n], this.colors[((int) along + 1) % n], (float) (along - Math.floor(along)));
            dash(out, this.onCircle(a0, r), this.onCircle(a1, r), 1.2 + after * 0.04, c, k);
        }
    }

    private Vec3 onCircle(double angle, double r) {
        return this.target.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
    }

    /** A dash lying on the ground from {@code a} to {@code b}. */
    private static void dash(GlowBatch out, Vec3 a, Vec3 b, double halfThickness, int rgb, float strength) {
        Vec3 along = new Vec3(b.x - a.x, 0.0, b.z - a.z);
        double l = along.length();
        if (l < 1.0E-6 || strength <= 0.003F) {
            return;
        }
        Vec3 d = along.scale(1.0 / l);
        out.flat(a.lerp(b, 0.5), new Vec3(-d.z, 0.0, d.x), halfThickness, l * 0.5, Shapes.DASH, 0, rgb, strength);
    }

    private static int mix(int a, int b, float k) {
        int r = Math.round(Mth.lerp(k, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(k, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(k, a & 0xFF, b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    private static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }
}
