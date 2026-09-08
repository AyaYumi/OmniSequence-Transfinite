package com.atir.molecularmanipulator.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** AE2 processing-pattern host used by the optional fabrication service bay. */
public final class MatterFabricationPatternAssemblyBlock
        extends AEBaseEntityBlock<MatterFabricationPatternAssemblyBlockEntity> {
    public MatterFabricationPatternAssemblyBlock() {
        super(Properties.of().strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                .lightLevel(state -> 13));
        registerDefaultState(defaultBlockState().setValue(
                PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
            getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.atir.molecularmanipulator.registry.ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_BE.get()) return null;
        return (tickLevel, pos, current, entity) -> ((MatterFabricationPatternAssemblyBlockEntity) entity).serverTick();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = new ArrayList<>(super.getDrops(state, builder));
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MatterFabricationPatternAssemblyBlockEntity machine
                && machine.hasRemovalRecovery()) {
            drops.removeIf(stack -> stack.is(asItem()));
        }
        return drops;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PatternProviderBlock.PUSH_DIRECTION);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean moving) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity != null) {
            blockEntity.getLogic().updateRedstoneState();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            setSide(level, pos, hit.getDirection());
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return useWithoutItem(state, level, pos, player, hit);
    }

    private InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            blockEntity.openAssemblyMenu(player, MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void setSide(Level level, BlockPos pos, Direction facing) {
        var currentState = level.getBlockState(pos);
        var pushSide = currentState.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();
        PushDirection next;
        if (pushSide == facing.getOpposite()) {
            next = PushDirection.fromDirection(facing);
        } else if (pushSide == facing) {
            next = PushDirection.ALL;
        } else if (pushSide == null) {
            next = PushDirection.fromDirection(facing.getOpposite());
        } else {
            next = PushDirection.fromDirection(Platform.rotateAround(pushSide, facing));
        }
        level.setBlockAndUpdate(pos, currentState.setValue(PatternProviderBlock.PUSH_DIRECTION, next));
    }
}
