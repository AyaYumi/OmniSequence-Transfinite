package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaixuMotionGeometryTest {
    @Test void bodiesAreDisjointExcludeControllerAndHaveSafeTravel() {
        var seen = new HashSet<BlockPos>();
        assertEquals(11, TaixuMotionGeometry.groups().size());
        for (var group : TaixuMotionGeometry.groups()) {
            assertFalse(group.parts().isEmpty());
            for (var part : group.parts()) {
                assertTrue(seen.add(part.pos())); assertFalse(TaixuStructure.isController(part));
                assertNotEquals(TaixuStructure.Type.CORE, part.type());
            }
            for (int t = 0; t < 10000; t += 13) {
                var pose = TaixuMotionGeometry.runningPose(group.id(), t);
                assertTrue(Math.abs(pose.lift()) <= 1.50001); assertTrue(Math.abs(pose.angle()) <= 180);
            }
        }
    }
    @Test void transformsAreInvertibleAndDockingReachesExactHome() {
        var position = new Vec3(28.75, 37.5, -.25);
        for (var facing : Direction.Plane.HORIZONTAL) for (var group : TaixuMotionGeometry.groups()) {
            var origin = TaixuMotionGeometry.origin(new BlockPos(15, 120, -31), facing);
            var pose = TaixuMotionGeometry.runningPose(group.id(), 1780);
            var moved = TaixuMotionGeometry.toWorld(position, origin, facing, pose);
            assertTrue(position.distanceTo(TaixuMotionGeometry.toLocal(moved, origin, facing, pose)) < 1e-9);
            var docked = TaixuMotionGeometry.dockingPose(group.id(), 1780, 1000);
            assertEquals(0, docked.angle(), 1e-9); assertEquals(0, docked.lift(), 1e-9);
        }
    }
    @Test void perTickMotionIsContinuousAcrossWrapAndBobCycle() {
        var origin = Vec3.ZERO; var point = new Vec3(48.5, 65, .5);
        for (int group = 0; group < 11; group++) for (int t = 0; t < 10000; t += 7) {
            var before = TaixuMotionGeometry.toWorld(point, origin, Direction.NORTH, TaixuMotionGeometry.runningPose(group, t));
            var after = TaixuMotionGeometry.toWorld(point, origin, Direction.NORTH, TaixuMotionGeometry.runningPose(group, t + 1));
            assertTrue(before.distanceTo(after) < .12, "Carriage cannot teleport across an angle wrap");
        }
    }
}
