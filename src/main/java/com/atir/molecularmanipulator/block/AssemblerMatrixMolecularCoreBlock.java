package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

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
    public List<ItemStack> getDrops(BlockState state,
            LootParams.Builder builder) {
        var drops = new ArrayList<>(super.getDrops(state, builder));
        var blockEntity = builder.getOptionalParameter(
                LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof AssemblerMatrixMolecularCoreBlockEntity core
                && core.hasRemovalRecovery()) {
            drops.removeIf(stack -> stack.is(getPresentItem()));
        }
        return drops;
    }

    @Override
    public Item getPresentItem() {
        return ModContent.ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM.get();
    }
}
