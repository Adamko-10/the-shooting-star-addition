package dev.ss05.halley.compat.client;

import dev.aek.shootingstardemo.client.cinematic.CutsceneDirector;
import dev.aek.shootingstardemo.client.fx.CastTitles;
import dev.aek.shootingstardemo.client.fx.FxManager;
import dev.aek.shootingstardemo.client.fx.Overlay;
import dev.aek.shootingstardemo.client.fx.SpellFx;
import dev.aek.shootingstardemo.net.SpellFxPayload;
import dev.aek.shootingstardemo.star.StarSkill;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.compat.StarBridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * The client half of {@link StarBridge}: the only client code that touches The Shooting Star. It catches SS-05's
 * effect packets on their way to the remote's effect manager and starts {@link HalleyFx} for them instead.
 *
 * <h2>When The Shooting Star updates</h2>
 * Fix the imports here and in {@link HalleyFxAdapter} and {@link HalleyCutscene}. The two small mixins in
 * {@code compat/mixin} (film overlay, remote animation) switch themselves off if their targets move; the skill and
 * its effects keep working without them.
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
            ClientPlayNetworking.@Nullable PlayPayloadHandler<SpellFxPayload> builtIn =
                (ClientPlayNetworking.PlayPayloadHandler<SpellFxPayload>) ClientPlayNetworking.unregisterGlobalReceiver(SpellFxPayload.TYPE.id());
            if (builtIn == null) {
                HalleyAddon.LOG.warn("SS-05 Halley found no receiver for The Shooting Star's effect packets; it is "
                    + "adding one that hands every other skill's packets straight to the remote's effect manager.");
            }
            boolean registered = ClientPlayNetworking.registerGlobalReceiver(SpellFxPayload.TYPE, (payload, context) -> {
                if (StarBridge.installed() && payload.spell() == StarBridge.skillIndex()) {
                    context.client().execute(() -> start(payload));
                } else if (builtIn != null) {
                    builtIn.receive(payload, context);
                } else {
                    context.client().execute(() -> FxManager.start(payload));
                }
            });
            if (!registered) {
                throw new IllegalStateException("the effect packet's receiver couldn't be replaced");
            }
            installed = true;
        } catch (Throwable failed) {
            HalleyAddon.LOG.error("SS-05 Halley couldn't hook its effects into The Shooting Star; strikes will happen "
                + "but won't be shown. See compat/client/StarClientBridge.java.", failed);
        }
    }

    private static void start(SpellFxPayload payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        HalleyFxAdapter fx = new HalleyFxAdapter(payload);
        FxManager.add(fx);
        if (fx.halley().mine && CutsceneDirector.automatic()) {
            CutsceneDirector.play(HalleyCutscene.of(fx));
        } else if (!fx.halley().mine) {
            CastTitles.show(StarBridge.SKILL, false, fx.focus());
        }
    }

    /** From compat/mixin: SS-05's overlay on the caster's film (the remote draws its own skills' there). */
    public static void renderFilmHud(GuiGraphicsExtractor graphics, float partial) {
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
