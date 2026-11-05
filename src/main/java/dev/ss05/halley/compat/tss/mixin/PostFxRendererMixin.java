package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.rimulib.mc1211.client.render.ScreenFx;
import dev.ss05.halley.compat.tss.client.StarClientBridge;
import java.util.List;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional: with Stellar Convergence's shake off, RimuLib's post-process screen jitter is off too while one hits. */
@Pseudo
@Mixin(targets = "cyou.rimuru.rimulib.mc1211.client.render.PostFxRenderer", remap = false)
abstract class PostFxRendererMixin {
    @Inject(method = "render(Ljava/util/List;Lcyou/rimuru/rimulib/mc1211/client/render/ScreenFx;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;F)V",
        at = @At("HEAD"), require = 0, remap = false)
    private void ss05halley$noJitter(List<?> passes, ScreenFx screen, Matrix4f projection, Matrix4f view, float partial, CallbackInfo ci) {
        if (StarClientBridge.shakeOff()) {
            screen.shake = 0.0F;
        }
    }
}
