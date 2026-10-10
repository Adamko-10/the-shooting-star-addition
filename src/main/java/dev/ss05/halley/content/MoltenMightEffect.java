package dev.ss05.halley.content;

import dev.ss05.halley.MoonConfig;
import java.util.UUID;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "Molten Might": what eating molten moon cheese gives (SS-06). While it lasts, max health is raised to
 * {@code MoonConfig.tuning().moltenHearts() * 2} (vanilla caps attribute values at 1024, i.e. 512 hearts); Strength
 * is applied separately alongside it (see {@link HalleyContent}, built with {@code MobEffects.DAMAGE_BOOST}).
 *
 * <p>The extra hearts are a single {@code MAX_HEALTH} modifier under a fixed id, added the moment the effect starts and
 * removed the moment it stops counting for any reason (expiry, milk, death), through the same hooks vanilla's own
 * Health Boost uses ({@link MobEffect#addAttributeModifiers}/{@link MobEffect#removeAttributeModifiers}). Like Health
 * Boost's, it is a permanent (saved) modifier: {@code AttributeInstance.addPermanentModifier} serialises the amount
 * itself into the entity's attribute save data, so it keeps the extra hearts (and the health) across a relog even
 * though {@code LivingEntity.readAdditionalSaveData} restores saved effects without calling these hooks again.
 *
 * <p>1.20.1's {@code MobEffect} has no {@code onEffectStarted} hook (that's a later addition) - but unlike the
 * NeoForge 1.21.1 original, this version's {@code addAttributeModifiers} is handed the {@code LivingEntity} directly,
 * so the "heal to the new full health" step that used to live in {@code onEffectStarted} is folded in here instead;
 * it still runs every time the effect (re)starts (every bite), after the max-health modifier has been (re)applied,
 * so "full health" already means the raised max.
 *
 * <p><b>1.20.1 vanilla quirk (the Strength amplifier):</b> {@code MobEffectInstance} saves its amplifier as a signed
 * byte ({@code nbt.putByte("Amplifier", (byte) amplifier)}, read back with {@code nbt.getByte(...)}) - 1.21 saves an
 * int. Strength's amplifier here is 254 (molten_strength 255 by default), which overflows a byte to -2 after a world
 * save/reload. The attack-damage boost itself is unaffected (vanilla's own {@code MobEffect.addAttributeModifiers}
 * also adds Strength's attribute modifier as a permanent one, so its amount is saved and restored independently of
 * the amplifier), but anything that reads {@code MobEffectInstance.getAmplifier()} afterwards (the HUD icon's
 * level, a second bite deciding whether to "upgrade") would see -2. Worked around below: Molten Might ticks once a
 * second and, if the Strength effect's amplifier has gone negative, re-applies Strength fresh at the configured
 * amplifier with its current remaining duration.
 */
public final class MoltenMightEffect extends MobEffect {
    /** Vanilla caps every attribute's final value here (see {@code AttributeInstance}/{@code RangedAttribute}). */
    private static final double MAX_HEALTH_CAP = 1024.0;
    private static final UUID MODIFIER_ID = UUID.fromString("5c7c9d1e-8c3e-4a0a-9f0a-3fa0c3451678");

    public MoltenMightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFA23C);
    }

    @Override
    public void addAttributeModifiers(LivingEntity livingEntity, AttributeMap attributeMap, int amplifier) {
        AttributeInstance maxHealth = attributeMap.getInstance(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(MODIFIER_ID);
        double target = Math.min(MAX_HEALTH_CAP, MoonConfig.tuning().moltenHearts() * 2.0);
        double amount = target - maxHealth.getBaseValue();
        if (amount != 0.0) {
            maxHealth.addPermanentModifier(new AttributeModifier(MODIFIER_ID, this.getDescriptionId(), amount, AttributeModifier.Operation.ADDITION));
        }
        // See the class javadoc: 1.20.1 has no onEffectStarted, so the "heal to full" step lives here instead.
        if (!livingEntity.level().isClientSide()) {
            livingEntity.setHealth(livingEntity.getMaxHealth());
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity livingEntity, AttributeMap attributeMap, int amplifier) {
        AttributeInstance maxHealth = attributeMap.getInstance(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MODIFIER_ID);
        }
    }

    /** Once a second, so the Strength-amplifier workaround (see the class javadoc) checks and fixes itself promptly. */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide()) {
            return;
        }
        MobEffectInstance strength = livingEntity.getEffect(MobEffects.DAMAGE_BOOST);
        if (strength != null && strength.getAmplifier() < 0) {
            int fixed = MoonConfig.tuning().moltenStrength() - 1;
            livingEntity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, strength.getDuration(), fixed,
                strength.isAmbient(), strength.isVisible(), strength.showIcon()));
        }
    }
}
