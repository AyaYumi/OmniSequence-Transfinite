package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.EndWarpPadBlockEntity;
import com.atir.molecularmanipulator.registry.WarpContent;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ItemInteractionResult;
import org.jetbrains.annotations.Nullable;

/** The 3x3x3 physical footprint of the editable coordinate warp pad. */
public final class EndWarpPadBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);
    public static final int CORE_PART = 13;
    private static final Set<BlockPos> OFFSETS = offsets();

    public EndWarpPadBlock() {
        super(Properties.of().strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> state.getValue(PART) == CORE_PART ? 5 : 0));
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(PART, CORE_PART));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection().getOpposite();
        for (var offset : OFFSETS) {
            var target = context.getClickedPos().offset(offset);
            if (context.getLevel().isOutsideBuildHeight(target)
                    || !context.getLevel().getBlockState(target).canBeReplaced()
                    || context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, Direction.UP, context.getItemInHand())) return null;
            var partState = defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(PART, WarpPadGeometry.partIndex(offset.getX(), offset.getY(), offset.getZ()));
            if (!context.getLevel().isUnobstructed(partState, target,
                    net.minecraft.world.phys.shapes.CollisionContext.empty())) return null;
        }
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(PART, CORE_PART);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || state.getValue(PART) != CORE_PART) return;
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        for (var offset : OFFSETS) {
            if (offset.equals(BlockPos.ZERO)) continue;
            level.setBlock(pos.offset(offset), defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(PART, EndWarpPadBlockEntity.partForOffset(offset.getX(), offset.getY(), offset.getZ())), 3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        var core = corePosition(pos, state);
        if (!(level.getBlockEntity(core) instanceof EndWarpPadBlockEntity pad)) return InteractionResult.PASS;
        return pad.useEmptyHand(player, hit.getLocation());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.ENDER_PEARL)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        var core = corePosition(pos, state);
        if (!(level.getBlockEntity(core) instanceof EndWarpPadBlockEntity pad)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return pad.usePearl(player, hand, hit.getLocation());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == CORE_PART ? new EndWarpPadBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != CORE_PART || type != WarpContent.WARP_PAD_BE.get()) return null;
        return (world, pos, current, entity) -> EndWarpPadBlockEntity.serverTick(
                world, pos, current, (EndWarpPadBlockEntity)entity);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return WarpPadGeometry.shape(state.getValue(PART), state.getValue(HorizontalDirectionalBlock.FACING));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        int dx = WarpPadGeometry.offsetX(state.getValue(PART));
        int dz = WarpPadGeometry.offsetZ(state.getValue(PART));
        int[] rotated = rotateOffset(dx, dz, rotation);
        return state.setValue(HorizontalDirectionalBlock.FACING,
                        rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)))
                .setValue(PART, EndWarpPadBlockEntity.partForOffset(rotated[0], WarpPadGeometry.offsetY(state.getValue(PART)), rotated[1]));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        int dx = WarpPadGeometry.offsetX(state.getValue(PART));
        int dz = WarpPadGeometry.offsetZ(state.getValue(PART));
        if (mirror == Mirror.LEFT_RIGHT) dz = -dz;
        else if (mirror == Mirror.FRONT_BACK) dx = -dx;
        return state.setValue(HorizontalDirectionalBlock.FACING,
                        mirror.mirror(state.getValue(HorizontalDirectionalBlock.FACING)))
                .setValue(PART, EndWarpPadBlockEntity.partForOffset(dx, WarpPadGeometry.offsetY(state.getValue(PART)), dz));
    }

    /** Core removal clears members too, including explosions and command replacement. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            var core = corePosition(pos, state);
            var facing = state.getValue(HorizontalDirectionalBlock.FACING);
            if (state.getValue(PART) == CORE_PART) {
                if (level.getBlockEntity(pos) instanceof EndWarpPadBlockEntity pad && pad.hasPearl()) {
                    popResource(level, pos, new ItemStack(Items.ENDER_PEARL));
                }
                for (var offset : OFFSETS) {
                    if (offset.equals(BlockPos.ZERO)) continue;
                    var target = core.offset(offset);
                    var member = level.getBlockState(target);
                    if (member.is(this) && member.getValue(HorizontalDirectionalBlock.FACING) == facing
                            && member.getValue(PART) == WarpPadGeometry.partIndex(offset.getX(), offset.getY(), offset.getZ())) {
                        level.removeBlock(target, false);
                    }
                }
            } else if (level.getBlockState(core).is(this)
                    && level.getBlockState(core).getValue(PART) == CORE_PART) {
                level.destroyBlock(core, true);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PART) != CORE_PART) {
            var core = corePosition(pos, state);
            var coreState = level.getBlockState(core);
            if (coreState.is(this) && coreState.getValue(PART) == CORE_PART) {
                if (!player.getAbilities().instabuild && coreState.canHarvestBlock(level, core, player)) {
                    dropResources(coreState, level, core, level.getBlockEntity(core), player, player.getMainHandItem());
                }
                level.removeBlock(core, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static BlockPos corePosition(BlockPos partPos, BlockState state) {
        int part = state.getValue(PART);
        return partPos.offset(-WarpPadGeometry.offsetX(part), -WarpPadGeometry.offsetY(part),
                -WarpPadGeometry.offsetZ(part));
    }

    private static int[] rotateOffset(int dx, int dz, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[] {dx, dz};
            case CLOCKWISE_90 -> new int[] {-dz, dx};
            case CLOCKWISE_180 -> new int[] {-dx, -dz};
            case COUNTERCLOCKWISE_90 -> new int[] {dz, -dx};
        };
    }

    private static Set<BlockPos> offsets() {
        var result = new java.util.LinkedHashSet<BlockPos>();
        for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) result.add(new BlockPos(dx, dy, dz));
        return java.util.Collections.unmodifiableSet(result);
    }
}
