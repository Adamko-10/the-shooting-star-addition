package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.FilmView;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.0: the game moves its sound listener to the camera; Stellar Convergence's split screen moves the camera to another
 * weapon for a frame now and then, which made the sounds cut in and out (Adam: "cuts sfx"). While the camera is on a part
 * that isn't the live one, the listener stays where it was ({@link FilmView#holdListener}).
 */
@Mixin(SoundManager.class)
abstract class SoundListenerMixin {
    @Inject(method = "updateSource", at = @At("HEAD"), cancellable = true, require = 0)
    private void ss05halley$holdListener(Camera camera, CallbackInfo ci) {
        if (FilmView.holdListener) {
            ci.cancel();
        }
    }
}
