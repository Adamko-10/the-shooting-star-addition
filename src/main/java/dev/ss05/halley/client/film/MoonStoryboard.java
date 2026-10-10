package dev.ss05.halley.client.film;

import dev.ss05.halley.MoonPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The caster's cutscene for SS-06, shot by shot - modelled directly on {@code client/film/HalleyStoryboard} (see
 * there for how {@code k} and {@code stage} work). compat/client/MoonCutscene turns these into The Shooting Star's
 * cutscene, played full-frame and skippable with the remote's cutscene key.
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
    private static final double MIN_RADII = 2.5;

    public static List<Take> takes(MoonPlan plan) {
        List<Take> takes = new ArrayList<>();
        Vec3 mark = plan.target;
        Vec3 side = new Vec3(-plan.travel.z, 0.0, plan.travel.x);
        double radius = plan.params.craterRadius();
        double moonR = plan.params.moonRadius();

        // 1. The hand: the cover flips open, the button goes down, the moon shudders above and cracks open.
        takes.add(Take.cut(0, MoonPlan.BREAK + 24, safe(plan, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 hand = frame(x, feet, 0.36, 1.38, 0.62);
            Vec3 pos = frame(x, feet, l(k, 1.3, 1.1), l(k, 1.7, 1.55), l(k, 2.0, 1.8));
            Vec3 look = x.time(p) < MoonPlan.ALARM + 10.0F ? hand : plan.centre(x.time(p));
            return new Pose(pos, look, (float) l(k, 42.0, 50.0), 0.0F);
        })));

        // 2. Low and wide, from beside the mark: the moon fills more and more of the sky as it falls, the camera
        //    easing back as it nears so the whole body always stays in shot (never a static cam inside the moon).
        takes.add(Take.cut(MoonPlan.BREAK + 24, MoonPlan.ENTRY - 10, safe(plan, (x, p, k) -> {
            double back = l(k, 34.0, 60.0) + moonR * 0.4;
            Vec3 pos = mark.add(side.scale(-back)).add(plan.travel.scale(26.0)).add(0.0, 2.4 + moonR * 0.1, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(pos, moon, (float) l(k, 76.0, 84.0), 0.0F);
        })));

        // 3. From well back of the mark, looking up at it burning through the air - zooming out as it rushes in,
        //    so the whole moon (now much bigger) is always in frame, never clipped into.
        takes.add(Take.cut(MoonPlan.ENTRY - 10, MoonPlan.CONTACT - 12, safe(plan, (x, p, k) -> {
            double back = moonR * l(k, 1.3, 2.6);
            Vec3 pos = mark.add(side.scale(back * 0.5)).add(plan.travel.scale(back * 0.85)).add(0.0, 2.0 + moonR * 0.25, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(pos, moon, (float) l(k, 70.0, 82.0), (float) l(k, 0.0, 2.0));
        })));

        // 4. The impact, watched from a safe distance, pulling back further still as the moon crashes down.
        takes.add(Take.cut(MoonPlan.CONTACT - 12, MoonPlan.CONTACT + 40, safe(plan, (x, p, k) -> {
            double back = radius * l(k, 0.9, 1.3) + moonR * l(k, 0.6, 0.3);
            Vec3 pos = mark.add(side.scale(back)).add(plan.travel.scale(-radius * 0.9)).add(0.0, radius * 0.55 + moonR * 0.2, 0.0);
            Vec3 look = mark.add(0.0, radius * l(k, 0.3, 0.55), 0.0);
            return new Pose(pos, look, (float) l(k, 66.0, 76.0), (float) l(k, -2.0, 0.0));
        })));

        // 5. The dust climbing, the crater smoking, from higher up.
        takes.add(Take.sweep(MoonPlan.CONTACT + 40, MoonPlan.SETTLE + 30, 8, safe(plan, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(-radius * 0.9)).add(0.0, radius * l(k, 1.0, 1.6), 0.0);
            return new Pose(pos, mark.add(0.0, moonR * 0.2, 0.0), (float) l(k, 72.0, 64.0), 0.0F);
        })));

        // 6. The moon itself, at rest, half-buried in its crater - close in, then pulling back.
        takes.add(Take.sweep(MoonPlan.SETTLE + 30, MoonPlan.SETTLE + 100, 10, safe(plan, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(radius * l(0.55F, 0.4, 0.75))).add(plan.travel.scale(-radius * l(k, 0.3, 0.65)))
                .add(0.0, moonR * l(k, 0.5, 1.1), 0.0);
            return new Pose(pos, plan.restCentre, (float) l(k, 58.0, 68.0), 0.0F);
        })));

        // 7. Back to the caster, looking out over what they called down.
        takes.add(Take.sweep(MoonPlan.SETTLE + 100, MoonPlan.DURATION, 10, safe(plan, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 away = new Vec3(feet.x - mark.x, 0.0, feet.z - mark.z);
            Vec3 h = away.lengthSqr() < 1.0 ? plan.travel.scale(-1.0) : away.normalize();
            Vec3 across = new Vec3(-h.z, 0.0, h.x);
            Vec3 pos = feet.add(h.scale(l(k, 4.0, 9.0))).add(across.scale(l(k, 2.0, 4.0))).add(0.0, l(k, 10.0, 22.0), 0.0);
            return new Pose(pos, plan.restCentre, (float) l(k, 62.0, 70.0), 0.0F);
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
