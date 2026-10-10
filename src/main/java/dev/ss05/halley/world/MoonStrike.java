package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What SS-06 does to the world, tick by tick (server side). The look and sound of it is {@code client/luna/}.
 *
 * <ol>
 *   <li>{@link MoonPlan#ALARM}: "EARTH SYSTEM SHUT DOWN" to everyone nearby.</li>
 *   <li>{@link MoonPlan#EVAC}: a creative caster standing in the crater-to-be is lifted clear.</li>
 *   <li>{@link MoonPlan#CONTACT}: everything within {@link MoonPlan#eraseRadius()} is erased, everything out to
 *       {@link MoonPlan#blastRadius()} is thrown, hurt and briefly set alight; the crater is cut and the moon built,
 *       a few chunks per tick, nearest the mark first.</li>
 * </ol>
 */
public final class MoonStrike {
    private static final TicketType<ChunkPos> TICKET = TicketType.create(HalleyAddon.MOD_ID + ":luna", Comparator.comparingLong(ChunkPos::toLong));
    private static final int EVAC_COLOR = 0x6FE8FF;

    // The crater's surface, scorched and blackened by the moon's heat: fiercest nearest the moon, fading toward the rim.
    private static final Palette CRATER_CORE = Palette.of(Blocks.MAGMA_BLOCK, 30, Blocks.BASALT, 40, Blocks.BLACKSTONE, 30);
    private static final Palette CRATER_MID = Palette.of(Blocks.BASALT, 28, Blocks.BLACKSTONE, 24, Blocks.COARSE_DIRT, 26, Blocks.COBBLED_DEEPSLATE, 22);
    private static final Palette CRATER_RIM = Palette.of(Blocks.COARSE_DIRT, 35, Blocks.GRAVEL, 20, Blocks.COBBLED_DEEPSLATE, 20, null, 25);

    private final ServerLevel level;
    private final MoonPlan plan;
    private final MoonParams params;
    private final MoonConfig.Tuning tuning;
    private ChunkPos ticketAt;
    private int ticketRadius;
    private boolean ticketsHeld;
    @Nullable
    private Excavation crater;
    @Nullable
    private MoonBuild moonBuild;
    @Nullable
    private MoonCracks cracks;
    @Nullable
    private BlockPos spared;
    private boolean decorated;

    public MoonStrike(ServerLevel level, MoonPlan plan, ServerPlayer caster) {
        this.level = level;
        this.plan = plan;
        this.params = plan.params;
        this.tuning = MoonConfig.tuning();
        this.holdChunks();
        HalleyAddon.LOG.debug("SS-06 called by {}: mark {}, falling from {}", caster.getScoreboardName(), plan.target, plan.startCentre);
    }

    public MoonPlan plan() {
        return this.plan;
    }

    // ---- Chunk tickets: keep everything the strike touches loaded while it runs. -----------------------------------

    private void holdChunks() {
        Vec3 t = this.plan.target;
        double radius = Math.max(this.params.craterRadius() * 1.15 + 10.0, this.params.moonRadius() + 10.0) + 16.0;
        this.ticketRadius = (int) Math.ceil(radius / 16.0);
        this.ticketAt = new ChunkPos(Mth.floor(t.x) >> 4, Mth.floor(t.z) >> 4);
        this.level.getChunkSource().addRegionTicket(TICKET, this.ticketAt, this.ticketRadius, this.ticketAt);
        this.ticketsHeld = true;
    }

    private void releaseChunks() {
        if (this.ticketsHeld) {
            this.ticketsHeld = false;
            this.level.getChunkSource().removeRegionTicket(TICKET, this.ticketAt, this.ticketRadius, this.ticketAt);
        }
    }

    // ---- The timeline. ---------------------------------------------------------------------------------------------

    public void tick(int t, @Nullable ServerPlayer caster) {
        if (caster != null && t % 10 == 0) {
            StarBridge.ward(caster);
        }
        if (t == MoonPlan.ALARM) {
            this.alarm(caster);
        }
        if (t == MoonPlan.EVAC && caster != null) {
            this.evacuate(caster);
        }
        if (t == MoonPlan.CONTACT - 1 && caster != null && this.spared == null && this.evacuates(caster)
                && this.horizontal(caster.getX(), caster.getZ()) <= this.plan.eraseRadius() + 4.0) {
            // A creative caster still standing where the moon is about to land keeps the ground under their feet.
            this.spared = BlockPos.containing(caster.getX(), caster.getY() - 1.0, caster.getZ());
        }
        if (t == MoonPlan.CONTACT) {
            this.detonate(caster);
        }
        if (t == MoonPlan.SETTLE && this.params.carve() && this.moonBuild == null) {
            // The client plays the moon ploughing down into the crater until SETTLE; only from there does the
            // block moon actually sit at rest, so there is nothing to build before this.
            this.moonBuild = new MoonBuild(this.level, this.plan, this.tuning.chunksPerTick());
        }

        if (this.crater != null && !this.crater.done()) {
            this.crater.tick();
        }
        if (this.cracks != null && !this.cracks.done()) {
            this.cracks.tick(t);
        }
        // Wait for the crater to be fully cut before painting any of the moon: the excavator removes everything
        // above its floor in whatever chunk it next reaches, so a chunk painted ahead of it would be cut away again.
        if (this.moonBuild != null && !this.moonBuild.done() && (this.crater == null || this.crater.done())) {
            this.moonBuild.tick();
        }
        if (!this.decorated && this.carvingDone()) {
            this.decorated = true;
            this.scatterDebris();
        }
        if (this.ticketsHeld && t > MoonPlan.CONTACT + 40 && this.carvingDone()) {
            this.releaseChunks();
        }
    }

    public void end() {
        // Crater first, always: the moon must never be painted into a chunk the excavator hasn't finished cutting.
        if (this.crater != null) {
            this.crater.finish();
        }
        if (this.cracks != null) {
            this.cracks.finish();
        }
        if (this.moonBuild == null && this.params.carve()) {
            this.moonBuild = new MoonBuild(this.level, this.plan, this.tuning.chunksPerTick());
        }
        if (this.moonBuild != null) {
            this.moonBuild.finish();
        }
        if (!this.decorated && this.carvingDone()) {
            this.decorated = true;
            this.scatterDebris();
        }
        this.releaseChunks();
    }

    private boolean carvingDone() {
        return (this.crater == null || this.crater.done()) && (this.moonBuild == null || this.moonBuild.done())
            && (this.cracks == null || this.cracks.done());
    }

    private double horizontal(double x, double z) {
        return Math.hypot(x - this.plan.target.x, z - this.plan.target.z);
    }

    private double top() {
        return this.level.getMaxBuildHeight() + 64.0;
    }

    // ---- The alarm. ------------------------------------------------------------------------------------------------

    private void alarm(@Nullable ServerPlayer caster) {
        double range = this.plan.blastRadius() * 3.0;
        Component message = Component.literal(MoonInfo.ALERT).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer player : this.level.players()) {
            if (player == caster) {
                continue;
            }
            if (this.horizontal(player.getX(), player.getZ()) <= range) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // ---- Evacuation. -----------------------------------------------------------------------------------------------

    private boolean evacuates(ServerPlayer caster) {
        return HalleyConfig.tuning().evacuateCreative() && (caster.isCreative() || caster.isSpectator());
    }

    private void evacuate(ServerPlayer caster) {
        if (!this.evacuates(caster)) {
            return;
        }
        double zone = this.plan.eraseRadius();
        if (this.horizontal(caster.getX(), caster.getZ()) >= zone) {
            return;
        }
        Vec3 from = caster.position();
        if (StarBridge.liftClear(caster, this.plan.target, zone, zone + 36.0, MoonPlan.DURATION - MoonPlan.EVAC)) {
            for (Vec3 at : new Vec3[]{from, caster.position()}) {
                this.level.playSound(null, at.x, at.y, at.z, HalleyContent.EVAC.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            }
            caster.connection.send(new ClientboundSetTitlesAnimationPacket(2, 30, 12));
            caster.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("EVAC // CLEAR OF THE STRIKE ZONE").withStyle(style -> style.withColor(EVAC_COLOR))));
            caster.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        } else {
            this.spared = BlockPos.containing(caster.getX(), caster.getY() - 1.0, caster.getZ());
        }
    }

    // ---- Impact. ---------------------------------------------------------------------------------------------------

    private void detonate(@Nullable ServerPlayer caster) {
        if (this.params.carve()) {
            int minY = this.level.getMinBuildHeight();
            this.crater = StarBridge.excavate(this.level, this.plan.target, this.params.craterRadius() * 1.15 + 10.0,
                (x, z) -> this.plan.craterFloor(x, z, minY), this::craterSurface, this.spared,
                "SS-06 Luna crater", this.tuning.chunksPerTick());
            // and the fissures racing out across the land from the rim (like Gungnir's)
            this.cracks = new MoonCracks(this.level, this.plan);
        }

        Vec3 t = this.plan.target;
        double erase = this.plan.eraseRadius();
        double blast = this.plan.blastRadius();
        double bottom = Math.min(t.y - this.params.craterDepth() - 16.0, this.plan.restCentre.y - this.params.moonRadius() - 16.0);
        AABB zone = new AABB(t.x - blast, bottom, t.z - blast, t.x + blast, this.top(), t.z + blast);
        for (Entity entity : this.level.getEntities((Entity) null, zone, e -> true)) {
            double d = this.horizontal(entity.getX(), entity.getZ());
            if (d <= erase) {
                this.hit(entity, caster);
            } else if (d <= blast) {
                double strength = 1.0 - (d - erase) / Math.max(1.0, blast - erase);
                Vec3 out = new Vec3(entity.getX() - t.x, 0.0, entity.getZ() - t.z);
                out = out.lengthSqr() < 1.0E-4 ? this.plan.travel : out.normalize();
                this.throwHurtIgnite(entity, caster, out, strength);
            }
        }
    }

    private BlockState craterSurface(int x, int y, int z, BlockState was) {
        if (!was.getFluidState().isEmpty()) {
            return null;
        }
        double k = this.plan.craterK(x + 0.5, z + 0.5) + (this.plan.hash(x, 0, z) - 0.5) * 0.14;
        double roll = this.plan.hash(z * 7 + 3, 0, x * 11 - 5);
        return (k < 0.3 ? CRATER_CORE : k < 0.68 ? CRATER_MID : CRATER_RIM).pick(roll);
    }

    /** Moon cheese thrown clear of the crater onto the land round it, deterministic from the plan's seed. */
    private void scatterDebris() {
        if (!this.params.carve()) {
            return;
        }
        Map<BlockPos, BlockState> out = new HashMap<>();
        Vec3 c = this.plan.target;
        double radius = this.params.craterRadius();
        BlockState cheese = HalleyContent.MOON_CHEESE.get().defaultBlockState();
        int count = 40 + (int) (this.plan.hash(11, 0, 29) * 40.0);
        for (int i = 0; i < count; i++) {
            double angle = this.plan.hash(i * 5 + 1, 0, 7) * Math.PI * 2.0;
            double dist = radius * (1.03 + this.plan.hash(i * 3 + 2, 0, 13) * 1.3);
            int x = Mth.floor(c.x + Math.cos(angle) * dist);
            int z = Mth.floor(c.z + Math.sin(angle) * dist);
            if (!this.level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = new BlockPos(x, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            BlockState was = this.level.getBlockState(top);
            if (was.isAir() || !was.getFluidState().isEmpty() || was.is(BlockTags.LEAVES)
                    || !was.isCollisionShapeFullBlock(this.level, top) || was.hasBlockEntity()) {
                continue;
            }
            out.put(top, cheese);
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }

    // ---- Hitting things. -------------------------------------------------------------------------------------------

    /** Inside the erase radius: living things are erased (creative and spectator players excepted), loose things removed. */
    private void hit(Entity entity, @Nullable ServerPlayer caster) {
        if (entity instanceof LivingEntity living) {
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return;
            }
            if (living.isAlive()) {
                StarBridge.erase(this.level, living, living == caster ? null : caster);
            }
        } else if (!(entity instanceof Player) && entity.getPassengers().isEmpty()
                && (caster == null || entity != caster.getVehicle()) && !entity.isRemoved()) {
            entity.discard();
        }
    }

    /** Beyond the erase radius, out to the blast radius: thrown, hurt and briefly set alight (falls off with distance). */
    private void throwHurtIgnite(Entity entity, @Nullable ServerPlayer caster, Vec3 away, double strength) {
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        double push = 2.0 + 5.0 * strength;
        double lift = 0.9 + 1.8 * strength;
        entity.setDeltaMovement(entity.getDeltaMovement().add(away.scale(push)).add(0.0, lift, 0.0));
        entity.hurtMarked = true;
        if (entity instanceof LivingEntity living && (living == caster || caster == null || StarBridge.affects(living, caster))) {
            float damage = (float) (10.0 + 60.0 * strength * strength);
            living.hurt(this.impact(living == caster ? null : caster), damage);
            living.igniteForSeconds((float) (3.0 + 6.0 * strength));
        }
    }

    private DamageSource impact(@Nullable Entity attacker) {
        return new DamageSource(this.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(MoonDamage.IMPACT),
            attacker, attacker);
    }
}
