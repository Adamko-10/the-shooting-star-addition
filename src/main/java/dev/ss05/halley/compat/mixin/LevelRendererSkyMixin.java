package dev.ss05.halley.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Unique;
import dev.ss05.halley.client.render.SkyPass;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.0: inside the game's sky drawing, where its stars are drawn: our sky things while a shader pack is on and they
 * stand above the clouds ({@link SkyPass}). A pack only treats what is drawn here as its sky, which it keeps and draws
 * its clouds over. Right after the star brightness is asked for, Iris has moved its stage on to the stars (not the sun
 * or the moon, which a pack may hide). The same place TON 618 Addition draws its stars from.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererSkyMixin {
    // 1.20.1 rotates this stack for the sun and stars. Our world-space bodies only need the camera turn.
    @Unique private final Matrix4f ss05halley$cameraPose = new Matrix4f();

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void ss05halley$rememberPose(PoseStack poses, Matrix4f projection, float partial, Camera camera,
                                         boolean foggy, Runnable fogSetup, CallbackInfo ci) {
        this.ss05halley$cameraPose.set(poses.last().pose());
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F", shift = At.Shift.AFTER))
    private void ss05halley$skyPass(PoseStack poses, Matrix4f projection, float partial, Camera camera,
                                    boolean foggy, Runnable fogSetup, CallbackInfo ci) {
        SkyPass.draw(this.ss05halley$cameraPose, partial, camera);
    }
}
