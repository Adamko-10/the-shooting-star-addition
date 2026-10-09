package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Optional: while a shader pack is on, SS-05's dark sky is the pack's own night, so the client's sky clock is wound on
 * into the night and back during a strike ({@link HalleySky#dayTime}). Adds ClientLevel's own version of
 * {@code LevelTimeAccess.getTimeOfDay}, which otherwise comes from the interface: the same sum, with the wound clock.
 * Only what is drawn changes; the world's real time (on the server) never does.
 *
 * <p>Forge 1.20.1 runs with SRG names, and an added method is neither remapped by the refmap nor by reobfuscation
 * (this class doesn't extend ClientLevel). So the override is declared under both names: {@code getTimeOfDay} takes
 * effect in a dev run (Mojang names), {@code m_46942_} in a release game (SRG names); the other one is an unused extra.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelTimeMixin {
    public float getTimeOfDay(float partialTick) {
        return ss05halley$timeOfDay();
    }

    /** {@code LevelTimeAccess.getTimeOfDay(F)F} under its SRG name (see the mappings' {@code m_46942_}). */
    public float m_46942_(float partialTick) {
        return ss05halley$timeOfDay();
    }

    private float ss05halley$timeOfDay() {
        ClientLevel self = (ClientLevel) (Object) this;
        return self.dimensionType().timeOfDay(HalleySky.dayTime(self.dayTime()));
    }
}
