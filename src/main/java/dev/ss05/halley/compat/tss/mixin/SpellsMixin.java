package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.mc1211.star.StarSkill;
import cyou.rimuru.shootingstardemo.mc1211.spell.ActiveSpell;
import dev.ss05.halley.NoCooldown;
import dev.ss05.halley.compat.tss.StrikeAim;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Shooting Star's spell engine ({@code SpellEngine}; unmapped in 1.5.1, mapped again since 1.5.2):
 * "this player's running spell of this skill" finds none while {@link StarCastMixin} casts for a player with no
 * cooldown (or a command casts), so a second one starts; and "where the player aims on the ground", which every
 * skill's cast asks (ours too, through TssLink.aimGround), lands where an operator's {@code /ssa strike ... at} says.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.spell.SpellEngine", remap = false)
abstract class SpellsMixin {
    @Inject(method = "liiillli1l(Lcyou/rimuru/shootingstardemo/mc1211/star/StarSkill;Ljava/util/UUID;)Lcyou/rimuru/shootingstardemo/mc1211/spell/ActiveSpell;",
        at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$noneRunning(StarSkill skill, UUID caster, CallbackInfoReturnable<ActiveSpell> cir) {
        if (NoCooldown.casting()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "aimGround(Lnet/minecraft/server/level/ServerPlayer;DD)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$aim(ServerPlayer player, double reach, double fallback, CallbackInfoReturnable<Vec3> cir) {
        Vec3 at = StrikeAim.at();
        if (at != null) {
            cir.setReturnValue(at);
        }
    }
}
