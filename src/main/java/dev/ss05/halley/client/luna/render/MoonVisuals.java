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
        if (t >= MoonPlan.ENTRY - 30.0 && t <= MoonPlan.CONTACT + 10.0) {
            double trueRadius = Math.max(1.0E-6, p.params.moonRadius());
            Mapping map = new Mapping(p.centre(t), placement.centre(), placement.radius() / trueRadius);
            entry(world, light, p, t, map);
            fragments(world, light, p, t, map);
        }
        if (t >= MoonPlan.CONTACT) {
            detonation(world, light, p, t, land);
        }
        if (t >= MoonPlan.SETTLE) {
            embers(world, p, t, fx.sphereVisibilityAt(t));
        }
    }

    /** The gold mark on the ground, from the alarm until the impact. */
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
    }

    /**
     * The burning shroud wrapping the body, a bow-shock cap on its leading face, a halo glow round it, and flame
     * streamers trailing back up the path it fell - everything drawn at the sphere's own {@code map}ped placement
     * (pulled in and cinematically exaggerated, see {@code MoonSphere}), so it never parts ways with the body it's
     * coming off and reads big even when the true moon is still a world away.
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
        light.billboard(displayCentre.add(heading.scale(effR * 0.5)), effR * 1.2, Shapes.BOW, 0, FIRE, 0.85F * burn, angle);
        light.billboard(displayCentre, effR * 0.95, Shapes.BOW, 0, HOT, 0.55F * burn, angle);
        // the shroud wrapping the whole body, and a wider halo glow round it
        world.billboard(displayCentre, effR * 1.7, Shapes.GLOW, 0, FIRE, 0.6F * burn, 0.0);
        world.billboard(displayCentre, effR * 2.8, Shapes.GLOW, Shapes.HAZE, HOT, 0.4F * burn, 0.0);
        world.rect(displayCentre, effR * 9.0, effR * 0.4, Shapes.FLARE, 0, FIRE, 0.45F * burn);

        // long flame streamers trailing back up the path, several moon diameters
        Vec3 back = displayCentre.subtract(heading.scale(effR * 8.0));
        world.ribbon(displayCentre, back, Vec3.ZERO, effR * 0.9, effR * 1.7, 22, Shapes.STREAK, Shapes.DUST, FIRE, 0.65F * burn);
        world.ribbon(displayCentre, back, Vec3.ZERO, effR * 0.55, effR * 1.05, 22, Shapes.STREAK, Shapes.PLASMA, HOT, 0.8F * burn);
        world.ribbon(displayCentre, back, Vec3.ZERO, effR * 0.25, effR * 0.5, 16, Shapes.STREAK, Shapes.ION, WHITE, 0.6F * burn);
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

    /** The flash, the dust dome, and the shock ring racing out over the land, kept in step with the server's blast. */
    private static void detonation(GlowBatch world, GlowBatch light, MoonPlan p, double t, CometVisuals.Land land) {
        double a = t - MoonPlan.CONTACT;
        double radius = p.params.craterRadius();
        Vec3 c = p.target;

        if (a < 40.0) {
            light.billboard(c.add(0.0, radius * 0.3, 0.0), radius * 3.2, Shapes.GLOW, 0, WHITE, 1.7F * (float) Math.exp(-a / 4.0), 0.0);
            light.rect(c.add(0.0, radius * 0.3, 0.0), radius * 26.0, radius * 0.32, Shapes.FLARE, 0, HOT, 1.3F * (float) Math.exp(-a / 6.0));
        }
        if (a < 220.0) {
            double height = radius * (1.1 + 4.2 * (1.0 - Math.exp(-a / 10.0)));
            world.column(c.add(0.0, -2.0, 0.0), height, radius * (0.24 + 0.22 * (1.0 - Math.exp(-a / 22.0))),
                Shapes.STREAK, Shapes.DUST, ASH, 0.85F * (float) Math.exp(-a / 70.0));
            world.column(c, height * 0.7, radius * 0.1, Shapes.STREAK, Shapes.ION, FIRE, 0.7F * (float) Math.exp(-a / 20.0));
        }
        if (!land.has(c.x, c.z)) {
            return;
        }
        double reach = radius * p.params.blastReach();
        if (a < 42.0) {
            double k = Math.pow(Mth.clamp(a / 28.0, 0.0, 1.0), 0.6);
            double front = radius + (reach - radius) * k + Math.max(0.0, a - 28.0) * 0.6;
            float s = (1.0F - CometVisuals.smooth((a - 6.0) / 36.0)) * CometVisuals.smooth(a / 1.5);
            for (int i = 0; i < 3; i++) {
                world.flat(c.add(0.0, 0.7 + i * 3.0, 0.0), p.travel, front / 0.9, front / 0.9, Shapes.SHOCK, i, HOT,
                    s * (i == 0 ? 1.0F : i == 1 ? 0.35F : 0.15F));
            }
        }
        float frost = CometVisuals.smooth((a - 4.0) / 18.0) * (1.0F - 0.6F * CometVisuals.smooth((a - 60.0) / 120.0))
            * (1.0F - CometVisuals.smooth((t - (MoonPlan.DURATION - 24.0)) / 24.0));
        double r = radius * 1.1 / 0.86;
        world.flat(c.add(0.0, 0.6, 0.0), p.travel, r, r, Shapes.RING, 2, DEEP, 0.45F * frost);
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
