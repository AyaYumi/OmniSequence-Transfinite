package com.atir.molecularmanipulator.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Observation window of the Negative Entropy Engine.
 *
 * <p>Carries the FORMED property so the formed shell can switch to a continuous
 * panel model; {@link com.atir.molecularmanipulator.block.MolecularCenterGlassBlock}
 * stays untouched for the other glass blocks.</p>
 */
public final class NegativeEntropyObservationWindowBlock extends TransparentBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public NegativeEntropyObservationWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        if (adjacentState.getBlock() instanceof NegativeEntropyObservationWindowBlock
                && adjacentState.getRenderShape() == state.getRenderShape()) {
            return true;
        }

        return super.skipRendering(state, adjacentState, direction);
    }
}
