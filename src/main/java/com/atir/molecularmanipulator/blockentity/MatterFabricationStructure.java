package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The original 31 x 31 fabrication well: stepped octagonal rings, four
 * cardinal stabilizer stations and a suspended central processing core.
 */
public final class MatterFabricationStructure {
    public static final int RADIUS = 15;
    public static final int CONTROLLER_Y = 2;
    public static final int CONTROLLER_Z = -15;
    private static final List<Part> PARTS = createParts();

    private MatterFabricationStructure() {
    }

    public static List<Part> parts() {
        return PARTS;
    }

    public static boolean isController(Part part) {
        return part.type() == PartType.CONTROLLER;
    }

    public static BlockPos worldPos(BlockPos controller, Direction facing, Part part) {
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        return controller.relative(right, part.x())
                .relative(Direction.UP, part.y() - CONTROLLER_Y)
                .relative(back, part.z() - CONTROLLER_Z);
    }

    public static BlockState partState(PartType type) {
        return switch (type) {
            case CONTROLLER -> ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState();
            case CASING -> ModContent.MATTER_FABRICATION_CASING.get().defaultBlockState();
            case GLASS -> ModContent.MATTER_FABRICATION_GLASS.get().defaultBlockState();
            case COIL -> ModContent.MATTER_FABRICATION_COIL.get().defaultBlockState();
            case STABILIZER -> ModContent.MATTER_FABRICATION_STABILIZER.get().defaultBlockState();
            case CORE -> ModContent.MATTER_FABRICATION_CORE.get().defaultBlockState();
        };
    }

    public static Inspection inspect(Level level, BlockPos controller, Direction facing) {
        int correct = 0;
        int missing = 0;
        int conflicts = 0;
        for (var part : PARTS) {
            if (isController(part)) {
                correct++;
                continue;
            }
            var pos = worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)) {
                missing++;
                continue;
            }
            var current = level.getBlockState(pos);
            if (current.is(partState(part.type()).getBlock())) {
                correct++;
            } else if (current.canBeReplaced()) {
                missing++;
            } else {
                conflicts++;
            }
        }
        return new Inspection(PARTS.size(), correct, missing, conflicts);
    }

    private static List<Part> createParts() {
        Map<LocalPos, PartType> layout = new LinkedHashMap<>();

        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                if (insideOctagon(x, z, 15)) {
                    int edge = Math.max(Math.abs(x), Math.abs(z));
                    put(layout, x, 0, z, edge >= 14 ? PartType.CASING : PartType.GLASS);
                }
            }
        }

        ring(layout, 14, 1, PartType.COIL);
        ring(layout, 11, 1, PartType.CASING);
        ring(layout, 8, 1, PartType.GLASS);
        spokes(layout, 2, 13, 1, PartType.CASING);
        ring(layout, 15, 2, PartType.CASING);
        ring(layout, 13, 2, PartType.GLASS);
        ring(layout, 10, 2, PartType.COIL);
        ring(layout, 7, 2, PartType.CASING);
        ring(layout, 12, 3, PartType.CASING);
        ring(layout, 9, 3, PartType.GLASS);
        ring(layout, 6, 3, PartType.COIL);
        ring(layout, 9, 4, PartType.CASING);
        ring(layout, 6, 4, PartType.GLASS);
        ring(layout, 4, 4, PartType.COIL);
        ring(layout, 6, 5, PartType.CASING);
        ring(layout, 4, 5, PartType.GLASS);
        ring(layout, 3, 6, PartType.COIL);

        station(layout, 0, -11);
        station(layout, 0, 11);
        station(layout, -11, 0);
        station(layout, 11, 0);
        heatSink(layout, -8, -8);
        heatSink(layout, 8, -8);
        heatSink(layout, -8, 8);
        heatSink(layout, 8, 8);

        for (int y = 1; y <= 4; y++) {
            int radius = Math.max(1, 4 - y);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    put(layout, x, y, z, y % 2 == 0 ? PartType.COIL : PartType.CASING);
                }
            }
        }

        put(layout, 0, 5, 0, PartType.CORE);
        put(layout, 0, CONTROLLER_Y, CONTROLLER_Z, PartType.CONTROLLER);

        var result = new ArrayList<Part>(layout.size());
        layout.forEach((pos, type) -> result.add(new Part(pos.x(), pos.y(), pos.z(), type)));
        return List.copyOf(result);
    }

    private static void station(Map<LocalPos, PartType> layout, int centerX, int centerZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                put(layout, x, 2, z, PartType.STABILIZER);
            }
        }
        put(layout, centerX, 3, centerZ, PartType.CORE);
    }

    private static void heatSink(Map<LocalPos, PartType> layout, int centerX, int centerZ) {
        for (int y = 2; y <= 4; y++) {
            put(layout, centerX, y, centerZ, y == 3 ? PartType.GLASS : PartType.COIL);
            put(layout, centerX + Integer.signum(centerX), y, centerZ, PartType.CASING);
            put(layout, centerX, y, centerZ + Integer.signum(centerZ), PartType.CASING);
        }
    }

    private static void spokes(Map<LocalPos, PartType> layout, int from, int to, int y,
            PartType type) {
        for (int distance = from; distance <= to; distance++) {
            put(layout, distance, y, 0, type);
            put(layout, -distance, y, 0, type);
            put(layout, 0, y, distance, type);
            put(layout, 0, y, -distance, type);
        }
    }

    private static void ring(Map<LocalPos, PartType> layout, int radius, int y, PartType type) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (insideOctagon(x, z, radius) && !insideOctagon(x, z, radius - 1)) {
                    put(layout, x, y, z, type);
                }
            }
        }
    }

    private static boolean insideOctagon(int x, int z, int radius) {
        if (radius < 0) {
            return false;
        }
        int ax = Math.abs(x);
        int az = Math.abs(z);
        return Math.max(ax, az) <= radius && ax + az <= radius + (radius + 1) / 2;
    }

    private static void put(Map<LocalPos, PartType> layout, int x, int y, int z, PartType type) {
        layout.put(new LocalPos(x, y, z), type);
    }

    public enum PartType {
        CONTROLLER,
        CASING,
        GLASS,
        COIL,
        STABILIZER,
        CORE
    }

    public record Part(int x, int y, int z, PartType type) {
    }

    private record LocalPos(int x, int y, int z) {
    }

    public record Inspection(int total, int correct, int missing, int conflicts) {
        public boolean formed() {
            return correct == total;
        }
    }
}
