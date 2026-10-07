package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.sky.HalleySky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Under SS-05's dark sky the land's daylight dims and the clouds darken: layers of its own on the client level's
 * environment attributes (they only change what this client draws).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "addEnvironmentAttributeLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;)Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;",
        at = @At("RETURN"))
    private void ss05halley$skyLayers(EnvironmentAttributeSystem.Builder layers, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
        HalleySky.addLayers(cir.getReturnValue());
    }
}
