package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.block.WarpPadGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarpPadGeometryTest {
    @BeforeAll static void bootstrap() { HighEnergyLabWorldgenTest.bootstrap(); }

    @Test
    void allFourFacingsKeepCollisionLocalAndLeaveAnOpenStandingPosition() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var full = fullShape(facing);
            var feet = WarpPadGeometry.worldPoint(WarpPadGeometry.STANDING_OFFSET, facing);
            var player = new AABB(feet.x - .3, feet.y + .001, feet.z - .3,
                    feet.x + .3, feet.y + 1.8, feet.z + .3);
            assertFalse(Shapes.joinIsNotEmpty(full, Shapes.create(player), BooleanOp.AND), "Player intersects pad: " + facing);
            assertTrue(Shapes.joinIsNotEmpty(full, Shapes.create(player.move(0, -.03, 0)), BooleanOp.AND));
            assertEquals(-1, full.bounds().minX, 1e-8);
            assertEquals(2, full.bounds().maxX, 1e-8);
            assertEquals(-1, full.bounds().minZ, 1e-8);
            assertEquals(2, full.bounds().maxZ, 1e-8);
        }
    }

    @Test
    void actualVoxelCollisionHitsTheThreeKeysFromStandingEyeHeightInAllFacings() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var full = fullShape(facing);
            var eye = WarpPadGeometry.worldPoint(WarpPadGeometry.STANDING_OFFSET.add(0, 1.62, .10), facing);
            for (int key = 0; key < 3; key++) {
                var center = WarpPadGeometry.worldPoint(WarpPadGeometry.KEYS[key].center(), facing);
                var hit = full.clip(eye, center.add(center.subtract(eye).scale(.2)), BlockPos.ZERO);
                assertNotNull(hit, "No key collision: " + facing + " key=" + key);
                assertEquals(key, WarpPadGeometry.keyAt(WarpPadGeometry.canonicalPoint(hit.getLocation(), facing)),
                        "Console collision covers a key: " + facing + " key=" + key + " hit=" + hit);
            }
        }
    }

    @Test
    void socketAndActivationAreSelectableThroughTheirActualCollision() {
        var full = fullShape(Direction.NORTH);
        var eye = WarpPadGeometry.STANDING_OFFSET.add(0, 1.62, .10);
        for (var surface : new WarpPadGeometry.Surface[] {WarpPadGeometry.PEARL, WarpPadGeometry.ACTIVATION}) {
            var hit = full.clip(eye, surface.center().add(surface.center().subtract(eye).scale(.2)), BlockPos.ZERO);
            assertNotNull(hit);
            if (surface == WarpPadGeometry.PEARL) assertTrue(WarpPadGeometry.pearlAt(hit.getLocation()));
            else assertTrue(WarpPadGeometry.activationAt(hit.getLocation()));
        }
    }

    @Test
    void facingTransformsRoundTripTheOffCenterConsoleAndSocket() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (var surface : new WarpPadGeometry.Surface[] {WarpPadGeometry.KEYS[0], WarpPadGeometry.KEYS[2], WarpPadGeometry.PEARL}) {
                assertTrue(surface.center().distanceToSqr(WarpPadGeometry.canonicalPoint(
                        WarpPadGeometry.worldPoint(surface.center(), facing), facing)) < 1e-20);
            }
        }
    }

    private static VoxelShape fullShape(Direction facing) {
        var full = Shapes.empty();
        for (int part = 0; part < 27; part++) {
            var local = WarpPadGeometry.shape(part, facing);
            for (var box : local.toAabbs()) {
                assertTrue(box.minX >= 0 && box.maxX <= 1 && box.minY >= 0 && box.maxY <= 1
                        && box.minZ >= 0 && box.maxZ <= 1, "Collision outside its occupied block: " + part + " " + box);
            }
            var offset = WarpPadGeometry.offset(part);
            full = Shapes.or(full, local.move(offset.getX(), offset.getY(), offset.getZ()));
        }
        return full;
    }
}
