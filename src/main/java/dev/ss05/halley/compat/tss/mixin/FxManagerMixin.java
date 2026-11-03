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
 * this adds SS-05's after them. And only the newest effect of each kind draws its overlay (the older ones are out of
 * the remote's list while it draws them). If The Shooting Star changes these methods, they are simply left alone.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.client.fx.FxManager", remap = false)
abstract class FxManagerMixin {
    @Inject(method = "renderFilmHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$filmHud(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.renderFilmHud(graphics, partial);
    }

    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$newestOnly(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.newestHudsOnly();
    }

    @Inject(method = "renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("RETURN"), require = 0, remap = false)
    private static void ss05halley$allBack(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.allHuds();
    }
}
