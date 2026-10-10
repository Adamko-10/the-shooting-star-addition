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

    public static List<Take> takes(MoonPlan plan) {
        List<Take> takes = new ArrayList<>();
        Vec3 mark = plan.target;
        Vec3 side = new Vec3(-plan.travel.z, 0.0, plan.travel.x);
        double radius = plan.params.craterRadius();
        double moonR = plan.params.moonRadius();

        // 1. The hand: the cover flips open, the button goes down, the moon shudders above and cracks open.
        takes.add(Take.cut(0, MoonPlan.BREAK + 24, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 hand = frame(x, feet, 0.36, 1.38, 0.62);
            Vec3 pos = frame(x, feet, l(k, 1.3, 1.1), l(k, 1.7, 1.55), l(k, 2.0, 1.8));
            Vec3 look = x.time(p) < MoonPlan.ALARM + 10.0F ? hand : plan.centre(x.time(p));
            return new Pose(pos, look, (float) l(k, 42.0, 50.0), 0.0F);
        }));

        // 2. Low and wide, from beside the mark: the moon fills more and more of the sky as it falls.
        takes.add(Take.cut(MoonPlan.BREAK + 24, MoonPlan.ENTRY - 10, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(-20.0)).add(plan.travel.scale(18.0)).add(0.0, 2.2, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(pos, moon, (float) l(k, 78.0, 86.0), 0.0F);
        }));

        // 3. From the ground right by the mark, looking straight up at it burning through the air.
        takes.add(Take.cut(MoonPlan.ENTRY - 10, MoonPlan.CONTACT - 12, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(6.0)).add(0.0, 1.6, 0.0);
            Vec3 moon = plan.centre(x.time(p));
            return new Pose(pos, moon, (float) l(k, 88.0, 96.0), (float) l(k, 0.0, 2.0));
        }));

        // 4. The impact, watched from a safe distance.
        takes.add(Take.cut(MoonPlan.CONTACT - 12, MoonPlan.CONTACT + 40, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(28.0)).add(plan.travel.scale(-radius * 0.8)).add(0.0, radius * 0.5, 0.0);
            Vec3 look = mark.add(0.0, radius * l(k, 0.3, 0.55), 0.0);
            return new Pose(pos, look, (float) l(k, 70.0, 78.0), (float) l(k, -2.0, 0.0));
        }));

        // 5. The dust climbing, the crater smoking, from higher up.
        takes.add(Take.sweep(MoonPlan.CONTACT + 40, MoonPlan.SETTLE + 30, 8, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(-radius * 0.9)).add(0.0, radius * l(k, 1.0, 1.6), 0.0);
            return new Pose(pos, mark.add(0.0, moonR * 0.2, 0.0), (float) l(k, 72.0, 64.0), 0.0F);
        }));

        // 6. The moon itself, at rest, half-buried in its crater - close in, then pulling back.
        takes.add(Take.sweep(MoonPlan.SETTLE + 30, MoonPlan.SETTLE + 100, 10, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(radius * l(0.55F, 0.4, 0.75))).add(plan.travel.scale(-radius * l(k, 0.3, 0.65)))
                .add(0.0, moonR * l(k, 0.5, 1.1), 0.0);
            return new Pose(pos, plan.restCentre, (float) l(k, 58.0, 68.0), 0.0F);
        }));

        // 7. Back to the caster, looking out over what they called down.
        takes.add(Take.sweep(MoonPlan.SETTLE + 100, MoonPlan.DURATION, 10, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 away = new Vec3(feet.x - mark.x, 0.0, feet.z - mark.z);
            Vec3 h = away.lengthSqr() < 1.0 ? plan.travel.scale(-1.0) : away.normalize();
            Vec3 across = new Vec3(-h.z, 0.0, h.x);
            Vec3 pos = feet.add(h.scale(l(k, 4.0, 9.0))).add(across.scale(l(k, 2.0, 4.0))).add(0.0, l(k, 10.0, 22.0), 0.0);
            return new Pose(pos, plan.restCentre, (float) l(k, 62.0, 70.0), 0.0F);
        }));
        return takes;
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
