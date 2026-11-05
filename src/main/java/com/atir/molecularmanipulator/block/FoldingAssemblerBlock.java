package com.atir.molecularmanipulator.block;

import appeng.block.AEBaseEntityBlock;
import com.atir.molecularmanipulator.blockentity.FoldingAssemblerBlockEntity;
import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.building.BuildingType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class FoldingAssemblerBlock extends AEBaseEntityBlock<FoldingAssemblerBlockEntity> {
    public FoldingAssemblerBlock() { super(Properties.of().strength(4).requiresCorrectToolForDrops()); }
    @Override public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = new java.util.ArrayList<>(super.getDrops(state, builder));
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof FoldingAssemblerBlockEntity assembler && assembler.hasPendingPackages()) drops.removeIf(stack -> stack.is(asItem()));
        return drops;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) player.displayClientMessage(Component.translatable("message.molecularmanipulator.folding.assembler"), false);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
