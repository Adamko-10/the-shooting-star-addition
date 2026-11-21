package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.rimulib.skill.Skill;
import dev.ss05.halley.client.HalleyClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: the client switch "close on pick" (client config {@code close_menu_on_pick}, [ on our plate; the user,
 * 2026-11-12; on every version since 1.9.0). RimuLib's remote menu (White Space) keeps itself open when a skill is
 * clicked; with the switch on, it leaves the way H or Esc does once the pick goes through. {@code commit} runs after a
 * click, and after a skill's warning page is confirmed (Gravitia), so a warning still shows first. If RimuLib renames
 * either method, nothing happens. 26.3: RimuLib's Fabric 26.3 build has no version tag in its package (checked in its
 * 1.2.2 build: {@code private static void commit(Skill, String)} and {@code static void leave()}).
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.rimulib.client.magic.WhiteSpace", remap = false)
abstract class WhiteSpaceMixin {
    @Shadow(remap = false)
    static void leave() {
        throw new AssertionError();
    }

    @Inject(method = "commit(Lcyou/rimuru/rimulib/skill/Skill;Ljava/lang/String;)V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$closeOnPick(Skill skill, String id, CallbackInfo ci) {
        if (HalleyClientConfig.closeMenuOnPick()) {
            leave();
        }
    }
}
