package com.atir.molecularmanipulator.block;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class StarSeaCalculationGeometryTest {
    private static final Path SOURCE = Path.of("src/test/resources/fixtures/star_sea_calculation/"
            + "m1-white-base-port/star-listener-m1.bbmodel");

    @Test
    void all225CanonicalPartsRoundTripAndRotateAroundTheBottomFrontCore() {
        assertEquals(225, StarSeaCalculationGeometry.PART_COUNT);
        assertEquals(2, StarSeaCalculationGeometry.partIndex(0, 0, 0));
        assertEquals(BlockPos.ZERO, StarSeaCalculationGeometry.offset(2));
        for (var facing : Direction.Plane.HORIZONTAL) {
            var offsets = new HashSet<BlockPos>();
            for (int part = 0; part < 225; part++) {
                var north = StarSeaCalculationGeometry.offset(part);
                assertEquals(part, north.getY() * 25 + north.getZ() * 5 + north.getX() + 2);
                assertEquals(part, StarSeaCalculationGeometry.partIndex(north.getX(), north.getY(), north.getZ()));
                var expectedCenter = rotate(new Vec3(north.getX() + .5, north.getY(), north.getZ() + .5), facing);
                var actual = StarSeaCalculationGeometry.offset(part, facing);
                assertEquals(expectedCenter.x - .5, actual.getX(), 1E-10);
                assertEquals(expectedCenter.y, actual.getY(), 1E-10);
                assertEquals(expectedCenter.z - .5, actual.getZ(), 1E-10);
                assertTrue(offsets.add(actual), "Duplicate reserved cell: " + facing + " part=" + part);
            }
            assertEquals(225, offsets.size());
            assertTrue(offsets.contains(BlockPos.ZERO), "Every facing retains the bottom-front core");
        }
        assertEquals(0, StarSeaCalculationGeometry.quarterTurns(Direction.NORTH));
        assertEquals(1, StarSeaCalculationGeometry.quarterTurns(Direction.EAST));
        assertEquals(2, StarSeaCalculationGeometry.quarterTurns(Direction.SOUTH));
        assertEquals(3, StarSeaCalculationGeometry.quarterTurns(Direction.WEST));
    }

    @Test
    void everyCachedShapeIsLocalAnd76ReservedCellsHaveNoPhantomCollision() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            int empty = 0;
            for (int part = 0; part < 225; part++) {
                var shape = StarSeaCalculationGeometry.shape(part, facing);
                assertSame(shape, StarSeaCalculationGeometry.shape(part, facing), "No per-call shape rebuilding");
                if (shape.isEmpty()) { empty++; continue; }
                for (var box : shape.toAabbs()) {
                    assertTrue(box.minX >= -1E-10 && box.minY >= -1E-10 && box.minZ >= -1E-10,
                            "Below cell bounds: " + facing + " part=" + part + " " + box);
                    assertTrue(box.maxX <= 1 + 1E-10 && box.maxY <= 1 + 1E-10 && box.maxZ <= 1 + 1E-10,
                            "Above cell bounds: " + facing + " part=" + part + " " + box);
                }
            }
            assertEquals(76, empty, "Reserve space without turning empty tower corners into full cubes");
            assertTrue(StarSeaCalculationGeometry.shape(StarSeaCalculationGeometry.partIndex(-2, 4, 0), facing).isEmpty());
            assertTrue(StarSeaCalculationGeometry.shape(StarSeaCalculationGeometry.partIndex(2, 4, 4), facing).isEmpty());
        }
    }

    @Test
    void all900CellShapesExactlyMatchIndependentlyClippedAndWorldRotatedSourceCuboids() throws Exception {
        var source = sourceCuboids();
        assertEquals(120, source.size());
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int part = 0; part < 225; part++) {
                // Decode independently of the generated helper's offset functions.
                int x = part % 5 - 2, y = part / 25, z = part / 5 % 5;
                var offset = rotate(new Vec3(x + .5, y, z + .5), facing).add(-.5, 0, -.5);
                var expected = Shapes.empty();
                for (var box : source) {
                    double x0 = Math.max(x, box.minX), y0 = Math.max(y, box.minY), z0 = Math.max(z, box.minZ);
                    double x1 = Math.min(x + 1, box.maxX), y1 = Math.min(y + 1, box.maxY), z1 = Math.min(z + 1, box.maxZ);
                    if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
                    var a = rotate(new Vec3(x0, y0, z0), facing).subtract(offset);
                    var b = rotate(new Vec3(x1, y1, z1), facing).subtract(offset);
                    expected = Shapes.joinUnoptimized(expected, Shapes.create(new AABB(a, b)), BooleanOp.OR);
                }
                assertFalse(Shapes.joinIsNotEmpty(expected, StarSeaCalculationGeometry.shape(part, facing), BooleanOp.NOT_SAME),
                        "Source/collision mismatch: " + facing + " part=" + part);
            }
        }
    }

    @Test
    void thinInspectionGlassAndOpenFrontPortPocketsAreNotReplacedByBoundingCubes() {
        // Source inspection glass: x=.22..78, y=4.57..5.23, z=1.66..1.70.
        assertTrue(intersects(0, 4, 1, new AABB(.3, .6, .665, .7, .7, .695)));
        // In front of the pane, inside its white frame, there is real air.
        assertFalse(intersects(0, 4, 1, new AABB(.3, .6, .60, .7, .7, .65)));
        // Recessed ME interface on the left, before the panel at z=1.14.
        assertFalse(intersects(-1, 1, 1, new AABB(.5, .2, .105, .6, .3, .13)));
        assertTrue(intersects(-1, 1, 1, new AABB(.5, .2, .15, .6, .3, .18)));
    }

    @Test
    void squareConnectionPortOccupiesTheBottomFrontCenterCellInAllFacings() {
        var original = new AABB(0, 0, 0, 1, 1, .25);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var a = rotate(new Vec3(original.minX, original.minY, original.minZ), facing);
            var b = rotate(new Vec3(original.maxX, original.maxY, original.maxZ), facing);
            var socket = Shapes.create(new AABB(a, b));
            var shape = StarSeaCalculationGeometry.shape(2, facing);
            assertFalse(Shapes.joinIsNotEmpty(socket, shape, BooleanOp.ONLY_FIRST), "Every port voxel is clickable: " + facing);
            assertEquals(0, socket.bounds().minY);
            assertEquals(1, socket.bounds().maxY);
        }
    }

    @Test
    void invalidPartsAndVerticalFacingsAreRejectedRatherThanAliased() {
        assertThrows(IndexOutOfBoundsException.class, () -> StarSeaCalculationGeometry.shape(-1, Direction.NORTH));
        assertThrows(IndexOutOfBoundsException.class, () -> StarSeaCalculationGeometry.shape(225, Direction.NORTH));
        assertThrows(IllegalArgumentException.class, () -> StarSeaCalculationGeometry.shape(2, Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> StarSeaCalculationGeometry.partIndex(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> StarSeaCalculationGeometry.partIndex(0, 9, 0));
        assertThrows(IllegalArgumentException.class, () -> StarSeaCalculationGeometry.partIndex(-3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> StarSeaCalculationGeometry.partIndex(0, 0, 5));
    }

    private static boolean intersects(int x, int y, int z, AABB local) {
        return Shapes.joinIsNotEmpty(StarSeaCalculationGeometry.shape(
                StarSeaCalculationGeometry.partIndex(x, y, z), Direction.NORTH), Shapes.create(local), BooleanOp.AND);
    }

    private static List<AABB> sourceCuboids() throws Exception {
        var result = new ArrayList<AABB>();
        var source = JsonParser.parseString(Files.readString(SOURCE)).getAsJsonObject();
        for (var value : source.getAsJsonArray("elements")) {
            var cube = value.getAsJsonObject();
            var lo = cube.getAsJsonArray("from");
            var hi = cube.getAsJsonArray("to");
            result.add(new AABB(lo.get(0).getAsDouble() / 16 + .5, lo.get(1).getAsDouble() / 16,
                    lo.get(2).getAsDouble() / 16 + 2.5, hi.get(0).getAsDouble() / 16 + .5,
                    hi.get(1).getAsDouble() / 16, hi.get(2).getAsDouble() / 16 + 2.5));
        }
        return result;
    }

    private static Vec3 rotate(Vec3 point, Direction facing) {
        return switch (facing) {
            case NORTH -> point;
            case EAST -> new Vec3(1 - point.z, point.y, point.x);
            case SOUTH -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case WEST -> new Vec3(point.z, point.y, 1 - point.x);
            default -> throw new IllegalArgumentException();
        };
    }
}
