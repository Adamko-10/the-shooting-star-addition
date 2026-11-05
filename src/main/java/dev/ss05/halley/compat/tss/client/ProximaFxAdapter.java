package dev.ss05.halley.compat.tss.client;

import cyou.rimuru.rimulib.mc1211.client.cinematic.CutsceneDirector;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Subject;
import cyou.rimuru.rimulib.mc1211.client.render.ScreenFx;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.FxManager;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.Frame;
import cyou.rimuru.shootingstardemo.mc1211.client.fx.SpellFx;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.proxima.ProximaFx;
import dev.ss05.halley.compat.tss.StrikeFx;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/** SA-03's effects as one of The Shooting Star's spell effects: it ticks and draws its post-processing there. */
final class ProximaFxAdapter extends SpellFx {
    private final ProximaFx fx;

    ProximaFxAdapter(StrikeFx strike) {
        super(StarClientBridge.payload(strike));
        Subject subject = this.liiillli1l();
        this.fx = new ProximaFx(new MoonFx.Host() {
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
                return ProximaFxAdapter.this.time(partial);
            }

            @Override
            public float speed() {
                return ProximaFxAdapter.this.speed;
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
        }, strike.casterId(), this.target, strike.seed(), strike.targets());
    }

    ProximaFx proxima() {
        return this.fx;
    }

    @Override
    public void beamTick(ClientLevel level, int t) {
        this.fx.tick(Minecraft.getInstance(), level, t);
    }

    @Override
    public int duration() {
        return ProximaPlan.DURATION;
    }

    @Override
    public void onRemoved() {
        this.fx.removed();
    }

    @Override
    public double range() {
        return 900.0 + this.fx.plan.params.scorchRadius() * 4.0;
    }

    @Override
    public void screen(Frame frame, ScreenFx screen) {
        this.fx.screen(new MoonFxAdapter.Screen(screen), Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }
}
