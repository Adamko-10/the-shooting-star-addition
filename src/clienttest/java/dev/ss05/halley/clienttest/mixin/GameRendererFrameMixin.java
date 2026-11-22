package dev.ss05.halley.clienttest.mixin;

import dev.ss05.halley.clienttest.Harness;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Place the camera before rendering and read the framebuffer after all renderer-tail overlays have returned. */
@Mixin(Minecraft.class)
abstract class GameRendererFrameMixin {
    private static final String RENDER = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V";

    @Inject(method = "runTick(Z)V", at = @At(value = "INVOKE", target = RENDER))
    private void ss05clienttest$frameStart(boolean renderLevel, CallbackInfo ci) {
        Harness.onRenderTick(true);
    }

    @Inject(method = "runTick(Z)V", at = @At(value = "INVOKE", target = RENDER, shift = At.Shift.AFTER))
    private void ss05clienttest$frameEnd(boolean renderLevel, CallbackInfo ci) {
        Harness.onRenderTick(false);
    }
}
