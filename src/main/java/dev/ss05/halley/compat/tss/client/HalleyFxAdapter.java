package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.client.cinematic.Subject;
import cyou.rimuru.shootingstardemo.client.fx.Frame;
import cyou.rimuru.shootingstardemo.client.fx.FxManager;
import cyou.rimuru.rimulib.client.text.Overlay;
import cyou.rimuru.shootingstardemo.client.fx.SpellFx;
import cyou.rimuru.rimulib.client.render.ScreenFx;
import cyou.rimuru.shootingstardemo.l1l1i1lii1;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.HalleyHud;
import dev.ss05.halley.compat.tss.StrikeFx;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/** SS-05's effects as one of The Shooting Star's spell effects: it ticks, draws its HUD and post-processing there. */
final class HalleyFxAdapter extends SpellFx {
    private final HalleyFx fx;

    HalleyFxAdapter(StrikeFx strike) {
        super(StarClientBridge.payload(strike));
        Subject subject = this.subject();
        this.fx = new HalleyFx(new HalleyFx.Host() {
            @Override
            public float filmWeight() {
                return CutsceneDirector.weight(subject);
            }

            @Override
            public float filmTime(float partial) {
                return CutsceneDirector.time(subject, partial);
            }

            @Override
            public float clock(float partial) {
                return HalleyFxAdapter.this.time(partial);
            }

            @Override
            public float speed() {
                return HalleyFxAdapter.this.speed;
            }

            @Override
            public void shake(Vec3 source, float amount, double range) {
                FxManager.shake(source, amount, range);
            }

            @Nullable
            @Override
            public SoundEvent remoteSound(boolean press) {
                return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("shooting_star_demo", press ? "star_press" : "star_arm"));
            }
        }, strike.casterId(), this.origin, this.target, strike.yaw(), strike.seed(), strike.targets());
    }

    HalleyFx halley() {
        return this.fx;
    }

    /** The game's partial tick for this frame (The Shooting Star's Frame no longer names it). */
    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    /** The film's subject: the caster, the strike's target and the way they faced. */
    Subject subject() {
        return this.liiillli1l();
    }

    /** The remote's speed slider for this cast: the film's clock runs at it too. */
    float filmSpeed() {
        return this.speed;
    }

    @Override
    public void beamTick(ClientLevel level, int t) {
        this.fx.tick(Minecraft.getInstance(), level, t);
    }

    /** The strike's own length, from the packet (this client's config may differ from the server's). */
    @Override
    public int duration() {
        return this.fx.plan.duration;
    }

    @Override
    public void onRemoved() {
        this.fx.removed();
    }

    /** Post-processing and HUD reach as far as the trench and the shock wave do, and then some. */
    @Override
    public double range() {
        HalleyPlan p = this.fx.plan;
        return 700.0 + p.params.trenchLength() + p.params.craterRadius() * p.params.blastReach();
    }

    @Override
    public void screen(Frame frame, ScreenFx screen) {
        this.fx.screen(new Screen(screen), partial());
    }

    @Override
    public void hud(GuiGraphics graphics, Frame frame) {
        HalleyHud.world(graphics, this.fx, partial(), Overlay.width(graphics));
        HalleyHud.marker(graphics, this.fx, partial(), Overlay.width(graphics), Overlay.height(graphics));
    }

    /** SS-05's screen effects written into the remote's post-processing settings for this frame. */
    private record Screen(ScreenFx fx) implements HalleyFx.Screen {
        @Override
        public void shake(float k) {
            this.fx.shake = Math.max(this.fx.shake, k);
        }

        @Override
        public void aberration(float k) {
            this.fx.aberration = Math.max(this.fx.aberration, k);
        }

        @Override
        public void zoomBlur(float k) {
            this.fx.zoomBlur = Math.max(this.fx.zoomBlur, k);
        }

        @Override
        public void bloom(float k, float radius) {
            if (k > 0.001F) {
                this.fx.bloom = Math.max(this.fx.bloom, k);
                this.fx.bloomRadius = Math.max(this.fx.bloomRadius, radius);
            }
        }

        @Override
        public void desaturate(float k) {
            this.fx.desaturate = Math.max(this.fx.desaturate, k);
        }

        @Override
        public void flash(int rgb, float k) {
            this.fx.addFlash(rgb, k);
        }

        @Override
        public void vignette(int rgb, float k) {
            this.fx.addVignette(rgb, k);
        }

        @Override
        public void impact(float strength, int mode, Vec3 focus) {
            float[] uv = FxManager.uv(focus);
            this.fx.addImpact(strength, mode, uv[0], uv[1]);
        }

        @Override
        public void stoppedWorld(float k, float fps) {
            frozen(this.fx, k, fps);
        }
    }

    /** The remote's own "stopped world" look (a protected helper, so it is reached from here, the subclass). */
    private static void frozen(ScreenFx screen, float k, float fps) {
        liiillli1l(screen, k, fps);
    }
}
