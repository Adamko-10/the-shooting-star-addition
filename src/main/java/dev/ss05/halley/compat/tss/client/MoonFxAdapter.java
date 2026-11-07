package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.client.cinematic.Subject;
import cyou.rimuru.shootingstardemo.client.fx.Frame;
import cyou.rimuru.shootingstardemo.client.fx.FxManager;
import cyou.rimuru.rimulib.client.api.UiApi;
import cyou.rimuru.shootingstardemo.client.fx.SpellFx;
import cyou.rimuru.rimulib.client.render.ScreenFx;
import cyou.rimuru.shootingstardemo.il11ill1iil;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.luna.MoonHud;
import dev.ss05.halley.compat.tss.StrikeFx;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/** SS-06's effects as one of The Shooting Star's spell effects: it ticks, draws its HUD and post-processing there. */
final class MoonFxAdapter extends SpellFx {
    private final MoonFx fx;

    MoonFxAdapter(StrikeFx strike) {
        super(StarClientBridge.payload(strike));
        Subject subject = this.subject();
        this.fx = new MoonFx(new MoonFx.Host() {
            @Override
            public float filmWeight() {
                return CutsceneDirector.weight(subject);
            }

            @Override
            public float filmTime(float partial) {
                return MoonFxAdapter.this.warp(CutsceneDirector.time(subject, partial));
            }

            @Override
            public float clock(float partial) {
                return MoonFxAdapter.this.warp(MoonFxAdapter.this.time(partial));
            }

            @Override
            public float speed() {
                return MoonFxAdapter.this.speed;
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
        }, strike.casterId(), this.origin, this.target, strike.seed(), strike.targets());
    }

    MoonFx moon() {
        return this.fx;
    }

    /** The plan's time at this clock time (Epic mode slows the fall; see MoonPlan.warp). */
    private float warp(float clock) {
        return this.fx == null ? clock : (float) this.fx.plan.warp(clock);
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

    /** The plan ticks done so far: in Epic mode a clock tick doesn't always bring a new one. */
    private int planTicks = -1;

    @Override
    public void beamTick(ClientLevel level, int t) {
        int due = (int) Math.floor(this.fx.plan.warp(t));
        if (this.planTicks < 0) {
            // from wherever The Shooting Star starts it, as before (never replaying ticks it skipped)
            this.planTicks = due - 1;
        }
        while (this.planTicks < due) {
            this.fx.tick(Minecraft.getInstance(), level, ++this.planTicks);
        }
    }

    @Override
    public int duration() {
        // read every tick by The Shooting Star: a kept moon stays until the switch goes off
        return MoonFx.kept() ? Integer.MAX_VALUE : (int) Math.ceil(this.fx.plan.unwarp(MoonPlan.DURATION));
    }

    @Override
    public void onRemoved() {
        this.fx.removed();
    }

    @Override
    public double range() {
        return 900.0 + this.fx.plan.params.craterRadius() * this.fx.plan.params.blastReach();
    }

    @Override
    public void screen(Frame frame, ScreenFx screen) {
        this.fx.screen(new Screen(screen), partial());
    }

    @Override
    public void hud(GuiGraphics graphics, Frame frame) {
        MoonHud.hud(graphics, this.fx, partial(), UiApi.width(graphics), UiApi.height(graphics));
    }

    /** SS-06's screen effects written into the remote's post-processing settings for this frame. */
    record Screen(ScreenFx fx) implements MoonFx.Screen {
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
