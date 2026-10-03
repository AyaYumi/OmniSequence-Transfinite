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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Static structure blocks; occasional gathering sparks run only on the client. */
public final class SingularityPartBlock extends Block {
    public enum Effect { NONE, GATHER }
    private final Effect effect;

    public SingularityPartBlock(Properties properties, Effect effect) {
        super(properties);
        this.effect = effect;
        registerDefaultState(defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int detail = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effect != Effect.GATHER || detail <= 0 || random.nextInt(detail > 1 ? 3 : 8) != 0) return;
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
        if (!level.getBlockState(pos.relative(front)).isAir()) return;
        double a = random.nextDouble() * Math.PI * 2;
        double lateral = Math.cos(a) * 0.32;
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.5 + front.getStepX() * 0.6 + front.getStepZ() * lateral,
                pos.getY() + 0.5 + Math.sin(a) * 0.32,
                pos.getZ() + 0.5 + front.getStepZ() * 0.6 - front.getStepX() * lateral,
                -front.getStepZ() * lateral * 0.035, -Math.sin(a) * 0.011,
                front.getStepX() * lateral * 0.035);
    }
}
