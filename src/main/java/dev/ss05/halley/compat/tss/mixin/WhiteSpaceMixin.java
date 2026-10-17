package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.mc1211.client.magic.WhiteSpace;
import dev.ss05.halley.client.AdditionPanel;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: the addon's plate in the Stellar Remote's menu (H, The Shooting Star's "White Space"), drawn over the menu
 * while it is open, and its two keys (M, C) while it is open ({@link AdditionPanel}). If The Shooting Star changes these
 * methods, the plate or its keys are simply missing.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.client.magic.WhiteSpace", remap = false)
abstract class WhiteSpaceMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$panel(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (WhiteSpace.capturesInput()) {
            AdditionPanel.draw(graphics);
        }
    }

    @Inject(method = "key(III)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$panelKey(int key, int scancode, int action, CallbackInfo ci) {
        // a press (not a release or a repeat), not while a skill's key is being rebound, and not one of the remote's own
        if (action == 1 && WhiteSpace.capturesInput() && !StarClientBridge.menuUses(key, scancode) && AdditionPanel.key(key)) {
            ci.cancel();
        }
    }
}
