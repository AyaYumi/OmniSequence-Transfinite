package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.atir.molecularmanipulator.world.LabCrystallization;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A physical single-stack input or output with direct, GUI-free interaction. */
public final class HighEnergyLabCradleBlock extends HighEnergyLabDecorationBlock implements EntityBlock {
    private final boolean input;

    public HighEnergyLabCradleBlock(Properties properties, boolean input) {
        super(properties);
        this.input = input;
    }
    public boolean input() { return input; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HighEnergyLabCradleBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || !input || type != HighEnergyLabContent.CRADLE_BE.get() ? null
                : (world, pos, current, entity) -> ((HighEnergyLabCradleBlockEntity)entity).serverTick();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            if (!input || LabCrystallization.resultFor(stack).isEmpty()) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle) {
                cradle.insert(stack, player.getAbilities().instabuild);
            }
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle) {
            if (!input || player.isShiftKeyDown()) cradle.take(player);
            else if (!cradle.start() && cradle.working().isEmpty()) player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.molecularmanipulator.lab.ready_required"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean movedByPiston) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle) cradle.dropContents();
        super.onRemove(state, level, pos, next, movedByPiston);
    }

    @Override protected VoxelShape shape(BlockState state) { return LabPlatformGeometry.equipment(input ? "input" : "output", state.getValue(FACING)); }
    @Override protected BlockPos inletOffset(BlockState state) { return (input ? LabPlatformGeometry.INPUT : LabPlatformGeometry.OUTPUT).multiply(-1); }
}
