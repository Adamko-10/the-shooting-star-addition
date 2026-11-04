package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1211.bulwark.Bulwark;
import cyou.rimuru.rimulib.mc1211.erase.ErasePolicy;
import cyou.rimuru.rimulib.mc1211.erase.Erasure;
import cyou.rimuru.rimulib.mc1211.net.RimuNet;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import cyou.rimuru.rimulib.mc1211.skill.Tuning;
import cyou.rimuru.rimulib.mc1211.terrain.Carving;
import cyou.rimuru.rimulib.mc1211.terrain.SectionSink;
import cyou.rimuru.shootingstardemo.mc1211.magic.Casting;
import cyou.rimuru.shootingstardemo.mc1211.l111i1lil1;
import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.compat.StarLink;
import dev.ss05.halley.compat.TssVersion;
import dev.ss05.halley.compat.mixin.ChunkAccessMixin;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import dev.ss05.halley.world.Excavation;
import dev.ss05.halley.world.HalleyCasting;
import dev.ss05.halley.world.HalleyInfo;
import dev.ss05.halley.world.MoonCasting;
import dev.ss05.halley.world.MoonInfo;
import dev.ss05.halley.world.Protected;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The one place the addon reaches into The Shooting Star (server and common side; the client side is
 * {@link StarClientBridge}). The rest of the addon calls it through {@link dev.ss05.halley.compat.StarBridge}.
 *
 * <p>Everything in {@code compat/tss/} is built once per supported version of The Shooting Star: the release jar
 * holds one copy of this package for each (as {@code compat/v1_4_1/} and so on, mapped to that version's names), and
 * StarBridge picks the copy for the version that is running. See gradle/tss.gradle.
 *
 * <h2>When The Shooting Star updates</h2>
 * Put the new jar in libs/ next to the old ones and rebuild. If it no longer compiles, or the game logs "couldn't
 * attach", the fix is in this package:
 * <ul>
 *   <li>Renamed packages or classes - fix the imports at the top of the files in {@code compat/tss/}.</li>
 *   <li>The remote's skill list ({@code SkillSet}) changed shape - adjust {@link #install()}; it finds the fields it
 *       needs by their types.</li>
 *   <li>A helper moved (spells, carving, erasure, the evac lift) - fix the one-line wrapper for it at the bottom.</li>
 * </ul>
 * Nothing outside {@code compat/tss/} imports The Shooting Star, so nothing else should need to change.
 */
public final class TssLink implements StarLink {
    /** Halley as The Shooting Star sees it. */
    public static final HalleySkill SKILL = new HalleySkill();
    /** Luna as The Shooting Star sees it. */
    public static final MoonSkill MOON = new MoonSkill();

    /**
     * Adds Halley and Luna to the Stellar Remote: after the built-in skills, each with its own index, key binding,
     * menu row, tooltip line and cooldown, exactly as if they had been among them. Safe to call more than once.
     *
     * <p>Since The Shooting Star 1.5.0 the remote's skill list is RimuLib's {@code SkillSet} (not obfuscated). It is
     * still built once with no "add a skill" hook, so Halley and Luna are written into its tables the way
     * {@code SkillSet.register} puts a set's skills there; RimuLib then shows them as an addon's skills.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void install() throws ReflectiveOperationException {
        if (SkillSet.of(SKILL) != null) {
            return;
        }
        // the protected blocks are "cores" every carving that keeps cores leaves where they are: ours, and The Shooting
        // Star's that do (one of its skills, in 1.5.1)
        Carving.keepCores(Protected::is);
        SkillSet remote = remote();
        Map<String, List<Skill>> byNamespace = (Map<String, List<Skill>>) field("BY_NAMESPACE").get(null);
        Map<Skill, SkillSet> owner = (Map<Skill, SkillSet>) field("OWNER").get(null);
        Map<Skill, Integer> index = (Map<Skill, Integer>) field("INDEX").get(null);
        Field skillsField = field("skills");
        Field casterField = field("caster");
        SkillSet.Caster<Skill> builtIn = (SkillSet.Caster<Skill>) casterField.get(remote);
        List<Skill> all = byNamespace.get(remote.namespace());
        List<Skill> before = remote.skills;
        if (builtIn == null || all == null || before == null || before.isEmpty()) {
            throw new IllegalStateException("the Stellar Remote has no skills to join");
        }

        List<Skill> after = new ArrayList<>(before);
        after.add(SKILL);
        after.add(MOON);
        // The remote hands every cast a Tuning: the sliders under the skill in its menu (power, damage, speed), clamped
        // to the skill's range. Halley and Luna get theirs as the addon's own CastTuning; the built-in skills unchanged.
        SkillSet.Caster<Skill> caster = (player, skill, tuning) -> skill == SKILL ? HalleyCasting.cast(player, tune(tuning))
            : skill == MOON ? MoonCasting.cast(player, tune(tuning)) : builtIn.cast(player, skill, tuning);

        for (Skill added : List.<Skill>of(SKILL, MOON)) {
            index.put(added, all.size());
            all.add(added);
            owner.put(added, remote);
        }
        try {
            skillsField.set(remote, List.copyOf(after));
            casterField.set(remote, caster);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            for (Skill added : List.<Skill>of(SKILL, MOON)) {
                all.remove(added);
                owner.remove(added);
                index.remove(added);
            }
            skillsField.set(remote, before);
            casterField.set(remote, builtIn);
            throw failed;
        }
        // the strikes' effect packet: declared now, before RimuLib registers its packets with the loader
        RimuNet.s2c(StrikeFx.PACKET);

        HalleyAddon.LOG.info("{} (Halley) joined the Stellar Remote as skill #{} (key slot {}).", HalleyInfo.title(), after.indexOf(SKILL) + 1, SkillSet.indexOf(SKILL));
        HalleyAddon.LOG.info("{} (Luna) joined the Stellar Remote as skill #{} (key slot {}).", MoonInfo.title(), after.indexOf(MOON) + 1, SkillSet.indexOf(MOON));
    }

    /** The Stellar Remote's skill set: The Shooting Star's set that holds its own skills (an enum of them). */
    private static SkillSet remote() {
        SkillSet found = null;
        for (SkillSet set : SkillSet.all(TssVersion.MOD_ID)) {
            if (!set.hidden && set.skills.stream().anyMatch(skill -> skill instanceof Enum<?>)
                && (found == null || set.skills.size() > found.skills.size())) {
                found = set;
            }
        }
        if (found == null) {
            throw new IllegalStateException("The Shooting Star registered no Stellar Remote skills");
        }
        return found;
    }

    /** One of SkillSet's fields, by name (RimuLib isn't obfuscated). */
    private static Field field(String name) throws NoSuchFieldException {
        Field field = SkillSet.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** The remote's sliders for one cast, as the addon's own type (the defaults if there are none). */
    static CastTuning tune(Tuning tuning) {
        return tuning == null ? CastTuning.DEFAULT : new CastTuning(tuning.power(), tuning.damage(), tuning.speed());
    }

    /** And back: what The Shooting Star's spell engine takes, so it runs the spell (and its film) at that speed. */
    static Tuning tuning(CastTuning tune) {
        return new Tuning(tune.power(), tune.damage(), tune.speed());
    }

    @Override
    public void tick(MinecraftServer server) {
        Spell.tickAll();
    }

    @Override
    public void clear() {
        Spell.clear();
    }

    /**
     * Clears one player's cooldowns on the remote (the no-cooldown option): The Shooting Star keeps, per player, the
     * game time each skill is ready again, in a static map of Casting's from UUID to long[]; without an entry there,
     * every skill is ready. Its names (and, since 1.5.0, its generic types) are obfuscated, so the map is the static
     * one whose entry for this player is a long[].
     */
    public static void clearCooldowns(ServerPlayer player) {
        try {
            for (Field field : Casting.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Map<?, ?> map = (Map<?, ?>) field.get(null);
                    if (map != null && map.get(player.getUUID()) instanceof long[]) {
                        map.remove(player.getUUID());
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failed) {
            if (!warnedCooldowns) {
                warnedCooldowns = true;
                HalleyAddon.LOG.warn("No cooldown: couldn't reach The Shooting Star's cooldowns ({}), so they stay on", failed.toString());
            }
        }
    }

    private static boolean warnedCooldowns;

    @Override
    public void installClient() {
        StarClientBridge.install();
    }

    @Override
    public int skillIndex() {
        return SkillSet.indexOf(SKILL);
    }

    @Override
    public int moonIndex() {
        return SkillSet.indexOf(MOON);
    }

    @Override
    public Object skill(boolean moon) {
        return moon ? MOON : SKILL;
    }

    // ---- Thin wrappers over The Shooting Star's helpers. One line each, so an update only touches these. ----------

    @Override
    public void startHalley(ServerPlayer caster, Vec3 target, long seed, HalleyParams params, CastTuning tuning) {
        Spell.start(new HalleySpell(caster, target, seed, params, tuning));
    }

    @Override
    public void startMoon(ServerPlayer caster, Vec3 target, long seed, MoonParams params, CastTuning tuning) {
        Spell.start(new MoonSpell(caster, target, seed, params, tuning));
    }

    @Override
    public boolean alreadyFlying(boolean moon, ServerPlayer player) {
        return Spell.running(moon ? MOON : SKILL, player.getUUID());
    }

    @Override
    public Vec3 aimGround(ServerPlayer player, double reach, double fallback) {
        // SpellEngine.aimGround, unmapped since 1.5.1
        return l111i1lil1.liiillli1l(player, reach, fallback);
    }

    @Override
    public void deny(ServerPlayer player, String message) {
        Casting.deny(player, message);
    }

    @Override
    public void ward(ServerPlayer caster) {
        // as The Shooting Star's own spells ward their caster (1.4.x SpellUtil.ward)
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, true, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, true));
    }

    @Override
    public boolean affects(Entity entity, ServerPlayer caster) {
        // as The Shooting Star's own spells choose (1.4.x SpellUtil.affects)
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity instanceof ArmorStand || entity.isSpectator()) {
            return false;
        }
        if (caster == null) {
            return true;
        }
        if (entity == caster || entity.isPassengerOfSameVehicle(caster)) {
            return false;
        }
        return !(entity instanceof OwnableEntity pet && caster.getUUID().equals(pet.getOwnerUUID()));
    }

    @Override
    public void erase(ServerLevel level, LivingEntity victim, ServerPlayer attacker) {
        Erasure.strike(policy(), level, victim, attacker);
    }

    @Override
    public void hit(ServerLevel level, LivingEntity victim, ServerPlayer attacker, float damage) {
        Erasure.hit(policy(), level, victim, attacker, damage);
    }

    /** How The Shooting Star's skills erase: its own two damage types, and its "force kill" settings. */
    private static ErasePolicy policy() {
        return ErasePolicy.unmake(damage("shooting_star"), damage("shooting_star_hit"))
            .forceKill(Tuning.forceKill(TssVersion.MOD_ID), Tuning.maxDamage(TssVersion.MOD_ID));
    }

    private static ResourceKey<DamageType> damage(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(TssVersion.MOD_ID, name));
    }

    @Override
    public boolean liftClear(ServerPlayer caster, Vec3 target, double zone, double refuge, int ticks, boolean lift) {
        // The Shooting Star 1.5.0 has no lift to call any more: take the caster out of the zone, falling slowly. Never
        // onto land that isn't loaded: its height reads as the bottom of the world (bedrock), and the teleport waits for
        // it to load, behind the strike's own chunks (a 40 s freeze at 10x, 2026-11-03)
        ServerLevel level = caster.serverLevel();
        Vec3 to = null;
        if (!lift) {
            Vec3 away = caster.position().subtract(target).multiply(1.0, 0.0, 1.0);
            Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            BlockPos edge = BlockPos.containing(target.add(dir.scale(refuge)));
            if (level.hasChunkAt(edge)) {
                to = new Vec3(edge.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, edge.getX(), edge.getZ()) + 1.0,
                    edge.getZ() + 0.5);
            }
        }
        if (to == null) {
            // straight up, above the strike: the caster's own chunk is loaded
            double y = Math.min(Math.max(target.y + zone, caster.getY() + 24.0), level.getMaxBuildHeight() + 16.0);
            to = new Vec3(caster.getX(), y, caster.getZ());
        }
        caster.teleportTo(to.x, to.y, to.z);
        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, true, false, true));
        return true;
    }

    @Override
    public void paint(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        // paint looks at each block's block entity first, which loads saved ones the crater left behind (MoonLand's rim
        // and cracks: 244 warnings at 10x, 2026-11-03); and it would write over protected blocks
        LongSet chunks = new LongOpenHashSet();
        LongSet kept = new LongOpenHashSet();
        for (BlockPos at : blocks.keySet()) {
            if (chunks.add(ChunkPos.asLong(at))) {
                dropStaleBlockEntities(level, at.getX() >> 4, at.getZ() >> 4);
                LevelChunk chunk = level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4);
                var found = chunk == null ? null : Protected.scan(chunk);
                if (found != null) {
                    kept.addAll(found.keySet());
                }
            }
        }
        if (!kept.isEmpty()) {
            blocks.keySet().removeIf(at -> kept.contains(at.asLong()));
        }
        Carving.paint(level, blocks);
    }

    /**
     * Drops a chunk's saved block entities not loaded yet whose block is gone (cut by a carving, covered by the moon):
     * loading one fails with a warning or a stack trace each ("Tried to load a DUMMY block entity").
     */
    private static void dropStaleBlockEntities(ServerLevel level, int x, int z) {
        if (level.getChunkSource().getChunkNow(x, z) instanceof ChunkAccessMixin chunk) {
            LevelChunk live = (LevelChunk) chunk;
            chunk.ss05halley$pendingBlockEntities().keySet().removeIf(at -> !live.getBlockState(at).hasBlockEntity());
        }
    }

    @Override
    public Excavation.Sink sink(ServerLevel level, ChunkPos pos, int fromY, int toY, int clearFrom) {
        Long2ObjectMap<int[]> range = new Long2ObjectOpenHashMap<>();
        range.put(pos.toLong(), new int[]{fromY, toY});
        // the cell is the chunk itself, so whole sections from clearFrom up (to the top) start empty
        SectionSink sink = SectionSink.snapshot(level, range, pos.getMinBlockX(), pos.getMinBlockZ(), 16,
            clearFrom, Integer.MAX_VALUE);
        return new Excavation.Sink() {
            @Override
            public void prepare() {
                sink.prepare();
            }

            @Override
            public void put(int x, int y, int z, BlockState state) {
                sink.put(x, y, z, state);
            }

            @Override
            public void clear(int x, int z, int y0, int y1) {
                sink.clear(x, z, y0, y1);
            }

            @Override
            public boolean stale() {
                return sink.stale(level);
            }

            @Override
            public void apply() {
                sink.apply(level);
                dropStaleBlockEntities(level, pos.x, pos.z);
            }

            @Override
            public void release() {
                sink.release(level);
            }
        };
    }

    @Override
    public void relight(ServerLevel level, List<ChunkPos> chunks) {
        List<LevelChunk> loaded = new ArrayList<>(chunks.size());
        for (ChunkPos pos : chunks) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                loaded.add(chunk);
            }
        }
        Carving.relightAll(level, loaded);
    }

    @Override
    public boolean spares(ServerLevel level, int x, int z) {
        return Bulwark.spares(level, x, z);
    }

    @Override
    public Excavation excavate(ServerLevel level, Vec3 centre, double radius, Excavation.Floor floor,
                               Excavation.Surface surface, BlockPos spared, String name, int chunksPerTick,
                               Excavation.Loader loader) {
        // keepsCores: the protected blocks (a "core", see install) stay where they are
        Carving base = Carving.flatten(level, centre, radius, floor::floor, surface::at, spared)
            .perTick(chunksPerTick)
            .keepsCores()
            .named(name);
        // with a loader it streams the chunks it is waiting for in (as the built-in skills' carvings do), else it cuts
        // the ones that are loaded and waits for the rest to be
        Carving carving = loader == null ? base.lazy() : base.streaming(new Carving.Tickets() {
            @Override
            public void hold(ChunkPos pos) {
                loader.hold(pos);
            }

            @Override
            public void release(ChunkPos pos) {
                loader.release(pos);
            }
        });
        return new Excavation() {
            @Override
            public void tick(double front) {
                carving.tick(front);
            }

            @Override
            public boolean done() {
                return carving.done();
            }

            @Override
            public void finish() {
                carving.finish();
            }

            @Override
            public void linger() {
                Carving.linger(carving);
            }
        };
    }
}
