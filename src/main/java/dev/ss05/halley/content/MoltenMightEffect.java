package dev.ss05.halley.content;

import dev.ss05.halley.HalleyAddon;
import dev.ss05.halley.MoonConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Molten Might": what eating molten moon cheese gives (SS-06). While it lasts, max health is raised to
 * {@code MoonConfig.tuning().moltenHearts() * 2} (vanilla caps attribute values at 1024, i.e. 512 hearts); Strength
 * is applied separately alongside it (see {@link HalleyContent}, built with {@code MobEffects.STRENGTH}).
 *
 * <p>The extra hearts are a single {@code MAX_HEALTH} modifier under a fixed id, added the moment the effect starts and
 * removed the moment it stops counting for any reason (expiry, milk, death), through the same hooks vanilla's own
 * Health Boost uses ({@link MobEffect#addAttributeModifiers}/{@link MobEffect#removeAttributeModifiers}). Like Health
 * Boost's, it is a permanent (saved) modifier: on a relog {@code LivingEntity.readAdditionalSaveData} puts saved effects
 * straight back without calling those hooks and then clamps the saved health to the max, so only a saved modifier
 * keeps the extra hearts (and the health) across a relog. Removing it leaves health alone; vanilla clamps it down to
 * the new max on the next tick.
 */
public final class MoltenMightEffect extends MobEffect {
    /** Vanilla caps every attribute's final value here (see {@code AttributeInstance}/{@code RangedAttribute}). */
    private static final double MAX_HEALTH_CAP = 1024.0;
    private static final Identifier MODIFIER_ID = HalleyAddon.id("effect.molten_might");

    public MoltenMightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFA23C);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        // Runs every time the effect (re)starts, i.e. every bite, after the max-health modifier above has been
        // (re)applied - so "full health" here already means the raised max.
        if (!entity.level().isClientSide()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        AttributeInstance maxHealth = attributeMap.getInstance(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(MODIFIER_ID);
        double target = Math.min(MAX_HEALTH_CAP, MoonConfig.tuning().moltenHearts() * 2.0);
        double amount = target - maxHealth.getBaseValue();
        if (amount != 0.0) {
            maxHealth.addPermanentModifier(new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        AttributeInstance maxHealth = attributeMap.getInstance(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MODIFIER_ID);
        }
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity livingEntity, int amplifier, Entity.RemovalReason reason) {
        super.onMobRemoved(level, livingEntity, amplifier, reason);
        // onMobRemoved (unlike the normal expiry/cure path) does NOT imply removeAttributeModifiers is called, so
        // death/discard is handled explicitly here too, belt-and-braces.
        removeAttributeModifiers(livingEntity.getAttributes());
    }
}
