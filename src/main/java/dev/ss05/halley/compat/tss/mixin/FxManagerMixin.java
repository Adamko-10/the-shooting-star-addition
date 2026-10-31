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
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1201.client.fx.FxManager", remap = false)
abstract class FxManagerMixin {
    // "renderFilmHud" is obfuscated and unmapped in The Shooting Star 1.5.0: javap/decompiling the readable jar
    // (build/shooting-star-named) found it as the one method of this shape that switches on instanceof
    // StarFx/GungnirFx/SevenStarsFx/<Seven Stars' finale type>, matching this method's own job (the built-in skills'
    // film overlays, by type, not the polymorphic per-effect hud() dispatch the other same-shaped method does).
    @Inject(method = "liiillli1l(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$filmHud(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.renderFilmHud(graphics, partial);
    }
}
