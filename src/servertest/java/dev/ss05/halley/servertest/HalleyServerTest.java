package dev.ss05.halley.servertest;

import com.mojang.logging.LogUtils;
import cyou.rimuru.shootingstardemo.mc1201.magic.Casting;
import cyou.rimuru.shootingstardemo.mc1201.magic.Skill;
import cyou.rimuru.shootingstardemo.mc1201.magic.SkillSet;
import cyou.rimuru.shootingstardemo.mc1201.magic.Tuning;
import cyou.rimuru.shootingstardemo.mc1201.registry.ModItems;
import cyou.rimuru.shootingstardemo.mc1201.registry.ModSkills;
import cyou.rimuru.shootingstardemo.mc1201.spell.ActiveSpell;
import cyou.rimuru.shootingstardemo.mc1201.spell.SpellEngine;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
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
    /** SS-06's strike is much shorter (MoonPlan.DURATION = 600 ticks); half a minute of margin is plenty. */
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
        this.caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STELLAR_REMOTE));
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

        Skill skill = StarBridge.SKILL;
        SkillSet owner = SkillSet.of(skill);
        this.record("skill_owned_by_stellar_remote", owner == ModSkills.STELLAR_REMOTE, "owner=" + owner);

        int index = SkillSet.indexOf(skill);
        this.record("skill_index_is_3", index == 3, "index=" + index);
        this.record("skill_id_is_halley", "halley".equals(skill.id()), "id=" + skill.id());
        this.record("skill_default_key_is_O", skill.defaultKey() == HalleyInfo.DEFAULT_KEY, "key=" + skill.defaultKey());
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
    }

    /** Same shape as {@link #checkSkillRegistration()}, for SS-06 Luna (skill index 4, key J). */
    private void checkMoonSkillRegistration() {
        if (!StarBridge.installed()) {
            return;
        }

        Skill skill = StarBridge.MOON;
        SkillSet owner = SkillSet.of(skill);
        this.record("moon_skill_owned_by_stellar_remote", owner == ModSkills.STELLAR_REMOTE, "owner=" + owner);

        int index = SkillSet.indexOf(skill);
        this.record("moon_skill_index_is_4", index == 4, "index=" + index);
        this.record("moon_skill_id_is_luna", "luna".equals(skill.id()), "id=" + skill.id());
        this.record("moon_skill_default_key_is_J", skill.defaultKey() == MoonInfo.DEFAULT_KEY, "key=" + skill.defaultKey());
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
            // The same call The Shooting Star's own cast-packet handler makes (CastSkillPayload -> Casting.request).
            this.request(this.skillIndexAtCast);
            this.record("cast_request_no_exception", true, "");
        } catch (Throwable ex) {
            this.record("cast_request_no_exception", false, "threw " + ex);
        }
    }

    private void postCast() {
        boolean running = SpellEngine.running(StarBridge.SKILL, this.caster.getUUID());
        this.record("cast_starts_spell", running, running ? "" : "SpellEngine.running() is false right after the cast");

        ActiveSpell found = null;
        try {
            for (Object o : this.activeSpells()) {
                ActiveSpell as = (ActiveSpell) o;
                if (as.skill == StarBridge.SKILL && as.casterId.equals(this.caster.getUUID())) {
                    found = as;
                    break;
                }
            }
            this.record("read_active_spell_reflection", found != null,
                found != null ? "" : "no matching ActiveSpell in SpellEngine.ACTIVE");
        } catch (Throwable ex) {
            this.record("read_active_spell_reflection", false, "threw " + ex);
        }

        if (found == null) {
            return;
        }

        try {
            HalleyConfig.Tuning tuning = HalleyConfig.tuning();
            Vec3 origin = found.origin;
            Vec3 target = found.target;
            Vec3 flat = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
            Vec3 dir = flat.lengthSqr() < 1.0 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
            double tdX = target.x + dir.x * tuning.trenchLength();
            double tdZ = target.z + dir.z * tuning.trenchLength();
            int ground = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(tdX), Mth.floor(tdZ));
            HalleyParams params = HalleyParams.of(tuning, ground);
            this.replicaPlan = new HalleyPlan(origin, target, this.caster.getYRot(), found.seed, params);
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
            int before = this.countActive();
            this.request(this.skillIndexAtCast);
            int after = this.countActive();
            int remaining = Casting.remaining(this.caster, StarBridge.SKILL);
            int expected = HalleyConfig.tuning().cooldownTicks();
            boolean pass = after == before && remaining > 0 && remaining <= expected && remaining >= expected - 40;
            this.record("cooldown_refuses_second_cast", pass,
                "before=" + before + " after=" + after + " remaining=" + remaining + " expectedCooldown=" + expected);
        } catch (Throwable ex) {
            this.record("cooldown_refuses_second_cast", false, "threw " + ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> activeSpells() throws ReflectiveOperationException {
        Field f = SpellEngine.class.getDeclaredField("ACTIVE");
        f.setAccessible(true);
        return new ArrayList<>((List<Object>) f.get(null));
    }

    private int countActive() throws ReflectiveOperationException {
        int n = 0;
        for (Object o : this.activeSpells()) {
            ActiveSpell as = (ActiveSpell) o;
            if (as.skill == StarBridge.SKILL && as.casterId.equals(this.caster.getUUID())) {
                n++;
            }
        }
        return n;
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
        boolean running = SpellEngine.running(StarBridge.SKILL, this.caster.getUUID());
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

        boolean notRunning = !SpellEngine.running(StarBridge.SKILL, this.caster.getUUID());
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

    /** Attempts the cast by day; MoonConfig's {@code night_only} (default true) should refuse it. */
    private void castMoonByDay() {
        this.moonSkillIndexAtCast = StarBridge.moonIndex();
        this.record("moon_skill_index_available", this.moonSkillIndexAtCast >= 0, "index=" + this.moonSkillIndexAtCast);
        boolean isDay = this.level.isDay();
        this.record("arena_is_day_for_refusal_check", isDay, "isDay=" + isDay + " isNight=" + this.level.isNight());
        try {
            this.request(this.moonSkillIndexAtCast);
            boolean running = SpellEngine.running(StarBridge.MOON, this.caster.getUUID());
            this.record("moon_day_cast_refused", !running, running ? "a moonfall started during the day" : "");
        } catch (Throwable ex) {
            this.record("moon_day_cast_refused", false, "threw " + ex);
        }
    }

    /** Attempts the real cast by night, once the day-refusal check above is done. */
    private void castMoonByNight() {
        boolean isNight = this.level.isNight();
        this.record("arena_is_night_for_real_cast", isNight, "isNight=" + isNight);
        this.moonCastTick = this.tick;
        try {
            this.request(this.moonSkillIndexAtCast);
            this.record("moon_cast_request_no_exception", true, "");
        } catch (Throwable ex) {
            this.record("moon_cast_request_no_exception", false, "threw " + ex);
        }
    }

    private void moonPostCast() {
        boolean running = SpellEngine.running(StarBridge.MOON, this.caster.getUUID());
        this.record("moon_cast_starts_spell", running, running ? "" : "SpellEngine.running() is false right after the cast");

        ActiveSpell found = null;
        try {
            for (Object o : this.activeSpells()) {
                ActiveSpell as = (ActiveSpell) o;
                if (as.skill == StarBridge.MOON && as.casterId.equals(this.caster.getUUID())) {
                    found = as;
                    break;
                }
            }
            this.record("moon_read_active_spell_reflection", found != null,
                found != null ? "" : "no matching ActiveSpell in SpellEngine.ACTIVE");
        } catch (Throwable ex) {
            this.record("moon_read_active_spell_reflection", false, "threw " + ex);
        }

        if (found == null) {
            return;
        }

        try {
            // Rebuilt the same way MoonCasting.cast() builds it: the sky direction read at cast time, from the
            // target/seed the running spell actually used (public fields on ActiveSpell, like HalleyPlan's replica).
            Vec3 sky = MoonPlan.skyDirection(this.level.getTimeOfDay(1.0F));
            MoonParams params = MoonParams.of(MoonConfig.tuning(), Mth.floor(found.target.y), sky);
            this.moonReplicaPlan = new MoonPlan(found.origin, found.target, found.seed, params);
            this.record("moon_replica_plan_built", true, "target=" + found.target + " core=" + this.moonReplicaPlan.core);
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
        boolean running = SpellEngine.running(StarBridge.MOON, this.caster.getUUID());
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
            return;
        }
        MoonPlan plan = this.moonReplicaPlan;

        try {
            // Within the crater radius but outside the moon's own sphere at ground level, so this samples the
            // crater's cut surface rather than a block the moon build painted over it.
            BlockPos craterSample = this.sampleTopBlockAt(plan.target.x + plan.params.craterRadius() * 0.7, plan.target.z);
            BlockState craterState = this.level.getBlockState(craterSample);
            boolean craterOk = this.isOneOf(craterState, Blocks.MAGMA_BLOCK, Blocks.BASALT, Blocks.BLACKSTONE,
                Blocks.COARSE_DIRT, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL);
            this.record("moon_crater_cut", craterOk, "block at " + craterSample + " = " + craterState.getBlock());
        } catch (Throwable ex) {
            this.record("moon_crater_cut", false, "threw " + ex);
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
            boolean ok = state1.is(HalleyContent.MOON_CHEESE.get()) && state2.is(HalleyContent.MOON_CHEESE.get());
            this.record("moon_cheese_sphere_painted", ok,
                "block at " + sample1 + " = " + state1.getBlock() + ", at " + sample2 + " = " + state2.getBlock());
        } catch (Throwable ex) {
            this.record("moon_cheese_sphere_painted", false, "threw " + ex);
        }

        this.checkMob("moon_inside_mob_erased", this.moonInsideMob, true);
        this.checkMob("moon_beside_mob_hurt", this.moonBesideMob, false);

        boolean notRunning = !SpellEngine.running(StarBridge.MOON, this.caster.getUUID());
        this.record("moon_spell_completes_and_stops", notRunning, notRunning ? "" : "SpellEngine still reports it running");
    }

    private BlockPos sampleTopBlockAt(double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int y = this.groundY + 10; y > this.level.getMinBuildHeight(); y--) {
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
            ItemStack stack = new ItemStack(HalleyContent.MOLTEN_MOON_CHEESE_ITEM.get());
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ItemStack after = stack.finishUsingItem(this.level, this.caster);
            this.caster.setItemInHand(InteractionHand.MAIN_HAND, after);

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
        } catch (Throwable ex) {
            this.record("moon_cheese_food_level_plus_6", false, "threw " + ex);
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

    /** The cast packet's path (The Shooting Star 1.3.2+ sends the remote's gauge with it): the untuned defaults. */
    private void request(int skillIndex) {
        Tuning tuning = Tuning.initial(SkillSet.byIndex(skillIndex));
        Casting.request(this.caster, skillIndex, tuning.power(), tuning.damage(), tuning.speed());
    }
}
