package dev.ss05.halley.client.film;

import dev.ss05.halley.client.render.ShaderPacks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
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
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return pos;
        }
        Vec3 at = pos;
        for (int i = 0; i < 16 && !loadedAround(level, at); i++) {
            at = at.lerp(home, 0.12);
        }
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        BlockPos block = BlockPos.containing(at);
        if (level.hasChunk(x >> 4, z >> 4) && !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
            double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            at = new Vec3(at.x, Math.max(at.y, ground + 1.6), at.z);
        }
        // 1.9.0: under water (seen on a coast): just over its surface instead
        BlockPos wet = BlockPos.containing(at);
        if (level.hasChunk(x >> 4, z >> 4) && !level.getFluidState(wet).isEmpty()) {
            at = new Vec3(at.x, Math.max(at.y, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1.2), at.z);
        }
        // 1.9.0: in the air under a tree's crown, or right beside one, the leaves fill the shot (seen in both films):
        // over the canopy instead
        double canopy = Double.NEGATIVE_INFINITY;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                int cx = Mth.floor(at.x) + dx;
                int cz = Mth.floor(at.z) + dz;
                if (!level.hasChunk(cx >> 4, cz >> 4)) {
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
        // 1.21.11 has no DimensionSpecialEffects cloud height: the camera's environment attribute instead
        return minecraft.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, 1.0F);
    }

    /** Whether the chunk under the camera and the ones next to it are loaded. */
    private static boolean loadedAround(ClientLevel level, Vec3 at) {
        int cx = Mth.floor(at.x) >> 4;
        int cz = Mth.floor(at.z) >> 4;
        return level.hasChunk(cx, cz) && level.hasChunk(cx + 1, cz) && level.hasChunk(cx - 1, cz)
            && level.hasChunk(cx, cz + 1) && level.hasChunk(cx, cz - 1);
    }
}
