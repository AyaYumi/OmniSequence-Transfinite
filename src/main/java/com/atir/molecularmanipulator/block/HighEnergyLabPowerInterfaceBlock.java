package com.atir.molecularmanipulator.block;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import com.atir.molecularmanipulator.blockentity.HighEnergyLabPowerInterfaceBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The laboratory's cable inlet. Its lit state reports paid operating power. */
public final class HighEnergyLabPowerInterfaceBlock
        extends AEBaseEntityBlock<HighEnergyLabPowerInterfaceBlockEntity> {
    public HighEnergyLabPowerInterfaceBlock() {
        super(Properties.of().strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion().lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 8 : 0));
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(
            BlockState state, HighEnergyLabPowerInterfaceBlockEntity inlet) {
        return state.setValue(BlockStateProperties.LIT, inlet.powered());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LabPlatformGeometry.equipment("power", state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            HighEnergyLabDecorationBlock.setPowered(level, pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING), false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.twin_singularity_observation_ring_power_interface"));
    }
}
