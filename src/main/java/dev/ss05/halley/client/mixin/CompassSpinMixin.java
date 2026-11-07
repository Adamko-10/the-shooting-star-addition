package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.proxima.ProximaFx;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngle;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional: compasses (and recovery compasses) spin while Proxima's coronal front batters the magnetic field. */
@Mixin(CompassAngle.class)
abstract class CompassSpinMixin {
    @Inject(method = "get(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/ItemOwner;I)F",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void ss05halley$storm(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner entity, int seed, CallbackInfoReturnable<Float> cir) {
        float spin = ProximaFx.compassSpin(seed);
        if (spin >= 0.0F) {
            cir.setReturnValue(spin);
        }
    }
}
