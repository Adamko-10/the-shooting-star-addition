package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.FilmPanels;
import dev.ss05.halley.client.HalleyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame, before anything is drawn: the skills decide how this frame's sky looks (see HalleyClient.frameStart). */
@Mixin(value = GameRenderer.class, priority = 5000)
abstract class GameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void ss05halley$frameStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        HalleyClient.frameStart(deltaTracker);
    }

    /**
     * Once a frame, after everything is drawn: a film's panels over the finished picture (FilmPanels.endOfFrame).
     * The high priority puts this after the other mods' end-of-frame hooks on the same method (The Shooting Star's own).
     */
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void ss05halley$frameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        FilmPanels.endOfFrame();
    }
}
