package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaixuStructureTest {
    @Test void blueprintHasExactDimensionsUniquePositionsAndEveryMaterial() {
        var parts = TaixuStructure.parts();
        assertEquals(parts.size(), parts.stream().map(TaixuStructure.Part::pos).distinct().count());
        assertEquals(-48, parts.stream().mapToInt(TaixuStructure.Part::x).min().orElseThrow());
        assertEquals(48, parts.stream().mapToInt(TaixuStructure.Part::x).max().orElseThrow());
        assertEquals(-48, parts.stream().mapToInt(TaixuStructure.Part::z).min().orElseThrow());
        assertEquals(48, parts.stream().mapToInt(TaixuStructure.Part::z).max().orElseThrow());
        assertEquals(0, parts.stream().mapToInt(TaixuStructure.Part::y).min().orElseThrow());
        assertEquals(128, parts.stream().mapToInt(TaixuStructure.Part::y).max().orElseThrow());
        assertEquals(13, TaixuStructure.counts().size());
        assertEquals(1, TaixuStructure.counts().get(TaixuStructure.Type.CONTROLLER));
        assertEquals(1, TaixuStructure.counts().get(TaixuStructure.Type.CORE));
        assertEquals(parts.size(), TaixuStructure.counts().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(parts.size() < 30000, "Sparse construction must remain bounded");
    }
    @Test void allFourFacingsKeepControllerAnchoredAndPreserveFootprint() {
        var anchor = new BlockPos(117, 105, -239);
        var controller = TaixuStructure.parts().stream().filter(TaixuStructure::isController).findFirst().orElseThrow();
        for (var facing : Direction.Plane.HORIZONTAL) {
            assertEquals(anchor, TaixuStructure.worldPos(anchor, facing, controller));
            var positions = TaixuStructure.parts().stream().map(p -> TaixuStructure.worldPos(anchor, facing, p)).toList();
            assertEquals(positions.size(), new HashSet<>(positions).size());
            assertEquals(97, positions.stream().mapToInt(BlockPos::getX).max().orElseThrow() - positions.stream().mapToInt(BlockPos::getX).min().orElseThrow() + 1);
            assertEquals(anchor.getY() - 65, positions.stream().mapToInt(BlockPos::getY).min().orElseThrow());
            assertEquals(anchor.getY() + 63, positions.stream().mapToInt(BlockPos::getY).max().orElseThrow());
        }
    }
    @Test void lightWellIsClearAndConstructionCompletesLayersInOrder() {
        var positions = new HashSet<>(TaixuStructure.parts().stream().map(TaixuStructure.Part::pos).toList());
        assertTrue(TaixuStructure.requiredAir().stream().noneMatch(positions::contains));
        int previous = -1;
        for (var part : TaixuStructure.parts()) { assertTrue(part.y() >= previous); previous = part.y(); }
        assertEquals(566, TaixuStructure.requiredAir().size());
    }

    @Test void terminalsAreEmbeddedInTheFixedDaisAndMovingGroupsStayIdentical() {
        assertEquals(new BlockPos(0, 65, -7), TaixuStructure.CONTROLLER);
        var terminals = TaixuStructure.parts().stream().filter(p -> p.type() == TaixuStructure.Type.CONTROLLER
                || p.type() == TaixuStructure.Type.RESOURCE_PORT).toList();
        assertEquals(5, terminals.size());
        assertTrue(terminals.stream().allMatch(p -> p.y() == 65 && Math.hypot(p.x(), p.z()) <= 7
                && TaixuMotionGeometry.groupOf(p) < 0));
        assertTrue(terminals.stream().noneMatch(p -> TaixuStructure.parts().stream().anyMatch(above -> above.pos().equals(p.pos().above()))), "Socket faces must not be hidden by the old rim caps");
        assertTrue(TaixuStructure.parts().stream().noneMatch(p -> p.type() == TaixuStructure.Type.STAIRS));
        var newMoving = TaixuStructure.parts(3).stream().filter(p -> TaixuMotionGeometry.groupOf(p, 3) >= 0).toList();
        var oldMoving = TaixuStructure.parts(2).stream().filter(p -> TaixuMotionGeometry.groupOf(p, 2) >= 0).toList();
        assertEquals(oldMoving, newMoving, "Existing motion entities retain exactly the same parts and collision geometry");
        System.out.println("TAIXU_EMBEDDED_COUNTS " + TaixuStructure.counts() + " total=" + TaixuStructure.parts().size());
    }

    @Test void suspendedDesignHasPhysicalGapsAndPreservesClassicBlueprintsAndRings() {
        assertEquals(14665, TaixuStructure.parts(3).size());
        assertEquals(14718, TaixuStructure.parts(2).size());
        for (int i=0;i<3;i++) assertEquals(TaixuMotionGeometry.group(i,3).parts(), TaixuMotionGeometry.group(i,4).parts());
        for (int i=0;i<8;i++) {
            var tower=TaixuMotionGeometry.group(i+3,4);
            assertTrue(tower.parts().stream().noneMatch(p->p.y()==44), "Suspended chambers must have real air gaps");
            assertTrue(tower.parts().stream().anyMatch(p->p.y()==64), "Keep the rideable landing");
            assertNotEquals(TaixuMotionGeometry.group(i+3,3).parts(),tower.parts());
        }
        assertTrue(TaixuStructure.parts().stream().noneMatch(p->p.y()==77 && Math.hypot(p.x(),p.z())<16),
                "The central crystal must be exposed, with no continuous old piers");
        System.out.println("TAIXU_SUSPENDED_COUNTS " + TaixuStructure.counts() + " total=" + TaixuStructure.parts().size());
    }

    @Test void reanchoringPreservesWorldPositionsForEveryFacing() {
        var oldAnchor = new BlockPos(117, 105, -239);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var newAnchor = TaixuStructure.worldPos(oldAnchor, facing, TaixuStructure.CONTROLLER, 2);
            for (var part : TaixuStructure.parts()) {
                assertEquals(TaixuStructure.worldPos(oldAnchor, facing, part, 2),
                        TaixuStructure.worldPos(newAnchor, facing, part), "Moving the terminal must not move the building");
            }
            assertEquals(TaixuMotionGeometry.origin(oldAnchor, facing, 2), TaixuMotionGeometry.origin(newAnchor, facing));
        }
    }
}
