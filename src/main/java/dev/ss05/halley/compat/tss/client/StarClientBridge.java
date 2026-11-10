package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.mc1211.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.mc1211.api.SkillApi;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import cyou.rimuru.rimulib.mc1211.client.api.NetClientApi;
import cyou.rimuru.rimulib.mc1211.client.api.UiApi;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.mc1211.net.SpellFxPayload;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.AdditionPanel;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import dev.ss05.halley.client.proxima.ProximaFx;
import dev.ss05.halley.client.proxima.ProximaCosmos;
import dev.ss05.halley.client.proxima.ProximaHud;
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
import net.minecraft.client.Minecraft;
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
            NetClientApi.receive(StrikeFx.PACKET, (strike, context) -> Minecraft.getInstance().execute(() -> {
                if (strike.skill() == SkillApi.index(TssLink.SKILL)) {
                    start(strike);
                } else if (strike.skill() == SkillApi.index(TssLink.MOON)) {
                    startMoon(strike);
                } else if (strike.skill() == SkillApi.index(TssLink.PROXIMA)) {
                    startProxima(strike);
                }
            }));
            NetClientApi.receive(ConvergenceFx.PACKET, (fx, context) -> Minecraft.getInstance().execute(() -> ConvergenceClient.start(fx)));
            // the addon's plates in the remote's menu (H) and their keys (the menu holds the mouse: things in it are picked
            // by looking at them), through RimuLib's public API; never a key the remote uses or one being rebound
            UiApi.onMenuDraw((graphics, partial) -> {
                if (!MenuApi.capturesInput()) {
                    return;
                }
                if (HalleyClientConfig.menuPlates()) {
                    AdditionPanel.draw(graphics);
                    ConvergencePlate.draw(graphics);
                } else {
                    // hidden by the client config: the Stellar Convergence picks still go to the server
                    ConvergencePlate.sync(false);
                }
            });
            MenuApi.onKey(key -> MenuApi.capturesInput() && HalleyClientConfig.menuPlates() && !menuUses(key, 0)
                && (AdditionPanel.key(key) || ConvergencePlate.key(key)));
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
     * target, yaw, seed, targets, flags, power, speed, width). On this jar its class kept its readable name,
     * {@code net.SpellFxPayload} (main's 1.5.0 jar has it obfuscated and unmapped instead - check with javap).
     */
    static SpellFxPayload payload(StrikeFx s) {
        return new SpellFxPayload(s.skill(), s.casterId(), s.origin().x, s.origin().y, s.origin().z, s.target().x, s.target().y,
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

    /** SA-04's effects (and its film, for the caster, or everyone near it when the caster plays it for everyone). */
    private static void startProxima(StrikeFx strike) {
        ProximaFxAdapter fx = new ProximaFxAdapter(strike);
        FxManager.add(fx);
        // a random flare has no film (nobody cast it)
        if (films(fx.proxima().mine, strike) && !fx.proxima().plan.params.natural()) {
            CutsceneDirector.play(ProximaCutscene.of(fx));
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
        // in RimuLib's overlay units (UiApi.overlay: one size at any GUI scale)
        if (!HalleyFx.active().isEmpty()) {
            UiApi.overlay(graphics, () -> {
                HalleyFx fx = HalleyFx.active().getLast();
                HalleyHud.film(graphics, fx, partial, UiApi.width(graphics), UiApi.height(graphics), world -> FxManager.project(world, graphics));
                FilmTitle.halley(graphics, fx, partial, UiApi.width(graphics), UiApi.height(graphics), world -> FxManager.project(world, graphics));
            });
        }
        if (!MoonFx.active().isEmpty()) {
            UiApi.overlay(graphics, () -> {
                MoonFx fx = MoonFx.active().getLast();
                MoonHud.film(graphics, fx, partial, UiApi.width(graphics), UiApi.height(graphics), world -> FxManager.project(world, graphics));
                FilmTitle.luna(graphics, fx, partial, UiApi.width(graphics), UiApi.height(graphics), world -> FxManager.project(world, graphics));
            });
        }
        if (!ProximaFx.active().isEmpty()) {
            ProximaFx proxima = ProximaFx.active().getLast();
            // its film's space and Earth shots: a scene of their own over the world, under the captions
            ProximaCosmos.render(graphics, proxima, partial);
            UiApi.overlay(graphics, () -> {
                ProximaHud.film(graphics, proxima, partial, UiApi.width(graphics), UiApi.height(graphics));
                FilmTitle.caption(graphics, ProximaHud.caption(proxima, partial), UiApi.width(graphics), UiApi.height(graphics));
                FilmTitle.centauri(graphics, proxima, partial, UiApi.width(graphics), UiApi.height(graphics));
                FilmTitle.dyson(graphics, proxima, partial, UiApi.width(graphics), UiApi.height(graphics));
                FilmTitle.cme(graphics, proxima, partial, UiApi.width(graphics), UiApi.height(graphics));
                FilmTitle.proxima(graphics, proxima, partial, UiApi.width(graphics), UiApi.height(graphics));
            });
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

    /** How hard the camera may shake during a Stellar Convergence when capped: one big weapon's worth (Luna peaks at 1.3). */
    private static final float SHAKE_CAP = 1.0F;
    /** FxManager's two static floats, its trauma (impact kicks, which it holds to 1 itself) and rumble (every effect's shake, added up). */
    private static List<Field> shakeFields;

    /** With Stellar Convergence's shake off, while one is on its way or just hit. */
    public static boolean shakeOff() {
        return !ConvergenceClient.active().isEmpty() && HalleyClientConfig.convergenceShake() == HalleyClientConfig.ConvergenceShake.OFF;
    }

    /**
     * From compat/mixin, before the remote shakes the camera: while a Stellar Convergence is on its way or just hit, its
     * plate's setting (the user, 2026-11-05). Every weapon adds its shake to the remote's, so five at once shook five
     * times as hard: capped holds it to {@link #SHAKE_CAP}, off skips it (true: no shake this frame), full leaves it.
     */
    public static boolean holdShake() {
        if (ConvergenceClient.active().isEmpty()) {
            return false;
        }
        HalleyClientConfig.ConvergenceShake mode = HalleyClientConfig.convergenceShake();
        if (mode == HalleyClientConfig.ConvergenceShake.OFF) {
            return true;
        }
        if (mode == HalleyClientConfig.ConvergenceShake.CAPPED) {
            try {
                // both held to the cap: trauma is at most 1 anyway, so which is which doesn't matter
                for (Field field : shakeFields()) {
                    field.setFloat(null, Math.min(field.getFloat(null), SHAKE_CAP));
                }
            } catch (ReflectiveOperationException | RuntimeException failed) {
                HalleyAddon.LOG.warn("Couldn't cap the remote's screen shake; it shakes in full", failed);
                shakeFields = List.of();
            }
        }
        return false;
    }

    private static List<Field> shakeFields() {
        if (shakeFields == null) {
            List<Field> found = new ArrayList<>();
            for (Field field : FxManager.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()) && field.getType() == float.class) {
                    field.setAccessible(true);
                    found.add(field);
                }
            }
            if (found.size() != 2) {
                HalleyAddon.LOG.warn("The remote's screen shake isn't where it was ({} static floats); Stellar Convergence's shake "
                    + "can't be capped, only switched off. See StarClientBridge.holdShake.", found.size());
                found.clear();
            }
            shakeFields = List.copyOf(found);
        }
        return shakeFields;
    }

    /**
     * Whether the remote's menu has a use for this key right now (before the addon's plates take theirs): a skill's key
     * is being rebound, or it is the menu's, the films' or a skill's own key. When unsure, yes.
     */
    public static boolean menuUses(int key, int scancode) {
        try {
            if (MenuApi.binding() != null) {
                return true;
            }
            if (matches(MenuApi.menuKey(), key, scancode) || matches(MenuApi.filmKey(), key, scancode)) {
                return true;
            }
            SkillSet remote = SkillApi.setOf(TssLink.SKILL);
            for (Skill skill : remote != null ? remote.skills : java.util.List.<Skill>of()) {
                if (matches(MenuApi.key(skill), key, scancode)) {
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
