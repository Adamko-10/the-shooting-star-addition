package dev.ss05.halley.compat.tss.mixin;

import cyou.rimuru.shootingstardemo.mc1201.magic.Casting;
import cyou.rimuru.shootingstardemo.mc1201.magic.Power;
import cyou.rimuru.shootingstardemo.mc1201.magic.Skill;
import cyou.rimuru.shootingstardemo.mc1201.magic.Tuning;
import dev.ss05.halley.HalleyConfig;
import dev.ss05.halley.NoCooldown;
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
@Mixin(targets = "cyou.rimuru.shootingstardemo.mc1201.magic.Casting", remap = false)
abstract class CastingMixin {
    @Inject(method = "syncCooldowns(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), require = 0, remap = false)
    private static void ss05halley$noCooldown(ServerPlayer player, CallbackInfo ci) {
        if (NoCooldown.appliesTo(player)) {
            TssLink.clearCooldowns(player);
        }
    }

    @Inject(method = "cast(Lnet/minecraft/server/level/ServerPlayer;Lcyou/rimuru/shootingstardemo/mc1201/magic/Skill;Lcyou/rimuru/shootingstardemo/mc1201/magic/Tuning;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ss05halley$limit(ServerPlayer player, Skill skill, Tuning tuning, CallbackInfo ci) {
        if (player != null && NoCooldown.appliesTo(player)) {
            TssLink.clearCooldowns(player);
        }
        if (tuning == null || skill == null || Float.isNaN(tuning.power()) || Float.isNaN(tuning.damage()) || Float.isNaN(tuning.speed())) {
            return;
        }
        HalleyConfig.Limits limits = HalleyConfig.limits();
        float power = tuning.power();
        float damage = tuning.damage();
        float speed = Math.min(tuning.speed(), limits.speed());
        if (limits.power() > 0.0F) {
            try {
                Power range = skill.power();
                if (range != null) {
                    power = Math.min(power, Math.max(range.min(), range.initial() * limits.power()));
                }
            } catch (LinkageError olderVersion) {
                // a Shooting Star without power ranges: leave power as it is
            }
        }
        if (limits.damage() > 0.0F) {
            damage = Math.min(damage, limits.damage());
        }
        if (power != tuning.power() || damage != tuning.damage() || speed != tuning.speed()) {
            ci.cancel();
            Casting.cast(player, skill, new Tuning(power, damage, speed));
        }
    }
}
