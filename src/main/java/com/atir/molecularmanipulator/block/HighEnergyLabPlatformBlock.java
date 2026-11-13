package com.atir.molecularmanipulator.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Individually baked paving sections keep rendering and collision local to their chunks. */
public final class HighEnergyLabPlatformBlock extends HighEnergyLabDecorationBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 48);

    public HighEnergyLabPlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 17));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(PART); }
    @Override protected VoxelShape shape(BlockState state) { return LabPlatformGeometry.floor(state.getValue(PART), state.getValue(FACING)); }
    @Override protected BlockPos inletOffset(BlockState state) { return LabPlatformGeometry.partOffset(state.getValue(PART)).multiply(-1); }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        var stack = super.getCloneItemStack(level, pos, state);
        stack.getOrCreateTagElement("BlockStateTag").putString(PART.getName(), PART.getName(state.getValue(PART)));
        return stack;
    }
}
