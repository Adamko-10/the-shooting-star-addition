package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.FilmView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.9.0: SS-21 Quasar (another author's addon, optional) turns its strike's space shot on from how much of its own film
 * is showing ({@code filmWeight}): a full-screen post effect (dark space, its jet, a ring) that covered Stellar
 * Convergence's film whenever Quasar was one of its weapons (its film is held back but keeps running, so it thought it
 * was being filmed; Adam's polish 8 and 9 screenshots). While our film plays ({@link FilmView#filmPlaying}) Quasar is
 * told none of its film shows, so it draws its strike as seen from the world. Applies only when SS-21 Quasar is
 * installed (a pseudo target); nothing of Quasar's is used.
 */
@Pseudo
@Mixin(targets = "dev.ss21.quasar.client.QuasarFx", remap = false)
abstract class QuasarFxMixin {
    @Inject(method = "filmWeight()F", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ss05halley$notFilmed(CallbackInfoReturnable<Float> cir) {
        if (FilmView.filmPlaying) {
            cir.setReturnValue(0.0F);
        }
    }
}
