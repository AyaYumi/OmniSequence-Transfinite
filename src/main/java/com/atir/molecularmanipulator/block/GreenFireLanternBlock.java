package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.GreenFireLanternBlockEntity;
import com.atir.molecularmanipulator.registry.GreenFireLanternContent;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Four open faces, placed on a supporting floor and retrieved directly into a free hand. */
public final class GreenFireLanternBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final MapCodec<GreenFireLanternBlock> CODEC = simpleCodec(GreenFireLanternBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(4.5, 0, 4.5, 11.5, 15, 11.5);

    public GreenFireLanternBlock() {
        this(Properties.of().strength(1.5F).sound(SoundType.LANTERN).noOcclusion()
                .lightLevel(state -> state.getValue(LIT) ? 14 : 0).pushReaction(PushReaction.DESTROY));
    }
    private GreenFireLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(WATERLOGGED, false));
    }
    @Override protected MapCodec<GreenFireLanternBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, WATERLOGGED);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GreenFireLanternBlockEntity(pos, state);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) return null;
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, water)
                .setValue(LIT, !water && GreenFireLanternContent.isLit(context.getItemInHand()));
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            // Returning water directly bypasses vanilla's destruction/drop path.
            // A scheduled destruction returns the contained fluid and drops one lamp.
            level.scheduleTick(pos, this, 1);
            return state;
        }
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return state.setValue(LIT, false);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }
    @Override protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    @Override public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (fluid.getType() != Fluids.WATER || state.getValue(WATERLOGGED)) return false;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(WATERLOGGED, true).setValue(LIT, false), 3);
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return true;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) take(level, pos, state, player, hand);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide) take(level, pos, state, player, InteractionHand.MAIN_HAND);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void take(Level level, BlockPos pos, BlockState state, Player player, InteractionHand requested) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return;
        InteractionHand free = player.getItemInHand(requested).isEmpty() ? requested
                : player.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().isEmpty() ? InteractionHand.OFF_HAND : null;
        if (free == null) {
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.lantern_free_hand"), true);
            return;
        }
        ItemStack stack = GreenFireLanternContent.stack(state.getValue(LIT));
        // Retrieval is one transfer, never a second loot drop.
        level.setBlock(pos, state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState()
                : Blocks.AIR.defaultBlockState(), 3);
        player.setItemInHand(free, stack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return super.getDrops(state, params).stream().map(stack -> {
            if (GreenFireLanternContent.isLantern(stack)) GreenFireLanternContent.setLit(stack, state.getValue(LIT));
            return stack;
        }).toList();
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + .5, pos.getY() + .4, pos.getZ() + .5, 0, .005, 0);
    }
}
