package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.FilmPanels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once a frame, the last thing the frame's GUI gathers before it is drawn (after the HUD and any screen, which a film
 * may cut short): Stellar Convergence's film panels (main draws them at the end of the frame, RenderFrameEvent.Post).
 */
@Mixin(ToastManager.class)
abstract class ToastManagerMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"))
    private void ss05halley$filmPanels(GuiGraphics graphics, CallbackInfo ci) {
        FilmPanels.endOfFrame(graphics);
    }
}
