package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.luna.MoonFx;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional (vanilla target): hides the vanilla moon quad while an SS-06 strike is drawing its own moon in its place,
 * so there's never two. {@code SkyRenderer.renderMoon} draws nothing else, so cancelling it at the head is enough -
 * if Minecraft's own sky rendering changes shape, this injection simply stops matching and {@code require = 0} (the
 * default for this config) drops it: the vanilla moon would then show alongside SS-06's, which is a visual nit, not
 * a crash.
 */
@Mixin(SkyRenderer.class)
abstract class MoonHideMixin {
    /** SS-06 can be called by day now (the sky falls to night for it), so the real sun is hidden too. */
    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 0)
    private void ss06luna$hideVanillaSun(CallbackInfo ci) {
        if (!MoonFx.active().isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private void ss06luna$hideVanillaMoon(CallbackInfo ci) {
        if (!MoonFx.active().isEmpty()) {
            ci.cancel();
        }
    }
}
