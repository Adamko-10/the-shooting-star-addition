package dev.ss05.halley.compat.client;

import dev.aek.shootingstardemo.mc1201.client.cinematic.CutsceneDirector;
import dev.aek.shootingstardemo.mc1201.client.fx.CastTitles;
import dev.aek.shootingstardemo.mc1201.client.fx.FxManager;
import dev.aek.shootingstardemo.mc1201.client.fx.Overlay;
import dev.aek.shootingstardemo.mc1201.client.fx.SpellFx;
import dev.aek.shootingstardemo.mc1201.net.Network;
import dev.aek.shootingstardemo.mc1201.net.SpellFxPayload;
import dev.aek.shootingstardemo.mc1201.star.StarSkill;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.compat.StarBridge;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The client half of {@link StarBridge}: the only client code that touches The Shooting Star. It catches SS-05's
 * effect packets on their way to the remote's effect manager and starts {@link HalleyFx} for them instead.
 *
 * <h2>When The Shooting Star updates</h2>
 * Fix the imports here and in {@link HalleyFxAdapter} and {@link HalleyCutscene}. The three small mixins in
 * {@code compat/mixin} (film overlay, remote animation, raised arm) switch themselves off if their targets move; the
 * skill and its effects keep working without them.
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

    /** From compat/mixin: SS-05's overlay on the caster's film (the remote draws its own skills' there). */
    public static void renderFilmHud(GuiGraphics graphics, float partial) {
        if (HalleyFx.active().isEmpty()) {
            return;
        }
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

    /**
     * From compat/mixin: the Stellar Remote animates its cover, button and screen for its newest strike. If that is
     * SS-05's, hand it SS-05's clock instead of the one it found ({@code builtIn}, -1 for none).
     */
    public static float remoteClock(float builtIn, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return builtIn;
        }
        int id = minecraft.player.getId();
        int mine = HalleyFx.newestAgeFor(id);
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
        return HalleyFx.remoteClockFor(id, partial);
    }
}
