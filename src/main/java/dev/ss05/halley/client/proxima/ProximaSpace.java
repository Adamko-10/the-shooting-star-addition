package dev.ss05.halley.client.proxima;

import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.Shapes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The space intro of SA-04 Proxima (docs/SS-07-PROXIMA.md, stage 3 part 2), drawn with the glow shapes far up in the
 * sky (everything is pulled in along its line of sight, see GlowBatch, so only the angle matters): the red dwarf behind
 * the Dyson sphere's lattice and iris, the iris opening and the star blazing out, two plasma loops twisting into an
 * infinity sign that flares and collapses to a point, the lattice folding into a lens that focuses the flare into a
 * beam, and the gate near Earth catching it, high over the mark where the front will come from. The glow pipeline only
 * adds light, so the closed panels are shown by holding the star dim, not by drawing them dark.
 */
final class ProximaSpace {
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private ProximaSpace() {
    }

    static void render(GlowBatch world, GlowBatch glare, ProximaPlan plan, float t) {
        if (t < ProximaPlan.SPACE || t > ProximaPlan.FRONT + 30) {
            return;
        }
        Vec3 camera = world.camera();
        Vec3 star = plan.starCentre();
        Vec3 axis = star.subtract(camera).normalize();
        Vec3 u = axis.cross(UP).normalize();
        Vec3 v = u.cross(axis).normalize();
        double r = ProximaPlan.STAR_RADIUS;
        float seen = smooth((t - ProximaPlan.SPACE) / 12.0) * (1.0F - smooth((t - ProximaPlan.FLASH) / 25.0));
        float open = smooth((t - ProximaPlan.IRIS - 10.0) / 25.0);
        float collapse = smooth((t - ProximaPlan.COLLAPSE) / 7.0);

        if (seen > 0.003F) {
            // the star: dim behind the closed panels, blazing once they open
            float glow = seen * (0.22F + 0.78F * open);
            world.billboard(star, r * 2.4, Shapes.GLOW, Shapes.HAZE, 0xC81E2E, 0.6F * glow, 0.0);
            world.billboard(star, r, Shapes.DISC, 0, 0xFF3A2A, glow, 0.0);
            world.billboard(star, r * 0.55, Shapes.GLOW, 0, 0xFFE8D8, 0.9F * glow, t * 0.002);
        }

        // the lattice: a ring of struts round the star and eight iris blades over it, until the lens forms
        float lattice = seen * (1.0F - smooth((t - ProximaPlan.LENS) / 10.0));
        if (lattice > 0.003F) {
            world.billboard(star, r * 1.32, Shapes.RING, 0, 0xFF8A50, 0.5F * lattice, 0.0);
            for (int i = 0; i < 24; i++) {
                Vec3 dir = round(u, v, i / 24.0 * Math.PI * 2.0);
                strut(world, camera, star.add(dir.scale(r * 1.12)), star.add(dir.scale(r * 1.42)), r * 0.02, 0xFF7040, 0.45F * lattice);
            }
            double aperture = r * 1.05 * open;
            for (int b = 0; b < 8; b++) {
                double a = b / 8.0 * Math.PI * 2.0 + open * 0.6;
                Vec3 inner = star.add(round(u, v, a).scale(aperture));
                Vec3 outer = star.add(round(u, v, a + 0.5).scale(r * 1.12));
                strut(world, camera, inner, outer, r * 0.025, 0xFFA060, 0.6F * lattice);
            }
        }

        // the loops: two plasma loops twisting into an infinity sign, flaring white at the crossing, collapsing to a point
        if (t >= ProximaPlan.LOOPS && t < ProximaPlan.LENS) {
            float drawn = smooth((t - ProximaPlan.LOOPS) / 15.0);
            double size = r * 1.7 * (1.0 - collapse);
            Vec3 front = star.subtract(axis.scale(r * 0.3));
            int n = 64;
            Vec3 last = null;
            for (int i = 0; i <= Math.round(n * drawn); i++) {
                double s = i / (double) n * Math.PI * 2.0;
                double den = 1.0 + Math.sin(s) * Math.sin(s);
                Vec3 p = front.add(u.scale(size * Math.cos(s) / den)).add(v.scale(size * Math.sin(s) * Math.cos(s) / den));
                if (last != null) {
                    strut(world, camera, last, p, r * 0.06, 0xFF6A9A, 0.85F * seen);
                }
                last = p;
            }
            float flare = pulse(t, ProximaPlan.COLLAPSE, 4.0);
            glare.billboard(front, r * (0.3 + 1.4 * flare), Shapes.GLOW, 0, 0xFFFFFF, flare * seen, 0.0);
        }

        // the lens: the lattice folded into one lens before the star, focusing the flare into a beam away across the sky
        float lens = smooth((t - ProximaPlan.LENS) / 10.0) * (1.0F - smooth((t - ProximaPlan.FLASH) / 6.0)) * seen;
        if (lens > 0.003F) {
            Vec3 at = star.subtract(axis.scale(r * 0.8));
            world.billboard(at, r * 1.5, Shapes.RING, 0, 0xFFB080, 0.7F * lens, 0.0);
            world.billboard(at, r * 1.4, Shapes.DISC, 0, 0x803040, 0.2F * lens, 0.0);
            glare.billboard(at, r * 0.3, Shapes.GLOW, 0, 0xFFF0F4, lens, 0.0);
            float beam = smooth((t - ProximaPlan.BEAM) / 6.0) * (1.0F - smooth((t - ProximaPlan.FLASH) / 6.0));
            if (beam > 0.003F) {
                Vec3 end = camera.add(u.scale(-500.0)).add(v.scale(-700.0)).add(axis.scale(300.0));
                strut(world, camera, at, end, r * 0.35, 0xFF4A6A, 0.4F * beam * seen);
                strut(world, camera, at, end, r * 0.12, 0xFFE0EA, beam * seen);
            }
        }

        // the gate near Earth: catches the beam high over the mark and spreads it into the front at the flash
        float gate = smooth((t - ProximaPlan.GATE) / 5.0) * (1.0F - smooth((t - (ProximaPlan.FRONT + 10.0)) / 20.0));
        if (gate > 0.003F) {
            Vec3 at = plan.gate();
            world.billboard(at, 420.0, Shapes.RING, 0, 0xFF5A78, 0.8F * gate, t * 0.01);
            world.billboard(at, 300.0, Shapes.GLOW, Shapes.HAZE, 0xE8294C, 0.4F * gate, 0.0);
            float into = gate * (1.0F - smooth((t - ProximaPlan.FLASH) / 8.0));
            if (into > 0.003F) {
                strut(world, camera, at.add(plan.starDirection().scale(6000.0)), at, 70.0, 0xFFE0EA, 0.8F * into);
            }
            float burst = pulse(t, ProximaPlan.FLASH, 6.0);
            glare.billboard(at, 300.0 + 900.0 * burst, Shapes.GLOW, 0, 0xFFF0F4, burst * gate, 0.0);
        }
    }

    /** A straight band of light from {@code a} to {@code b}, {@code width} across, turned to face the camera. */
    private static void strut(GlowBatch world, Vec3 camera, Vec3 a, Vec3 b, double width, int rgb, float strength) {
        Vec3 along = b.subtract(a);
        Vec3 view = a.add(b).scale(0.5).subtract(camera);
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0E-9) {
            return;
        }
        side = side.normalize().scale(width * 0.5);
        world.corner(a.subtract(side), rgb, strength);
        world.corner(a.add(side), rgb, strength);
        world.corner(b.add(side), rgb, strength);
        world.corner(b.subtract(side), rgb, strength);
    }

    private static Vec3 round(Vec3 u, Vec3 v, double a) {
        return u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
    }

    private static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }

    private static float pulse(double t, double at, double decay) {
        return t < at ? 0.0F : (float) Math.exp(-(t - at) / decay);
    }
}
