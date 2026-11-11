package com.atir.molecularmanipulator.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Generated from M1 fixtures in src/test/resources/fixtures/star_sea_calculation/
 * plus the bottom-center port by src/test/python/export_star_sea_runtime.py.
 * The core is the front-center BOTTOM cell, not the tower's center or mid-height.
 * PART is canonical NORTH: y * 25 + z * 5 + (x + 2), x=-2..2, y=0..8, z=0..4.
 * Placement reserves all 225 cells, but collision/click shapes contain only real
 * cuboids. A PART keeps its canonical index for every facing; move its block via
 * offset(part, facing), and use shape(part, facing) inside that rotated block.
 */
public final class StarSeaCalculationGeometry {
    public static final int PART_COUNT = 225;
    // bb / 16 + (.5, 0, 2.5), relative to the core's lower corner; 120 rows.
    private static final double[][] CUBOIDS = {
        {-2.0, 0.1, 0.0, 0.0, 0.46, 5.0},
        {1.0, 0.1, 0.0, 3.0, 0.46, 5.0},
        {0.0, 0.1, 0.25, 1.0, 0.46, 5.0},
        {-1.94, 0.46, 0.06, 0.0, 0.6, 4.94},
        {1.0, 0.46, 0.06, 2.94, 0.6, 4.94},
        {0.0, 0.46, 0.25, 1.0, 0.6, 4.94},
        {-1.9, 0.0, 0.1, -1.38, 0.2, 0.62},
        {-1.83, 0.6, 0.17, -1.45, 0.72, 0.55},
        {-1.705, 0.72, 0.295, -1.575, 0.75, 0.425},
        {-1.9, 0.0, 4.38, -1.38, 0.2, 4.9},
        {-1.83, 0.6, 4.45, -1.45, 0.72, 4.83},
        {-1.705, 0.72, 4.575, -1.575, 0.75, 4.705},
        {2.38, 0.0, 0.1, 2.9, 0.2, 0.62},
        {2.45, 0.6, 0.17, 2.83, 0.72, 0.55},
        {2.575, 0.72, 0.295, 2.705, 0.75, 0.425},
        {2.38, 0.0, 4.38, 2.9, 0.2, 4.9},
        {2.45, 0.6, 4.45, 2.83, 0.72, 4.83},
        {2.575, 0.72, 4.575, 2.705, 0.75, 4.705},
        {-0.9, 0.6, 1.34, 1.9, 2.06, 3.9},
        {-0.9, 0.6, 1.1, 1.9, 0.9, 1.34},
        {-0.9, 1.73, 1.1, 1.9, 2.06, 1.34},
        {0.42, 0.9, 1.1, 0.58, 1.73, 1.34},
        {-0.9, 0.9, 1.1, -0.64, 1.73, 1.34},
        {1.64, 0.9, 1.1, 1.9, 1.73, 1.34},
        {-0.9, 2.06, 1.1, 1.9, 2.2, 1.22},
        {-0.9, 2.06, 3.78, 1.9, 2.2, 3.9},
        {-0.9, 2.06, 1.22, -0.78, 2.2, 3.78},
        {1.78, 2.06, 1.22, 1.9, 2.2, 3.78},
        {-0.78, 2.06, 1.22, 1.78, 2.18, 3.78},
        {-0.5, 1.7, 1.8, 1.5, 8.1, 3.4},
        {-0.5, 1.7, 1.6, 1.5, 4.47, 1.8},
        {-0.5, 5.33, 1.6, 1.5, 8.1, 1.8},
        {-0.5, 4.47, 1.6, 0.13, 5.33, 1.8},
        {0.87, 4.47, 1.6, 1.5, 5.33, 1.8},
        {-0.5, 1.7, 1.52, -0.38, 8.1, 1.64},
        {-0.5, 1.7, 3.36, -0.38, 8.1, 3.48},
        {1.38, 1.7, 1.52, 1.5, 8.1, 1.64},
        {1.38, 1.7, 3.36, 1.5, 8.1, 3.48},
        {-0.65, 2.1, 1.44, 1.65, 2.28, 3.56},
        {-0.57, 2.28, 1.51, 1.57, 2.4, 3.49},
        {0.13, 4.47, 1.74, 0.87, 5.33, 1.8},
        {0.35, 4.7, 1.716, 0.65, 5.1, 1.74},
        {0.22, 4.57, 1.66, 0.78, 5.23, 1.7},
        {0.13, 4.47, 1.54, 0.87, 4.56, 1.7},
        {0.13, 5.24, 1.54, 0.87, 5.33, 1.7},
        {0.13, 4.56, 1.54, 0.22, 5.24, 1.7},
        {0.78, 4.56, 1.54, 0.87, 5.24, 1.7},
        {-0.9, 2.78, 1.255, -0.74, 7.72, 3.745},
        {-1.06, 2.9, 1.375, -0.84, 7.6, 3.625},
        {-1.18, 2.78, 1.255, -1.0, 2.92, 3.745},
        {-1.18, 7.58, 1.255, -1.0, 7.72, 3.745},
        {-1.18, 2.92, 1.255, -1.0, 7.58, 1.395},
        {-1.18, 2.92, 3.605, -1.0, 7.58, 3.745},
        {-0.94, 2.89, 1.6, -0.46, 3.11, 1.94},
        {-0.94, 2.89, 3.06, -0.46, 3.11, 3.4},
        {-0.94, 7.37, 1.6, -0.46, 7.59, 1.94},
        {-0.94, 7.37, 3.06, -0.46, 7.59, 3.4},
        {1.74, 2.78, 1.255, 1.9, 7.72, 3.745},
        {1.84, 2.9, 1.375, 2.06, 7.6, 3.625},
        {2.0, 2.78, 1.255, 2.18, 2.92, 3.745},
        {2.0, 7.58, 1.255, 2.18, 7.72, 3.745},
        {2.0, 2.92, 1.255, 2.18, 7.58, 1.395},
        {2.0, 2.92, 3.605, 2.18, 7.58, 3.745},
        {1.46, 2.89, 1.6, 1.94, 3.11, 1.94},
        {1.46, 2.89, 3.06, 1.94, 3.11, 3.4},
        {1.46, 7.37, 1.6, 1.94, 7.59, 1.94},
        {1.46, 7.37, 3.06, 1.94, 7.59, 3.4},
        {-1.3, 3.99, 1.1, 2.3, 4.21, 1.6},
        {-1.3, 3.99, 1.6, -0.5, 4.21, 3.4},
        {-1.3, 3.99, 3.4, 2.3, 4.21, 3.9},
        {1.5, 3.99, 1.6, 2.3, 4.21, 3.4},
        {-1.3, 5.54, 1.1, 2.3, 5.76, 1.6},
        {-1.3, 5.54, 1.6, -0.5, 5.76, 3.4},
        {-1.3, 5.54, 3.4, 2.3, 5.76, 3.9},
        {1.5, 5.54, 1.6, 2.3, 5.76, 3.4},
        {-1.3, 7.04, 1.1, 2.3, 7.26, 1.6},
        {-1.3, 7.04, 1.6, -0.5, 7.26, 3.4},
        {-1.3, 7.04, 3.4, 2.3, 7.26, 3.9},
        {1.5, 7.04, 1.6, 2.3, 7.26, 3.4},
        {-1.1, 7.96, 1.1, 2.1, 8.1, 3.9},
        {-1.4, 8.1, 0.8, 2.4, 8.34, 4.2},
        {-1.24, 8.34, 0.96, 2.24, 8.4, 4.04},
        {-1.4, 8.34, 0.8, 2.4, 8.52, 0.96},
        {-1.4, 8.34, 0.96, -1.24, 8.52, 4.04},
        {-1.4, 8.34, 4.04, 2.4, 8.52, 4.2},
        {2.24, 8.34, 0.96, 2.4, 8.52, 4.04},
        {-1.17, 8.4, 1.03, -1.07, 8.46, 1.13},
        {-1.17, 8.4, 3.87, -1.07, 8.46, 3.97},
        {2.07, 8.4, 1.03, 2.17, 8.46, 1.13},
        {2.07, 8.4, 3.87, 2.17, 8.46, 3.97},
        {-0.12, 8.4, 2.47, 1.12, 8.46, 2.53},
        {-0.27, 8.4, 2.34, 0.03, 8.54, 2.66},
        {-0.19, 8.54, 2.42, -0.05, 8.88, 2.58},
        {-0.19, 8.88, 2.42, -0.05, 9.0, 2.58},
        {0.97, 8.4, 2.34, 1.27, 8.54, 2.66},
        {1.05, 8.54, 2.42, 1.19, 8.88, 2.58},
        {1.05, 8.88, 2.42, 1.19, 9.0, 2.58},
        {-0.64, 0.9, 1.28, 0.42, 1.73, 1.34},
        {-0.64, 0.9, 1.1, 0.42, 0.99, 1.2},
        {-0.64, 1.64, 1.1, 0.42, 1.73, 1.2},
        {-0.64, 0.99, 1.1, -0.55, 1.64, 1.2},
        {0.33, 0.99, 1.1, 0.42, 1.64, 1.2},
        {-0.54, 1.005, 1.14, 0.32, 1.625, 1.29},
        {0.155, 1.54, 1.116, 0.225, 1.61, 1.14},
        {0.58, 0.9, 1.28, 1.64, 1.73, 1.34},
        {0.58, 0.9, 1.1, 1.64, 0.99, 1.2},
        {0.58, 1.64, 1.1, 1.64, 1.73, 1.2},
        {0.58, 0.99, 1.1, 0.67, 1.64, 1.2},
        {1.55, 0.99, 1.1, 1.64, 1.64, 1.2},
        {0.68, 1.005, 1.14, 1.54, 1.625, 1.29},
        {1.375, 1.54, 1.116, 1.445, 1.61, 1.14},
        {-0.74, 2.08, 3.48, -0.62, 8.16, 3.6},
        {-0.78, 3.24, 3.39, -0.42, 3.36, 3.69},
        {-0.78, 6.24, 3.39, -0.42, 6.36, 3.69},
        {-0.78, 7.79, 3.39, -0.42, 7.91, 3.69},
        {1.62, 2.08, 3.48, 1.74, 8.16, 3.6},
        {1.42, 3.24, 3.39, 1.78, 3.36, 3.69},
        {1.42, 6.24, 3.39, 1.78, 6.36, 3.69},
        {1.42, 7.79, 3.39, 1.78, 7.91, 3.69},
        {0.0, 0.0, 0.0, 1.0, 1.0, 0.25},
    };
    private static final VoxelShape[][] SHAPES = buildShapes();

