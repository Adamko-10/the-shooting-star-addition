package dev.ss05.halley.client.film;

import dev.ss05.halley.MoonPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The caster's cutscene for SS-06, shot by shot - modelled on {@code client/film/HalleyStoryboard} (see there for how
 * {@code k} and {@code stage} work). compat/tss/client/MoonCutscene turns these into The Shooting Star's cutscene,
 * played full-frame and skippable with the remote's cutscene key.
 *
 * <p>Takes are timed on the strike's own clock; {@code stage.time(p)} is the clock as the film shows it, which runs in
 * slow motion over the impact ({@code MoonFx#slowLag}), so the shots that track the moon and the blast slow down with
 * it while their camera moves keep their own pace. Every camera is kept clear of the moon ({@link #MIN_RADII}), in land
 * this game has loaded and out of the ground ({@link Stage#keep}) - the film never looks at the void past the edge of
 * what the server has sent, or from inside a hill.
 */
public final class MoonStoryboard {
    /** A camera: where it is, what it looks at, its vertical field of view and its roll, in degrees. */
    public record Pose(Vec3 position, Vec3 look, float fov, float roll) {
    }

    /** What the shots are anchored to. */
    public interface Stage {
        Vec3 feet(float partial);

        Vec3 forward();

        float time(float partial);

        /**
         * Where a camera wanting to be at {@code pos} actually goes: drawn back toward {@code home} until it is over land
         * this game has, and lifted out of the ground if it is in it.
         */
        default Vec3 keep(Vec3 pos, Vec3 home) {
            return pos;
        }
    }

    @FunctionalInterface
    public interface Path {
        Pose pose(Stage stage, float partial, float k);
    }

    public record Take(int start, int end, int blendIn, Path path) {
        static Take cut(int start, int end, Path path) {
            return new Take(start, end, 0, path);
        }

        static Take sweep(int start, int end, int blendIn, Path path) {
            return new Take(start, end, blendIn, path);
        }
    }

    private MoonStoryboard() {
    }

    /** The camera is never allowed closer to the moon's centre than this many radii, so it never clips into (or
     * ends up right at) its surface - see {@link #safe}. */
    private static final double MIN_RADII = 2.4;

    /** When the last shot ends: the remote hands the view back to the caster from there. */
    public static final int END = MoonPlan.SETTLE + 95;

    public static List<Take> takes(MoonPlan plan) {
        List<Take> takes = new ArrayList<>();
        Vec3 mark = plan.target;
        Vec3 side = new Vec3(-plan.travel.z, 0.0, plan.travel.x);
        Vec3 travel = plan.travel;
        double radius = plan.params.craterRadius();
        double moonR = plan.params.moonRadius();
        Vec3 up = new Vec3(0.0, 1.0, 0.0);

        // 1. Uplink: the cover flips open, the button goes down; the alarm.
        takes.add(Take.cut(0, MoonPlan.ALARM + 18, safe(plan, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 hand = frame(x, feet, 0.36, 1.38, 0.62);
            Vec3 pos = frame(x, feet, l(k, 1.3, 1.08), l(k, 1.72, 1.56), l(k, 2.0, 1.75));
            return new Pose(pos, hand, (float) l(k, 42.0, 48.0), 0.0F);
        })));

        // 2. Fracture: from just behind the caster's shoulder, a long lens on the moon in the sky - it shudders, cracks
        //    open along its molten seams and tears loose, filling more and more of the frame as the lens closes in.
        takes.add(Take.cut(MoonPlan.ALARM + 18, MoonPlan.FALL + 12, safe(plan, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 pos = frame(x, feet, l(k, 0.55, 0.42), l(k, 1.78, 1.86), l(k, -0.7, -0.55));
            return new Pose(pos, plan.centre(x.time(p)), (float) l(k, 34.0, 20.0), (float) l(k, 0.0, -1.5));
        })));

        // 3. Low on the land beside the mark, looking up: the red sky, the moon starting to come down over the horizon.
        takes.add(Take.cut(MoonPlan.FALL + 12, MoonPlan.FALL + 90, safe(plan, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(radius * l(k, 0.5, 0.42))).add(travel.scale(-radius * 0.18)).add(0.0, 2.4, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(x.keep(pos, x.feet(p)), moon, (float) l(k, 74.0, 70.0), 0.0F);
        })));

        // 4. The long fall: a slow half-orbit round the mark at a distance, the moon growing over the land it will hit.
        takes.add(Take.cut(MoonPlan.FALL + 90, MoonPlan.ENTRY - 12, safe(plan, (x, p, k) -> {
            double angle = Math.toRadians(l(k, -20.0, 28.0));
            Vec3 around = side.scale(-Math.cos(angle)).add(travel.scale(Math.sin(angle)));
            Vec3 pos = mark.add(around.scale(radius * 0.95 + moonR * 0.5)).add(0.0, 6.0 + moonR * 0.18, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            Vec3 look = mark.add(up.scale(moonR * 0.3)).lerp(moon, 0.72);
            return new Pose(x.keep(pos, x.feet(p)), look, (float) l(k, 64.0, 72.0), 0.0F);
        })));

        // 5. Entry: tracking alongside its line of fall, the burning moon and the ground it is about to hit both in frame.
        takes.add(Take.cut(MoonPlan.ENTRY - 12, MoonPlan.CONTACT - 26, safe(plan, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(radius * 1.05 + moonR * 0.9)).add(travel.scale(radius * l(k, 0.35, 0.1)))
                .add(0.0, 4.0 + moonR * 0.25, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(x.keep(pos, x.feet(p)), moon.lerp(mark, 0.34), (float) l(k, 74.0, 80.0), (float) l(k, 0.0, 3.0));
        })));

        // 6. Looming: from the land beyond the mark, low, as it comes down over the camera and fills the sky.
        takes.add(Take.cut(MoonPlan.CONTACT - 26, MoonPlan.CONTACT, safe(plan, (x, p, k) -> {
            Vec3 pos = mark.add(travel.scale(radius * 1.15 + moonR * 0.6)).add(side.scale(radius * 0.18)).add(0.0, 2.2, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(x.keep(pos, x.feet(p)), moon.lerp(mark, 0.2), (float) l(k, 80.0, 88.0), (float) l(k, -1.0, 2.5));
        })));

        // 7. The impact, in slow motion: a hard cut to three-quarters on, pulling back and up as the fireball, the jets
        //    of fire and the wall of dust roll out at a fifth of their speed.
        Vec3 quarter = side.scale(-0.8).add(travel.scale(-0.6)).normalize();
        takes.add(Take.cut(MoonPlan.CONTACT, MoonPlan.CONTACT + 58, safe(plan, (x, p, k) -> {
            double e = k * k * (3.0 - 2.0 * k);
            Vec3 pos = mark.add(quarter.scale(l((float) e, radius * 0.78 + moonR, radius * 1.5 + moonR * 1.3)))
                .add(0.0, l((float) e, radius * 0.16 + moonR * 0.25, radius * 0.55 + moonR * 0.6), 0.0);
            Vec3 look = mark.add(0.0, l((float) e, moonR * 0.55, moonR * 0.3), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), look, (float) l((float) e, 60.0, 76.0), (float) l(k, -2.0, 0.0));
        })));

        // 8. The dust climbing and the rim heaving up, from high over the crater, turning slowly round it.
        Vec3 high = side.scale(0.7).add(travel.scale(-0.7)).normalize();
        takes.add(Take.sweep(MoonPlan.CONTACT + 58, MoonPlan.SETTLE + 30, 8, safe(plan, (x, p, k) -> {
            double angle = Math.toRadians(l(k, 0.0, 38.0));
            Vec3 around = high.scale(Math.cos(angle)).add(new Vec3(-high.z, 0.0, high.x).scale(Math.sin(angle)));
            Vec3 pos = mark.add(around.scale(radius * 1.25)).add(0.0, radius * 0.75 + moonR * 0.4, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), mark.add(0.0, moonR * 0.2, 0.0), (float) l(k, 70.0, 64.0), 0.0F);
        })));

        // 9. The moon itself, at rest deep in its crater, most of it buried: low over the rim, rising as the film ends.
        takes.add(Take.sweep(MoonPlan.SETTLE + 30, END, 10, safe(plan, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(radius * 0.62)).add(travel.scale(-radius * l(k, 0.42, 0.7)))
                .add(0.0, plan.params.rimHeight() + moonR * l(k, 0.55, 1.35), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), plan.restCentre.add(0.0, moonR * 0.25, 0.0), (float) l(k, 56.0, 66.0), 0.0F);
        })));
        return takes;
    }

    /**
     * Wraps a shot so its camera is never inside (or right at the surface of) the moon: whatever position the shot
     * itself computes, if it ends up closer than {@link #MIN_RADII} moon radii from the moon's actual centre at that
     * moment, it is pushed straight back along the same line until it clears that distance - a continuous "zoom
     * out" as the moon arrives, never a sudden cut, and never a clipped, static camera sitting inside it.
     */
    private static Path safe(MoonPlan plan, Path path) {
        double minDist = plan.params.moonRadius() * MIN_RADII;
        return (stage, partial, k) -> {
            Pose pose = path.pose(stage, partial, k);
            double t = stage.time(partial);
            Vec3 moon = t <= MoonPlan.SETTLE ? plan.centre(t) : plan.restCentre;
            Vec3 pos = pushOutside(pose.position(), moon, minDist);
            return new Pose(pos, pose.look(), pose.fov(), pose.roll());
        };
    }

    private static Vec3 pushOutside(Vec3 pos, Vec3 centre, double minDist) {
        Vec3 d = pos.subtract(centre);
        double len = d.length();
        if (len >= minDist) {
            return pos;
        }
        Vec3 dir = len < 1.0E-4 ? new Vec3(0.0, 0.15, 1.0).normalize() : d.scale(1.0 / len);
        return centre.add(dir.scale(minDist));
    }

    // ---- Helpers. -------------------------------------------------------------------------------------------------

    private static double l(float k, double a, double b) {
        return a + (b - a) * k;
    }

    private static Vec3 frame(Stage x, Vec3 anchor, double r, double u, double f) {
        Vec3 fw = x.forward();
        Vec3 rt = new Vec3(-fw.z, 0.0, fw.x);
        return anchor.add(rt.x * r + fw.x * f, u, rt.z * r + fw.z * f);
    }
}
