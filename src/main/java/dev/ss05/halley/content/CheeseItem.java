package dev.ss05.halley.content;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.EffectCures;

/**
 * Moon cheese and molten moon cheese as items: placed like any block item, eaten like food - and, being cheese, eating
 * one first clears the eater's effects the way a bucket of milk does (only the ones milk cures). Molten moon cheese's
 * own effects are given after that, as part of the eating, so they stay.
 */
public final class CheeseItem extends BlockItem {
    public CheeseItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            entity.removeEffectsCuredBy(EffectCures.MILK);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
