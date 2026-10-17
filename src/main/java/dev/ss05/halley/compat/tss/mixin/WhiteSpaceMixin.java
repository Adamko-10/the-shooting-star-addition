package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.client.magic.WhiteSpace;
import dev.ss05.halley.client.AdditionPanel;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
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
@Mixin(targets = "cyou.rimuru.shootingstardemo.client.magic.WhiteSpace", remap = false)
abstract class WhiteSpaceMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$panel(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (WhiteSpace.capturesInput()) {
            AdditionPanel.draw(graphics);
        }
    }

    // Minecraft 1.21.11's key(KeyEvent)V replaces the old key(III)V (press, release and repeat are separate methods
    // now; this one is presses only, like the old action == 1 check was).
    @Inject(method = "key(Lnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$panelKey(KeyEvent event, CallbackInfo ci) {
        // not while a skill's key is being rebound, and not one of the remote's own
        if (WhiteSpace.capturesInput() && !StarClientBridge.menuUses(event.key(), event.scancode()) && AdditionPanel.key(event.key())) {
            ci.cancel();
        }
    }
}
