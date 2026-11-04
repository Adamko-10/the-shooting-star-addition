package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.mc1211.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.mc1211.client.magic.SkillKeys;
import cyou.rimuru.rimulib.mc1211.client.magic.WhiteSpace;
import cyou.rimuru.rimulib.mc1211.client.text.Overlay;
import cyou.rimuru.rimulib.mc1211.net.RimuNet;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.mc1211.ll1lil1iill;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import dev.ss05.halley.compat.tss.ConvergenceFx;
import dev.ss05.halley.compat.tss.StrikeFx;
import dev.ss05.halley.net.ServerSettings;
import dev.ss05.halley.compat.tss.TssLink;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The client half of {@link TssLink}: the only client code that touches The Shooting Star. It catches Halley's and
 * Luna's effect packets ({@link StrikeFx}, through RimuLib's network) and starts their effects in the remote's effect
 * manager, and their films in RimuLib's director.
 *
 * <h2>When The Shooting Star updates</h2>
 * Fix the imports here and in the adapters. The small mixins in {@code compat/tss/mixin} (film overlay, menu plate)
 * switch themselves off if their targets move; the skills and their effects keep working without them.
 */
public final class StarClientBridge {
    private static boolean installed;

    private StarClientBridge() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            RimuNet.clientHandler(StrikeFx.PACKET, packet -> {
                StrikeFx strike = (StrikeFx) packet;
                if (strike.skill() == SkillSet.indexOf(TssLink.SKILL)) {
                    start(strike);
                } else if (strike.skill() == SkillSet.indexOf(TssLink.MOON)) {
                    startMoon(strike);
                }
            });
            RimuNet.clientHandler(ConvergenceFx.PACKET, packet -> ConvergenceClient.start((ConvergenceFx) packet));
            // once the server has said hello, tell it this player's Stellar Convergence picks
            Runnable before = ServerSettings.onReceived;
            ServerSettings.onReceived = () -> {
                before.run();
                ConvergencePlate.sync(true);
            };
            installed = true;
        } catch (Throwable failed) {
            HalleyAddon.LOG.error("Halley and Luna couldn't hook their effects into The Shooting Star; strikes will "
                + "happen but won't be shown. See compat/tss/client/StarClientBridge.java.", failed);
        }
    }

    /**
     * The Shooting Star's own effect packet for a strike, which its effects are built from: (skill, caster, origin,
     * target, yaw, seed, targets, flags, power, speed, width). Its class name is obfuscated since 1.5.0 and unmapped
     * (1.5.0: {@code l1l1i1lii1}; 1.5.1: {@code ll1lil1iill}, same fields).
     */
    static ll1lil1iill payload(StrikeFx s) {
        return new ll1lil1iill(s.skill(), s.casterId(), s.origin().x, s.origin().y, s.origin().z, s.target().x, s.target().y,
            s.target().z, s.yaw(), s.seed(), s.targets(), s.flags(), s.power(), s.speed(), 1.0F);
    }

    /** Whether this client plays the strike's film: the caster's own, or anyone's when they play it for everyone. */
    private static boolean films(boolean mine, StrikeFx strike) {
        return CutsceneDirector.automatic() && (mine || (strike.flags() & StrikeFx.FLAG_FOR_EVERYONE) != 0);
    }

    private static void start(StrikeFx strike) {
        HalleyFxAdapter fx = new HalleyFxAdapter(strike);
        FxManager.add(fx);
        if (films(fx.halley().mine, strike)) {
            CutsceneDirector.play(HalleyCutscene.of(fx));
        }
    }

    /** SS-06's effects (and its film, for the caster, or everyone near it when the caster plays it for everyone). */
    private static void startMoon(StrikeFx strike) {
        MoonFxAdapter fx = new MoonFxAdapter(strike);
        FxManager.add(fx);
        if (films(fx.moon().mine, strike)) {
            CutsceneDirector.play(MoonCutscene.of(fx));
        }
    }

    /**
     * From compat/mixin: SS-05's and SS-06's overlays on the caster's film (the remote draws its own skills' there):
     * the newest strike's only, as for the remote's own (see {@link #newestHudsOnly}).
     */
    public static void renderFilmHud(GuiGraphics graphics, float partial) {
        // called from both of FxManager's overlay passes (see FxManagerMixin); a frame has one GuiGraphics
        if (graphics == lastFilmHud) {
            return;
        }
        lastFilmHud = graphics;
        if (!HalleyFx.active().isEmpty()) {
            Overlay.begin(graphics);
            try {
                HalleyHud.film(graphics, HalleyFx.active().getLast(), partial, Overlay.width(graphics), Overlay.height(graphics),
                    world -> FxManager.project(world, graphics));
                FilmTitle.halley(graphics, HalleyFx.active().getLast(), partial, Overlay.width(graphics), Overlay.height(graphics),
                    world -> FxManager.project(world, graphics));
            } finally {
                Overlay.end(graphics);
            }
        }
        if (!MoonFx.active().isEmpty()) {
            Overlay.begin(graphics);
            try {
                MoonHud.film(graphics, MoonFx.active().getLast(), partial, Overlay.width(graphics), Overlay.height(graphics),
                    world -> FxManager.project(world, graphics));
                FilmTitle.luna(graphics, MoonFx.active().getLast(), partial, Overlay.width(graphics), Overlay.height(graphics),
                    world -> FxManager.project(world, graphics));
            } finally {
                Overlay.end(graphics);
            }
        }
    }

    private static GuiGraphics lastFilmHud;

    /** The remote's running effects, oldest first: FxManager's one static list, found by type (its name is obfuscated). */
    private static List<Object> running;
    private static boolean looked;
    /** The whole list while the older effects are taken out of it, else null. */
    private static List<Object> hidden;

    /**
     * From compat/mixin, before the remote draws its effects' overlays: only the newest effect of each kind keeps its
     * overlay (two Gungnirs at once, with no cooldown, drew two over each other; the user picked "newest only",
     * 2026-11-03). The older ones are taken out of the list while it draws and put back right after
     * ({@link #allHuds}); they go on running as before.
     */
    public static void newestHudsOnly() {
        allHuds();
        List<Object> list = running();
        if (list == null || list.size() < 2) {
            return;
        }
        Set<Class<?>> seen = new HashSet<>();
        List<Object> newest = new ArrayList<>(list.size());
        for (int i = list.size() - 1; i >= 0; i--) {
            if (seen.add(list.get(i).getClass())) {
                newest.add(0, list.get(i));
            }
        }
        if (newest.size() < list.size()) {
            hidden = new ArrayList<>(list);
            list.clear();
            list.addAll(newest);
        }
    }

    /** From compat/mixin, after the remote drew its overlays: the older effects go back into its list. */
    public static void allHuds() {
        if (hidden != null) {
            running.clear();
            running.addAll(hidden);
            hidden = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> running() {
        if (!looked) {
            looked = true;
            for (Field field : FxManager.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == List.class) {
                    try {
                        field.setAccessible(true);
                        running = (List<Object>) field.get(null);
                    } catch (ReflectiveOperationException | RuntimeException failed) {
                        HalleyAddon.LOG.warn("Couldn't find the remote's running effects; every overlay is drawn", failed);
                    }
                    break;
                }
            }
        }
        return running;
    }

    /**
     * Whether the remote's menu has a use for this key right now (from compat/mixin, before the addon's plate takes M
     * or C): a skill's key is being rebound, or it is the menu's, the films' or a skill's own key. When unsure, yes.
     */
    public static boolean menuUses(int key, int scancode) {
        try {
            if (WhiteSpace.binding() != null) {
                return true;
            }
            if (matches(SkillKeys.menu, key, scancode) || matches(SkillKeys.cutscenes, key, scancode)) {
                return true;
            }
            SkillSet remote = SkillSet.of(TssLink.SKILL);
            for (Skill skill : remote != null ? remote.skills : java.util.List.<Skill>of()) {
                if (matches(SkillKeys.of(skill), key, scancode)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable unsure) {
            return true;
        }
    }

    private static boolean matches(KeyMapping mapping, int key, int scancode) {
        return mapping != null && mapping.matches(key, scancode);
    }
}
