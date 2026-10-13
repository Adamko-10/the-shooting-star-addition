package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.HalleyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame, before anything is drawn: the skills decide how this frame's sky looks (see HalleyClient.frameStart). */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void ss05halley$frameStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        HalleyClient.frameStart(deltaTracker);
    }
}
