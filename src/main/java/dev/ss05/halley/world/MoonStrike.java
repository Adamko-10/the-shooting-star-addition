package dev.ss05.halley.world;

import dev.ss05.halley.CastTuning;
import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.MoonConfig;
import dev.ss05.halley.MoonParams;
import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What Luna does to the world, tick by tick (server side). The look and sound of it is {@code client/luna/}.
 *
 * <ol>
 *   <li>{@link MoonPlan#ALARM}: "EARTH SYSTEM SHUT DOWN" to everyone nearby.</li>
 *   <li>{@link MoonPlan#EVAC}: a creative caster standing where it will land is lifted clear.</li>
 *   <li>{@link MoonPlan#CONTACT}: everything within {@link MoonPlan#eraseRadius()} is erased, everything out to
 *       {@link MoonPlan#blastRadius()} is thrown, hurt and briefly set alight. The crater is blasted out round the
 *       moon's resting place, with the molten seam where the moon meets its floor ({@link #crater}); once it is dug,
 *       the crust buckles up against the moon and the crater's rim heaves up while the moon drives in
 *       ({@link MoonRim}), and cracks race out across the land and veins of molten rock across the floor
 *       ({@link MoonCracks}).</li>
 *   <li>{@link MoonPlan#SETTLE}: the moon itself is built out of moon cheese, a few chunks per tick, nearest the mark
 *       first ({@link MoonBuild}).</li>
 * </ol>
 */
public final class MoonStrike {
    /** The most chunks round the mark held loaded at once, in chunks (The Shooting Star's own big strikes hold 12). */
    private static final int MAX_TICKET_RADIUS = 12;
    private static final TicketType<ChunkPos> TICKET = TicketType.create(HalleyAddon.MOD_ID + ":luna", Comparator.comparingLong(ChunkPos::toLong));
    private static final int EVAC_COLOR = 0x6FE8FF;

    /**
     * Ticks after everything is cut and built at which the crater's chunks are sent to players again. The fast carver
     * sends each chunk the moment it is cut, before the light engine has caught up, so a fresh crater's floor can look
     * unlit in patches (as Halley's scar does).
     */
    private static final int[] RESEND_AFTER = {40, 140};

    /** The molten seam where moon and land meet: magma, with lava pooled in it. */
    private static final Palette SEAM = Palette.of(Blocks.MAGMA_BLOCK, 50, Blocks.LAVA, 35, Blocks.BLACKSTONE, 15);
    /** The crater's floor near the moon: burnt black, still glowing here and there. */
    private static final Palette FLOOR_HOT = Palette.of(Blocks.BLACKSTONE, 30, Blocks.BASALT, 22, Blocks.MAGMA_BLOCK, 16,
        Blocks.SMOOTH_BASALT, 10, Blocks.COBBLED_DEEPSLATE, 14, Blocks.TUFF, 8);
    /** Further out: scorched rock and rubble. */
    private static final Palette FLOOR_MID = Palette.of(Blocks.BLACKSTONE, 20, Blocks.TUFF, 16, Blocks.COBBLED_DEEPSLATE, 14,
        Blocks.BASALT, 12, Blocks.COARSE_DIRT, 14, Blocks.GRAVEL, 14, Blocks.MAGMA_BLOCK, 5, Blocks.ANDESITE, 5);
    /** Up the slopes toward the rim: blasted earth and gravel. */
    private static final Palette FLOOR_OUTER = Palette.of(Blocks.COARSE_DIRT, 30, Blocks.GRAVEL, 20, Blocks.TUFF, 12,
        Blocks.DIRT, 18, Blocks.COBBLESTONE, 10, Blocks.BLACKSTONE, 10);
    /** Lumps of crust thrown out with the moon cheese. */
    private static final Palette CRUST_LUMP = Palette.of(Blocks.BLACKSTONE, 35, Blocks.BASALT, 25, Blocks.COBBLED_DEEPSLATE, 25,
        Blocks.MAGMA_BLOCK, 15);

    private final ServerLevel level;
    private final MoonPlan plan;
    /** The remote's sliders for this cast: its damage slider decides what each hit does. */
    private final CastTuning cast;
    private final MoonParams params;
    private final MoonConfig.Tuning tuning;
    private ChunkPos ticketAt;
    private int ticketRadius;
    private boolean ticketsHeld;
    /** The single chunks the carver is streaming in (see {@link Excavation.Loader}), let go once cut or at the end. */
    private final Set<ChunkPos> streamed = new HashSet<>();
    private final Excavation.Loader loader = new Excavation.Loader() {
        @Override
        public void hold(ChunkPos pos) {
            if (MoonStrike.this.streamed.add(pos)) {
                MoonStrike.this.holdChunk(pos);
            }
        }

        @Override
        public void release(ChunkPos pos) {
            if (MoonStrike.this.streamed.remove(pos)) {
                MoonStrike.this.releaseChunk(pos);
            }
        }
    };
    @Nullable
    private Excavation crater;
    @Nullable
    private MoonBuild moonBuild;
    @Nullable
    private MoonCracks cracks;
    @Nullable
    private MoonRim rim;
    @Nullable
    private BlockPos spared;
    private boolean decorated;
    /** When everything was cut and built (the tick), or -1. */
    private int carvedAt = -1;

    /** {@code cast} is the remote's sliders for this cast (the sizes in the plan are already scaled by its power). */
    public MoonStrike(ServerLevel level, MoonPlan plan, ServerPlayer caster, CastTuning cast) {
        this.level = level;
        this.plan = plan;
        this.cast = cast;
        this.params = plan.params;
        this.tuning = MoonConfig.tuning();
        this.holdChunks();
        HalleyAddon.LOG.debug("Luna called by {}: mark {}, falling from {}", caster.getScoreboardName(), plan.target, plan.startCentre);
    }

    public MoonPlan plan() {
        return this.plan;
    }

    // ---- Chunk tickets: keep everything the strike touches loaded while it runs. -----------------------------------

    private void holdChunks() {
        Vec3 t = this.plan.target;
        double radius = Math.max(this.plan.rimOuter + 10.0, this.params.moonRadius() + 10.0) + 16.0;
        // at most MAX_TICKET_RADIUS chunks round the mark at once: a bigger moonfall (the power slider) streams the
        // rest of its crater in as the carver reaches it, like The Shooting Star's own big strikes
        this.ticketRadius = Math.min(MAX_TICKET_RADIUS, (int) Math.ceil(radius / 16.0));
        this.ticketAt = new ChunkPos(Mth.floor(t.x) >> 4, Mth.floor(t.z) >> 4);
        this.level.getChunkSource().addRegionTicket(TICKET, this.ticketAt, this.ticketRadius, this.ticketAt);
        this.ticketsHeld = true;
    }

    private void releaseChunks() {
        if (this.ticketsHeld) {
            this.ticketsHeld = false;
            this.level.getChunkSource().removeRegionTicket(TICKET, this.ticketAt, this.ticketRadius, this.ticketAt);
        }
        for (ChunkPos pos : this.streamed) {
            this.releaseChunk(pos);
        }
        this.streamed.clear();
    }

    /** One chunk the carver is waiting for, kept loaded (the way The Shooting Star's own carvings hold theirs). */
    private void holdChunk(ChunkPos pos) {
        this.level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
    }

    private void releaseChunk(ChunkPos pos) {
        this.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
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
        // the rim, the slabs and the cracks rise and are cut into the crater's floor: they wait for it to be dug (it
        // takes a second or two), or the excavator would cut them away again
        boolean dug = this.crater == null || this.crater.done();
        if (this.rim != null && !this.rim.done() && dug) {
            this.rim.tick(t);
        }
        if (this.cracks != null && !this.cracks.done() && dug) {
            this.cracks.tick(t);
        }
        // Wait for the crater to be fully cut before painting any of the moon: the excavator removes everything
        // above its floor in whatever chunk it next reaches, so a chunk painted ahead of it would be cut away again.
        if (this.moonBuild != null && !this.moonBuild.done() && (this.crater == null || this.crater.done())) {
            this.moonBuild.tick();
        }
        if (!this.decorated && this.carvingDone()) {
            this.decorated = true;
            this.carvedAt = t;
            this.scatterDebris();
        }
        if (this.carvedAt >= 0) {
            for (int after : RESEND_AFTER) {
                if (t == this.carvedAt + after) {
                    this.resendCrater();
                }
            }
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
        if (this.rim != null) {
            this.rim.finish();
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
        if (this.params.carve()) {
            this.resendCrater();
        }
        this.releaseChunks();
    }

    /**
     * Whether everything it does to the land (crater, rim, cracks, the moon itself) is done. A big moonfall (the remote's
     * power slider) can take longer than its film: the spell then runs on until this is true, rather than building the
     * rest in one go at the end.
     */
    public boolean worldDone() {
        // the cracks and the rim's far reaches are only cut into loaded chunks (and finished, as far as they can be,
        // at the end), so only the crater and the moon are waited for
        return !this.params.carve() || (this.crater == null || this.crater.done()) && this.moonBuild != null && this.moonBuild.done();
    }

    private boolean carvingDone() {
        return (this.crater == null || this.crater.done()) && (this.moonBuild == null || this.moonBuild.done())
            && (this.cracks == null || this.cracks.done()) && (this.rim == null || this.rim.done());
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
            this.crater = StarBridge.excavate(this.level, this.plan.target, this.plan.rimCrest + 2.0, this::crater,
                this::craterSurface, this.spared, "Luna impact", this.tuning.chunksPerTick(), this.loader);
            // the crust buckling up against the moon and the rim heaving up as it drives in, and the fissures racing
            // out across the land
            this.rim = new MoonRim(this.level, this.plan);
            this.cracks = new MoonCracks(this.level, this.plan);
        }

        Vec3 t = this.plan.target;
        double erase = this.plan.eraseRadius();
        double blast = this.plan.blastRadius();
        double bottom = Math.min(t.y - this.params.rimHeight() - 16.0, this.plan.restCentre.y - this.params.moonRadius() - 16.0);
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

    /**
     * What is cut away at the impact: over the moon's resting place, everything above the moon's top (it fills the rest
     * itself); round it, the crater down to its floor ({@link MoonPlan#floorY}), with a trench for the molten rock in
     * the seam where the moon meets it. Never into the world's bottom layer. The rim is left to MoonRim, the land
     * beyond to MoonCracks.
     */
    private int crater(int x, int z) {
        double d = this.plan.fromMark(x + 0.5, z + 0.5);
        if (d >= this.plan.rimCrest || !this.level.hasChunk(x >> 4, z >> 4)) {
            return MoonPlan.UNTOUCHED;
        }
        int bottom = this.level.getMinBuildHeight() + 1;
        int moonTop = this.plan.moonTop(x, z);
        if (moonTop != MoonPlan.UNTOUCHED && d < this.plan.restFootprint) {
            return Math.max(bottom, moonTop);
        }
        int ground = MoonRim.groundAt(this.level, x, z);
        if (ground == Integer.MIN_VALUE) {
            return MoonPlan.UNTOUCHED;
        }
        int floor = this.plan.floorY(d, ground);
        if (this.plan.inSeam(x + 0.5, z + 0.5)) {
            floor -= 2;
        }
        return Math.max(bottom, floor);
    }

    /** The new top block of a cut column: molten rock in the seam, burnt rock and rubble across the floor (the moon's own
     * columns keep theirs: it is built over them). */
    @Nullable
    private BlockState craterSurface(int x, int y, int z, BlockState was) {
        if (!was.getFluidState().isEmpty()) {
            return null;
        }
        if (this.plan.inSeam(x + 0.5, z + 0.5)) {
            return SEAM.pick(this.plan.hash(z * 7 + 3, y, x * 11 - 5));
        }
        double d = this.plan.fromMark(x + 0.5, z + 0.5);
        if (d < this.plan.floorInner || d >= this.plan.rimCrest) {
            return null;
        }
        double k = this.plan.floorK(d) + (this.plan.hash(x, 151, z) - 0.5) * 0.16;
        double pick = this.plan.hash(z * 5 - 1, y, x * 3 + 7);
        return k < 0.3 ? FLOOR_HOT.pick(pick) : k < 0.72 ? FLOOR_MID.pick(pick) : FLOOR_OUTER.pick(pick);
    }

    /** Sends the crater's chunks, with their now settled light, to everyone who has them loaded. */
    private void resendCrater() {
        ThreadedLevelLightEngine light = this.level.getChunkSource().getLightEngine();
        double reach = this.plan.rimOuter + 8.0;
        Vec3 t = this.plan.target;
        int x0 = Mth.floor(t.x - reach) >> 4;
        int x1 = Mth.floor(t.x + reach) >> 4;
        int z0 = Mth.floor(t.z - reach) >> 4;
        int z1 = Mth.floor(t.z + reach) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                double dx = Math.max(0.0, Math.abs(cx * 16 + 8 - t.x) - 8.0);
                double dz = Math.max(0.0, Math.abs(cz * 16 + 8 - t.z) - 8.0);
                if (dx * dx + dz * dz > reach * reach) {
                    continue;
                }
                ChunkPos pos = new ChunkPos(cx, cz);
                List<ServerPlayer> players = this.level.getChunkSource().chunkMap.getPlayers(pos, false);
                LevelChunk chunk = players.isEmpty() ? null : this.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
                    for (ServerPlayer player : players) {
                        player.connection.send(packet);
                    }
                }
            }
        }
    }

    /** Moon cheese and lumps of crust thrown clear onto the land round the rim, where the client sees them land. */
    private void scatterDebris() {
        if (!this.params.carve()) {
            return;
        }
        Map<BlockPos, BlockState> out = new HashMap<>();
        BlockState cheese = HalleyContent.MOON_CHEESE.get().defaultBlockState();
        BlockState holey = HalleyContent.HOLEY_MOON_CHEESE.get().defaultBlockState();
        for (int i = 0; i < this.plan.debrisCount(); i++) {
            Vec3 land = this.plan.debrisLanding(i);
            int x = Mth.floor(land.x);
            int z = Mth.floor(land.z);
            if (!this.level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = new BlockPos(x, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState below = this.level.getBlockState(top.below());
            if (!below.getFluidState().isEmpty() || below.is(BlockTags.LEAVES)
                    || !below.isCollisionShapeFullBlock(this.level, top.below()) || !this.level.getBlockState(top).isAir()) {
                continue;
            }
            BlockState lump = this.plan.hash(i, 5, 3) < 0.6 ? (this.plan.hash(i, 19, 4) < 0.2 ? holey : cheese)
                : CRUST_LUMP.pick(this.plan.hash(i, 9, 1));
            out.put(top, lump);
            // the bigger lumps: a second block beside or on top
            if (this.plan.hash(i, 13, 7) < 0.35) {
                out.put(this.plan.hash(i, 17, 2) < 0.5 ? top.above() : top.east(), lump);
            }
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }

    // ---- Hitting things. -------------------------------------------------------------------------------------------

    /**
     * Inside the erase radius: living things are erased (creative and spectator players excepted), loose things removed.
     * With the remote's damage slider below ∞, living things take that much instead (The Shooting Star's own hit).
     */
    private void hit(Entity entity, @Nullable ServerPlayer caster) {
        if (entity instanceof LivingEntity living) {
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return;
            }
            if (living.isAlive()) {
                StarBridge.hit(this.level, living, living == caster ? null : caster, this.cast.damage());
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
            float damage = this.cast.cap((float) (10.0 + 60.0 * strength * strength));
            living.hurt(this.impact(living == caster ? null : caster), damage);
            // 1.20.1's Entity.setSecondsOnFire takes an int (1.21's igniteForSeconds takes a float); round up so a
            // nonzero strength always catches at least one second of fire.
            living.setSecondsOnFire(Mth.ceil(3.0 + 6.0 * strength));
        }
    }

    private DamageSource impact(@Nullable Entity attacker) {
        return new DamageSource(this.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(MoonDamage.IMPACT),
            attacker, attacker);
    }
}
