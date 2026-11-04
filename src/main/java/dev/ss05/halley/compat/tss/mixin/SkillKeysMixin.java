package dev.ss05.halley.compat.tss.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import cyou.rimuru.rimulib.mc1211.client.platform.ClientPlatform;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: a key for the remote's "menu only" skills too (The Shooting Star's SS-06 Gravitia, which sends the whole
 * world into Imaginary Space; players asked for one). RimuLib gives a skill with a select warning no key at all. This
 * gives it one, <b>unbound</b>: nothing changes until the player binds it (in the remote's menu, like the other skills'
 * keys, or in Controls). A skill key only selects the skill on the remote, as the menu does; it still has to be fired.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.rimulib.mc1211.client.magic.SkillKeys", remap = false)
abstract class SkillKeysMixin {
    @Shadow(remap = false)
    @Final
    private static Map<Skill, KeyMapping> KEYS;
    @Shadow(remap = false)
    @Final
    private static Map<KeyMapping, Skill> SKILLS;

    @Inject(method = "register()V", at = @At("TAIL"), require = 0, remap = false)
    private static void ss05halley$menuOnlyKeys(CallbackInfo ci) {
        for (SkillSet set : SkillSet.all()) {
            if (set.hidden) {
                continue;
            }
            // the same category as RimuLib gives the set's other keys
            String category = "key.category." + set.id.getNamespace() + "." + set.id.getPath();
            for (Skill skill : set.skills) {
                if (skill.selectWarning() != null && !KEYS.containsKey(skill)) {
                    KeyMapping key = ClientPlatform.get().registerKey(new KeyMapping(set.keyName(skill), InputConstants.Type.KEYSYM,
                        InputConstants.UNKNOWN.getValue(), category));
                    KEYS.put(skill, key);
                    SKILLS.put(key, skill);
                }
            }
        }
    }
}
