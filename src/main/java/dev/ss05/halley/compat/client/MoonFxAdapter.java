package dev.ss05.halley.compat.client;

import cyou.rimuru.shootingstardemo.mc1211.client.cinematic.CutsceneDirector;
import cyou.rimuru.shootingstardemo.mc1211.client.cinematic.Subject;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.Frame;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.Overlay;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.SpellFx;
import cyou.rimuru.shootingstardemo.mc1211.client.render.ScreenFx;
import cyou.rimuru.shootingstardemo.mc1211.net.SpellFxPayload;
import cyou.rimuru.shootingstardemo.mc1211.registry.ModSounds;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/** SS-06's effects as one of The Shooting Star's spell effects: it ticks, draws its HUD and post-processing there. */
final class MoonFxAdapter extends SpellFx {
    private final MoonFx fx;

    MoonFxAdapter(SpellFxPayload payload) {
        super(payload);
        Subject subject = this.subject();
        this.fx = new MoonFx(new MoonFx.Host() {
            @Override
            public float filmWeight() {
                return CutsceneDirector.weight(subject);
            }

            @Override
            public float filmTime(float partial) {
                return CutsceneDirector.time(subject, partial);
            }

            @Override
            public void shake(Vec3 source, float amount, double range) {
                FxManager.shake(source, amount, range);
            }

            @Nullable
            @Override
            public SoundEvent remoteSound(boolean press) {
                return press ? ModSounds.STAR_PRESS : ModSounds.STAR_ARM;
            }
        }, payload.casterId(), this.origin, this.target, payload.seed(), payload.targets());
    }

    MoonFx moon() {
        return this.fx;
    }

    @Override
    protected void onTick(Minecraft minecraft, ClientLevel level, int t) {
        this.fx.tick(minecraft, level, t);
    }

    @Override
    protected int duration() {
        return MoonPlan.DURATION;
    }

    @Override
    protected void onRemoved() {
        this.fx.removed();
    }

    @Override
    public Vec3 focus() {
        return this.target;
    }

    @Override
    public double range() {
        return 900.0 + this.fx.plan.params.craterRadius() * this.fx.plan.params.blastReach();
    }

    @Override
    public void screen(Frame frame, ScreenFx screen) {
        this.fx.screen(new Screen(screen), frame.partial());
    }

    @Override
    public void hud(GuiGraphics graphics, Frame frame) {
        MoonHud.hud(graphics, this.fx, frame.partial(), Overlay.width(graphics), Overlay.height(graphics));
    }

    /** SS-06's screen effects written into the remote's post-processing settings for this frame. */
    private record Screen(ScreenFx fx) implements MoonFx.Screen {
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
        stoppedWorld(screen, k, fps);
    }
}
