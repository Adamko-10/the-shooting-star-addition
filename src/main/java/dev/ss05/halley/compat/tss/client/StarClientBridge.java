package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.client.magic.SkillKeys;
import cyou.rimuru.rimulib.client.magic.WhiteSpace;
import cyou.rimuru.rimulib.client.net.RimuNetClient;
import cyou.rimuru.rimulib.client.text.Overlay;
import cyou.rimuru.rimulib.skill.Skill;
import cyou.rimuru.rimulib.skill.SkillSet;
import cyou.rimuru.shootingstardemo.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.net.SpellFxPayload;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import dev.ss05.halley.compat.tss.StrikeFx;
import dev.ss05.halley.compat.tss.TssLink;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
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

    /**
     * Wraps The Shooting Star's receiver for its effect packet (registered in its client initializer, so this runs
     * once every mod's client has started): SS-05's packets start SS-05's effects, everything else goes on to the
     * remote's own handler untouched.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            RimuNetClient.receive(StrikeFx.TYPE, (strike, context) -> {
                if (strike.skill() == SkillSet.indexOf(TssLink.SKILL)) {
                    start(strike);
                } else if (strike.skill() == SkillSet.indexOf(TssLink.MOON)) {
                    startMoon(strike);
                }
            });
            installed = true;
        } catch (Throwable failed) {
            HalleyAddon.LOG.error("Halley and Luna couldn't hook their effects into The Shooting Star; strikes will "
                + "happen but won't be shown. See compat/tss/client/StarClientBridge.java.", failed);
        }
    }

    /**
     * The Shooting Star's own effect packet for a strike, which its effects are built from: (skill, caster, origin,
     * target, yaw, seed, targets, flags, power, speed, width). On this jar the mapper matched it back to its old
     * readable name (SpellFxPayload); other loaders' jars may leave it obfuscated - check with javap before reusing.
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

    /** SS-06's effects (and its film, for the caster, or everyone near it when the caster plays it for everyone). */
    private static void startMoon(StrikeFx strike) {
        MoonFxAdapter fx = new MoonFxAdapter(strike);
        FxManager.add(fx);
        if (films(fx.moon().mine, strike)) {
            CutsceneDirector.play(MoonCutscene.of(fx));
        }
    }

    /** From compat/mixin: SS-05's and SS-06's overlays on the caster's film (the remote draws its own skills' there). */
    public static void renderFilmHud(GuiGraphics graphics, float partial) {
        if (!HalleyFx.active().isEmpty()) {
            Overlay.begin(graphics);
            try {
                int w = Overlay.width(graphics);
                int h = Overlay.height(graphics);
                for (HalleyFx fx : HalleyFx.active()) {
                    HalleyHud.film(graphics, fx, partial, w, h, world -> FxManager.project(world, graphics));
                }
            } finally {
                Overlay.end(graphics);
            }
        }
        if (!MoonFx.active().isEmpty()) {
            Overlay.begin(graphics);
            try {
                int w = Overlay.width(graphics);
                int h = Overlay.height(graphics);
                for (MoonFx fx : MoonFx.active()) {
                    MoonHud.film(graphics, fx, partial, w, h, world -> FxManager.project(world, graphics));
                }
            } finally {
                Overlay.end(graphics);
            }
        }
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
        return mapping != null && mapping.matches(new KeyEvent(key, scancode, 0));
    }
}
