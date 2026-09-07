package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class DismantlePlanOrderTest {
    @Test
    void completesEachLayerAndAlternatesActualSparseRows() {
        var a = new BlockPos(-3, 12, -8);
        var b = new BlockPos(4, 12, -8);
        var c = new BlockPos(-2, 12, -2);
        var d = new BlockPos(5, 12, -2);
        var e = new BlockPos(0, 12, 7);
        var f = new BlockPos(8, 12, 7);
        var g = new BlockPos(-1, 4, -8);
        var h = new BlockPos(3, 4, -8);
        assertEquals(List.of(a, b, d, c, e, f, g, h),
                DismantlePlan.orderedPositions(List.of(g, c, f, h, b, d, a, e, b)));
    }

    @Test
    void freezesMutablePositionsAndHandlesEmptyPlans() {
        var mutable = new BlockPos.MutableBlockPos(1, 9, 2);
        var result = DismantlePlan.orderedPositions(List.of(mutable));
        mutable.set(99, -2, 100);
        assertEquals(List.of(new BlockPos(1, 9, 2)), result);
        assertEquals(List.of(), DismantlePlan.orderedPositions(List.of()));
    }
}
