package dev.ss05.halley.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a shader pack is on, SS-05's dark sky is the pack's own night: the clock the client's sky follows (the sun,
 * the moon, the stars, the light) is wound on into the night and back during a strike ({@link HalleySky#clockShift}).
 * Only on this client, and only what is drawn: the server's time never changes.
 *
 * <p>Minecraft 1.21.11 (Fabric 1.21.11 branch): the sampler reads the day time from a plain {@code LongSupplier}
 * (26.x has a clock manager that tells the client's clock apart). The client's sky is sampled on the render thread
 * and a singleplayer world's server on its own thread, so only reads on the render thread are wound on.
 */
@Mixin(AttributeTrackSampler.class)
abstract class AttributeTrackSamplerMixin {
    @ModifyExpressionValue(method = "applyTimeBased", at = @At(value = "INVOKE", target = "Ljava/util/function/LongSupplier;getAsLong()J"))
    private long ss05halley$windTheClock(long ticks) {
        return RenderSystem.isOnRenderThread() ? ticks + HalleySky.clockShift() : ticks;
    }
}
