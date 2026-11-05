package com.atir.molecularmanipulator.blockentity;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Geometry of optional, horizontal 3x3 anchor columns attached to an engine. */
public final class StarComputationAnchorStructure {
    public static final int PARTS_PER_LAYER = 9;

    public enum Probe {
        ANCHOR, OTHER, UNLOADED
    }

    /** An unresolved column must retain its previous states until its boundary is loaded. */
    public record Column(int layers, boolean resolved) {
    }

    private StarComputationAnchorStructure() {
    }

    /** Layer zero is directly outside the 3x3x3 shell, two blocks from the core. */
    public static BlockPos position(BlockPos core, Direction facing, int layer, int anchorPos) {
        return inLayer(core.relative(facing, layer + 2), facing, anchorPos);
    }

    /** Rows run top to bottom; columns run left to right when looking inward from the end cap. */
    public static BlockPos inLayer(BlockPos center, Direction facing, int anchorPos) {
        checkCoordinates(facing, anchorPos);
        var right = facing.getCounterClockWise();
        int row = anchorPos / 3;
        int column = anchorPos % 3;
        return center.offset(right.getStepX() * (column - 1), 1 - row,
                right.getStepZ() * (column - 1));
    }

    public static BlockPos centerOf(BlockPos part, Direction facing, int anchorPos) {
        var offset = inLayer(BlockPos.ZERO, facing, anchorPos);
        return part.offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    /** Only contiguous complete layers count. Never step across a gap or an unloaded boundary. */
    public static Column scan(BlockPos core, Direction facing, BiFunction<BlockPos, Integer, Probe> reader) {
        checkCoordinates(facing, 0);
        int layers = 0;
        while (true) {
            boolean unloaded = false;
            for (int index = 0; index < PARTS_PER_LAYER; index++) {
                var probe = reader.apply(position(core, facing, layers, index), index);
                if (probe == Probe.OTHER) {
                    // A known missing member already proves that this is not a complete layer,
                    // even if another member of the same layer is in an unloaded chunk.
                    return new Column(layers, true);
                }
                unloaded |= probe == Probe.UNLOADED;
            }
            if (unloaded) {
                return new Column(layers, false);
            }
            layers++;
        }
    }

    private static void checkCoordinates(Direction facing, int anchorPos) {
        if (facing.getAxis() == Direction.Axis.Y || anchorPos < 0 || anchorPos >= PARTS_PER_LAYER) {
            throw new IllegalArgumentException("Anchor columns require a horizontal facing and position 0..8");
        }
    }
}
