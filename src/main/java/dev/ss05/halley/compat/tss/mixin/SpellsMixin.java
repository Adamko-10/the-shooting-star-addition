package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.mc1211.liiliiii1;
import cyou.rimuru.shootingstardemo.mc1211.spell.ActiveSpell;
import dev.ss05.halley.NoCooldown;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Shooting Star's running spells: "this player's running spell of this skill" finds none while
 * {@link StarCastMixin} casts for a player with no cooldown, so a second one starts. Obfuscated and unmapped since
 * 1.5.1 (it was {@code SpellEngine}), so it is named as it is.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.l111i1lil1", remap = false)
abstract class SpellsMixin {
    @Inject(method = "liiillli1l(Lcyou/rimuru/shootingstardemo/mc1211/liiliiii1;Ljava/util/UUID;)Lcyou/rimuru/shootingstardemo/mc1211/spell/ActiveSpell;",
        at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$noneRunning(liiliiii1 skill, UUID caster, CallbackInfoReturnable<ActiveSpell> cir) {
        if (NoCooldown.casting()) {
            cir.setReturnValue(null);
        }
    }
}
