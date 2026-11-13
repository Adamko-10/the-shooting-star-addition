package dev.ss05.halley.world;

import dev.ss05.halley.HalleyAddon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The blocks Halley, Luna and Proxima never break or cover: the block tag {@code shooting_star_addition:unbreakable}
 * (bedrock, command and structure blocks, end portals and gateways... by default; servers add their own with a data
 * pack). Each is kept where it is: our carvings keep them as RimuLib keeps a carving's "cores" (they are registered as
 * cores in TssLink), {@code TssLink.paint} drops writes onto them and {@code MoonBuild} puts them back after building a
 * chunk. The Shooting Star's own skills only spare them where that skill keeps cores itself (most don't).
 */
public final class Protected {
    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, HalleyAddon.id("unbreakable"));
    /**
     * The things (not living) a strike never deletes: the entity type tag {@code shooting_star_addition:never_removed}
     * (grave mods' corpses holding a dead player's items, Create's contraptions and trains by default; servers add
     * their own). Living things are still hurt as usual.
     */
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> ENTITIES =
        TagKey.create(Registries.ENTITY_TYPE, HalleyAddon.id("never_removed"));

    private Protected() {
    }

    /** Whether a strike may delete this loose thing (not in {@link #ENTITIES}). */
    public static boolean removable(net.minecraft.world.entity.Entity entity) {
        return !entity.getType().builtInRegistryHolder().is(ENTITIES);
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
