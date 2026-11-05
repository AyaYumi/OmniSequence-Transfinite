package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import com.atir.molecularmanipulator.blockentity.EntropyArmillaryOwnership;
import com.atir.molecularmanipulator.blockentity.EntropyArmillaryRings;
import com.atir.molecularmanipulator.blockentity.StarComputationAnchorStructure;
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

/** An individually rendered member of a 3x3 entropy-anchor column or a nine-ring armillary. */
public final class EntropyAnchorBlock extends Block {
    public static final BooleanProperty FORMED = StarComputationEngineCoreBlock.FORMED;
    public static final IntegerProperty ANCHOR_POS = IntegerProperty.create("anchor_pos", 0, 8);
    public static final BooleanProperty ANCHOR_END = BooleanProperty.create("anchor_end");
    /** Opt-in armillary marker; legacy column anchors always keep this false. */
    public static final BooleanProperty ARMILLARY = BooleanProperty.create("armillary");
    /** Selectable/collidable gem while the armillary BER hides the block model. */
    public static final VoxelShape ARMILLARY_SHAPE = Block.box(4.0D, 3.0D, 4.0D, 12.0D, 13.0D, 12.0D);
    private static final int INTEGRITY_INTERVAL = 40;
    // These states only change appearance. Avoid neighbor/shape propagation,
    // which can otherwise synchronously read unloaded chunks at the column boundary.
    public static final int VISUAL_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public enum Support {
        SUPPORTED, UNSUPPORTED, UNLOADED
    }

