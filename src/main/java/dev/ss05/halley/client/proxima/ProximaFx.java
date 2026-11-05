package dev.ss05.halley.client.proxima;

import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.content.HalleyContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * SA-03 Proxima on a client: what is seen and heard (docs/SS-07-PROXIMA.md). Stage 1 of its build: the flash's white-red
 * light and the embers it raises, the red sky while the coronal front passes (only felt for now: the ring itself comes
 * in stage 2), the hush, and ground zero (the white flash, impact frames, a ring of fire, smoke and ash). Its sounds are
 * the addon's others, pitched, until Proxima has its own. It talks to the remote through Luna's {@link MoonFx.Host} and
 * {@link MoonFx.Screen}, which are the same for every skill.
 */
public final class ProximaFx {
    private static final List<ProximaFx> ACTIVE = new ArrayList<>();
    /** The flare's light: white with a red edge. */
    private static final int FLASH = 0xFFD8C8;
    private static final int CRIMSON = 0xB3122E;

    public final ProximaPlan plan;
    public final boolean mine;
    private final MoonFx.Host host;
    private final RandomSource random;
    private final float particles;

    public ProximaFx(MoonFx.Host host, int casterId, Vec3 target, long seed, int[] data) {
        this.host = host;
        this.plan = new ProximaPlan(target, seed, ProximaParams.decode(data));
        Minecraft minecraft = Minecraft.getInstance();
        this.mine = minecraft.player != null && minecraft.player.getId() == casterId;
        this.random = RandomSource.create(seed);
        ParticleStatus status = minecraft.options.particles().get();
        this.particles = status == ParticleStatus.ALL ? 1.0F : status == ParticleStatus.DECREASED ? 0.45F : 0.12F;
        ACTIVE.add(this);
    }

    public static List<ProximaFx> active() {
        return ACTIVE;
    }

    public void removed() {
        ACTIVE.remove(this);
    }

    // ---- Sounds and particles, tick by tick. ----------------------------------------------------------------------

