package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

/** Water suppresses the dangerous field without consuming the stored matter. */
public final class GhostMatterBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty DISPERSED = BooleanProperty.create("dispersed");
    public static final IntegerProperty FLOW_LEVEL = IntegerProperty.create("flow_level", 0, 15);
    private static final VoxelShape OUTLINE = box(2, 0, 2, 14, 8, 14);

    public GhostMatterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false)
                .setValue(DISPERSED, false).setValue(FLOW_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED, DISPERSED, FLOW_LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = withFluid(defaultBlockState(), context.getLevel().getFluidState(context.getClickedPos()));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        if (!state.getValue(BlockStateProperties.WATERLOGGED)) return Fluids.EMPTY.defaultFluidState();
        int flow = state.getValue(FLOW_LEVEL);
        return flow == 0 ? Fluids.WATER.getSource(false) : Fluids.FLOWING_WATER.getFlowing(8 - (flow & 7), flow >= 8);
    }

    public static BlockState withFluid(BlockState state, FluidState fluid) {
        boolean water = fluid.getType().isSame(Fluids.WATER);
        return state.setValue(BlockStateProperties.WATERLOGGED, water)
                .setValue(FLOW_LEVEL, water ? fluid.createLegacyBlock().getValue(LiquidBlock.LEVEL) : 0);
    }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return fluid.isSame(Fluids.WATER);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!fluid.getType().isSame(Fluids.WATER)) return false;
        if (!level.isClientSide()) {
            level.setBlock(pos, withFluid(state, fluid), 3);
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
        return true;
    }

    @Override
    public net.minecraft.world.item.ItemStack pickupBlock(Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (!state.getValue(BlockStateProperties.WATERLOGGED) || state.getValue(FLOW_LEVEL) != 0)
            return net.minecraft.world.item.ItemStack.EMPTY;
        level.setBlock(pos, withFluid(state, Fluids.EMPTY.defaultFluidState()), 3);
        return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(DISPERSED) || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED))
            level.scheduleTick(pos, state.getFluidState().getType(), Fluids.WATER.getTickDelay(level));
        if (direction == Direction.DOWN && !state.getValue(DISPERSED) && !state.canSurvive(level, pos)) {
            if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
            return state.setValue(DISPERSED, true);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof GhostMatterBlockEntity matter) matter.serverTick();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(DISPERSED) ? Shapes.empty() : OUTLINE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GhostMatterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (world, pos, current, entity) -> {
            if (entity instanceof GhostMatterBlockEntity matter) matter.serverTick();
        };
    }

}