    public EntropyAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(ANCHOR_POS, 0)
                .setValue(ANCHOR_END, false)
                .setValue(ARMILLARY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, FORMED, ANCHOR_POS, ANCHOR_END, ARMILLARY);
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
        return state.getValue(ARMILLARY) ? rotated
                : rotated.setValue(ANCHOR_POS, index / 3 * 3 + 2 - index % 3);
    }

    public static boolean isArmillary(BlockState state) {
        return state.hasProperty(ARMILLARY) && state.getValue(ARMILLARY);
    }

    public static BlockState formedState(BlockState state, Direction facing, int index, boolean end) {
        return state.setValue(FORMED, true)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(ANCHOR_POS, index)
                .setValue(ANCHOR_END, end)
                .setValue(ARMILLARY, false);
    }

    /** Armillary members store the zero-based ring number in {@code anchor_pos}. */
    public static BlockState armillaryState(BlockState state, Direction facing, int ring, boolean end) {
        if (ring < 0 || ring >= EntropyArmillaryRings.RINGS) {
            throw new IllegalArgumentException("Armillary ring must be 0..8");
        }
        return state.setValue(FORMED, true)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(ANCHOR_POS, ring)
                .setValue(ANCHOR_END, end)
                .setValue(ARMILLARY, true);
    }

    public static BlockState unformedState(BlockState state) {
        return state.setValue(FORMED, false)
                .setValue(ANCHOR_POS, 0)
                .setValue(ANCHOR_END, false)
                .setValue(ARMILLARY, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The armillary BER draws the smooth ring and lit/unlit gem; the block
        // itself only remains as a small selectable holder.
        return state.getValue(ARMILLARY) && state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ARMILLARY) ? ARMILLARY_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ARMILLARY) ? ARMILLARY_SHAPE : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(ARMILLARY) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(ARMILLARY) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(ARMILLARY) || super.propagatesSkylightDown(state, level, pos);
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
        if (state.getValue(ARMILLARY)) {
            var support = armillarySupport(level, pos, state);
            if (support == Support.UNSUPPORTED) {
                // Orphan self-healing is local: never rewrite a whole legacy pillar
                // or another controller's ring from an armillary member.
                level.setBlock(pos, unformedState(state), VISUAL_UPDATE_FLAGS);
            } else {
                scheduleIntegrityCheck(level, pos, state);
            }
            return;
        }
        if (support(pos, state, part -> loadedState(level, part)) == Support.UNSUPPORTED) {
            resetLayer(level, pos, state);
        } else {
            scheduleIntegrityCheck(level, pos, state);
        }
    }

    /** Null states represent unloaded chunks, not air. Each legacy member depends only on the layer inward. */
    public static Support support(BlockPos pos, BlockState state, Function<BlockPos, BlockState> reader) {
        if (state.getValue(ARMILLARY)) {
            return armillarySupport(pos, state, reader);
        }
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var center = StarComputationAnchorStructure.centerOf(pos, facing, state.getValue(ANCHOR_POS));
        var ownLayer = checkLayer(center, facing, state.getValue(ANCHOR_END), reader);
        if (ownLayer == Support.UNSUPPORTED) {
            return ownLayer;
        }

        var inward = center.relative(facing.getOpposite());
        var neighbor = reader.apply(inward);
        Support source;
        if (neighbor == null) {
            source = Support.UNLOADED;
        } else if (neighbor.getBlock() instanceof EntropyAnchorBlock) {
            source = checkLayer(inward, facing, false, reader);
        } else if (neighbor.getBlock() instanceof StarComputationObservationPortBlock
                && neighbor.getValue(StarComputationObservationPortBlock.FORMED)) {
            var core = reader.apply(inward.relative(facing.getOpposite()));
            source = core == null ? Support.UNLOADED
                    : core.getBlock() instanceof StarComputationEngineCoreBlock
                            && core.getValue(StarComputationEngineCoreBlock.FORMED)
                                    ? Support.SUPPORTED : Support.UNSUPPORTED;
        } else {
            source = Support.UNSUPPORTED;
        }
        if (source == Support.UNSUPPORTED) {
            return source;
        }
        return ownLayer == Support.UNLOADED || source == Support.UNLOADED ? Support.UNLOADED : Support.SUPPORTED;
    }

    /**
     * Geometry-only armillary support used by state tests and non-server callers.
     * The live server tick additionally consults the per-level ownership map.
     */
    private static Support armillarySupport(BlockPos pos, BlockState state,
            Function<BlockPos, BlockState> reader) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int ring = state.getValue(ANCHOR_POS);
        boolean unloaded = false;
        for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
            var corePos = pos.subtract(EntropyArmillaryLayout.offset(facing, ring, slot));
            var core = reader.apply(corePos);
            if (core == null) {
                unloaded = true;
            } else if (isArmillaryCore(core, facing)) {
                return Support.SUPPORTED;
            }
        }
        return unloaded ? Support.UNLOADED : Support.UNSUPPORTED;
    }

    private static boolean isArmillaryCore(BlockState state, Direction facing) {
        return state.getBlock() instanceof StarComputationEngineCoreBlock
                && state.getValue(StarComputationEngineCoreBlock.FORMED)
                && state.getValue(StarComputationEngineCoreBlock.ARMILLARY)
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    /** Ownership-aware self check for one armillary member; never touches legacy columns. */
    private static Support armillarySupport(ServerLevel level, BlockPos pos, BlockState state) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int ring = state.getValue(ANCHOR_POS);
        var owner = EntropyArmillaryOwnership.owner(level, pos);
        if (owner != null) {
            if (!level.hasChunkAt(owner)) {
                return Support.UNLOADED;
            }
            if (level.getBlockEntity(owner) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isArmillary() && core.isFormed()
                    && core.isArmillaryMember(pos)
                    && core.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing) {
                return Support.SUPPORTED;
            }
            EntropyArmillaryOwnership.releaseIfOwner(level, pos, owner);
        }
        boolean unloaded = false;
        for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
            var corePos = pos.subtract(EntropyArmillaryLayout.offset(facing, ring, slot));
            if (!level.hasChunkAt(corePos)) {
                unloaded = true;
                continue;
            }
            if (!(level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core)) {
                continue;
            }
            if (core.isArmillary() && core.isFormed()
                    && core.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing
                    && (core.isArmillaryMember(pos) || core.mayOwnArmillarySlot(ring, slot))) {
                return Support.SUPPORTED;
            }
        }
        return unloaded ? Support.UNLOADED : Support.UNSUPPORTED;
    }

    private static Support checkLayer(BlockPos center, Direction facing, boolean end,
            Function<BlockPos, BlockState> reader) {
        boolean unloaded = false;
        for (int index = 0; index < StarComputationAnchorStructure.PARTS_PER_LAYER; index++) {
            var state = reader.apply(StarComputationAnchorStructure.inLayer(center, facing, index));
            if (state == null) {
                unloaded = true;
            } else if (!(state.getBlock() instanceof EntropyAnchorBlock)
                    || !state.getValue(FORMED)
                    || state.getValue(ARMILLARY)
                    || state.getValue(HorizontalDirectionalBlock.FACING) != facing
                    || state.getValue(ANCHOR_POS) != index
                    || state.getValue(ANCHOR_END) != end) {
                return Support.UNSUPPORTED;
            }
        }
        return unloaded ? Support.UNLOADED : Support.SUPPORTED;
    }

    private static void resetLayer(Level level, BlockPos pos, BlockState member) {
        var facing = member.getValue(HorizontalDirectionalBlock.FACING);
        var center = StarComputationAnchorStructure.centerOf(pos, facing, member.getValue(ANCHOR_POS));
        for (int index = 0; index < StarComputationAnchorStructure.PARTS_PER_LAYER; index++) {
            var part = StarComputationAnchorStructure.inLayer(center, facing, index);
            var state = loadedState(level, part);
            if (state != null && state.getBlock() instanceof EntropyAnchorBlock
                    && state.getValue(FORMED)
                    && !state.getValue(ARMILLARY)
                    && state.getValue(HorizontalDirectionalBlock.FACING) == facing
                    && state.getValue(ANCHOR_POS) == index) {
                level.setBlock(part, unformedState(state), VISUAL_UPDATE_FLAGS);
            }
        }
    }

    @Nullable
    public static BlockState loadedState(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return level.hasChunkAt(pos) ? level.getBlockState(pos) : null;
    }
}
