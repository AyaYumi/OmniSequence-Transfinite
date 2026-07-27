package com.atir.molecularmanipulator.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class OmniComputationCasingBlock extends TransparentBlock {
    public static final BooleanProperty CONNECT_NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty CONNECT_EAST = BlockStateProperties.EAST;
    public static final BooleanProperty CONNECT_SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty CONNECT_WEST = BlockStateProperties.WEST;
    public static final BooleanProperty CONNECT_UP = BlockStateProperties.UP;
    public static final BooleanProperty CONNECT_DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
            Direction.NORTH, CONNECT_NORTH,
            Direction.EAST, CONNECT_EAST,
            Direction.SOUTH, CONNECT_SOUTH,
            Direction.WEST, CONNECT_WEST,
            Direction.UP, CONNECT_UP,
            Direction.DOWN, CONNECT_DOWN);

    public OmniComputationCasingBlock(Properties properties) {
        super(properties);

        BlockState state = defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(
                HorizontalDirectionalBlock.FACING,
                CONNECT_NORTH,
                CONNECT_EAST,
                CONNECT_SOUTH,
                CONNECT_WEST,
                CONNECT_UP,
                CONNECT_DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(
                HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());

        for (Direction direction : Direction.values()) {
            state = state.setValue(
                    CONNECTIONS.get(direction),
                    connectsTo(context.getLevel().getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        return state.setValue(CONNECTIONS.get(direction), connectsTo(neighborState));
    }

    private boolean connectsTo(BlockState neighborState) {
        return neighborState.is(this);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        if (adjacentState.is(this)) {
            return true;
        }
        return super.skipRendering(state, adjacentState, direction);
    }
}
