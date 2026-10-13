package dev.ss05.halley.clienttest.mixin;

import dev.ss05.halley.clienttest.Harness;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The harness's frame hooks (Fabric has no event round a whole frame): before it is drawn, and once it is. */
@Mixin(GameRenderer.class)
abstract class GameRendererFrameMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void ss05clienttest$frameStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Harness.onRenderFramePre();
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void ss05clienttest$frameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Harness.onRenderFrame();
    }
}
