package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SingularityMotionGeometryTest {
    @Test void towersContainOnlyAuthoredBodiesAndLeaveAccessJointsFixed() {
        for (int version : new int[]{2, 3, 4}) {
            var parts = SingularityStructure.parts(version);
            for (int tower = 0; tower < 8; tower++) {
                int x = SingularityMotionGeometry.towerX(tower), z = SingularityMotionGeometry.towerZ(tower);
                var node = SingularityStructure.collectionNodePos(tower, version);
                var expected = new HashSet<BlockPos>();
                SingularityStructure.towerBodyParts(tower, version).forEach(p -> expected.add(p.pos()));
                var actual = SingularityMotionGeometry.group(tower + 3, version).index().keySet();
                assertEquals(expected, actual, "Every authored tower block must move, without nearby spokes: " + tower);
                for (var part : parts) {
                    if (part.y() == 64 && Math.abs(part.x() - x) <= 2 && Math.abs(part.z() - z) <= 2
                            || part.pos().equals(node))
                        assertEquals(-1, SingularityMotionGeometry.groupOf(part, version), "Access platform and collector must stay fixed");
                }
            }
            // Migration only releases stationary blocks; it never invents or transfers moving material.
            var previous = new HashSet<BlockPos>();
            SingularityMotionGeometry.groups(version, 2).forEach(g -> g.parts().forEach(p -> previous.add(p.pos())));
            for (var group : SingularityMotionGeometry.groups(version)) for (var part : group.parts()) {
                assertTrue(previous.contains(part.pos()));
                assertEquals(group.id(), SingularityMotionGeometry.groupOf(part, version, 2));
            }
            assertTrue(previous.size() > SingularityMotionGeometry.groups(version).stream().mapToInt(g -> g.parts().size()).sum());
        }
    }
    @Test void bodiesAreDisjointExcludeControllerAndHaveSafeTravel() {
        var seen = new HashSet<BlockPos>();
        assertEquals(11, SingularityMotionGeometry.groups().size());
        for (var group : SingularityMotionGeometry.groups()) {
            assertFalse(group.parts().isEmpty());
            for (var part : group.parts()) {
                assertTrue(seen.add(part.pos())); assertFalse(SingularityStructure.isController(part));
                assertNotEquals(SingularityStructure.Type.CORE, part.type());
            }
            for (int t = 0; t < 10000; t += 13) {
                var pose = SingularityMotionGeometry.runningPose(group.id(), t);
                assertTrue(Math.abs(pose.lift()) <= 1.50001); assertTrue(Math.abs(pose.angle()) <= 180);
            }
        }
    }
    @Test void transformsAreInvertibleAndDockingReachesExactHome() {
        var position = new Vec3(28.75, 37.5, -.25);
        for (var facing : Direction.Plane.HORIZONTAL) for (var group : SingularityMotionGeometry.groups()) {
            var origin = SingularityMotionGeometry.origin(new BlockPos(15, 120, -31), facing);
            var pose = SingularityMotionGeometry.runningPose(group.id(), 1780);
            var moved = SingularityMotionGeometry.toWorld(position, origin, facing, pose);
            assertTrue(position.distanceTo(SingularityMotionGeometry.toLocal(moved, origin, facing, pose)) < 1e-9);
            var docked = SingularityMotionGeometry.dockingPose(group.id(), 1780, 1000);
            assertEquals(0, docked.angle(), 1e-9); assertEquals(0, docked.lift(), 1e-9);
        }
    }
    @Test void perTickMotionIsContinuousAcrossWrapAndBobCycle() {
        var origin = Vec3.ZERO; var point = new Vec3(48.5, 65, .5);
        for (int group = 0; group < 11; group++) for (int t = 0; t < 10000; t += 7) {
            var before = SingularityMotionGeometry.toWorld(point, origin, Direction.NORTH, SingularityMotionGeometry.runningPose(group, t));
            var after = SingularityMotionGeometry.toWorld(point, origin, Direction.NORTH, SingularityMotionGeometry.runningPose(group, t + 1));
            assertTrue(before.distanceTo(after) < .12, "Carriage cannot teleport across an angle wrap");
        }
    }
}
