package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SingularityStructureTest {
    @Test void blueprintHasExactDimensionsUniquePositionsAndEveryMaterial() {
        var parts = SingularityStructure.parts();
        assertEquals(parts.size(), parts.stream().map(SingularityStructure.Part::pos).distinct().count());
        assertEquals(-48, parts.stream().mapToInt(SingularityStructure.Part::x).min().orElseThrow());
        assertEquals(48, parts.stream().mapToInt(SingularityStructure.Part::x).max().orElseThrow());
        assertEquals(-48, parts.stream().mapToInt(SingularityStructure.Part::z).min().orElseThrow());
        assertEquals(48, parts.stream().mapToInt(SingularityStructure.Part::z).max().orElseThrow());
        assertEquals(0, parts.stream().mapToInt(SingularityStructure.Part::y).min().orElseThrow());
        assertEquals(128, parts.stream().mapToInt(SingularityStructure.Part::y).max().orElseThrow());
        assertEquals(12, SingularityStructure.counts().size());
        assertEquals(1, SingularityStructure.counts().get(SingularityStructure.Type.CONTROLLER));
        assertEquals(1, SingularityStructure.counts().get(SingularityStructure.Type.CORE));
        assertEquals(parts.size(), SingularityStructure.counts().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(parts.size() < 30000, "Sparse construction must remain bounded");
    }
    @Test void allFourFacingsKeepControllerAnchoredAndPreserveFootprint() {
        var anchor = new BlockPos(117, 105, -239);
        var controller = SingularityStructure.parts().stream().filter(SingularityStructure::isController).findFirst().orElseThrow();
        for (var facing : Direction.Plane.HORIZONTAL) {
            assertEquals(anchor, SingularityStructure.worldPos(anchor, facing, controller));
            var positions = SingularityStructure.parts().stream().map(p -> SingularityStructure.worldPos(anchor, facing, p)).toList();
            assertEquals(positions.size(), new HashSet<>(positions).size());
            assertEquals(97, positions.stream().mapToInt(BlockPos::getX).max().orElseThrow() - positions.stream().mapToInt(BlockPos::getX).min().orElseThrow() + 1);
            assertEquals(anchor.getY() - 65, positions.stream().mapToInt(BlockPos::getY).min().orElseThrow());
            assertEquals(anchor.getY() + 63, positions.stream().mapToInt(BlockPos::getY).max().orElseThrow());
        }
    }
    @Test void lightWellIsClearAndConstructionCompletesLayersInOrder() {
        var positions = new HashSet<>(SingularityStructure.parts().stream().map(SingularityStructure.Part::pos).toList());
        assertTrue(SingularityStructure.requiredAir().stream().noneMatch(positions::contains));
        int previous = -1;
        for (var part : SingularityStructure.parts()) { assertTrue(part.y() >= previous); previous = part.y(); }
        assertEquals(566, SingularityStructure.requiredAir().size());
    }

    @Test void controllerIsEmbeddedInTheFixedDaisAndMovingGroupsStayIdentical() {
        assertEquals(new BlockPos(0, 65, -7), SingularityStructure.CONTROLLER);
        var terminals = SingularityStructure.parts().stream().filter(p -> p.type() == SingularityStructure.Type.CONTROLLER).toList();
        assertEquals(1, terminals.size());
        for (int version : new int[]{2, 3, 4}) {
            int y = version == 2 ? 39 : 65, z = version == 2 ? -34 : -6;
            for (int x : new int[]{-3, -2, 2, 3})
                assertTrue(SingularityStructure.parts(version).stream().noneMatch(p -> p.pos().equals(new BlockPos(x, y, z))),
                        "Retired port sockets must remain empty in every supported layout");
        }
        assertTrue(terminals.stream().allMatch(p -> p.y() == 65 && Math.hypot(p.x(), p.z()) <= 7
                && SingularityMotionGeometry.groupOf(p) < 0));
        assertTrue(terminals.stream().noneMatch(p -> SingularityStructure.parts().stream().anyMatch(above -> above.pos().equals(p.pos().above()))), "Socket faces must not be hidden by the old rim caps");
        assertTrue(SingularityStructure.parts().stream().noneMatch(p -> p.type() == SingularityStructure.Type.STAIRS));
        var newMoving = SingularityStructure.parts(3).stream().filter(p -> SingularityMotionGeometry.groupOf(p, 3) >= 0).toList();
        var oldMoving = SingularityStructure.parts(2).stream().filter(p -> SingularityMotionGeometry.groupOf(p, 2) >= 0).toList();
        assertEquals(oldMoving, newMoving, "Existing motion entities retain exactly the same parts and collision geometry");
        System.out.println("SINGULARITY_EMBEDDED_COUNTS " + SingularityStructure.counts() + " total=" + SingularityStructure.parts().size());
    }

    @Test void suspendedDesignHasPhysicalGapsAndPreservesClassicBlueprintsAndRings() {
        assertEquals(14661, SingularityStructure.parts(3).size());
        assertEquals(14714, SingularityStructure.parts(2).size());
        for (int i=0;i<3;i++) assertEquals(SingularityMotionGeometry.group(i,3).parts(), SingularityMotionGeometry.group(i,4).parts());
        for (int i=0;i<8;i++) {
            var tower=SingularityMotionGeometry.group(i+3,4);
            assertTrue(tower.parts().stream().noneMatch(p->p.y()==44), "Suspended chambers must have real air gaps");
            int x=SingularityMotionGeometry.towerX(i),z=SingularityMotionGeometry.towerZ(i);
            assertTrue(SingularityStructure.parts().stream().anyMatch(p->p.x()==x && p.y()==64 && p.z()==z
                    && p.type()==SingularityStructure.Type.GLASS && SingularityMotionGeometry.groupOf(p)<0), "Keep the landing stationary and rideable");
            assertNotEquals(SingularityMotionGeometry.group(i+3,3).parts(),tower.parts());
        }
        assertTrue(SingularityStructure.parts().stream().noneMatch(p->p.y()==77 && Math.hypot(p.x(),p.z())<16),
                "The central crystal must be exposed, with no continuous old piers");
        System.out.println("SINGULARITY_SUSPENDED_COUNTS " + SingularityStructure.counts() + " total=" + SingularityStructure.parts().size());
    }

    @Test void reanchoringPreservesWorldPositionsForEveryFacing() {
        var oldAnchor = new BlockPos(117, 105, -239);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var newAnchor = SingularityStructure.worldPos(oldAnchor, facing, SingularityStructure.CONTROLLER, 2);
            for (var part : SingularityStructure.parts()) {
                assertEquals(SingularityStructure.worldPos(oldAnchor, facing, part, 2),
                        SingularityStructure.worldPos(newAnchor, facing, part), "Moving the terminal must not move the building");
            }
            assertEquals(SingularityMotionGeometry.origin(oldAnchor, facing, 2), SingularityMotionGeometry.origin(newAnchor, facing));
        }
    }
    @Test void legacyMaterialRecoveryKeepsStairsAndSlabsAndDropsRetiredPorts() {
        var tag = new net.minecraft.nbt.CompoundTag();
        int[] old = new int[14];
        old[1] = 17; old[11] = 64; old[12] = 23; old[13] = 41;
        tag.putIntArray("portable", old);
        var migrated = SingularityStructure.readMaterialCounts(tag, "portable");
        assertEquals(17, migrated[SingularityStructure.Type.CASING.ordinal()]);
        assertEquals(23, migrated[SingularityStructure.Type.STAIRS.ordinal()]);
        assertEquals(41, migrated[SingularityStructure.Type.SLAB.ordinal()]);
        assertEquals(81, Arrays.stream(migrated).sum());
        SingularityStructure.writeMaterialCounts(tag, "portable", migrated);
        assertArrayEquals(migrated, SingularityStructure.readMaterialCounts(tag, "portable"),
                "Reloading migrated counts must not migrate them a second time");
        tag.putIntArray("portable", new int[]{0, 7});
        tag.remove("singularityMaterialVersion");
        assertEquals(7, SingularityStructure.readMaterialCounts(tag, "portable")[1]);
    }
}
