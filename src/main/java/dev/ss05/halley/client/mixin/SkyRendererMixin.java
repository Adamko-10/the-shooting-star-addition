package dev.ss05.halley.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SS-05's sky dome, drawn right after the vanilla sky (and before the land, which still covers it). */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Inject(method = "render(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
        at = @At("TAIL"))
    private void ss05halley$dome(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
        HalleySky.renderDome(state, this.renderTarget);
    }
}
