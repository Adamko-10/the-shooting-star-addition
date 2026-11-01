package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.compat.StarBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Halley and Luna join the Stellar Remote just before RimuLib's client starts: by then every mod's main entrypoint has
 * run (so The Shooting Star has registered its skills; Fabric runs main entrypoints in no fixed order), and RimuLib
 * hasn't made the skills' key bindings yet, so Halley and Luna get theirs like the remote's own skills.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.rimulib.client.RimulibFabricClient", remap = false)
abstract class RimulibClientMixin {
    @Inject(method = "onInitializeClient()V", at = @At("HEAD"), require = 0, remap = false)
    private void ss05halley$attach(CallbackInfo ci) {
        StarBridge.install();
    }
}
