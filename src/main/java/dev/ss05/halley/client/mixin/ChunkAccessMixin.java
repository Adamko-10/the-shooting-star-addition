package dev.ss05.halley.client.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A chunk's saved block entities that haven't been loaded yet. Carving (ours and The Shooting Star's) replaces the
 * blocks but leaves these behind, so they fail noisily when something later loads them ("Tried to load a DUMMY block
 * entity ... found not block entity block ...", hundreds after a 10x moonfall); the strikes drop those whose block is gone.
 */
@Mixin(ChunkAccess.class)
public interface ChunkAccessMixin {
    @Accessor("pendingBlockEntities")
    Map<BlockPos, CompoundTag> ss05halley$pendingBlockEntities();
}
