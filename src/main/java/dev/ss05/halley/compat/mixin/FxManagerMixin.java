package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.compat.client.StarClientBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: SS-05's overlay on the caster's film. The remote draws its film overlays for its own skills only; this
 * adds SS-05's after them. If The Shooting Star changes this method, the overlay is simply missing.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.client.fx.FxManager", remap = false)
abstract class FxManagerMixin {
    @Inject(method = "renderFilmHud(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$filmHud(GuiGraphicsExtractor graphics, float partial, CallbackInfo ci) {
        StarClientBridge.renderFilmHud(graphics, partial);
    }
}
