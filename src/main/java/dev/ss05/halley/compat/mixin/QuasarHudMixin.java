package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.FilmView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.0: SS-21 Quasar (another author's addon, optional) draws its strike's HUD (countdown, its film's titles) from the
 * start of the HUD, over Stellar Convergence's film when Quasar is one of its weapons. While our film plays
 * ({@link FilmView#filmPlaying}) that overlay is skipped; Quasar's strike itself goes on (its space shot: QuasarFxMixin).
 * Applies only when SS-21 Quasar is installed (a pseudo target); nothing of Quasar's is used.
 */
@Pseudo
@Mixin(targets = "dev.ss21.quasar.client.QuasarHud", remap = false)
abstract class QuasarHudMixin {
    // Quasar has a single render method. Its GuiGraphics descriptor changes when Loom remaps Minecraft.
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0,
        remap = false)
    private static void ss05halley$keepOutOfFilm(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (FilmView.filmPlaying) {
            ci.cancel();
        }
    }
}
