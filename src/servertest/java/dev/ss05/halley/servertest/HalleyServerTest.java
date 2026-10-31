package dev.ss05.halley.servertest;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1201.skill.Skill;
import cyou.rimuru.rimulib.mc1201.skill.SkillSet;
import cyou.rimuru.rimulib.mc1201.skill.Tuning;
import cyou.rimuru.shootingstardemo.mc1201.magic.Casting;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.compat.StrikeInfo;
import dev.ss05.halley.compat.TssVersion;
import dev.ss05.halley.content.HalleyContent;
import dev.ss05.halley.world.HalleyInfo;
import dev.ss05.halley.world.MoonInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Dev-only game-logic test for SS-05 Halley and SS-06 Luna. Does nothing unless started with
 * {@code -Dss05.servertest=true} (see {@code ./gradlew runServerTest}). Casts each skill for real through The
 * Shooting Star's own casting path (the same one its network handler uses), on a throwaway flat arena, then checks
 * what the strike did to the world and to some test mobs, and halts the server with a summary.
 *
 * <p>Everything is logged as {@code [SS05TEST] PASS <name>} / {@code [SS05TEST] FAIL <name>: <details>}, and
 * finally {@code [SS05TEST] SUMMARY x/y passed}. SS-05's checks run first (phases {@code WAIT}..{@code WATCH}),
 * then SS-06's (phases {@code MOON_*}).
 */
