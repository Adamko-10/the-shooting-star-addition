package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The blocks Halley and Luna never break or cover: the block tag {@code shooting_star_addition:unbreakable} (bedrock,
 * command and structure blocks, end portals... by default; servers add their own with a data pack). Each is kept
 * where it is, as RimuLib's carvings keep their "cores" (they are one of them, see TssLink).
 */
public final class Protected {
    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, HalleyAddon.id("unbreakable"));

    private Protected() {
    }

    public static boolean is(BlockState state) {
        return state.is(TAG);
    }

    /**
     * Every protected block in a chunk and what it is (by {@link BlockPos#asLong}), or null if it has none, which is
     * almost always: told from each section's palette without looking at its blocks.
     */
    @Nullable
    public static Long2ObjectMap<BlockState> scan(LevelChunk chunk) {
        Long2ObjectMap<BlockState> out = null;
        LevelChunkSection[] sections = chunk.getSections();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(Protected::is)) {
                continue;
            }
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (is(state)) {
                            if (out == null) {
                                out = new Long2ObjectOpenHashMap<>();
                            }
                            out.put(BlockPos.asLong(x0 + x, y0 + y, z0 + z), state);
                        }
                    }
                }
            }
        }
        return out;
    }
}
