package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.CameraPose;
import cyou.rimuru.rimulib.client.cinematic.Cutscene;
import cyou.rimuru.rimulib.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.client.cinematic.Shot;
import cyou.rimuru.rimulib.client.cinematic.Subject;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.FilmPanels;
import dev.ss05.halley.client.FilmSplit;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.render.ShaderPacks;
import dev.ss05.halley.compat.tss.Convergence;
import dev.ss05.halley.client.film.FilmLand;
import dev.ss05.halley.compat.tss.ConvergenceFx;
import dev.ss05.halley.compat.tss.TssLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Stellar Convergence's own film (the user's pick: a panel montage): over the caster's shoulder at the mark, a high
 * turn round it while the sigil forms, then quick cuts to each weapon through its own film's camera (the films the
 * weapons would have played, held back by {@link ConvergenceClient}, keep running unseen and lend their cameras), each
 * shot freezing into a comic panel that slides into a grid ({@link FilmPanels}) until the screen is full; at the hit the
 * grid shatters onto a wide shot of everything landing at once. Or (1.9.0, a setting: F on its card) the split screen:
 * for the montage the screen is split into a live part per weapon ({@link FilmSplit}), the camera visiting the weapons'
 * cameras in turn, one a frame, each part keeping its weapon's last picture.
 */
final class ConvergenceFilm {
    /** How long the film runs on after the hit. */
    private static final int AFTER = 70;
    /**
     * How long the split screen stays after the hit (Adam, polish 9: "the view disappears a little early still"): the
     * parts show the weapons landing, then fly apart onto the wide shot.
     */
    private static final int SPLIT_AFTER = 40;

