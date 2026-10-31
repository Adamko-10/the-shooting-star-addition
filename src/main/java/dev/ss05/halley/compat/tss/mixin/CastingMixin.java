package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.rimulib.mc1211.skill.Power;
import cyou.rimuru.rimulib.mc1211.skill.Skill;
import cyou.rimuru.rimulib.mc1211.skill.SkillSet;
import cyou.rimuru.shootingstardemo.mc1211.magic.Casting;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
import dev.ss05.halley.compat.TssVersion;
import dev.ss05.halley.compat.tss.TssLink;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * The no-cooldown option ({@code no_cooldown} in the common config, see {@link NoCooldown}): for a player who has it,
 * their cooldowns are cleared just before a cast is checked against them, and again just before The Shooting Star
 * sends them their cooldowns (right after every cast), so every skill is ready again at once and shows so. Only that
 * player's: everyone else's cooldowns run as usual. That is all of the remote's skills, its own as well as Halley and
 * Luna.
 *
 * <p>And the server's limits on the remote's sliders ({@code max_power}, {@code max_damage}, {@code max_speed}): a cast
 * set past them is cast again held to them, for every skill on the remote.
 */
@Pseudo
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1211.magic.Casting", remap = false)
abstract class CastingMixin {
    @Inject(method = "syncCooldowns(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$noCooldown(ServerPlayer player, CallbackInfo ci) {
        if (NoCooldown.appliesTo(player)) {
            TssLink.clearCooldowns(player);
        }
    }

    /**
     * The cast request: (player, the skill's index, power, damage, speed, width, play the film for everyone). Its name
     * is obfuscated since The Shooting Star 1.5.0 (and not mapped to a readable one), so it is named as it is.
     */
    @Inject(method = "liiillli1l(Lnet/minecraft/server/level/ServerPlayer;IFFFFZ)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$limit(ServerPlayer player, int index, float power, float damage, float speed, float width,
                                         boolean everyone, CallbackInfo ci) {
        if (player != null && NoCooldown.appliesTo(player)) {
            TssLink.clearCooldowns(player);
        }
        if (Float.isNaN(power) || Float.isNaN(damage) || Float.isNaN(speed)) {
            return;
        }
        HalleyConfig.Limits limits = HalleyConfig.limits();
        float heldPower = power;
        float heldDamage = damage;
        float heldSpeed = Math.min(speed, limits.speed());
        Skill skill = SkillSet.byIndex(TssVersion.MOD_ID, index);
        if (limits.power() > 0.0F && skill != null) {
            Power range = skill.power();
            if (range != null) {
                heldPower = Math.min(power, Math.max(range.min(), range.initial() * limits.power()));
            }
        }
        if (limits.damage() > 0.0F) {
            heldDamage = Math.min(damage, limits.damage());
        }
        if (heldPower != power || heldDamage != damage || heldSpeed != speed) {
            ci.cancel();
            Casting.liiillli1l(player, index, heldPower, heldDamage, heldSpeed, width, everyone);
        }
    }
}
