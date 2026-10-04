package dev.ss05.halley.client.film;

import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.client.render.CometVisuals;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The caster's cutscene, shot by shot. Each take is a camera path over a span of the strike's timeline; compat/client
 * turns them into The Shooting Star's cutscene (it plays them full-frame, skippable with the remote's cutscene key).
 *
 * <p>All cameras stay near the caster and the mark, where the land is actually loaded, and the aftermath is filmed from
 * the caster's side (the client only has the land round the player); the timings come from
 * {@link HalleyPlan}, so retiming the strike keeps the film in step. To recut it, edit {@link #takes}: {@code k} runs
 * 0..1 across each take (eased), {@code stage.time(p)} is the strike's own clock for shots that track the comet.
 */
public final class HalleyStoryboard {
    /** A camera: where it is, what it looks at, its vertical field of view and its roll, in degrees. */
    public record Pose(Vec3 position, Vec3 look, float fov, float roll) {
    }

    /** What the shots are anchored to. */
    public interface Stage {
        /** The caster's feet (they may move, or be lifted clear in creative). */
        Vec3 feet(float partial);

        /** Which way the caster faced when they pressed the button (horizontal, unit length). */
        Vec3 forward();

        /** Ticks since the button was pressed. */
        float time(float partial);
    }

    @FunctionalInterface
    public interface Path {
        Pose pose(Stage stage, float partial, float k);
    }

    /** One shot, from {@code start} to {@code end} ticks; {@code blendIn} > 0 glides into it instead of cutting. */
    public record Take(int start, int end, int blendIn, Path path) {
        static Take cut(int start, int end, Path path) {
            return new Take(start, end, 0, path);
        }

        static Take sweep(int start, int end, int blendIn, Path path) {
            return new Take(start, end, blendIn, path);
        }
    }

    private HalleyStoryboard() {
    }

    public static List<Take> takes(HalleyPlan plan, CometVisuals visuals) {
        List<Take> takes = new ArrayList<>();
        Vec3 dir = plan.dir;
        Vec3 home = dir.scale(-1.0);
        Vec3 side = plan.side;
        int s = plan.sideSign;
        double radius = plan.params.craterRadius();
        int length = plan.params.trenchLength();
        Vec3 mark = plan.target;
        Vec3 touchdown = plan.touchdown;
        double endWidth = plan.trenchHalfWidth(length);
        int impact = plan.impact;
        Vec3 heart = visuals.heart();

        // 1. The hand: the cover flips open, the button goes down.
        takes.add(Take.cut(0, HalleyPlan.MARK, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 hand = frame(x, feet, 0.36, 1.38, 0.62);
            return new Pose(frame(x, feet, l(k, 1.35, 1.2), l(k, 1.7, 1.6), l(k, 2.1, 1.85)), hand, 40.0F, 0.0F);
        }));

        // 2. Over the shoulder: the mark lands on the crosshair.
        takes.add(Take.cut(HalleyPlan.MARK, 30, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            return new Pose(frame(x, feet, 1.1, 2.1, -3.2), mark.add(0.0, l(k, 18.0, 2.0), 0.0), (float) l(k, 60.0, 34.0), 0.0F);
        }));

        // 3. Down the corridor from the mark, to the horizon the comet is about to rise over.
        takes.add(Take.cut(30, 64, (x, p, k) -> {
            Vec3 pos = mark.add(home.scale(l(k, 10.0, 16.0))).add(side.scale(s * 3.0)).add(0.0, l(k, 2.2, 5.0), 0.0);
            Vec3 look = touchdown.add(dir.scale(200.0)).add(0.0, l(k, 10.0, 60.0), 0.0);
            return new Pose(pos, look, (float) l(k, 72.0, 64.0), (float) (l(k, 0.0, -3.0) * s));
        }));

        // 4. Telephoto: the comet over the horizon, its tails reaching up across the sky.
        takes.add(Take.cut(64, 122, (x, p, k) -> {
            Vec3 comet = plan.comet(x.time(p));
            Vec3 pos = mark.add(home.scale(36.0)).add(side.scale(-s * 10.0)).add(0.0, 3.2, 0.0);
            Vec3 look = comet.add(visuals.tailDirection().scale(comet.distanceTo(pos) * 0.05));
            return new Pose(pos, look, (float) l(k, 15.0, 10.0), 0.0F);
        }));

        // 5. Wide, from high up behind the caster: the corridor laid out on the land, the comet hanging over it.
        takes.add(Take.cut(122, 186, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 pos = feet.add(home.scale(l(k, 44.0, 36.0))).add(side.scale(-s * 30.0)).add(0.0, l(k, 34.0, 28.0), 0.0);
            Vec3 look = lerp(mark.add(dir.scale(length * 0.6)), plan.comet(x.time(p)), 0.3);
            return new Pose(pos, look, (float) l(k, 76.0, 72.0), 0.0F);
        }));

        // 6. Under the tail: from beside the mark, looking up at it as it grows.
        takes.add(Take.cut(186, 238, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(-s * 5.0)).add(home.scale(6.0)).add(0.0, 1.6, 0.0);
            return new Pose(pos, plan.comet(x.time(p)), (float) l(k, 76.0, 84.0), (float) (l(k, 0.0, 4.0) * s));
        }));

        // 7. It comes: entry, the fireball crossing the sky toward the land.
        takes.add(Take.cut(238, 272, (x, p, k) -> {
            Vec3 pos = mark.add(home.scale(24.0)).add(side.scale(s * 14.0)).add(0.0, 2.5, 0.0);
            Vec3 look = lerp(plan.comet(x.time(p)), touchdown.add(0.0, 8.0, 0.0), k * 0.6);
            return new Pose(pos, look, (float) l(k, 74.0, 70.0), 0.0F);
        }));

        // 8. Touchdown, from behind the caster: the fireball coming down over the land to the horizon. (From here
        //    on, cameras near the mark look back toward the caster: the client only has the land round the player,
        //    so whatever lies far beyond the mark may not be there to film.)
        takes.add(Take.cut(272, HalleyPlan.TOUCHDOWN, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 pos = feet.add(home.scale(14.0)).add(side.scale(-s * 6.0)).add(0.0, 5.0, 0.0);
            Vec3 look = lerp(plan.comet(x.time(p)), touchdown.add(0.0, 12.0, 0.0), 0.35 + 0.65 * k);
            return new Pose(pos, look, (float) l(k, 60.0, 48.0), 0.0F);
        }));

        // 9. The plough, from low down just behind the mark: the nucleus coming straight down the trench at us.
        takes.add(Take.cut(HalleyPlan.TOUCHDOWN, impact - 2, (x, p, k) -> {
            Vec3 pos = mark.add(home.scale(radius * 0.9)).add(side.scale(-s * radius * 0.25)).add(0.0, 2.5, 0.0);
            return new Pose(pos, plan.comet(x.time(p)).add(0.0, 1.0, 0.0), (float) l(k, 56.0, 64.0), 0.0F);
        }));

        // 10. The detonation, watched from a safe distance. (The aftermath is filmed from the caster's side: the
        //     client only has the land round the player, and cameras near the player see its far edge fogged over
        //     the way the game's own horizon is.)
        takes.add(Take.cut(impact - 2, impact + 24, (x, p, k) -> {
            Vec3 pos = between(x, p, mark, 0.4).add(side.scale(s * 15.0)).add(0.0, 10.0, 0.0);
            return new Pose(pos, mark.add(0.0, radius * l(k, 0.3, 0.5), 0.0), (float) l(k, 62.0, 68.0), (float) l(k, -3.0, 0.0));
        }));

        // 11. The plume climbing into a glowing cloud of ice.
        takes.add(Take.sweep(impact + 24, impact + 82, 6, (x, p, k) -> {
            Vec3 pos = between(x, p, mark, l(k, 0.25, 0.32)).add(side.scale(-s * 25.0)).add(0.0, 8.0, 0.0);
            return new Pose(pos, mark.add(0.0, radius * l(k, 0.3, 0.9), 0.0), (float) l(k, 66.0, 70.0), 0.0F);
        }));

        // 12. The comet heart, from the crater floor below it, standing against the sky.
        takes.add(Take.cut(impact + 82, impact + 124, (x, p, k) -> {
            Vec3 toward = towardCaster(x, p, mark, home);
            Vec3 across = new Vec3(-toward.z, 0.0, toward.x);
            Vec3 pos = heart.add(toward.scale(radius * l(k, 0.36, 0.3))).add(across.scale(s * radius * 0.1)).add(0.0, -6.0, 0.0);
            return new Pose(pos, heart.add(0.0, l(k, 6.0, 10.0), 0.0), (float) l(k, 62.0, 56.0), 0.0F);
        }));

        // 13. Over the crater from the caster's side: the scar, the heart, the frost thrown out round it.
        takes.add(Take.sweep(impact + 124, impact + 150, 8, (x, p, k) -> {
            Vec3 pos = between(x, p, mark, l(k, 0.4, 0.46)).add(side.scale(s * radius * 0.35)).add(0.0, radius * 0.75, 0.0);
            return new Pose(pos, mark, (float) l(k, 66.0, 62.0), 0.0F);
        }));

        // 14. Back to the caster, looking out over what they called down.
        takes.add(Take.sweep(impact + 150, impact + 176, 10, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 away = new Vec3(feet.x - mark.x, 0.0, feet.z - mark.z);
            Vec3 h = away.lengthSqr() < 1.0 ? home : away.normalize();
            Vec3 across = new Vec3(-h.z, 0.0, h.x);
            Vec3 pos = feet.add(h.scale(l(k, 4.0, 9.0))).add(across.scale(l(k, 2.0, 4.0))).add(0.0, l(k, 10.0, 24.0), 0.0);
            return new Pose(pos, mark.add(0.0, -radius * 0.15, 0.0), (float) l(k, 62.0, 70.0), 0.0F);
        }));
        return takes;
    }

    // ---- Helpers. -------------------------------------------------------------------------------------------------

    private static double l(float k, double a, double b) {
        return a + (b - a) * k;
    }

    /** A point {@code k} of the way from the caster's feet to {@code to}, at ground height. */
    private static Vec3 between(Stage x, float p, Vec3 to, double k) {
        Vec3 feet = x.feet(p);
        return new Vec3(feet.x + (to.x - feet.x) * k, to.y + (feet.y - to.y) * (1.0 - k), feet.z + (to.z - feet.z) * k);
    }

    /** Horizontal unit vector from {@code from} toward the caster (or {@code fallback} when they stand on it). */
    private static Vec3 towardCaster(Stage x, float p, Vec3 from, Vec3 fallback) {
        Vec3 feet = x.feet(p);
        Vec3 d = new Vec3(feet.x - from.x, 0.0, feet.z - from.z);
        return d.lengthSqr() < 1.0 ? fallback : d.normalize();
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return a.add(b.subtract(a).scale(k));
    }

    /** A point relative to the caster: {@code r} to their right, {@code u} up, {@code f} forward. */
    private static Vec3 frame(Stage x, Vec3 anchor, double r, double u, double f) {
        Vec3 fw = x.forward();
        Vec3 rt = new Vec3(-fw.z, 0.0, fw.x);
        return anchor.add(rt.x * r + fw.x * f, u, rt.z * r + fw.z * f);
    }

    private static Vec3 orbit(Vec3 point, Vec3 pivot, double degrees) {
        double a = Math.toRadians(degrees);
        double x = point.x - pivot.x;
        double z = point.z - pivot.z;
        return new Vec3(pivot.x + x * Math.cos(a) - z * Math.sin(a), point.y, pivot.z + x * Math.sin(a) + z * Math.cos(a));
    }
}
