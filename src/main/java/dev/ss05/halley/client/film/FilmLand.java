package dev.ss05.halley.client.film;

import dev.ss05.halley.client.render.ShaderPacks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a film's camera where there is something to see: this game only has the land the server has sent it round the
 * player (their view distance), so a camera placed further out than that films an empty void, and one placed inside a
 * hill films the dark. The first is drawn back toward the player until it is over loaded land (with a chunk to spare,
 * so the edge isn't in shot), the second lifted out of the ground.
 */
public final class FilmLand {
    private FilmLand() {
    }

    public static Vec3 keep(Vec3 pos, Vec3 home) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return pos;
        }
        // ClientLevel.hasChunk always returns true in 1.21.1. Only the cache knows what actually arrived.
        ClientChunkCache chunks = level.getChunkSource();
        // The cache has a padding of chunks beyond the player's render grid. A detached camera there can see only sky.
        Vec3 viewer = minecraft.player != null ? minecraft.player.position() : home;
        int viewRadius = minecraft.options.getEffectiveRenderDistance() - 1;
        Vec3 at = pos;
        for (int i = 0; !loadedAround(chunks, at, viewer, viewRadius); i++) {
            if (i == 16) {
                at = viewer;
                break;
            }
            at = at.lerp(viewer, 0.12);
        }
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        BlockPos block = BlockPos.containing(at);
        if (chunks.hasChunk(x >> 4, z >> 4) && !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
            double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            at = new Vec3(at.x, Math.max(at.y, ground + 1.6), at.z);
        }
        // 1.9.0: under water (seen on a coast): just over its surface instead
        BlockPos wet = BlockPos.containing(at);
        if (chunks.hasChunk(x >> 4, z >> 4) && !level.getFluidState(wet).isEmpty()) {
            at = new Vec3(at.x, Math.max(at.y, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1.2), at.z);
        }
        // 1.9.0: in the air under a tree's crown, or right beside one, the leaves fill the shot (seen in both films):
        // over the canopy instead
        double canopy = Double.NEGATIVE_INFINITY;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                int cx = Mth.floor(at.x) + dx;
                int cz = Mth.floor(at.z) + dz;
                if (!chunks.hasChunk(cx >> 4, cz >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
                if (level.getBlockState(new BlockPos(cx, top - 1, cz)).is(BlockTags.LEAVES)) {
                    canopy = Math.max(canopy, top);
                }
            }
        }
        if (at.y < canopy + 1.5) {
            at = new Vec3(at.x, canopy + 2.2, at.z);
        }
        return at;
    }

    /**
     * The height of the cloud layer here, or NaN where there are none (another dimension, or clouds switched off without
     * a shader pack: a pack draws its own clouds at about the game's height whatever the game's cloud setting says).
     */
    public static double cloudHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ShaderPacks.active() && minecraft.options.getCloudsType() == CloudStatus.OFF) {
            return Double.NaN;
        }
        return minecraft.level.effects().getCloudHeight();
    }

    /** Whether the chunk under the camera and the ones next to it are loaded. */
    private static boolean loadedAround(ClientChunkCache chunks, Vec3 at, Vec3 viewer, int viewRadius) {
        int cx = Mth.floor(at.x) >> 4;
        int cz = Mth.floor(at.z) >> 4;
        return Math.abs(cx - (Mth.floor(viewer.x) >> 4)) <= viewRadius
            && Math.abs(cz - (Mth.floor(viewer.z) >> 4)) <= viewRadius
            && chunks.hasChunk(cx, cz) && chunks.hasChunk(cx + 1, cz) && chunks.hasChunk(cx - 1, cz)
            && chunks.hasChunk(cx, cz + 1) && chunks.hasChunk(cx, cz - 1);
    }
}
