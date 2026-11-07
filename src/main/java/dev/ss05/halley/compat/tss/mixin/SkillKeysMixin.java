package dev.ss05.halley.compat.tss.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import cyou.rimuru.rimulib.client.platform.ClientPlatform;
import cyou.rimuru.rimulib.skill.Skill;
import cyou.rimuru.rimulib.skill.SkillSet;
import dev.ss05.halley.world.ProximaInfo;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: a key for the remote's "menu only" skills too (The Shooting Star's SS-06 Gravitia, which sends the whole
 * world into Imaginary Space; players asked for one). RimuLib gives a skill with a select warning no key at all. This
 * gives it one, <b>unbound</b>: nothing changes until the player binds it (in the remote's menu, like the other skills'
 * keys, or in Controls). A skill key only selects the skill on the remote, as the menu does; it still has to be fired.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.rimulib.client.magic.SkillKeys", remap = false)
abstract class SkillKeysMixin {
    @Shadow(remap = false)
    @Final
    private static Map<Skill, KeyMapping> KEYS;
    @Shadow(remap = false)
    @Final
    private static Map<KeyMapping, Skill> SKILLS;

    /** RimuLib 1.2.2 translates only A-Z defaults on 26.3; keep Proxima's punctuation key bound, including Reset. */
    @ModifyArgs(method = "register()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/KeyMapping;<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V"),
        require = 1, remap = false)
    private static void ss05halley$proximaKey(Args args) {
        if (ProximaInfo.DEFAULT_KEY == 93 && "key.shooting_star_demo.stellar_remote.proxima".equals(args.get(0))) {
            args.set(2, InputConstants.KEY_RBRACKET);
        }
    }

    @Inject(method = "register()V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$menuOnlyKeys(CallbackInfo ci) {
        for (SkillSet set : SkillSet.all()) {
            if (set.hidden) {
                continue;
            }
            // the same category as RimuLib gives the set's other keys (26.3: a record, equal by its id; RimuLib has
            // registered it already, and registering it again would throw)
            KeyMapping.Category category = new KeyMapping.Category(set.id);
            for (int i = 0; i < set.skills.size(); i++) {
                Skill skill = set.skills.get(i);
                if (skill.selectWarning() != null && !KEYS.containsKey(skill)) {
                    KeyMapping key = ClientPlatform.get().registerKey(new KeyMapping(set.keyName(skill), InputConstants.Type.KEYBOARD,
                        InputConstants.UNKNOWN.getValue(), category, i));
                    KEYS.put(skill, key);
                    SKILLS.put(key, skill);
                }
            }
        }
    }
}
