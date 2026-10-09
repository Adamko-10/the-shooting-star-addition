package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.compat.client.StarClientBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional: the Stellar Remote in your hand opens its cover, lights its button and counts down on its screen for
 * SS-05 the way it does for its own skills. Without this, the remote just stays shut during an SS-05 strike.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.client.item.RemoteRenderer", remap = false)
abstract class RemoteRendererMixin {
    @Inject(method = "strikeTime()F", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$clock(CallbackInfoReturnable<Float> cir) {
        float builtIn = cir.getReturnValueF();
        float clock = StarClientBridge.remoteClock(builtIn, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (clock != builtIn) {
            cir.setReturnValue(clock);
        }
    }
}
