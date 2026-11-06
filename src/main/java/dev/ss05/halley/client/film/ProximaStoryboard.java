package dev.ss05.halley.client.film;

import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.film.MoonStoryboard.Pose;
import dev.ss05.halley.client.film.MoonStoryboard.Stage;
import dev.ss05.halley.client.film.MoonStoryboard.Take;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * SA-04 Proxima's film, in the world (docs/SS-07-PROXIMA.md, stage 3 part 1; the space intro comes next), shot by
 * shot on the strike's clock, in Luna's film types (see {@link MoonStoryboard} for how {@code k} and {@code stage} work).
 * Every camera is kept over land this game has loaded and out of the ground ({@link Stage#keep}).
 */
public final class ProximaStoryboard {
    /** When the last shot ends: the remote hands the view back to the caster from there. */
    public static final int END = ProximaPlan.IMPACT + 190;

    private ProximaStoryboard() {
    }

    public static List<Take> takes(ProximaPlan plan) {
        List<Take> takes = new ArrayList<>();
        Vec3 mark = plan.target;
        Vec3 front = plan.frontDirection();
        // the way the front comes from, along the ground, and across it
        Vec3 from = new Vec3(-front.x, 0.0, -front.z).normalize();
        Vec3 side = new Vec3(-from.z, 0.0, from.x);
        double bowl = plan.params.bowlRadius();
        double scorch = plan.params.scorchRadius();

        Vec3 star = plan.starCentre();
        Vec3 gate = plan.gate();
        // 1. Uplink: over the caster's shoulder, the remote's cover and button.
        takes.add(Take.cut(0, ProximaPlan.SPACE, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 hand = frame(x, feet, 0.36, 1.38, 0.62);
            Vec3 pos = frame(x, feet, l(k, 1.3, 1.08), l(k, 1.72, 1.56), l(k, 2.0, 1.75));
            return new Pose(pos, hand, (float) l(k, 42.0, 48.0), 0.0F);
        }));

        // The space intro (stage 3 part 2): 2. rising off the land into a sky gone to space, turning up to the star;
        takes.add(Take.cut(ProximaPlan.SPACE, ProximaPlan.IRIS, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            double e = k * k * (3.0 - 2.0 * k);
            Vec3 pos = feet.add(0.0, l((float) e, 2.0, 300.0), 0.0);
            Vec3 horizon = feet.add(x.forward().scale(60.0)).add(0.0, 20.0, 0.0);
            return new Pose(x.keep(pos, feet), horizon.lerp(star, e), (float) l((float) e, 70.0, 26.0), 0.0F);
        }));
        // 3. the Dyson sphere: the iris opening on the red star, pushing in;
        takes.add(Take.cut(ProximaPlan.IRIS, ProximaPlan.LOOPS, (x, p, k) -> {
            Vec3 pos = x.feet(p).add(0.0, 300.0, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), star, (float) l(k, 24.0, 16.0), (float) l(k, 0.0, 2.0));
        }));
        // 4. the loops twisting into an infinity sign, flaring and collapsing, closer still;
        takes.add(Take.cut(ProximaPlan.LOOPS, ProximaPlan.LENS, (x, p, k) -> {
            Vec3 pos = x.feet(p).add(0.0, 300.0, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), star, (float) l(k, 16.0, 12.0), 2.0F);
        }));
        // 5. the lens and the beam fired across the sky, pulling back;
        takes.add(Take.cut(ProximaPlan.LENS, ProximaPlan.EARTH, (x, p, k) -> {
            Vec3 pos = x.feet(p).add(0.0, 300.0, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), star, (float) l(k, 14.0, 26.0), (float) l(k, 2.0, 0.0));
        }));
        // 6. Earth from space (client/proxima/EarthSphere hangs it behind the gate as this camera sees it): the beam
        //    streaking in from above, the gate opening before Earth and catching it, pushing in;
        takes.add(Take.cut(ProximaPlan.EARTH, ProximaPlan.FLASH - 7, (x, p, k) -> {
            Vec3 base = x.feet(p).add(0.0, 300.0, 0.0);
            double e = k * k * (3.0 - 2.0 * k);
            Vec3 pos = base.add(gate.subtract(base).normalize().scale(l((float) e, 0.0, 180.0)));
            return new Pose(x.keep(pos, x.feet(p)), gate, (float) l((float) e, 40.0, 33.0), (float) l(k, -3.0, 0.0));
        }));
        // 7. back on the land, looking up at the gate in the sky as it catches the beam: the flash.
        takes.add(Take.cut(ProximaPlan.FLASH - 7, ProximaPlan.FLASH + 30, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 pos = frame(x, feet, 0.6, 1.7, -1.2);
            return new Pose(x.keep(pos, feet), gate, (float) l(k, 58.0, 68.0), 0.0F);
        }));

        // 3. Low beside the mark, looking out the way the front will come: the coronal mass ejection rising over the
        //    horizon as a band of light across the sky.
        takes.add(Take.cut(ProximaPlan.FLASH + 30, ProximaPlan.FRONT + 50, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(bowl * 0.8)).add(from.scale(-bowl * 0.5)).add(0.0, 2.4, 0.0);
            Vec3 nose = nose(plan, x.time(p));
            return new Pose(x.keep(pos, x.feet(p)), pos.lerp(nose, 0.5).add(0.0, 40.0, 0.0), (float) l(k, 78.0, 72.0), 0.0F);
        }));

        // 4. The long approach: a slow turn high over the land, the front's leading edge sweeping down the sky toward it.
        takes.add(Take.cut(ProximaPlan.FRONT + 50, ProximaPlan.HUSH - 15, (x, p, k) -> {
            double angle = Math.toRadians(l(k, -30.0, 25.0));
            Vec3 around = side.scale(Math.cos(angle)).add(from.scale(-Math.sin(angle)));
            Vec3 pos = mark.add(around.scale(scorch * 0.9)).add(0.0, scorch * l(k, 0.35, 0.22), 0.0);
            Vec3 look = mark.lerp(nose(plan, x.time(p)), 0.45);
            return new Pose(x.keep(pos, x.feet(p)), look, (float) l(k, 70.0, 80.0), 0.0F);
        }));

        // 5. The hush: on the ground at the mark's edge, looking straight up into the white-hot nose coming down.
        takes.add(Take.cut(ProximaPlan.HUSH - 15, ProximaPlan.IMPACT, (x, p, k) -> {
            Vec3 pos = mark.add(from.scale(-bowl * 1.6)).add(0.0, 2.0, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), nose(plan, x.time(p)), (float) l(k, 76.0, 92.0), (float) l(k, 0.0, 4.0));
        }));

        // 6. Ground zero: a hard cut wide and three-quarters on, the white point, the cracks and the wall rising.
        Vec3 quarter = side.scale(0.7).add(from.scale(-0.7)).normalize();
        takes.add(Take.cut(ProximaPlan.IMPACT, ProximaPlan.IMPACT + 34, (x, p, k) -> {
            double e = k * k * (3.0 - 2.0 * k);
            Vec3 pos = mark.add(quarter.scale(l((float) e, bowl * 2.4, bowl * 3.2))).add(0.0, l((float) e, bowl * 0.5, bowl * 1.0), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), mark.add(0.0, bowl * 0.3, 0.0), (float) l((float) e, 66.0, 74.0), 0.0F);
        }));

        // 7. The wave: on the land ahead of it, low, as the wall of fire rolls out over the land toward and past the camera.
        takes.add(Take.cut(ProximaPlan.IMPACT + 34, ProximaPlan.IMPACT + 100, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(scorch * 0.75)).add(0.0, 3.0, 0.0);
            double wave = plan.waveRadius(x.time(p));
            Vec3 look = mark.add(side.scale(Math.max(0.0, wave - 20.0))).add(0.0, 8.0, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), look, (float) l(k, 74.0, 84.0), 0.0F);
        }));

        // 8. Afterwards: rising high over the scorched land, the glassy bowl smoking in the middle.
        Vec3 high = side.scale(-0.6).add(from.scale(0.8)).normalize();
        takes.add(Take.sweep(ProximaPlan.IMPACT + 100, END, 10, (x, p, k) -> {
            double angle = Math.toRadians(l(k, 0.0, 30.0));
            Vec3 around = high.scale(Math.cos(angle)).add(new Vec3(-high.z, 0.0, high.x).scale(Math.sin(angle)));
            // close enough that the bowl stays out of the fog (at 0.5-0.75 x the scorch it was lost in it)
            Vec3 pos = mark.add(around.scale(scorch * l(k, 0.3, 0.45))).add(0.0, scorch * l(k, 0.18, 0.32), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), mark, (float) l(k, 66.0, 72.0), 0.0F);
        }));
        return takes;
    }

    /** The front's white-hot nose at {@code t} (on the mark from the impact on). */
    private static Vec3 nose(ProximaPlan plan, double t) {
        return plan.target.subtract(plan.frontDirection().scale(plan.frontDistance(t)));
    }

    private static double l(float k, double a, double b) {
        return a + (b - a) * k;
    }

    private static Vec3 frame(Stage x, Vec3 anchor, double r, double u, double f) {
        Vec3 fw = x.forward();
        Vec3 rt = new Vec3(-fw.z, 0.0, fw.x);
        return anchor.add(rt.x * r + fw.x * f, u, rt.z * r + fw.z * f);
    }
}
