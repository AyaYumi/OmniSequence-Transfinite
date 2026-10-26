package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.NegativeEntropyAnchorStructure;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/** An individually rendered member of a 3x3 entropy-anchor column. */
public final class EntropyAnchorBlock extends Block {
    public static final BooleanProperty FORMED = NegativeEntropyEngineCoreBlock.FORMED;
    public static final IntegerProperty ANCHOR_POS = IntegerProperty.create("anchor_pos", 0, 8);
    public static final BooleanProperty ANCHOR_END = BooleanProperty.create("anchor_end");
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
                .setValue(ANCHOR_END, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HorizontalDirectionalBlock.FACING, FORMED, ANCHOR_POS, ANCHOR_END);
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
        return rotate(state, mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)))
                .setValue(ANCHOR_POS, index / 3 * 3 + 2 - index % 3);
    }

    public static BlockState formedState(BlockState state, Direction facing, int index, boolean end) {
        return state.setValue(FORMED, true)
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(ANCHOR_POS, index)
                .setValue(ANCHOR_END, end);
    }

    public static BlockState unformedState(BlockState state) {
        return state.setValue(FORMED, false).setValue(ANCHOR_POS, 0).setValue(ANCHOR_END, false);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return com.atir.molecularmanipulator.entropy.NegativeEntropyInteraction.open(level, pos, player);
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
        if (support(pos, state, part -> loadedState(level, part)) == Support.UNSUPPORTED) {
            resetLayer(level, pos, state);
        } else {
            scheduleIntegrityCheck(level, pos, state);
        }
    }

    /** Null states represent unloaded chunks, not air. Each member depends only on the layer inward. */
    public static Support support(BlockPos pos, BlockState state, Function<BlockPos, BlockState> reader) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var center = NegativeEntropyAnchorStructure.centerOf(pos, facing, state.getValue(ANCHOR_POS));
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
        } else if (neighbor.getBlock() instanceof NegativeEntropyObservationPortBlock
                && neighbor.getValue(NegativeEntropyObservationPortBlock.FORMED)) {
            var core = reader.apply(inward.relative(facing.getOpposite()));
            source = core == null ? Support.UNLOADED
                    : core.getBlock() instanceof NegativeEntropyEngineCoreBlock
                            && core.getValue(NegativeEntropyEngineCoreBlock.FORMED)
                                    ? Support.SUPPORTED : Support.UNSUPPORTED;
        } else {
            source = Support.UNSUPPORTED;
        }
        if (source == Support.UNSUPPORTED) {
            return source;
        }
        return ownLayer == Support.UNLOADED || source == Support.UNLOADED ? Support.UNLOADED : Support.SUPPORTED;
    }

    private static Support checkLayer(BlockPos center, Direction facing, boolean end,
            Function<BlockPos, BlockState> reader) {
        boolean unloaded = false;
        for (int index = 0; index < NegativeEntropyAnchorStructure.PARTS_PER_LAYER; index++) {
            var state = reader.apply(NegativeEntropyAnchorStructure.inLayer(center, facing, index));
            if (state == null) {
                unloaded = true;
            } else if (!(state.getBlock() instanceof EntropyAnchorBlock)
                    || !state.getValue(FORMED)
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
        var center = NegativeEntropyAnchorStructure.centerOf(pos, facing, member.getValue(ANCHOR_POS));
        for (int index = 0; index < NegativeEntropyAnchorStructure.PARTS_PER_LAYER; index++) {
            var part = NegativeEntropyAnchorStructure.inLayer(center, facing, index);
            var state = loadedState(level, part);
            if (state != null && state.getBlock() instanceof EntropyAnchorBlock
                    && state.getValue(FORMED)
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
