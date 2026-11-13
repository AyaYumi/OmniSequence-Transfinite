package com.atir.molecularmanipulator.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Observation port of the Star Computation Engine.
 *
 * <p>The port is transparent while unformed, and hides its ordinary mesh for
 * the orrery renderer while formed. Other glass blocks remain unchanged.</p>
 */
public final class StarComputationObservationPortBlock extends GlassBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final BooleanProperty FORMED = StarComputationEngineCoreBlock.FORMED;
    public static final BooleanProperty INVISIBLE = StarComputationEngineCasingBlock.INVISIBLE;

    public StarComputationObservationPortBlock(Properties properties) {
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
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.atir.molecularmanipulator.blockentity.StarComputationProxyBlockEntity(pos, state);
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return com.atir.molecularmanipulator.starcomputation.StarComputationInteraction.open(level, pos, player);
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

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        if (adjacentState.getBlock() instanceof StarComputationObservationPortBlock) {
            // A hidden member must not cull a neighboring loose port's face.
            return adjacentState.getRenderShape() == state.getRenderShape();
        }

        return super.skipRendering(state, adjacentState, direction);
    }
}
