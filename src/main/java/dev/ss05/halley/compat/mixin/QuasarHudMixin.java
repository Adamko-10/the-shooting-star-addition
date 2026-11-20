package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.FilmView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.0: SS-21 Quasar (another author's addon, optional) draws its strike's space scene over the whole screen from the
 * start of the HUD, which covered Stellar Convergence's film when Quasar was one of its weapons (Adam's polish 9
 * screenshot). While our film plays ({@link FilmView#filmPlaying}) that overlay is skipped; Quasar's strike itself goes
 * on. Applies only when SS-21 Quasar is installed (a pseudo target); nothing of Quasar's is used.
 */
@Pseudo
@Mixin(targets = "dev.ss21.quasar.mc1211.client.QuasarHud", remap = false)
abstract class QuasarHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true, require = 0,
        remap = false)
    private static void ss05halley$keepOutOfFilm(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (FilmView.filmPlaying) {
            ci.cancel();
        }
    }
}
