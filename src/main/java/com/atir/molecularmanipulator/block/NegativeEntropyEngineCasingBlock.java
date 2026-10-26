package com.atir.molecularmanipulator.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shell block of the Negative Entropy Engine.
 *
 * <p>Formed members are hidden beneath the core's oversized baked model,
 * without changing their collision or selection shape.</p>
 */
public class NegativeEntropyEngineCasingBlock extends Block {
    public static final BooleanProperty FORMED = NegativeEntropyEngineCoreBlock.FORMED;
    public static final BooleanProperty INVISIBLE = BooleanProperty.create("invisible");

    public NegativeEntropyEngineCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(INVISIBLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, FORMED, INVISIBLE);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return com.atir.molecularmanipulator.entropy.NegativeEntropyInteraction.open(level, pos, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(INVISIBLE) ? RenderShape.INVISIBLE : super.getRenderShape(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(INVISIBLE) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(INVISIBLE) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }
}
