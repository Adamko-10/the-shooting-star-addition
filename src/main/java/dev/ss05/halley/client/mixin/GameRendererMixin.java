package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.FilmPanels;
import dev.ss05.halley.client.FilmSplit;
import dev.ss05.halley.client.FilmView;
import dev.ss05.halley.client.HalleyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame, before Minecraft gathers up what it is about to draw: SS-05 decides how this frame's sky looks. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void ss05halley$frameStart(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        HalleyClient.frameStart(deltaTracker);
    }

    /**
     * Once the world and the screen effects are drawn, just before the GUI (1.9.0): Stellar Convergence's split screen
     * copies the parts it keeps (without the split drawn over them), and its per-part filters come off (main does that
     * before the HUD, which 26.3 gathers before the world is drawn).
     */
    @Inject(method = "render()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void ss05halley$beforeGui(CallbackInfo ci) {
        FilmSplit.copyPending();
        FilmView.runBeforeGui();
    }

    /**
     * At the very end of the frame (the world, the screen effects and the GUI drawn): Stellar Convergence's film panels
     * copy the shots they freeze, as the player saw them (see FilmPanels).
     */
    @Inject(method = "render()V", at = @At("TAIL"))
    private void ss05halley$frameEnd(CallbackInfo ci) {
        FilmPanels.copyPending();
    }
}
