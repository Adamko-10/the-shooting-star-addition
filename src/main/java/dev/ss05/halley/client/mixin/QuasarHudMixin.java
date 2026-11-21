package dev.ss05.halley.client.mixin;

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
// Fabric 1.21.11: dev.ss21.quasar.client.QuasarHud here (no version tag), static render(GuiGraphics, float) as on main
@Mixin(targets = "dev.ss21.quasar.client.QuasarHud", remap = false)
abstract class QuasarHudMixin {
    // by name only: SS-21 Quasar's Fabric jar is in intermediary names (its GuiGraphics is class_332 in a player's game),
    // and a remap = false descriptor isn't mapped; QuasarHud has one render method, render(GuiGraphics, float)
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$keepOutOfFilm(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (FilmView.filmPlaying) {
            ci.cancel();
        }
    }
}
