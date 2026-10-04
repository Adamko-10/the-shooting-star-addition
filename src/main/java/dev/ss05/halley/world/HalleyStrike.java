package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.HalleyParams;
import dev.ss05.halley.HalleyPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
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
 * What SS-05 does to the world, tick by tick (server side). The look and sound of it is {@code client/HalleyFx}.
 *
 * <ol>
 *   <li>{@link HalleyPlan#EVAC}: a creative caster standing in the crater is lifted clear.</li>
 *   <li>{@link HalleyPlan#TOUCHDOWN}: the comet touches down at the far end of the corridor.</li>
 *   <li>Touchdown to impact: it ploughs the trench toward the mark; everything in the trench dies as the nucleus
 *       passes, things beside it are thrown aside and frosted.</li>
 *   <li>Impact: the crater is cut, everything in it dies, the comet heart is left in its floor and a shock wave
 *       throws, hurts and frosts everything out to {@code blast_reach} crater radii.</li>
 * </ol>
 */
public final class HalleyStrike {
    private static final TicketType<ChunkPos> TICKET = TicketType.create(HalleyAddon.MOD_ID + ":halley", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TRENCH_CHUNKS_PER_TICK = 12;
    /** The trench is cut in pieces this long, each started as the nucleus reaches it (a single circle round it all
     *  would visit hundreds of chunks that the trench never touches). */
    private static final double SEGMENT = 32.0;
    private static final int CRATER_CHUNKS_PER_TICK = 24;
    private static final int BLAST_TICKS = 28;
    /**
     * Ticks after the crater is finished at which the scar's chunks are sent to players again. The fast carver sends
     * each chunk the moment it is cut, before the light engine has caught up, so a fresh crater can look unlit.
     */
    private static final int[] RESEND_AFTER = {40, 140};
    private static final int EVAC_COLOR = 0x6FE8FF;

    // What the cut surfaces turn into: comet ice in the middle, frost and shattered rock toward the edges.
    // Each is pairs of (block, weight); null keeps whatever was there.
    private static final Palette TRENCH_CORE = Palette.of(Blocks.BLUE_ICE, 38, Blocks.PACKED_ICE, 34, Blocks.CALCITE, 16, Blocks.SNOW_BLOCK, 12);
    private static final Palette TRENCH_SLOPE = Palette.of(Blocks.PACKED_ICE, 25, Blocks.SNOW_BLOCK, 25, Blocks.TUFF, 20, Blocks.CALCITE, 15, Blocks.COBBLED_DEEPSLATE, 15);
    private static final Palette TRENCH_LIP = Palette.of(Blocks.SNOW_BLOCK, 30, Blocks.TUFF, 20, Blocks.GRAVEL, 12, null, 38);
    private static final Palette CRATER_CORE = Palette.of(Blocks.BLUE_ICE, 45, Blocks.PACKED_ICE, 35, Blocks.CALCITE, 20);
    private static final Palette CRATER_BOWL = Palette.of(Blocks.PACKED_ICE, 30, Blocks.SNOW_BLOCK, 22, Blocks.CALCITE, 18, Blocks.TUFF, 16, Blocks.SMOOTH_BASALT, 14);
    private static final Palette CRATER_RIM = Palette.of(Blocks.SNOW_BLOCK, 32, Blocks.TUFF, 23, Blocks.COBBLED_DEEPSLATE, 20, null, 25);

    private final ServerLevel level;
    private final HalleyPlan plan;
    private final HalleyParams params;
    private final UUID casterId;
    private final HalleyConfig.Tuning tuning;
    private final List<int[]> tickets = new ArrayList<>();
    private final Set<Integer> wakeThrown = new HashSet<>();
    private final Set<Integer> blastThrown = new HashSet<>();
    private boolean ticketsHeld = true;
    private final List<Excavation> trench = new ArrayList<>();
    private int trenchSegments;
    @Nullable
    private Excavation crater;
    @Nullable
    private BlockPos spared;
    private boolean heartPlaced;
    private int craterDoneAt = -1;
    private boolean raysPainted;
    private double ploughedBefore;
    private double blastBefore;

    public HalleyStrike(ServerLevel level, HalleyPlan plan, ServerPlayer caster) {
        this.level = level;
        this.plan = plan;
        this.params = plan.params;
        this.casterId = caster.getUUID();
        this.tuning = HalleyConfig.tuning();
        this.holdChunks();
        HalleyAddon.LOG.debug("SS-05 called by {}: mark {}, touchdown {}, coming in from {}", caster.getScoreboardName(),
            plan.target, plan.touchdown, plan.touchdown.add(plan.incoming.scale(HalleyPlan.START_DISTANCE)));
    }

    public HalleyPlan plan() {
        return this.plan;
    }

    // ---- Chunk tickets: keep everything the strike touches loaded while it runs. ---------------------------------

    private void holdChunks() {
        Vec3 t = this.plan.target;
        int craterRadius = (int) Math.ceil((this.params.craterRadius() * (this.params.rays() ? 2.4 : 1.3) + 16.0) / 16.0);
        this.ticket(t.x, t.z, craterRadius);
        int steps = Math.max(1, (int) Math.ceil(this.params.trenchLength() / 48.0));
        int lineRadius = Math.max(2, (int) Math.ceil((this.params.trenchWidth() * 0.5 + 24.0) / 16.0));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = this.plan.groundPoint(this.params.trenchLength() * (double) i / steps);
            this.ticket(p.x, p.z, lineRadius);
        }
        for (int[] ticket : this.tickets) {
            ChunkPos at = new ChunkPos(ticket[0], ticket[1]);
            this.level.getChunkSource().addRegionTicket(TICKET, at, ticket[2], at);
        }
    }

    private void ticket(double x, double z, int radius) {
        this.tickets.add(new int[]{Mth.floor(x) >> 4, Mth.floor(z) >> 4, radius});
    }

    private void releaseChunks() {
        if (this.ticketsHeld) {
            this.ticketsHeld = false;
            for (int[] ticket : this.tickets) {
                ChunkPos at = new ChunkPos(ticket[0], ticket[1]);
                this.level.getChunkSource().removeRegionTicket(TICKET, at, ticket[2], at);
            }
        }
    }

    // ---- The timeline. --------------------------------------------------------------------------------------------

    public void tick(int t, @Nullable ServerPlayer caster) {
        if (caster != null && t % 10 == 0) {
            StarBridge.ward(caster);
        }
        if (t == HalleyPlan.EVAC && caster != null) {
            this.evacuate(caster);
        }
        if (t == HalleyPlan.TOUCHDOWN - 1 && caster != null && this.spared == null && this.evacuates(caster)
                && this.plan.inScar(caster.getX(), caster.getZ(), 4.0)) {
            // A creative caster still standing in the scar keeps the ground under their feet.
            this.spared = BlockPos.containing(caster.getX(), caster.getY() - 1.0, caster.getZ());
        }
        if (t == HalleyPlan.TOUCHDOWN) {
            this.touchdown(caster);
        }
        if (t > HalleyPlan.TOUCHDOWN && t <= this.plan.impact) {
            this.plough(t, caster);
        }
        if (t == this.plan.impact) {
            this.detonate(caster);
        }
        if (t > this.plan.impact && t <= this.plan.impact + BLAST_TICKS) {
            this.blast(t, caster);
        }

        if (this.crater != null && !this.crater.done()) {
            this.crater.tick();
        }
        if (this.crater != null && this.crater.done() && !this.raysPainted) {
            this.raysPainted = true;
            this.craterDoneAt = t;
            if (this.params.rays()) {
                this.paintRays();
            }
        }
        if (this.craterDoneAt >= 0) {
            for (int after : RESEND_AFTER) {
                if (t == this.craterDoneAt + after) {
                    this.resendScar();
                }
            }
        }
        if (this.ticketsHeld && t > this.plan.impact + 40 && this.allCut()) {
            this.releaseChunks();
        }
    }

    public void end() {
        this.cutTrenchUpTo(Double.MAX_VALUE);
        for (Excavation cut : this.trench) {
            cut.finish();
        }
        if (this.crater != null) {
            this.crater.finish();
        }
        if (this.crater != null && !this.heartPlaced && this.params.heart()) {
            this.placeHeart();
        }
        this.releaseChunks();
    }

    private boolean allCut() {
        for (Excavation cut : this.trench) {
            if (!cut.done()) {
                return false;
            }
        }
        return this.crater == null || this.crater.done();
    }

    // ---- Evacuation. ----------------------------------------------------------------------------------------------

    private boolean evacuates(ServerPlayer caster) {
        return this.tuning.evacuateCreative() && (caster.isCreative() || caster.isSpectator());
    }

    private void evacuate(ServerPlayer caster) {
        if (!this.evacuates(caster)) {
            return;
        }
        double zone = this.params.craterRadius() + 20.0;
        if (Math.hypot(caster.getX() - this.plan.target.x, caster.getZ() - this.plan.target.z) >= zone) {
            return;
        }
        Vec3 from = caster.position();
        if (StarBridge.liftClear(caster, this.plan.target, zone, zone + 36.0, this.plan.duration - HalleyPlan.EVAC)) {
            for (Vec3 at : new Vec3[]{from, caster.position()}) {
                this.level.playSound(null, at.x, at.y, at.z, HalleyContent.EVAC.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            }
            caster.connection.send(new ClientboundSetTitlesAnimationPacket(2, 30, 12));
            caster.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("EVAC // CLEAR OF THE IMPACT ZONE").withStyle(style -> style.withColor(EVAC_COLOR))));
            caster.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        } else {
            this.spared = BlockPos.containing(caster.getX(), caster.getY() - 1.0, caster.getZ());
        }
    }

    // ---- Touchdown and the plough. --------------------------------------------------------------------------------

    private void touchdown(@Nullable ServerPlayer caster) {
        this.cutTrenchUpTo(12.0);
        // the touchdown itself: a burst as wide as the trench's end
        Vec3 p = this.plan.touchdown;
        double r = this.plan.trenchHalfWidth(this.params.trenchLength()) + 6.0;
        for (Entity entity : this.entitiesIn(new AABB(p.x - r, p.y - 24.0, p.z - r, p.x + r, this.top(), p.z + r))) {
            if (Math.hypot(entity.getX() - p.x, entity.getZ() - p.z) <= r) {
                this.hit(entity, caster, true);
            }
        }
    }

    private void plough(int t, @Nullable ServerPlayer caster) {
        double ploughed = this.plan.ploughed(t);
        this.cutTrenchUpTo(ploughed + 12.0);

        // everything in the trench between where the nucleus was last tick and where it is now
        double fromAlong = this.params.trenchLength() - ploughed - 4.0;
        double toAlong = this.params.trenchLength() - this.ploughedBefore + 6.0;
        this.ploughedBefore = ploughed;
        Vec3 a = this.plan.groundPoint(Mth.clamp(fromAlong, 0.0, this.params.trenchLength()));
        Vec3 b = this.plan.groundPoint(Mth.clamp(toAlong, 0.0, this.params.trenchLength()));
        double pad = this.params.trenchWidth() * 0.5 + 26.0;
        AABB box = new AABB(Math.min(a.x, b.x) - pad, this.level.getMinBuildHeight(), Math.min(a.z, b.z) - pad,
            Math.max(a.x, b.x) + pad, this.top(), Math.max(a.z, b.z) + pad);
        double headAlong = this.plan.frontAlong(t);
        Vec3 head = this.plan.comet(t);

        for (Entity entity : this.entitiesIn(box)) {
            double along = this.plan.along(entity.getX(), entity.getZ());
            double cross = this.plan.trenchCross(entity.getX(), entity.getZ());
            if (along >= fromAlong && along <= toAlong && cross <= 1.15
                    && entity.getY() > this.plan.groundLine(along) - this.params.trenchDepth() - 10.0) {
                this.hit(entity, caster, true);
            } else if (cross > 1.15 && cross < 2.6 && Math.abs(along - headAlong) < 18.0 && this.wakeThrown.add(entity.getId())) {
                // the wake: thrown clear of the trench, sideways and up, and frosted
                double lat = this.plan.across(entity.getX(), entity.getZ());
                Vec3 away = this.plan.side.scale(Math.signum(lat == 0.0 ? 1.0 : lat));
                double k = Mth.clamp(1.0 - (cross - 1.15) / 1.45, 0.0, 1.0);
                this.throwAndHurt(entity, caster, away.add(this.plan.dir.scale(-0.4)).normalize(), 0.9 + 1.6 * k,
                    0.5 + 0.7 * k, 3.0F + 10.0F * (float) (k * k), head);
            }
        }
    }

    /**
     * Starts cutting every trench piece the nucleus has reached ({@code ploughed} blocks from the touchdown point,
     * counting the rounded end beyond it), and keeps cutting the ones already started.
     */
    private void cutTrenchUpTo(double ploughed) {
        if (this.params.carve()) {
            int minY = this.level.getMinBuildHeight();
            double end = this.plan.trenchHalfWidth(this.params.trenchLength());
            double total = this.params.trenchLength() + end;
            int pieces = (int) Math.ceil(total / SEGMENT);
            while (this.trenchSegments < pieces && this.trenchSegments * SEGMENT - end <= ploughed) {
                double far = this.params.trenchLength() + end - this.trenchSegments * SEGMENT;
                double near = Math.max(0.0, far - SEGMENT);
                double middle = (far + near) * 0.5;
                double radius = (far - near) * 0.5 + this.plan.trenchHalfWidth(near) * 1.12 + 3.0;
                this.trench.add(StarBridge.excavate(this.level, this.plan.groundPoint(middle), radius,
                    (x, z) -> this.plan.trenchFloor(x, z, minY), this::trenchSurface, this.spared,
                    "SS-05 Halley trench " + (this.trenchSegments + 1) + "/" + pieces, TRENCH_CHUNKS_PER_TICK));
                this.trenchSegments++;
            }
        }
        for (Excavation cut : this.trench) {
            if (!cut.done()) {
                cut.tick();
            }
        }
    }

    private BlockState trenchSurface(int x, int y, int z, BlockState was) {
        if (!was.getFluidState().isEmpty() || was.is(HalleyContent.COMET_TRAIL.get()) || was.is(HalleyContent.COMET_HEART.get())) {
            return null;
        }
        double lat = Math.abs(this.plan.across(x + 0.5, z + 0.5));
        if (this.params.heart() && lat < 1.6) {
            return HalleyContent.COMET_TRAIL.get().defaultBlockState();
        }
        double cross = this.plan.trenchCross(x + 0.5, z + 0.5);
        double roll = this.plan.hash(x * 3 + 1, z * 5 - 2);
        return (cross < 0.45 ? TRENCH_CORE : cross < 0.8 ? TRENCH_SLOPE : TRENCH_LIP).pick(roll);
    }

    // ---- Impact. --------------------------------------------------------------------------------------------------

    private void detonate(@Nullable ServerPlayer caster) {
        this.cutTrenchUpTo(Double.MAX_VALUE);
        if (this.params.carve()) {
            int minY = this.level.getMinBuildHeight();
            this.crater = StarBridge.excavate(this.level, this.plan.target, this.params.craterRadius() * 1.22 + 10.0,
                (x, z) -> this.plan.craterFloor(x, z, minY), this::craterSurface, this.spared,
                "SS-05 Halley crater", CRATER_CHUNKS_PER_TICK);
            // the middle first, so the heart has its floor before it is set down
            this.crater.tick();
            this.crater.tick();
            if (this.params.heart()) {
                this.placeHeart();
            }
        }

        Vec3 t = this.plan.target;
        double r = this.params.craterRadius() * 1.22 + 4.0;
        double below = t.y - this.params.craterDepth() - 16.0;
        for (Entity entity : this.entitiesIn(new AABB(t.x - r, below, t.z - r, t.x + r, this.top(), t.z + r))) {
            if (this.plan.craterK(entity.getX(), entity.getZ()) <= 1.06) {
                this.hit(entity, caster, true);
            }
        }
        this.blastBefore = this.params.craterRadius();
    }

    private void blast(int t, @Nullable ServerPlayer caster) {
        double radius = this.params.craterRadius();
        double reach = radius * this.params.blastReach();
        double k = Math.pow((t - this.plan.impact) / (double) BLAST_TICKS, 0.6);
        double front = radius + (reach - radius) * k;
        double from = this.blastBefore;
        this.blastBefore = front;
        Vec3 c = this.plan.target;
        AABB box = new AABB(c.x - front, this.level.getMinBuildHeight(), c.z - front, c.x + front, this.top(), c.z + front);
        for (Entity entity : this.entitiesIn(box)) {
            double d = Math.hypot(entity.getX() - c.x, entity.getZ() - c.z);
            if (d > from && d <= front && this.blastThrown.add(entity.getId())) {
                double strength = 1.0 - (d - radius) / Math.max(1.0, reach - radius);
                Vec3 out = new Vec3(entity.getX() - c.x, 0.0, entity.getZ() - c.z);
                out = out.lengthSqr() < 1.0E-4 ? this.plan.dir : out.normalize();
                this.throwAndHurt(entity, caster, out, 1.0 + 2.6 * strength, 0.5 + 1.1 * strength,
                    (float) (4.0 + 24.0 * strength * strength), c);
            }
        }
    }

    private BlockState craterSurface(int x, int y, int z, BlockState was) {
        if (!was.getFluidState().isEmpty() || was.is(HalleyContent.COMET_HEART.get())) {
            return null;
        }
        double k = this.plan.craterK(x + 0.5, z + 0.5) + (this.plan.hash(x, z) - 0.5) * 0.16;
        double roll = this.plan.hash(z * 7 + 3, x * 11 - 5);
        return (k < 0.24 ? CRATER_CORE : k < 0.66 ? CRATER_BOWL : CRATER_RIM).pick(roll);
    }

    /** The nucleus, left standing in the crater floor: a cluster of glowing ice crystals. */
    private void placeHeart() {
        this.heartPlaced = true;
        Map<BlockPos, BlockState> out = new HashMap<>();
        BlockState ice = HalleyContent.COMET_HEART.get().defaultBlockState();
        Vec3 t = this.plan.target;
        int floor = this.plan.craterFloor(Mth.floor(t.x), Mth.floor(t.z), this.level.getMinBuildHeight());
        double baseX = Mth.floor(t.x) + 0.5;
        double baseZ = Mth.floor(t.z) + 0.5;
        double baseY = floor + 0.5;
        double size = Math.max(0.5, this.params.craterRadius() / 56.0);
        int spikes = 6 + (int) (this.plan.hash(5, 9) * 3.0);

        for (int s = -1; s < spikes; s++) {
            double yaw = s < 0 ? 0.0 : (s + this.plan.hash(s * 13, 7) * 0.5) / spikes * Math.PI * 2.0;
            double lean = s < 0 ? 0.08 : 0.42 + this.plan.hash(s, 31) * 0.55;
            double length = (s < 0 ? 26.0 : 9.0 + this.plan.hash(s * 3, 17) * 11.0) * size;
            double thick = (s < 0 ? 3.4 : 1.7 + this.plan.hash(s, 5) * 0.9) * size;
            double ax = Math.cos(yaw) * Math.sin(lean);
            double ay = Math.cos(lean);
            double az = Math.sin(yaw) * Math.sin(lean);
            for (double l = 0.0; l < length; l += 0.5) {
                double w = thick * Math.pow(1.0 - l / length, 0.8) + 0.45;
                double px = baseX + ax * l;
                double py = baseY + ay * l;
                double pz = baseZ + az * l;
                int r = (int) Math.ceil(w);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            // hexagonal prisms, roughly: a hex in xz, a box in y
                            double hx = Math.abs(dx);
                            double hz = Math.abs(dz);
                            if (Math.max(hx * 0.866 + hz * 0.5, hz) <= w && Math.abs(dy) <= w) {
                                out.put(BlockPos.containing(px + dx, py + dy, pz + dz), ice);
                            }
                        }
                    }
                }
            }
        }
        StarBridge.paint(this.level, out);
    }

    /** Rays of snow thrown out over the land, like the ejecta round a fresh crater on the Moon. */
    private void paintRays() {
        Map<BlockPos, BlockState> out = new HashMap<>();
        Vec3 c = this.plan.target;
        double radius = this.params.craterRadius();
        int rays = 12 + (int) (this.plan.hash(77, 3) * 6.0);
        for (int i = 0; i < rays; i++) {
            double angle = (i + this.plan.hash(i, 101) * 0.7) / rays * Math.PI * 2.0;
            double length = radius * (0.55 + this.plan.hash(i * 7, 13) * 0.75);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            for (double d = radius * 1.04; d < radius * 1.04 + length; d += 1.0) {
                double fade = 1.0 - (d - radius * 1.04) / length;
                double half = 0.6 + 2.4 * fade;
                for (double w = -half; w <= half; w += 1.0) {
                    int x = Mth.floor(c.x + cos * d - sin * w);
                    int z = Mth.floor(c.z + sin * d + cos * w);
                    if (this.plan.hash(x * 13, z * 17) > 0.25 + 0.6 * (1.0 - fade) || !this.level.hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }
                    BlockPos top = new BlockPos(x, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                    BlockState was = this.level.getBlockState(top);
                    if (was.isAir() || !was.getFluidState().isEmpty() || was.is(BlockTags.LEAVES) || !was.isCollisionShapeFullBlock(this.level, top)
                            || was.hasBlockEntity() || was.is(HalleyContent.COMET_HEART.get()) || was.is(HalleyContent.COMET_TRAIL.get())) {
                        continue;
                    }
                    out.put(top, this.plan.hash(x, z * 3) < 0.18 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState());
                }
            }
        }
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }

    /** Sends the scar's chunks, with their now settled light, to everyone who has them loaded. */
    private void resendScar() {
        ThreadedLevelLightEngine light = this.level.getChunkSource().getLightEngine();
        double margin = this.params.craterRadius() * 0.25 + 16.0;
        double reach = this.params.craterRadius() * 1.3 + 16.0;
        Vec3 t = this.plan.target;
        Vec3 far = this.plan.groundPoint(this.params.trenchLength() + this.plan.trenchHalfWidth(this.params.trenchLength()));
        int x0 = Mth.floor(Math.min(t.x - reach, far.x - reach)) >> 4;
        int x1 = Mth.floor(Math.max(t.x + reach, far.x + reach)) >> 4;
        int z0 = Mth.floor(Math.min(t.z - reach, far.z - reach)) >> 4;
        int z1 = Mth.floor(Math.max(t.z + reach, far.z + reach)) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!this.plan.inScar(cx * 16 + 8, cz * 16 + 8, margin)) {
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

    // ---- Hitting things. ------------------------------------------------------------------------------------------

    private List<Entity> entitiesIn(AABB box) {
        return this.level.getEntities((Entity) null, box, entity -> true);
    }

    private double top() {
        return this.level.getMaxBuildHeight() + 64.0;
    }

    /** Inside the zone: living things are erased (creative and spectator players excepted), loose things removed. */
    private void hit(Entity entity, @Nullable ServerPlayer caster, boolean loose) {
        if (entity instanceof LivingEntity living) {
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return;
            }
            if (living.isAlive()) {
                StarBridge.erase(this.level, living, living == caster ? null : caster);
            }
        } else if (loose && !(entity instanceof Player) && entity.getPassengers().isEmpty()
                && (caster == null || entity != caster.getVehicle()) && !entity.isRemoved()) {
            entity.discard();
        }
    }

    /** Outside the zone: thrown, hurt and frosted (players in creative are only thrown). */
    private void throwAndHurt(Entity entity, @Nullable ServerPlayer caster, Vec3 away, double push, double lift,
                              float damage, Vec3 from) {
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        entity.setDeltaMovement(entity.getDeltaMovement().add(away.scale(push)).add(0.0, lift, 0.0));
        entity.hurtMarked = true;
        if (entity instanceof LivingEntity living && (living == caster || caster == null || StarBridge.affects(living, caster))) {
            living.hurt(this.wake(living == caster ? null : caster), damage);
            living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + 100));
        }
    }

    private DamageSource wake(@Nullable Entity attacker) {
        return new DamageSource(this.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HalleyContent.WAKE),
            attacker, attacker);
    }

}
