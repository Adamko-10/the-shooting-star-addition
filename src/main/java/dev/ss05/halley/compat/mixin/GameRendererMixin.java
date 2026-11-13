package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.proxima.ProximaCosmos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional (vanilla target): once a frame, just before the GUI, Proxima's sky gate when a shader pack is on (it would
 * be masked in the world pass; ProximaCosmos.renderSkyLate, Fable's review B4). Forge 1.21.1 has no "before the GUI"
 * event of its own, so this is where main's RenderGuiEvent.Pre listener went. Without it, the gate just doesn't show
 * with a shader pack, as before.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    // remap = false: Forge 1.21.1 runs with Mojang (official) names at runtime (see ClientLevelMixin)
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        remap = false), require = 0, remap = false)
    private void ss05halley$beforeGui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ProximaCosmos.renderSkyLate();
    }
}
