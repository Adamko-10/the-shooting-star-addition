package dev.ss05.halley.client.proxima;

import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.luna.MoonFx;
import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.Shapes;
import dev.ss05.halley.client.sky.SkyLook;
import dev.ss05.halley.world.ProximaInfo;
import dev.ss05.halley.world.ProximaStrike;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

/**
 * SA-04 Proxima on a client: what is seen and heard (docs/SS-07-PROXIMA.md). Stage 1 of its build: the flash's white-red
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
    /** Ticks to wind the sky's clock on to sunset: with a shader pack on, the red sky is the pack's own sunset (as Halley and Luna wind it on to night). */
    private final long toSunset;
    /** Where the camera was against the coronal front last tick (above 0: still ahead of it), and how far out the shock wave had run. */
    private double frontSide = Double.NaN;
    private double lastWave;

    public ProximaFx(MoonFx.Host host, int casterId, Vec3 target, long seed, int[] data) {
        this.host = host;
        this.plan = new ProximaPlan(target, seed, ProximaParams.decode(data));
        Minecraft minecraft = Minecraft.getInstance();
        this.mine = minecraft.player != null && minecraft.player.getId() == casterId;
        this.random = RandomSource.create(seed);
        ParticleStatus status = minecraft.options.particles().get();
        this.particles = status == ParticleStatus.ALL ? 1.0F : status == ParticleStatus.DECREASED ? 0.45F : 0.12F;
        long day = minecraft.level == null ? 6000L : Math.floorMod(minecraft.level.getDayTime(), 24000L);
        this.toSunset = day >= 12200L && day < 13200L ? 0L : Math.floorMod(12700L - day, 24000L);
        ACTIVE.add(this);
    }

    public static List<ProximaFx> active() {
        return ACTIVE;
    }

    /** The bowls still hot after their strikes (ProximaStrike's radiation zones, as this client saw them begin). */
    private record Zone(ClientLevel level, Vec3 centre, double radius, long from, long until) {
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    /** After a flare the magnetic storm lingers: auroras for a night or two (36000 ticks, a day and a half). */
    private static final long AURORA_TICKS = 36000L;

    private record Aurora(ClientLevel level, Vec3 centre, long from, long until, long seed) {
    }

    private static final List<Aurora> AURORAS = new ArrayList<>();

    /** Whether some flare's auroras may still be in this world's sky (HalleySky asks before anything else). */
    public static boolean auroras() {
        return !AURORAS.isEmpty();
    }

    /**
     * The auroras a past flare leaves, as HalleySky draws them (red-topped green curtains, Proxima's palette): only by
     * night, within 2000 blocks of the flare, fading in after it and out over their last hours; null when there are none.
     */
    public static SkyLook aurora(float partial, Vec3 camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (AURORAS.isEmpty() || level == null) {
            return null;
        }
        AURORAS.removeIf(a -> a.level() != level || level.getGameTime() > a.until());
        float night = Mth.clamp(level.getStarBrightness(partial) * 2.0F, 0.0F, 1.0F);
        float best = 0.0F;
        Aurora shown = null;
        for (Aurora a : AURORAS) {
            double away = Math.hypot(camera.x - a.centre().x, camera.z - a.centre().z);
            float since = level.getGameTime() - a.from();
            float strength = smooth(since / 1200.0) * (1.0F - smooth((since - (AURORA_TICKS - 6000.0)) / 6000.0))
                * (1.0F - smooth((away - 1200.0) / 800.0));
            if (strength > best) {
                best = strength;
                shown = a;
            }
        }
        if (shown == null || night * best <= 0.002F) {
            return null;
        }
        return new SkyLook(0.0F, 0.0F, 0.9F * night * best, 0.0F, 0.0F, 0.0F, new Vec3(0.0, 1.0, 0.0), 0.0F, 0.0F, Vec3.ZERO, 0.0F,
            (float) (shown.seed() & 1023L), 0.0F, SkyLook.PALETTE_PROXIMA);
    }

    /** From the client tick: the hot bowls shimmer (crimson motes and sparks rising) and hum at their edge, fading out. */
    public static void tickZones() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (ZONES.isEmpty() || level == null) {
            return;
        }
        ZONES.removeIf(zone -> zone.level() != level || level.getGameTime() > zone.until());
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        RandomSource random = level.getRandom();
        for (Zone zone : ZONES) {
            double away = Math.hypot(camera.x - zone.centre().x, camera.z - zone.centre().z);
            if (away > zone.radius() + 64.0) {
                continue;
            }
            float left = 1.0F - (level.getGameTime() - zone.from()) / (float) Math.max(1L, zone.until() - zone.from());
            int n = Math.round(10 * left);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = Math.sqrt(random.nextDouble()) * zone.radius();
                double x = zone.centre().x + Math.cos(a) * r;
                double z = zone.centre().z + Math.sin(a) * r;
                if (Math.hypot(x - camera.x, z - camera.z) > 48.0) {
                    continue;
                }
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.2;
                level.addParticle(random.nextFloat() < 0.8F ? new DustParticleOptions(new Vector3f(0.95F, 0.15F, 0.35F), 0.9F)
                    : ParticleTypes.END_ROD, x, y, z, 0.0, 0.03 + random.nextDouble() * 0.04, 0.0);
            }
            if (Math.abs(away - zone.radius()) < 12.0 && level.getGameTime() % 50 == 0) {
                minecraft.getSoundManager().play(new SimpleSoundInstance(HalleyContent.HUM.get().getLocation(), SoundSource.AMBIENT,
                    0.5F * left, 0.5F, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
            }
        }
    }

    /**
     * From compat/mixin: a compass's needle while a coronal front batters the magnetic field near the camera (from the
     * front's arrival to five seconds after the impact, within 3000 blocks): spinning, wandering, each compass its own
     * way; -1 when no front is near (the compass points as usual).
     */
    public static float compassSpin(int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ACTIVE.isEmpty() || minecraft.level == null) {
            return -1.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (ProximaFx fx : ACTIVE) {
            float t = fx.host.clock(0.0F);
            if (t >= ProximaPlan.FRONT && t <= ProximaPlan.IMPACT + 100 && fx.plan.horizontal(camera.x, camera.z) < 3000.0) {
                double s = System.nanoTime() / 1.0E9;
                double wander = 1.7 * s + seed * 0.618 + 0.15 * Math.sin(s * 5.3 + seed);
                return (float) (wander - Math.floor(wander));
            }
        }
        return -1.0F;
    }

    public void removed() {
        ACTIVE.remove(this);
    }

    /** The strike's clock (ticks since the cast, at the remote's speed slider). */
    public float time(float partial) {
        return this.host.clock(partial);
    }

    /** How much of the screen the caster's film has (0: none). */
    public float filmWeight() {
        return this.host.filmWeight();
    }

    /** The film's clock, or NaN when no film plays. */
    public float filmTime(float partial) {
        return this.host.filmTime(partial);
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
        // the coronal front: its radio burst whining down as it comes on, and a deep roar rolling across the sky
        if (t == ProximaPlan.FRONT) {
            this.play(HalleyContent.PROXIMA_RADIO.get(), camera, 0.06F, 1.0F);
            this.play(HalleyContent.MOON_RUMBLE.get(), camera, 0.06F, 0.55F);
        }
        // the air drawn in just before the plasma lands
        if (t == ProximaPlan.IMPACT - 13) {
            this.play(HalleyContent.MOON_INHALE.get(), target, 1.0F, 1.1F);
        }
        if (t == ProximaPlan.IMPACT) {
            long now = level.getGameTime();
            AURORAS.add(new Aurora(level, target, now, now + AURORA_TICKS, this.plan.seed));
            ZONES.add(new Zone(level, target, this.plan.params.bowlRadius() * 1.3, now,
                now + (long) (ProximaStrike.RADIATION_TICKS * Math.max(0.5, Math.sqrt(this.plan.params.power())))));
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
        // the coronal front passing over: a roar and a jolt where its leading line crosses the camera, embers round it
        if (t >= ProximaPlan.FRONT && t <= ProximaPlan.IMPACT) {
            double side = camera.distanceTo(this.plan.frontCentre(t)) - ProximaPlan.FRONT_RADIUS;
            if (this.frontSide > 0.0 && side <= 0.0) {
                this.play(HalleyContent.MOON_SHOCKWAVE.get(), camera, 0.07F, 0.7F);
                this.host.shake(camera, 0.35F, 80.0);
            }
            this.frontSide = side;
            if (Math.abs(side) < 60.0) {
                for (int i = 0; i < this.count(8); i++) {
                    Vec3 at = camera.add(this.gauss(12.0), this.gauss(5.0), this.gauss(12.0));
                    this.particle(level, this.random.nextFloat() < 0.5F ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, at,
                        this.gauss(0.08), this.random.nextDouble() * 0.1, this.gauss(0.08));
                }
            }
        }
        // ground zero's shock wave running out: a blast and a jolt as it passes the camera, fire and dust thrown up along it
        double wave = this.plan.waveRadius(t);
        double away = this.plan.horizontal(camera.x, camera.z);
        if (t > ProximaPlan.IMPACT && this.lastWave < away && wave >= away && away < this.plan.waveReach()) {
            this.play(HalleyContent.MOON_SHOCKWAVE.get(), camera, 0.08F, 1.0F);
            this.host.shake(camera, 0.5F, 60.0);
        }
        this.lastWave = wave;
        if (wave > 0.0 && wave < this.plan.waveReach() && Math.abs(wave - away) < 90.0) {
            double facing = Math.atan2(camera.z - target.z, camera.x - target.x);
            double spread = Math.min(1.0, 60.0 / Math.max(1.0, wave));
            for (int i = 0; i < this.count(14); i++) {
                double a = facing + this.gauss(spread);
                double x = target.x + Math.cos(a) * wave;
                double z = target.z + Math.sin(a) * wave;
                Vec3 at = new Vec3(x, groundAt(level, x, z, target.y) + 0.5, z);
                float pick = this.random.nextFloat();
                this.particle(level, pick < 0.45F ? ParticleTypes.CAMPFIRE_COSY_SMOKE : pick < 0.8F ? ParticleTypes.FLAME : ParticleTypes.LAVA, at,
                    Math.cos(a) * 0.3, 0.15 + this.random.nextDouble() * 0.35, Math.sin(a) * 0.3);
            }
        }
        // the magnetic storm: redstone near the camera sparks while the front comes on and just after
        if (t >= ProximaPlan.FRONT && t < ProximaPlan.IMPACT + 60 && away < this.plan.params.scorchRadius() * 3.0) {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 30; i++) {
                at.set(camera.x + this.gauss(10.0), camera.y + this.gauss(4.0), camera.z + this.gauss(10.0));
                BlockState state = level.getBlockState(at);
                boolean wire = state.is(Blocks.REDSTONE_WIRE);
                if ((wire || state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)
                    || state.is(Blocks.REDSTONE_BLOCK)) && this.random.nextFloat() < 0.6F) {
                    Vec3 spark = Vec3.atCenterOf(at).add(0.0, wire ? -0.4 : 0.0, 0.0);
                    for (int k = 0; k < 3; k++) {
                        this.particle(level, ParticleTypes.ELECTRIC_SPARK, spark, this.gauss(0.15), this.random.nextDouble() * 0.15, this.gauss(0.15));
                    }
                    this.particle(level, new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.05F), 1.2F), spark, 0.0, 0.05, 0.0);
                }
            }
        }
        // water under the open sky steams in the flash and as the shock wave passes
        boolean steaming = t >= ProximaPlan.FLASH && t < ProximaPlan.FLASH + 80 && away < this.plan.params.scorchRadius() * 1.25
            || wave > 0.0 && Math.abs(wave - away) < 40.0;
        if (steaming) {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 24; i++) {
                int x = Mth.floor(camera.x + this.gauss(14.0));
                int z = Mth.floor(camera.z + this.gauss(14.0));
                at.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (level.getFluidState(at).is(FluidTags.WATER) && level.canSeeSky(at.above())) {
                    this.particle(level, ParticleTypes.CLOUD, Vec3.atCenterOf(at).add(0.0, 0.6, 0.0), this.gauss(0.02), 0.06 + this.random.nextDouble() * 0.08, this.gauss(0.02));
                    if (this.random.nextFloat() < 0.04F) {
                        this.play(SoundEvents.FIRE_EXTINGUISH, Vec3.atCenterOf(at), 0.02F, 0.6F + this.random.nextFloat() * 0.4F);
                    }
                }
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

    // ---- The sky (drawn by client/sky/HalleySky). -------------------------------------------------------------------

    public boolean finished(float partial) {
        return this.host.clock(partial) > ProximaPlan.DURATION;
    }

    /**
     * The sky under the flare: it burns crimson at the flash (with a shader pack on, the pack's own sunset: the clock is
     * wound on to it), the plasma glowing high over the mark, brighter and lower as the front comes on, white at the
     * impact, an ashen red haze after it, all clearing over the aftermath.
     */
    public SkyLook sky(float partial, Vec3 camera) {
        float t = Math.min(this.host.clock(partial), ProximaPlan.DURATION);
        float w = 1.0F - smooth((this.plan.horizontal(camera.x, camera.z) - 1400.0) / 1600.0);
        float cover = smooth((t - ProximaPlan.FLASH) / 8.0) * (1.0F - smooth((t - (ProximaPlan.DURATION - 120.0)) / 120.0));
        float flash = 1.6F * pulse(t, ProximaPlan.FLASH, 10.0) + 1.5F * pulse(t, ProximaPlan.IMPACT, 6.0);
        float veil = smooth((t - (ProximaPlan.IMPACT + 10.0)) / 40.0) * (1.0F - smooth((t - (ProximaPlan.DURATION - 60.0)) / 60.0));
        float front = smooth((t - ProximaPlan.FRONT) / (double) (ProximaPlan.IMPACT - ProximaPlan.FRONT));
        Vec3 at = this.plan.target.add(0.0, t <= ProximaPlan.IMPACT ? Mth.lerp(front, 260.0, 40.0) : 20.0, 0.0);
        Vec3 toward = at.subtract(camera);
        toward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : toward.normalize();
        float glow = t <= ProximaPlan.IMPACT ? cover * (0.3F + 1.6F * front) : 1.8F * (float) Math.exp(-(t - ProximaPlan.IMPACT) / 30.0);
        double wound = this.toSunset * cover * w;
        float rise = smooth((t - (ProximaPlan.DURATION - 120.0)) / 120.0);
        double shift = wound >= 12000.0 ? wound + (24000.0 - wound) * rise : wound * (1.0 - rise);
        return new SkyLook(cover * w * 0.85F, 0.0F, 0.0F, veil * w * 0.6F, 0.0F, flash * w, toward, glow * w, 1.0F, Vec3.ZERO,
            cover * w * 0.25F, (float) (this.plan.seed & 1023L), (float) shift, SkyLook.PALETTE_PROXIMA);
    }

    private static float smooth(double x) {
        double k = Mth.clamp(x, 0.0, 1.0);
        return (float) (k * k * (3.0 - 2.0 * k));
    }

    private static float pulse(double t, double at, double decay) {
        return t < at ? 0.0F : (float) Math.exp(-(t - at) / decay);
    }

    // ---- The coronal front and ground zero's shock wave (drawn by client/render/CometRenderer). --------------------

    /**
     * The coronal front: a shell of plasma thousands of blocks across coming down out of the sky onto the mark, brightest
     * where it is seen edge-on (its leading line sweeping across the sky, then over the land), streaming with filaments,
     * a fainter sheath behind it and a white-hot nose; then ground zero's shock wave, a wall of fire and dust running out
     * over the land to the scorch's edge. Everything far off is pulled in along its line of sight (see GlowBatch), so it
     * is seen at any render distance.
     */
    public void render(GlowBatch world, GlowBatch glare, float partial, ClientLevel level) {
        float t = Math.min(this.host.clock(partial), ProximaPlan.DURATION);
        Vec3 camera = world.camera();
        if (t >= ProximaPlan.FRONT && t < ProximaPlan.IMPACT + 8) {
            float fade = smooth((t - ProximaPlan.FRONT) / 25.0) * (1.0F - smooth((t - ProximaPlan.IMPACT) / 8.0));
            this.front(world, t, fade, camera, 0.0, 1.0F, 0.0);
            this.front(world, t, fade, camera, 260.0, 0.45F, 2.1);
            Vec3 nose = this.plan.target.subtract(this.plan.frontDirection().scale(this.plan.frontDistance(t)));
            world.billboard(nose, 120.0 + 0.12 * this.plan.frontDistance(t), Shapes.GLOW, 0, 0xFF7A96, 0.9F * fade, 0.0);
        }
        // the white point at ground zero
        if (t >= ProximaPlan.IMPACT && t < ProximaPlan.IMPACT + 24) {
            float k = 1.0F - (t - ProximaPlan.IMPACT) / 24.0F;
            glare.billboard(this.plan.target.add(0.0, 4.0, 0.0), 20.0 + this.plan.params.bowlRadius() * 2.5 * (1.0F - k * 0.5F),
                Shapes.GLOW, 0, 0xFFF2F6, k * k, 0.0);
        }
        double wave = this.plan.waveRadius(t);
        if (wave > 0.0 && wave < this.plan.waveReach() * 0.999) {
            this.shockWave(world, wave, level);
        }
        if (t >= ProximaPlan.IMPACT && t < ProximaPlan.IMPACT + 80) {
            this.cracks(world, t - ProximaPlan.IMPACT, level);
        }
    }

    /** Cracks of light racing out over the ground from the bowl at the impact, glowing white-orange, cooling over four seconds. */
    private void cracks(GlowBatch world, float since, ClientLevel level) {
        float fade = (float) Math.pow(1.0 - since / 80.0, 1.5);
        float reach = Math.min(1.0F, since / 10.0F);
        double bowl = this.plan.params.bowlRadius();
        RandomSource shape = RandomSource.create(this.plan.seed ^ 0x5EEDL);
        for (int c = 0; c < 9; c++) {
            double a = (c + shape.nextDouble() * 0.6) / 9.0 * Math.PI * 2.0;
            double length = bowl * (1.6 + shape.nextDouble()) * reach;
            int pieces = 8;
            Vec3 last = null;
            Vec3 lastSide = null;
            for (int i = 0; i <= pieces; i++) {
                double r = bowl * 0.4 + (length - bowl * 0.4) * i / pieces;
                double bend = a + (shape.nextDouble() - 0.5) * 0.25;
                double x = this.plan.target.x + Math.cos(bend) * r;
                double z = this.plan.target.z + Math.sin(bend) * r;
                Vec3 p = new Vec3(x, groundAt(level, x, z, this.plan.target.y) + 0.15, z);
                Vec3 side = new Vec3(-Math.sin(bend), 0.0, Math.cos(bend)).scale(1.4 * (1.0 - 0.7 * i / (double) pieces));
                if (last != null) {
                    float s = fade * (1.0F - 0.6F * i / pieces);
                    world.corner(last.subtract(lastSide), 0xFFE0A0, s);
                    world.corner(last.add(lastSide), 0xFFE0A0, s);
                    world.corner(p.add(side), 0xFF9A50, s);
                    world.corner(p.subtract(side), 0xFF9A50, s);
                }
                last = p;
                lastSide = side;
            }
        }
    }

    /** One shell of the front, {@code back} blocks behind its nose; {@code phase} shifts its filaments. */
    private void front(GlowBatch world, float t, float fade, Vec3 camera, double back, float gain, double phase) {
        if (fade <= 0.003F) {
            return;
        }
        Vec3 dir = this.plan.frontDirection();
        Vec3 centre = this.plan.frontCentre(t).subtract(dir.scale(back));
        Vec3 b1 = dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 b2 = b1.cross(dir).normalize();
        double ground = this.plan.target.y - 4.0;
        int rings = 18;
        int sectors = 48;
        double maxAngle = 0.72;
        Vec3[][] at = new Vec3[rings + 1][sectors + 1];
        int[][] colour = new int[rings + 1][sectors + 1];
        float[][] strength = new float[rings + 1][sectors + 1];
        for (int i = 0; i <= rings; i++) {
            double angle = maxAngle * i / rings;
            float nose = (float) Math.pow(1.0 - angle / maxAngle, 3.0);
            float edge = 1.0F - smooth((angle - 0.65 * maxAngle) / (0.35 * maxAngle));
            for (int j = 0; j <= sectors; j++) {
                double around = Math.PI * 2.0 * j / sectors;
                Vec3 normal = dir.scale(Math.cos(angle)).add(b1.scale(Math.sin(angle) * Math.cos(around)))
                    .add(b2.scale(Math.sin(angle) * Math.sin(around)));
                Vec3 p = centre.add(normal.scale(ProximaPlan.FRONT_RADIUS));
                Vec3 view = p.subtract(camera);
                double facing = view.lengthSqr() < 1.0E-6 ? 1.0 : Math.abs(normal.dot(view.normalize()));
                float rim = (float) Math.pow(1.0 - facing, 3.0);
                float filament = (float) Math.pow(0.5 + 0.5 * Math.sin(around * 13.0 + phase + 2.5 * Math.sin(angle * 9.0 + t * 0.05)), 6.0);
                float under = smooth((p.y - ground) / 30.0);
                at[i][j] = p;
                colour[i][j] = mix(mix(ProximaInfo.COLOR, 0xFF5AA8, filament), 0xFFD6E4, rim * 0.6F);
                strength[i][j] = fade * gain * edge * under * (0.10F + 0.6F * rim + 0.22F * nose + 0.25F * filament);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sectors; j++) {
                world.corner(at[i][j], colour[i][j], strength[i][j]);
                world.corner(at[i + 1][j], colour[i + 1][j], strength[i + 1][j]);
                world.corner(at[i + 1][j + 1], colour[i + 1][j + 1], strength[i + 1][j + 1]);
                world.corner(at[i][j + 1], colour[i][j + 1], strength[i][j + 1]);
            }
        }
    }

    /** Ground zero's shock wave at {@code radius} blocks out: a wall of fire and dust on the land, and a burning band behind it. */
    private void shockWave(GlowBatch world, double radius, ClientLevel level) {
        float k = (float) (radius / this.plan.waveReach());
        float fade = (float) Math.pow(1.0 - k, 0.7);
        // a towering wall at first (the user, 2026-11-05: "a proper big wave"), lower as it runs out
        double height = 25.0 + 120.0 * (1.0 - k);
        int segments = 96;
        Vec3[] base = new Vec3[segments + 1];
        Vec3[] behind = new Vec3[segments + 1];
        for (int j = 0; j <= segments; j++) {
            double a = Math.PI * 2.0 * j / segments;
            double x = this.plan.target.x + Math.cos(a) * radius;
            double z = this.plan.target.z + Math.sin(a) * radius;
            double bx = this.plan.target.x + Math.cos(a) * Math.max(0.0, radius - 40.0);
            double bz = this.plan.target.z + Math.sin(a) * Math.max(0.0, radius - 40.0);
            base[j] = new Vec3(x, groundAt(level, x, z, this.plan.target.y) + 0.2, z);
            behind[j] = new Vec3(bx, groundAt(level, bx, bz, this.plan.target.y) + 0.2, bz);
        }
        for (int j = 0; j < segments; j++) {
            // the wall: white-hot at its foot, glowing crimson up its lower half, fading out at its crest
            world.corner(base[j], 0xFFC27A, 0.95F * fade);
            world.corner(base[j + 1], 0xFFC27A, 0.95F * fade);
            world.corner(base[j + 1].add(0.0, height * 0.45, 0.0), 0xFF4A6A, 0.6F * fade);
            world.corner(base[j].add(0.0, height * 0.45, 0.0), 0xFF4A6A, 0.6F * fade);
            world.corner(base[j].add(0.0, height * 0.45, 0.0), 0xFF4A6A, 0.6F * fade);
            world.corner(base[j + 1].add(0.0, height * 0.45, 0.0), 0xFF4A6A, 0.6F * fade);
            world.corner(base[j + 1].add(0.0, height, 0.0), ProximaInfo.COLOR, 0.0F);
            world.corner(base[j].add(0.0, height, 0.0), ProximaInfo.COLOR, 0.0F);
            // the burning band on the ground behind it
            world.corner(behind[j], ProximaInfo.COLOR, 0.0F);
            world.corner(behind[j + 1], ProximaInfo.COLOR, 0.0F);
            world.corner(base[j + 1], 0xFF8A4C, 0.6F * fade);
            world.corner(base[j], 0xFF8A4C, 0.6F * fade);
        }
    }

    private static double groundAt(ClientLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return y <= level.getMinBuildHeight() ? fallback : y;
    }

    private static int mix(int a, int b, float k) {
        float c = Mth.clamp(k, 0.0F, 1.0F);
        int r = (int) Mth.lerp(c, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(c, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(c, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
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
        // the front's rumble, building as it comes on
        if (t >= ProximaPlan.FRONT && t < ProximaPlan.IMPACT) {
            screen.shake(0.12F * (t - ProximaPlan.FRONT) / (ProximaPlan.IMPACT - ProximaPlan.FRONT) * near);
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
