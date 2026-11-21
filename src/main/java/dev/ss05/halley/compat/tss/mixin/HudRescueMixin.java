package dev.ss05.halley.compat.tss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.9.0, TON 618's HUD rescue: TON 618 (rimuru_dev's) cuts the game's HUD short while a RimuLib film plays, which took
 * our film overlays with it (the title cards, Proxima's whole space part). Around the HUD: before it, a new frame's
 * overlay is still to draw ({@link StarClientBridge#beforeHud}); after it, if nothing drew ours, it is drawn then
 * ({@link StarClientBridge#hudRescue}).
 *
 * <p>26.3 extracts the GUI instead of drawing it: main wraps {@code Gui.render} in {@code GameRenderer.render}; here
 * {@code Gui.extractRenderState} makes the frame's {@link GuiGraphicsExtractor} and hands it to
 * {@code Hud.extractRenderState}, the HUD itself, so that call is wrapped and the rescue extracts into the same one.
 */
@Mixin(Gui.class)
abstract class HudRescueMixin {
    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void ss05halley$aroundHud(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        StarClientBridge.beforeHud();
        original.call(hud, graphics, delta);
        StarClientBridge.hudRescue(graphics, delta.getGameTimeDeltaPartialTick(false));
    }
}
