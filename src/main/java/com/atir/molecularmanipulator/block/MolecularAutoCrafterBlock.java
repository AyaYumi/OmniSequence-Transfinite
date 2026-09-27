package com.atir.molecularmanipulator.block;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.List;

public final class MolecularAutoCrafterBlock extends AEBaseEntityBlock<MolecularAutoCrafterBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public MolecularAutoCrafterBlock() {
        super(metalProps().noOcclusion());
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL)
                .setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, PatternProviderBlock.PUSH_DIRECTION, WORKING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var heldItem = player.getItemInHand(hand);
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            if (!level.isClientSide()) {
                level.setBlockAndUpdate(pos, state.cycle(HorizontalDirectionalBlock.FACING));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return useWithoutItem(state, level, pos, player, hit);
    }

    private InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide() && getBlockEntity(level, pos) != null) {
            getBlockEntity(level, pos).openMenu(player, MenuLocators.forBlockEntity(getBlockEntity(level, pos)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = new java.util.ArrayList<>(super.getDrops(state, builder));
        var entity = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (entity instanceof MolecularAutoCrafterBlockEntity crafter && crafter.hasRemovalRecovery()) {
            drops.removeIf(stack -> stack.is(asItem()));
        }
        return drops;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving) {
        var entity = getBlockEntity(level, pos);
        if (entity != null) entity.getLogic().updateRedstoneState();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter context,
            List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "tooltip.molecularmanipulator.molecular_auto_crafter"));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() ? null : (serverLevel, pos, currentState, blockEntity) -> {
            if (blockEntity instanceof MolecularAutoCrafterBlockEntity crafter) crafter.serverTick();
        };
    }
}
