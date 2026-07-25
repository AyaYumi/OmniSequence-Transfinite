package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class AssemblerMatrixMolecularCoreBlock
        extends BlockAssemblerMatrixBase<AssemblerMatrixMolecularCoreBlockEntity> {
    public AssemblerMatrixMolecularCoreBlock() {
        super();
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
    public Item getPresentItem() {
        return ModContent.ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM.get();
    }
}
