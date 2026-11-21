package dev.ss05.halley.compat.tss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.9.0, Adam's test ("still no title card", "proxima rn just zooms out and doesn't work"): TON 618 (rimuru's mod)
 * cancels the whole of the game's HUD at its start while a RimuLib film plays, and our films' overlays (captions, title
 * cards, Proxima's space and Earth shots) are drawn inside it, through The Shooting Star's overlay pass. So around the
 * game's HUD call: if our overlay wasn't drawn this frame, it is drawn right after, on the same layer (as TON 618
 * Addition's HudRescue does for its own film). See {@link StarClientBridge#hudRescue}.
 *
 * <p>Minecraft 1.21.11 records the HUD into the frame's {@link GuiGraphics} and draws it all later, so the rescue records
 * into that same one: MixinExtras' {@code @WrapOperation} hands it over (Fabric Loader ships MixinExtras; TON 618
 * Addition's 1.21.11 port wraps the same call).
 */
@Mixin(GameRenderer.class)
abstract class HudRescueMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void ss05halley$hud(Gui gui, GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        StarClientBridge.beforeHud();
        original.call(gui, graphics, delta);
        StarClientBridge.hudRescue(graphics, delta.getGameTimeDeltaPartialTick(false));
    }
}
