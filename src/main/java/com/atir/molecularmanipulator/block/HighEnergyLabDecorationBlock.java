package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.HighEnergyLabPowerInterfaceBlockEntity;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Non-ticking physical architecture; its light reports the nearby inlet's paid power. */
public abstract class HighEnergyLabDecorationBlock extends HorizontalDirectionalBlock {
    protected HighEnergyLabDecorationBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 4 : 0));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(BlockStateProperties.LIT, false));
    }

    protected abstract VoxelShape shape(BlockState state);
    protected abstract BlockPos inletOffset(BlockState state);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var relative = LabPlatformGeometry.rotateOffset(inletOffset(state), state.getValue(FACING));
        boolean powered = level.getBlockEntity(pos.offset(relative)) instanceof HighEnergyLabPowerInterfaceBlockEntity inlet && inlet.powered();
        if (state.getValue(BlockStateProperties.LIT) != powered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    public static void setPowered(Level level, BlockPos inlet, Direction facing, boolean powered) {
        for (var part : LabPlatformGeometry.PARTS) {
            var pos = inlet.offset(LabPlatformGeometry.rotateOffset(part.offset(), facing));
            var state = level.getBlockState(pos);
            if (state.is(HighEnergyLabContent.PLATFORM.get()) && state.getValue(HighEnergyLabPlatformBlock.PART) == part.index()
                    && state.getValue(FACING) == facing) update(level, pos, state, powered);
        }
        for (boolean input : new boolean[] {true, false}) {
            var pos = inlet.offset(LabPlatformGeometry.rotateOffset(input ? LabPlatformGeometry.INPUT : LabPlatformGeometry.OUTPUT, facing));
            var state = level.getBlockState(pos);
            if (state.is(input ? HighEnergyLabContent.INPUT_CRADLE.get() : HighEnergyLabContent.OUTPUT_CRADLE.get())
                    && state.getValue(FACING) == facing) update(level, pos, state, powered);
        }
    }

    private static void update(Level level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(BlockStateProperties.LIT) != powered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, powered), Block.UPDATE_CLIENTS);
        }
    }
}
