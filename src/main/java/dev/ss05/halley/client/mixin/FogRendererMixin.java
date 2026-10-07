package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fog takes on SS-05's sky at the horizon, so the land fades into it. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFLorg/joml/Vector4f;)V",
        at = @At("TAIL"))
    private void ss05halley$fogColor(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darkenWorldAmount,
                                     Vector4f dest, CallbackInfo ci) {
        HalleySky.fogColor(dest);
    }
}
