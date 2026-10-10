package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional (vanilla target, very unlikely to move): under SS-05's dark sky the land's daylight dims and the clouds
 * darken. Without it the sky still changes; the land just stays as bright as the time of day makes it.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    // remap = false: Forge 1.21.1 runs with Mojang (official) names at runtime, so there is no obfuscation mapping
    // for the Mixin annotation processor to look these vanilla targets up in - treat the names as final already.
    @Inject(method = "getSkyDarken(F)F", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void ss05halley$darkenLand(float partialTick, CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValueF();
        float darkened = HalleySky.skyDarken(vanilla);
        if (darkened != vanilla) {
            cir.setReturnValue(darkened);
        }
    }

    @Inject(method = "getCloudColor(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void ss05halley$darkenClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 vanilla = cir.getReturnValue();
        Vec3 changed = HalleySky.cloudColor(vanilla);
        if (changed != vanilla) {
            cir.setReturnValue(changed);
        }
    }
}