    private StarSeaCalculationGeometry() {}

    /** Exact cell-local geometry, including an empty shape for unoccupied cells. */
    public static VoxelShape shape(int part, Direction facing) {
        return SHAPES[quarterTurns(facing)][java.util.Objects.checkIndex(part, PART_COUNT)];
    }

    public static int partIndex(int x, int y, int z) {
        if (x < -2 || x > 2 || y < 0 || y > 8 || z < 0 || z > 4) {
            throw new IllegalArgumentException("Outside the canonical 5 x 9 x 5 reservation");
        }
        return y * 25 + z * 5 + x + 2;
    }

    public static int offsetX(int part) { return java.util.Objects.checkIndex(part, PART_COUNT) % 5 - 2; }
    public static int offsetY(int part) { return java.util.Objects.checkIndex(part, PART_COUNT) / 25; }
    public static int offsetZ(int part) { return java.util.Objects.checkIndex(part, PART_COUNT) / 5 % 5; }

    public static BlockPos offset(int part) {
        return new BlockPos(offsetX(part), offsetY(part), offsetZ(part));
    }

    public static BlockPos offset(int part, Direction facing) {
        return rotateOffset(offset(part), facing);
    }

    public static int quarterTurns(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    /** Same -90*q Y rotation as the render helper, around the core's center. */
    public static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        int x = offset.getX(), y = offset.getY(), z = offset.getZ();
        return switch (quarterTurns(facing)) {
            case 0 -> offset;
            case 1 -> new BlockPos(-z, y, x);
            case 2 -> new BlockPos(-x, y, -z);
            default -> new BlockPos(z, y, -x);
        };
    }

