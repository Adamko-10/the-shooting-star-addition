package dev.ss05.halley.compat.client;

import cyou.rimuru.shootingstardemo.mc1201.client.cinematic.CutsceneDirector;
import cyou.rimuru.shootingstardemo.mc1201.client.fx.CastTitles;
import cyou.rimuru.shootingstardemo.mc1201.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.mc1201.client.fx.Overlay;
import cyou.rimuru.shootingstardemo.mc1201.client.fx.SpellFx;
import cyou.rimuru.shootingstardemo.mc1201.net.Network;
import cyou.rimuru.shootingstardemo.mc1201.net.SpellFxPayload;
import cyou.rimuru.shootingstardemo.mc1201.star.StarSkill;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import dev.ss05.halley.compat.StarBridge;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The client half of {@link StarBridge}: the only client code that touches The Shooting Star. It catches SS-05's and
 * SS-06's effect packets on their way to the remote's effect manager and starts {@link HalleyFx}/{@link MoonFx} for
 * them instead.
 *
 * <h2>When The Shooting Star updates</h2>
 * Fix the imports here and in {@link HalleyFxAdapter}/{@link HalleyCutscene} and {@link MoonFxAdapter}/
 * {@link MoonCutscene}. The small mixins in {@code compat/mixin} (film overlay, remote animation, raised arm, hiding
 * the vanilla moon) switch themselves off if their targets move; the skills and their effects keep working without
 * them.
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
            Consumer<SpellFxPayload> builtIn = Network.clientSpellFx;
            Network.clientSpellFx = payload -> {
                if (StarBridge.installed() && payload.spell() == StarBridge.skillIndex()) {
                    start(payload);
                } else if (StarBridge.installed() && payload.spell() == StarBridge.moonIndex()) {
                    startMoon(payload);
                } else {
                    builtIn.accept(payload);
                }
            };
            installed = true;
        } catch (Throwable failed) {
            HalleyAddon.LOG.error("SS-05 Halley couldn't hook its effects into The Shooting Star; strikes will happen "
                + "but won't be shown. See compat/client/StarClientBridge.java.", failed);
        }
    }

    private static void start(SpellFxPayload payload) {
        HalleyFxAdapter fx = new HalleyFxAdapter(payload);
        FxManager.add(fx);
        if (fx.halley().mine && CutsceneDirector.automatic()) {
            CutsceneDirector.play(HalleyCutscene.of(fx));
        } else if (!fx.halley().mine) {
            CastTitles.show(StarBridge.SKILL, false, fx.focus());
        }
    }

    /** SS-06's effects (and its film, for the caster). */
    private static void startMoon(SpellFxPayload payload) {
        MoonFxAdapter fx = new MoonFxAdapter(payload);
        FxManager.add(fx);
        if (fx.moon().mine && CutsceneDirector.automatic()) {
            CutsceneDirector.play(MoonCutscene.of(fx));
        } else if (!fx.moon().mine) {
            CastTitles.show(StarBridge.MOON, false, fx.focus());
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
     * From compat/mixin: the Stellar Remote animates its cover, button and screen for its newest strike. If that is
     * SS-05's or SS-06's, hand it that skill's clock instead of the one it found ({@code builtIn}, -1 for none).
     */
    public static float remoteClock(float builtIn, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return builtIn;
        }
        int id = minecraft.player.getId();
        int halleyAge = HalleyFx.newestAgeFor(id);
        int moonAge = MoonFx.newestAgeFor(id);
        boolean useMoon = moonAge >= 0 && (halleyAge < 0 || moonAge < halleyAge);
        int mine = useMoon ? moonAge : halleyAge;
        if (mine < 0) {
            return builtIn;
        }
        if (builtIn >= 0.0F) {
            for (StarSkill skill : StarSkill.values()) {
                SpellFx other = FxManager.latest(skill);
                if (other != null && other.casterId() == id && other.age() < mine) {
                    return builtIn;
                }
            }
        }
        return useMoon ? MoonFx.remoteClockFor(id, partial) : HalleyFx.remoteClockFor(id, partial);
    }
}
