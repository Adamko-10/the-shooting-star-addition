package dev.ss05.halley.compat.tss.mixin;

import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.0, Adam's test ("still no title card", "proxima rn just zooms out and doesn't work"): TON 618 (rimuru's mod)
 * cancels the whole of the game's HUD at its start while a RimuLib film plays, and our films' overlays (captions, title
 * cards, Proxima's space and Earth shots) are drawn inside it, through The Shooting Star's overlay pass. So around the
 * game's HUD call: if our overlay wasn't drawn this frame, it is drawn right after, on the same layer (as TON 618
 * Addition's HudRescue does for its own film). See {@link StarClientBridge#hudRescue}.
 */
@Mixin(GameRenderer.class)
abstract class HudRescueMixin {
    private static final String GUI_RENDER =
        "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V";

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE", target = GUI_RENDER, remap = false), require = 0, remap = false)
    private void ss05halley$beforeHud(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        StarClientBridge.beforeHud();
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE", target = GUI_RENDER, shift = At.Shift.AFTER, remap = false), require = 0, remap = false)
    private void ss05halley$afterHud(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        StarClientBridge.hudRescue(new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()),
            delta.getGameTimeDeltaPartialTick(false));
    }
}
