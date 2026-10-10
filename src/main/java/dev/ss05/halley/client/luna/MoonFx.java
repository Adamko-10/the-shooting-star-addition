package dev.ss05.halley.client.luna;

import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.client.HalleyClientConfig;
import dev.ss05.halley.client.render.CometVisuals;
import dev.ss05.halley.client.sky.SkyLook;
import dev.ss05.halley.content.HalleyContent;
import dev.ss05.halley.world.MoonInfo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One SS-06 moonfall as a client sees it: its sounds, particles, screen effects, sky look, raised arm and the
 * sphere/glow drawn by {@link dev.ss05.halley.client.luna.render.MoonRenderer}. Modelled directly on
 * {@code client/HalleyFx}, trimmed to what the moon needs; nothing here touches The Shooting Star (that's
 * {@code compat/client/MoonFxAdapter}, which hands this a {@link Host} and drives {@link #tick}, {@link #screen}
 * and the HUD).
 *
 * <h2>Handing over to the block moon</h2>
 * The server starts building the real moon out of moon-cheese blocks at {@link MoonPlan#SETTLE}; a big one can take
 * a while. This keeps drawing the rendered sphere at rest until the blocks are actually there (polled every 5 ticks,
 * {@link #checkBuilt}), then fades it out over 10 ticks ({@link #sphereVisibility}). If the strike carves nothing
 * ({@code carve_terrain = false}), there are no blocks to wait for, so it just fades near the end of the strike.
 */
public final class MoonFx {
    /** How long "EARTH SYSTEM SHUT DOWN" and its vignette pulse last, in ticks (~3.5s). */
    public static final int ALARM_TICKS = 70;
    /** Sound crosses about 17 blocks a tick (340 m/s), so far-off booms land after their flash... */
    private static final double SOUND_SPEED = 17.0;
    /** ...but never more than this late. */
    private static final int MAX_SOUND_DELAY = 30;
    /** Further than this from the strike, nobody sees its particles anyway. */
    private static final double PARTICLE_RANGE = 900.0;
    /** How often, in ticks, the block moon is polled for after SETTLE. */
    private static final int POLL_EVERY = 5;
    /** How long the rendered sphere takes to fade away once it's handed over (or the strike is about to end): a slow
     * cross-fade into a glowing, cooling molten shell (see {@link #sphereVisibilityAt}, {@code MoonSphere}'s cooling
     * tint and {@code MoonVisuals}' handover glow), not a pop. */
    private static final float HANDOVER_FADE = 55.0F;
    /** How long the cooling molten-shell look lingers after handover, in ticks (see the same places). */
    public static final float COOLING_TICKS = 100.0F;

    /** What the film needs from The Shooting Star (implemented in compat/client). */
    public interface Host {
        float filmWeight();

        float filmTime(float partial);

        void shake(Vec3 source, float amount, double range);

        @Nullable
        SoundEvent remoteSound(boolean press);
    }

    /** The screen effects SS-06 uses: The Shooting Star's post-processing, reached through compat/client. */
    public interface Screen {
        void shake(float k);

        void aberration(float k);

        void zoomBlur(float k);

        void bloom(float k, float radius);

        void desaturate(float k);

        void flash(int rgb, float k);

        void vignette(int rgb, float k);

        void impact(float strength, int mode, Vec3 focus);

        void stoppedWorld(float k, float fps);
    }

    private static final List<MoonFx> ACTIVE = new ArrayList<>();

    public final MoonPlan plan;
    public final int casterId;
    public final boolean mine;
    private final Host host;
    private final RandomSource random;
    private final List<SoundInstance> filmSounds = new ArrayList<>();
    private final List<Later> later = new ArrayList<>();
    private final float particles;
    private boolean filmed;
    private boolean removed;
    private int age;
    /** The tick (age) the block moon was confirmed built, or -1 until then. */
    private int handoverAt = -1;
    /** How far the clock has to be wound on from the real time for the middle of the night (0: it's night already);
     * see {@code HalleyFx}'s own field of the same name - SS-06 winds a shader pack's night on exactly the same way,
     * so the alarm turns the sky to night whether it's day or night when the moon is called down. */
    private final long toNight;

    public MoonFx(Host host, int casterId, Vec3 origin, Vec3 target, long seed, int[] data) {
        Minecraft minecraft = Minecraft.getInstance();
        MoonParams params = MoonParams.decode(data, Mth.floor(target.y), new Vec3(0.0, 1.0, 0.0));
        this.plan = new MoonPlan(origin, target, seed, params);
        this.casterId = casterId;
        this.mine = minecraft.player != null && minecraft.player.getId() == casterId;
        this.host = host;
        this.random = RandomSource.create(seed ^ 0x6C75_6E61_06L);
        ParticleStatus status = minecraft.options.particles().get();
        this.particles = status == ParticleStatus.ALL ? 1.0F : status == ParticleStatus.DECREASED ? 0.45F : 0.12F;
        long day = minecraft.level == null ? 6000L : Math.floorMod(minecraft.level.getDefaultClockTime(), 24000L);
        this.toNight = day >= 13000L && day < 23000L ? 0L : Math.floorMod(18000L - day, 24000L);
        ACTIVE.add(this);
    }

    // ---- Registry. ------------------------------------------------------------------------------------------------

    public static List<MoonFx> active() {
        return ACTIVE;
    }

    public static void clearAll() {
        for (MoonFx fx : List.copyOf(ACTIVE)) {
            fx.removed();
        }
        ACTIVE.clear();
    }

    /** The raised arm of a caster calling down SS-06, or null: {rightArmX, rightArmY, rightArmZ, weight}. */
    @Nullable
    public static float[] armPoseFor(int entityId, float partial) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            MoonFx fx = ACTIVE.get(i);
            if (fx.casterId == entityId) {
                return fx.armPose(partial);
            }
        }
        return null;
    }

    public static float remoteClockFor(int playerId, float partial) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            MoonFx fx = ACTIVE.get(i);
            if (fx.casterId == playerId) {
                return fx.remoteClock(partial);
            }
        }
        return -1.0F;
    }

    public static int newestAgeFor(int playerId) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            MoonFx fx = ACTIVE.get(i);
            if (fx.casterId == playerId) {
                return fx.age;
            }
        }
        return -1;
    }

    public int age() {
        return this.age;
    }

    /** The tick the block moon was confirmed built, or -1 before that (for the cross-fade's cooling glow). */
    public int handoverAt() {
        return this.handoverAt;
    }

    public float time(float partial) {
        return this.age + partial;
    }

    public boolean inFilm() {
        return this.host.filmWeight() > 0.5F;
    }

    public float filmWeight() {
        return this.host.filmWeight();
    }

    public float filmTime(float partial) {
        return this.host.filmTime(partial);
    }

    public boolean finished() {
        return this.age > MoonPlan.DURATION;
    }

    // ---- Each tick. -----------------------------------------------------------------------------------------------

    public void tick(Minecraft minecraft, ClientLevel level, int t) {
        this.age = t + 1;
        boolean film = this.inFilm();
        this.filmed |= film;
        if (this.filmed && !film && !this.filmSounds.isEmpty() && t < MoonPlan.CONTACT) {
            this.stopFilmSounds();
        }
        for (Iterator<Later> it = this.later.iterator(); it.hasNext(); ) {
            Later job = it.next();
            if (t >= job.at) {
                it.remove();
                job.run.run();
            }
        }

        Vec3 camera = camera();
        this.sounds(level, t, film, camera);
        if (camera.distanceTo(this.plan.target) < PARTICLE_RANGE + this.plan.params.craterRadius()) {
            this.particles(level, t);
        }
        if (t == MoonPlan.CONTACT && HalleyClientConfig.worldFlashes() && camera.distanceTo(this.plan.target) < 1800.0) {
            level.setSkyFlashTime(3);
        }
        if (this.plan.params.carve() && this.handoverAt < 0 && t >= MoonPlan.SETTLE && t % POLL_EVERY == 0) {
            this.checkBuilt(level);
        }
    }

    /** Polls a handful of points just inside the moon's surface for moon-cheese blocks (see the class doc). */
    private void checkBuilt(ClientLevel level) {
        MoonParams p = this.plan.params;
        Vec3 rc = this.plan.restCentre;
        int r = p.moonRadius();
        int cy = Mth.floor(rc.y);
        // the top, and eight points round the equator, all just inside the surface: the server builds the whole
        // shell before the inside, so once all of these are there the surface is complete
        BlockPos[] samples = new BlockPos[9];
        samples[0] = new BlockPos(Mth.floor(rc.x), Mth.floor(rc.y + r - 1.0), Mth.floor(rc.z));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            samples[i + 1] = new BlockPos(Mth.floor(rc.x + Math.cos(a) * (r - 1.0)), cy, Mth.floor(rc.z + Math.sin(a) * (r - 1.0)));
        }
        int loaded = 0;
        int built = 0;
        for (BlockPos pos : samples) {
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            loaded++;
            BlockState state = level.getBlockState(pos);
            if (state.is(HalleyContent.MOON_CHEESE) || state.is(HalleyContent.MOLTEN_MOON_CHEESE)) {
                built++;
            }
        }
        if (loaded >= 5 && built == loaded) {
            this.handoverAt = this.age;
        }
    }

    /**
     * 1 while the rendered sphere should show at full size, fading to 0 once the block moon has taken over (or, if
     * nothing is ever carved, near the very end of the strike).
     */
    public float sphereVisibility(float partial) {
        return this.sphereVisibilityAt(this.age + partial);
    }

    /** As {@link #sphereVisibility}, for an absolute time (age + partial) rather than a partial tick. */
    public float sphereVisibilityAt(double t) {
        if (t < MoonPlan.SETTLE) {
            return 1.0F;
        }
        int handover = this.handoverAt;
        float fadeFrom = handover >= 0 ? handover : MoonPlan.DURATION - 12.0F;
        return 1.0F - smooth((t - fadeFrom) / HANDOVER_FADE);
    }

    /**
     * How much smaller than its true size to draw the sphere (negative = bigger): at rest it is drawn a block bigger,
     * so it covers the block moon (whose block corners reach ~0.9 past the radius) while the server is still building
     * it, instead of the blocks showing through it piece by piece; then it cross-fades away (see sphereVisibilityAt).
     */
    public static double radiusShrinkAt(double t) {
        return -1.0 * MoonPlan.settle(t);
    }

    public void removed() {
        if (this.removed) {
            return;
        }
        this.removed = true;
        this.stopFilmSounds();
        this.later.clear();
        ACTIVE.remove(this);
    }

    // ---- Sounds. --------------------------------------------------------------------------------------------------

    private void sounds(ClientLevel level, int t, boolean film, Vec3 camera) {
        MoonPlan p = this.plan;
        Vec3 caster = this.casterPos(level);
        if (t == MoonPlan.ARM || t == MoonPlan.PRESS) {
            SoundEvent click = this.host.remoteSound(t == MoonPlan.PRESS);
            if (click != null) {
                this.play(click, caster, t == MoonPlan.PRESS ? 0.9F : 0.8F, 1.0F, film);
            }
        }
        if (t == MoonPlan.ALARM) {
            this.play(HalleyContent.MOON_ALARM, film ? p.target : caster, 1.5F, 1.0F, film);
        }
        if (t == MoonPlan.BREAK) {
            Vec3 at = p.centre(t);
            this.play(HalleyContent.MOON_CRACK, sky(camera, at), 1.2F, 1.0F, film);
        }
        if (t == MoonPlan.FALL) {
            this.follow(HalleyContent.MOON_FALL, 1.8F, film,
                age -> age > MoonPlan.CONTACT ? null : sky(camera(), p.centre(Math.min(age, MoonPlan.CONTACT))));
        }
        if (t == MoonPlan.ENTRY) {
            Vec3 at = p.centre(t);
            this.boom(HalleyContent.MOON_ENTRY, at, sky(camera, at), 2.2F, film, 0.35F, Double.MAX_VALUE);
        }
        if (t > MoonPlan.FALL && t < MoonPlan.CONTACT && t % 4 == 0) {
            this.host.shake(p.centre(t), 0.1F * (float) MoonPlan.fall(t), 650.0);
        }
        if (t == MoonPlan.CONTACT) {
            this.stopFilmSounds();
            double reach = p.params.craterRadius() * p.params.blastReach();
            this.boom(HalleyContent.MOON_IMPACT, p.target, near(camera, p.target, reach), 3.2F, film, 1.4F, 1800.0);
        }
        if (t == MoonPlan.CONTACT + 6) {
            this.play(HalleyContent.MOON_SETTLE, near(camera, p.target, p.params.craterRadius()), 1.3F, 1.0F, film);
        }
        if (t > MoonPlan.CONTACT && t < MoonPlan.SETTLE + 60 && t % 3 == 0) {
            this.host.shake(p.target, 0.12F * Math.max(0.0F, 1.0F - (t - MoonPlan.CONTACT) / 140.0F), 600.0);
        }
        if (t > MoonPlan.SETTLE && t < MoonPlan.DURATION - 30 && (t - MoonPlan.SETTLE) % 110 == 0) {
            this.play(HalleyContent.MOON_DEBRIS, near(camera, p.target, p.params.craterRadius() * 1.3), 1.0F,
                0.9F + this.random.nextFloat() * 0.2F, film);
        }
    }

    private void boom(SoundEvent sound, Vec3 source, Vec3 ear, float volume, boolean film, float shake, double shakeRange) {
        int delay = film ? 0 : (int) Math.min(MAX_SOUND_DELAY, camera().distanceTo(source) / SOUND_SPEED);
        Runnable go = () -> {
            boolean filmNow = film && this.inFilm();
            this.play(sound, ear, volume, 1.0F, filmNow);
            this.host.shake(shakeRange == Double.MAX_VALUE ? camera() : source, shake, shakeRange == Double.MAX_VALUE ? 1.0E9 : shakeRange);
        };
        if (delay <= 0) {
            go.run();
        } else {
            this.later.add(new Later(this.age - 1 + delay, go));
        }
    }

    private void play(SoundEvent event, Vec3 at, float volume, float pitch, boolean film) {
        SoundInstance instance;
        if (film) {
            instance = new SimpleSoundInstance(event.location(), SoundSource.PLAYERS, Math.min(1.0F, volume), pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            this.filmSounds.add(instance);
        } else {
            instance = new SimpleSoundInstance(event.location(), SoundSource.PLAYERS, volume * 16.0F, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        }
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    private void follow(SoundEvent event, float volume, boolean film, IntFunction<Vec3> where) {
        Follow sound = new Follow(event, volume, film, where);
        if (film) {
            this.filmSounds.add(sound);
        }
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private void stopFilmSounds() {
        for (SoundInstance instance : this.filmSounds) {
            Minecraft.getInstance().getSoundManager().stop(instance);
        }
        this.filmSounds.clear();
    }

    private final class Follow extends AbstractTickableSoundInstance {
        private final IntFunction<Vec3> where;
        private final boolean film;

        Follow(SoundEvent event, float volume, boolean film, IntFunction<Vec3> where) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.where = where;
            this.film = film;
            this.volume = film ? Math.min(1.0F, volume) : volume * 16.0F;
            this.relative = film;
            this.attenuation = film ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
            this.place();
        }

        private void place() {
            if (this.film) {
                this.x = this.y = this.z = 0.0;
                return;
            }
            Vec3 at = this.where.apply(MoonFx.this.age);
            if (at != null) {
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
            }
        }

        @Override
        public void tick() {
            if (MoonFx.this.removed || this.where.apply(MoonFx.this.age) == null) {
                this.stop();
                return;
            }
            this.place();
        }
    }

    // ---- Particles. -----------------------------------------------------------------------------------------------

    private void particles(ClientLevel level, int t) {
        MoonPlan p = this.plan;
        RandomSource r = this.random;
        Vec3 mark = p.target;

        if (t == MoonPlan.ALARM) {
            for (int i = 0; i < this.count(36); i++) {
                particle(level, dust(MoonInfo.COLOR, 1.1F), mark.add(this.gauss(0.3), 0.15, this.gauss(0.3)),
                    this.gauss(0.2), 0.05 + r.nextDouble() * 0.2, this.gauss(0.2));
            }
        }
        if (t > MoonPlan.ENTRY - 20 && t <= MoonPlan.CONTACT) {
            float burn = MoonPlan.burn(t);
            if (burn > 0.02F) {
                Vec3 head = p.centre(t);
                Vec3 back = headingAt(p, t).scale(-1.0);
                for (int i = 0; i < this.count((int) (16 * burn)); i++) {
                    particle(level, ParticleTypes.FLAME, head.add(this.gauss(3.0), this.gauss(3.0), this.gauss(3.0)),
                        back.x * 0.3 + this.gauss(0.08), back.y * 0.3 + this.gauss(0.08), back.z * 0.3 + this.gauss(0.08));
                }
                for (int i = 0; i < this.count((int) (6 * burn)); i++) {
                    particle(level, ParticleTypes.LAVA, head.add(this.gauss(4.0), this.gauss(4.0), this.gauss(4.0)), 0.0, -0.02, 0.0);
                }
            }
        }
        if (t == MoonPlan.CONTACT) {
            this.burst(level, mark, p.params.craterRadius());
        }
        if (t > MoonPlan.CONTACT && t <= MoonPlan.CONTACT + 28) {
            double radius = p.params.craterRadius();
            double reach = radius * p.params.blastReach();
            double front = radius + (reach - radius) * Math.pow((t - MoonPlan.CONTACT) / 28.0, 0.6);
            for (int i = 0; i < this.count(44); i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                Vec3 at = mark.add(Math.cos(a) * front, 0.6, Math.sin(a) * front);
                particle(level, i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.ASH, at,
                    Math.cos(a) * 0.55, 0.12 + r.nextDouble() * 0.3, Math.sin(a) * 0.55);
            }
        }
        if (t > MoonPlan.CONTACT && t < MoonPlan.DURATION - 20) {
            this.aftermath(level, t);
        }
    }

    private void burst(ClientLevel level, Vec3 at, double radius) {
        RandomSource r = this.random;
        for (int i = 0; i < 3; i++) {
            particle(level, ParticleTypes.EXPLOSION_EMITTER, at.add(this.gauss(radius * 0.3), 2.0, this.gauss(radius * 0.3)), 0.0, 0.0, 0.0);
        }
        BlockParticleOption lava = new BlockParticleOption(ParticleTypes.BLOCK, HalleyContent.MOLTEN_MOON_CHEESE.defaultBlockState());
        for (int i = 0; i < this.count(150); i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * radius;
            Vec3 ground = at.add(Math.cos(a) * d, 0.5, Math.sin(a) * d);
            double out = 0.6 + r.nextDouble() * 1.6;
            particle(level, lava, ground, Math.cos(a) * out, 0.8 + r.nextDouble() * 1.8, Math.sin(a) * out);
        }
        for (int i = 0; i < this.count(70); i++) {
            particle(level, ParticleTypes.LARGE_SMOKE, at.add(this.gauss(radius * 0.5), 1.0 + r.nextDouble() * radius * 0.3, this.gauss(radius * 0.5)),
                this.gauss(0.1), 0.2 + r.nextDouble() * 0.3, this.gauss(0.1));
        }
        for (int i = 0; i < this.count(30); i++) {
            particle(level, ParticleTypes.LAVA, at.add(this.gauss(radius * 0.3), 1.0, this.gauss(radius * 0.3)), this.gauss(0.4), 0.3, this.gauss(0.4));
        }
    }

    private void aftermath(ClientLevel level, int t) {
        MoonPlan p = this.plan;
        RandomSource r = this.random;
        double radius = p.params.craterRadius();
        float fade = 1.0F - (float) (t - MoonPlan.CONTACT) / (MoonPlan.DURATION - MoonPlan.CONTACT);
        for (int i = 0; i < this.count((int) (16 * fade) + 1); i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * radius * 1.5;
            particle(level, ParticleTypes.ASH, p.target.add(Math.cos(a) * d, 20.0 + r.nextDouble() * 40.0, Math.sin(a) * d),
                this.gauss(0.04), -0.08 - r.nextDouble() * 0.1, this.gauss(0.04));
        }
        if (t % 6 == 0 && p.params.core()) {
            Vec3 core = Vec3.atCenterOf(p.core);
            for (int i = 0; i < this.count(2); i++) {
                particle(level, ParticleTypes.LAVA, core.add(this.gauss(p.params.moonRadius() * 0.3), this.gauss(2.0), this.gauss(p.params.moonRadius() * 0.3)), 0.0, 0.02, 0.0);
            }
        }
    }

    private int count(int n) {
        return n <= 0 ? 0 : Math.max(1, Math.round(n * this.particles));
    }

    private double gauss(double scale) {
        return this.random.nextGaussian() * scale;
    }

    private static void particle(ClientLevel level, ParticleOptions options, Vec3 at, double vx, double vy, double vz) {
        level.addParticle(options, true, false, at.x, at.y, at.z, vx, vy, vz);
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgb & 0xFFFFFF, scale);
    }

    // ---- The sky (drawn by client/sky/HalleySky). ------------------------------------------------------------------

    /** The sick red-orange night under the alarm, bright orange through entry and the impact, clearing afterward. */
    public SkyLook sky(float partial, Vec3 camera) {
        if (!HalleyClientConfig.sky()) {
            return new SkyLook(0, 0, 0, 0, 0, 0, new Vec3(0, 1, 0), 0, 0, Vec3.ZERO, 0, 0, 0,
                SkyLook.PALETTE_LUNA);
        }
        MoonPlan p = this.plan;
        double t = this.age + partial;
        float w = this.skyReach(camera);
        // the sky falls to night exactly like SS-05's, whether it's day or night when the moon is called: full dark
        // by the end of the alarm, holding through the fall and the impact, clearing again in the aftermath
        float cover = smooth((t - MoonPlan.ALARM) / 30.0) * (1.0F - smooth((t - (MoonPlan.DURATION - 90.0)) / 90.0));
        float heat = smooth((t - (MoonPlan.ENTRY - 20.0)) / 50.0) * (1.0F - smooth((t - (MoonPlan.CONTACT + 50.0)) / 90.0));
        float flash = 0.4F * pulse(t, MoonPlan.ENTRY, 5.0) + 1.5F * pulse(t, MoonPlan.CONTACT, 6.0);
        float veil = smooth((t - (MoonPlan.CONTACT + 10.0)) / 40.0) * (1.0F - smooth((t - (MoonPlan.DURATION - 60.0)) / 60.0));
        // stars show through the red-orange night, washed out by the burning approach and the impact's glare
        float stars = cover * (1.0F - 0.8F * heat);
        // the land dims under the dark sky, exactly as SS-05's does (so the moon's own light stands out against it)
        float land = cover * (1.0F - 0.5F * heat) * 0.85F;

        Vec3 at = t <= MoonPlan.CONTACT ? p.centre(t) : p.target.add(0.0, p.params.craterRadius() * 0.4, 0.0);
        Vec3 toward = at.subtract(camera);
        toward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : toward.normalize();
        float glow = t <= MoonPlan.CONTACT ? 0.5F + 1.7F * heat : 1.8F * (float) Math.exp(-(t - MoonPlan.CONTACT) / 30.0);

        // with a shader pack on, the night is the pack's own: the sky's clock is wound on into it as the alarm turns
        // the sky dark, and back again as the aftermath clears, exactly like HalleyFx.sky's own wind/unwind
        double wound = this.toNight * cover * w;
        float rise = smooth((t - (MoonPlan.DURATION - 90.0)) / 90.0);
        double shift = wound >= 12000.0 ? wound + (24000.0 - wound) * rise : wound * (1.0 - rise);

        return new SkyLook(cover * w, stars * w, 0.0F, veil * w * 0.6F, 0.0F, flash * w, toward,
            glow * w, 1.0F, Vec3.ZERO, land * w, (float) (p.seed & 1023L), (float) shift, SkyLook.PALETTE_LUNA);
    }

    private float skyReach(Vec3 camera) {
        double d = Math.hypot(camera.x - this.plan.target.x, camera.z - this.plan.target.z);
        return 1.0F - smooth((d - 1400.0) / 1600.0);
    }

    private static float pulse(double t, double at, double decay) {
        return t < at ? 0.0F : (float) Math.exp(-(t - at) / decay);
    }

    /** Post-processing for this frame: the alarm's vignette pulse, the approach, the impact. */
    public void screen(Screen screen, float partial) {
        MoonPlan p = this.plan;
        float t = this.age + partial;
        boolean film = this.host.filmWeight() > 0.001F;
        Vec3 camera = camera();

        float alarmWin = window(t, MoonPlan.ALARM, MoonPlan.ALARM + 2.0F, MoonPlan.ALARM + ALARM_TICKS - 10.0F, MoonPlan.ALARM + ALARM_TICKS);
        if (alarmWin > 0.0F) {
            float pulse = 0.55F + 0.45F * (float) Math.sin(t * 1.1);
            screen.vignette(0xB31A1A, alarmWin * 0.55F * pulse);
        }

        if (t > MoonPlan.FALL && t < MoonPlan.CONTACT) {
            float fallK = (float) MoonPlan.fall(t);
            float near = film ? 1.0F : proximity(camera, p.target, 2400.0);
            screen.shake(fallK * fallK * 0.5F * near);
            screen.aberration(fallK * fallK * 0.3F * near);
            screen.bloom(0.25F * fallK * near, 1.2F);
            float entryWin = window(t, MoonPlan.ENTRY - 10.0F, MoonPlan.ENTRY + 10.0F, MoonPlan.CONTACT - 10.0F, MoonPlan.CONTACT);
            screen.flash(0xFF7A2E, 0.18F * entryWin * near);
            screen.vignette(0x401200, entryWin * 0.3F * near);

            // the last ~25 ticks: a strong, deliberate build-up so it reads as a crash rushing in, not a slide
            float preImpact = window(t, MoonPlan.CONTACT - 25.0F, MoonPlan.CONTACT - 16.0F, MoonPlan.CONTACT - 2.0F, MoonPlan.CONTACT);
            if (preImpact > 0.0F) {
                screen.zoomBlur(preImpact * 0.55F * near);
                screen.shake(preImpact * preImpact * 1.3F * near);
                screen.aberration(preImpact * 0.5F * near);
                screen.vignette(0xB3360A, preImpact * 0.55F * near);
            }
        }

        float since = (t - MoonPlan.CONTACT) / 20.0F;
        if (since >= 0.0F && since < 12.0F) {
            float near = film ? 1.0F : proximity(camera, p.target, 3200.0);
            Vec3 focus = p.target.add(0.0, p.params.craterRadius() * 0.3, 0.0);
            // a longer, punchier staged sequence than SS-05's own (the moon hits far harder), in The Shooting Star's warm impact frames (its composite shader): 9-10 white and gold, 15-16 black and ember,
            // 17 ember line-art, 19 ember halftone, 2 crimson - the moon-cheese gold and the burning entry, never SS-05's cyan
            impactSequence(screen, since, focus, near, 0.05F, 9, 15, 10, 17, 2, 16, 9, 19, 15, 10);
            float decay = since < 0.06F ? 1.0F : (float) Math.exp(-(since - 0.06F) * 4.2F);
            screen.flash(0xFFFFFF, 2.1F * near * decay);
            screen.flash(0xFFEFC8, 1.6F * near * decay);
            screen.flash(0xFF7A2E, 0.8F * near * (float) Math.exp(-since * 1.1F));
            screen.zoomBlur(window(since, 0.0F, 0.04F, 0.5F, 2.0F) * 0.9F * near);
            screen.shake(window(since, 0.0F, 0.06F, 3.2F, 7.5F) * 1.2F * near);
            screen.aberration(window(since, 0.0F, 0.06F, 0.9F, 4.5F) * near);
            float hold = window(t, MoonPlan.CONTACT, MoonPlan.CONTACT + 8.0F, MoonPlan.CONTACT + 140.0F, MoonPlan.CONTACT + 210.0F);
            screen.bloom(1.4F * hold, 1.6F + 1.5F * hold);
            screen.vignette(0x2A0E05, hold * 0.5F * near);
            screen.stoppedWorld(window(since, 0.0F, 0.025F, 0.35F, 0.65F) * near, 11.0F);
            screen.desaturate(0.22F * window(t, MoonPlan.CONTACT + 10.0F, MoonPlan.CONTACT + 40.0F, MoonPlan.CONTACT + 180.0F, MoonPlan.CONTACT + 250.0F) * near);
        }
        if (t > MoonPlan.CONTACT && t < MoonPlan.SETTLE + 60) {
            float near = film ? 1.0F : proximity(camera, p.target, 1800.0);
            float k = 1.0F - smooth((t - MoonPlan.CONTACT) / 140.0);
            screen.shake(0.15F * k * near);
        }
    }

    private static void impactSequence(Screen screen, float since, Vec3 focus, float strength, float frameSeconds, int... modes) {
        if (since < 0.0F || strength <= 0.0F) {
            return;
        }
        int step = (int) (since / frameSeconds);
        if (step < modes.length && modes[step] >= 0) {
            screen.impact(strength, modes[step], focus);
        }
    }

    // ---- The caster and the remote. -------------------------------------------------------------------------------

    /** Right arm: raised to the sky at the alarm, held up through the fall and the impact, then lowered. */
    public float[] armPose(float partial) {
        float t = this.age + partial;
        float end = MoonPlan.DURATION;
        float[][] keys = {
            {0.0F, -0.35F}, {MoonPlan.ALARM, -1.3F}, {MoonPlan.ALARM + 12.0F, -2.3F}, {MoonPlan.FALL, -2.25F},
            {MoonPlan.CONTACT, -2.1F}, {MoonPlan.CONTACT + 20.0F, -1.2F}, {end - 30.0F, -1.2F}, {end - 10.0F, -0.35F}
        };
        float x = keys[keys.length - 1][1];
        for (int i = 0; i < keys.length - 1; i++) {
            if (t >= keys[i][0] && t < keys[i + 1][0]) {
                x = Mth.lerp(smooth((t - keys[i][0]) / (keys[i + 1][0] - keys[i][0])), keys[i][1], keys[i + 1][1]);
                break;
            }
        }
        return new float[]{x, -0.12F, 0.0F, window(t, 0.0F, 3.0F, end - 14.0F, end - 6.0F)};
    }

    /** Maps SS-06's timeline onto the Stellar Remote's 352-tick clock, counting down to the impact at 352. */
    public float remoteClock(float partial) {
        float t = this.age + partial;
        float contact = MoonPlan.CONTACT;
        float clock = t < MoonPlan.ALARM ? t
            : t < contact ? MoonPlan.ALARM + (t - MoonPlan.ALARM) * (352.0F - MoonPlan.ALARM) / (contact - MoonPlan.ALARM)
            : 352.0F + (t - contact);
        return clock > 526.0F ? -1.0F : clock;
    }

    // ---- Helpers. -------------------------------------------------------------------------------------------------

    private Vec3 casterPos(ClientLevel level) {
        var caster = level.getEntity(this.casterId);
        return caster != null ? caster.position() : this.plan.origin;
    }

    public static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    /** The moon's direction of travel at time {@code t} (straight down the fall line, then straight down into the crater). */
    public static Vec3 headingAt(MoonPlan p, double t) {
        Vec3 d = t < MoonPlan.CONTACT ? p.contactCentre.subtract(p.startCentre) : p.restCentre.subtract(p.contactCentre);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0.0, -1.0, 0.0) : d.normalize();
    }

    static Vec3 near(Vec3 camera, Vec3 centre, double radius) {
        Vec3 flat = new Vec3(camera.x - centre.x, 0.0, camera.z - centre.z);
        double d = flat.length();
        Vec3 pt = d < 1.0E-3 ? centre : centre.add(flat.scale(Math.min(d, radius) / d));
        return new Vec3(pt.x, centre.y + Mth.clamp(camera.y - centre.y, 0.0, 60.0), pt.z);
    }

    static Vec3 sky(Vec3 camera, Vec3 source) {
        Vec3 d = source.subtract(camera);
        double l = d.length();
        return l < 96.0 ? source : camera.add(d.scale(96.0 / l));
    }

    private static float proximity(Vec3 camera, Vec3 at, double range) {
        return (float) Mth.clamp(1.0 - camera.distanceTo(at) / range, 0.15, 1.0);
    }

    static float window(float t, float in0, float in1, float out0, float out1) {
        return (float) (Mth.clamp((t - in0) / Math.max(in1 - in0, 0.001), 0.0, 1.0)
            * (1.0 - Mth.clamp((t - out0) / Math.max(out1 - out0, 0.001), 0.0, 1.0)));
    }

    static float smooth(double x) {
        return CometVisuals.smooth(x);
    }

    private record Later(int at, Runnable run) {
    }
}
