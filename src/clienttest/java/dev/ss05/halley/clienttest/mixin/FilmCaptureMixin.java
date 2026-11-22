package dev.ss05.halley.clienttest.mixin;

import dev.ss05.halley.clienttest.Harness;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class FilmCaptureMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void ss05test$captureFilm(DeltaTracker delta, boolean advanceGameTime, CallbackInfo ci) {
        Harness.captureFilmFrame(delta.getGameTimeDeltaPartialTick(true));
    }
}
