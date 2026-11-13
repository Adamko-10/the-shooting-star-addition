package dev.ss05.halley.world;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.ProximaConfig;
import dev.ss05.halley.ProximaParams;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SA-04 Proxima on the server: what the flare does to the world and to the living (docs/SS-07-PROXIMA.md).
 * <ul>
 *   <li>{@link ProximaPlan#FLASH}, the flash burn: everything living out past the scorch, under the open sky, catches
 *   fire and is hurt a little. Shelter is {@code shelter_blocks} (ProximaConfig) solid blocks overhead (glass doesn't count).</li>
 *   <li>{@link ProximaPlan#IMPACT}, ground zero: in the bowl things are erased (or take the damage slider's hit); in the
 *   charred ring they are hurt badly, set alight and thrown; in the scorch, hurt and pushed. Players are only nudged
 *   (creative, spectators and the caster never), mobs are thrown. Then the bowl is cut (a glassy floor of tinted glass,
 *   obsidian and magma) and the land round it scorched ({@link ProximaLand}).</li>
 * </ul>
 * Only loaded land is touched; the rest as it loads.
 */
public final class ProximaStrike {
    public static final ResourceKey<DamageType> FLARE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("proxima_flare"));
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("proxima_radiation"));
    /** Lands still waiting for chunks to load, after their strike. */
    private static final List<ProximaLand> LINGERING = new ArrayList<>();
    /** Worlds whose radiation zones failed to tick (logged once each). */
    private static final java.util.Set<ResourceKey<net.minecraft.world.level.Level>> ZONES_FAILED = new java.util.HashSet<>();

    private final ServerLevel level;
    private final ProximaPlan plan;
    private final CastTuning cast;
    private final ProximaConfig.Tuning config = ProximaConfig.tuning();
    private final BlockPos spared;
    @Nullable
    private Excavation bowl;
    @Nullable
    private ProximaLand land;
    /** How far out ground zero's shock wave has hit (it burns and throws as it runs out, see {@link ProximaPlan#waveRadius}). */
    private double waveHit = -1.0;
    private final java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
    /** The game time of the impact (Long.MAX_VALUE before it). */
    private long impactAt = Long.MAX_VALUE;

    public ProximaStrike(ServerLevel level, ProximaPlan plan, @Nullable ServerPlayer caster, CastTuning cast) {
        this.level = level;
        this.plan = plan;
        this.cast = cast;
        // nobody's footing to spare (a random flare): a point well outside the bowl
        this.spared = caster != null ? caster.blockPosition() : BlockPos.containing(plan.target.add(plan.params.scorchRadius() + 64.0, 0.0, 0.0));
    }

    public ProximaPlan plan() {
        return this.plan;
    }

    public void tick(int t, @Nullable ServerPlayer caster) {
        if (caster != null && t >= ProximaPlan.FLASH - 5 && t <= ProximaPlan.IMPACT + 60 && t % 30 == 0) {
            StarBridge.ward(caster);
        }
        ProximaParams params = this.plan.params;
        if (t == ProximaPlan.FLASH && params.burns()) {
            this.flash(caster);
            // a major random flare (no front coming): its light burns the plants away where it falls, all at once
            if (!params.cme() && this.config.carveTerrain() && this.config.flaresBurnPlants()) {
                this.land = new ProximaLand(this.level, this.plan, true);
            }
        }
        if (t == ProximaPlan.IMPACT && params.cme()) {
            this.impact(caster);
        }
        if (this.bowl != null && !this.bowl.done()) {
            this.bowl.tick();
        }
        double wave = this.plan.waveRadius(t);
        if (this.waveHit >= 0.0 && wave > this.waveHit) {
            this.wave(this.waveHit, wave, caster);
            this.waveHit = wave;
        }
        if (this.land != null) {
            this.land.tick(params.cme() ? wave : Double.MAX_VALUE);
        }
    }

    /** Whether the world work that can be done now is done (unloaded land waits, after the spell, for its chunks). */
    public boolean worldDone() {
        if (!this.plan.params.cme()) {
            return this.land == null || this.land.loadedDone();
        }
        return this.waveHit >= this.plan.waveReach() && (this.land == null || this.land.loadedDone());
    }

    /** The spell is over: what is left is done as its land loads. */
    public void end() {
        if (this.bowl != null && !this.bowl.done()) {
            this.bowl.linger();
        }
        if (this.land != null && !this.land.finished()) {
            LINGERING.add(this.land);
        }
    }

    /** From the server tick: scorches what loaded since its strike ended; once a second the radiation zones hurt. */
    public static void tickLingering(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                try {
                    Zones.of(level).tick(level);
                } catch (RuntimeException | LinkageError failed) {
                    // the zones are saved, so a failure here would come back on every start: log it once and go on
                    if (ZONES_FAILED.add(level.dimension())) {
                        dev.ss05.halley.HalleyAddon.LOG.error("Proxima's radiation zones in {} failed; they stay but don't hurt",
                            level.dimension().location(), failed);
                    }
                }
            }
        }
        LINGERING.removeIf(land -> {
            try {
                land.tick(Double.MAX_VALUE);
                return land.finished();
            } catch (RuntimeException | LinkageError failed) {
                dev.ss05.halley.HalleyAddon.LOG.error("A past flare's leftover land failed and was dropped", failed);
                return true;
            }
        });
    }

    public static void clearLingering() {
        LINGERING.clear();
    }

    /** For `/ssa zones list`: one line per radiation zone in this world (where, how wide, how long it has left). */
    public static List<String> zoneLines(ServerLevel level) {
        List<String> lines = new ArrayList<>();
        long now = level.getGameTime();
        for (Zones.Zone zone : Zones.of(level).zones) {
            String left = zone.until() == Long.MAX_VALUE ? "permanent" : Math.max(0L, (zone.until() - now) / 20L) + " s left";
            lines.add(String.format(java.util.Locale.ROOT, "%d %d %d, %d blocks wide, %s", (int) Math.floor(zone.centre().x),
                (int) Math.floor(zone.centre().y), (int) Math.floor(zone.centre().z), (int) Math.round(zone.radius() * 2.0), left));
        }
        return lines;
    }

    /** For `/ssa zones clear`: removes every radiation zone in this world; returns how many there were. */
    public static int clearZones(ServerLevel level) {
        Zones zones = Zones.of(level);
        int count = zones.zones.size();
        if (count > 0) {
            zones.zones.clear();
            zones.setDirty();
        }
        return count;
    }

    /**
     * A world's radiation zones (the bowls still hot), saved with it so they outlast a restart: what lingers in one is
     * hurt and weakened once a second, less toward its edge and as it fades out at {@code until} ({@link Long#MAX_VALUE}:
     * never, with {@code permanent_radiation}).
     */
    private static final class Zones extends SavedData {
        private static final String NAME = "shooting_star_addition_proxima_zones";

        private record Zone(Vec3 centre, double radius, long from, long until) {
        }

        private final List<Zone> zones = new ArrayList<>();

        static Zones of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Zones::load, Zones::new, NAME);
        }

        void add(Vec3 centre, double radius, long from, long until) {
            this.zones.add(new Zone(centre, radius, from, until));
            this.setDirty();
        }

        void tick(ServerLevel level) {
            if (this.zones.isEmpty()) {
                return;
            }
            long now = level.getGameTime();
            if (this.zones.removeIf(zone -> now > zone.until())) {
                this.setDirty();
            }
            double damage = ProximaConfig.tuning().radiationDamage();
            for (Zone zone : this.zones) {
                double left = 1.0 - (now - zone.from()) / (double) Math.max(1L, zone.until() - zone.from());
                Vec3 c = zone.centre();
                AABB box = new AABB(c.x - zone.radius(), c.y - 16.0, c.z - zone.radius(), c.x + zone.radius(), c.y + 24.0, c.z + zone.radius());
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    double d = Math.hypot(living.getX() - c.x, living.getZ() - c.z);
                    if (d > zone.radius() || living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                        continue;
                    }
                    float hurt = (float) (damage * (0.17 + 0.83 * left * (1.0 - d / zone.radius())));
                    if (hurt > 0.0F) {
                        living.hurt(new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RADIATION)), hurt);
                    }
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
            }
        }

        private static Zones load(CompoundTag tag) {
            Zones zones = new Zones();
            for (Tag entry : tag.getList("zones", Tag.TAG_COMPOUND)) {
                CompoundTag z = (CompoundTag) entry;
                zones.zones.add(new Zone(new Vec3(z.getDouble("x"), z.getDouble("y"), z.getDouble("z")), z.getDouble("radius"),
                    z.getLong("from"), z.getLong("until")));
            }
            return zones;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Zone zone : this.zones) {
                CompoundTag z = new CompoundTag();
                z.putDouble("x", zone.centre().x);
                z.putDouble("y", zone.centre().y);
                z.putDouble("z", zone.centre().z);
                z.putDouble("radius", zone.radius());
                z.putLong("from", zone.from());
                z.putLong("until", zone.until());
                list.add(z);
            }
            tag.put("zones", list);
            return tag;
        }
    }

    // ---- The flash. ------------------------------------------------------------------------------------------------

    private void flash(@Nullable ServerPlayer caster) {
        double reach = this.plan.params.scorchRadius() * 1.25;
        for (LivingEntity living : this.level.getEntitiesOfClass(LivingEntity.class, this.around(reach))) {
            double d = this.plan.horizontal(living.getX(), living.getZ());
            if (d > reach || immune(living, caster) || this.sheltered(living)) {
                continue;
            }
            double k = 1.0 - d / reach;
            living.setSecondsOnFire((int) (3.0 + 5.0 * k));
            living.hurt(this.flare(living == caster ? null : caster), this.cast.cap((float) (2.0 + 6.0 * k)));
        }
    }

    // ---- Ground zero. ----------------------------------------------------------------------------------------------

    /**
     * Items and experience that appeared since the impact (what a player killed in the bowl dropped): the wave runs over
     * the bowl again every tick, and would wipe them before anyone could pick them up (Fable's review, R2).
     */
    private boolean droppedSinceImpact(Entity entity) {
        return (entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb)
            && entity.tickCount <= this.level.getGameTime() - this.impactAt + 1;
    }

    private void impact(@Nullable ServerPlayer caster) {
        this.impactAt = this.level.getGameTime();
        double bowl = this.plan.params.bowlRadius();
        for (Entity entity : this.level.getEntities((Entity) null, this.around(bowl), Entity::isAlive)) {
            if (this.plan.horizontal(entity.getX(), entity.getZ()) < bowl) {
                this.struck.add(entity.getUUID());
                this.vaporise(entity, caster);
            }
        }
        this.waveHit = 0.0;
        long now = this.level.getGameTime();
        int radiation = this.plan.params.radiationTicks();
        if (radiation != 0) {
            Zones.of(this.level).add(this.plan.target, bowl * 1.3, now, radiation == ProximaParams.PERMANENT ? Long.MAX_VALUE : now + radiation);
        }
        if (!this.config.carveTerrain()) {
            return;
        }
        int minY = this.level.getMinBuildHeight();
        this.bowl = StarBridge.excavate(this.level, this.plan.target, bowl + 3.0, (x, z) -> this.bowlFloor(x, z, minY),
            (x, y, z, was) -> this.bowlSurface(x, y, z), this.spared, "SA-04 Proxima bowl", HalleyConfig.chunksPerTick(), null);
        this.land = new ProximaLand(this.level, this.plan, false);
    }

    /**
     * Ground zero's shock wave passing from {@code from} to {@code to} blocks out: what it reaches is hurt by tier (the
     * charred ring badly, set alight; the scorch less, staggered) and thrown (players only nudged); sheltered ones only
     * feel it.
     */
    private void wave(double from, double to, @Nullable ServerPlayer caster) {
        double bowl = this.plan.params.bowlRadius();
        double charred = this.plan.params.charRadius();
        double reach = this.plan.params.scorchRadius();
        for (Entity entity : this.level.getEntities((Entity) null, this.around(Math.min(to, reach)), Entity::isAlive)) {
            double d = this.plan.horizontal(entity.getX(), entity.getZ());
            if (d > to || d > reach || d <= from && d >= bowl || !this.struck.add(entity.getUUID())) {
                continue;
            }
            if (d < bowl) {
                this.vaporise(entity, caster);
                continue;
            }
            boolean sheltered = entity instanceof LivingEntity living && this.sheltered(living);
            double k = d < charred ? 1.0 - (d - bowl) / Math.max(1.0, charred - bowl) : 0.4 * (1.0 - (d - charred) / Math.max(1.0, reach - charred));
            this.knock(entity, caster, k, sheltered);
            if (entity instanceof LivingEntity living && !sheltered && !immune(living, caster)) {
                boolean heavy = d < charred;
                living.hurt(this.flare(living == caster ? null : caster), this.cast.cap((float) (heavy ? 12.0 + 40.0 * k : 3.0 + 12.0 * k)));
                if (heavy) {
                    living.setSecondsOnFire((int) (4.0 + 6.0 * k));
                } else {
                    // staggered
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            }
        }
    }

    /** In the bowl: erased (the damage slider below ∞: its hit instead); loose things burn up. */
    private void vaporise(Entity entity, @Nullable ServerPlayer caster) {
        if (entity instanceof LivingEntity living) {
            if (!immune(living, caster)) {
                StarBridge.hit(this.level, living, living == caster ? null : caster, this.cast.damage());
            }
        } else if (!(entity instanceof Player) && entity.getPassengers().isEmpty()
                && (caster == null || entity != caster.getVehicle()) && Protected.removable(entity)
                && !this.droppedSinceImpact(entity)) {
            entity.discard();
        }
    }

    /** Thrown away from the mark: mobs hard, players only nudged (never creative ones, spectators or the caster). */
    private void knock(Entity entity, @Nullable ServerPlayer caster, double k, boolean sheltered) {
        if (sheltered || entity == caster || entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        // the config scales both: players up to player_knockback (0.6 by default), mobs by mob_knockback
        double scale = entity instanceof Player ? this.config.playerKnockback() / 0.6 : this.config.mobKnockback();
        if (scale <= 0.0) {
            return;
        }
        Vec3 away = new Vec3(entity.getX() - this.plan.target.x, 0.0, entity.getZ() - this.plan.target.z);
        away = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        double push = scale * (entity instanceof Player ? Math.min(0.6, 0.2 + 0.6 * k) : 1.0 + 3.0 * k);
        double lift = scale * (entity instanceof Player ? 0.25 : 0.4 + 0.8 * k);
        entity.setDeltaMovement(entity.getDeltaMovement().add(away.scale(push)).add(0.0, lift, 0.0));
        entity.hurtMarked = true;
    }

    private static boolean immune(LivingEntity living, @Nullable ServerPlayer caster) {
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        return living != caster && caster != null && !StarBridge.affects(living, caster);
    }

    /** {@code shelter_blocks} solid blocks overhead (a glass roof is no bunker). */
    private boolean sheltered(LivingEntity living) {
        BlockPos head = BlockPos.containing(living.getEyePosition());
        int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, head.getX(), head.getZ());
        int shelter = this.config.shelterBlocks();
        int solid = 0;
        BlockPos.MutableBlockPos at = head.mutable();
        for (int y = head.getY() + 1; y < top && solid < shelter; y++) {
            at.setY(y);
            if (this.level.getBlockState(at).isSolidRender(this.level, at)) {
                solid++;
            }
        }
        return solid >= shelter;
    }

    private AABB around(double reach) {
        Vec3 t = this.plan.target;
        return new AABB(t.x - reach, t.y - 48.0, t.z - reach, t.x + reach, t.y + 160.0, t.z + reach);
    }

    private DamageSource flare(@Nullable Entity attacker) {
        return new DamageSource(this.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(FLARE), attacker, attacker);
    }

    // ---- The bowl. -------------------------------------------------------------------------------------------------

    /** The bowl's new floor in this column: the ground, less the bowl's depth there. */
    private int bowlFloor(int x, int z, int minY) {
        double depth = this.plan.bowlDepth(this.plan.horizontal(x + 0.5, z + 0.5) / this.plan.edge(x + 0.5, z + 0.5));
        if (depth < 0.5 || !this.level.hasChunk(x >> 4, z >> 4)) {
            return HalleyPlan.UNTOUCHED;
        }
        int ground = groundAt(this.level, x, z);
        return ground <= minY + 1 ? HalleyPlan.UNTOUCHED : ground - (int) Math.round(depth);
    }

    /** The bowl's glassy floor: tinted glass and obsidian in the middle, threaded with magma, blackstone toward the rim. */
    private BlockState bowlSurface(int x, int y, int z) {
        float n = this.plan.noise(x, y, z);
        double k = this.plan.horizontal(x + 0.5, z + 0.5) / this.plan.params.bowlRadius();
        if (n < 0.12F) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        // the plasma's residue, here and there in the glassy floor (the user's server trades it)
        if (n < 0.145F && k < 0.85 && this.config.solarResidue()) {
            return HalleyContent.SOLAR_RESIDUE.get().defaultBlockState();
        }
        if (k < 0.55) {
            return n < 0.6F ? Blocks.TINTED_GLASS.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        }
        return n < 0.5F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    /** The top of the natural ground in a column: under trees, plants and snow. */
    static int groundAt(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top, z);
        for (int i = 0; i < 40 && at.getY() > level.getMinBuildHeight(); i++) {
            BlockState state = level.getBlockState(at);
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.REPLACEABLE) && !state.isAir()) {
                return at.getY();
            }
            at.move(0, -1, 0);
        }
        return top;
    }
}
