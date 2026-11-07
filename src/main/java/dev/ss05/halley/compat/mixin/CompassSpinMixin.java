package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.proxima.ProximaFx;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional: compasses (and recovery compasses) spin while Proxima's coronal front batters the magnetic field. */
@Mixin(CompassItemPropertyFunction.class)
abstract class CompassSpinMixin {
    @Inject(method = "unclampedCall(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;I)F",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void ss05halley$storm(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, CallbackInfoReturnable<Float> cir) {
        float spin = ProximaFx.compassSpin(seed);
        if (spin >= 0.0F) {
            cir.setReturnValue(spin);
        }
    }
}
