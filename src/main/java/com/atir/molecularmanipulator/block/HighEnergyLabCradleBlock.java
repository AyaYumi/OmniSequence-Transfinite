package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.atir.molecularmanipulator.world.LabCrystallization;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
    public static final MapCodec<HighEnergyLabCradleBlock> INPUT_CODEC = simpleCodec(properties -> new HighEnergyLabCradleBlock(properties, true));
    public static final MapCodec<HighEnergyLabCradleBlock> OUTPUT_CODEC = simpleCodec(properties -> new HighEnergyLabCradleBlock(properties, false));
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!input || LabCrystallization.resultFor(stack).isEmpty()) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle) {
            cradle.insert(stack, player.getAbilities().instabuild);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle) {
            if (!input || player.isShiftKeyDown()) cradle.take(player);
            else if (!cradle.start() && cradle.working().isEmpty()) player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.molecularmanipulator.lab.ready_required"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean movedByPiston) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle) cradle.dropContents();
        super.onRemove(state, level, pos, next, movedByPiston);
    }

    @Override protected MapCodec<HighEnergyLabCradleBlock> codec() { return input ? INPUT_CODEC : OUTPUT_CODEC; }
    @Override protected VoxelShape shape(BlockState state) { return LabPlatformGeometry.equipment(input ? "input" : "output", state.getValue(FACING)); }
    @Override protected BlockPos inletOffset(BlockState state) { return (input ? LabPlatformGeometry.INPUT : LabPlatformGeometry.OUTPUT).multiply(-1); }
}
