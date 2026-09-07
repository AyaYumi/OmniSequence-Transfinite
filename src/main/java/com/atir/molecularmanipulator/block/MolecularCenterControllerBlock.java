package com.atir.molecularmanipulator.block;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.util.InteractionUtil;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public final class MolecularCenterControllerBlock extends AEBaseEntityBlock<MolecularCenterBlockEntity> {
    public MolecularCenterControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL)
                .setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, PatternProviderBlock.PUSH_DIRECTION,
                BlockStateProperties.POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MultiblockChunkLoading.release(serverLevel, pos);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state,
            LootParams.Builder builder) {
        var drops = new ArrayList<>(super.getDrops(state, builder));
        var blockEntity = builder.getOptionalParameter(
                LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof MolecularCenterBlockEntity center
                && center.hasRemovalRecovery()) {
            drops.removeIf(stack -> stack.is(asItem()));
        }
        return drops;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            if (!level.isClientSide()) {
                var next = state.getValue(HorizontalDirectionalBlock.FACING).getClockWise();
                level.setBlockAndUpdate(pos, state.setValue(HorizontalDirectionalBlock.FACING, next));
                var be = getBlockEntity(level, pos);
                if (be != null) {
                    be.refreshStructure();
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            blockEntity.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean isMoving) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity != null && !level.isClientSide()) {
            blockEntity.refreshStructure();
        }
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
            getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() ? null : (net.minecraft.world.level.block.entity.BlockEntityTicker<T>)
                ((level1, pos, state1, blockEntity) -> ((MolecularCenterBlockEntity) blockEntity).serverTick());
    }
}
