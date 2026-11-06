package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.CameraPose;
import cyou.rimuru.rimulib.client.cinematic.Cutscene;
import cyou.rimuru.rimulib.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.client.cinematic.Shot;
import cyou.rimuru.rimulib.client.cinematic.Subject;
import dev.ss05.halley.client.FilmPanels;
import dev.ss05.halley.client.film.FilmLand;
import dev.ss05.halley.compat.tss.ConvergenceFx;
import dev.ss05.halley.compat.tss.TssLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Stellar Convergence's own film (the user's pick: a panel montage): over the caster's shoulder at the mark, a high
 * turn round it while the sigil forms, then quick cuts to each weapon through its own film's camera (the films the
 * weapons would have played, held back by {@link ConvergenceClient}, keep running unseen and lend their cameras), each
 * shot freezing into a comic panel that slides into a grid ({@link FilmPanels}) until the screen is full; at the hit the
 * grid shatters onto a wide shot of everything landing at once.
 */
final class ConvergenceFilm {
    /** How long the film runs on after the hit. */
    private static final int AFTER = 70;

    private static Cutscene current;
    private static int hit;
    private static int montage;
    private static int[] colors = new int[0];

    private ConvergenceFilm() {
    }

    static boolean is(Cutscene film) {
        return film != null && film == current;
    }

    /** The caster's own convergence is on its way: its film, if they watch films. */
    static void start(ConvergenceFx fx) {
        end();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !CutsceneDirector.automatic()) {
            return;
        }
        int n = Math.max(1, fx.colors().length);
        colors = fx.colors();
        hit = (int) Math.max(40, fx.hitAt() - minecraft.level.getGameTime());
        // the cuts: about 14 ticks a weapon, in the last seconds before the hit
        montage = Mth.clamp(n * 14, 40, Math.min(110, hit - 20));
        Vec3 target = fx.target();
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z).lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(look.x, 0.0, look.z).normalize();
        // how far out to film from: the spread of the ring (or the mark alone)
        double spread = 0.0;
        for (Vec3 point : fx.points()) {
            spread = Math.max(spread, Math.hypot(point.x - target.x, point.z - target.z));
        }
        double reach = Math.max(40.0, spread * 1.2 + 30.0);
        Subject subject = new Subject() {
            @Override
            public Vec3 feet(float partial) {
                return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getPosition(partial) : target;
            }

            @Override
            public Vec3 target() {
                return target;
            }

            @Override
            public Vec3 forward() {
                return forward;
            }
        };

        List<Shot> shots = new ArrayList<>();
        int intro = Math.min(30, (hit - montage) / 2);
        // 1. over the caster's shoulder, the mark on the crosshair
        shots.add(Shot.cut(0, intro, (s, partial, k) -> {
            Vec3 eye = s.feet(partial).add(0.0, 1.62, 0.0);
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            Vec3 pos = eye.add(forward.scale(-2.6)).add(right.scale(0.9)).add(0.0, 0.4, 0.0);
            return new CameraPose(pos, target.add(0.0, Mth.lerp(k, 12.0, 2.0), 0.0), Mth.lerp(k, 55.0F, 40.0F), 0.0F);
        }));
        // 2. high over the mark, turning slowly round it while the sigil lights weapon by weapon
        shots.add(Shot.sweep(intro, hit - montage, 8, (s, partial, k) -> {
            double a = Math.atan2(-forward.z, -forward.x) + Math.toRadians(Mth.lerp(k, -40.0, 50.0));
            double r = reach * 1.9;
            Vec3 pos = target.add(Math.cos(a) * r, Mth.lerp(k, reach * 1.4, reach * 1.0), Math.sin(a) * r);
            return new CameraPose(FilmLand.keep(pos, s.feet(partial)), target.add(0.0, 4.0, 0.0), Mth.lerp(k, 62.0F, 70.0F), 0.0F);
        }));
        // 3. each weapon through its own film's camera, in the order they were called
        for (int i = 0; i < n; i++) {
            int index = i;
            int from = hit - montage + montage * i / n;
            int to = hit - montage + montage * (i + 1) / n;
            shots.add(Shot.cut(from, to, (s, partial, k) -> weaponCamera(index, partial, target, forward, reach, k)));
        }
        // 4. the hit, from high and wide, rising as everything lands at once
        shots.add(Shot.cut(hit, hit + AFTER, (s, partial, k) -> {
            Vec3 back = forward.scale(-reach * 2.2);
            Vec3 pos = target.add(back).add(0.0, Mth.lerp(k, reach * 1.3, reach * 2.0), 0.0);
            return new CameraPose(FilmLand.keep(pos, s.feet(partial)), target.add(0.0, Mth.lerp(k, 8.0, 0.0), 0.0),
                Mth.lerp(k, 70.0F, 78.0F), 0.0F);
        }));

        FilmPanels.begin(n);
        FilmPanels.frame = ConvergenceFilm::frame;
        current = new Cutscene(subject, TssLink.CONVERGENCE.tier(), TssLink.CONVERGENCE.title(), TssLink.CONVERGENCE.color(), 0, shots)
            .hardCut()
            .fullFrame()
            .priority(3);
        CutsceneDirector.play(current);
    }

    /** Weapon {@code i}'s own film's camera now, or (before it has one) a low look up from beside the mark. */
    private static CameraPose weaponCamera(int i, float partial, Vec3 target, Vec3 forward, double reach, float k) {
        List<Cutscene> held = ConvergenceClient.held();
        if (i < held.size()) {
            try {
                Cutscene film = held.get(i);
                if (film.started() && !film.finished()) {
                    return film.pose(partial);
                }
            } catch (RuntimeException ignored) {
                // its film can't say (it may be over): the stand-in below
            }
        }
        double a = Math.PI * 2.0 * i / 7.0;
        Vec3 pos = target.add(Math.cos(a) * reach * 0.6, 3.0, Math.sin(a) * reach * 0.6);
        Vec3 home = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : target;
        return new CameraPose(FilmLand.keep(pos, home), target.add(0.0, 30.0 + 20.0 * k, 0.0), 75.0F, 0.0F);
    }

    /**
     * At the end of each frame while the film plays ({@link FilmPanels#frame}): freezes the shot being cut away from into
     * its panel, and draws the panels over the finished picture.
     */
    private static void frame(GuiGraphicsExtractor g) {
        if (current == null) {
            return;
        }
        if (current.finished()) {
            end();
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float t = current.time(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        int n = colors.length;
        int next = FilmPanels.frozen();
        // the last frame of weapon "next"'s shot: freeze it before the cut
        if (next < n && t >= hit - montage + montage * (next + 1) / (float) n - 1.0F) {
            FilmPanels.freeze(g, colors[next], t);
        }
        FilmPanels.draw(g, w, h, t, hit);
    }

    static void end() {
        current = null;
        FilmPanels.frame = null;
        FilmPanels.clear();
    }
}
