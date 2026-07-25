package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class MolecularCenterCoreBlock extends Block {
    public MolecularCenterCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0) {
            return;
        }
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                0, 0.02, 0);
        if (effectLevel > 1 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5,
                    pos.getY() + 0.5, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.08, (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.08);
        }
    }
}
