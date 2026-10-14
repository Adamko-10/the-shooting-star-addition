package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.client.magic.WhiteSpace;
import dev.ss05.halley.client.NoCooldownNote;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: the no-cooldown note in the Stellar Remote's menu (H, The Shooting Star's "White Space"), drawn over the
 * menu while it is open ({@link NoCooldownNote}). If The Shooting Star changes this method, the note is simply missing.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.client.magic.WhiteSpace", remap = false)
abstract class WhiteSpaceMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$noCooldownNote(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (WhiteSpace.capturesInput()) {
            NoCooldownNote.draw(graphics);
        }
    }
}
