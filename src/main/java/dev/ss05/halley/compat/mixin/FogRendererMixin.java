package dev.ss05.halley.compat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The fog takes on the strike's sky at the horizon, so the land fades into it: the colour Minecraft has just worked out
 * is changed, and the screen is cleared to the new one (Minecraft has already cleared it to the old by then).
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void ss05halley$fogColor(Camera camera, float partialTicks, ClientLevel level, int renderDistanceChunks,
                                            float bossColorModifier, CallbackInfo ci) {
        float[] rgb = HalleySky.fogColor(camera, fogRed, fogGreen, fogBlue);
        if (rgb != null) {
            fogRed = rgb[0];
            fogGreen = rgb[1];
            fogBlue = rgb[2];
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
        }
    }
}
