package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.blockentity.StarOrreryOwnership;
import com.atir.molecularmanipulator.blockentity.StarOrreryRings;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A nine-ring star orrery anchor. Legacy column states remain readable only for migration. */
public final class StarPositionAnchorBlock extends Block {
    public static final BooleanProperty FORMED = StarComputationEngineCoreBlock.FORMED;
    public static final IntegerProperty ANCHOR_POS = IntegerProperty.create("anchor_pos", 0, 8);
    public static final BooleanProperty ANCHOR_END = BooleanProperty.create("anchor_end");
    /** Distinguishes real ring members from retired column states in existing saves. */
    public static final BooleanProperty STAR_ORRERY = BooleanProperty.create("star_orrery");
    /** Selectable/collidable gem while the star_orrery BER hides the block model. */
    public static final VoxelShape STAR_ORRERY_SHAPE = Block.box(4.0D, 3.0D, 4.0D, 12.0D, 13.0D, 12.0D);
    private static final int INTEGRITY_INTERVAL = 40;
    // These states only change appearance. Avoid neighbor/shape propagation,
    // which can otherwise synchronously read unloaded chunks at the column boundary.
    public static final int VISUAL_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public enum Support {
        SUPPORTED, UNSUPPORTED, UNLOADED
    }

    public StarPositionAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(ANCHOR_POS, 0)
                .setValue(ANCHOR_END, false)
                .setValue(STAR_ORRERY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, FORMED, ANCHOR_POS, ANCHOR_END, STAR_ORRERY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING,
                rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        int index = state.getValue(ANCHOR_POS);
        BlockState rotated = rotate(state, mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
        // Ring numbers are absolute around the core, not a 3x3 row/column index.
        return state.getValue(STAR_ORRERY) ? rotated
                : rotated.setValue(ANCHOR_POS, index / 3 * 3 + 2 - index % 3);
    }

    public static boolean isStarOrrery(BlockState state) {
        return state.hasProperty(STAR_ORRERY) && state.getValue(STAR_ORRERY);
    }

    /** Compatibility state factory; legacy columns no longer receive runtime support. */
    public static BlockState formedState(BlockState state, Direction facing, int index, boolean end) {
        return state.setValue(FORMED, true)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(ANCHOR_POS, index)
                .setValue(ANCHOR_END, end)
                .setValue(STAR_ORRERY, false);
    }

    /** StarOrrery members store the zero-based ring number in {@code anchor_pos}. */
    public static BlockState starOrreryState(BlockState state, Direction facing, int ring, boolean end) {
        if (ring < 0 || ring >= StarOrreryRings.RINGS) {
            throw new IllegalArgumentException("StarOrrery ring must be 0..8");
        }
        return state.setValue(FORMED, true)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(ANCHOR_POS, ring)
                .setValue(ANCHOR_END, end)
                .setValue(STAR_ORRERY, true);
    }

    public static BlockState unformedState(BlockState state) {
        return state.setValue(FORMED, false)
                .setValue(ANCHOR_POS, 0)
                .setValue(ANCHOR_END, false)
                .setValue(STAR_ORRERY, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The star_orrery BER draws the smooth ring and lit/unlit gem; the block
        // itself only remains as a small selectable holder.
        return state.getValue(STAR_ORRERY) && state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAR_ORRERY) ? STAR_ORRERY_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAR_ORRERY) ? STAR_ORRERY_SHAPE : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(STAR_ORRERY) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(STAR_ORRERY) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(STAR_ORRERY) || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return com.atir.molecularmanipulator.starcomputation.StarComputationInteraction.open(level, pos, player);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        scheduleIntegrityCheck(level, pos, state);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos,
            boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (!level.isClientSide && state.getValue(FORMED)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    public static void scheduleIntegrityCheck(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide && state.getValue(FORMED)) {
            // Scheduled ticks survive chunk unloads. This also repairs orphaned members
            // if their controller was removed while their chunk was not loaded.
            level.scheduleTick(pos, state.getBlock(), INTEGRITY_INTERVAL + Math.floorMod(pos.hashCode(), 10));
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FORMED)) {
            return;
        }
        if (state.getValue(STAR_ORRERY)) {
            var support = starOrrerySupport(level, pos, state);
            if (support == Support.UNSUPPORTED) {
                // Orphan self-healing is local: never rewrite a whole legacy pillar
                // or another controller's ring from a star orrery member.
                level.setBlock(pos, unformedState(state), VISUAL_UPDATE_FLAGS);
            } else {
                scheduleIntegrityCheck(level, pos, state);
            }
            return;
        }
        // Retired columns never remain formed, even if an old core/port still
        // looks formed or the inward chunk is unavailable. Repair only this block.
        level.setBlock(pos, unformedState(state), VISUAL_UPDATE_FLAGS);
    }

    /** Null states defer ring checks; retired columns are unsupported without reading any neighbors. */
    public static Support support(BlockPos pos, BlockState state, Function<BlockPos, BlockState> reader) {
        return state.getValue(STAR_ORRERY) ? starOrrerySupport(pos, state, reader) : Support.UNSUPPORTED;
    }

    /**
     * Geometry-only star_orrery support used by state tests and non-server callers.
     * The live server tick additionally consults the per-level ownership map.
     */
    private static Support starOrrerySupport(BlockPos pos, BlockState state,
            Function<BlockPos, BlockState> reader) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int ring = state.getValue(ANCHOR_POS);
        boolean unloaded = false;
        for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
            var corePos = pos.subtract(StarOrreryLayout.offset(facing, ring, slot));
            var core = reader.apply(corePos);
            if (core == null) {
                unloaded = true;
            } else if (isStarOrreryCore(core, facing)) {
                return Support.SUPPORTED;
            }
        }
        return unloaded ? Support.UNLOADED : Support.UNSUPPORTED;
    }

    private static boolean isStarOrreryCore(BlockState state, Direction facing) {
        return state.getBlock() instanceof StarComputationEngineCoreBlock
                && state.getValue(StarComputationEngineCoreBlock.FORMED)
                && state.getValue(StarComputationEngineCoreBlock.STAR_ORRERY)
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    /** Ownership-aware self check for one star_orrery member; never touches legacy columns. */
    private static Support starOrrerySupport(ServerLevel level, BlockPos pos, BlockState state) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int ring = state.getValue(ANCHOR_POS);
        var owner = StarOrreryOwnership.owner(level, pos);
        if (owner != null) {
            if (!level.hasChunkAt(owner)) {
                return Support.UNLOADED;
            }
            if (level.getBlockEntity(owner) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isStarOrrery() && core.isFormed()
                    && core.isStarOrreryMember(pos)
                    && core.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing) {
                return Support.SUPPORTED;
            }
            StarOrreryOwnership.releaseIfOwner(level, pos, owner);
        }
        boolean unloaded = false;
        for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
            var corePos = pos.subtract(StarOrreryLayout.offset(facing, ring, slot));
            if (!level.hasChunkAt(corePos)) {
                unloaded = true;
                continue;
            }
            if (!(level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core)) {
                continue;
            }
            if (core.isStarOrrery() && core.isFormed()
                    && core.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing
                    && (core.isStarOrreryMember(pos) || core.mayOwnStarOrrerySlot(ring, slot))) {
                return Support.SUPPORTED;
            }
        }
        return unloaded ? Support.UNLOADED : Support.UNSUPPORTED;
    }

    @Nullable
    public static BlockState loadedState(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return level.hasChunkAt(pos) ? level.getBlockState(pos) : null;
    }
}
