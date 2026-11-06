package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.rimulib.skill.Tuning;
import cyou.rimuru.shootingstardemo.star.StarSkill;
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
 * none while this cast is made. Unmapped in 1.5.1 (named as it was); mapped again since 1.5.2: {@code StarEngine.cast}.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.star.StarEngine", remap = false)
abstract class StarCastMixin {
    @Inject(method = "cast(Lnet/minecraft/server/level/ServerPlayer;Lcyou/rimuru/shootingstardemo/star/StarSkill;Lcyou/rimuru/rimulib/skill/Tuning;)Z",
        at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$free(ServerPlayer player, StarSkill skill, Tuning tuning, CallbackInfoReturnable<Boolean> cir) {
        NoCooldown.casting(player != null && NoCooldown.appliesTo(player));
    }

    @Inject(method = "cast(Lnet/minecraft/server/level/ServerPlayer;Lcyou/rimuru/shootingstardemo/star/StarSkill;Lcyou/rimuru/rimulib/skill/Tuning;)Z",
        at = @At("RETURN"), require = 0, remap = false)
    private static void ss05halley$done(ServerPlayer player, StarSkill skill, Tuning tuning, CallbackInfoReturnable<Boolean> cir) {
        NoCooldown.casting(false);
    }
}
