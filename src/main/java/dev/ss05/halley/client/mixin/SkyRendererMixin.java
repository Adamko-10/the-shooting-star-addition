package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SS-05's sky dome, drawn right after the vanilla sky (and before the land, which still covers it). Minecraft 1.21.11
 * draws the overworld sky in steps from the level renderer's sky pass; the dome goes after the sun, moon and stars
 * (only the overworld's sky has those).
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void ss05halley$dome(CallbackInfo ci) {
        HalleySky.renderDome();
    }
}
