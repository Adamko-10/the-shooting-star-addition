package dev.ss05.halley.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Solar Residue: what SA-04 Proxima's plasma leaves in its glassy bowl. It glows, crackles with sparks now and then,
 * and drops itself to a pickaxe (the user's server trades it, CLAUDE.md §14).
 */
public final class SolarResidueBlock extends Block {
    public SolarResidueBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 1.02;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.1, random.nextDouble() * 0.1,
                (random.nextDouble() - 0.5) * 0.1);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                0.4F, 0.5F + random.nextFloat() * 0.4F, false);
        }
    }
}
