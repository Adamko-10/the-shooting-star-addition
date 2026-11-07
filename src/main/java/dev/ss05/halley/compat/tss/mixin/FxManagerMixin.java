package dev.ss05.halley.compat.tss.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: SS-05's overlay on the caster's film. The remote draws its film overlays for its own three skills only;
 * this adds SS-05's after them. And only the newest effect of each kind draws its overlays: the older ones are out of
 * the remote's list while it draws them, in both of its passes ({@code renderHud}, and {@code renderFilmHud}, which
 * calls each effect's {@code hud}: Gungnir's "KINETIC STRIKE INBOUND" countdown). If The Shooting Star changes these
 * methods, they are simply left alone.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.client.fx.FxManager", remap = false)
abstract class FxManagerMixin {
    @Inject(method = {"renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", "liiillli1l(Lnet/minecraft/client/gui/GuiGraphics;F)V"},
        at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$newestOnly(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.newestHudsOnly();
    }

    // Both passes: since 1.5 the mapping names them the wrong way round (same shape; by their bodies the film overlays
    // are drawn in "renderHud"), and per loader build. StarClientBridge draws ours once a frame, whichever runs.
    // The legacy film style returns early, so draw the overlay at every return.
    @Inject(method = {"renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", "liiillli1l(Lnet/minecraft/client/gui/GuiGraphics;F)V"},
        at = @At("RETURN"), require = 0, remap = false)
    private static void ss05halley$filmHud(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.renderFilmHud(graphics, partial);
    }

    @Inject(method = {"renderHud(Lnet/minecraft/client/gui/GuiGraphics;F)V", "liiillli1l(Lnet/minecraft/client/gui/GuiGraphics;F)V"},
        at = @At("RETURN"), require = 0, remap = false)
    private static void ss05halley$allBack(GuiGraphics graphics, float partial, CallbackInfo ci) {
        StarClientBridge.allHuds();
    }

    // the camera shake during a Stellar Convergence: capped, full or off (its plate's X)
    @Inject(method = "applyShake(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$convergenceShake(PoseStack pose, float partial, CallbackInfo ci) {
        if (StarClientBridge.holdShake()) {
            ci.cancel();
        }
    }
}
