package dev.ss05.halley.world;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.compat.StarBridge;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SA-03 Proxima on the server: what the flare does to the world and to the living (docs/SS-07-PROXIMA.md).
 * <ul>
 *   <li>{@link ProximaPlan#FLASH}, the flash burn: everything living out past the scorch, under the open sky, catches
 *   fire and is hurt a little. Shelter is {@link #SHELTER} solid blocks overhead (glass doesn't count).</li>
 *   <li>{@link ProximaPlan#IMPACT}, ground zero: in the bowl things are erased (or take the damage slider's hit); in the
 *   charred ring they are hurt badly, set alight and thrown; in the scorch, hurt and pushed. Players are only nudged
 *   (creative, spectators and the caster never), mobs are thrown. Then the bowl is cut (a glassy floor of tinted glass,
 *   obsidian and magma) and the land round it scorched ({@link ProximaLand}).</li>
 * </ul>
 * Only loaded land is touched; the rest as it loads.
 */
public final class ProximaStrike {
    public static final ResourceKey<DamageType> FLARE = ResourceKey.create(Registries.DAMAGE_TYPE, HalleyAddon.id("proxima_flare"));
    /** Solid blocks over a creature's head that shelter it from the flash and the blast. */
    private static final int SHELTER = 3;
    /** Lands still waiting for chunks to load, after their strike. */
    private static final List<ProximaLand> LINGERING = new ArrayList<>();

    private final ServerLevel level;
    private final ProximaPlan plan;
    private final CastTuning cast;
    private final BlockPos spared;
    @Nullable
    private Excavation bowl;
    @Nullable
    private ProximaLand land;

    public ProximaStrike(ServerLevel level, ProximaPlan plan, @Nullable ServerPlayer caster, CastTuning cast) {
        this.level = level;
        this.plan = plan;
        this.cast = cast;
        this.spared = caster != null ? caster.blockPosition() : BlockPos.containing(plan.target);
    }

    public ProximaPlan plan() {
        return this.plan;
    }

    public void tick(int t, @Nullable ServerPlayer caster) {
        if (caster != null && t >= ProximaPlan.FLASH - 5 && t <= ProximaPlan.IMPACT + 60 && t % 30 == 0) {
            StarBridge.ward(caster);
        }
        if (t == ProximaPlan.FLASH) {
            this.flash(caster);
        }
        if (t == ProximaPlan.IMPACT) {
            this.impact(caster);
        }
        if (this.bowl != null && !this.bowl.done()) {
            this.bowl.tick();
        }
        if (this.land != null) {
            this.land.tick();
        }
    }

    /** Whether the world work that can be done now is done (unloaded land waits, after the spell, for its chunks). */
    public boolean worldDone() {
        return this.land != null && this.land.loadedDone();
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

    /** From the server tick: scorches what loaded since its strike ended. */
    public static void tickLingering() {
        LINGERING.removeIf(land -> {
            land.tick();
            return land.finished();
        });
    }

    public static void clearLingering() {
        LINGERING.clear();
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
            living.igniteForSeconds((float) (3.0 + 5.0 * k));
            living.hurt(this.flare(living == caster ? null : caster), this.cast.cap((float) (2.0 + 6.0 * k)));
        }
    }

    // ---- Ground zero. ----------------------------------------------------------------------------------------------

    private void impact(@Nullable ServerPlayer caster) {
        double bowl = this.plan.params.bowlRadius();
        double charred = this.plan.params.charRadius();
        double reach = this.plan.params.scorchRadius();
        for (Entity entity : this.level.getEntities((Entity) null, this.around(reach), Entity::isAlive)) {
            double d = this.plan.horizontal(entity.getX(), entity.getZ());
            if (d > reach) {
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
                    living.igniteForSeconds((float) (4.0 + 6.0 * k));
                } else {
                    // staggered
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            }
        }
        int minY = this.level.getMinBuildHeight();
        this.bowl = StarBridge.excavate(this.level, this.plan.target, bowl + 3.0, (x, z) -> this.bowlFloor(x, z, minY),
            (x, y, z, was) -> this.bowlSurface(x, y, z), this.spared, "SA-03 Proxima bowl", HalleyConfig.chunksPerTick(), null);
        this.land = new ProximaLand(this.level, this.plan);
    }

    /** In the bowl: erased (the damage slider below ∞: its hit instead); loose things burn up. */
    private void vaporise(Entity entity, @Nullable ServerPlayer caster) {
        if (entity instanceof LivingEntity living) {
            if (!immune(living, caster)) {
                StarBridge.hit(this.level, living, living == caster ? null : caster, this.cast.damage());
            }
        } else if (!(entity instanceof Player) && entity.getPassengers().isEmpty()
                && (caster == null || entity != caster.getVehicle())) {
            entity.discard();
        }
    }

    /** Thrown away from the mark: mobs hard, players only nudged (never creative ones, spectators or the caster). */
    private void knock(Entity entity, @Nullable ServerPlayer caster, double k, boolean sheltered) {
        if (sheltered || entity == caster || entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        Vec3 away = new Vec3(entity.getX() - this.plan.target.x, 0.0, entity.getZ() - this.plan.target.z);
        away = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        double push = entity instanceof Player ? Math.min(0.6, 0.2 + 0.6 * k) : 1.0 + 3.0 * k;
        double lift = entity instanceof Player ? 0.25 : 0.4 + 0.8 * k;
        entity.setDeltaMovement(entity.getDeltaMovement().add(away.scale(push)).add(0.0, lift, 0.0));
        entity.hurtMarked = true;
    }

    private static boolean immune(LivingEntity living, @Nullable ServerPlayer caster) {
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        return living != caster && caster != null && !StarBridge.affects(living, caster);
    }

    /** {@link #SHELTER} solid blocks overhead (a glass roof is no bunker). */
    private boolean sheltered(LivingEntity living) {
        BlockPos head = BlockPos.containing(living.getEyePosition());
        int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, head.getX(), head.getZ());
        int solid = 0;
        BlockPos.MutableBlockPos at = head.mutable();
        for (int y = head.getY() + 1; y < top && solid < SHELTER; y++) {
            at.setY(y);
            if (this.level.getBlockState(at).isSolidRender(this.level, at)) {
                solid++;
            }
        }
        return solid >= SHELTER;
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