    public void tick(Minecraft minecraft, ClientLevel level, int t) {
        Vec3 target = this.plan.target;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double scorch = this.plan.params.scorchRadius();
        if (t == ProximaPlan.PRESS && this.mine) {
            SoundEvent press = this.host.remoteSound(true);
            if (press != null) {
                this.play(press, camera, 0.06F, 1.0F);
            }
        }
        // the flare's light arrives: a searing burst, and everything under the open sky starts to smoke
        if (t == ProximaPlan.FLASH) {
            this.play(HalleyContent.BURST.get(), camera, 0.1F, 0.55F);
            this.play(SoundEvents.FIRECHARGE_USE, camera, 0.08F, 0.5F);
        }
        boolean underIt = this.plan.horizontal(camera.x, camera.z) < scorch * 1.25;
        if (t > ProximaPlan.FLASH && t < ProximaPlan.FLASH + 70 && underIt) {
            for (int i = 0; i < this.count(5); i++) {
                Vec3 at = camera.add(this.gauss(14.0), this.gauss(4.0) - 2.0, this.gauss(14.0));
                this.particle(level, this.random.nextFloat() < 0.6F ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE, at,
                    0.0, 0.03 + this.random.nextFloat() * 0.05, 0.0);
            }
        }
        // the coronal front: a deep roar rolling across the sky (its look comes in stage 2)
        if (t == ProximaPlan.FRONT) {
            this.play(HalleyContent.MOON_RUMBLE.get(), camera, 0.08F, 0.55F);
        }
        // the air drawn in just before the plasma lands
        if (t == ProximaPlan.IMPACT - 13) {
            this.play(HalleyContent.MOON_INHALE.get(), target, 1.0F, 1.1F);
        }
        if (t == ProximaPlan.IMPACT) {
            this.play(HalleyContent.MOON_IMPACT.get(), target, 1.0F, 1.2F);
            this.play(HalleyContent.MOON_SHOCKWAVE.get(), target, 1.0F, 1.35F);
            this.host.shake(target, 0.6F, scorch * 3.0);
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, target.x, target.y + 1.0, target.z, 0.0, 0.0, 0.0);
            // a ring of fire running out from the bowl
            double bowl = this.plan.params.bowlRadius();
            for (int i = 0; i < this.count(220); i++) {
                double a = this.random.nextDouble() * Math.PI * 2.0;
                Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
                Vec3 at = target.add(out.scale(bowl * (0.3 + 0.4 * this.random.nextDouble()))).add(0.0, 1.0 + this.random.nextDouble() * 2.0, 0.0);
                Vec3 v = out.scale(1.2 + this.random.nextDouble() * 1.8).add(0.0, 0.1 + this.random.nextDouble() * 0.3, 0.0);
                this.particle(level, this.random.nextFloat() < 0.7F ? ParticleTypes.FLAME : ParticleTypes.LAVA, at, v.x, v.y, v.z);
            }
        }
        // after: smoke columns over the charred land, ash and embers drifting near the camera
        if (t > ProximaPlan.IMPACT && t < ProximaPlan.IMPACT + 220) {
            if (t % 3 == 0) {
                double a = this.random.nextDouble() * Math.PI * 2.0;
                double r = this.plan.params.bowlRadius() + this.random.nextDouble() * (this.plan.params.charRadius() - this.plan.params.bowlRadius());
                Vec3 at = target.add(Math.cos(a) * r, 0.5, Math.sin(a) * r);
                if (at.distanceTo(camera) < 160.0) {
                    this.particle(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 0.0, 0.08, 0.0);
                }
            }
            if (underIt) {
                for (int i = 0; i < this.count(4); i++) {
                    Vec3 at = camera.add(this.gauss(10.0), this.random.nextDouble() * 6.0, this.gauss(10.0));
                    this.particle(level, this.random.nextFloat() < 0.7F ? ParticleTypes.WHITE_ASH : ParticleTypes.SMALL_FLAME, at, 0.0, -0.02, 0.0);
                }
            }
        }
    }

    // ---- The screen, frame by frame. ------------------------------------------------------------------------------

    public void screen(MoonFx.Screen screen, float partial) {
        float t = this.host.clock(partial);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float near = (float) Mth.clamp(1.0 - camera.distanceTo(this.plan.target) / (this.plan.params.scorchRadius() * 4.0), 0.15, 1.0);
        // the flash: white-red, searing, fading over two seconds
        if (t >= ProximaPlan.FLASH && t < ProximaPlan.FLASH + 40) {
            float k = 1.0F - (t - ProximaPlan.FLASH) / 40.0F;
            screen.flash(FLASH, k * k * 0.85F);
            screen.aberration(0.3F * k);
        }
        // the red sky while the front passes
        float red = window(t, ProximaPlan.FLASH, ProximaPlan.FLASH + 10, ProximaPlan.HUSH, ProximaPlan.HUSH + 8);
        if (red > 0.0F) {
            screen.vignette(CRIMSON, 0.35F * red);
            screen.bloom(0.35F * red, 1.2F);
        }
        // the hush: the colour drains out
        if (t >= ProximaPlan.HUSH && t < ProximaPlan.IMPACT) {
            screen.desaturate(0.5F * (t - ProximaPlan.HUSH) / (ProximaPlan.IMPACT - ProximaPlan.HUSH));
        }
        if (t >= ProximaPlan.IMPACT) {
            float since = (t - ProximaPlan.IMPACT) / 20.0F;
            screen.flash(0xFFFFFF, Math.max(0.0F, 1.0F - since / 1.2F) * near);
            screen.zoomBlur(0.5F * (float) Math.exp(-since * 2.0) * near);
            screen.shake(window(since, 0.0F, 0.05F, 0.8F, 3.0F) * 0.9F * near);
            // the impact frames (F on the addon's plate: full, fainter, none)
            float strength = near * HalleyClientConfig.impactFrames().strength;
            int[] modes = {9, 15, 10, 17, 2};
            int step = (int) (since / 0.05F);
            if (strength > 0.0F && step < modes.length) {
                screen.impact(strength, modes[step], this.plan.target);
            }
        }
    }

    // ---- Helpers. --------------------------------------------------------------------------------------------------

    /** {@code volume} as for the addon's other skills' sounds: heard far off, falling off with distance. */
    private void play(SoundEvent event, Vec3 at, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event.getLocation(), SoundSource.PLAYERS,
            volume * 16.0F, pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false));
    }

    private int count(int n) {
        return n <= 0 ? 0 : Math.max(1, Math.round(n * this.particles));
    }

    private double gauss(double scale) {
        return this.random.nextGaussian() * scale;
    }

    private void particle(ClientLevel level, ParticleOptions options, Vec3 at, double vx, double vy, double vz) {
        level.addParticle(options, true, at.x, at.y, at.z, vx, vy, vz);
    }

    private static float window(float t, float in0, float in1, float out0, float out1) {
        return (float) (Mth.clamp((t - in0) / Math.max(in1 - in0, 0.001), 0.0, 1.0)
            * (1.0 - Mth.clamp((t - out0) / Math.max(out1 - out0, 0.001), 0.0, 1.0)));
    }
}
