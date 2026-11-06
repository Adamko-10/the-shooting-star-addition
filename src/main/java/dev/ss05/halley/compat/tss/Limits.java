package dev.ss05.halley.compat.tss;

import cyou.rimuru.rimulib.mc1201.skill.Power;
import cyou.rimuru.rimulib.mc1201.skill.Skill;
import cyou.rimuru.rimulib.mc1201.skill.Tuning;
import dev.ss05.halley.HalleyConfig;

/** The server's limits on the remote's sliders ({@code max_power}, {@code max_damage}, {@code max_speed}), for one cast. */
public final class Limits {
    private Limits() {
    }

    /** {power, damage, speed} held to the limits ({@code skill} may be null: then power isn't held). */
    public static float[] hold(Skill skill, float power, float damage, float speed) {
        HalleyConfig.Limits limits = HalleyConfig.limits();
        float heldPower = power;
        if (limits.power() > 0.0F && skill != null) {
            Power range = skill.power();
            if (range != null) {
                heldPower = Math.min(power, Math.max(range.min(), range.initial() * limits.power()));
            }
        }
        float heldDamage = limits.damage() > 0.0F ? Math.min(damage, limits.damage()) : damage;
        return new float[]{heldPower, heldDamage, Math.min(speed, limits.speed())};
    }

    static Tuning hold(Skill skill, Tuning tuning) {
        float[] held = hold(skill, tuning.power(), tuning.damage(), tuning.speed());
        return new Tuning(held[0], held[1], held[2], tuning.width());
    }
}