@Mod("ss05_servertest")
public final class HalleyServerTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = "true".equals(System.getProperty("ss05.servertest"));

    private static final double CAST_DISTANCE = 220.0;
    // The mark is wherever The Shooting Star's own aim-ground logic puts it, at most "reach" from the player, and the
    // touchdown point a further trench_length past it. The test's run config shrinks "reach" to 48 (see
    // src/servertest/server-setup/config/shooting_star_addition-common.toml, copied into run/servertest before each run) so
    // the loaded arena stays a manageable square; the strike's own sizes (trench/crater/blast) are the shipped defaults.
    private static final int MIN_CHUNK_X = -28;
    private static final int MAX_CHUNK_X = 28;
    private static final int MIN_CHUNK_Z = -28;
    private static final int MAX_CHUNK_Z = 28;
    /** Hard stop: 2 minutes of game time after the cast, win or lose. */
    private static final int HARD_TIMEOUT_TICKS = 2400;
    /** Luna's strike is much shorter (MoonPlan.DURATION = 680 ticks); half a minute of margin is plenty. */
    private static final int MOON_HARD_TIMEOUT_TICKS = 1200;

    private enum Phase {
        WAIT, CAST, POSTCAST, WATCH,
        MOON_SET_DAY, MOON_CAST_DAY, MOON_CAST_NIGHT, MOON_POSTCAST, MOON_WATCH, MOON_EAT,
        DONE
    }

    private Phase phase = Phase.WAIT;
    private MinecraftServer server;
    private ServerLevel level;
    private ServerPlayer caster;
    private int tick;
    private int castTick = -1;
    private int skillIndexAtCast = -1;
    private boolean wasRunning;
    private boolean earlyDeathFail;
    private HalleyPlan replicaPlan;
    /** The run's dedicated-server.properties sets up a flat world; this is that floor's surface Y, read back from
     *  the heightmap rather than assumed, so it does not drift if the generator settings ever change. */
    private int groundY;

    private Pig insideMob1;
    private Pig insideMob2;
    private Pig besideMob;
    private Pig controlMob;

    // ---- SS-06 Luna. ------------------------------------------------------------------------------------------------
    private int moonCastTick = -1;
    private int moonSkillIndexAtCast = -1;
    private boolean moonWasRunning;
    private boolean moonEarlyDeathFail;
    private MoonPlan moonReplicaPlan;
    private Pig moonInsideMob;
    private Pig moonBesideMob;
    /** The region ticket this test itself adds round where Luna's mark will land (MoonStrike no longer adds one of
     *  its own, §12), since the fake caster is never "nearby" in the sense a real, chunk-tracking client would be.
     *  Added just before the cast, removed once the final checks have read the world. */
    private net.minecraft.world.level.ChunkPos moonTicketChunk;
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> MOON_TEST_TICKET =
        net.minecraft.server.level.TicketType.create("ss06test", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

    private int passCount;
    private int totalCount;

    public HalleyServerTest() {
        if (!ENABLED) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!ENABLED) {
            return;
        }
        this.server = event.getServer();
        this.level = this.server.overworld();
        LOG.info("[SS05TEST] dedicated test server started, scheduling the test");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || this.server == null || this.phase == Phase.DONE || event.phase != TickEvent.Phase.END) {
            return;
        }
        this.tick++;
        switch (this.phase) {
            case WAIT:
                if (this.tick >= 5) {
                    boolean ok = this.setup();
                    this.phase = ok ? Phase.CAST : Phase.DONE;
                    if (this.phase == Phase.DONE) {
                        this.finish();
                    }
                }
                break;
            case CAST:
                this.performCast();
                this.phase = Phase.POSTCAST;
                break;
            case POSTCAST:
                this.postCast();
                this.phase = Phase.WATCH;
                break;
            case WATCH:
                this.watch();
                break;
            case MOON_SET_DAY:
                // Turn to face the opposite horizontal direction from SS-05's cast (yaw 270 -> 90): the two tests
                // share one arena and one caster, and The Shooting Star's own aim-ground has some lateral scatter of
                // its own (SS-05's mark landed noticeably off the straight line out of the caster), so without this
                // the moonfall's crater can end up close enough to SS-05's crater/blast zone that leftover comet_trail
                // (SS-05's debris) is still sitting on top where this test wants to see Luna's own crater surface.
                // Facing the other way puts Luna's mark on the far side of spawn from SS-05's, comfortably clear.
                this.caster.setYRot(90.0F);
                this.caster.setYHeadRot(90.0F);
                this.caster.yHeadRotO = 90.0F;
                // isNight()/isDay() read ServerLevel's skyDarken, recomputed once a tick from the day time - give it
                // a tick to catch up before the day-cast attempt below.
                this.level.setDayTime(1000L);
                this.phase = Phase.MOON_CAST_DAY;
                break;
            case MOON_CAST_DAY:
                this.castMoonByDay();
                this.level.setDayTime(18000L);
                this.phase = Phase.MOON_CAST_NIGHT;
                break;
            case MOON_CAST_NIGHT:
                this.castMoonByNight();
                this.phase = Phase.MOON_POSTCAST;
                break;
            case MOON_POSTCAST:
                this.moonPostCast();
                this.phase = Phase.MOON_WATCH;
                break;
            case MOON_WATCH:
                this.moonWatch();
                break;
            case MOON_EAT:
                this.moonEat();
                this.phase = Phase.DONE;
                this.finish();
                break;
            default:
                break;
        }
    }

    // ---- Setup. -----------------------------------------------------------------------------------------------

    /** Returns false if the bridge never attached (nothing else to usefully test then). */
    private boolean setup() {
        this.checkSkillRegistration();
        this.checkMoonSkillRegistration();
        if (!StarBridge.installed()) {
            return false;
        }
        try {
            this.buildArena();
        } catch (Throwable ex) {
            this.record("arena_built", false, "threw " + ex);
            return false;
        }
        this.record("arena_built", true, "");

        this.caster = FakePlayerFactory.getMinecraft(this.level);
        this.caster.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("shooting_star_demo", "stellar_remote"))));
        double eyeHeight = this.caster.getEyeHeight();
        float pitch = (float) Math.toDegrees(Math.atan2(eyeHeight, CAST_DISTANCE));
        // yaw 270 = facing +X (east), a flat look slightly downward to land on the arena's floor ~220 blocks out.
        this.caster.moveTo(0.5, this.groundY, 0.5, 270.0F, pitch);
        // a player's look comes from its head rotation, which moveTo leaves alone
        this.caster.setYHeadRot(270.0F);
        this.caster.yHeadRotO = 270.0F;
        return true;
    }

    private void checkSkillRegistration() {
        boolean attached = StarBridge.installed();
        this.record("bridge_attached", attached, attached ? "" : StarBridge.problem());
        if (!attached) {
            return;
        }

        Skill skill = (Skill) StarBridge.skill(false);
        SkillSet owner = SkillSet.of(skill);
        // RimuLib doesn't name which SkillSet is "the Stellar Remote"; Luna joining the same set it did is as close
        // a check as there is without it.
        this.record("skill_owned_by_stellar_remote", owner != null && owner == SkillSet.of((Skill) StarBridge.skill(true)), "owner=" + owner);

        // The Shooting Star's own skills come first: Halley right after them, under its own codename (SA-01, a
        // series of the addon's own, so it never clashes with a built-in SS number).
        int builtIns = SkillSet.skillCount(TssVersion.MOD_ID) - 2;
        int index = SkillSet.indexOf(skill);
        this.record("skill_index_after_builtins", index == builtIns, "index=" + index + " builtIns=" + builtIns);
        this.record("skill_codename", skill.title().equals("SA-01 · Halley"), "title=" + skill.title());
        this.record("skill_id_is_halley", "halley".equals(skill.id()), "id=" + skill.id());
        this.record("skill_default_key_is_O", skill.defaultKey() == 79 && skill.defaultKey() == HalleyInfo.DEFAULT_KEY, "key=" + skill.defaultKey());
        this.record("skill_cooldown_matches_config", skill.cooldown() == HalleyConfig.tuning().cooldownTicks(),
            "cooldown=" + skill.cooldown() + " config=" + HalleyConfig.tuning().cooldownTicks());
        int expectedDuration = HalleyPlan.durationFor(HalleyConfig.tuning().trenchLength());
        this.record("skill_duration_matches_plan", skill.duration() == expectedDuration,
            "duration=" + skill.duration() + " expected=" + expectedDuration);

        try {
            ResourceLocation art = owner.art(skill);
            boolean ok = art != null && art.getPath().toLowerCase(java.util.Locale.ROOT).contains("halley");
            this.record("skill_art_name", ok, "art=" + art);
        } catch (Throwable ex) {
            this.record("skill_art_name", false, "threw " + ex);
        }

        // The remote's sliders under Halley: The Shooting Star's own power range (so its menu shows power, damage and
        // speed sliders for it), 0.1x to 10x, starting at 1x; a cast clamps to it.
        try {
            var power = skill.power();
            boolean ok = power != null && power.min() == 0.1F && power.max() == 10.0F && power.initial() == 1.0F && power.factor();
            this.record("skill_power_slider", ok, "power=" + power);
            Tuning clamped = new Tuning(50.0F, 3.0F, 9.0F).clampFor(skill);
            this.record("skill_tuning_clamps", clamped.power() == 10.0F && clamped.speed() == 3.0F && clamped.damage() == 3.0F,
                "clamped=" + clamped);
        } catch (Throwable ex) {
            this.record("skill_power_slider", false, "threw " + ex);
        }

        // ...and what a cast makes of them: the sizes times the power, the comet as much bigger, the same timeline.
        try {
            HalleyConfig.Tuning tuning = HalleyConfig.tuning();
            HalleyParams base = HalleyParams.of(tuning, 64);
            HalleyParams twice = HalleyParams.of(tuning, 64, 2.0F);
            HalleyParams tenth = HalleyParams.of(tuning, 64, 0.1F);
            boolean ok = twice.craterRadius() == base.craterRadius() * 2 && twice.trenchLength() == base.trenchLength() * 2
                && twice.scale() == 2.0F && tenth.craterRadius() == Math.max(3, Math.round(base.craterRadius() * 0.1F))
                && HalleyParams.decode(twice.encode(), 0).equals(twice);
            Vec3 o = new Vec3(0.0, 64.0, 0.0);
            Vec3 t = new Vec3(100.0, 64.0, 0.0);
            int baseImpact = new HalleyPlan(o, t, 0.0F, 7L, base).impact;
            int twiceImpact = new HalleyPlan(o, t, 0.0F, 7L, twice).impact;
            this.record("power_scales_halley", ok && Math.abs(baseImpact - twiceImpact) <= 1,
                "base=" + base + " twice=" + twice + " impact " + baseImpact + "/" + twiceImpact);
            dev.ss05.halley.CastTuning odd = new dev.ss05.halley.CastTuning(Float.NaN, -1.0F, 9.0F);
            this.record("cast_tuning_defaults", odd.power() == 1.0F && odd.erases() && odd.speed() == 3.0F
                && new dev.ss05.halley.CastTuning(1.0F, 20.0F, 1.0F).cap(40.0F) == 20.0F
                && dev.ss05.halley.CastTuning.DEFAULT.cap(40.0F) == 40.0F, "odd=" + odd);
        } catch (Throwable ex) {
            this.record("power_scales_halley", false, "threw " + ex);
        }
    }

    /** Same shape as {@link #checkSkillRegistration()}, for Luna (right after Halley, key N). */
    private void checkMoonSkillRegistration() {
        if (!StarBridge.installed()) {
            return;
        }

        Skill skill = (Skill) StarBridge.skill(true);
        SkillSet owner = SkillSet.of(skill);
        this.record("moon_skill_owned_by_stellar_remote", owner != null && owner == SkillSet.of((Skill) StarBridge.skill(false)), "owner=" + owner);

        int builtIns = SkillSet.skillCount(TssVersion.MOD_ID) - 2;
        int index = SkillSet.indexOf(skill);
        this.record("moon_skill_index_after_halley", index == builtIns + 1, "index=" + index + " builtIns=" + builtIns);
        this.record("moon_skill_codename", skill.title().equals("SA-02 · Luna"), "title=" + skill.title());
        this.record("moon_skill_id_is_luna", "luna".equals(skill.id()), "id=" + skill.id());
        this.record("moon_skill_default_key_is_N", skill.defaultKey() == 78 && skill.defaultKey() == MoonInfo.DEFAULT_KEY, "key=" + skill.defaultKey());
        this.record("moon_skill_cooldown_matches_config", skill.cooldown() == MoonConfig.tuning().cooldownTicks(),
            "cooldown=" + skill.cooldown() + " config=" + MoonConfig.tuning().cooldownTicks());
        this.record("moon_skill_duration_matches_plan", skill.duration() == MoonPlan.DURATION,
            "duration=" + skill.duration() + " expected=" + MoonPlan.DURATION);

        try {
            ResourceLocation art = owner.art(skill);
            boolean ok = art != null && art.getPath().toLowerCase(java.util.Locale.ROOT).contains("luna");
            this.record("moon_skill_art_name", ok, "art=" + art);
        } catch (Throwable ex) {
            this.record("moon_skill_art_name", false, "threw " + ex);
        }

        // Luna's sliders, and what a cast makes of them: the moon, the crater and the rim times the power.
        try {
            var power = skill.power();
            MoonConfig.Tuning tuning = MoonConfig.tuning();
            Vec3 sky = new Vec3(0.3, 0.8, 0.2);
            MoonParams base = MoonParams.of(tuning, 64, sky);
            MoonParams half = MoonParams.of(tuning, 64, sky, 0.5F);
            MoonParams most = MoonParams.of(tuning, 64, sky, 10.0F);
            boolean ok = power != null && power.max() == 10.0F && power.initial() == 1.0F
                && half.moonRadius() == Math.round(base.moonRadius() * 0.5F) && half.craterRadius() == Math.round(base.craterRadius() * 0.5F)
                && most.moonRadius() == base.moonRadius() * 10 && most.craterRadius() == base.craterRadius() * 10
                && MoonParams.decode(most.encode(), 0, sky).moonRadius() == most.moonRadius()
                && MoonParams.decode(half.encode(), 0, sky).scale() == 0.5F;
            this.record("power_scales_luna", ok, "power=" + power + " base=" + base.moonRadius() + "/" + base.craterRadius()
                + " half=" + half.moonRadius() + "/" + half.craterRadius() + " most=" + most.moonRadius() + "/" + most.craterRadius());
        } catch (Throwable ex) {
            this.record("power_scales_luna", false, "threw " + ex);
        }
    }

    /**
     * Forces and force-loads every chunk the strike (default config sizes) can reach. The arena's floor is not
     * built here - run/servertest/server.properties sets the world's generator to a thick flat stone layer, so
     * every chunk already has the same solid, uniform floor the moment it is generated, with its heightmap already
     * correct (a hand-built platform placed with the fast bulk carver, {@link StarBridge#paint}, looked solid but
     * left the heightmap stale, which both The Shooting Star's own aim-ground logic and this test's scans read).
     */
    private void buildArena() {
        for (int cx = MIN_CHUNK_X; cx <= MAX_CHUNK_X; cx++) {
            for (int cz = MIN_CHUNK_Z; cz <= MAX_CHUNK_Z; cz++) {
                this.level.getChunk(cx, cz);
                // Force-load the whole arena (not just the strike's own, smaller chunk ticket) so blocks and
                // entities in it stay loaded for the length of the test regardless of where the mark lands.
                this.level.setChunkForced(cx, cz, true);
            }
        }
        this.groundY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        LOG.info("[SS05TEST] arena ready: {} chunks forced, floor surface y={}",
            (MAX_CHUNK_X - MIN_CHUNK_X + 1) * (MAX_CHUNK_Z - MIN_CHUNK_Z + 1), this.groundY);
    }

    // ---- The cast. ----------------------------------------------------------------------------------------------

    private void performCast() {
        this.castTick = this.tick;
        this.skillIndexAtCast = StarBridge.skillIndex();
        this.record("skill_index_available", this.skillIndexAtCast >= 0, "index=" + this.skillIndexAtCast);
        try {
            // What The Shooting Star's own cast-packet handler does (CastSkillPayload -> Casting.request -> cast).
            this.request(this.skillIndexAtCast);
            this.record("cast_request_no_exception", true, "");
        } catch (Throwable ex) {
            this.record("cast_request_no_exception", false, "threw " + ex);
        }
    }

    private void postCast() {
        boolean running = StarBridge.alreadyFlying(this.caster);
        this.record("cast_starts_spell", running, running ? "" : "SpellEngine.running() is false right after the cast");

        StrikeInfo found = StarBridge.activeStrike(false, this.caster.getUUID());
        this.record("read_active_spell_reflection", found != null,
            found != null ? "" : "no matching active strike on the server");

        if (found == null) {
            return;
        }

        try {
            HalleyConfig.Tuning tuning = HalleyConfig.tuning();
            Vec3 origin = found.origin();
            Vec3 target = found.target();
            Vec3 flat = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
            Vec3 dir = flat.lengthSqr() < 1.0 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
            double tdX = target.x + dir.x * tuning.trenchLength();
            double tdZ = target.z + dir.z * tuning.trenchLength();
            int ground = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(tdX), Mth.floor(tdZ));
            HalleyParams params = HalleyParams.of(tuning, ground);
            this.replicaPlan = new HalleyPlan(origin, target, this.caster.getYRot(), found.seed(), params);
            this.record("replica_plan_built", true, "target=" + target + " touchdown=" + this.replicaPlan.touchdown);
        } catch (Throwable ex) {
            this.record("replica_plan_built", false, "threw " + ex);
            return;
        }

        try {
            this.placeMobs();
            this.record("test_mobs_placed", true, "");
        } catch (Throwable ex) {
            this.record("test_mobs_placed", false, "threw " + ex);
        }

        try {
            boolean before = StarBridge.alreadyFlying(this.caster);
            this.request(this.skillIndexAtCast);
            boolean after = StarBridge.alreadyFlying(this.caster);
            int remaining = this.remaining((Skill) StarBridge.skill(false));
            int expected = HalleyConfig.tuning().cooldownTicks();
            boolean pass = after == before && remaining > 0 && remaining <= expected && remaining >= expected - 40;
            this.record("cooldown_refuses_second_cast", pass,
                "before=" + before + " after=" + after + " remaining=" + remaining + " expectedCooldown=" + expected);
        } catch (Throwable ex) {
            this.record("cooldown_refuses_second_cast", false, "threw " + ex);
        }

        try {
            // The no-cooldown option (compat/tss/mixin/CastingMixin, NoCooldown): the caster here is in survival, so
            // no_cooldown alone keeps their cooldown (it is for creative players unless no_cooldown_survival says
            // otherwise); with both on, the next time the remote sends them their cooldowns they are cleared first.
            setFlag("NO_COOLDOWN", true);
            Casting.syncCooldowns(this.caster);
            int kept = this.remaining((Skill) StarBridge.skill(false));
            this.record("no_cooldown_creative_only_by_default", kept > 0, "remaining=" + kept + " with no_cooldown on, survival off");
            setFlag("NO_COOLDOWN_SURVIVAL", true);
            Casting.syncCooldowns(this.caster);
            int remaining = this.remaining((Skill) StarBridge.skill(false));
            this.record("no_cooldown_clears_cooldowns", remaining == 0, "remaining=" + remaining + " with no_cooldown and no_cooldown_survival on");
        } catch (Throwable ex) {
            this.record("no_cooldown_clears_cooldowns", false, "threw " + ex);
        } finally {
            try {
                setFlag("NO_COOLDOWN", false);
                setFlag("NO_COOLDOWN_SURVIVAL", false);
            } catch (Throwable ignored) {
                // (only the test's own switches)
            }
        }
    }

    /** Switches one of HalleyConfig's boolean options on or off for the test (the config file itself keeps them off). */
    private static void setFlag(String name, boolean on) throws ReflectiveOperationException {
        Field field = HalleyConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue) field.get(null)).set(on);
    }

    /**
     * How many more ticks until this skill is ready again for the test's caster, 0 if it's ready now (The Shooting
     * Star 1.5.0's {@code Casting.readyAt(player)} is a {@code long[]} indexed the same way the remote's menu slots
     * are, {@link SkillSet#indexOf}).
     */
    private int remaining(Skill skill) {
        long readyAt = Casting.readyAt(this.caster)[SkillSet.indexOf(skill)];
        return (int) Math.max(0L, readyAt - this.level.getGameTime());
    }

    // ---- Test mobs. -----------------------------------------------------------------------------------------------

    private void placeMobs() {
        HalleyPlan plan = this.replicaPlan;
        double radius = plan.params.craterRadius();
        double reach = radius * plan.params.blastReach();
        // Both "along < 0" (behind the mark): clear of the trench/wake zones, purely inside the crater.
        this.insideMob1 = this.spawnPig(plan, -15.0, 12.0, "ss05test_inside_1");
        this.insideMob2 = this.spawnPig(plan, -20.0, -14.0, "ss05test_inside_2");
        // Between the crater radius and the blast reach, but close enough to the outer edge that the shock wave's
        // damage (which scales up sharply toward the crater) stays well under a pig's 10 HP, so it survives hurt.
        double besideDistance = radius + (reach - radius) * 0.6;
        this.besideMob = this.spawnPig(plan, 0.0, besideDistance, "ss05test_beside");
        // Clearly outside the blast reach: a control that should come out of this untouched.
        this.controlMob = this.spawnPig(plan, 0.0, reach + 50.0, "ss05test_control");
    }

    private Pig spawnPig(HalleyPlan plan, double along, double across, String name) {
        Vec3 p = plan.target.add(plan.dir.scale(along)).add(plan.side.scale(across));
        Pig pig = new Pig(EntityType.PIG, this.level);
        pig.moveTo(p.x, this.groundY, p.z, 0.0F, 0.0F);
        pig.setNoAi(true);
        pig.setPersistenceRequired();
        pig.setCustomName(Component.literal(name));
        this.level.addFreshEntity(pig);
        return pig;
    }

    // ---- Watching the strike run. ----------------------------------------------------------------------------------

    private void watch() {
        int elapsed = this.tick - this.castTick;
        boolean running = StarBridge.alreadyFlying(this.caster);
        if (running) {
            this.wasRunning = true;
        }

        if (!this.earlyDeathFail && elapsed > 0 && elapsed < HalleyPlan.TOUCHDOWN - 2
                && this.insideMob1 != null && this.insideMob2 != null
                && (!this.insideMob1.isAlive() || !this.insideMob2.isAlive())) {
            this.earlyDeathFail = true;
            LOG.error("[SS05TEST] a mob inside the crater died at tick {} (before HalleyPlan.TOUCHDOWN={})", elapsed, HalleyPlan.TOUCHDOWN);
        }

        boolean finishedNaturally = this.wasRunning && !running;
        boolean timedOut = elapsed > HARD_TIMEOUT_TICKS;
        if (finishedNaturally || timedOut) {
            if (timedOut && !finishedNaturally) {
                LOG.error("[SS05TEST] hard timeout after {} ticks since the cast (still running={})", elapsed, running);
            }
            this.finalizeChecks();
            // On to SS-06 Luna's checks (see the Phase enum javadoc).
            this.phase = Phase.MOON_SET_DAY;
        }
    }

    private void finalizeChecks() {
        this.record("no_early_deaths", !this.earlyDeathFail,
            this.earlyDeathFail ? "a mob inside the crater died before HalleyPlan.TOUCHDOWN" : "");

        if (this.replicaPlan == null) {
            this.record("world_checks_skipped", false, "no replica plan (cast or reflection failed earlier)");
            return;
        }
        HalleyPlan plan = this.replicaPlan;

        try {
            BlockPos trenchSample = this.sampleTopBlock(plan, 120.0, 0.0);
            BlockState trenchState = this.level.getBlockState(trenchSample);
            boolean trenchOk = this.isOneOf(trenchState, Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.CALCITE, Blocks.SNOW_BLOCK,
                Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL, HalleyContent.COMET_TRAIL.get());
            this.record("trench_carved", trenchOk, "block at " + trenchSample + " = " + trenchState.getBlock());
        } catch (Throwable ex) {
            this.record("trench_carved", false, "threw " + ex);
        }

        try {
            BlockPos craterSample = this.sampleTopBlock(plan, -25.0, 25.0);
            BlockState craterState = this.level.getBlockState(craterSample);
            boolean craterOk = this.isOneOf(craterState, Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.CALCITE, Blocks.SNOW_BLOCK,
                Blocks.TUFF, Blocks.SMOOTH_BASALT, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL, HalleyContent.COMET_HEART.get());
            this.record("crater_carved", craterOk, "block at " + craterSample + " = " + craterState.getBlock());
        } catch (Throwable ex) {
            this.record("crater_carved", false, "threw " + ex);
        }

        try {
            boolean heartFound = this.scanForBlock(plan.target, 40, HalleyContent.COMET_HEART.get());
            this.record("comet_heart_placed", heartFound, heartFound ? "" : "no comet_heart found within 40 blocks of the mark");
        } catch (Throwable ex) {
            this.record("comet_heart_placed", false, "threw " + ex);
        }

        try {
            boolean raysFound = this.scanRingForBlocks(plan, plan.params.craterRadius() * 1.3, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE);
            this.record("frost_rays_painted", raysFound, raysFound ? "" : "no snow/packed ice on the ring just beyond the crater");
        } catch (Throwable ex) {
            this.record("frost_rays_painted", false, "threw " + ex);
        }

        this.checkMob("inside_mob_1_erased", this.insideMob1, true);
        this.checkMob("inside_mob_2_erased", this.insideMob2, true);
        this.checkMob("beside_mob_hurt_and_frozen", this.besideMob, false);
        this.checkControlMob();

        boolean notRunning = !StarBridge.alreadyFlying(this.caster);
        this.record("spell_completes_and_stops", notRunning, notRunning ? "" : "SpellEngine still reports it running");
    }

    private void checkMob(String name, Pig mob, boolean expectErased) {
        if (mob == null) {
            this.record(name, false, "mob was never placed");
            return;
        }
        if (expectErased) {
            boolean erased = !mob.isAlive();
            this.record(name, erased, "alive=" + mob.isAlive() + " removed=" + mob.isRemoved());
        } else {
            // "Hurt and frozen" per the strike's own logic (HalleyStrike#throwAndHurt sets both in the same branch),
            // but ticksFrozen decays back toward 0 a couple of points per tick once the entity is no longer in
            // powder snow, and this check runs after the full ~520-tick strike (so well after the ~28-tick blast
            // window) - plenty of time for it to have fully decayed even though it really was set. Either sign of
            // having been caught by the blast is enough, matching how the task describes this check (frozen OR hurt).
            // (SS-06's blast only hurts and ignites, never freezes, so for its mobs this is really just "hurt".)
            boolean hurt = mob.isAlive() && mob.getHealth() < mob.getMaxHealth();
            boolean frozen = mob.getTicksFrozen() > 0;
            this.record(name, mob.isAlive() && (hurt || frozen),
                "alive=" + mob.isAlive() + " health=" + mob.getHealth() + "/" + mob.getMaxHealth() + " ticksFrozen=" + mob.getTicksFrozen());
        }
    }

    private void checkControlMob() {
        if (this.controlMob == null) {
            this.record("control_mob_unaffected", false, "mob was never placed");
            return;
        }
        Pig mob = this.controlMob;
        boolean ok = mob.isAlive() && mob.getHealth() == mob.getMaxHealth() && mob.getTicksFrozen() == 0;
        this.record("control_mob_unaffected", ok,
            "alive=" + mob.isAlive() + " health=" + mob.getHealth() + "/" + mob.getMaxHealth() + " ticksFrozen=" + mob.getTicksFrozen());
    }

    // ==== SS-06 Luna. ================================================================================================

    /** Attempts the cast by day; the test config sets MoonConfig's {@code require_night} (default false), so it should refuse it. */
    private void castMoonByDay() {
        this.moonSkillIndexAtCast = StarBridge.moonIndex();
        this.record("moon_skill_index_available", this.moonSkillIndexAtCast >= 0, "index=" + this.moonSkillIndexAtCast);
        boolean isDay = this.level.isDay();
        this.record("arena_is_day_for_refusal_check", isDay, "isDay=" + isDay + " isNight=" + this.level.isNight());
        try {
            this.request(this.moonSkillIndexAtCast);
            boolean running = StarBridge.moonAlreadyFalling(this.caster);
            this.record("moon_day_cast_refused", !running, running ? "a moonfall started during the day" : "");
        } catch (Throwable ex) {
            this.record("moon_day_cast_refused", false, "threw " + ex);
        }
    }

    /** Attempts the real cast by night, once the day-refusal check above is done. */
    private void castMoonByNight() {
        boolean isNight = this.level.isNight();
        this.record("arena_is_night_for_real_cast", isNight, "isNight=" + isNight);
        this.loadLandRoundMoonMark();
        this.moonCastTick = this.tick;
        try {
            this.request(this.moonSkillIndexAtCast);
            this.record("moon_cast_request_no_exception", true, "");
        } catch (Throwable ex) {
            this.record("moon_cast_request_no_exception", false, "threw " + ex);
        }
    }

    /**
     * MoonStrike no longer force-loads the land round the mark itself (§12: "Luna never loads land it doesn't
     * need") - in real play, a nearby player's own view distance keeps it loaded, which this test's fake caster
     * never provides. Without this, MoonLand's immediate (non-lazy) carving never gets to run on the chunks round
     * the mark within the test's window, and the outer crater/rim checks below would see untouched land. Adds a
     * region ticket centred on where the mark will land - the same horizontal reach-and-direction estimate The
     * Shooting Star's own aim-ground uses (the test's caster stands put and only its yaw differs from SS-05's) -
     * with a radius covering {@link MoonPlan#rimOuter} plus a margin, removed once the final checks have read the
     * world ({@link #finalizeMoonChecks()}).
     */
    private void loadLandRoundMoonMark() {
        double reach = MoonConfig.tuning().reach();
        float yawRad = this.caster.getYRot() * ((float) Math.PI / 180.0F);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double markX = this.caster.getX() + dirX * reach;
        double markZ = this.caster.getZ() + dirZ * reach;
        this.moonTicketChunk = new net.minecraft.world.level.ChunkPos(Mth.floor(markX) >> 4, Mth.floor(markZ) >> 4);

        // rimOuter is roughly craterRadius * 1.4 (MoonPlan.rimOuter); +40 blocks of margin, then to chunks.
        double craterRadius = MoonConfig.tuning().craterRadius();
        int radiusChunks = Mth.ceil((craterRadius * 1.4 + 40.0) / 16.0);
        this.level.getChunkSource().addRegionTicket(MOON_TEST_TICKET, this.moonTicketChunk, radiusChunks, this.moonTicketChunk);
        LOG.info("[SS05TEST] loaded {} chunks round the moon's estimated mark {} (radius {})",
            (radiusChunks * 2 + 1) * (radiusChunks * 2 + 1), this.moonTicketChunk, radiusChunks);
    }

    /** Drops the region ticket {@link #loadLandRoundMoonMark()} added, now that the final checks have read the world. */
    private void releaseLandRoundMoonMark() {
        if (this.moonTicketChunk == null) {
            return;
        }
        double craterRadius = MoonConfig.tuning().craterRadius();
        int radiusChunks = Mth.ceil((craterRadius * 1.4 + 40.0) / 16.0);
        this.level.getChunkSource().removeRegionTicket(MOON_TEST_TICKET, this.moonTicketChunk, radiusChunks, this.moonTicketChunk);
        this.moonTicketChunk = null;
    }

    private void moonPostCast() {
        boolean running = StarBridge.moonAlreadyFalling(this.caster);
        this.record("moon_cast_starts_spell", running, running ? "" : "SpellEngine.running() is false right after the cast");

        StrikeInfo found = StarBridge.activeStrike(true, this.caster.getUUID());
        this.record("moon_read_active_spell_reflection", found != null,
            found != null ? "" : "no matching active strike on the server");

        if (found == null) {
            return;
        }

        try {
            // Rebuilt the same way MoonCasting.cast() builds it: the sky direction read at cast time, from the
            // target/seed the running strike actually used, like HalleyPlan's replica.
            Vec3 sky = MoonPlan.skyDirection(this.level.getTimeOfDay(1.0F));
            MoonParams params = MoonParams.of(MoonConfig.tuning(), Mth.floor(found.target().y), sky);
            this.moonReplicaPlan = new MoonPlan(found.origin(), found.target(), found.seed(), params);
            this.record("moon_replica_plan_built", true, "target=" + found.target() + " core=" + this.moonReplicaPlan.core);
        } catch (Throwable ex) {
            this.record("moon_replica_plan_built", false, "threw " + ex);
            return;
        }

        try {
            this.placeMoonMobs();
            this.record("moon_test_mobs_placed", true, "");
        } catch (Throwable ex) {
            this.record("moon_test_mobs_placed", false, "threw " + ex);
        }
    }

    private void placeMoonMobs() {
        MoonPlan plan = this.moonReplicaPlan;
        double erase = plan.eraseRadius();
        double blast = plan.blastRadius();
        // Well inside the erase radius: should be erased outright.
        this.moonInsideMob = this.spawnMoonPig(plan, erase * 0.3, 0.0, "ss06test_inside");
        // Close to the outer edge of the blast radius, where the shock wave's damage (which scales up sharply
        // toward the crater, see MoonStrike#throwHurtIgnite) is comfortably under a pig's 10 HP, so it survives hurt.
        double besideDistance = erase + (blast - erase) * 0.82;
        this.moonBesideMob = this.spawnMoonPig(plan, 0.0, besideDistance, "ss06test_beside");
        // SS-06's shock wave does at least 10 damage anywhere in its ring (a pig has 10): give this one more health so
        // "hurt but alive" can still be told apart from "erased"
        this.moonBesideMob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
        this.moonBesideMob.setHealth(100.0F);
    }

    private Pig spawnMoonPig(MoonPlan plan, double dx, double dz, String name) {
        Vec3 p = plan.target.add(dx, 0.0, dz);
        Pig pig = new Pig(EntityType.PIG, this.level);
        pig.moveTo(p.x, this.groundY, p.z, 0.0F, 0.0F);
        pig.setNoAi(true);
        pig.setPersistenceRequired();
        pig.setCustomName(Component.literal(name));
        this.level.addFreshEntity(pig);
        return pig;
    }

    private void moonWatch() {
        int elapsed = this.tick - this.moonCastTick;
        boolean running = StarBridge.moonAlreadyFalling(this.caster);
        if (running) {
            this.moonWasRunning = true;
        }

        if (!this.moonEarlyDeathFail && elapsed > 0 && elapsed < MoonPlan.CONTACT - 2
                && this.moonInsideMob != null && !this.moonInsideMob.isAlive()) {
            this.moonEarlyDeathFail = true;
            LOG.error("[SS05TEST] a mob inside the moon's erase radius died at tick {} (before MoonPlan.CONTACT={})", elapsed, MoonPlan.CONTACT);
        }

        boolean finishedNaturally = this.moonWasRunning && !running;
        boolean timedOut = elapsed > MOON_HARD_TIMEOUT_TICKS;
        if (finishedNaturally || timedOut) {
            if (timedOut && !finishedNaturally) {
                LOG.error("[SS05TEST] moon hard timeout after {} ticks since the cast (still running={})", elapsed, running);
            }
            this.finalizeMoonChecks();
            this.phase = Phase.MOON_EAT;
        }
    }

    private void finalizeMoonChecks() {
        this.record("moon_no_early_deaths", !this.moonEarlyDeathFail,
            this.moonEarlyDeathFail ? "a mob inside the erase radius died before MoonPlan.CONTACT" : "");

        if (this.moonReplicaPlan == null) {
            this.record("moon_world_checks_skipped", false, "no replica plan (cast or reflection failed earlier)");
            this.releaseLandRoundMoonMark();
            return;
        }
        MoonPlan plan = this.moonReplicaPlan;

        try {
            // The rim: the ground heaved up round the crater. Sampled all round on its crest, where it stands tallest;
            // the plates vary (and there are cracks between them), so most samples, not all, must be up.
            int raised = 0;
            StringBuilder heights = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6.0;
                double d = plan.rimCrest;
                BlockPos top = this.sampleTopBlockAt(plan.target.x + Math.cos(angle) * d, plan.target.z + Math.sin(angle) * d);
                int over = top.getY() - (this.groundY - 1);
                heights.append(over).append(' ');
                if (over >= 2) {
                    raised++;
                }
            }
            this.record("moon_rim_raised", raised >= 7, raised + "/12 sampled columns raised (heights over the old ground: " + heights + ")");
        } catch (Throwable ex) {
            this.record("moon_rim_raised", false, "threw " + ex);
        }

        try {
            // The crater: dug down well under the old ground across its floor, between the moon and the rim (a vein of
            // molten rock cut into it only goes deeper).
            int dug = 0;
            StringBuilder depths = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0 + 0.4;
                double d = (plan.floorInner + plan.rimInner) * 0.5;
                BlockPos top = this.sampleTopBlockAt(plan.target.x + Math.cos(angle) * d, plan.target.z + Math.sin(angle) * d);
                int over = top.getY() - (this.groundY - 1);
                depths.append(over).append(' ');
                if (over <= -3) {
                    dug++;
                }
            }
            this.record("moon_crater_dug", dug >= 6, dug + "/8 sampled columns dug out (heights over the old ground: " + depths + ")");
        } catch (Throwable ex) {
            this.record("moon_crater_dug", false, "threw " + ex);
        }

        try {
            // The molten seam where the moon meets the crater's floor: a trench of magma and lava (or the moon itself,
            // where it bulges out over it).
            int molten = 0;
            StringBuilder found = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0 + 0.2;
                double d = (plan.restFootprint + plan.floorInner) * 0.5;
                BlockPos top = this.sampleTopBlockAt(plan.target.x + Math.cos(angle) * d, plan.target.z + Math.sin(angle) * d);
                BlockState state = this.level.getBlockState(top);
                found.append(state.getBlock()).append(' ');
                if (this.isOneOf(state, Blocks.MAGMA_BLOCK, Blocks.LAVA, Blocks.BLACKSTONE) || HalleyContent.isMoonCheese(state)) {
                    molten++;
                }
            }
            this.record("moon_seam_molten", molten >= 6, molten + "/8 (" + found + ")");
        } catch (Throwable ex) {
            this.record("moon_seam_molten", false, "threw " + ex);
        }

        try {
            BlockState coreState = this.level.getBlockState(plan.core);
            boolean coreOk = coreState.is(HalleyContent.MOLTEN_MOON_CHEESE.get());
            this.record("moon_core_is_molten_moon_cheese", coreOk, "block at " + plan.core + " = " + coreState.getBlock());
        } catch (Throwable ex) {
            this.record("moon_core_is_molten_moon_cheese", false, "threw " + ex);
        }

        try {
            double r = plan.params.moonRadius();
            // Two sample points inside the moon's sphere, above the ground, offset horizontally from its centre.
            BlockPos sample1 = BlockPos.containing(plan.restCentre.x + r * 0.3, plan.restCentre.y, plan.restCentre.z);
            BlockPos sample2 = BlockPos.containing(plan.restCentre.x - r * 0.3, plan.restCentre.y + r * 0.2, plan.restCentre.z);
            BlockState state1 = this.level.getBlockState(sample1);
            BlockState state2 = this.level.getBlockState(sample2);
            boolean ok = HalleyContent.isMoonCheese(state1) && HalleyContent.isMoonCheese(state2);
            this.record("moon_cheese_sphere_painted", ok,
                "block at " + sample1 + " = " + state1.getBlock() + ", at " + sample2 + " = " + state2.getBlock());
        } catch (Throwable ex) {
            this.record("moon_cheese_sphere_painted", false, "threw " + ex);
        }

        this.checkMob("moon_inside_mob_erased", this.moonInsideMob, true);
        this.checkMob("moon_beside_mob_hurt", this.moonBesideMob, false);

        boolean notRunning = !StarBridge.moonAlreadyFalling(this.caster);
        this.record("moon_spell_completes_and_stops", notRunning, notRunning ? "" : "SpellEngine still reports it running");

        this.releaseLandRoundMoonMark();
    }

    private BlockPos sampleTopBlockAt(double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int y = this.groundY + 48; y > this.level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (!this.level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return new BlockPos(bx, this.groundY - 1, bz);
    }

    /** Feeds the FakePlayer caster both moon-cheese items for real (through {@code ItemStack.finishUsingItem}, the
     *  same call the normal eat-animation path ends in) and checks what each one did. */
    private void moonEat() {
        try {
            MobEffectInstance before = this.caster.getEffect(MobEffects.DAMAGE_BOOST);
            this.record("molten_no_strength_before_eating", before == null, "found " + before);
        } catch (Throwable ex) {
            this.record("molten_no_strength_before_eating", false, "threw " + ex);
        }

        try {
            // starving first: it should fill the hunger bar and its saturation right to the top; and slowed: like milk,
            // it clears that (but keeps its own effects, given after)
            this.caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
            this.caster.getFoodData().setFoodLevel(3);
            this.caster.getFoodData().setSaturation(0.0F);
            ItemStack stack = new ItemStack(HalleyContent.MOLTEN_MOON_CHEESE_ITEM.get());
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ItemStack after = stack.finishUsingItem(this.level, this.caster);
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, after);

            int food = this.caster.getFoodData().getFoodLevel();
            float saturation = this.caster.getFoodData().getSaturationLevel();
            this.record("molten_fills_hunger_and_saturation", food == 20 && saturation >= 20.0F,
                "food=" + food + " saturation=" + saturation + " (expected 20 / 20)");
            this.record("molten_clears_effects_like_milk", !this.caster.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    && this.caster.hasEffect(HalleyContent.MOLTEN_MIGHT.get()),
                "effects after eating: " + this.caster.getActiveEffects());

            double maxHealth = this.caster.getAttributeValue(Attributes.MAX_HEALTH);
            double expectedMaxHealth = MoonConfig.tuning().moltenHearts() * 2.0;
            this.record("molten_max_health_raised", maxHealth == expectedMaxHealth,
                "maxHealth=" + maxHealth + " expected=" + expectedMaxHealth);

            MobEffectInstance strength = this.caster.getEffect(MobEffects.DAMAGE_BOOST);
            int expectedAmplifier = MoonConfig.tuning().moltenStrength() - 1;
            this.record("molten_strength_amplifier", strength != null && strength.getAmplifier() == expectedAmplifier,
                "strength=" + strength + " expectedAmplifier=" + expectedAmplifier);

            int expectedDuration = MoonConfig.tuning().moltenMinutes() * 60 * 20;
            boolean durationOk = strength != null && strength.getDuration() > expectedDuration - 20 && strength.getDuration() <= expectedDuration;
            this.record("molten_strength_duration", durationOk,
                "duration=" + (strength == null ? "null" : strength.getDuration()) + " expected~=" + expectedDuration);

            this.record("molten_healed_to_new_max", this.caster.getHealth() == (float) maxHealth,
                "health=" + this.caster.getHealth() + " maxHealth=" + maxHealth);
        } catch (Throwable ex) {
            this.record("molten_fills_hunger_and_saturation", false, "threw " + ex);
            this.record("molten_clears_effects_like_milk", false, "threw " + ex);
            this.record("molten_max_health_raised", false, "threw " + ex);
            this.record("molten_strength_amplifier", false, "threw " + ex);
            this.record("molten_strength_duration", false, "threw " + ex);
        }

        try {
            // Leave room under the cap (20) so the "+6" is actually visible instead of being clamped away.
            this.caster.getFoodData().setFoodLevel(10);
            int before = this.caster.getFoodData().getFoodLevel();
            ItemStack stack = new ItemStack(HalleyContent.MOON_CHEESE_ITEM.get());
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ItemStack after = stack.finishUsingItem(this.level, this.caster);
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, after);
            int afterLevel = this.caster.getFoodData().getFoodLevel();
            this.record("moon_cheese_food_level_plus_6", afterLevel - before == 6,
                "before=" + before + " after=" + afterLevel);
            this.record("moon_cheese_clears_effects_like_milk", this.caster.getActiveEffects().isEmpty(),
                "effects after eating: " + this.caster.getActiveEffects());
        } catch (Throwable ex) {
            this.record("moon_cheese_food_level_plus_6", false, "threw " + ex);
            this.record("moon_cheese_clears_effects_like_milk", false, "threw " + ex);
        }

        try {
            // the holey kind is the same cheese
            this.caster.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
            ItemStack stack = new ItemStack(HalleyContent.HOLEY_MOON_CHEESE_ITEM.get());
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ItemStack after = stack.finishUsingItem(this.level, this.caster);
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, after);
            this.record("holey_moon_cheese_clears_effects_like_milk", this.caster.getActiveEffects().isEmpty(),
                "effects after eating: " + this.caster.getActiveEffects());
        } catch (Throwable ex) {
            this.record("holey_moon_cheese_clears_effects_like_milk", false, "threw " + ex);
        }
    }

    // ---- Small world-reading helpers. -------------------------------------------------------------------------

    private BlockPos sampleTopBlock(HalleyPlan plan, double along, double across) {
        Vec3 p = plan.target.add(plan.dir.scale(along)).add(plan.side.scale(across));
        int x = Mth.floor(p.x);
        int z = Mth.floor(p.z);
        for (int y = this.groundY + 10; y > this.level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!this.level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return new BlockPos(x, this.groundY - 1, z);
    }

    private boolean isOneOf(BlockState state, Block... blocks) {
        for (Block b : blocks) {
            if (state.is(b)) {
                return true;
            }
        }
        return false;
    }

    private boolean scanForBlock(Vec3 center, int radius, Block block) {
        int cx = Mth.floor(center.x);
        int cz = Mth.floor(center.z);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = this.level.getMinBuildHeight(); y <= this.groundY + 10; y++) {
                    if (this.level.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean scanRingForBlocks(HalleyPlan plan, double distance, Block... blocks) {
        Vec3 t = plan.target;
        for (int i = 0; i < 72; i++) {
            double angle = i * (Math.PI * 2.0 / 72.0);
            int x = Mth.floor(t.x + Math.cos(angle) * distance);
            int z = Mth.floor(t.z + Math.sin(angle) * distance);
            int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockState state = this.level.getBlockState(new BlockPos(x, y, z));
            for (Block b : blocks) {
                if (state.is(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Bookkeeping. -------------------------------------------------------------------------------------------

    private void record(String name, boolean pass, String detail) {
        this.totalCount++;
        if (pass) {
            this.passCount++;
            LOG.info("[SS05TEST] PASS {}", name);
        } else {
            LOG.error("[SS05TEST] FAIL {}: {}", name, detail);
        }
    }

    private void finish() {
        LOG.info("[SS05TEST] SUMMARY {}/{} passed", this.passCount, this.totalCount);
        this.server.halt(false);
    }

    /**
     * The cast packet's path (the remote sends its sliders with it; The Shooting Star clamps them to the skill's
     * range and casts): the untuned defaults. The Shooting Star 1.5.0's cast request is obfuscated and unmapped
     * (player, the skill's index, power, damage, speed, width, play the film for everyone) - same name as
     * {@code compat/tss/mixin/CastingMixin} and the client-test harness use.
     */
    private void request(int skillIndex) {
        Skill skill = SkillSet.byIndex(TssVersion.MOD_ID, skillIndex);
        Tuning tuning = Tuning.initial(skill).clampFor(skill);
        Casting.liiillli1l(this.caster, skillIndex, tuning.power(), tuning.damage(), tuning.speed(), tuning.width(), false);
    }
}
