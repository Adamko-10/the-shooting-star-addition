package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.HalleyClient;
import dev.ss05.halley.client.FilmPanels;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame, before anything is drawn: the skills decide how this frame's sky looks (see HalleyClient.frameStart). */
@Mixin(value = GameRenderer.class, priority = 900)
abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void ss05halley$frameStart(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        HalleyClient.frameStart(partialTicks);
    }

    /**
     * Once a frame, just before the GUI: Proxima's sky gate when a shader pack is on (it would be masked in the
     * world pass; ProximaCosmos.renderSkyLate, Fable's review B4).
     */
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void ss05halley$beforeGui(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        dev.ss05.halley.client.proxima.ProximaCosmos.renderSkyLate();
    }

    // After RimuLib and TSS draw their overlays; the harness captures the resulting frame afterward.
    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void ss05halley$filmPanels(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        FilmPanels.endOfFrame();
    }
}
