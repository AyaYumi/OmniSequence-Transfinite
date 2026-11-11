package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.StarSeaCalculationMainframeBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.registry.StarSeaCalculationContent;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** One item places the complete M1 tower, with one BE and passive, shape-clipped members. */
public final class StarSeaCalculationMainframeBlock extends Block implements EntityBlock {
    public static final int CORE_PART = 2;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0,
            StarSeaCalculationGeometry.PART_COUNT - 1);

    public StarSeaCalculationMainframeBlock() {
        super(Properties.of().strength(4F, 12F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                .noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(defaultBlockState().setValue(PART, CORE_PART)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, HorizontalDirectionalBlock.FACING);
    }

    /** Shared by the item and ghost: permissions/space refer to every cell, not only the anchor. */
    public boolean canPlaceBuilding(BlockPlaceContext context, Direction facing) {
        var level = context.getLevel();
        var origin = context.getClickedPos();
        var player = context.getPlayer();
        if (player != null && (player.isSpectator() || !player.mayBuild())) return false;
        for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++) {
            var target = origin.offset(StarSeaCalculationGeometry.offset(part, facing));
            if (level.isOutsideBuildHeight(target) || !level.hasChunkAt(target)
                    || !level.getWorldBorder().isWithinBounds(target) || BuildingTransfer.locked(level, target)) return false;
            if (!level.getBlockState(target).canBeReplaced() || level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()) return false;
            if (player != null && (!level.mayInteract(player, target)
                    || !player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand()))) return false;
            if (part < 25 && !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) return false;
            var member = defaultBlockState().setValue(PART, part).setValue(HorizontalDirectionalBlock.FACING, facing);
            if (!level.isUnobstructed(member, target, CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection().getOpposite();
        return canPlaceBuilding(context, facing)
                ? defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing) : null;
    }

    /** BlockItem's normal snapshot capture makes claim-mod cancellation roll back the WHOLE building. */
    public boolean placeBuilding(BlockPlaceContext context, BlockState coreState) {
        var facing = coreState.getValue(HorizontalDirectionalBlock.FACING);
        if (!canPlaceBuilding(context, facing)) return false;
        var level = context.getLevel();
        var origin = context.getClickedPos();
        var placed = new ArrayList<BlockPos>();
        var previous = new ArrayList<BlockState>();
        // Write the owner last. Failed partial placement has no live controller to dismantle or drop.
        for (int step = 0; step < StarSeaCalculationGeometry.PART_COUNT; step++) {
            int part = step == StarSeaCalculationGeometry.PART_COUNT - 1 ? CORE_PART
                    : step < CORE_PART ? step : step + 1;
            var target = origin.offset(StarSeaCalculationGeometry.offset(part, facing));
            var before = level.getBlockState(target);
            var member = coreState.setValue(PART, part);
            if (!level.setBlock(target, member, Block.UPDATE_ALL)) {
                rollback(level, placed, previous);
                return false;
            }
            placed.add(target);
            previous.add(before);
        }
        return true;
    }

    private static void rollback(Level level, ArrayList<BlockPos> placed, ArrayList<BlockState> previous) {
        boolean restoring = level.restoringBlockSnapshots;
        boolean capturing = level.captureBlockSnapshots;
        level.restoringBlockSnapshots = true;
        level.captureBlockSnapshots = false;
        try {
            for (int i = placed.size() - 1; i >= 0; i--) level.setBlock(placed.get(i), previous.get(i), Block.UPDATE_ALL);
        } finally {
            level.restoringBlockSnapshots = restoring;
            level.captureBlockSnapshots = capturing;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof StarSeaCalculationMainframeBlockEntity core) {
            core.getMainNode().setOwningPlayer(player);
        }
    }

    public static BlockPos corePosition(BlockPos partPos, BlockState state) {
        return partPos.subtract(StarSeaCalculationGeometry.offset(state.getValue(PART),
                state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    public static boolean structureComplete(Level level, BlockPos origin, Direction facing) {
        for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++) {
            var target = origin.offset(StarSeaCalculationGeometry.offset(part, facing));
            if (!level.hasChunkAt(target)) return false;
            var state = level.getBlockState(target);
            if (!(state.getBlock() instanceof StarSeaCalculationMainframeBlock)
                    || state.getValue(PART) != part || state.getValue(HorizontalDirectionalBlock.FACING) != facing) return false;
        }
        return true;
    }

    public static AABB bounds(BlockPos origin, Direction facing) {
        var opposite = origin.offset(StarSeaCalculationGeometry.offset(
                StarSeaCalculationGeometry.partIndex(2, 8, 4), facing));
        var corner = origin.offset(StarSeaCalculationGeometry.offset(
                StarSeaCalculationGeometry.partIndex(-2, 0, 0), facing));
        return new AABB(corner.getX(), corner.getY(), corner.getZ(),
                opposite.getX(), opposite.getY(), opposite.getZ()).expandTowards(1, 1, 1);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(corePosition(pos, state)) instanceof StarSeaCalculationMainframeBlockEntity core) {
            core.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == CORE_PART ? new StarSeaCalculationMainframeBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != CORE_PART || type != StarSeaCalculationContent.BLOCK_ENTITY.get()) return null;
        return (world, pos, current, entity) -> ((StarSeaCalculationMainframeBlockEntity) entity).serverTick();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide() && !level.restoringBlockSnapshots && !state.is(next.getBlock())) {
            var origin = corePosition(pos, state);
            var facing = state.getValue(HorizontalDirectionalBlock.FACING);
            if (state.getValue(PART) == CORE_PART) {
                for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++) {
                    if (part == CORE_PART) continue;
                    var target = origin.offset(StarSeaCalculationGeometry.offset(part, facing));
                    // Removal may touch up to four chunks; resolve them now rather than leave orphan members.
                    var member = level.getBlockState(target);
                    if (member.is(this) && member.getValue(PART) == part
                            && member.getValue(HorizontalDirectionalBlock.FACING) == facing) level.removeBlock(target, false);
                }
            } else if (ownsCore(level, origin, facing)) {
                level.destroyBlock(origin, true);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    private boolean ownsCore(Level level, BlockPos pos, Direction facing) {
        var state = level.getBlockState(pos);
        return state.is(this) && state.getValue(PART) == CORE_PART
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PART) != CORE_PART) {
            var origin = corePosition(pos, state);
            if (ownsCore(level, origin, state.getValue(HorizontalDirectionalBlock.FACING))) {
                var coreState = level.getBlockState(origin);
                if (!player.getAbilities().instabuild && state.canHarvestBlock(level, pos, player)) {
                    dropResources(coreState, level, origin, level.getBlockEntity(origin), player, player.getMainHandItem());
                }
                level.removeBlock(origin, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StarSeaCalculationGeometry.shape(state.getValue(PART), state.getValue(HorizontalDirectionalBlock.FACING));
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int x = StarSeaCalculationGeometry.offsetX(state.getValue(PART));
        // Reflection reverses local handedness, in addition to the orientation.
        return mirror == Mirror.NONE ? state : state.setValue(HorizontalDirectionalBlock.FACING, mirror.mirror(facing))
                .setValue(PART, StarSeaCalculationGeometry.partIndex(-x,
                        StarSeaCalculationGeometry.offsetY(state.getValue(PART)),
                        StarSeaCalculationGeometry.offsetZ(state.getValue(PART))));
    }
}
