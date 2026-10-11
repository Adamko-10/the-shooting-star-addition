package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The fog takes on SS-05's sky at the horizon, so the land fades into it. (Minecraft 1.21.11 returns the colour it
 * computes; 26.x fills one in.)
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)Lorg/joml/Vector4f;",
        at = @At("RETURN"))
    private void ss05halley$fogColor(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darkenWorldAmount,
                                     CallbackInfoReturnable<Vector4f> cir) {
        HalleySky.fogColor(cir.getReturnValue());
    }
}
