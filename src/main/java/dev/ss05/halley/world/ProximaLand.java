package dev.ss05.halley.world;

import dev.ss05.halley.ProximaPlan;
import dev.ss05.halley.compat.StarBridge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Proxima's scorch round the bowl, chunk by chunk: out to {@code scorchRadius} leaves, grass, flowers, snow and crops
 * burn away and the topsoil turns to coarse dirt; out to {@code charRadius} trunks char to basalt and the ground to
 * blackstone, basalt and coarse dirt. Buildings stand (only the bowl takes everything). Only loaded land is touched,
 * never loaded for it: the rest is done as it loads ({@link ProximaStrike#tickLingering}). Each chunk is written once,
 * through the remote's carver (relit and resent once; protected blocks never written).
 */
final class ProximaLand {
    /** Server-thread time a tick may spend here. */
    private static final long BUDGET_NS = 12_000_000L;
    /** How long a scorch waits for its land to load, after the strike: 20 minutes. */
    private static final long LINGER = 24_000L;

    private final ServerLevel level;
    private final ProximaPlan plan;
    private final ArrayDeque<ChunkPos> todo = new ArrayDeque<>();
    private final List<ChunkPos> waiting = new ArrayList<>();
    private final long madeAt;
    private int sinceSweep;

    ProximaLand(ServerLevel level, ProximaPlan plan) {
        this.level = level;
        this.plan = plan;
        this.madeAt = level.getGameTime();
        double reach = plan.params.scorchRadius() * 1.12 + 1.0;
        int r = (int) Math.ceil(reach / 16.0) + 1;
        ChunkPos middle = new ChunkPos(BlockPos.containing(plan.target));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkPos pos = new ChunkPos(middle.x + dx, middle.z + dz);
                if (nearest(pos) <= reach) {
                    chunks.add(pos);
                }
            }
        }
        // nearest the mark first
        chunks.sort(Comparator.comparingDouble(this::nearest));
        this.todo.addAll(chunks);
    }

    private double nearest(ChunkPos pos) {
        double x = Math.max(pos.getMinBlockX(), Math.min(this.plan.target.x, pos.getMaxBlockX() + 1));
        double z = Math.max(pos.getMinBlockZ(), Math.min(this.plan.target.z, pos.getMaxBlockZ() + 1));
        return this.plan.horizontal(x, z);
    }

    /** Scorches loaded chunks for up to {@link #BUDGET_NS}; looks again at the unloaded ones once a second. */
    void tick() {
        if (this.todo.isEmpty() && !this.waiting.isEmpty() && ++this.sinceSweep >= 20) {
            this.sinceSweep = 0;
            this.todo.addAll(this.waiting);
            this.waiting.clear();
        }
        long deadline = System.nanoTime() + BUDGET_NS;
        while (!this.todo.isEmpty() && System.nanoTime() < deadline) {
            ChunkPos pos = this.todo.poll();
            if (this.level.hasChunk(pos.x, pos.z)) {
                this.scorch(pos);
            } else {
                this.waiting.add(pos);
            }
        }
    }

    /** Every loaded chunk is done (the others wait for their land to load). */
    boolean loadedDone() {
        return this.todo.isEmpty();
    }

    boolean finished() {
        return this.todo.isEmpty() && this.waiting.isEmpty() || this.level.getGameTime() - this.madeAt > LINGER;
    }

    private void scorch(ChunkPos pos) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        double bowl = this.plan.params.bowlRadius();
        int minY = this.level.getMinBuildHeight();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                double r = this.plan.horizontal(x + 0.5, z + 0.5);
                double edge = this.plan.edge(x + 0.5, z + 0.5);
                double scorch = this.plan.params.scorchRadius() * edge;
                // the bowl's own carving takes its middle; the outer edge frays
                if (r < bowl - 1.0 || r >= scorch || r > scorch - 6.0 && this.plan.noise(x, 0, z) < (r - scorch + 6.0) / 6.0) {
                    continue;
                }
                boolean charred = r < this.plan.params.charRadius() * edge;
                int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = top; y > Math.max(minY, top - 48); y--) {
                    at.set(x, y, z);
                    BlockState was = this.level.getBlockState(at);
                    if (was.isAir()) {
                        continue;
                    }
                    if (!was.getFluidState().isEmpty()) {
                        // water and lava: left alone (the steam is only seen)
                        break;
                    }
                    if (burnsAway(was)) {
                        blocks.put(at.immutable(), Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if (was.is(BlockTags.LOGS)) {
                        if (charred) {
                            BlockState basalt = Blocks.BASALT.defaultBlockState();
                            if (was.hasProperty(RotatedPillarBlock.AXIS)) {
                                basalt = basalt.setValue(RotatedPillarBlock.AXIS, was.getValue(RotatedPillarBlock.AXIS));
                            }
                            blocks.put(at.immutable(), basalt);
                        }
                        continue;
                    }
                    // the ground (or a roof: buildings keep theirs)
                    BlockState ground = this.ground(was, x, y, z, charred, r);
                    if (ground != null) {
                        blocks.put(at.immutable(), ground);
                        if (charred && this.level.getBlockState(at.below()).is(BlockTags.DIRT)) {
                            blocks.put(at.below().immutable(), Blocks.COARSE_DIRT.defaultBlockState());
                        }
                    }
                    break;
                }
            }
        }
        if (!blocks.isEmpty()) {
            StarBridge.paint(this.level, blocks);
        }
    }

    /** Leaves, plants, flowers, crops, vines and snow: burnt away by the flare. */
    private static boolean burnsAway(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof BushBlock || state.getBlock() instanceof VineBlock
            || state.is(BlockTags.REPLACEABLE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    /** What the top of the ground becomes, or null to leave it. */
    private BlockState ground(BlockState was, int x, int y, int z, boolean charred, double r) {
        boolean soil = was.is(BlockTags.DIRT) || was.is(Blocks.DIRT_PATH) || was.is(Blocks.FARMLAND);
        float n = this.plan.noise(x, y, z);
        if (charred) {
            if (!soil && !was.is(BlockTags.SAND) && !was.is(Blocks.GRAVEL) && !was.is(BlockTags.BASE_STONE_OVERWORLD)) {
                return null;
            }
            // blacker toward the bowl
            float k = (float) ((r - this.plan.params.bowlRadius()) / Math.max(1.0, this.plan.params.charRadius() - this.plan.params.bowlRadius()));
            return n < 0.45F - 0.25F * k ? Blocks.BLACKSTONE.defaultBlockState()
                : n < 0.7F - 0.2F * k ? Blocks.BASALT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (!soil || was.is(Blocks.COARSE_DIRT)) {
            return null;
        }
        // scorched toward the middle, only browned at the edge
        float k = (float) ((r - this.plan.params.charRadius()) / Math.max(1.0, this.plan.params.scorchRadius() - this.plan.params.charRadius()));
        return n < 0.85F - 0.6F * k ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }
}
