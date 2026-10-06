package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Optional: while a shader pack is on, SS-05's dark sky is the pack's own night, so the client's sky clock is wound on
 * into the night and back during a strike ({@link HalleySky#dayTime}). Adds ClientLevel's own version of
 * {@code LevelTimeAccess.getTimeOfDay}, which otherwise comes from the interface: the same sum, with the wound clock.
 * Only what is drawn changes; the world's real time (on the server) never does.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelTimeMixin {
    public float getTimeOfDay(float partialTick) {
        ClientLevel self = (ClientLevel) (Object) this;
        return self.dimensionType().timeOfDay(HalleySky.dayTime(self.dayTime()));
    }
}
