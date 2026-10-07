package dev.ss05.halley.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.ClientClockManager;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a shader pack is on, SS-05's dark sky is the pack's own night: the clock the client's sky follows (the sun,
 * the moon, the stars, the light) is wound on into the night and back during a strike ({@link HalleySky#clockShift}).
 * Only on this client, and only what is drawn: the server's time never changes.
 */
@Mixin(AttributeTrackSampler.class)
abstract class AttributeTrackSamplerMixin {
    @Shadow
    @Final
    private ClockManager clockManager;

    @ModifyExpressionValue(method = "applyTimeBased", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))
    private long ss05halley$windTheClock(long ticks) {
        return this.clockManager instanceof ClientClockManager ? ticks + HalleySky.clockShift() : ticks;
    }
}
