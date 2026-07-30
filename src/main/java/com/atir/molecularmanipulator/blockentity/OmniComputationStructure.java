package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmniComputationStructure {
    public static final int WIDTH = 31;
    public static final int HEIGHT = 39;
    public static final int MIN_X = -15;
    public static final int MAX_X = 15;
    public static final int MIN_Z = -15;
    public static final int MAX_Z = 15;
    public static final int CONTROLLER_X = 0;
    public static final int CONTROLLER_Y = 2;
    public static final int CONTROLLER_Z = MIN_Z;
    public static final int EFFECT_X = 0;
    public static final int EFFECT_Y = 17;
    public static final int EFFECT_Z = 0;
    public static final int RELOCATED_ENTANGLER_X = 0;
    public static final int RELOCATED_ENTANGLER_Y = 9;
    public static final int RELOCATED_ENTANGLER_Z = -12;

    private static final List<Part> PARTS = createParts(StructureLayout.CURRENT);
    private static final List<Part> LEGACY_PARTS = createParts(StructureLayout.LEGACY);
    private static final List<Part> DISMANTLE_PARTS = createDismantleParts();
    private static final Map<LocalPos, Part> PART_LOOKUP = createLookup(PARTS);

    private OmniComputationStructure() {
    }

    public static List<Part> parts() {
        return PARTS;
    }

    public static List<Part> parts(StructureLayout layout) {
        return layout == StructureLayout.LEGACY ? LEGACY_PARTS : PARTS;
    }

    public static List<Part> dismantleParts() {
        return DISMANTLE_PARTS;
    }

    public static Part partAt(int x, int y, int z) {
        return PART_LOOKUP.get(new LocalPos(x, y, z));
    }

    public static BlockPos worldPos(BlockPos controller, Direction facing, Part part) {
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return controller.relative(right, part.x() - CONTROLLER_X)
                .relative(Direction.UP, part.y() - CONTROLLER_Y)
                .relative(back, part.z() - CONTROLLER_Z);
    }

    public static Vec3 worldPoint(BlockPos controller, Direction facing,
            double localX, double localY, double localZ) {
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return Vec3.atCenterOf(controller).add(
                right.getStepX() * (localX - CONTROLLER_X) + back.getStepX() * (localZ - CONTROLLER_Z),
                localY - CONTROLLER_Y,
                right.getStepZ() * (localX - CONTROLLER_X) + back.getStepZ() * (localZ - CONTROLLER_Z));
    }

    public static boolean isWithinBuildHeight(Level level, BlockPos controller) {
        int bottom = controller.getY() - CONTROLLER_Y;
        return bottom >= level.getMinBuildHeight()
                && bottom + HEIGHT - 1 < level.getMaxBuildHeight();
    }

    public static boolean areRequiredChunksLoaded(Level level, BlockPos controller, Direction facing) {
        var corners = List.of(
                worldPos(controller, facing, new Part(MIN_X, 0, MIN_Z, PartType.CASING)),
                worldPos(controller, facing, new Part(MAX_X, 0, MIN_Z, PartType.CASING)),
                worldPos(controller, facing, new Part(MIN_X, 0, MAX_Z, PartType.CASING)),
                worldPos(controller, facing, new Part(MAX_X, 0, MAX_Z, PartType.CASING)));
        int minX = corners.stream().mapToInt(BlockPos::getX).min().orElse(controller.getX());
        int maxX = corners.stream().mapToInt(BlockPos::getX).max().orElse(controller.getX());
        int minZ = corners.stream().mapToInt(BlockPos::getZ).min().orElse(controller.getZ());
        int maxZ = corners.stream().mapToInt(BlockPos::getZ).max().orElse(controller.getZ());
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Inspection inspect(Level level, BlockPos controller, Direction facing) {
        if (!isWithinBuildHeight(level, controller)
                || !areRequiredChunksLoaded(level, controller, facing)) {
            return new Inspection(PARTS.size(), 0, PARTS.size(), 0, false,
                    StructureLayout.INCOMPLETE);
        }
        var effectPart = new Part(EFFECT_X, EFFECT_Y, EFFECT_Z, PartType.AIR);
        var effectState = level.getBlockState(worldPos(controller, facing, effectPart));
        if (effectState.isAir()) {
            return inspectLayout(level, controller, facing, PARTS, StructureLayout.CURRENT);
        }
        if (effectState.is(ModContent.COMPUTATION_DATA_ENTANGLER.get())) {
            return inspectLayout(level, controller, facing, LEGACY_PARTS, StructureLayout.LEGACY);
        }
        return inspectLayout(level, controller, facing, PARTS, StructureLayout.INCOMPLETE);
    }

    private static Inspection inspectLayout(Level level, BlockPos controller, Direction facing,
            List<Part> parts, StructureLayout layout) {
        int correct = 0;
        int missing = 0;
        int conflicts = 0;
        for (var part : parts) {
            var state = level.getBlockState(worldPos(controller, facing, part));
            if (part.type() == PartType.CONTROLLER) {
                if (state.is(ModContent.OMNI_COMPUTATION_CONTROLLER.get())) {
                    correct++;
                } else {
                    conflicts++;
                }
                continue;
            }
            if (part.type() == PartType.AIR) {
                if (state.isAir()) {
                    correct++;
                } else {
                    conflicts++;
                }
                continue;
            }
            if (state.is(block(part.type()))) {
                correct++;
            } else if (state.isAir() || state.canBeReplaced()) {
                missing++;
            } else {
                conflicts++;
            }
        }
        return new Inspection(parts.size(), correct, missing, conflicts,
                correct == parts.size(), layout);
    }

    public static Block block(PartType type) {
        return switch (type) {
            case CONTROLLER -> ModContent.OMNI_COMPUTATION_CONTROLLER.get();
            case CASING -> ModContent.OMNI_COMPUTATION_CASING.get();
            case GLASS -> ModContent.OMNI_COMPUTATION_GLASS.get();
            case PARALLEL_MATRIX -> ModContent.INFINITE_PARALLEL_MATRIX.get();
            case STORAGE_MATRIX -> ModContent.INFINITE_CRAFTING_STORAGE.get();
            case PATTERN_MATRIX -> ModContent.UNIVERSAL_PATTERN_MATRIX.get();
            case DATA_ENTANGLER -> ModContent.COMPUTATION_DATA_ENTANGLER.get();
            case ENERGY_STABILIZER -> ModContent.COMPUTATION_ENERGY_STABILIZER.get();
            case OUTPUT_NODE -> ModContent.COMPUTATION_OUTPUT_NODE.get();
            case CRYSTAL_PYLON -> ModContent.COMPUTATION_CRYSTAL_PYLON.get();
            case AIR -> Blocks.AIR;
        };
    }

    private static List<Part> createParts(StructureLayout layout) {
        var builder = new Builder();
        addBase(builder);
        addCentralChamber(builder);
        addLowerPylons(builder);
        builder.put(EFFECT_X, EFFECT_Y, EFFECT_Z,
                layout == StructureLayout.LEGACY ? PartType.DATA_ENTANGLER : PartType.AIR);
        builder.put(RELOCATED_ENTANGLER_X, RELOCATED_ENTANGLER_Y, RELOCATED_ENTANGLER_Z,
                layout == StructureLayout.LEGACY ? PartType.AIR : PartType.DATA_ENTANGLER);
        addOrbitalAssembly(builder);
        addUpperCrown(builder);
        builder.put(CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CONTROLLER);
        return List.copyOf(builder.build());
    }

    private static List<Part> createDismantleParts() {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var part : PARTS) {
            if (part.type() != PartType.AIR) {
                result.put(new LocalPos(part.x(), part.y(), part.z()), part);
            }
        }
        for (var part : LEGACY_PARTS) {
            if (part.type() != PartType.AIR) {
                result.putIfAbsent(new LocalPos(part.x(), part.y(), part.z()), part);
            }
        }
        return List.copyOf(result.values());
    }

    private static Map<LocalPos, Part> createLookup(List<Part> parts) {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var part : parts) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return Map.copyOf(result);
    }

    private static void addBase(Builder builder) {
        builder.squareRing(0, 15, 2, PartType.CASING);
        builder.circleRing(1, 11.0, 15.6, PartType.CASING);
        builder.circleRing(2, 8.5, 14.8, PartType.CASING);
        builder.circleDisk(3, 11.2, PartType.CASING);
        builder.circleRing(3, 13.2, 15.6, PartType.CASING);
        builder.circleRing(4, 8.7, 14.8, PartType.CASING);
        builder.spokes(4, 3, 14, PartType.DATA_ENTANGLER);

        for (int angle = 0; angle < 16; angle++) {
            double radians = angle * Math.PI / 8.0;
            builder.putPolar(1, 13.0, radians,
                    angle % 2 == 0 ? PartType.STORAGE_MATRIX : PartType.ENERGY_STABILIZER);
        }
        builder.put(0, 2, -14, PartType.OUTPUT_NODE);
        builder.put(0, 2, 14, PartType.OUTPUT_NODE);
        builder.put(-14, 2, 0, PartType.OUTPUT_NODE);
        builder.put(14, 2, 0, PartType.OUTPUT_NODE);
    }

    private static void addCentralChamber(Builder builder) {
        builder.circleRing(5, 3.1, 4.6, PartType.PATTERN_MATRIX);
        for (int y = 6; y <= 18; y++) {
            builder.circleRing(y, 3.4, 4.6, PartType.GLASS);
            if (y % 4 == 2) {
                builder.circleRing(y, 4.0, 5.2, PartType.ENERGY_STABILIZER);
            }
        }
        builder.circleDisk(19, 4.8, PartType.PATTERN_MATRIX);
        for (int y = 7; y <= 17; y += 2) {
            builder.put(0, y, 0, PartType.DATA_ENTANGLER);
        }
        for (int y = 6; y <= 18; y += 4) {
            builder.put(-4, y, -4, PartType.ENERGY_STABILIZER);
            builder.put(4, y, -4, PartType.ENERGY_STABILIZER);
            builder.put(-4, y, 4, PartType.ENERGY_STABILIZER);
            builder.put(4, y, 4, PartType.ENERGY_STABILIZER);
        }
    }

    private static void addLowerPylons(Builder builder) {
        int[][] centers = {
                {-11, -11}, {0, -12}, {11, -11}, {12, 0},
                {11, 11}, {0, 12}, {-11, 11}, {-12, 0}
        };
        for (int index = 0; index < centers.length; index++) {
            int cx = centers[index][0];
            int cz = centers[index][1];
            for (int y = 5; y <= 13; y++) {
                builder.smallTowerRing(cx, cz, y,
                        y == 7 || y == 11 ? PartType.ENERGY_STABILIZER : PartType.CASING);
            }
            builder.smallTowerCap(cx, cz, 14, PartType.PATTERN_MATRIX);
            builder.put(cx, 15, cz, PartType.CRYSTAL_PYLON);
            if (index % 2 == 0) {
                builder.put(cx, 10, cz, PartType.PARALLEL_MATRIX);
            } else {
                builder.put(cx, 10, cz, PartType.STORAGE_MATRIX);
            }
        }
    }

    private static void addOrbitalAssembly(Builder builder) {
        builder.circleRing(16, 11.0, 15.6, PartType.CASING);
        builder.circleRing(17, 12.2, 15.6, PartType.PARALLEL_MATRIX);
        builder.circleRing(18, 10.5, 14.6, PartType.CASING);
        builder.circleRing(20, 8.0, 12.8, PartType.DATA_ENTANGLER);
        builder.circleRing(21, 8.8, 13.8, PartType.CASING);

        for (int angle = 0; angle < 16; angle++) {
            double radians = angle * Math.PI / 8.0;
            builder.putPolar(16, 13.7, radians, PartType.ENERGY_STABILIZER);
            builder.putPolar(18, 12.5, radians,
                    angle % 2 == 0 ? PartType.PARALLEL_MATRIX : PartType.STORAGE_MATRIX);
        }

        for (int angle = 0; angle < 8; angle++) {
            double radians = angle * Math.PI / 4.0;
            int x = (int) Math.round(Math.cos(radians) * 10.0);
            int z = (int) Math.round(Math.sin(radians) * 10.0);
            for (int y = 19; y <= 25; y++) {
                builder.put(x, y, z, y % 3 == 0 ? PartType.DATA_ENTANGLER : PartType.CASING);
            }
        }
    }

    private static void addUpperCrown(Builder builder) {
        builder.circleRing(24, 7.0, 12.8, PartType.CASING);
        builder.circleRing(25, 8.0, 13.8, PartType.PARALLEL_MATRIX);
        builder.circleRing(26, 7.0, 12.8, PartType.CASING);

        for (int angle = 0; angle < 8; angle++) {
            double radians = angle * Math.PI / 4.0;
            int cx = (int) Math.round(Math.cos(radians) * 10.0);
            int cz = (int) Math.round(Math.sin(radians) * 10.0);
            for (int y = 27; y <= 32; y++) {
                builder.smallTowerRing(cx, cz, y,
                        y == 29 ? PartType.STORAGE_MATRIX : PartType.CASING);
            }
            builder.put(cx, 33, cz, PartType.CRYSTAL_PYLON);
        }

        for (int y = 20; y <= 34; y++) {
            double radius = Math.max(2.0, 5.4 - Math.abs(27 - y) * 0.35);
            builder.circleRing(y, Math.max(1.0, radius - 1.1), radius, PartType.PATTERN_MATRIX);
        }
        builder.circleRing(34, 1.2, 3.2, PartType.ENERGY_STABILIZER);
        builder.put(0, 35, 0, PartType.PATTERN_MATRIX);
        builder.put(0, 36, 0, PartType.DATA_ENTANGLER);
        builder.put(0, 37, 0, PartType.CRYSTAL_PYLON);
        builder.put(0, 38, 0, PartType.CRYSTAL_PYLON);
    }

    public enum PartType {
        CONTROLLER,
        CASING,
        GLASS,
        PARALLEL_MATRIX,
        STORAGE_MATRIX,
        PATTERN_MATRIX,
        DATA_ENTANGLER,
        ENERGY_STABILIZER,
        OUTPUT_NODE,
        CRYSTAL_PYLON,
        AIR
    }

    public enum StructureLayout {
        CURRENT,
        LEGACY,
        INCOMPLETE
    }

    public record Part(int x, int y, int z, PartType type) {
    }

    public record Inspection(int total, int correct, int missing, int conflicts, boolean formed,
            StructureLayout layout) {
    }

    private record LocalPos(int x, int y, int z) {
    }

    private static final class Builder {
        private final Map<LocalPos, PartType> parts = new LinkedHashMap<>();

        void put(int x, int y, int z, PartType type) {
            if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z || y < 0 || y >= HEIGHT) {
                throw new IllegalArgumentException("Part outside 31x31x39 bounds: " + x + "," + y + "," + z);
            }
            parts.put(new LocalPos(x, y, z), type);
        }

        void putPolar(int y, double radius, double angle, PartType type) {
            put((int) Math.round(Math.cos(angle) * radius), y,
                    (int) Math.round(Math.sin(angle) * radius), type);
        }

        void circleDisk(int y, double radius, PartType type) {
            double max = radius * radius;
            int bound = (int) Math.ceil(radius);
            for (int x = -bound; x <= bound; x++) {
                for (int z = -bound; z <= bound; z++) {
                    if (x * x + z * z <= max) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void circleRing(int y, double inner, double outer, PartType type) {
            double min = inner * inner;
            double max = outer * outer;
            int bound = Math.min(15, (int) Math.ceil(outer));
            for (int x = -bound; x <= bound; x++) {
                for (int z = -bound; z <= bound; z++) {
                    double distance = x * x + z * z;
                    if (distance >= min && distance <= max) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void squareRing(int y, int radius, int thickness, PartType type) {
            int inner = radius - thickness + 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) >= inner) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void spokes(int y, int inner, int outer, PartType type) {
            for (int distance = inner; distance <= outer; distance++) {
                put(distance, y, 0, type);
                put(-distance, y, 0, type);
                put(0, y, distance, type);
                put(0, y, -distance, type);
                int diagonal = (int) Math.round(distance / Math.sqrt(2.0));
                put(diagonal, y, diagonal, type);
                put(diagonal, y, -diagonal, type);
                put(-diagonal, y, diagonal, type);
                put(-diagonal, y, -diagonal, type);
            }
        }

        void smallTowerRing(int cx, int cz, int y, PartType type) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                        put(cx + dx, y, cz + dz, type);
                    }
                }
            }
        }

        void smallTowerCap(int cx, int cz, int y, PartType type) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(cx + dx, y, cz + dz, type);
                }
            }
        }

        List<Part> build() {
            var result = new ArrayList<Part>(parts.size());
            for (var entry : parts.entrySet()) {
                var pos = entry.getKey();
                result.add(new Part(pos.x(), pos.y(), pos.z(), entry.getValue()));
            }
            return result;
        }
    }
}
