package dev.ss05.halley.client;

import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.client.render.CometExtras;
import dev.ss05.halley.client.render.CometVisuals;
import dev.ss05.halley.client.render.GlowBatch;
import dev.ss05.halley.client.render.GroundMarks;
import dev.ss05.halley.client.sky.SkyLook;
import dev.ss05.halley.content.HalleyContent;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * One SS-05 strike as a client sees it: its sounds, particles, screen effects, the caster's raised arm and the glow
 * drawn by {@link CometVisuals}. The server decides what happens; this only shows it, from the same
 * {@link HalleyPlan}.
 *
 * <p>Nothing here touches The Shooting Star directly. It is driven through compat/client (which hands it a
 * {@link Host} and calls {@link #tick}, {@link #screen}, the HUD and so on), so an update of that mod never needs
 * changes in this file.
 */
public final class HalleyFx {
    // ---- When the sounds play, in ticks (the rest follow HalleyPlan's timeline). ------------------------------------
    /** The roar of the approach builds over the last 8.5 seconds before touchdown. */
    private static final int APPROACH_SOUND = 130;
    /** Ice crackling out over the land, after the detonation. */
    private static final int FROST_SOUND = 12;
    /** The sky starts to go dark (HalleyFx.sky) and its sound plays. */
    private static final int SKY_SOUND = 12;
    /** The heart's hum repeats this often (the sound is a little longer, so it never drops out). */
    private static final int HUM_EVERY = 100;
    /** Sound crosses about 17 blocks a tick (340 m/s), so far-off booms land after their flash... */
    private static final double SOUND_SPEED = 17.0;
    /** ...but never more than this late. */
    private static final int MAX_SOUND_DELAY = 30;
    /** Further than this from the strike, nobody sees its particles anyway. */
    private static final double PARTICLE_RANGE = 700.0;

    /** What the film needs from The Shooting Star (implemented in compat/client). */
    public interface Host {
        /** How much of the screen this strike's cutscene has, 0..1 (0 when it isn't being filmed). */
        float filmWeight();

        /** The cutscene's clock in ticks, or NaN when this strike isn't being filmed. */
        float filmTime(float partial);

        /** Camera shake that falls off with distance from {@code source}. */
        void shake(Vec3 source, float amount, double range);

        /** The Stellar Remote's own clicks: the cover ({@code press} false) and the button. Null if missing. */
        @Nullable
        SoundEvent remoteSound(boolean press);
    }

    /** The screen effects SS-05 uses: The Shooting Star's post-processing, reached through compat/client. */
    public interface Screen {
        void shake(float k);

        void aberration(float k);

        void zoomBlur(float k);

        void bloom(float k, float radius);

        void desaturate(float k);

        void flash(int rgb, float k);

        void vignette(int rgb, float k);

        /** A one-frame "impact frame" in one of the remote's styles (0 ink, 1 inverted, 3 ink with a cold accent...). */
        void impact(float strength, int mode, Vec3 focus);

        /** The world holds still for a moment: drained of colour, stepping at {@code fps}. */
        void stoppedWorld(float k, float fps);
    }

    private static final List<HalleyFx> ACTIVE = new ArrayList<>();

    public final HalleyPlan plan;
    public final int casterId;
    /** True on the caster's own client. */
    public final boolean mine;
    public final CometVisuals visuals;
    public final CometExtras extras;
    private final Host host;
    private final RandomSource random;
    private final List<SoundInstance> filmSounds = new ArrayList<>();
    private final List<SoundInstance> worldSounds = new ArrayList<>();
    private final List<Later> later = new ArrayList<>();
    private final float particles;
    @Nullable
    private GroundMarks marks;
    private boolean filmed;
    private boolean removed;
    private int age;
    /** How far the clock has to be wound on from the real time for the middle of the night (0: it's night already). */
    private final long toNight;

    public HalleyFx(Host host, int casterId, Vec3 origin, Vec3 target, float yaw, long seed, int[] data) {
        Minecraft minecraft = Minecraft.getInstance();
        HalleyParams params = HalleyParams.decode(data, Mth.floor(target.y));
        this.plan = new HalleyPlan(origin, target, yaw, seed, params);
        this.casterId = casterId;
        this.mine = minecraft.player != null && minecraft.player.getId() == casterId;
        this.host = host;
        this.random = RandomSource.create(seed ^ 0x5EED_05L);
        int minY = minecraft.level == null ? -64 : minecraft.level.getMinY();
        this.visuals = new CometVisuals(this.plan, minY);
        this.extras = new CometExtras(this.plan);
        ParticleStatus status = minecraft.options.particles().get();
        this.particles = status == ParticleStatus.ALL ? 1.0F : status == ParticleStatus.DECREASED ? 0.45F : 0.12F;
        long day = minecraft.level == null ? 6000L : Math.floorMod(minecraft.level.getDefaultClockTime(), 24000L);
        this.toNight = day >= 13000L && day < 23000L ? 0L : Math.floorMod(18000L - day, 24000L);
        ACTIVE.add(this);
    }

    // ---- Registry: the renderer and the optional mixins find strikes here. -----------------------------------------

    public static List<HalleyFx> active() {
        return ACTIVE;
    }

    public static void clearAll() {
        for (HalleyFx fx : List.copyOf(ACTIVE)) {
            fx.removed();
        }
        ACTIVE.clear();
    }

    /** The raised arm of a caster calling down SS-05, or null: {rightArmX, rightArmY, rightArmZ, weight}. */
    @Nullable
    public static float[] armPoseFor(int entityId, float partial) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            HalleyFx fx = ACTIVE.get(i);
            if (fx.casterId == entityId) {
                return fx.armPose(partial);
            }
        }
        return null;
    }

    /** The Stellar Remote's animation clock for the local player's newest strike, or -1. */
    public static float remoteClockFor(int playerId, float partial) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            HalleyFx fx = ACTIVE.get(i);
            if (fx.casterId == playerId) {
                return fx.remoteClock(partial);
            }
        }
        return -1.0F;
    }

    /** The age of the local player's newest strike in ticks, or -1 (to pick between it and a built-in skill). */
    public static int newestAgeFor(int playerId) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            HalleyFx fx = ACTIVE.get(i);
            if (fx.casterId == playerId) {
                return fx.age;
            }
        }
        return -1;
    }

    public int age() {
        return this.age;
    }

    public float time(float partial) {
        return this.age + partial;
    }

    @Nullable
    public GroundMarks marks() {
        return this.marks;
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

    // ---- Each tick. -----------------------------------------------------------------------------------------------

    /** Called once a tick with the strike's age in ticks (0 on the tick it starts). */
    public void tick(Minecraft minecraft, ClientLevel level, int t) {
        this.age = t + 1;
        boolean film = this.inFilm();
        this.filmed |= film;
        if (this.filmed && !film && !this.filmSounds.isEmpty() && t < this.plan.impact) {
            // the cutscene was skipped: its sounds stop, the world's own carry on from here
            this.stopFilmSounds();
        }
        for (Iterator<Later> it = this.later.iterator(); it.hasNext(); ) {
            Later job = it.next();
            if (t >= job.at) {
                it.remove();
                job.run.run();
            }
        }
        if (this.marks == null && t >= HalleyPlan.MARK) {
            this.marks = new GroundMarks(this.plan, (x, z) -> surface(level, x, z));
        }

        Vec3 camera = camera();
        this.sounds(level, t, film, camera);
        if (camera.distanceTo(this.plan.target) < PARTICLE_RANGE + this.plan.params.trenchLength()) {
            this.particles(level, t);
        }
        if (t == HalleyPlan.TOUCHDOWN) {
            this.worldFlash(level, camera, this.plan.touchdown, 2);
        }
        if (t == this.plan.impact) {
            this.worldFlash(level, camera, this.plan.target, 3);
        }
        if (HalleyClientConfig.extraEffects()) {
            this.extras(level, t, camera);
        }
    }

    /** The whole world lights up for an instant, as it does under lightning (the land, not just the screen). */
    private void worldFlash(ClientLevel level, Vec3 camera, Vec3 at, int ticks) {
        if (HalleyClientConfig.worldFlashes() && camera.distanceTo(at) < 1400.0) {
            level.setSkyFlashTime(ticks);
        }
    }

    /** Sounds and particles for the extras: fragments bursting in the sky, ice from the crater landing. */
    private void extras(ClientLevel level, int t, Vec3 camera) {
        boolean film = this.inFilm();
        for (CometExtras.Fragment f : this.extras.fragments) {
            if (t == (int) Math.ceil(f.burstAt())) {
                Vec3 at = f.at(this.plan, f.burstAt());
                this.boom(HalleyContent.BURST, at, sky(camera, at), 1.4F, film, 0.12F, 900.0);
                if (camera.distanceTo(at) < 260.0) {
                    for (int i = 0; i < this.count(24); i++) {
                        particle(level, ParticleTypes.FIREWORK, at, this.gauss(0.6), this.gauss(0.6), this.gauss(0.6));
                    }
                }
            }
        }
        for (CometExtras.Shard s : this.extras.shards) {
            if (t == (int) Math.ceil(s.landsAt())) {
                Vec3 land = s.landing();
                Vec3 ground = this.ground(level, land.x, land.z, land.y);
                if (camera.distanceTo(ground) < 160.0) {
                    BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
                    for (int i = 0; i < this.count(12); i++) {
                        particle(level, ice, ground.add(this.gauss(0.6), 0.4, this.gauss(0.6)), this.gauss(0.25), 0.25 + this.random.nextDouble() * 0.35, this.gauss(0.25));
                    }
                    for (int i = 0; i < this.count(6); i++) {
                        particle(level, ParticleTypes.SNOWFLAKE, ground.add(this.gauss(1.0), 0.6, this.gauss(1.0)), this.gauss(0.08), 0.1 + this.random.nextDouble() * 0.2, this.gauss(0.08));
                    }
                    particle(level, ParticleTypes.CLOUD, ground.add(0.0, 0.6, 0.0), 0.0, 0.05, 0.0);
                }
                if (!film && camera.distanceTo(ground) < 90.0) {
                    this.play(SoundEvents.AMETHYST_CLUSTER_BREAK, ground, 1.3F, 0.6F + this.random.nextFloat() * 0.4F, false);
                }
            }
        }
    }

    public boolean finished() {
        return this.age > this.plan.duration;
    }

    /** When the strike's effect ends or the world is left. */
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
        HalleyPlan p = this.plan;
        Vec3 caster = this.casterPos(level);
        if (t == HalleyPlan.ARM || t == HalleyPlan.PRESS) {
            SoundEvent click = this.host.remoteSound(t == HalleyPlan.PRESS);
            if (click != null) {
                this.play(click, caster, t == HalleyPlan.PRESS ? 0.9F : 0.8F, 1.0F, film);
            }
        }
        if (t == HalleyPlan.MARK) {
            this.play(HalleyContent.MARK, film ? p.target : near(camera, p.target, p.params.craterRadius()), 1.0F, 1.0F, film);
        }
        if (t == SKY_SOUND && HalleyClientConfig.sky() && this.skyReach(camera) > 0.05F) {
            // the sky goes dark: heard from overhead
            this.play(HalleyContent.DUSK, film ? p.target : camera.add(0.0, 24.0, 0.0), 1.2F, 1.0F, film);
        }
        if (t == HalleyPlan.COUNTDOWN) {
            this.play(HalleyContent.COUNTDOWN, near(camera, p.target, p.params.craterRadius()), 1.0F, 1.0F, film);
        }
        if (t == HalleyPlan.SIGHT) {
            this.play(HalleyContent.SIGHT, sky(camera, p.comet(t)), 1.0F, 1.0F, film);
        }
        if (t == APPROACH_SOUND) {
            this.follow(HalleyContent.APPROACH, 1.4F, film, age -> age > HalleyPlan.TOUCHDOWN + 10 ? null : sky(camera(), p.comet(Math.min(age, HalleyPlan.TOUCHDOWN))));
        }
        if (t == HalleyPlan.ENTRY) {
            Vec3 at = p.comet(t);
            this.boom(HalleyContent.BOOM, at, sky(camera, at), 2.0F, film, 0.3F, Double.MAX_VALUE);
        }
        if (t == HalleyPlan.TOUCHDOWN) {
            Vec3 at = p.touchdown;
            this.boom(HalleyContent.TOUCHDOWN, at, near(camera, at, p.trenchHalfWidth(p.params.trenchLength()) * 3.0), 2.4F, film, 0.7F, 800.0);
        }
        if (t == HalleyPlan.TOUCHDOWN + 1) {
            this.follow(HalleyContent.PLOUGH, 2.0F, film, age -> age > p.impact + 4 ? null
                : near(camera(), p.comet(Math.min(age, p.impact)), p.trenchHalfWidth(p.frontAlong(age)) * 1.5));
        }
        if (t > HalleyPlan.TOUCHDOWN && t < p.impact && t % 3 == 0) {
            this.host.shake(p.comet(t), 0.12F, 420.0);
        }
        if (t == p.impact) {
            this.stopFilmSounds();
            Vec3 at = p.target;
            double reach = p.params.craterRadius() * p.params.blastReach();
            this.boom(HalleyContent.IMPACT, at, near(camera, at, reach), 3.0F, film, 1.0F, 1100.0);
        }
        if (t == p.impact + FROST_SOUND) {
            this.play(HalleyContent.FROST, near(camera, p.target, p.params.craterRadius() * 1.2), 1.4F, 1.0F, film);
        }
        if (this.visuals.hasHeart() && t >= p.impact + 40 && t < p.duration - 60 && (t - p.impact - 40) % HUM_EVERY == 0) {
            this.play(HalleyContent.HUM, this.visuals.heart(), 0.8F, 1.0F, film);
        }
    }

    /** A loud event far off: heard as far away as it is (up to a point), and felt when it arrives. */
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
            // the film's soundtrack: heard as if from the camera, wherever the shot is
            instance = new SimpleSoundInstance(event.location(), SoundSource.PLAYERS, Math.min(1.0F, volume), pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            this.filmSounds.add(instance);
        } else {
            // in the world: volume x16 is how far it carries (16 blocks per unit of volume, as the remote's skills do)
            instance = new SimpleSoundInstance(event.location(), SoundSource.PLAYERS, volume * 16.0F, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        }
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    /** A sound that moves with something (the comet in the sky, the nucleus down the trench). */
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
            Vec3 at = this.where.apply(HalleyFx.this.age);
            if (at != null) {
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
            }
        }

        @Override
        public void tick() {
            if (HalleyFx.this.removed || this.where.apply(HalleyFx.this.age) == null) {
                this.stop();
                return;
            }
            this.place();
        }
    }

    // ---- Particles. -----------------------------------------------------------------------------------------------

    private void particles(ClientLevel level, int t) {
        HalleyPlan p = this.plan;
        RandomSource r = this.random;
        Vec3 mark = p.target;

        if (t == HalleyPlan.MARK) {
            for (int i = 0; i < this.count(40); i++) {
                particle(level, dust(i % 3 == 0 ? CometVisuals.PALE : CometVisuals.ICE, 1.0F), mark.add(0.0, 0.2, 0.0),
                    this.gauss(0.25), 0.1 + r.nextDouble() * 0.4, this.gauss(0.25));
            }
            for (int i = 0; i < this.count(14); i++) {
                particle(level, ParticleTypes.END_ROD, mark.add(this.gauss(0.6), 0.3, this.gauss(0.6)), 0.0, 0.05 + r.nextDouble() * 0.12, 0.0);
            }
        }
        if (t > HalleyPlan.MARK && t < p.impact) {
            for (int i = 0; i < 2; i++) {
                particle(level, dust(i == 0 ? CometVisuals.PALE : CometVisuals.ICE, 0.6F + r.nextFloat() * 0.5F),
                    mark.add(this.gauss(0.2), 0.1, this.gauss(0.2)), this.gauss(0.06), 0.05 + r.nextDouble() * 0.2, this.gauss(0.06));
            }
            GroundMarks marks = this.marks;
            if (marks != null && t < HalleyPlan.TOUCHDOWN && t % 3 == 0) {
                Vec3 at = marks.chevrons[r.nextInt(marks.chevrons.length)];
                particle(level, ParticleTypes.END_ROD, at.add(this.gauss(1.5), 0.2, this.gauss(1.5)), 0.0, 0.03 + r.nextDouble() * 0.05, 0.0);
            }
        }

        // the air at the touchdown point stirs as the comet comes down on it
        if (t >= 268 && t < HalleyPlan.TOUCHDOWN) {
            float k = (t - 268) / 32.0F;
            for (int i = 0; i < this.count((int) (4 + 30 * k * k)); i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = Math.sqrt(r.nextDouble()) * 34.0;
                Vec3 at = this.ground(level, p.touchdown.x + Math.cos(a) * d, p.touchdown.z + Math.sin(a) * d, p.touchdown.y);
                particle(level, ParticleTypes.SNOWFLAKE, at.add(0.0, 0.4, 0.0), Math.cos(a) * 0.15, 0.1 + r.nextDouble() * 0.4 * k, Math.sin(a) * 0.15);
            }
        }

        if (t == HalleyPlan.TOUCHDOWN) {
            this.burst(level, p.touchdown, p.trenchHalfWidth(p.params.trenchLength()) * 1.6, 1.0F);
        }
        if (t > HalleyPlan.TOUCHDOWN && t <= p.impact) {
            this.ploughSpray(level, t);
        }
        if (t == p.impact) {
            this.burst(level, mark, p.params.craterRadius(), 3.2F);
        }
        if (t > p.impact && t <= p.impact + 28) {
            // dust and snow lifted along the shock front as it crosses the land
            double radius = p.params.craterRadius();
            double reach = radius * p.params.blastReach();
            double front = radius + (reach - radius) * Math.pow((t - p.impact) / 28.0, 0.6);
            for (int i = 0; i < this.count(40); i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                Vec3 at = this.ground(level, mark.x + Math.cos(a) * front, mark.z + Math.sin(a) * front, mark.y);
                particle(level, i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, at.add(0.0, 0.5, 0.0),
                    Math.cos(a) * 0.6, 0.15 + r.nextDouble() * 0.35, Math.sin(a) * 0.6);
            }
        }
        if (t > p.impact && t < p.duration - 20) {
            this.aftermath(level, t);
        }
    }

    /** Ice and rock thrown up where the comet strikes the ground. */
    private void burst(ClientLevel level, Vec3 at, double radius, float size) {
        RandomSource r = this.random;
        for (int i = 0; i < 3; i++) {
            particle(level, ParticleTypes.EXPLOSION_EMITTER, at.add(this.gauss(radius * 0.3), 2.0, this.gauss(radius * 0.3)), 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < this.count((int) (160 * size)); i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * radius;
            Vec3 ground = this.ground(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, at.y);
            double out = 0.6 + r.nextDouble() * 1.4 * Math.sqrt(size);
            particle(level, new BlockParticleOption(ParticleTypes.BLOCK, this.groundState(level, ground)), ground.add(0.0, 0.5, 0.0),
                Math.cos(a) * out, 0.8 + r.nextDouble() * 1.8, Math.sin(a) * out);
        }
        for (int i = 0; i < this.count((int) (90 * size)); i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = radius * (0.9 + r.nextDouble() * 0.25);
            Vec3 ground = this.ground(level, at.x + Math.cos(a) * d, at.z + Math.sin(a) * d, at.y);
            particle(level, ParticleTypes.CLOUD, ground.add(0.0, 1.0, 0.0), Math.cos(a) * 1.2, 0.15 + r.nextDouble() * 0.3, Math.sin(a) * 1.2);
        }
        for (int i = 0; i < this.count((int) (80 * size)); i++) {
            particle(level, ParticleTypes.SNOWFLAKE, at.add(this.gauss(radius * 0.5), 2.0 + r.nextDouble() * radius * 0.4, this.gauss(radius * 0.5)),
                this.gauss(0.6), 0.3 + r.nextDouble() * 0.9, this.gauss(0.6));
        }
        for (int i = 0; i < this.count((int) (30 * size)); i++) {
            particle(level, ParticleTypes.END_ROD, at.add(this.gauss(radius * 0.3), 1.0, this.gauss(radius * 0.3)),
                this.gauss(0.5), 0.4 + r.nextDouble() * 0.8, this.gauss(0.5));
        }
        for (int i = 0; i < this.count((int) (8 * size)); i++) {
            particle(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.add(this.gauss(radius * 0.4), 1.0, this.gauss(radius * 0.4)),
                this.gauss(0.02), 0.12 + r.nextDouble() * 0.1, this.gauss(0.02));
        }
    }

    /** The nucleus throws the ground aside as it ploughs, and leaves steam behind it. */
    private void ploughSpray(ClientLevel level, int t) {
        HalleyPlan p = this.plan;
        RandomSource r = this.random;
        double along = p.frontAlong(t);
        double hw = p.trenchHalfWidth(along);
        Vec3 head = p.comet(t);
        Vec3 ground = this.ground(level, head.x, head.z, p.groundLine(along));
        BlockState state = this.groundState(level, ground);
        for (int i = 0; i < this.count(26); i++) {
            double side = r.nextBoolean() ? 1.0 : -1.0;
            double lat = side * hw * (0.6 + r.nextDouble() * 0.5);
            Vec3 at = ground.add(p.side.scale(lat)).add(p.dir.scale(this.gauss(4.0))).add(0.0, 0.5, 0.0);
            Vec3 throwOut = p.side.scale(side * (0.6 + r.nextDouble() * 1.1)).add(p.dir.scale(-0.4 - r.nextDouble() * 0.8));
            particle(level, new BlockParticleOption(ParticleTypes.BLOCK, state), at, throwOut.x, 0.6 + r.nextDouble() * 1.2, throwOut.z);
        }
        for (int i = 0; i < this.count(10); i++) {
            particle(level, ParticleTypes.SNOWFLAKE, head.add(this.gauss(hw * 0.5), this.gauss(2.0), this.gauss(hw * 0.5)),
                this.gauss(0.5), 0.2 + r.nextDouble() * 0.6, this.gauss(0.5));
        }
        for (int i = 0; i < this.count(4); i++) {
            particle(level, ParticleTypes.END_ROD, head.add(this.gauss(2.0), this.gauss(1.5), this.gauss(2.0)),
                this.gauss(0.4) - p.dir.x * 0.6, this.gauss(0.3), this.gauss(0.4) - p.dir.z * 0.6);
        }
        // steam rising off the fresh trench behind it
        for (int i = 0; i < this.count(6); i++) {
            double back = along + r.nextDouble() * 40.0;
            if (back > p.params.trenchLength()) {
                continue;
            }
            Vec3 g = p.groundPoint(back);
            Vec3 at = this.ground(level, g.x + p.side.x * this.gauss(hw * 0.3), g.z + p.side.z * this.gauss(hw * 0.3), g.y);
            particle(level, ParticleTypes.CLOUD, at.add(0.0, 0.6, 0.0), 0.0, 0.08 + r.nextDouble() * 0.12, 0.0);
        }
    }

    /** Snow falling over the crater, mist in it, motes rising off the heart. */
    private void aftermath(ClientLevel level, int t) {
        HalleyPlan p = this.plan;
        RandomSource r = this.random;
        double radius = p.params.craterRadius();
        float fade = 1.0F - (float) (t - p.impact) / (p.duration - p.impact);
        for (int i = 0; i < this.count((int) (26 * fade) + 1); i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * radius * 1.6;
            particle(level, ParticleTypes.SNOWFLAKE, p.target.add(Math.cos(a) * d, 30.0 + r.nextDouble() * 50.0, Math.sin(a) * d),
                this.gauss(0.05), -0.1 - r.nextDouble() * 0.15, this.gauss(0.05));
        }
        for (int i = 0; i < this.count(3); i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * radius * 0.9;
            Vec3 at = this.ground(level, p.target.x + Math.cos(a) * d, p.target.z + Math.sin(a) * d, p.target.y);
            particle(level, ParticleTypes.CLOUD, at.add(0.0, 0.8, 0.0), this.gauss(0.03), 0.02 + r.nextDouble() * 0.04, this.gauss(0.03));
        }
        if (this.visuals.hasHeart()) {
            Vec3 heart = this.visuals.heart();
            for (int i = 0; i < this.count(2); i++) {
                particle(level, ParticleTypes.END_ROD, heart.add(this.gauss(5.0), this.gauss(6.0), this.gauss(5.0)),
                    this.gauss(0.02), 0.04 + r.nextDouble() * 0.08, this.gauss(0.02));
            }
            if (t % 7 == 0) {
                particle(level, ParticleTypes.ELECTRIC_SPARK, heart.add(this.gauss(3.0), this.gauss(8.0), this.gauss(3.0)), 0.0, 0.0, 0.0);
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

    /** The first air above the ground at (x, z), or the fallback height where the chunk isn't loaded. */
    private Vec3 ground(ClientLevel level, double x, double z, double fallback) {
        double y = surface(level, x, z);
        return new Vec3(x, Double.isNaN(y) ? fallback : y, z);
    }

    private static double surface(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return y <= level.getMinY() ? Double.NaN : y;
    }

    private BlockState groundState(ClientLevel level, Vec3 ground) {
        BlockState state = level.getBlockState(BlockPos.containing(ground.x, ground.y - 0.5, ground.z));
        return state.isAir() || !state.getFluidState().isEmpty() ? Blocks.PACKED_ICE.defaultBlockState() : state;
    }

    // ---- Rendering. -----------------------------------------------------------------------------------------------

    public void render(GlowBatch world, GlowBatch light, float partial, double landRange, CometVisuals.Land land) {
        double t = this.age + partial;
        this.visuals.draw(world, light, t, this.marks, landRange, land, this.host.filmWeight());
        if (HalleyClientConfig.extraEffects()) {
            float glitter = this.veil(t) * this.skyReach(world.camera());
            this.extras.draw(world, light, t, land, glitter, this.visuals.heartTip());
        }
    }

    // ---- The sky (drawn by client/sky). ---------------------------------------------------------------------------

    /**
     * What this strike does to the sky right now, seen from {@code camera}: it goes to night as the uplink locks on
     * (stars, an aurora, the comet lighting the air), the fireball outshines the stars as it comes down, and after the
     * impact the air is full of ice: a pale haze with a halo round the sun, clearing as the crystals settle.
     */
    public SkyLook sky(float partial, Vec3 camera) {
        HalleyPlan p = this.plan;
        double t = this.age + partial;
        double impact = p.impact;
        float heat = CometVisuals.smooth((t - (HalleyPlan.ENTRY - 20.0)) / 50.0);
        // the night lingers a moment after the impact, so the blast lights up a dark sky before the haze rolls in
        float night = CometVisuals.smooth((t - 12.0) / 50.0)
            * (1.0F - 0.3F * CometVisuals.smooth((t - HalleyPlan.TOUCHDOWN) / 20.0))
            * (1.0F - CometVisuals.smooth((t - impact - 4.0) / 36.0));
        float stars = night * (1.0F - 0.75F * heat);
        float aurora = CometVisuals.smooth((t - 60.0) / 70.0) * (1.0F - CometVisuals.smooth((t - (HalleyPlan.ENTRY - 30.0)) / 60.0));
        float halo = CometVisuals.smooth((t - (impact + 45.0)) / 40.0) * (1.0F - CometVisuals.smooth((t - (p.duration - 70.0)) / 50.0));
        float flash = 0.35F * pulse(t, HalleyPlan.ENTRY, 4.0) + 0.9F * pulse(t, HalleyPlan.TOUCHDOWN, 3.5) + 1.4F * pulse(t, impact, 5.0);

        // where the light in the sky comes from: the comet, the plough, then the glow over the crater
        Vec3 at;
        float glow;
        float colour;
        if (t <= HalleyPlan.TOUCHDOWN) {
            at = p.comet(t);
            glow = (0.3F + 0.7F * (float) Math.pow(HalleyPlan.approach(t), 3.0)) * CometVisuals.smooth((t - HalleyPlan.SIGHT) / 30.0) + 1.2F * heat;
            colour = heat;
        } else if (t <= impact) {
            at = p.comet(t);
            glow = 1.0F;
            colour = 0.5F;
        } else {
            at = p.target.add(0.0, p.params.craterRadius() * 0.4, 0.0);
            glow = 1.5F * (float) Math.exp(-(t - impact) / 25.0);
            colour = 0.1F;
        }
        Vec3 toward = at.subtract(camera);
        toward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : toward.normalize();
        float land = night * (1.0F - 0.55F * heat) * 0.85F;
        float w = this.skyReach(camera);

        // with a shader pack on, the night is the pack's own (HalleySky.dayTime): the sky's clock is wound on through
        // the evening into the night as the sky goes dark, and after the impact on through the dawn to the real time
        // again, or back the way it came if that's shorter
        double wound = this.toNight * CometVisuals.smooth((t - 12.0) / 50.0) * w;
        float rise = CometVisuals.smooth((t - impact - 4.0) / 36.0);
        double shift = wound >= 12000.0 ? wound + (24000.0 - wound) * rise : wound * (1.0 - rise);

        return new SkyLook(night * w, stars * w, aurora * w, this.veil(t) * w, halo * w, flash * w, toward, glow * w, colour,
            this.visuals.tailDirection(), land * w, (float) (p.seed & 1023L), (float) shift, SkyLook.PALETTE_HALLEY);
    }

    /** The haze of ice after the impact: rolling in as the blast fades, clearing over the rest of the strike. */
    private float veil(double t) {
        double impact = this.plan.impact;
        return CometVisuals.smooth((t - impact - 10.0) / 35.0)
            * (1.0F - CometVisuals.smooth((t - (impact + 80.0)) / Math.max(20.0, this.plan.duration - impact - 100.0)));
    }

    /** The sky changes fully within 900 blocks of the mark, and not at all beyond 2000. */
    private float skyReach(Vec3 camera) {
        double d = Math.hypot(camera.x - this.plan.target.x, camera.z - this.plan.target.z);
        return 1.0F - CometVisuals.smooth((d - 900.0) / 1100.0);
    }

    private static float pulse(double t, double at, double decay) {
        return t < at ? 0.0F : (float) Math.exp(-(t - at) / decay);
    }

    /** Post-processing for this frame: impact frames, flashes, shake, bloom. */
    public void screen(Screen screen, float partial) {
        HalleyPlan p = this.plan;
        float t = this.age + partial;
        boolean film = this.host.filmWeight() > 0.001F;
        Vec3 camera = camera();

        // the mark lands
        float mark = window(t, HalleyPlan.MARK, HalleyPlan.MARK + 0.5F, HalleyPlan.MARK + 1.0F, HalleyPlan.MARK + 7.0F);
        if (mark > 0.0F) {
            float near = film ? 1.0F : proximity(camera, p.target, 500.0);
            screen.aberration(0.3F * mark * near);
            screen.flash(CometVisuals.ICE, 0.1F * mark * near);
        }

        // it comes down
        if (t > HalleyPlan.ENTRY && t < HalleyPlan.TOUCHDOWN) {
            float k = CometVisuals.smooth((t - HalleyPlan.ENTRY) / (double) (HalleyPlan.TOUCHDOWN - HalleyPlan.ENTRY));
            float near = film ? 1.0F : proximity(camera, p.touchdown, 1100.0);
            screen.vignette(0x081E46, k * 0.4F * near);
            screen.shake(k * k * 0.3F * near);
            screen.aberration(k * k * 0.25F * near);
        }

        // touchdown
        float touchdown = (t - HalleyPlan.TOUCHDOWN) / 20.0F;
        if (touchdown >= 0.0F && touchdown < 4.0F) {
            float near = film ? 1.0F : proximity(camera, p.touchdown, 1100.0);
            impactSequence(screen, touchdown, p.touchdown.add(0.0, 10.0, 0.0), near, 0.05F, 3, 3, 1, 3, 1, 1);
            screen.flash(CometVisuals.PALE, 1.2F * near * (touchdown < 0.05F ? 1.0F : (float) Math.exp(-(touchdown - 0.05F) * 5.0F)));
            screen.zoomBlur(window(touchdown, 0.0F, 0.03F, 0.3F, 1.2F) * 0.5F * near);
            screen.shake(window(touchdown, 0.0F, 0.05F, 0.8F, 2.5F) * 0.7F * near);
            screen.aberration(window(touchdown, 0.0F, 0.05F, 0.5F, 2.0F) * 0.8F * near);
        }

        // the plough
        if (t > HalleyPlan.TOUCHDOWN && t < p.impact) {
            float near = film ? 1.0F : proximity(camera, p.comet(t), 700.0);
            screen.shake(0.2F * near);
            screen.aberration(0.15F * near);
            screen.bloom(0.3F * near, 1.2F);
        }

        // the detonation
        float since = (t - p.impact) / 20.0F;
        if (since >= 0.0F && since < 9.0F) {
            float near = film ? 1.0F : proximity(camera, p.target, 1300.0);
            Vec3 focus = p.target.add(0.0, p.params.craterRadius() * 0.3, 0.0);
            impactSequence(screen, since, focus, near, 0.05F, 3, 1, 3, 1, 0, 3);
            screen.flash(CometVisuals.WHITE, 1.3F * near * (since < 0.05F ? 1.0F : (float) Math.exp(-(since - 0.05F) * 6.0F)));
            screen.zoomBlur(window(since, 0.0F, 0.03F, 0.35F, 1.4F) * 0.6F * near);
            screen.shake(window(since, 0.0F, 0.05F, 2.0F, 5.0F) * 0.9F * near);
            screen.aberration(window(since, 0.0F, 0.05F, 0.6F, 3.0F) * near);
            float hold = window(t, p.impact, p.impact + 6.0F, p.impact + 90.0F, p.impact + 140.0F);
            screen.bloom(1.0F * hold, 1.0F + 1.0F * hold);
            screen.vignette(0x0A2C55, hold * 0.35F * near);
            screen.stoppedWorld(window(since, 0.0F, 0.02F, 0.25F, 0.5F) * near, 12.0F);
            screen.desaturate(0.25F * window(since, 0.5F, 1.5F, 5.0F, 9.0F) * near);
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

    /** Right arm: raised to the sky as the mark lands, then held out in front until the end. */
    public float[] armPose(float partial) {
        float t = this.age + partial;
        float end = this.plan.duration;
        float[][] keys = {
            {0.0F, -0.35F}, {4.0F, -1.4F}, {9.0F, -1.4F}, {16.0F, -2.35F}, {58.0F, -2.35F}, {78.0F, -1.25F},
            {end - 30.0F, -1.25F}, {end - 10.0F, -0.35F}
        };
        float x = keys[keys.length - 1][1];
        for (int i = 0; i < keys.length - 1; i++) {
            if (t >= keys[i][0] && t < keys[i + 1][0]) {
                x = Mth.lerp(CometVisuals.smooth((t - keys[i][0]) / (keys[i + 1][0] - keys[i][0])), keys[i][1], keys[i + 1][1]);
                break;
            }
        }
        return new float[]{x, -0.12F, 0.0F, window(t, 0.0F, 3.0F, end - 14.0F, end - 6.0F)};
    }

    /**
     * The Stellar Remote's own animation runs on a 352-tick clock (cover open, button, screen counting down to the
     * impact at 352). This maps SS-05's timeline onto it, so the remote counts down to SS-05's detonation.
     */
    public float remoteClock(float partial) {
        float t = this.age + partial;
        float impact = this.plan.impact;
        float clock = t < HalleyPlan.MARK ? t
            : t < impact ? HalleyPlan.MARK + (t - HalleyPlan.MARK) * (352.0F - HalleyPlan.MARK) / (impact - HalleyPlan.MARK)
            : 352.0F + (t - impact);
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

    /** The point within {@code radius} of {@code centre} nearest the listener: a wide event is heard from its edge. */
    static Vec3 near(Vec3 camera, Vec3 centre, double radius) {
        Vec3 flat = new Vec3(camera.x - centre.x, 0.0, camera.z - centre.z);
        double d = flat.length();
        Vec3 p = d < 1.0E-3 ? centre : centre.add(flat.scale(Math.min(d, radius) / d));
        return new Vec3(p.x, centre.y + Mth.clamp(camera.y - centre.y, 0.0, 60.0), p.z);
    }

    /** Something far off in the sky: heard from its direction, but near enough to be heard at all. */
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

    private record Later(int at, Runnable run) {
    }
}
