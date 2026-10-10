package dev.ss05.halley.world;

import dev.ss05.halley.MoonPlan;
import dev.ss05.halley.compat.StarBridge;
import dev.ss05.halley.content.HalleyContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds SS-06's fallen moon out of moon cheese (and the one molten block at its heart), a few chunks per tick,
 * nearest the mark first, so a huge moon never freezes the server tick. Server side only. Unlike
 * {@link Excavation} (which only ever cuts blocks away above a floor), this one also places blocks, so it is its
 * own small class rather than another {@code StarBridge.excavate} shape; what to place comes straight from
 * {@link MoonPlan#inMoon}, so it is always the same sphere the client films.
 *
 * <p>It goes over the chunks twice: first only the outer {@link #SHELL} blocks, so the moon's whole surface is there
 * as early as possible (the client cross-fades its rendered moon into the blocks as they appear), then the inside.
 */
public final class MoonBuild {
    private final ServerLevel level;
    private final MoonPlan plan;
    private final int chunksPerTick;
    /** How thick the first pass (the surface) is, in blocks. */
    private static final double SHELL = 3.0;

    private final List<ChunkPos> order;
    private int next;
    /** 0 = the shell, 1 = the inside. */
    private int pass;

    public MoonBuild(ServerLevel level, MoonPlan plan, int chunksPerTick) {
        this.level = level;
        this.plan = plan;
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.order = footprint(plan);
    }

    /** Every chunk whose square touches the moon's horizontal footprint around {@code restCentre}, nearest the mark first. */
    private static List<ChunkPos> footprint(MoonPlan plan) {
        double r = plan.params.moonRadius();
        double cx = plan.restCentre.x;
        double cz = plan.restCentre.z;
        int cx0 = Mth.floor(cx - r) >> 4;
        int cx1 = Mth.floor(cx + r) >> 4;
        int cz0 = Mth.floor(cz - r) >> 4;
        int cz1 = Mth.floor(cz + r) >> 4;
        List<ChunkPos> all = new ArrayList<>();
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                double nx = Mth.clamp(cx, x * 16.0, x * 16.0 + 15.0);
                double nz = Mth.clamp(cz, z * 16.0, z * 16.0 + 15.0);
                if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= r * r) {
                    all.add(new ChunkPos(x, z));
                }
            }
        }
        all.sort(Comparator.comparingDouble(pos -> sqrDistToMark(pos, plan)));
        return all;
    }

    private static double sqrDistToMark(ChunkPos pos, MoonPlan plan) {
        double dx = pos.x() * 16.0 + 8.0 - plan.target.x;
        double dz = pos.z() * 16.0 + 8.0 - plan.target.z;
        return dx * dx + dz * dz;
    }

    public boolean done() {
        return this.pass > 1;
    }

    /** Whether the moon's whole surface has been built (the inside may still be filling). */
    public boolean shellDone() {
        return this.pass > 0;
    }

    public void tick() {
        this.build(this.chunksPerTick);
    }

    /** Builds everything that is left, right now (used when the spell is cut short). */
    public void finish() {
        this.build(Integer.MAX_VALUE);
    }

    private void build(int count) {
        while (count > 0 && !this.done()) {
            int end = Math.min(this.order.size(), this.next + count);
            count -= end - this.next;
            this.buildChunks(end, this.pass == 0);
            if (this.next >= this.order.size()) {
                this.pass++;
                this.next = 0;
            }
        }
    }

    private void buildChunks(int end, boolean shell) {
        double inner = Math.max(0.0, this.plan.params.moonRadius() - SHELL);
        int minY = this.level.getMinY();
        int maxY = this.level.getMaxY();
        double r = this.plan.params.moonRadius();
        BlockState cheese = HalleyContent.MOON_CHEESE.defaultBlockState();
        BlockState molten = HalleyContent.MOLTEN_MOON_CHEESE.defaultBlockState();
        boolean core = this.plan.params.core();
        Map<BlockPos, BlockState> out = new HashMap<>();

        for (int i = this.next; i < end; i++) {
            ChunkPos pos = this.order.get(i);
            int x0 = pos.x() * 16;
            int z0 = pos.z() * 16;
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx;
                double ex = x + 0.5 - this.plan.restCentre.x;
                double exSqr = ex * ex;
                if (exSqr > r * r) {
                    continue;
                }
                for (int dz = 0; dz < 16; dz++) {
                    int z = z0 + dz;
                    double ez = z + 0.5 - this.plan.restCentre.z;
                    double flatSqr = exSqr + ez * ez;
                    if (flatSqr > r * r) {
                        continue;
                    }
                    double half = Math.sqrt(r * r - flatSqr);
                    int yFrom = Math.max(minY, Mth.floor(this.plan.restCentre.y - half));
                    int yTo = Math.min(maxY, Mth.ceil(this.plan.restCentre.y + half));
                    for (int y = yFrom; y <= yTo; y++) {
                        if (!this.plan.inMoon(x, y, z)) {
                            continue;
                        }
                        // the shell pass takes the outer SHELL blocks, the second pass everything inside them
                        double d = this.plan.restCentre.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                        if (shell != d >= inner * inner) {
                            continue;
                        }
                        BlockPos at = new BlockPos(x, y, z);
                        out.put(at, core && at.equals(this.plan.core) ? molten : cheese);
                    }
                }
            }
        }
        this.next = end;
        if (!out.isEmpty()) {
            StarBridge.paint(this.level, out);
        }
    }
}
