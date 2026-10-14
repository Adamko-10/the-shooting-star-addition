package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.shootingstardemo.client.cinematic.CutsceneDirector;
import cyou.rimuru.shootingstardemo.client.fx.CastTitles;
import cyou.rimuru.shootingstardemo.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.client.fx.Overlay;
import cyou.rimuru.shootingstardemo.client.fx.SpellFx;
import cyou.rimuru.shootingstardemo.net.SpellFxPayload;
import cyou.rimuru.shootingstardemo.star.StarSkill;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.compat.tss.TssLink;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The client half of {@link TssLink}: the only client code that touches The Shooting Star. It catches Halley's and
 * Luna's effect packets on their way to the remote's effect manager and starts {@link HalleyFx}/{@link MoonFx} for
 * them instead.
 *
 * <h2>When The Shooting Star updates</h2>
 * Fix the imports here and in {@link HalleyFxAdapter}/{@link HalleyCutscene} and {@link MoonFxAdapter}/
 * {@link MoonCutscene}. The small mixins in {@code compat/tss/mixin} (film overlay, remote animation) switch
 * themselves off if their targets move, as do the vanilla ones in {@code compat/mixin} (raised arm, hiding the
 * vanilla moon); the skills and their effects keep working without them.
 */
public final class StarClientBridge {
    private static boolean installed;

    private StarClientBridge() {
    }

    /**
     * Wraps The Shooting Star's receiver for its effect packet (registered in its client initializer, so this runs
     * once every mod's client has started): Halley's and Luna's packets start their own effects, everything else goes
     * on to the remote's own handler untouched. (Fabric hands these packets over on the game's own thread.)
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            @Nullable ClientPlayNetworking.PlayPacketHandler<SpellFxPayload> builtIn = ClientPlayNetworking.unregisterGlobalReceiver(SpellFxPayload.TYPE);
            if (builtIn == null) {
                HalleyAddon.LOG.warn("Halley found no receiver for The Shooting Star's effect packets; it is adding one "
                    + "that hands every other skill's packets straight to the remote's effect manager.");
            }
            boolean registered = ClientPlayNetworking.registerGlobalReceiver(SpellFxPayload.TYPE, (packet, player, sender) -> {
                if (StarBridge.installed() && packet.spell() == StarBridge.skillIndex()) {
                    start(packet);
                } else if (StarBridge.installed() && packet.spell() == StarBridge.moonIndex()) {
                    startMoon(packet);
                } else if (builtIn != null) {
                    builtIn.receive(packet, player, sender);
                } else {
                    FxManager.start(packet);
                }
            });
            if (!registered) {
                throw new IllegalStateException("the effect packet's receiver couldn't be replaced");
            }
            installed = true;
        } catch (Throwable failed) {
            HalleyAddon.LOG.error("Halley couldn't hook its effects into The Shooting Star; strikes will happen "
                + "but won't be shown. See compat/tss/client/StarClientBridge.java.", failed);
        }
    }

    /**
     * The Shooting Star 1.4.3+ sets this in a spell's effect flags when its caster plays their films for everyone near
     * the spell (the remote's menu, "Play for everyone"); earlier versions never set it.
     */
    private static final int FLAG_FILM_FOR_EVERYONE = 8;

    /** Whether this client plays the strike's film: the caster's own, or anyone's when they play it for everyone. */
    private static boolean films(boolean mine, SpellFxPayload payload) {
        return CutsceneDirector.automatic() && (mine || (payload.flags() & FLAG_FILM_FOR_EVERYONE) != 0);
    }

    private static void start(SpellFxPayload payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        HalleyFxAdapter fx = new HalleyFxAdapter(payload);
        FxManager.add(fx);
        if (films(fx.halley().mine, payload)) {
            CutsceneDirector.play(HalleyCutscene.of(fx));
        } else if (!fx.halley().mine) {
            CastTitles.show(TssLink.SKILL, false, fx.focus());
        }
    }

    /** SS-06's effects (and its film, for the caster, or everyone near it when the caster plays it for everyone). */
    private static void startMoon(SpellFxPayload payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        MoonFxAdapter fx = new MoonFxAdapter(payload);
        FxManager.add(fx);
        if (films(fx.moon().mine, payload)) {
            CutsceneDirector.play(MoonCutscene.of(fx));
        } else if (!fx.moon().mine) {
            CastTitles.show(TssLink.MOON, false, fx.focus());
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