    private static Cutscene current;
    private static int hit;
    private static int montage;
    private static int[] colors = new int[0];
    private static boolean shattered;
    /** The split screen this time (else the comic panels), and the weapon whose camera this frame shows in it. */
    private static boolean split;
    private static int visiting;
    private static int framesHere;
    /** The convergence's weapons, in the order the server sent them (null where the client can't tell which it is). */
    private static List<cyou.rimuru.rimulib.skill.Skill> weaponSkills = List.of();
    private static Vec3[] splitPoints = new Vec3[0];
    private static Vec3 splitForward = new Vec3(0.0, 0.0, 1.0);
    /** The next part (other than the live one) to refresh between the live one's frames. */
    private static int nextOther;

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
        shattered = false;
        split = HalleyClientConfig.convergenceFilm() == HalleyClientConfig.ConvergenceFilm.SPLIT && fx.colors().length > 0;
        visiting = 0;
        framesHere = 0;
        hit = (int) Math.max(40, fx.hitAt() - minecraft.level.getGameTime());
        // the cuts: about 14 ticks a weapon, in the last seconds before the hit (the split screen: a longer look at all
        // of them at once)
        // (Adam, after the first split screen: "make live view better and last longer"): a part each in turn live
        montage = split ? Mth.clamp(100 + n * 30, 40, Math.min(320, hit - 30)) : Mth.clamp(n * 14, 40, Math.min(110, hit - 20));
        Vec3 target = fx.target();
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z).lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(look.x, 0.0, look.z).normalize();
        // how far out to film from: the spread of the ring (or the mark alone)
        double spread = 0.0;
        for (Vec3 point : fx.points()) {
            spread = Math.max(spread, Math.hypot(point.x - target.x, point.z - target.z));
        }
        double reach = Math.max(40.0, spread * 1.2 + 30.0);
        // the far shots stay within the render distance (a wide ring put the camera ~1000 blocks out, where only the sky
        // and SS-21 Quasar's sky-high jet were left: Adam's polish 8 and 9 screenshots)
        double far = Math.max(96.0, minecraft.options.getEffectiveRenderDistance() * 16.0 * 0.75);
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
            Vec3 pos = within(target, new Vec3(Math.cos(a) * r, Mth.lerp(k, reach * 1.4, reach * 1.0), Math.sin(a) * r), far);
            return new CameraPose(FilmLand.keep(pos, s.feet(partial)), target.add(0.0, 4.0, 0.0), Mth.lerp(k, 62.0F, 70.0F), 0.0F);
        }));
        // 3. each weapon through its own film's camera, in the order they were called (the split screen: one shot, the
        // camera visiting them in turn, one a frame)
        if (split) {
            splitPoints = fx.points();
            splitForward = forward;
            nextOther = 0;
            shots.add(Shot.cut(hit - montage, hit + SPLIT_AFTER, (s, partial, k) -> splitCamera(visiting, partial, target, forward, reach, k)));
            dev.ss05.halley.client.render.FilmEye.next = () -> {
                Minecraft mc = Minecraft.getInstance();
                float partial = mc.getFrameTime();
                return current != null && current.time(partial) >= hit - montage && current.time(partial) < hit + SPLIT_AFTER
                    ? splitCamera(visiting, partial, target, forward, reach, 0.5F).position() : null;
            };
        } else {
            for (int i = 0; i < n; i++) {
                int index = i;
                int from = hit - montage + montage * i / n;
                int to = hit - montage + montage * (i + 1) / n;
                shots.add(Shot.cut(from, to, (s, partial, k) -> weaponCamera(index, partial, target, forward, reach, k)));
            }
        }
        // 4. the hit, from high and wide, rising as everything lands at once (after the split screen's last look)
        int wide = split ? hit + SPLIT_AFTER : hit;
        shots.add(Shot.cut(wide, wide + AFTER, (s, partial, k) -> {
            Vec3 back = forward.scale(-reach * 2.2);
            Vec3 pos = within(target, back.add(0.0, Mth.lerp(k, reach * 1.3, reach * 2.0), 0.0), far);
            return new CameraPose(FilmLand.keep(pos, s.feet(partial)), target.add(0.0, Mth.lerp(k, 8.0, 0.0), 0.0),
                Mth.lerp(k, 70.0F, 78.0F), 0.0F);
        }));

        FilmPanels.begin(split ? 0 : n);
        weaponSkills = skills(fx.colors());
        if (split) {
            List<String> titles = new ArrayList<>();
            for (cyou.rimuru.rimulib.skill.Skill skill : weaponSkills) {
                titles.add(skill == null ? "" : skill.title());
            }
            FilmSplit.begin(fx.colors(), titles);
        }
        FilmPanels.frame = ConvergenceFilm::frame;
        dev.ss05.halley.client.FilmView.frameStart = ConvergenceFilm::frameStart;
        current = new Cutscene(subject, TssLink.CONVERGENCE.tier(), TssLink.CONVERGENCE.title(), TssLink.CONVERGENCE.color(), 0, shots)
            .hardCut()
            .fullFrame()
            .priority(3);
        CutsceneDirector.play(current);
    }

    /** {@code target} moved by {@code offset}, the offset shortened to {@code far} at most. */
    private static Vec3 within(Vec3 target, Vec3 offset, double far) {
        double length = offset.length();
        return target.add(length > far ? offset.scale(far / length) : offset);
    }

    /**
     * The weapons this convergence fires, in the order the server sent them: each found among the player's ticked ones by
     * its colour (the server sends their colours; in a ring it sorts them by size, so the menu's order won't do); null
     * where none matches.
     */
    private static List<cyou.rimuru.rimulib.skill.Skill> skills(int[] colors) {
        List<cyou.rimuru.rimulib.skill.Skill> ticked = new ArrayList<>();
        try {
            java.util.List<String> ids = HalleyClientConfig.convergence();
            for (cyou.rimuru.rimulib.skill.Skill skill : ConvergencePlate.weapons()) {
                if (ids.contains(skill.id()) && !Convergence.finale(skill)) {
                    ticked.add(skill);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            ticked.clear();
        }
        List<cyou.rimuru.rimulib.skill.Skill> skills = new ArrayList<>();
        for (int color : colors) {
            cyou.rimuru.rimulib.skill.Skill found = null;
            for (cyou.rimuru.rimulib.skill.Skill skill : ticked) {
                if ((skill.color() & 0xFFFFFF) == (color & 0xFFFFFF)) {
                    found = skill;
                    break;
                }
            }
            if (found != null) {
                ticked.remove(found);
            }
            skills.add(found);
        }
        return skills;
    }

    /**
     * The weapon's own film, held back while the convergence is on its way: found by the weapon's colour or title (the
     * films are held in the order they started, which isn't the server's order); by position if the weapon is unknown.
     */
    private static Cutscene heldFilm(int i) {
        List<Cutscene> held = ConvergenceClient.held();
        cyou.rimuru.rimulib.skill.Skill skill = i < weaponSkills.size() ? weaponSkills.get(i) : null;
        if (skill == null) {
            return i < held.size() ? held.get(i) : null;
        }
        for (Cutscene film : held) {
            if (film.title != null && film.title.equals(skill.title())) {
                return film;
            }
        }
        for (Cutscene film : held) {
            if ((film.color & 0xFFFFFF) == (skill.color() & 0xFFFFFF)) {
                return film;
            }
        }
        return null;
    }

    /**
     * The split screen's camera on weapon {@code i} (Adam: "u cant even see the skill that its supposed to be in the split
     * screen"): our own skills framed on their own thing (Halley's comet, Luna's moon, Proxima's gate and then its bowl),
     * zoomed to fit it; the others through their own film's camera, as the comic panels are.
     */
    private static CameraPose splitCamera(int i, float partial, Vec3 target, Vec3 forward, double reach, float k) {
        cyou.rimuru.rimulib.skill.Skill skill = i < weaponSkills.size() ? weaponSkills.get(i) : null;
        Vec3 mark = i < splitPoints.length && splitPoints[i] != null ? splitPoints[i] : target;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            int me = minecraft.player != null ? minecraft.player.getId() : -1;
            if (skill == TssLink.SKILL) {
                dev.ss05.halley.client.HalleyFx fx = newest(dev.ss05.halley.client.HalleyFx.active(), f -> f.casterId == me);
                if (fx != null) {
                    double t = fx.time(partial);
                    double size = 45.0 * Math.max(0.3, fx.plan.params.scale());
                    return lookAt(eye(fx.plan.target, forward, 70.0 + size, 14.0), fx.plan.comet(Math.min(t, dev.ss05.halley.HalleyPlan.TOUCHDOWN + 10.0)),
                        size, 14.0F, 70.0F, fx.plan.target);
                }
            } else if (skill == TssLink.MOON) {
                dev.ss05.halley.client.luna.MoonFx fx = newest(dev.ss05.halley.client.luna.MoonFx.active(), f -> f.casterId == me);
                if (fx != null) {
                    double radius = fx.plan.params.moonRadius();
                    return lookAt(eye(fx.plan.target, forward, 90.0 + radius, 20.0), fx.plan.centre(fx.time(partial)), radius * 1.7,
                        16.0F, 75.0F, fx.plan.target);
                }
            } else if (skill == TssLink.PROXIMA) {
                dev.ss05.halley.client.proxima.ProximaFx fx = newest(dev.ss05.halley.client.proxima.ProximaFx.active(), f -> true);
                if (fx != null) {
                    boolean gate = fx.time(partial) < dev.ss05.halley.ProximaPlan.FLASH + 10;
                    Vec3 at = gate ? dev.ss05.halley.client.proxima.ProximaCosmos.gatePosition(fx.plan) : fx.plan.target;
                    return lookAt(eye(fx.plan.target, forward, 110.0, 24.0), at, gate ? 130.0 : 60.0, 18.0F, 75.0F, fx.plan.target);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // the framing can't be worked out: the weapon's own film camera below
        }
        // another mod's weapon: its own film's camera while that is in the world; when its film is out in space (drawn
        // in that mod's own film pass, so only grey here) or far off, a look at where it lands and the sky above it
        CameraPose film = weaponCamera(i, partial, mark, forward, reach, k);
        if (film.position().distanceTo(mark) < 500.0 && film.position().y < mark.y + 260.0) {
            return film;
        }
        double a = Math.PI * 2.0 * i / Math.max(1, splitPoints.length) + 0.6;
        Vec3 side = new Vec3(Math.cos(a), 0.0, Math.sin(a));
        Vec3 home = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : mark;
        Vec3 pos = FilmLand.keep(mark.add(side.scale(70.0)).add(forward.scale(-40.0)).add(0.0, 14.0, 0.0), home);
        return new CameraPose(pos, mark.add(0.0, 26.0 + 30.0 * k, 0.0), 62.0F, 0.0F);
    }

    private static <T> T newest(List<T> active, java.util.function.Predicate<T> mine) {
        for (int i = active.size() - 1; i >= 0; i--) {
            if (mine.test(active.get(i))) {
                return active.get(i);
            }
        }
        return null;
    }

    /** A camera back from {@code mark} along the caster's line, {@code back} out and {@code up} above it. */
    private static Vec3 eye(Vec3 mark, Vec3 forward, double back, double up) {
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 pos = mark.add(forward.scale(-back)).add(right.scale(back * 0.35)).add(0.0, up, 0.0);
        Vec3 home = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : mark;
        return FilmLand.keep(pos, home);
    }

    /** From {@code eye} at {@code at}, zoomed so something {@code size} across fills about half the part. */
    private static CameraPose lookAt(Vec3 eye, Vec3 at, double size, float minFov, float maxFov, Vec3 mark) {
        double distance = Math.max(1.0, eye.distanceTo(at));
        float fov = (float) Math.toDegrees(2.0 * Math.atan(size / distance));
        return new CameraPose(eye, at, Mth.clamp(fov, minFov, maxFov), 0.0F);
    }

    /** Weapon {@code i}'s own film's camera now, or (before it has one) a low look up from beside the mark. */
    private static CameraPose weaponCamera(int i, float partial, Vec3 target, Vec3 forward, double reach, float k) {
        try {
            Cutscene film = heldFilm(i);
            if (film != null && film.started() && !film.finished()) {
                return film.pose(partial);
            }
        } catch (RuntimeException ignored) {
            // its film can't say (it may be over): the stand-in below
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
    /** The weapons this convergence fires (those the client could tell), for the split screen's effect filter. */
    private static java.util.Set<cyou.rimuru.rimulib.skill.Skill> weaponSet() {
        java.util.Set<cyou.rimuru.rimulib.skill.Skill> set = new java.util.HashSet<>();
        for (cyou.rimuru.rimulib.skill.Skill skill : weaponSkills) {
            if (skill != null) {
                set.add(skill);
            }
        }
        return set;
    }

    /** Whether this frame is one of the split screen's (the montage, and a last look at the hit). */
    private static boolean splitting(float t) {
        return split && current != null && t >= hit - montage && t < hit + SPLIT_AFTER;
    }

    /** Which comic panel's shot this frame is (the panels style), or -1 outside the montage. */
    private static int panelShot(float t) {
        int n = colors.length;
        if (split || current == null || n == 0 || t < hit - montage || t >= hit) {
            return -1;
        }
        return Mth.clamp((int) ((t - (hit - montage)) * n / Math.max(1.0F, montage)), 0, n - 1);
    }

    /**
     * At the start of every frame while the film plays: in the split screen, this frame shows only the weapon whose part
     * the camera is on (the others' effects hidden: ours through FilmView, The Shooting Star's out of its list until the
     * HUD), and the sound listener stays put while that part isn't the live one.
     */
    private static void frameStart() {
        StarClientBridge.allSkills();
        dev.ss05.halley.client.FilmView.reset();
        capturedThisFrame = false;
        dev.ss05.halley.client.FilmView.filmPlaying = current != null && !current.finished();
        if (current == null) {
            return;
        }
        float t = current.time(Minecraft.getInstance().getFrameTime());
        // the weapon this frame shows: the part the camera is on (split screen), or the panel being filmed (Adam, polish 9:
        // "luna appears in almost every view")
        int shown = splitting(t) ? visiting : panelShot(t);
        if (shown < 0) {
            return;
        }
        cyou.rimuru.rimulib.skill.Skill skill = shown < weaponSkills.size() ? weaponSkills.get(shown) : null;
        if (skill == null) {
            return;
        }
        dev.ss05.halley.client.FilmView.only = skill == TssLink.SKILL ? dev.ss05.halley.client.FilmView.HALLEY
            : skill == TssLink.MOON ? dev.ss05.halley.client.FilmView.LUNA
            : skill == TssLink.PROXIMA ? dev.ss05.halley.client.FilmView.PROXIMA : 0;
        dev.ss05.halley.client.FilmView.holdListener = split && visiting != FilmSplit.liveIndex() && FilmSplit.liveIndex() >= 0;
        StarClientBridge.onlySkill(skill, weaponSet());
    }

    private static boolean capturedThisFrame;
    /** The split screen's frame rate, logged once at the hit (how smooth it ran on this computer). */
    private static long statsFrom;
    private static long statsLast;
    private static long statsWorst;
    private static int statsFrames;

    /** Just before the HUD: every effect back, and this frame's picture kept (before any HUD layer is drawn over it). */
    static void beforeHud() {
        StarClientBridge.allSkills();
        dev.ss05.halley.client.FilmView.reset();
        if (current == null || current.finished() || capturedThisFrame) {
            return;
        }
        capturedThisFrame = true;
        float t = current.time(Minecraft.getInstance().getFrameTime());
        if (split) {
            keepSplit(t);
        } else {
            keepPanel(t);
        }
    }

    /**
     * The split screen's picture of this frame: one part is live at a time, in turn (Adam: "its really low fps"): the
     * camera stays on it, keeping every frame, and every eighth frame or so visits one of the others so they keep
     * moving too (each visit costs a jump of the camera: fewer of them, smoother). With a shader pack the camera needs
     * a frame to settle after each switch (its smoothing over frames), and that frame isn't kept.
     */
    private static void keepSplit(float t) {
        if (!splitting(t)) {
            if (statsFrames > 0 && t >= hit + SPLIT_AFTER) {
                double seconds = (statsLast - statsFrom) / 1.0E9;
                HalleyAddon.LOG.info("Stellar Convergence's split screen: {} frames in {} s ({} fps), the slowest {} ms", statsFrames,
                    String.format(java.util.Locale.ROOT, "%.1f", seconds), String.format(java.util.Locale.ROOT, "%.0f",
                        seconds > 0.0 ? statsFrames / seconds : 0.0), statsWorst / 1_000_000L);
                statsFrames = 0;
            }
            return;
        }
        long now = System.nanoTime();
        if (statsFrames == 0) {
            statsFrom = now;
            statsWorst = 0L;
        } else {
            statsWorst = Math.max(statsWorst, now - statsLast);
        }
        statsLast = now;
        statsFrames++;
        int count = Math.max(1, FilmSplit.count());
        int live = Mth.clamp((int) ((t - (hit - montage)) / Math.max(1.0F, (montage + SPLIT_AFTER) / (float) count)), 0, count - 1);
        int settle = ShaderPacks.active() ? 2 : 1;
        framesHere++;
        if (visiting == live) {
            if (framesHere >= settle) {
                FilmSplit.capture(visiting);
            }
            if (count > 1 && framesHere >= settle + 7) {
                nextOther = (nextOther + 1) % count;
                if (nextOther == live) {
                    nextOther = (nextOther + 1) % count;
                }
                visiting = nextOther;
                framesHere = 0;
            }
        } else if (framesHere >= settle) {
            FilmSplit.capture(visiting);
            visiting = live;
            framesHere = 0;
        }
        FilmSplit.live(live);
    }

    /** The comic panels: the last frame of weapon "next"'s shot frozen before the cut. */
    private static void keepPanel(float t) {
        int n = colors.length;
        int next = FilmPanels.frozen();
        if (next < n && t >= hit - montage + montage * (next + 1) / (float) n - 1.0F) {
            Minecraft minecraft = Minecraft.getInstance();
            FilmPanels.freeze(new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()), colors[next], t);
            if (FilmPanels.frozen() > next) {
                sound("convergence_panel", 0.94F + 0.12F * (float) Math.random());
            }
        }
    }

    private static void frame(GuiGraphics g) {
        if (current == null) {
            return;
        }
        if (current.finished()) {
            end();
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float t = current.time(Minecraft.getInstance().getFrameTime());
        // (if the game skipped the before-HUD hook this frame, keep the picture now, as before 1.9.0)
        if (!capturedThisFrame) {
            capturedThisFrame = true;
            if (split) {
                keepSplit(t);
            } else {
                keepPanel(t);
            }
        }
        if (split) {
            if (!shattered && t >= hit + SPLIT_AFTER) {
                shattered = true;
                sound("convergence_shatter", 1.0F);
            }
            FilmSplit.draw(g, w, h, t, hit - montage, hit, hit + SPLIT_AFTER);
            return;
        }
        // the grid breaking apart as everything hits
        if (!shattered && t >= hit && FilmPanels.frozen() > 0) {
            shattered = true;
            sound("convergence_shatter", 1.0F);
        }
        FilmPanels.draw(g, w, h, t, hit);
    }

    /** One of the film's own sounds (1.9.0), heard as if from the camera, on the Players volume like the other films'. */
    private static void sound(String name, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(HalleyAddon.id(name), SoundSource.PLAYERS, 1.0F,
            pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    static void end() {
        current = null;
        FilmPanels.frame = null;
        FilmPanels.clear();
        FilmSplit.clear();
        split = false;
        dev.ss05.halley.client.render.FilmEye.next = null;
        dev.ss05.halley.client.FilmView.frameStart = null;
        dev.ss05.halley.client.FilmView.filmPlaying = false;
        dev.ss05.halley.client.FilmView.reset();
        StarClientBridge.allSkills();
    }
}
