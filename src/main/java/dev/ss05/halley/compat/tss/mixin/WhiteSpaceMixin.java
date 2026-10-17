package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.client.magic.WhiteSpace;
import dev.ss05.halley.client.AdditionPanel;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$panel(GuiGraphicsExtractor graphics, float partial, CallbackInfo ci) {
        if (WhiteSpace.capturesInput()) {
            AdditionPanel.draw(graphics);
        }
    }

    // 26.3's WhiteSpace.key takes a KeyEvent (key(), keycode()/shortcutKey(), modifiers()) instead of the GLFW
    // (key, scancode, action) triple other versions get; key() is the one vanilla's own KeyMapping.matches(KeyEvent)
    // compares against a binding's default, so that is what is compared against our own M/C (see AdditionPanel).
    @Inject(method = "key(Lnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$panelKey(KeyEvent event, CallbackInfo ci) {
        // not while a skill's key is being rebound, and not one of the remote's own
        if (WhiteSpace.capturesInput() && !StarClientBridge.menuUses(event) && AdditionPanel.key(event.key())) {
            ci.cancel();
        }
    }
}
