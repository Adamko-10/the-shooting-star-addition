package dev.ss05.halley.client.film;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
        return at;
    }

    /** Whether the chunk under the camera and the ones next to it are loaded. */
    private static boolean loadedAround(ClientLevel level, Vec3 at) {
        int cx = Mth.floor(at.x) >> 4;
        int cz = Mth.floor(at.z) >> 4;
        return level.hasChunk(cx, cz) && level.hasChunk(cx + 1, cz) && level.hasChunk(cx - 1, cz)
            && level.hasChunk(cx, cz + 1) && level.hasChunk(cx, cz - 1);
    }
}
