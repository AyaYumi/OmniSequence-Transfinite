package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.StarComputationAnchorStructure.Column;
import com.atir.molecularmanipulator.blockentity.StarComputationAnchorStructure.Probe;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class StarComputationAnchorStructureTest {
    private static final BlockPos CORE = new BlockPos(23, 70, -11);

    @Test
    void everyFacingUsesRowsFromTheTopAndColumnsFromOutsideLookingIn() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var positions = new HashSet<BlockPos>();
            for (int layer = 0; layer < 4; layer++) {
                int distance = layer + 2;
                for (int index = 0; index < 9; index++) {
                    int row = index / 3;
                    int column = index % 3;
                    var expected = switch (facing) {
                        case NORTH -> CORE.offset(1 - column, 1 - row, -distance);
                        case EAST -> CORE.offset(distance, 1 - row, 1 - column);
                        case SOUTH -> CORE.offset(column - 1, 1 - row, distance);
                        case WEST -> CORE.offset(-distance, 1 - row, column - 1);
                        default -> throw new AssertionError(facing);
                    };
                    var actual = StarComputationAnchorStructure.position(CORE, facing, layer, index);
                    assertEquals(expected, actual, facing + " layer=" + layer + " index=" + index);
                    assertTrue(positions.add(actual));
                    assertEquals(CORE.relative(facing, distance),
                            StarComputationAnchorStructure.centerOf(actual, facing, index));
                }
            }
            assertEquals(36, positions.size());
        }
    }

    @Test
    void columnsAreOptionalAndSupportOneTwoFourOrMoreLayersWithoutAFixedLengthLimit() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int layers : new int[] {0, 1, 2, 4, 129}) {
                var blocks = column(facing, layers);
                assertEquals(new Column(layers, true), scan(facing, blocks));
            }
        }
    }

    @Test
    void missingAnyMemberStopsAtTheLastCompleteLayerAndNeverSkipsAGap() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int missing = 0; missing < 9; missing++) {
                var blocks = column(facing, 4);
                var hole = StarComputationAnchorStructure.position(CORE, facing, 1, missing);
                blocks.remove(hole);
                assertEquals(new Column(1, true), scan(facing, blocks));
                blocks.put(hole, Probe.ANCHOR);
                assertEquals(new Column(4, true), scan(facing, blocks), "Extension is detected without rebuilding the shell");
            }
        }
    }

    @Test
    void unloadedBoundariesDeferTheWholeColumnInsteadOfInventingAnEndCap() {
        var blocks = column(Direction.EAST, 3);
        var unknown = StarComputationAnchorStructure.position(CORE, Direction.EAST, 1, 0);
        blocks.put(unknown, Probe.UNLOADED);
        var queried = new HashSet<BlockPos>();
        var result = StarComputationAnchorStructure.scan(CORE, Direction.EAST, (pos, index) -> {
            queried.add(pos);
            return blocks.getOrDefault(pos, Probe.OTHER);
        });
        assertEquals(new Column(1, false), result);
        assertFalse(queried.contains(StarComputationAnchorStructure.position(CORE, Direction.EAST, 2, 0)),
                "The scanner must never probe beyond the unloaded boundary");
        blocks.put(unknown, Probe.ANCHOR);
        assertEquals(new Column(3, true), scan(Direction.EAST, blocks));
    }

    @Test
    void aKnownMissingMemberProvesTheEndEvenIfAnotherMemberIsUnloaded() {
        var blocks = column(Direction.NORTH, 2);
        blocks.put(StarComputationAnchorStructure.position(CORE, Direction.NORTH, 1, 0), Probe.UNLOADED);
        blocks.remove(StarComputationAnchorStructure.position(CORE, Direction.NORTH, 1, 8));
        assertEquals(new Column(1, true), scan(Direction.NORTH, blocks));
    }

    @Test
    void verticalFacingsAndOutOfRangePositionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> StarComputationAnchorStructure.position(CORE, Direction.UP, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StarComputationAnchorStructure.position(CORE, Direction.NORTH, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> StarComputationAnchorStructure.position(CORE, Direction.NORTH, 0, 9));
    }

    private static Map<BlockPos, Probe> column(Direction facing, int layers) {
        var blocks = new HashMap<BlockPos, Probe>();
        for (int layer = 0; layer < layers; layer++) {
            for (int index = 0; index < 9; index++) {
                blocks.put(StarComputationAnchorStructure.position(CORE, facing, layer, index), Probe.ANCHOR);
            }
        }
        return blocks;
    }

    private static Column scan(Direction facing, Map<BlockPos, Probe> blocks) {
        return StarComputationAnchorStructure.scan(CORE, facing, (pos, index) -> blocks.getOrDefault(pos, Probe.OTHER));
    }
}
