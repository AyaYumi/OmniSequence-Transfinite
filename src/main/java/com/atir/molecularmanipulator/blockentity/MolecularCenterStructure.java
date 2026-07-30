package com.atir.molecularmanipulator.blockentity;

import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class MolecularCenterStructure {
    public static final int WIDTH = 31;
    public static final int HEIGHT = 46;
    public static final int MIN_X = -15;
    public static final int MAX_X = 15;
    public static final int MIN_Z = -15;
    public static final int MAX_Z = 15;
    public static final int CENTER_Z = 0;
    public static final int CORE_Y = 29;
    public static final int CONTROLLER_X = 0;
    public static final int CONTROLLER_Y = 13;
    public static final int CONTROLLER_Z = MIN_Z;
    public static final double VISUAL_CENTER_X = 0.0;
    public static final double VISUAL_CENTER_Z = 0.0;

    private static final int LEGACY_MIN_X = -6;
    private static final int LEGACY_MAX_X = 7;
    private static final int LEGACY_WIDTH = 14;
    private static final int LEGACY_HEIGHT = 13;
    private static final List<Part> PARTS = createParts();
    private static final List<Part> LEGACY_PARTS = createLegacyParts();
    private static final Part VISUAL_CENTER_PART = new Part(0, CORE_Y, 0, PartType.AIR);
    private static final Part UPPER_CORE_PART = new Part(0, CORE_Y + 7, 0, PartType.CORE);
    private static final Map<LocalPos, Part> PART_LOOKUP = createLookup();
    private static final List<Part> WORK_PARTS = createWorkParts();

    private MolecularCenterStructure() {
    }

    public static List<Part> parts() {
        return PARTS;
    }

    public static List<Part> workParts() {
        return WORK_PARTS;
    }

    public static BlockPos visualCenterPos(BlockPos controller, Direction facing) {
        return worldPos(controller, facing, VISUAL_CENTER_PART);
    }

    public static BlockPos upperCorePos(BlockPos controller, Direction facing) {
        return worldPos(controller, facing, UPPER_CORE_PART);
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
        return controller.getY() - CONTROLLER_Y >= level.getMinBuildHeight()
                && controller.getY() - CONTROLLER_Y + HEIGHT <= level.getMaxBuildHeight();
    }

    public static boolean areRequiredChunksLoaded(Level level, BlockPos controller, Direction facing) {
        var first = worldPos(controller, facing, new Part(MIN_X, 0, MIN_Z, PartType.CASING));
        var second = worldPos(controller, facing, new Part(MAX_X, 0, MIN_Z, PartType.CASING));
        var third = worldPos(controller, facing, new Part(MIN_X, 0, MAX_Z, PartType.CASING));
        var fourth = worldPos(controller, facing, new Part(MAX_X, 0, MAX_Z, PartType.CASING));
        int minX = Math.min(Math.min(first.getX(), second.getX()), Math.min(third.getX(), fourth.getX()));
        int maxX = Math.max(Math.max(first.getX(), second.getX()), Math.max(third.getX(), fourth.getX()));
        int minZ = Math.min(Math.min(first.getZ(), second.getZ()), Math.min(third.getZ(), fourth.getZ()));
        int maxZ = Math.max(Math.max(first.getZ(), second.getZ()), Math.max(third.getZ(), fourth.getZ()));
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean matches(Level level, BlockPos controller, Direction facing) {
        return detectLayout(level, controller, facing).isFormed();
    }

    public static StructureLayout detectLayout(Level level, BlockPos controller, Direction facing) {
        if (!isWithinBuildHeight(level, controller)
                || !areRequiredChunksLoaded(level, controller, facing)) {
            return StructureLayout.INCOMPLETE;
        }

        var center = level.getBlockState(visualCenterPos(controller, facing));
        var upper = level.getBlockState(upperCorePos(controller, facing));
        if (center.isAir() && upper.is(ModContent.MOLECULAR_CENTER_CORE.get())) {
            return matchesParts(level, controller, facing, PARTS)
                    ? StructureLayout.CURRENT
                    : StructureLayout.INCOMPLETE;
        }
        if (center.is(ModContent.MOLECULAR_CENTER_CORE.get())
                && upper.is(ModContent.MOLECULAR_CENTER_STABILIZER.get())) {
            return matchesParts(level, controller, facing, LEGACY_PARTS)
                    ? StructureLayout.LEGACY
                    : StructureLayout.INCOMPLETE;
        }
        return StructureLayout.INCOMPLETE;
    }

    private static boolean matchesParts(Level level, BlockPos controller, Direction facing, List<Part> parts) {
        for (var part : parts) {
            if (isController(part)) {
                continue;
            }
            var state = level.getBlockState(worldPos(controller, facing, part));
            if (part.partType() == PartType.AIR) {
                if (!state.isAir()) {
                    return false;
                }
            } else if (!state.is(partBlock(part.partType()))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isController(Part part) {
        return part.x() == CONTROLLER_X
                && part.y() == CONTROLLER_Y
                && part.z() == CONTROLLER_Z;
    }

    public static boolean isUploadCore(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "extendedae_plus", "assembler_matrix_upload_core"))
                .map(state::is)
                .orElse(false);
    }

    public static boolean isStructurePart(BlockState state) {
        return state.is(ModContent.MOLECULAR_CENTER_CONTROLLER.get())
                || state.is(ModContent.MOLECULAR_CENTER_CASING.get())
                || state.is(ModContent.MOLECULAR_CENTER_GLASS.get())
                || state.is(ModContent.MOLECULAR_CENTER_COIL.get())
                || state.is(ModContent.MOLECULAR_CENTER_STABILIZER.get())
                || state.is(ModContent.MOLECULAR_CENTER_CORE.get())
                || state.is(AEBlocks.QUARTZ_BLOCK.block())
                || state.is(AEBlocks.QUARTZ_VIBRANT_GLASS.block())
                || state.is(AEBlocks.FLUIX_BLOCK.block())
                || isUploadCore(state);
    }

    public static BlockState partState(PartType type) {
        return partBlock(type).defaultBlockState();
    }

    public static Block partBlock(PartType type) {
        return switch (type) {
            case CASING -> ModContent.MOLECULAR_CENTER_CASING.get();
            case GLASS -> ModContent.MOLECULAR_CENTER_GLASS.get();
            case COIL -> ModContent.MOLECULAR_CENTER_COIL.get();
            case STABILIZER -> ModContent.MOLECULAR_CENTER_STABILIZER.get();
            case CORE -> ModContent.MOLECULAR_CENTER_CORE.get();
            case AE_QUARTZ -> AEBlocks.QUARTZ_BLOCK.block();
            case AE_VIBRANT_GLASS -> AEBlocks.QUARTZ_VIBRANT_GLASS.block();
            case AE_FLUIX -> AEBlocks.FLUIX_BLOCK.block();
            case AIR -> Blocks.AIR;
        };
    }

    private static List<Part> createParts() {
        var builder = new StructureBuilder();
        addGroundAnchor(builder);
        addInvertedCrystal(builder);
        addPalacePlatform(builder);
        addColonnade(builder);
        addCentralPedestal(builder);
        addOrbitalTowers(builder);
        addPhysicalArches(builder);
        addCoreSphere(builder);
        addCrown(builder);
        builder.put(CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CASING);
        return builder.build();
    }

    private static void addGroundAnchor(StructureBuilder builder) {
        builder.ring(0, 2.4, 3.6, PartType.AE_FLUIX);
        builder.put(0, 0, 0, PartType.STABILIZER);
        builder.put(-4, 0, 0, PartType.AE_QUARTZ);
        builder.put(4, 0, 0, PartType.AE_QUARTZ);
        builder.put(0, 0, -4, PartType.AE_QUARTZ);
        builder.put(0, 0, 4, PartType.AE_QUARTZ);
    }

    private static void addInvertedCrystal(StructureBuilder builder) {
        for (int y = 1; y <= 11; y++) {
            double radius = 1.15 + y * 1.18;
            builder.ring(y, Math.max(0.0, radius - 0.72), radius + 0.55,
                    PartType.AE_VIBRANT_GLASS);
            for (int ray = 0; ray < 8; ray++) {
                builder.putPolar(y, radius, ray * Math.PI / 4.0, PartType.AE_FLUIX);
            }
            if (y == 4 || y == 8 || y == 11) {
                builder.ring(y, Math.max(0.0, radius - 0.95), radius + 0.35,
                        PartType.AE_QUARTZ);
                for (int ray = 0; ray < 8; ray++) {
                    builder.putPolar(y, radius, ray * Math.PI / 4.0, PartType.STABILIZER);
                }
            }
        }
    }

    private static void addPalacePlatform(StructureBuilder builder) {
        builder.ring(12, 9.3, 15.55, PartType.AE_QUARTZ);
        builder.disk(12, 5.7, PartType.AE_QUARTZ);
        builder.spokes(12, 5.0, 14.8, PartType.AE_FLUIX);
        builder.ring(12, 14.75, 15.55, PartType.STABILIZER);

        builder.ring(13, 11.8, 15.55, PartType.AE_QUARTZ);
        builder.ring(13, 7.2, 11.1, PartType.AE_QUARTZ);
        builder.disk(13, 5.5, PartType.AE_QUARTZ);
        builder.spokes(13, 5.2, 14.7, PartType.AE_FLUIX);
        builder.ring(13, 14.7, 15.55, PartType.CASING);

        builder.ring(14, 13.25, 15.55, PartType.AE_QUARTZ);
        builder.ring(14, 9.0, 12.25, PartType.AE_QUARTZ);
        builder.ring(14, 5.0, 7.0, PartType.STABILIZER);
        builder.spokes(14, 6.4, 13.9, PartType.COIL);

        builder.ring(15, 13.6, 15.55, PartType.AE_FLUIX);
        builder.ring(15, 9.5, 12.3, PartType.AE_QUARTZ);
        builder.disk(15, 5.4, PartType.AE_QUARTZ);
    }

    private static void addColonnade(StructureBuilder builder) {
        for (var anchor : builder.polarAnchors(11.0, 32)) {
            builder.put(anchor.x(), 15, anchor.z(), PartType.STABILIZER);
            builder.put(anchor.x(), 16, anchor.z(), PartType.AE_QUARTZ);
            builder.put(anchor.x(), 17, anchor.z(), PartType.CASING);
            builder.put(anchor.x(), 18, anchor.z(), PartType.AE_QUARTZ);
            builder.put(anchor.x(), 19, anchor.z(), PartType.AE_FLUIX);
        }
        builder.ring(19, 9.4, 12.45, PartType.AE_QUARTZ);
        builder.ring(20, 9.9, 12.0, PartType.STABILIZER);
        builder.ring(20, 13.6, 15.45, PartType.AE_QUARTZ);
    }

    private static void addCentralPedestal(StructureBuilder builder) {
        builder.ring(16, 3.5, 5.2, PartType.AE_QUARTZ);
        builder.ring(17, 3.5, 4.5, PartType.STABILIZER);
        builder.ring(18, 3.0, 4.2, PartType.AE_QUARTZ);
        builder.ring(19, 2.6, 3.8, PartType.AE_FLUIX);
        builder.ring(20, 2.2, 3.5, PartType.AE_QUARTZ);
        builder.ring(21, 2.0, 3.1, PartType.STABILIZER);
        for (var anchor : builder.polarAnchors(3.0, 8)) {
            for (int y = 16; y <= 22; y++) {
                builder.put(anchor.x(), y, anchor.z(),
                        y == 18 || y == 21 ? PartType.COIL : PartType.CASING);
            }
        }
        builder.put(0, 20, 0, PartType.STABILIZER);
    }

    private static void addOrbitalTowers(StructureBuilder builder) {
        int[][] towers = {
                { 0, -13, 44 }, { 0, 13, 44 }, { -13, 0, 44 }, { 13, 0, 44 },
                { -9, -9, 35 }, { 9, -9, 35 }, { -9, 9, 35 }, { 9, 9, 35 }
        };
        for (int[] tower : towers) {
            addTower(builder, tower[0], tower[1], 14, tower[2]);
        }
    }

    private static void addTower(StructureBuilder builder, int x, int z, int baseY, int topY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 1) {
                    builder.put(x + dx, baseY, z + dz, PartType.AE_QUARTZ);
                    builder.put(x + dx, baseY + 1, z + dz,
                            dx == 0 && dz == 0 ? PartType.AE_FLUIX : PartType.STABILIZER);
                }
            }
        }
        for (int y = baseY + 2; y <= topY - 4; y++) {
            PartType type = y % 5 == 0 ? PartType.AE_FLUIX
                    : y % 3 == 0 ? PartType.CASING : PartType.AE_QUARTZ;
            builder.put(x, y, z, type);
            if (y <= baseY + 5 && y % 2 == 0) {
                builder.put(x + 1, y, z, PartType.AE_QUARTZ);
                builder.put(x - 1, y, z, PartType.AE_QUARTZ);
                builder.put(x, y, z + 1, PartType.AE_QUARTZ);
                builder.put(x, y, z - 1, PartType.AE_QUARTZ);
            }
        }
        builder.put(x, topY - 3, z, PartType.AE_QUARTZ);
        builder.put(x, topY - 2, z, PartType.AE_VIBRANT_GLASS);
        builder.put(x, topY - 1, z, PartType.AE_FLUIX);
        builder.put(x, topY, z, PartType.STABILIZER);
    }

    private static void addPhysicalArches(StructureBuilder builder) {
        builder.verticalCircleX(CORE_Y, 12.0, -1, PartType.AE_QUARTZ);
        builder.verticalCircleX(CORE_Y, 12.0, 1, PartType.AE_QUARTZ);
        builder.verticalCircleZ(CORE_Y, 12.0, -1, PartType.AE_QUARTZ);
        builder.verticalCircleZ(CORE_Y, 12.0, 1, PartType.AE_QUARTZ);
        for (int eighth = 0; eighth < 8; eighth++) {
            double angle = eighth * Math.PI / 4.0;
            builder.putVerticalX(CORE_Y, 12.0, -1, angle, PartType.AE_FLUIX);
            builder.putVerticalX(CORE_Y, 12.0, 1, angle, PartType.AE_FLUIX);
            builder.putVerticalZ(CORE_Y, 12.0, -1, angle, PartType.AE_FLUIX);
            builder.putVerticalZ(CORE_Y, 12.0, 1, angle, PartType.AE_FLUIX);
        }
        builder.ring(CORE_Y, 13.25, 14.25, PartType.AE_VIBRANT_GLASS);
        for (int ray = 0; ray < 8; ray++) {
            builder.putPolar(CORE_Y, 13.8, ray * Math.PI / 4.0, PartType.STABILIZER);
        }
    }

    private static void addCoreSphere(StructureBuilder builder) {
        double radius = 7.25;
        builder.horizontalCircle(CORE_Y, radius, PartType.GLASS);
        builder.verticalCircleX(CORE_Y, radius, 0, PartType.GLASS);
        builder.verticalCircleZ(CORE_Y, radius, 0, PartType.GLASS);

        // The renderer is anchored here, so this position must stay empty.
        builder.put(0, CORE_Y, 0, PartType.AIR);
        builder.put(0, CORE_Y - 7, 0, PartType.STABILIZER);
        // Keep the physical core as the upper sphere anchor, outside the visual field.
        builder.put(0, CORE_Y + 7, 0, PartType.CORE);
        builder.put(-7, CORE_Y, 0, PartType.STABILIZER);
        builder.put(7, CORE_Y, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y, -7, PartType.STABILIZER);
        builder.put(0, CORE_Y, 7, PartType.STABILIZER);
    }

    private static void addCrown(StructureBuilder builder) {
        builder.ring(38, 1.5, 3.0, PartType.AE_QUARTZ);
        builder.ring(39, 1.2, 2.6, PartType.AE_FLUIX);
        builder.ring(40, 0.5, 2.2, PartType.AE_VIBRANT_GLASS);
        builder.ring(41, 0.0, 1.8, PartType.AE_QUARTZ);
        builder.ring(42, 0.0, 1.45, PartType.AE_FLUIX);
        builder.ring(43, 0.0, 1.1, PartType.AE_VIBRANT_GLASS);
        builder.put(0, 44, 0, PartType.AE_FLUIX);
        builder.put(0, 45, 0, PartType.STABILIZER);
    }

    private static List<Part> createPrevious31Parts() {
        var builder = new StructureBuilder();
        for (int offset = MIN_X; offset <= MAX_X; offset++) {
            PartType axisType = Math.floorMod(offset, 4) == 0 ? PartType.AE_FLUIX : PartType.AE_QUARTZ;
            builder.put(offset, 0, CENTER_Z, axisType);
            builder.put(0, 0, offset, axisType);
        }
        builder.put(MIN_X, 0, 0, PartType.STABILIZER);
        builder.put(MAX_X, 0, 0, PartType.STABILIZER);
        builder.put(0, 0, MIN_Z, PartType.STABILIZER);
        builder.put(0, 0, MAX_Z, PartType.STABILIZER);
        builder.ring(0, 2.4, 3.6, PartType.AE_FLUIX);

        addInvertedCrystal(builder);
        addPalacePlatform(builder);
        addColonnade(builder);
        addCentralPedestal(builder);
        addOrbitalTowers(builder);
        addPhysicalArches(builder);
        addCoreSphere(builder);
        addCrown(builder);
        builder.put(0, 0, 0, PartType.CASING);
        return builder.build();
    }

    private static List<Part> createPrevious32Parts() {
        var builder = new StructureBuilder(-15, 16, 0, 31, 0.5, 15.5);
        for (int z = 0; z <= 15; z++) {
            builder.put(0, 0, z, z % 4 == 0 ? PartType.AE_FLUIX : PartType.AE_QUARTZ);
            if (z >= 10) {
                builder.put(1, 0, z, PartType.AE_QUARTZ);
            }
        }
        builder.put(0, 0, 15, PartType.STABILIZER);
        builder.put(1, 0, 15, PartType.AE_FLUIX);
        builder.put(0, 0, 16, PartType.AE_FLUIX);
        builder.put(1, 0, 16, PartType.STABILIZER);

        addInvertedCrystal(builder);
        addPalacePlatform(builder);
        addColonnade(builder);
        addCentralPedestal(builder);
        builder.put(0, 20, 15, PartType.STABILIZER);

        int[][] towers = {
                { 0, 2, 44 }, { 1, 29, 44 }, { -13, 16, 44 }, { 14, 15, 44 },
                { -9, 6, 35 }, { 10, 6, 35 }, { -9, 25, 35 }, { 10, 25, 35 }
        };
        for (int[] tower : towers) {
            addTower(builder, tower[0], tower[1], 14, tower[2]);
        }

        builder.verticalCircleX(CORE_Y, 12.0, 15, PartType.AE_QUARTZ);
        builder.verticalCircleX(CORE_Y, 12.0, 16, PartType.AE_QUARTZ);
        builder.verticalCircleZ(CORE_Y, 12.0, 0, PartType.AE_QUARTZ);
        builder.verticalCircleZ(CORE_Y, 12.0, 1, PartType.AE_QUARTZ);
        for (int eighth = 0; eighth < 8; eighth++) {
            double angle = eighth * Math.PI / 4.0;
            builder.putVerticalX(CORE_Y, 12.0, 15, angle, PartType.AE_FLUIX);
            builder.putVerticalX(CORE_Y, 12.0, 16, angle, PartType.AE_FLUIX);
            builder.putVerticalZ(CORE_Y, 12.0, 0, angle, PartType.AE_FLUIX);
            builder.putVerticalZ(CORE_Y, 12.0, 1, angle, PartType.AE_FLUIX);
        }
        builder.ring(CORE_Y, 13.25, 14.25, PartType.AE_VIBRANT_GLASS);
        for (int ray = 0; ray < 8; ray++) {
            builder.putPolar(CORE_Y, 13.8, ray * Math.PI / 4.0, PartType.STABILIZER);
        }

        double radius = 7.25;
        builder.horizontalCircle(CORE_Y, radius, PartType.GLASS);
        builder.verticalCircleX(CORE_Y, radius, 15, PartType.GLASS);
        builder.verticalCircleX(CORE_Y, radius, 16, PartType.GLASS);
        builder.verticalCircleZ(CORE_Y, radius, 0, PartType.GLASS);
        builder.verticalCircleZ(CORE_Y, radius, 1, PartType.GLASS);
        builder.put(0, CORE_Y, 15, PartType.CORE);
        builder.put(1, CORE_Y, 15, PartType.STABILIZER);
        builder.put(0, CORE_Y, 16, PartType.STABILIZER);
        builder.put(1, CORE_Y, 16, PartType.STABILIZER);
        builder.put(0, CORE_Y - 7, 15, PartType.STABILIZER);
        builder.put(1, CORE_Y + 7, 16, PartType.STABILIZER);
        builder.put(-7, CORE_Y, 15, PartType.STABILIZER);
        builder.put(8, CORE_Y, 16, PartType.STABILIZER);
        builder.put(0, CORE_Y, 8, PartType.STABILIZER);
        builder.put(1, CORE_Y, 23, PartType.STABILIZER);

        builder.ring(38, 1.5, 3.0, PartType.AE_QUARTZ);
        builder.ring(39, 1.2, 2.6, PartType.AE_FLUIX);
        builder.ring(40, 0.5, 2.2, PartType.AE_VIBRANT_GLASS);
        builder.ring(41, 0.0, 1.8, PartType.AE_QUARTZ);
        builder.ring(42, 0.0, 1.45, PartType.AE_FLUIX);
        builder.ring(43, 0.0, 1.1, PartType.AE_VIBRANT_GLASS);
        builder.put(0, 44, 15, PartType.AE_FLUIX);
        builder.put(1, 44, 16, PartType.AE_FLUIX);
        builder.put(0, 45, 15, PartType.STABILIZER);
        builder.put(1, 45, 16, PartType.STABILIZER);
        builder.put(0, 0, 0, PartType.CASING);
        return builder.build();
    }

    private static List<Part> createLegacyParts() {
        var result = new java.util.ArrayList<Part>(PARTS.size());
        for (var part : PARTS) {
            if (part.x() == 0 && part.y() == CORE_Y && part.z() == 0) {
                result.add(new Part(part.x(), part.y(), part.z(), PartType.CORE));
            } else if (part.x() == 0 && part.y() == CORE_Y + 7 && part.z() == 0) {
                result.add(new Part(part.x(), part.y(), part.z(), PartType.STABILIZER));
            } else {
                result.add(part);
            }
        }
        return List.copyOf(result);
    }

    private static Map<LocalPos, Part> createLookup() {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var part : PARTS) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return Map.copyOf(result);
    }

    private static List<Part> createWorkParts() {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (int y = 0; y < LEGACY_HEIGHT; y++) {
            for (int x = LEGACY_MIN_X; x <= LEGACY_MAX_X; x++) {
                for (int z = 0; z < LEGACY_WIDTH; z++) {
                    var part = new Part(x, y, z, PartType.AIR);
                    result.put(new LocalPos(x, y, z), part);
                }
            }
        }
        for (var previous : createPrevious31Parts()) {
            var cleanup = new Part(previous.x(), previous.y(), previous.z(), PartType.AIR);
            result.put(new LocalPos(cleanup.x(), cleanup.y(), cleanup.z()), cleanup);
        }
        for (var previous : createPrevious32Parts()) {
            var cleanup = new Part(previous.x(), previous.y(), previous.z() - 15, PartType.AIR);
            result.put(new LocalPos(cleanup.x(), cleanup.y(), cleanup.z()), cleanup);
        }
        for (var part : PARTS) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return List.copyOf(result.values());
    }

    public enum StructureLayout {
        CURRENT,
        LEGACY,
        INCOMPLETE;

        public boolean isFormed() {
            return this != INCOMPLETE;
        }
    }

    public enum PartType {
        CASING,
        GLASS,
        COIL,
        STABILIZER,
        CORE,
        AE_QUARTZ,
        AE_VIBRANT_GLASS,
        AE_FLUIX,
        AIR
    }

    public record Part(int x, int y, int z, PartType partType) {
    }

    private record LocalPos(int x, int y, int z) {
    }

    private static final class StructureBuilder {
        private final Map<LocalPos, PartType> parts = new LinkedHashMap<>();
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final double centerX;
        private final double centerZ;

        private StructureBuilder() {
            this(MIN_X, MAX_X, MIN_Z, MAX_Z, VISUAL_CENTER_X, VISUAL_CENTER_Z);
        }

        private StructureBuilder(int minX, int maxX, int minZ, int maxZ,
                double centerX, double centerZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
        }

        void put(int x, int y, int z, PartType type) {
            if (x < minX || x > maxX || y < 0 || y >= HEIGHT || z < minZ || z > maxZ) {
                return;
            }
            parts.put(new LocalPos(x, y, z), type);
        }

        void disk(int y, double radius, PartType type) {
            ring(y, 0.0, radius, type);
        }

        void ring(int y, double innerRadius, double outerRadius, PartType type) {
            double innerSquared = innerRadius * innerRadius;
            double outerSquared = outerRadius * outerRadius;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double distanceSquared = radialSquared(x, z);
                    if (distanceSquared >= innerSquared && distanceSquared <= outerSquared) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void spokes(int y, double innerRadius, double outerRadius, PartType type) {
            double innerSquared = innerRadius * innerRadius;
            double outerSquared = outerRadius * outerRadius;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = x - centerX;
                    double dz = z - centerZ;
                    double distanceSquared = dx * dx + dz * dz;
                    boolean onSpoke = Math.abs(dx) <= 0.55 || Math.abs(dz) <= 0.55
                            || Math.abs(Math.abs(dx) - Math.abs(dz)) <= 0.55;
                    if (onSpoke && distanceSquared >= innerSquared && distanceSquared <= outerSquared) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void putPolar(int y, double radius, double angle, PartType type) {
            put((int) Math.round(centerX + Math.cos(angle) * radius), y,
                    (int) Math.round(centerZ + Math.sin(angle) * radius), type);
        }

        void horizontalCircle(int y, double radius, PartType type) {
            for (int step = 0; step < 360; step++) {
                putPolar(y, radius, step * Math.PI / 180.0, type);
            }
        }

        void verticalCircleX(int centerY, double radius, int z, PartType type) {
            for (int step = 0; step < 360; step++) {
                putVerticalX(centerY, radius, z, step * Math.PI / 180.0, type);
            }
        }

        void verticalCircleZ(int centerY, double radius, int x, PartType type) {
            for (int step = 0; step < 360; step++) {
                putVerticalZ(centerY, radius, x, step * Math.PI / 180.0, type);
            }
        }

        void putVerticalX(int centerY, double radius, int z, double angle, PartType type) {
            put((int) Math.round(centerX + Math.cos(angle) * radius),
                    (int) Math.round(centerY + Math.sin(angle) * radius), z, type);
        }

        void putVerticalZ(int centerY, double radius, int x, double angle, PartType type) {
            put(x, (int) Math.round(centerY + Math.sin(angle) * radius),
                    (int) Math.round(centerZ + Math.cos(angle) * radius), type);
        }

        List<LocalPos> polarAnchors(double radius, int count) {
            var result = new LinkedHashSet<LocalPos>();
            for (int index = 0; index < count; index++) {
                double angle = Math.PI * 2.0 * index / count;
                result.add(new LocalPos(
                        (int) Math.round(centerX + Math.cos(angle) * radius),
                        0,
                        (int) Math.round(centerZ + Math.sin(angle) * radius)));
            }
            return List.copyOf(result);
        }

        List<Part> build() {
            return parts.entrySet().stream()
                    .map(entry -> new Part(entry.getKey().x(), entry.getKey().y(), entry.getKey().z(),
                            entry.getValue()))
                    .sorted(Comparator.comparingInt(Part::y)
                            .thenComparingInt(Part::z)
                            .thenComparingInt(Part::x))
                    .toList();
        }

        private double radialSquared(int x, int z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz;
        }
    }
}
