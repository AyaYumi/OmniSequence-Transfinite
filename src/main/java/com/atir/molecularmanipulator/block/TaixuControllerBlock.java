package com.atir.molecularmanipulator.block;

import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import com.atir.molecularmanipulator.blockentity.TaixuBlockEntity;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class TaixuControllerBlock extends AEBaseEntityBlock<TaixuBlockEntity> {
    public TaixuControllerBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(HorizontalDirectionalBlock.FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING))); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(stack)) {
            var machine = getBlockEntity(level, pos);
            if (!level.isClientSide() && player.mayBuild() && level.mayInteract(player, pos) && machine != null
                    && machine.operation() == TaixuBlockEntity.Operation.IDLE && !machine.formed() && !machine.motion().hasBodies())
                level.setBlockAndUpdate(pos, rotate(state, Rotation.CLOCKWISE_90));
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var machine = getBlockEntity(level, pos);
        if (machine == null) return InteractionResult.PASS;
        if (!level.isClientSide()) machine.openMenu(player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) MultiblockChunkLoading.release(server, pos);
        super.onRemove(state, level, pos, replacement, moved);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = new java.util.ArrayList<>(super.getDrops(state, builder));
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof TaixuBlockEntity machine
                && machine.hasRemovalRecovery()) drops.removeIf(stack -> stack.is(asItem()));
        return drops;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (world, pos, current, be) -> { if (be instanceof TaixuBlockEntity machine) machine.serverTick(); };
    }
}
