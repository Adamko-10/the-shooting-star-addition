package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.mc1211.magic.Casting;
import dev.ss05.halley.HalleyConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The no-cooldown option ({@code no_cooldown} in the common config): The Shooting Star sends a player their cooldowns
 * right after every cast (and whenever else they change); with the option on, every cooldown is cleared just before
 * that, so the player sees every skill ready again at once and the remote never refuses a skill as recharging. That is
 * all of the remote's skills, its own as well as Halley and Luna, for every player.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.magic.Casting", remap = false)
abstract class CastingMixin {
    @Inject(method = "syncCooldowns(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$noCooldown(ServerPlayer player, CallbackInfo ci) {
        if (HalleyConfig.noCooldown()) {
            Casting.clear();
        }
    }
}
