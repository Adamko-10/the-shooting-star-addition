package dev.ss05.halley.clienttest.mixin;

import dev.ss05.halley.clienttest.Harness;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The harness's frame hooks (Fabric has no event round a whole frame): before it is drawn, and once it is. */
@Mixin(GameRenderer.class)
abstract class GameRendererFrameMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void ss05clienttest$frameStart(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Harness.onRenderTick(true);
    }

    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void ss05clienttest$frameEnd(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Harness.onRenderTick(false);
    }
}
