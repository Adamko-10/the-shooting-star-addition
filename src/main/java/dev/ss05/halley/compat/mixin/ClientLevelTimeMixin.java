package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Optional: while a shader pack is on, the dark sky of a strike is the pack's own night, so the client's sky clock is
 * wound on into the night and back during a strike ({@link HalleySky#dayTime}). Gives ClientLevel its own version of
 * {@code LevelTimeAccess.getTimeOfDay}, which otherwise comes from the interface: the same sum, with the wound clock.
 * Only what is drawn changes; the world's real time (on the server) never does.
 *
 * <p>It extends {@code Level} (as ClientLevel does) so the build knows the method overrides Minecraft's own and gives
 * it Minecraft's name in the release jar; the constructor is never used.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelTimeMixin extends Level {
    protected ClientLevelTimeMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess,
                                   Holder<DimensionType> dimensionType, Supplier<ProfilerFiller> profiler, boolean isClientSide,
                                   boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionType, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Override
    public float getTimeOfDay(float partialTick) {
        return this.dimensionType().timeOfDay(HalleySky.dayTime(this.dayTime()));
    }
}
