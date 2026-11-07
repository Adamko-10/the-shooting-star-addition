package dev.ss05.halley.client.film;

import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.film.MoonStoryboard.Pose;
import dev.ss05.halley.client.film.MoonStoryboard.Stage;
import dev.ss05.halley.client.film.MoonStoryboard.Take;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * SA-04 Proxima's film (the approved previz, 36 s; docs/SS-07-PROXIMA.md), its shots in the world, on the strike's
 * clock, in Luna's film types (see {@link MoonStoryboard} for how {@code k} and {@code stage} work). From
 * {@link ProximaPlan#ORBIT} to the flash the camera waits high over the caster: this port defers ProximaCosmos's space
 * scene and keeps that world view visible. Every camera is kept over land this
 * game has loaded and out of the ground ({@link Stage#keep}).
 */
public final class ProximaStoryboard {
    /** When the last shot ends: the remote hands the view back to the caster from there. */
    public static final int END = ProximaPlan.IMPACT + 210;
    /** How high the pull up off the caster's shoulder goes (through the clouds) before the cut to orbit. */
    private static final double RISE = 480.0;

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

        // 1. Uplink: over the caster's shoulder, close on the remote in their hand.
        takes.add(Take.cut(0, ProximaPlan.ASCENT, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 hand = frame(x, feet, 0.36, 1.38, 0.62);
            Vec3 pos = frame(x, feet, l(k, 1.3, 1.08), l(k, 1.72, 1.56), l(k, 2.0, 1.75));
            return new Pose(pos, hand, (float) l(k, 46.0, 40.0), 0.0F);
        }));

        // 2. Leaving Earth, the Railgun's way (docs: "The Railgun reference"): a whip turn to look straight down (0.15 s),
        //    then the pull straight up, easing in, then faster and faster through the clouds, drifting a little sideways;
        //    at ORBIT it cuts to Earth from orbit (ProximaCosmos) under a white punch through the cloud layer.
        takes.add(Take.cut(ProximaPlan.ASCENT, ProximaPlan.ORBIT, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 fw = x.forward();
            Vec3 across = new Vec3(-fw.z, 0.0, fw.x);
            double secs = k * (ProximaPlan.ORBIT - ProximaPlan.ASCENT) / 20.0;
            double whip = smooth(secs / 0.15);
            double rise = rise(secs);
            double height = riseHeight(ProximaPlan.ASCENT + secs * 20.0);
            Vec3 pos = feet.add(fw.scale(-1.2 * (1.0 - whip))).add(across.scale(20.0 * rise * rise)).add(0.0, height, 0.0);
            double pitch = Math.toRadians(l((float) whip, 20.0, 87.0));
            Vec3 look = pos.add(fw.scale(Math.cos(pitch))).add(0.0, -Math.sin(pitch), 0.0);
            return new Pose(x.keep(pos, feet), look, (float) l((float) rise, 70.0, 64.0), 0.0F);
        }));
        // World-camera fallback while ProximaCosmos is deferred: wait where the pull up ended.
        takes.add(Take.cut(ProximaPlan.ORBIT, ProximaPlan.FLASH, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 fw = x.forward();
            Vec3 pos = feet.add(new Vec3(-fw.z, 0.0, fw.x).scale(20.0)).add(0.0, RISE, 0.0);
            return new Pose(x.keep(pos, feet), pos.add(fw.scale(0.05)).add(0.0, -1.0, 0.0), 64.0F, 0.0F);
        }));

        // 8. The flash: back on the land beside the caster, looking up the way the front will come as the sky burns.
        takes.add(Take.cut(ProximaPlan.FLASH, ProximaPlan.FRONT, (x, p, k) -> {
            Vec3 feet = x.feet(p);
            Vec3 pos = feet.add(x.forward().scale(-1.1)).add(0.0, 1.7, 0.0);
            Vec3 look = feet.add(from.scale(40.0)).add(0.0, 30.0, 0.0);
            return new Pose(x.keep(pos, feet), look, (float) l(k, 62.0, 70.0), 0.0F);
        }));

        // 9. The front: low beside the mark, looking out the way it comes, the coronal mass ejection coming over the
        //    horizon as a band of light across the sky...
        takes.add(Take.cut(ProximaPlan.FRONT, ProximaPlan.FRONT + 48, (x, p, k) -> {
            Vec3 pos = mark.add(side.scale(bowl * 0.8)).add(from.scale(-bowl * 0.5)).add(0.0, 2.4, 0.0);
            Vec3 nose = nose(plan, x.time(p));
            return new Pose(x.keep(pos, x.feet(p)), pos.lerp(nose, 0.5).add(0.0, 40.0, 0.0), (float) l(k, 78.0, 72.0), 0.0F);
        }));

        //    ...then a slow turn high over the land, the front's leading edge sweeping down the sky toward it.
        takes.add(Take.cut(ProximaPlan.FRONT + 48, ProximaPlan.HUSH, (x, p, k) -> {
            double angle = Math.toRadians(l(k, -30.0, 25.0));
            Vec3 around = side.scale(Math.cos(angle)).add(from.scale(-Math.sin(angle)));
            Vec3 pos = mark.add(around.scale(scorch * 0.9)).add(0.0, scorch * l(k, 0.35, 0.22), 0.0);
            Vec3 look = mark.lerp(nose(plan, x.time(p)), 0.45);
            return new Pose(x.keep(pos, x.feet(p)), look, (float) l(k, 70.0, 80.0), 0.0F);
        }));

        // 10. The hush: on the ground at the mark's edge, looking straight up into the white-hot nose coming down.
        takes.add(Take.cut(ProximaPlan.HUSH, ProximaPlan.IMPACT, (x, p, k) -> {
            Vec3 pos = mark.add(from.scale(-bowl * 1.6)).add(0.0, 2.0, 0.0);
            return new Pose(x.keep(pos, x.feet(p)), nose(plan, x.time(p)), (float) l(k, 76.0, 92.0), (float) l(k, 0.0, 4.0));
        }));

        // 11. Ground zero: a hard cut wide and three-quarters on, the white point, the cracks and the wall rising.
        Vec3 quarter = side.scale(0.7).add(from.scale(-0.7)).normalize();
        takes.add(Take.cut(ProximaPlan.IMPACT, ProximaPlan.IMPACT + 32, (x, p, k) -> {
            double e = k * k * (3.0 - 2.0 * k);
            Vec3 pos = mark.add(quarter.scale(l((float) e, bowl * 2.4, bowl * 3.2))).add(0.0, l((float) e, bowl * 0.5, bowl * 1.0), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), mark.add(0.0, bowl * 0.3, 0.0), (float) l((float) e, 66.0, 74.0), 0.0F);
        }));

        // 12. The shock wave (6 s): pulling back and rising, slowly, as the wall of fire rolls out over the land toward
        //     the camera, blasting the trees it reaches, passes under it and runs on to the scorch's edge.
        takes.add(Take.cut(ProximaPlan.IMPACT + 32, ProximaPlan.IMPACT + 152, (x, p, k) -> {
            double e = k * k * (3.0 - 2.0 * k);
            double wave = plan.waveRadius(x.time(p));
            Vec3 pos = mark.add(quarter.scale(l((float) e, scorch * 0.8, scorch * 1.05))).add(0.0, l((float) e, scorch * 0.1, scorch * 0.3), 0.0);
            Vec3 look = mark.add(quarter.scale(wave * 0.45)).add(0.0, l((float) e, bowl * 0.4, 4.0), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), look, (float) l((float) e, 76.0, 66.0), (float) (2.0 * Math.sin(k * Math.PI)));
        }));

        // 13. Afterwards: rising high over the scorched land, the glassy bowl smoking in the middle.
        Vec3 high = side.scale(-0.6).add(from.scale(0.8)).normalize();
        takes.add(Take.sweep(ProximaPlan.IMPACT + 152, END, 10, (x, p, k) -> {
            double angle = Math.toRadians(l(k, 0.0, 30.0));
            Vec3 around = high.scale(Math.cos(angle)).add(new Vec3(-high.z, 0.0, high.x).scale(Math.sin(angle)));
            // close enough that the bowl stays out of the fog (at 0.5-0.75 x the scorch it was lost in it)
            Vec3 pos = mark.add(around.scale(scorch * l(k, 0.3, 0.45))).add(0.0, scorch * l(k, 0.18, 0.32), 0.0);
            return new Pose(x.keep(pos, x.feet(p)), mark, (float) l(k, 66.0, 72.0), 0.0F);
        }));
        return takes;
    }

    /**
     * How high over the caster's feet the pull up is at film tick {@code ct}: easing in, then faster and faster (the
     * Railgun's: about a second from the ground to above the clouds), {@link #RISE} at the cut to orbit.
     */
    public static double riseHeight(double ct) {
        double r = rise((ct - ProximaPlan.ASCENT) / 20.0);
        return 2.2 + (RISE - 2.2) * (Math.exp(5.0 * r) - 1.0) / (Math.exp(5.0) - 1.0);
    }

    /** How far through the rise {@code secs} after it starts (the first 0.1 s is the whip turn to look down). */
    private static double rise(double secs) {
        return Math.min(1.0, Math.max(0.0, (secs - 0.1) / 1.2));
    }

    /** The front's white-hot nose at {@code t} (on the mark from the impact on). */
    private static Vec3 nose(ProximaPlan plan, double t) {
        return plan.target.subtract(plan.frontDirection().scale(plan.frontDistance(t)));
    }

    private static double smooth(double x) {
        double k = Math.min(1.0, Math.max(0.0, x));
        return k * k * (3.0 - 2.0 * k);
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
