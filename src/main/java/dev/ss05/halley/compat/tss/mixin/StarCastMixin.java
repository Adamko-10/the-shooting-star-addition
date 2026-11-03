package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.rimulib.mc1211.skill.Tuning;
import cyou.rimuru.shootingstardemo.mc1211.liiliiii1;
import dev.ss05.halley.NoCooldown;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The no-cooldown option for The Shooting Star's own skills: their cast (player, skill, sliders) refuses a second one
 * while the player's last of that skill is still running. For a player the option applies to, {@link SpellsMixin} finds
 * none while this cast is made. Obfuscated and unmapped since The Shooting Star 1.5.1 (the class casting the remote's
 * own skills, and {@code liiliiii1} their enum), so it is named as it is.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.il1i1li1l11", remap = false)
abstract class StarCastMixin {
    @Inject(method = "liiillli1l(Lnet/minecraft/server/level/ServerPlayer;Lcyou/rimuru/shootingstardemo/mc1211/liiliiii1;Lcyou/rimuru/rimulib/mc1211/skill/Tuning;)Z",
        at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$free(ServerPlayer player, liiliiii1 skill, Tuning tuning, CallbackInfoReturnable<Boolean> cir) {
        NoCooldown.casting(player != null && NoCooldown.appliesTo(player));
    }

    @Inject(method = "liiillli1l(Lnet/minecraft/server/level/ServerPlayer;Lcyou/rimuru/shootingstardemo/mc1211/liiliiii1;Lcyou/rimuru/rimulib/mc1211/skill/Tuning;)Z",
        at = @At("RETURN"), require = 0, remap = false)
    private static void ss05halley$done(ServerPlayer player, liiliiii1 skill, Tuning tuning, CallbackInfoReturnable<Boolean> cir) {
        NoCooldown.casting(false);
    }
}
