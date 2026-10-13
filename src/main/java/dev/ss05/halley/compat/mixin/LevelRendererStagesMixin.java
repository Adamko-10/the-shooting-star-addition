package dev.ss05.halley.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ss05.halley.client.HalleyClient;
import dev.ss05.halley.client.render.WorldFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two points of the level's drawing the skills draw at (Fabric's own world render events have neither): right
 * after the vanilla sky (the strike's sky dome, before the land covers it), and right after the world's particles
 * (the comet's and the moon's glow, so it sorts with them, also under Fabulous graphics - both of the particle draws,
 * Fabulous's and the plain one). If either stops matching, the dome is missing and the glow is drawn at the end of the
 * world instead (see HalleyClient). The pose stack holds the camera's turn (1.20.1 keeps it there).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererStagesMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
        shift = At.Shift.AFTER))
    private void ss05halley$afterSky(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                     Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix,
                                     CallbackInfo ci) {
        HalleyClient.afterSky(new WorldFrame(camera, projectionMatrix, new Matrix4f(poseStack.last().pose()), partialTick));
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
        shift = At.Shift.AFTER))
    private void ss05halley$afterParticles(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                           Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix,
                                           CallbackInfo ci) {
        HalleyClient.afterParticles(new WorldFrame(camera, projectionMatrix, new Matrix4f(poseStack.last().pose()), partialTick));
    }
}
