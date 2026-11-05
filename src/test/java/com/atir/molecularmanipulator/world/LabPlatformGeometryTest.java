package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.block.LabPlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class LabPlatformGeometryTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() { HighEnergyLabWorldgenTest.bootstrap(); }

    @Test
    void floorSectionsStayLocalInAllFourFacingsAndRetainTheirRaisedGuards() {
        assertEquals(45, LabPlatformGeometry.PARTS.size());
        for (var facing : Direction.Plane.HORIZONTAL) {
            boolean guard = false;
            for (var part : LabPlatformGeometry.PARTS) {
                var shape = LabPlatformGeometry.floor(part.index(), facing);
                assertFalse(shape.isEmpty());
                var bounds = shape.bounds();
                assertTrue(bounds.minX >= 0 && bounds.minZ >= 0 && bounds.minY >= 0);
                assertTrue(bounds.maxX <= 1 && bounds.maxZ <= 1 && bounds.maxY <= 1.35 + 1e-7);
                guard |= bounds.maxY > 1;
            }
            assertTrue(guard);
        }
    }

    @Test
    void trayCentersRemainOpenAboveTheSupportSurfaces() {
        assertFalse(Shapes.joinIsNotEmpty(LabPlatformGeometry.equipment("input", Direction.SOUTH),
                Shapes.create(new AABB(.22,.68,.72,.28,.8,.78)), BooleanOp.AND));
        assertFalse(Shapes.joinIsNotEmpty(LabPlatformGeometry.equipment("output", Direction.SOUTH),
                Shapes.create(new AABB(.72,.68,.72,.78,.8,.78)), BooleanOp.AND));
        for (String kind : new String[] {"input", "output", "power"}) {
            for (var facing : Direction.Plane.HORIZONTAL) assertFalse(LabPlatformGeometry.equipment(kind, facing).isEmpty());
        }
    }

    @Test
    void thePaidPowerBlockKeepsAFullBodyAndTheBeamStartsAtTheNewRearContact() {
        var body = Shapes.create(new AABB(.01,.01,.01,.99,.99,.99));
        assertFalse(Shapes.joinIsNotEmpty(body, LabPlatformGeometry.equipment("power", Direction.SOUTH), BooleanOp.ONLY_FIRST));
        assertEquals(new BlockPos(0,3,47), LabPlatformGeometry.INLET);
        assertEquals(new BlockPos(0,4,50), LabPlatformGeometry.ANCHOR);
        var start = LabPlatformGeometry.SUPPLY_PATH[0];
        assertEquals(0, start.x, 1e-6);
        assertEquals(2.405, start.y, 1e-6);
        assertEquals(45.3, start.z, 1e-6);
    }

    @Test
    void equipmentOffsetsRotateTogetherWithTheirControlFaces() {
        assertEquals(new BlockPos(-2,0,1), LabPlatformGeometry.rotateOffset(LabPlatformGeometry.INPUT, Direction.SOUTH));
        assertEquals(new BlockPos(-1,0,-2), LabPlatformGeometry.rotateOffset(LabPlatformGeometry.INPUT, Direction.WEST));
        assertEquals(new BlockPos(2,0,-1), LabPlatformGeometry.rotateOffset(LabPlatformGeometry.INPUT, Direction.NORTH));
        assertEquals(new BlockPos(1,0,2), LabPlatformGeometry.rotateOffset(LabPlatformGeometry.INPUT, Direction.EAST));
        assertEquals(new BlockPos(3,-1,0), LabPlatformGeometry.rotateOffset(new BlockPos(0,-1,-3), Direction.WEST));
    }
}
