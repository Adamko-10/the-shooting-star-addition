package dev.ss05.halley.compat.tss.mixin;

import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: SS-05's overlay on the caster's film. The remote draws its film overlays for its own three skills only;
 * this adds SS-05's after them. If The Shooting Star changes this method, the overlay is simply missing.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.client.fx.FxManager", remap = false)
abstract class FxManagerMixin {
    // TSS 1.5.0 renamed this to renderHud (films themselves moved to RimuLib; checked via the 1.5.0 mapping, §5).
    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$filmHud(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.renderFilmHud(graphics, partial);
    }
}
