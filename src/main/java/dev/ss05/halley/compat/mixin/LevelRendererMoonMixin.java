package dev.ss05.halley.compat.mixin;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import dev.ss05.halley.client.luna.MoonFx;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional (vanilla target): hides the vanilla moon quad while an SS-06 strike is drawing its own moon in its place,
 * so there's never two. {@code renderSky} draws the sun's quad, then the moon's, both with one
 * {@code BufferUploader.drawWithShader(MeshData)} call each (the sunset glow fan, drawn earlier in the same method
 * when the sun is near the horizon, uses a third); {@code ordinal = 2} is the moon's - the third one, textually,
 * whether or not the sunset fan actually runs this frame. If The Shooting Star... no, if Minecraft's own sky
 * rendering changes shape, this redirect simply stops matching and {@code require = 0} drops it: the vanilla moon
 * would then show alongside SS-06's, which is a visual nit, not a crash.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMoonMixin {
    @Redirect(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V", ordinal = 2),
        require = 0)
    private static void ss06luna$hideVanillaMoon(MeshData mesh) {
        if (MoonFx.active().isEmpty()) {
            BufferUploader.drawWithShader(mesh);
        } else {
            mesh.close();
        }
    }
}
