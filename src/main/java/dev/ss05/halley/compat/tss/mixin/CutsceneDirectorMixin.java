package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.rimulib.client.cinematic.Cutscene;
import dev.ss05.halley.compat.tss.client.ConvergenceClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: while the player's own Stellar Convergence is on its way, its weapons' films (The Shooting Star's, Halley's,
 * Luna's, other addons') don't play one after another; they are held for the convergence's own film. If RimuLib changes
 * this method, each weapon simply plays its own film.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.rimulib.client.cinematic.CutsceneDirector", remap = false)
abstract class CutsceneDirectorMixin {
    @Inject(method = "play(Lcyou/rimuru/rimulib/client/cinematic/Cutscene;)V", at = @At("HEAD"), cancellable = true,
        require = 0, remap = false)
    private static void ss05halley$holdForConvergence(Cutscene film, CallbackInfo ci) {
        if (ConvergenceClient.hold(film)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$runHeldFilms(CallbackInfo ci) {
        ConvergenceClient.tickHeld();
    }
}
