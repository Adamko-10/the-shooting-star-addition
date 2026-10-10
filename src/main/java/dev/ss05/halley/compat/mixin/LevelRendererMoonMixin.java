package dev.ss05.halley.compat.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import dev.ss05.halley.client.luna.MoonFx;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional (vanilla target): hides the vanilla sun and moon quads while an SS-06 strike is drawing its own moon in
 * the sky, so there's never two - and, since a moonfall can now be called by day (the sky falls to night for it
 * regardless), the real sun is never left sitting right next to it either. {@code renderSky} draws the sun's quad,
 * then the moon's, both with one {@code BufferUploader.drawWithShader(BufferBuilder.RenderedBuffer)} call each (1.20.1; 1.21 passes a {@code MeshData}) (the sunset glow fan,
 * drawn earlier in the same method when the sun is near the horizon, uses a third); {@code ordinal = 1} is the
 * sun's and {@code ordinal = 2} is the moon's - the second and third, textually, whether or not the sunset fan
 * actually runs this frame. If Minecraft's own sky rendering changes shape, either redirect simply stops matching
 * and {@code require = 0} drops it: the vanilla body would then show alongside SS-06's, which is a visual nit, not
 * a crash.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMoonMixin {
    @Redirect(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V", ordinal = 1),
        require = 0)
    private static void ss06luna$hideVanillaSun(BufferBuilder.RenderedBuffer buffer) {
        if (MoonFx.active().isEmpty()) {
            BufferUploader.drawWithShader(buffer);
        } else {
            buffer.release();
        }
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V", ordinal = 2),
        require = 0)
    private static void ss06luna$hideVanillaMoon(BufferBuilder.RenderedBuffer buffer) {
        if (MoonFx.active().isEmpty()) {
            BufferUploader.drawWithShader(buffer);
        } else {
            buffer.release();
        }
    }
}