    private static VoxelShape[][] buildShapes() {
        var result = new VoxelShape[4][PART_COUNT];
        for (int part = 0; part < PART_COUNT; part++) {
            int x = offsetX(part), y = offsetY(part), z = offsetZ(part);
            var north = Shapes.empty();
            for (double[] box : CUBOIDS) {
                double x0 = Math.max(box[0], x), y0 = Math.max(box[1], y), z0 = Math.max(box[2], z);
                double x1 = Math.min(box[3], x + 1), y1 = Math.min(box[4], y + 1), z1 = Math.min(box[5], z + 1);
                if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
                north = Shapes.joinUnoptimized(north,
                        Shapes.box(x0 - x, y0 - y, z0 - z, x1 - x, y1 - y, z1 - z), BooleanOp.OR);
            }
            result[0][part] = north.optimize();
            // Rotate the already clipped local voxels, NEVER the PART number.
            var boxes = result[0][part].toAabbs();
            for (int turns = 1; turns < 4; turns++) {
                var rotated = Shapes.empty();
                for (var box : boxes) {
                    var piece = switch (turns) {
                        case 1 -> Shapes.box(1 - box.maxZ, box.minY, box.minX,
                                1 - box.minZ, box.maxY, box.maxX);
                        case 2 -> Shapes.box(1 - box.maxX, box.minY, 1 - box.maxZ,
                                1 - box.minX, box.maxY, 1 - box.minZ);
                        default -> Shapes.box(box.minZ, box.minY, 1 - box.maxX,
                                box.maxZ, box.maxY, 1 - box.minX);
                    };
                    rotated = Shapes.joinUnoptimized(rotated, piece, BooleanOp.OR);
                }
                result[turns][part] = rotated.optimize();
            }
        }
        return result;
    }
}
