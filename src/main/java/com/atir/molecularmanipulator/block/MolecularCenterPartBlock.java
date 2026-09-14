package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.MolecularCenterCrystalBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterShellBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class MolecularCenterPartBlock extends Block implements EntityBlock {
    private final boolean networkShell;
    private final boolean patternCrystal;

    public MolecularCenterPartBlock(Properties properties) {
        this(properties, false);
    }

    public MolecularCenterPartBlock(Properties properties, boolean networkShell) {
        this(properties, networkShell, false);
    }

    public MolecularCenterPartBlock(Properties properties, boolean networkShell, boolean patternCrystal) {
        super(properties);
        this.networkShell = networkShell;
        this.patternCrystal = patternCrystal;
        registerDefaultState(defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (patternCrystal) return new MolecularCenterCrystalBlockEntity(pos, state);
        return networkShell ? new MolecularCenterShellBlockEntity(pos, state) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (patternCrystal) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.molecular_center_coil"));
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.molecular_center_coil.dismantle"));
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.molecular_center_coil.build"));
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = new ArrayList<>(super.getDrops(state, builder));
        if (!patternCrystal) return drops;
        var blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof MolecularCenterCrystalBlockEntity crystal) || crystal.patterns().isEmpty()) {
            return drops;
        }
        drops.removeIf(stack -> stack.is(asItem()));
        drops.add(crystal.createDrop());
        return drops;
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
}
