package com.atir.molecularmanipulator.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import appeng.block.AEBaseEntityBlock;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;

public final class MatterFabricationPortBlock extends AEBaseEntityBlock<MatterFabricationPortBlockEntity> {
    private final MatterFabricationPortType portType;

    public MatterFabricationPortBlock(Properties properties, MatterFabricationPortType portType) {
        super(properties);
        this.portType = portType;
    }

    public MatterFabricationPortType getPortType() {
        return portType;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = new ArrayList<>(super.getDrops(state, builder));
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MatterFabricationPortBlockEntity machine
                && machine.hasRemovalRecovery()) {
            drops.removeIf(stack -> stack.is(asItem()));
        }
        return drops;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || portType.isInput() || type != ModContent.MATTER_FABRICATION_PORT_BE.get()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> ((MatterFabricationPortBlockEntity) blockEntity).serverTick();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            blockEntity.openMenu(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
