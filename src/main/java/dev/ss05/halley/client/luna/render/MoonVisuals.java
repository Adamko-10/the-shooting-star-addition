package dev.ss05.halley.client.luna.render;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.CometVisuals;
import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.Shapes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Everything SS-06 draws in the world as additive glow quads: the gold mark on the ground, the burning shroud and
 * flame streamers of atmospheric entry, fragments breaking off and bursting, the flash and shock ring of the impact,
 * the dust dome, and embers glowing in the aftermath. Pure geometry from a {@link MoonPlan} and a time, drawn with
 * the same {@code halley_glow} shader and shapes SS-05's comet uses (see {@code client/render/Shapes}), so no new
 * shader or atlas is needed; only the tint differs (warm fire colours instead of Halley's ice).
 *
 * <p>Two batches come in, exactly as for SS-05: {@code world} is hidden behind terrain, {@code light} is not (only
 * for the brief, blinding moments whose glare would otherwise be cut off sharply at the ground).
 */
public final class MoonVisuals {
    // ---- Colours. -------------------------------------------------------------------------------------------------
    static final int HOT = 0xFFE9B0;
    static final int FIRE = 0xFF8A2E;
    static final int DEEP = 0xB33018;
    static final int WHITE = 0xFFFFFF;
    static final int ASH = 0x8A7A6C;
    static final int GOLD = 0xFFC93C;

    private static final int FRAGMENTS = 6;

    private MoonVisuals() {
    }

    /**
     * Maps a point (or a size) attached to the moon's true position into the same pulled/exaggerated placement the
     * sphere itself is drawn at ({@link MoonSphere#placement}), so its burning shroud, streamers and fragments never
     * part ways with the body they're coming off. {@code trueCentre} and {@code scale} are this frame's only.
     */
    private record Mapping(Vec3 trueCentre, Vec3 displayCentre, double scale) {
        Vec3 at(Vec3 truePoint) {
            return this.displayCentre.add(truePoint.subtract(this.trueCentre).scale(this.scale));
        }

        double size(double trueSize) {
            return trueSize * this.scale;
        }
    }

    public static void draw(MoonFx fx, MoonSphere.Placement placement, GlowBatch world, GlowBatch light, double t,
                            CometVisuals.Land land, float film) {
        MoonPlan p = fx.plan;
        if (t < 0.0 || t > MoonPlan.DURATION) {
            return;
        }
        mark(world, p, t, land);
        double trueRadius = Math.max(1.0E-6, p.params.moonRadius());
        Mapping map = new Mapping(p.centre(t), placement.centre(), placement.radius() / trueRadius);
        if (t >= MoonPlan.BREAK && t < MoonPlan.CONTACT + 10.0) {
            // always-on, so it reads against the night long before it starts burning: a luminous halo and a
            // compression glow building on the leading face as it nears the atmosphere
            halo(world, p, t, map);
        }
        if (t >= MoonPlan.ENTRY - 30.0 && t <= MoonPlan.CONTACT + 10.0) {
            entry(world, light, p, t, map);
            fragments(world, light, p, t, map);
        }
        if (t >= MoonPlan.CONTACT) {
            detonation(world, light, p, t, land);
            cracks(world, p, t, land);
        }
        if (t >= MoonPlan.SETTLE) {
            embers(world, p, t, fx.sphereVisibilityAt(t));
            handoverGlow(world, fx, p, t);
        }
    }

    /** How many short radial hatch ticks mark each blast-zone ring (a "marching" animated look, like a target lock). */
    private static final int HATCH_COUNT = 48;

    /** The gold mark on the ground, from the alarm until the impact - with the erase radius and the much bigger
     * blast radius drawn round it as bright, animated, hatched rings (like SS-05's ground marks), so the blast
     * zone is obvious and visible from far off, long before the moon itself is close. */
    private static void mark(GlowBatch out, MoonPlan p, double t, CometVisuals.Land land) {
        if (t < MoonPlan.ALARM || t > MoonPlan.CONTACT + 2.0 || !land.has(p.target.x, p.target.z)) {
            return;
        }
        float in = CometVisuals.smooth((t - MoonPlan.ALARM) / 10.0) * (1.0F - CometVisuals.smooth((t - (MoonPlan.CONTACT - 4.0)) / 6.0));
        if (in <= 0.0F) {
            return;
        }
        Vec3 at = p.target.add(0.0, 0.08, 0.0);
        double size = 8.0 * (1.0 + 1.6 * (1.0 - CometVisuals.smooth((t - MoonPlan.ALARM) / 20.0)));
        out.flat(at, new Vec3(1, 0, 0), size, size, Shapes.RETICLE, 0, GOLD, 0.9F * in);
        out.flat(at, new Vec3(1, 0, 0), 2.4, 2.4, Shapes.GLOW, 0, GOLD, 0.5F * in);
        float beat = 0.7F + 0.3F * (float) Math.sin(t * 0.3);
        out.billboard(at.add(0.0, 1.0, 0.0), 2.6, Shapes.GLOW, 0, GOLD, 0.6F * in * beat, 0.0);

        double eraseR = p.eraseRadius();
        double blastR = p.blastRadius();
        float pulse = 0.65F + 0.35F * (float) Math.sin(t * 0.22);
        float sweep = (float) Mth.frac(t * 0.012);
        out.flat(at, p.travel, eraseR, eraseR, Shapes.RING, 1, GOLD, (0.75F + 0.25F * pulse) * in);
        out.flat(at, p.travel, blastR, blastR, Shapes.RING, 2, FIRE, (0.55F + 0.3F * (1.0F - pulse)) * in);
        ring(out, at, eraseR, HATCH_COUNT, GOLD, 0.9F * in, sweep);
        ring(out, at, blastR, HATCH_COUNT, FIRE, 0.8F * in, -sweep * 0.6F);
    }

    /** A dashed, slowly rotating hatch round a circle on the ground: short radial ticks, half of them lit at a time
     * so the ring reads as "marching" and animated, not a static line - drawn the same way SS-05's trench edges are. */
    private static void ring(GlowBatch out, Vec3 centre, double radius, int count, int rgb, float strength, double phase) {
        double tick = Math.max(1.5, radius * 0.045);
        for (int i = 0; i < count; i++) {
            double k = (double) i / count;
            if (Mth.frac(k * 3.0 + phase) > 0.5) {
                continue;
            }
            double angle = k * Math.PI * 2.0;
            Vec3 axis = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            Vec3 at = centre.add(axis.scale(radius));
            out.flat(at, axis, tick, tick * 0.3, Shapes.DASH, 0, rgb, strength);
        }
    }

    /**
     * Always on while it falls, long before it starts to burn: a luminous rim glow and halo round the body, so it
     * reads against the night sky from far away, plus a faint compression glow building on its leading face as it
     * nears the atmosphere - the moon "crashing down", not just sliding closer.
     */
    private static void halo(GlowBatch world, MoonPlan p, double t, Mapping map) {
        double effR = map.size(p.params.moonRadius());
        if (effR < 0.05) {
            return;
        }
        Vec3 c = map.displayCentre();
        // fades out as the full burn shroud (entry()) takes over, so the two never double up
        float steady = 1.0F - CometVisuals.smooth((t - (MoonPlan.ENTRY - 20.0)) / 30.0);
        if (steady > 0.01F) {
            world.billboard(c, effR * 1.5, Shapes.GLOW, 0, HOT, 0.4F * steady, 0.0);
            world.billboard(c, effR * 2.4, Shapes.GLOW, Shapes.HAZE, GOLD, 0.26F * steady, 0.0);
        }

        double compress = Mth.clamp((t - MoonPlan.BREAK) / Math.max(1.0, MoonPlan.ENTRY - 30.0 - MoonPlan.BREAK), 0.0, 1.0);
        float build = (float) Math.pow(compress, 2.4) * (1.0F - CometVisuals.smooth((t - (MoonPlan.ENTRY - 8.0)) / 24.0));
        if (build > 0.01F) {
            Vec3 heading = MoonFx.headingAt(p, t);
            double angle = world.screenAngle(c, heading);
            world.billboard(c.add(heading.scale(effR * 0.35)), effR * 1.3, Shapes.BOW, 0, FIRE, 0.55F * build, angle);
        }
    }

    /**
     * The burning shroud wrapping the body, a bow-shock cap on its leading face, a halo glow round it, and a long,
     * wide re-entry plasma trail streaming back up the path it fell, visible from far away - everything drawn at the
     * sphere's own {@code map}ped placement (pulled in and cinematically exaggerated, see {@code MoonSphere}), so it
     * never parts ways with the body it's coming off and reads big even when the true moon is still a world away.
     */
    private static void entry(GlowBatch world, GlowBatch light, MoonPlan p, double t, Mapping map) {
        float burn = MoonPlan.burn(t);
        double effR = map.size(p.params.moonRadius());
        if (burn <= 0.003F || effR < 0.05) {
            return;
        }
        Vec3 displayCentre = map.displayCentre();
        Vec3 heading = MoonFx.headingAt(p, t);
        double angle = world.screenAngle(displayCentre, heading);

        // the bow-shock cap, about the moon's own size, on the leading face
        light.billboard(displayCentre.add(heading.scale(effR * 0.55)), effR * 1.5, Shapes.BOW, 0, FIRE, 0.95F * burn, angle);
        light.billboard(displayCentre, effR * 1.15, Shapes.BOW, 0, HOT, 0.65F * burn, angle);
        light.billboard(displayCentre.add(heading.scale(effR * 0.3)), effR * 0.8, Shapes.BOW, 0, WHITE, 0.35F * burn, angle);
        // the plasma sheath wrapping the whole body, and a wide halo glow round it, bright enough to spot at night
        world.billboard(displayCentre, effR * 2.1, Shapes.GLOW, 0, FIRE, 0.75F * burn, 0.0);
        world.billboard(displayCentre, effR * 3.6, Shapes.GLOW, Shapes.HAZE, HOT, 0.5F * burn, 0.0);
        world.rect(displayCentre, effR * 13.0, effR * 0.55, Shapes.FLARE, 0, FIRE, 0.55F * burn);

        // a long, bright, wide trail streaming back up the sky - several more moon diameters than before, so it's
        // seen from far off long before the moon itself is close
        Vec3 back = displayCentre.subtract(heading.scale(effR * 13.0));
        world.ribbon(displayCentre, back, Vec3.ZERO, effR * 1.3, effR * 2.6, 26, Shapes.STREAK, Shapes.DUST, FIRE, 0.75F * burn);
        world.ribbon(displayCentre, back, Vec3.ZERO, effR * 0.8, effR * 1.6, 26, Shapes.STREAK, Shapes.PLASMA, HOT, 0.9F * burn);
        world.ribbon(displayCentre, back, Vec3.ZERO, effR * 0.35, effR * 0.75, 18, Shapes.STREAK, Shapes.ION, WHITE, 0.7F * burn);
    }

    /** Glowing fragments breaking off the moon as it burns through the atmosphere, bursting a few seconds later -
     * their true positions (split and drift) mapped the same way as the sphere, so they peel off the body you see. */
    private static void fragments(GlowBatch world, GlowBatch light, MoonPlan p, double t, Mapping map) {
        for (int i = 0; i < FRAGMENTS; i++) {
            long h = hash(p.seed, i);
            double splitAt = MoonPlan.ENTRY - 10.0 + frac(h) * (MoonPlan.CONTACT - MoonPlan.ENTRY - 20.0);
            double burstAt = Math.min(splitAt + 14.0 + frac(h >>> 16) * 22.0, MoonPlan.CONTACT - 4.0);
            if (t < splitAt) {
                continue;
            }
            Vec3 side = new Vec3(-p.travel.z, 0.0, p.travel.x).scale((frac(h >>> 32) - 0.5) * 2.0);
            Vec3 drift = side.scale(4.0 + frac(h >>> 8) * 6.0).add(0.0, -(2.0 + frac(h >>> 24) * 3.0), 0.0);
            double baseSize = Math.max(0.3, map.size(3.0 + frac(h >>> 40) * 4.0));
            if (t < burstAt) {
                double k = Math.min(t, burstAt) - splitAt;
                Vec3 at = map.at(p.centre(Math.min(t, burstAt)).add(drift.scale(k)));
                float in = CometVisuals.smooth((t - splitAt) / 4.0);
                world.billboard(at, baseSize * 2.0, Shapes.GLOW, 0, HOT, 0.85F * in, 0.0);
                world.billboard(at, baseSize * 3.6, Shapes.GLOW, Shapes.HAZE, FIRE, 0.3F * in, 0.0);
                continue;
            }
            double a = t - burstAt;
            if (a > 36.0) {
                continue;
            }
            Vec3 at = map.at(p.centre(burstAt).add(drift.scale(burstAt - splitAt)));
            light.billboard(at, baseSize * 14.0, Shapes.GLOW, 0, HOT, 1.1F * (float) Math.exp(-a / 3.0), 0.0);
            light.rect(at, baseSize * 36.0, baseSize, Shapes.FLARE, 0, FIRE, 0.6F * (float) Math.exp(-a / 4.0));
            double ring = baseSize * (2.0 + 8.0 * Math.sqrt(a / 30.0));
            world.billboard(at, ring, Shapes.SHOCK, 1, HOT, 0.3F * (float) Math.pow(Math.max(0.0, 1.0 - a / 30.0), 1.4), 0.0);
        }
    }

    /** The flash, the expanding dust dome, the shock ring racing out over the land and glowing debris arcs - far
     * bigger and more devastating than before, kept in step with the server's blast. */
    private static void detonation(GlowBatch world, GlowBatch light, MoonPlan p, double t, CometVisuals.Land land) {
        double a = t - MoonPlan.CONTACT;
        double radius = p.params.craterRadius();
        Vec3 c = p.target;

        if (a < 50.0) {
            light.billboard(c.add(0.0, radius * 0.35, 0.0), radius * 4.6, Shapes.GLOW, 0, WHITE, 2.2F * (float) Math.exp(-a / 5.0), 0.0);
            light.rect(c.add(0.0, radius * 0.35, 0.0), radius * 38.0, radius * 0.45, Shapes.FLARE, 0, HOT, 1.7F * (float) Math.exp(-a / 7.0));
        }
        if (a < 260.0) {
            double height = radius * (1.4 + 5.4 * (1.0 - Math.exp(-a / 11.0)));
            world.column(c.add(0.0, -2.0, 0.0), height, radius * (0.3 + 0.28 * (1.0 - Math.exp(-a / 24.0))),
                Shapes.STREAK, Shapes.DUST, ASH, 0.9F * (float) Math.exp(-a / 85.0));
            world.column(c, height * 0.75, radius * 0.13, Shapes.STREAK, Shapes.ION, FIRE, 0.75F * (float) Math.exp(-a / 24.0));
            // an expanding dome of light rising off the crater in the first instants, over and above the column
            if (a < 36.0) {
                double domeHeight = radius * 0.5 * CometVisuals.smooth(a / 20.0);
                for (int i = 0; i < 3; i++) {
                    double domeR = radius * (0.65 + 0.22 * i) * CometVisuals.smooth(a / 14.0);
                    world.billboard(c.add(0.0, domeHeight * (0.3 + 0.35 * i), 0.0), domeR, Shapes.GLOW, Shapes.HAZE, HOT,
                        0.5F * (float) Math.exp(-a / 16.0) / (1.0F + i), 0.0);
                }
            }
        }
        if (!land.has(c.x, c.z)) {
            return;
        }
        double reach = radius * p.params.blastReach();
        if (a < 50.0) {
            double k = Math.pow(Mth.clamp(a / 32.0, 0.0, 1.0), 0.6);
            double front = radius + (reach - radius) * k + Math.max(0.0, a - 32.0) * 0.7;
            float s = (1.0F - CometVisuals.smooth((a - 6.0) / 40.0)) * CometVisuals.smooth(a / 1.5);
            for (int i = 0; i < 4; i++) {
                world.flat(c.add(0.0, 0.7 + i * 3.2, 0.0), p.travel, front / 0.9, front / 0.9, Shapes.SHOCK, i, HOT,
                    s * (i == 0 ? 1.1F : i == 1 ? 0.5F : i == 2 ? 0.28F : 0.14F));
            }
            // a wall of dust riding just behind the shock front, thrown out all round the crater
            world.flat(c.add(0.0, 0.5, 0.0), p.travel, front * 0.96, front * 0.96, Shapes.RING, 3, ASH, s * 0.5F);
        }
        float frost = CometVisuals.smooth((a - 4.0) / 20.0) * (1.0F - 0.6F * CometVisuals.smooth((a - 70.0) / 140.0))
            * (1.0F - CometVisuals.smooth((t - (MoonPlan.DURATION - 24.0)) / 24.0));
        double r = radius * 1.1 / 0.86;
        world.flat(c.add(0.0, 0.6, 0.0), p.travel, r, r, Shapes.RING, 2, DEEP, 0.5F * frost);
    }

    /**
     * Glowing cracks racing out across the land from the crater's rim along {@link MoonPlan}'s own crack lines
     * (shared with the server, which cuts fissures along exactly these - see {@code CometExtras.cracks}, SS-05's
     * Gungnir-style ground cracks, which this is modelled on), cooling over a few seconds.
     */
    private static void cracks(GlowBatch world, MoonPlan p, double t, CometVisuals.Land land) {
        double a = t - MoonPlan.CONTACT;
        if (a > 150.0 || !land.has(p.target.x, p.target.z)) {
            return;
        }
        float glow = CometVisuals.smooth(a / 6.0) * (1.0F - CometVisuals.smooth((a - 30.0) / 110.0));
        if (glow <= 0.01F) {
            return;
        }
        double front = MoonPlan.crackFront(t);
        int pieces = 10;
        for (int i = 0; i < MoonPlan.CRACKS; i++) {
            Vec3 prev = onLand(p.crackPoint(i, 0.0), land, p);
            for (int k = 1; k <= pieces; k++) {
                double along = front * k / (double) pieces;
                if (along > 1.0) {
                    break;
                }
                Vec3 next = onLand(p.crackPoint(i, along), land, p);
                float taper = 1.0F - 0.55F * (float) along;
                double hw = p.crackHalfWidth(i, along);
                dash(world, prev, next, hw, GOLD, glow * taper);
                dash(world, prev, next, hw * 4.0, FIRE, glow * taper * 0.35F);
                prev = next;
            }
        }
    }

    /**
     * The hand-over from the rendered sphere to the server's block moon, made smooth: a glow round the block moon
     * that flares bright orange-white and cools down to nothing over a few seconds (the fade of the sphere itself,
     * and its own cooling tint, are {@code MoonFx.sphereVisibilityAt} and {@code MoonSphere}'s placement).
     */
    private static void handoverGlow(GlowBatch out, MoonFx fx, MoonPlan p, double t) {
        int handover = fx.handoverAt();
        if (handover < 0) {
            return;
        }
        double since = t - handover;
        if (since < 0.0 || since > MoonFx.COOLING_TICKS) {
            return;
        }
        float k = (float) Math.exp(-since / 32.0);
        Vec3 rc = p.restCentre;
        out.billboard(rc, p.params.moonRadius() * 1.5, Shapes.GLOW, Shapes.HAZE, FIRE, 0.55F * k, 0.0);
        out.billboard(rc, p.params.moonRadius() * 0.65, Shapes.GLOW, 0, HOT, 0.7F * k, 0.0);
    }

    /** Embers glowing on the moon's buried surface and at its molten heart, in the aftermath. */
    private static void embers(GlowBatch out, MoonPlan p, double t, float sphereVisible) {
        if (!p.params.core() || sphereVisible <= 0.01F) {
            return;
        }
        float on = CometVisuals.smooth((t - (MoonPlan.SETTLE + 6.0)) / 20.0) * (1.0F - CometVisuals.smooth((t - (MoonPlan.DURATION - 40.0)) / 40.0));
        if (on <= 0.0F) {
            return;
        }
        float pulse = 0.6F + 0.4F * (float) Math.sin(t * 0.17);
        Vec3 core = Vec3.atCenterOf(p.core);
        out.billboard(core, p.params.moonRadius() * 1.1, Shapes.GLOW, Shapes.HAZE, FIRE, 0.18F * on * pulse * sphereVisible, 0.0);
        out.billboard(core, p.params.moonRadius() * 0.4, Shapes.GLOW, 0, HOT, 0.3F * on * sphereVisible, 0.0);
    }

    private static Vec3 onLand(Vec3 point, CometVisuals.Land land, MoonPlan p) {
        double y = land.surface(point.x, point.z);
        return new Vec3(point.x, (Double.isNaN(y) ? p.target.y : y) + 0.09, point.z);
    }

    /** A glowing line lying on the ground from {@code a} to {@code b} (as {@code CometExtras.dash}). */
    private static void dash(GlowBatch out, Vec3 a, Vec3 b, double halfThickness, int rgb, float strength) {
        Vec3 along = b.subtract(a);
        double l = Math.sqrt(along.x * along.x + along.z * along.z);
        if (l < 1.0E-3) {
            return;
        }
        Vec3 middle = a.add(b).scale(0.5);
        out.flat(middle, new Vec3(-along.z / l, 0.0, along.x / l), halfThickness, l * 0.5 + halfThickness * 0.5, Shapes.DASH, 0, rgb, strength);
    }

    private static long hash(long seed, int i) {
        long h = seed ^ (long) i * 0x9E3779B97F4A7C15L;
        h = (h ^ h >>> 33) * 0xFF51AFD7ED558CCDL;
        h = (h ^ h >>> 33) * 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }

    private static double frac(long h) {
        return (double) (h >>> 11 & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL;
    }
}
