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

    private static final int REACTOR_TOWER_OFFSET = 12;
    private static final int REACTOR_EMITTER_OFFSET = 7;

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

    public static boolean isVisualCenter(Part part) {
        return part.x() == VISUAL_CENTER_PART.x()
                && part.y() == VISUAL_CENTER_PART.y()
                && part.z() == VISUAL_CENTER_PART.z();
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

        var upper = level.getBlockState(upperCorePos(controller, facing));
        if (upper.is(ModContent.MOLECULAR_CENTER_CORE.get())) {
            return matchesParts(level, controller, facing, PARTS)
                    ? StructureLayout.CURRENT
                    : StructureLayout.INCOMPLETE;
        }
        var center = level.getBlockState(visualCenterPos(controller, facing));
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
            // AIR entries are construction and migration hints, not physical
            // multiblock parts. Occupying the visual center must not unform an
            // otherwise complete structure.
            if (part.partType() == PartType.AIR) {
                continue;
            }
            var state = level.getBlockState(worldPos(controller, facing, part));
            if (!state.is(partBlock(part.partType()))) {
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
        addTwistedFoundation(builder);
        addTwistedEnergyWell(builder);
        addTwistedPylons(builder);
        addTwistedContainmentFrame(builder);
        addReactorCore(builder);
        addTwistedRoof(builder);
        builder.put(CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CASING);
        return builder.build();
    }

    /**
     * Low, wide quantum forge layout. Two horizontal rings carry a suspended
     * central cage; the diagonal supports and side pods provide the visual
     * rhythm without recreating the previous tower silhouette.
     */
    private static void addQuantumForgeFoundation(StructureBuilder builder) {
        builder.filledOctagon(8, 15, 5, PartType.AE_QUARTZ);
        builder.octagonRing(8, 15, 5, 2, PartType.CASING);
        builder.filledOctagon(9, 14, 4, PartType.AE_QUARTZ);
        builder.octagonRing(9, 14, 4, 1, PartType.AE_FLUIX);
        builder.filledOctagon(10, 13, 4, PartType.AE_VIBRANT_GLASS);
        builder.octagonRing(10, 13, 4, 1, PartType.STABILIZER);

        builder.ring(11, 10.0, 14.0, PartType.CASING);
        builder.spokes(11, 3.0, 11.5, PartType.AE_FLUIX);
        builder.ring(12, 12.0, 15.0, PartType.AE_QUARTZ);
        builder.spokes(12, 4.0, 12.0, PartType.COIL);
        builder.ring(13, 6.0, 9.0, PartType.AE_VIBRANT_GLASS);
        builder.filledDiamondAt(0, 14, 0, 3, PartType.AE_FLUIX);

        // Four diagonal power rails lead from the foundation to the side pods.
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                for (int distance = 4; distance <= 12; distance++) {
                    builder.put(xSign * distance, 13, zSign * distance,
                            distance % 3 == 0 ? PartType.COIL : PartType.AE_FLUIX);
                    if (distance >= 8) {
                        builder.put(xSign * distance, 14, zSign * distance,
                                distance % 2 == 0 ? PartType.AE_QUARTZ : PartType.STABILIZER);
                    }
                }
            }
        }
    }

    private static void addQuantumForgePods(StructureBuilder builder) {
        int[][] pods = { { -12, -12 }, { -12, 12 }, { 12, -12 }, { 12, 12 } };
        for (int[] pod : pods) {
            addQuantumForgePod(builder, pod[0], pod[1]);
        }
    }

    private static void addQuantumForgePod(StructureBuilder builder, int x, int z) {
        for (int y = 12; y <= 15; y++) {
            int radius = y == 12 || y == 15 ? 2 : 1;
            builder.filledDiamondAt(x, y, z, radius, PartType.AE_QUARTZ);
            builder.put(x, y, z,
                    y == 13 ? PartType.COIL : y == 15 ? PartType.STABILIZER : PartType.CASING);
        }
        for (int y = 16; y <= 21; y++) {
            builder.put(x, y, z, y % 3 == 0 ? PartType.AE_FLUIX : PartType.AE_VIBRANT_GLASS);
            builder.put(x - Integer.signum(x), y, z, PartType.CASING);
            builder.put(x, y, z - Integer.signum(z), PartType.CASING);
            if (y == 18 || y == 21) {
                builder.put(x + Integer.signum(x), y, z, PartType.AE_QUARTZ);
                builder.put(x, y, z + Integer.signum(z), PartType.AE_QUARTZ);
            }
        }
        builder.put(x, 22, z, PartType.STABILIZER);

        // Short connectors make each pod visibly belong to the lower ring.
        int signX = Integer.signum(x);
        int signZ = Integer.signum(z);
        for (int distance = 8; distance <= 12; distance++) {
            builder.put(signX * distance, 20, signZ * distance,
                    distance % 3 == 0 ? PartType.COIL : PartType.AE_QUARTZ);
            builder.put(signX * distance, 21, signZ * distance,
                    distance % 2 == 0 ? PartType.AE_FLUIX : PartType.STABILIZER);
        }
    }

    private static void addQuantumForgeLowerRing(StructureBuilder builder) {
        builder.ring(20, 9.0, 13.0, PartType.CASING);
        builder.spokes(20, 3.0, 10.0, PartType.COIL);
        builder.ring(21, 11.0, 14.0, PartType.AE_QUARTZ);
        builder.ring(22, 8.0, 11.0, PartType.AE_FLUIX);
        builder.ring(23, 5.0, 8.0, PartType.AE_VIBRANT_GLASS);
        builder.spokes(23, 3.0, 7.0, PartType.STABILIZER);

        for (int[] axis : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            for (int distance = 4; distance <= 11; distance++) {
                builder.put(axis[0] * distance, 22, axis[1] * distance,
                        distance % 3 == 0 ? PartType.COIL : PartType.AE_QUARTZ);
            }
        }
    }

    private static void addQuantumForgeCoreCage(StructureBuilder builder) {
        // A compact glass-and-quartz cage surrounds, but never occupies, the
        // renderer's air center.
        builder.squareRingAt(0, 24, 0, 4, 1, PartType.AE_QUARTZ);
        builder.squareRingAt(0, 34, 0, 4, 1, PartType.AE_QUARTZ);
        for (int y = 25; y <= 33; y++) {
            builder.put(-4, y, -4, PartType.CASING);
            builder.put(-4, y, 4, PartType.CASING);
            builder.put(4, y, -4, PartType.CASING);
            builder.put(4, y, 4, PartType.CASING);
            builder.put(0, y, -4, PartType.GLASS);
            builder.put(0, y, 4, PartType.GLASS);
            builder.put(-4, y, 0, PartType.GLASS);
            builder.put(4, y, 0, PartType.GLASS);
            if (y == 27 || y == 31) {
                builder.squareRingAt(0, y, 0, 4, 1, PartType.AE_FLUIX);
            }
        }

        // Lower and upper crystal stems suspend the actual rendered field.
        builder.put(0, CORE_Y - 7, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y - 6, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y - 5, 0, PartType.COIL);
        builder.put(0, CORE_Y - 4, 0, PartType.AE_VIBRANT_GLASS);
        builder.put(0, CORE_Y - 3, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y - 2, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y, 0, PartType.AIR);
        builder.put(0, CORE_Y + 2, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y + 3, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y + 4, 0, PartType.AE_VIBRANT_GLASS);
        builder.put(0, CORE_Y + 5, 0, PartType.COIL);
        builder.put(0, CORE_Y + 6, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y + 7, 0, PartType.CORE);
    }

    private static void addQuantumForgeSupports(StructureBuilder builder) {
        // Four articulated diagonal arms connect the lower ring to the upper
        // ring, giving the machine its suspended, low-gravity appearance.
        int[][] diagonals = { { -1, -1 }, { -1, 1 }, { 1, -1 }, { 1, 1 } };
        for (int[] diagonal : diagonals) {
            for (int step = 0; step <= 11; step++) {
                int radius = (int) Math.round(11.0 - step * 2.5 / 11.0);
                int y = 22 + step;
                int x = diagonal[0] * radius;
                int z = diagonal[1] * radius;
                PartType main = step % 4 == 0 ? PartType.COIL : PartType.CASING;
                builder.put(x, y, z, main);
                builder.put(x - diagonal[0], y, z, PartType.AE_QUARTZ);
                builder.put(x, y, z - diagonal[1], PartType.AE_FLUIX);
                if (step == 0 || step == 6 || step == 11) {
                    builder.put(x + diagonal[0], y, z + diagonal[1], PartType.STABILIZER);
                }
            }
        }
    }

    private static void addQuantumForgeUpperRing(StructureBuilder builder) {
        builder.ring(34, 8.0, 11.0, PartType.CASING);
        builder.spokes(34, 3.0, 9.0, PartType.COIL);
        builder.ring(35, 11.0, 14.0, PartType.AE_QUARTZ);
        builder.ring(36, 9.0, 13.0, PartType.AE_FLUIX);
        builder.ring(37, 7.0, 10.0, PartType.AE_VIBRANT_GLASS);
        builder.spokes(37, 3.0, 8.0, PartType.STABILIZER);

        for (int[] axis : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            for (int distance = 8; distance <= 14; distance++) {
                builder.put(axis[0] * distance, 35, axis[1] * distance,
                        distance % 3 == 0 ? PartType.COIL : PartType.AE_QUARTZ);
            }
        }
    }

    private static void addQuantumForgeTopSpine(StructureBuilder builder) {
        // Keep the top deliberately short: this is a suspended processing
        // machine, not another vertical tower.
        builder.ring(38, 3.0, 6.0, PartType.CASING);
        builder.ring(39, 2.0, 4.0, PartType.AE_QUARTZ);
        builder.ring(40, 1.0, 3.0, PartType.AE_FLUIX);
        builder.put(0, 41, 0, PartType.AE_VIBRANT_GLASS);
        builder.put(0, 42, 0, PartType.STABILIZER);
    }

    /**
     * The current molecular center is deliberately built as a radial star-gate
     * rather than a stack of square decks.  The four cardinal pylons leave the
     * core visually open, while the octagonal plinth, intersecting arches and
     * tapered crown provide a clear silhouette from every direction.
     */
    private static void addAstralFoundation(StructureBuilder builder) {
        // Three offset octagonal plates create a broad, non-rectangular foot.
        builder.filledOctagon(8, 15, 5, PartType.AE_QUARTZ);
        builder.octagonRing(8, 15, 5, 2, PartType.CASING);
        builder.filledOctagon(9, 14, 4, PartType.AE_QUARTZ);
        builder.octagonRing(9, 14, 4, 1, PartType.AE_FLUIX);
        builder.filledOctagon(10, 13, 4, PartType.AE_VIBRANT_GLASS);
        builder.octagonRing(10, 13, 4, 1, PartType.STABILIZER);

        // The lower rings and spokes read as a technical energy distribution
        // plane instead of a solid square platform.
        builder.ring(11, 10.0, 13.0, PartType.CASING);
        builder.spokes(11, 3.0, 11.0, PartType.AE_FLUIX);
        builder.ring(12, 12.0, 14.0, PartType.AE_QUARTZ);
        builder.spokes(12, 4.0, 12.0, PartType.COIL);
        builder.ring(13, 12.0, 15.0, PartType.CASING);
        builder.spokes(13, 5.0, 12.0, PartType.AE_FLUIX);

        // Four broad radial rails visually pin the machine to the plinth.
        int[][] axes = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] axis : axes) {
            for (int distance = 5; distance <= 14; distance++) {
                PartType type = distance % 4 == 0 ? PartType.COIL : PartType.AE_FLUIX;
                builder.put(axis[0] * distance, 14, axis[1] * distance, type);
                if (distance >= 8) {
                    builder.put(axis[0] * distance, 15, axis[1] * distance,
                            distance % 3 == 0 ? PartType.STABILIZER : PartType.AE_QUARTZ);
                }
            }
        }
    }

    private static void addAstralLowerChamber(StructureBuilder builder) {
        // A nested circular well leaves an intentional air volume at its heart.
        builder.ring(15, 6.5, 9.0, PartType.AE_VIBRANT_GLASS);
        builder.ring(16, 5.0, 7.0, PartType.CASING);
        builder.ring(17, 3.5, 5.5, PartType.AE_FLUIX);
        builder.ring(18, 2.0, 4.0, PartType.COIL);
        builder.filledDiamondAt(0, 17, 0, 2, PartType.AE_VIBRANT_GLASS);

        // Four diagonal service nodes bridge the well to the outer pylons.
        int[][] nodes = { { -8, -8 }, { -8, 8 }, { 8, -8 }, { 8, 8 } };
        for (int[] node : nodes) {
            for (int y = 16; y <= 21; y++) {
                PartType type = y == 18 ? PartType.COIL
                        : y == 20 ? PartType.STABILIZER : PartType.AE_QUARTZ;
                builder.filledDiamondAt(node[0], y, node[1], y <= 17 ? 2 : 1, type);
            }
            builder.put(node[0], 22, node[1], PartType.AE_FLUIX);
        }

        // Four short cross-beams establish the lower chamber's cardinal axes.
        for (int[] axis : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            for (int distance = 4; distance <= 10; distance++) {
                builder.put(axis[0] * distance, 20, axis[1] * distance,
                        distance % 3 == 0 ? PartType.COIL : PartType.AE_QUARTZ);
                builder.put(axis[0] * distance, 21, axis[1] * distance,
                        distance % 4 == 0 ? PartType.STABILIZER : PartType.AE_FLUIX);
            }
        }
    }

    private static void addAstralPylons(StructureBuilder builder) {
        // Cardinal pylons replace the old corner tower forest with four strong
        // gate posts. Their alternating cap bands remain readable in JEI and in
        // the world while keeping the core unobstructed.
        int[][] pylons = { { 12, 0 }, { -12, 0 }, { 0, 12 }, { 0, -12 } };
        for (int[] pylon : pylons) {
            addAstralPylon(builder, pylon[0], pylon[1]);
        }

        // Small diagonal counterweights make the transition from the circular
        // base to the four posts feel intentional instead of abrupt.
        int[][] braces = { { -9, -9 }, { -9, 9 }, { 9, -9 }, { 9, 9 } };
        for (int[] brace : braces) {
            for (int y = 19; y <= 24; y++) {
                builder.put(brace[0], y, brace[1],
                        y == 21 || y == 24 ? PartType.STABILIZER : PartType.AE_QUARTZ);
            }
            builder.put(brace[0], 25, brace[1], PartType.COIL);
        }
    }

    private static void addAstralPylon(StructureBuilder builder, int x, int z) {
        for (int y = 16; y <= 19; y++) {
            int radius = y == 16 || y == 19 ? 2 : 1;
            builder.filledDiamondAt(x, y, z, radius, PartType.AE_QUARTZ);
            builder.put(x, y, z,
                    y == 18 ? PartType.COIL : y == 19 ? PartType.STABILIZER : PartType.CASING);
        }

        for (int y = 20; y <= 35; y++) {
            PartType shaft = y % 5 == 0 ? PartType.COIL
                    : y % 3 == 0 ? PartType.AE_FLUIX : PartType.AE_VIBRANT_GLASS;
            builder.put(x, y, z, shaft);
            if (y % 4 == 0) {
                builder.put(x - Integer.signum(x), y, z, PartType.AE_QUARTZ);
                builder.put(x + Integer.signum(x), y, z, PartType.AE_QUARTZ);
                builder.put(x, y, z - Integer.signum(z), PartType.AE_QUARTZ);
                builder.put(x, y, z + Integer.signum(z), PartType.AE_QUARTZ);
            }
        }

        for (int y = 36; y <= 40; y++) {
            int radius = y == 36 || y == 40 ? 2 : 1;
            builder.filledDiamondAt(x, y, z, radius,
                    y == 38 ? PartType.AE_VIBRANT_GLASS : PartType.AE_QUARTZ);
            builder.put(x, y, z,
                    y == 37 || y == 39 ? PartType.STABILIZER : PartType.COIL);
        }
        builder.put(x, 41, z, PartType.AE_FLUIX);
        builder.put(x, 42, z, PartType.STABILIZER);
    }

    private static void addAstralContainment(StructureBuilder builder) {
        // Four intersecting vertical arches make a physical gyroscope around
        // the renderer's air volume. The arches are deliberately offset by one
        // block so the center never becomes a required solid block.
        builder.verticalCircleX(CORE_Y, 11.5, -1, PartType.AE_QUARTZ);
        builder.verticalCircleX(CORE_Y, 11.5, 1, PartType.AE_QUARTZ);
        builder.verticalCircleZ(CORE_Y, 11.5, -1, PartType.AE_QUARTZ);
        builder.verticalCircleZ(CORE_Y, 11.5, 1, PartType.AE_QUARTZ);
        for (int eighth = 0; eighth < 8; eighth++) {
            double angle = eighth * Math.PI / 4.0;
            PartType accent = eighth % 2 == 0 ? PartType.STABILIZER : PartType.AE_FLUIX;
            builder.putVerticalX(CORE_Y, 11.5, -1, angle, accent);
            builder.putVerticalX(CORE_Y, 11.5, 1, angle, accent);
            builder.putVerticalZ(CORE_Y, 11.5, -1, angle, accent);
            builder.putVerticalZ(CORE_Y, 11.5, 1, angle, accent);
        }

        // Equatorial bands and four axis links tie the arches into the pylons.
        builder.ring(26, 10.0, 12.0, PartType.CASING);
        builder.spokes(27, 4.0, 11.0, PartType.AE_FLUIX);
        builder.ring(28, 12.0, 14.0, PartType.AE_QUARTZ);
        builder.ring(29, 13.0, 14.5, PartType.AE_VIBRANT_GLASS);
        builder.ring(31, 10.0, 12.0, PartType.COIL);
        builder.spokes(32, 4.0, 11.0, PartType.AE_QUARTZ);

        for (int[] axis : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            for (int distance = 4; distance <= 11; distance++) {
                builder.put(axis[0] * distance, 27, axis[1] * distance,
                        distance % 3 == 0 ? PartType.COIL : PartType.AE_QUARTZ);
                builder.put(axis[0] * distance, 32, axis[1] * distance,
                        distance % 4 == 0 ? PartType.STABILIZER : PartType.AE_FLUIX);
            }
        }

        // Slim upper and lower anchors leave the active field between them.
        builder.put(0, CORE_Y - 7, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y - 6, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y - 5, 0, PartType.COIL);
        builder.put(0, CORE_Y - 4, 0, PartType.AE_VIBRANT_GLASS);
        builder.put(0, CORE_Y - 3, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y - 2, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y, 0, PartType.AIR);
        builder.put(0, CORE_Y + 2, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y + 3, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y + 4, 0, PartType.AE_VIBRANT_GLASS);
        builder.put(0, CORE_Y + 5, 0, PartType.COIL);
        builder.put(0, CORE_Y + 6, 0, PartType.AE_FLUIX);
        builder.put(0, CORE_Y + 7, 0, PartType.CORE);
    }

    private static void addAstralCrown(StructureBuilder builder) {
        // A tapered, open crown replaces the old flat roof and avoids another
        // large square layer above the gyroscope.
        builder.ring(37, 9.0, 12.0, PartType.AE_QUARTZ);
        builder.spokes(37, 3.0, 9.0, PartType.COIL);
        builder.ring(38, 7.0, 10.0, PartType.CASING);
        builder.ring(39, 5.0, 8.0, PartType.AE_FLUIX);
        builder.ring(40, 3.5, 6.0, PartType.AE_VIBRANT_GLASS);
        builder.ring(41, 2.0, 4.5, PartType.AE_QUARTZ);
        builder.ring(42, 0.5, 2.5, PartType.STABILIZER);
        builder.put(0, 42, 0, PartType.COIL);

        // Four rising arms visually connect each pylon head to the crown.
        int[][] axes = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] axis : axes) {
            for (int distance = 7; distance <= 12; distance++) {
                int y = 38 + (distance % 2);
                builder.put(axis[0] * distance, y, axis[1] * distance,
                        distance % 3 == 0 ? PartType.STABILIZER : PartType.AE_FLUIX);
            }
        }
        builder.put(0, 43, 0, PartType.AE_FLUIX);
        builder.put(0, 44, 0, PartType.AE_VIBRANT_GLASS);
        builder.put(0, 45, 0, PartType.STABILIZER);
    }

    private static List<Part> createPreviousFourTowerParts() {
        var builder = new StructureBuilder();
        addReactorFoundation(builder);
        addReactorEnergyWell(builder);
        addReactorTowers(builder);
        addReactorContainmentFrame(builder);
        addReactorCore(builder);
        addReactorRoof(builder);
        builder.put(CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CASING);
        return builder.build();
    }

    private static void addTwistedFoundation(StructureBuilder builder) {
        // A clipped octagonal plinth breaks the box silhouette while keeping
        // the controller on the center of the front face.
        builder.filledOctagon(12, 15, 5, PartType.AE_QUARTZ);
        builder.octagonRing(12, 15, 5, 2, PartType.CASING);
        builder.filledOctagon(13, 15, 5, PartType.AE_QUARTZ);
        builder.octagonRing(13, 15, 5, 1, PartType.CASING);
        builder.filledOctagon(14, 14, 4, PartType.AE_QUARTZ);
        builder.octagonRing(14, 14, 4, 1, PartType.AE_FLUIX);
        builder.filledOctagon(15, 13, 4, PartType.AE_QUARTZ);
        builder.octagonRing(15, 13, 4, 1, PartType.CASING);

        // Four ice-blue diamond panels and eight purple circuit paths point
        // toward the energy well.
        builder.filledDiamondAt(0, 15, -8, 4, PartType.AE_VIBRANT_GLASS);
        builder.filledDiamondAt(0, 15, 8, 4, PartType.AE_VIBRANT_GLASS);
        builder.filledDiamondAt(-8, 15, 0, 4, PartType.AE_VIBRANT_GLASS);
        builder.filledDiamondAt(8, 15, 0, 4, PartType.AE_VIBRANT_GLASS);
        for (int offset = -12; offset <= 12; offset++) {
            PartType channel = Math.floorMod(offset, 4) == 0
                    ? PartType.COIL : PartType.AE_FLUIX;
            builder.put(offset, 15, 0, channel);
            builder.put(0, 15, offset, channel);
            if (Math.abs(offset) >= 5 && Math.abs(offset) <= 11) {
                builder.put(offset, 15, offset, PartType.AE_FLUIX);
                builder.put(offset, 15, -offset, PartType.AE_FLUIX);
            }
        }
    }

    private static void addTwistedEnergyWell(StructureBuilder builder) {
        builder.filledDiamond(15, 5, PartType.AE_VIBRANT_GLASS);
        builder.diamondRing(15, 5, 7, PartType.AE_FLUIX);
        builder.diamondRing(16, 4, 6, PartType.CASING);
        builder.diamondRing(17, 3, 5, PartType.STABILIZER);

        int[][] anchors = {
                { -3, -3 }, { -3, 3 }, { 3, -3 }, { 3, 3 }
        };
        for (int[] anchor : anchors) {
            for (int y = 16; y <= 20; y++) {
                builder.put(anchor[0], y, anchor[1],
                        y == 18 ? PartType.COIL : PartType.CASING);
            }
            builder.put(anchor[0], 21, anchor[1], PartType.STABILIZER);
        }
    }

    private static void addTwistedPylons(StructureBuilder builder) {
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                addTwistedPylonPair(builder, xSign, zSign);
            }
        }
    }

    private static void addTwistedPylonPair(StructureBuilder builder, int xSign, int zSign) {
        int[] outerOffsets = { 12, 11, 10, 9, 8 };
        int[] innerOffsets = { 9, 8, 7, 7, 7 };

        builder.filledDiamondAt(xSign * 12, 15, zSign * 9, 2, PartType.AE_QUARTZ);
        builder.filledDiamondAt(xSign * 9, 15, zSign * 12, 2, PartType.AE_QUARTZ);

        int previousStage = -1;
        int previousOuter = 0;
        int previousInner = 0;
        for (int y = 16; y <= 42; y++) {
            int stage = y <= 20 ? 0 : y <= 26 ? 1 : y <= 33 ? 2 : y <= 39 ? 3 : 4;
            int outer = outerOffsets[stage];
            int inner = innerOffsets[stage];
            if (stage != previousStage && previousStage >= 0) {
                addTwistedPylonStep(builder, xSign, zSign, y,
                        previousOuter, previousInner, outer, inner);
            }
            addTwistedFinLayer(builder, xSign * outer, zSign * inner,
                    true, xSign, zSign, y);
            addTwistedFinLayer(builder, xSign * inner, zSign * outer,
                    false, xSign, zSign, y);
            previousStage = stage;
            previousOuter = outer;
            previousInner = inner;
        }

        // A stepped local bridge binds each split pair without forming a
        // continuous rectangular cage around the machine.
        int[][] localBridge = {
                { 11, 8, 23 }, { 10, 8, 23 }, { 10, 9, 23 },
                { 9, 9, 24 }, { 9, 10, 24 }, { 8, 10, 24 }, { 8, 11, 24 }
        };
        for (int index = 0; index < localBridge.length; index++) {
            int[] point = localBridge[index];
            builder.put(xSign * point[0], point[2], zSign * point[1],
                    index == 3 ? PartType.COIL : PartType.AE_QUARTZ);
        }
    }

    private static void addTwistedFinLayer(StructureBuilder builder, int centerX, int centerZ,
            boolean alongZ, int xSign, int zSign, int y) {
        boolean jointBand = y == 20 || y == 26 || y == 33 || y == 39 || y == 42;
        for (int tangent = -1; tangent <= 1; tangent++) {
            int x = alongZ ? centerX : centerX + tangent;
            int z = alongZ ? centerZ + tangent : centerZ;
            builder.put(x, y, z,
                    jointBand ? PartType.AE_FLUIX
                            : tangent == 0 ? PartType.AE_VIBRANT_GLASS : PartType.CASING);
            builder.put(x - (alongZ ? xSign : 0), y,
                    z - (alongZ ? 0 : zSign),
                    jointBand ? PartType.STABILIZER : PartType.AE_QUARTZ);
        }
        if (jointBand) {
            builder.put(centerX, y, centerZ, PartType.COIL);
        }
    }

    private static void addTwistedPylonStep(StructureBuilder builder, int xSign, int zSign, int y,
            int previousOuter, int previousInner, int outer, int inner) {
        int previousFirstX = xSign * previousOuter;
        int previousFirstZ = zSign * previousInner;
        int firstX = xSign * outer;
        int firstZ = zSign * inner;
        builder.put(previousFirstX, y, previousFirstZ, PartType.CASING);
        builder.put(firstX, y, previousFirstZ, PartType.AE_QUARTZ);
        builder.put(firstX, y, firstZ, PartType.COIL);

        int previousSecondX = xSign * previousInner;
        int previousSecondZ = zSign * previousOuter;
        int secondX = xSign * inner;
        int secondZ = zSign * outer;
        builder.put(previousSecondX, y, previousSecondZ, PartType.CASING);
        builder.put(previousSecondX, y, secondZ, PartType.AE_QUARTZ);
        builder.put(secondX, y, secondZ, PartType.COIL);
    }

    private static void addTwistedContainmentFrame(StructureBuilder builder) {
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                // Two offset short emitters create a three-dimensional broken
                // diamond around the field instead of a full square rail.
                for (int offset = 7; offset <= 10; offset++) {
                    builder.put(xSign * offset, 28, zSign * 7,
                            offset == 9 ? PartType.COIL : PartType.AE_QUARTZ);
                    builder.put(xSign * 7, 30, zSign * offset,
                            offset == 9 ? PartType.COIL : PartType.AE_QUARTZ);
                }
                builder.put(xSign * 7, 28, zSign * 7, PartType.CASING);
                builder.put(xSign * 7, 29, zSign * 7, PartType.STABILIZER);
                builder.put(xSign * 7, 30, zSign * 7, PartType.CASING);

                int[][] firstRoofBridge = {
                        { 8, 7, 42 }, { 7, 7, 42 }, { 7, 6, 42 },
                        { 7, 5, 42 }, { 7, 5, 43 }, { 6, 5, 43 }, { 6, 4, 43 }
                };
                int[][] secondRoofBridge = {
                        { 7, 8, 42 }, { 7, 7, 42 }, { 6, 7, 42 },
                        { 5, 7, 42 }, { 5, 7, 43 }, { 5, 6, 43 }, { 4, 6, 43 }
                };
                addTwistedRoofBridge(builder, xSign, zSign, firstRoofBridge);
                addTwistedRoofBridge(builder, xSign, zSign, secondRoofBridge);
            }
        }
    }

    private static void addTwistedRoofBridge(StructureBuilder builder, int xSign, int zSign,
            int[][] bridge) {
        for (int index = 0; index < bridge.length; index++) {
            int[] point = bridge[index];
            builder.put(xSign * point[0], point[2], zSign * point[1],
                    index == 2 ? PartType.AE_FLUIX : PartType.CASING);
        }
    }

    private static void addTwistedRoof(StructureBuilder builder) {
        builder.diamondRing(43, 7, 11, PartType.AE_QUARTZ);
        builder.diamondRing(43, 6, 7, PartType.AE_FLUIX);
        builder.diamondRing(44, 9, 11, PartType.CASING);
        for (int offset = -6; offset <= 6; offset++) {
            PartType spoke = Math.floorMod(offset, 4) == 0
                    ? PartType.COIL : PartType.AE_VIBRANT_GLASS;
            builder.put(offset, 43, 0, spoke);
            builder.put(0, 43, offset, spoke);
        }
        builder.filledDiamond(43, 1, PartType.CASING);
        builder.put(0, 42, 0, PartType.STABILIZER);
        builder.put(0, 41, 0, PartType.AE_FLUIX);
    }

    private static void addReactorFoundation(StructureBuilder builder) {
        // A stepped square plinth replaces the former circular palace. The
        // foundation stays solid while circuit bands give each layer a clear
        // technical role.
        builder.filledSquare(12, 15, PartType.AE_QUARTZ);
        builder.squareRing(12, 15, 2, PartType.CASING);

        builder.filledSquare(13, 15, PartType.AE_QUARTZ);
        builder.squareRing(13, 15, 1, PartType.CASING);
        for (int offset = -13; offset <= 13; offset++) {
            builder.put(offset, 13, 0, PartType.AE_FLUIX);
            builder.put(0, 13, offset, PartType.AE_FLUIX);
        }

        builder.filledSquare(14, 14, PartType.AE_QUARTZ);
        builder.squareRing(14, 14, 1, PartType.AE_FLUIX);
        builder.filledSquare(15, 13, PartType.AE_QUARTZ);
        builder.squareRing(15, 13, 1, PartType.CASING);
        for (int offset = -11; offset <= 11; offset++) {
            PartType channel = Math.floorMod(offset, 4) == 0
                    ? PartType.COIL : PartType.AE_FLUIX;
            builder.put(offset, 15, 0, channel);
            builder.put(0, 15, offset, channel);
        }
    }

    private static void addReactorEnergyWell(StructureBuilder builder) {
        builder.filledSquareAt(0, 15, 0, 4, PartType.AE_VIBRANT_GLASS);
        builder.squareRingAt(0, 15, 0, 6, 2, PartType.AE_FLUIX);
        builder.squareRingAt(0, 16, 0, 5, 1, PartType.CASING);
        builder.squareRingAt(0, 17, 0, 4, 1, PartType.STABILIZER);

        int[][] anchors = {
                { -3, -3 }, { -3, 3 }, { 3, -3 }, { 3, 3 }
        };
        for (int[] anchor : anchors) {
            for (int y = 16; y <= 20; y++) {
                builder.put(anchor[0], y, anchor[1],
                        y == 18 ? PartType.COIL : PartType.CASING);
            }
            builder.put(anchor[0], 21, anchor[1], PartType.STABILIZER);
        }
    }

    private static void addReactorTowers(StructureBuilder builder) {
        int[][] towers = {
                { -REACTOR_TOWER_OFFSET, -REACTOR_TOWER_OFFSET },
                { REACTOR_TOWER_OFFSET, -REACTOR_TOWER_OFFSET },
                { -REACTOR_TOWER_OFFSET, REACTOR_TOWER_OFFSET },
                { REACTOR_TOWER_OFFSET, REACTOR_TOWER_OFFSET }
        };
        for (int[] tower : towers) {
            addReactorTower(builder, tower[0], tower[1]);
        }
    }

    private static void addReactorTower(StructureBuilder builder, int x, int z) {
        for (int y = 16; y <= 19; y++) {
            builder.filledSquareAt(x, y, z, 2, PartType.AE_QUARTZ);
            builder.squareRingAt(x, y, z, 2, 1,
                    y == 16 || y == 19 ? PartType.CASING : PartType.STABILIZER);
            builder.put(x, y, z, y == 18 ? PartType.COIL : PartType.AE_FLUIX);
        }

        for (int y = 20; y <= 35; y++) {
            builder.filledSquareAt(x, y, z, 1, PartType.AE_QUARTZ);
            if (y % 4 == 0) {
                builder.squareRingAt(x, y, z, 1, 1, PartType.CASING);
            }
            builder.put(x, y, z,
                    y % 5 == 0 ? PartType.COIL : PartType.AE_FLUIX);
        }

        for (int y = 36; y <= 40; y++) {
            builder.filledSquareAt(x, y, z, 2, PartType.AE_QUARTZ);
            builder.squareRingAt(x, y, z, 2, 1,
                    y == 36 || y == 40 ? PartType.CASING : PartType.STABILIZER);
            builder.put(x, y, z, y == 38 ? PartType.COIL : PartType.AE_FLUIX);
        }
    }

    private static void addReactorContainmentFrame(StructureBuilder builder) {
        // Two rigid beam levels tie the four towers together. The front-center
        // section of the lower frame remains open for an unobstructed approach
        // to the controller and energy well.
        for (int offset = -REACTOR_TOWER_OFFSET + 1;
                offset <= REACTOR_TOWER_OFFSET - 1; offset++) {
            PartType lowerType = Math.floorMod(offset, 4) == 0
                    ? PartType.AE_FLUIX : PartType.AE_QUARTZ;
            if (Math.abs(offset) > 5) {
                builder.put(offset, 20, -REACTOR_TOWER_OFFSET, lowerType);
                builder.put(offset, 21, -REACTOR_TOWER_OFFSET, PartType.CASING);
            }
            builder.put(offset, 20, REACTOR_TOWER_OFFSET, lowerType);
            builder.put(offset, 21, REACTOR_TOWER_OFFSET, PartType.CASING);
            builder.put(-REACTOR_TOWER_OFFSET, 20, offset, lowerType);
            builder.put(-REACTOR_TOWER_OFFSET, 21, offset, PartType.CASING);
            builder.put(REACTOR_TOWER_OFFSET, 20, offset, lowerType);
            builder.put(REACTOR_TOWER_OFFSET, 21, offset, PartType.CASING);

            PartType upperType = Math.floorMod(offset, 5) == 0
                    ? PartType.COIL : PartType.AE_QUARTZ;
            builder.put(offset, 38, -REACTOR_TOWER_OFFSET, upperType);
            builder.put(offset, 38, REACTOR_TOWER_OFFSET, upperType);
            builder.put(-REACTOR_TOWER_OFFSET, 38, offset, upperType);
            builder.put(REACTOR_TOWER_OFFSET, 38, offset, upperType);
            builder.put(offset, 39, -REACTOR_TOWER_OFFSET, PartType.CASING);
            builder.put(offset, 39, REACTOR_TOWER_OFFSET, PartType.CASING);
            builder.put(-REACTOR_TOWER_OFFSET, 39, offset, PartType.CASING);
            builder.put(REACTOR_TOWER_OFFSET, 39, offset, PartType.CASING);
        }

        // Four short diagonal emitters point inward without enclosing the core
        // in another physical ring.
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                for (int step = 0; step <= 5; step++) {
                    int x = xSign * (REACTOR_TOWER_OFFSET - step);
                    int z = zSign * (REACTOR_TOWER_OFFSET - step);
                    builder.put(x, 28, z, PartType.CASING);
                    builder.put(x, 29, z,
                            step == 2 ? PartType.COIL : PartType.AE_QUARTZ);
                }
                int emitterX = xSign * REACTOR_EMITTER_OFFSET;
                int emitterZ = zSign * REACTOR_EMITTER_OFFSET;
                builder.put(emitterX, 29, emitterZ, PartType.STABILIZER);
                builder.put(emitterX - xSign, 29, emitterZ, PartType.AE_FLUIX);
                builder.put(emitterX, 29, emitterZ - zSign, PartType.AE_FLUIX);
            }
        }
    }

    private static void addReactorCore(StructureBuilder builder) {
        // Renderer anchor only: the active field occupies this air volume.
        builder.put(0, CORE_Y, 0, PartType.AIR);
        builder.put(0, CORE_Y - 7, 0, PartType.STABILIZER);
        builder.put(0, CORE_Y + 7, 0, PartType.CORE);
    }

    private static void addReactorRoof(StructureBuilder builder) {
        builder.filledSquare(40, 10, PartType.AE_QUARTZ);
        builder.squareRing(40, 10, 1, PartType.CASING);
        for (int offset = -9; offset <= 9; offset++) {
            PartType channel = Math.floorMod(offset, 4) == 0
                    ? PartType.COIL : PartType.AE_FLUIX;
            builder.put(offset, 40, 0, channel);
            builder.put(0, 40, offset, channel);
        }
        builder.squareRing(41, 10, 2, PartType.AE_QUARTZ);
        builder.filledSquareAt(0, 41, 0, 1, PartType.AE_VIBRANT_GLASS);
        builder.put(0, 41, 0, PartType.STABILIZER);
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
        builder.disk(12, 15.55, PartType.AE_QUARTZ);
        builder.spokes(12, 5.0, 14.8, PartType.AE_FLUIX);
        builder.ring(12, 14.75, 15.55, PartType.CASING);

        builder.disk(13, 15.55, PartType.AE_QUARTZ);
        builder.spokes(13, 5.2, 14.7, PartType.AE_FLUIX);
        builder.ring(13, 14.7, 15.55, PartType.CASING);

        builder.disk(14, 15.55, PartType.AE_QUARTZ);
        builder.ring(14, 5.0, 7.0, PartType.STABILIZER);
        builder.spokes(14, 6.4, 13.9, PartType.COIL);

        builder.disk(15, 15.55, PartType.AE_QUARTZ);
        builder.ring(15, 13.6, 15.55, PartType.AE_FLUIX);
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

        // Logical renderer anchor only; this is not a required physical part.
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
        for (var previous : createPreviousFourTowerParts()) {
            var cleanup = new Part(previous.x(), previous.y(), previous.z(), PartType.AIR);
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

        void filledSquare(int y, int halfExtent, PartType type) {
            filledSquareAt((int) Math.round(centerX), y,
                    (int) Math.round(centerZ), halfExtent, type);
        }

        void filledSquareAt(int centerX, int y, int centerZ,
                int halfExtent, PartType type) {
            for (int x = centerX - halfExtent; x <= centerX + halfExtent; x++) {
                for (int z = centerZ - halfExtent; z <= centerZ + halfExtent; z++) {
                    put(x, y, z, type);
                }
            }
        }

        void squareRing(int y, int outerHalfExtent, int width, PartType type) {
            squareRingAt((int) Math.round(centerX), y,
                    (int) Math.round(centerZ), outerHalfExtent, width, type);
        }

        void squareRingAt(int centerX, int y, int centerZ,
                int outerHalfExtent, int width, PartType type) {
            int innerHalfExtent = Math.max(-1, outerHalfExtent - width);
            for (int x = centerX - outerHalfExtent; x <= centerX + outerHalfExtent; x++) {
                for (int z = centerZ - outerHalfExtent; z <= centerZ + outerHalfExtent; z++) {
                    if (Math.abs(x - centerX) > innerHalfExtent
                            || Math.abs(z - centerZ) > innerHalfExtent) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void filledOctagon(int y, int halfExtent, int cornerCut, PartType type) {
            for (int x = -halfExtent; x <= halfExtent; x++) {
                for (int z = -halfExtent; z <= halfExtent; z++) {
                    if (insideOctagon(x, z, halfExtent, cornerCut)) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void octagonRing(int y, int halfExtent, int cornerCut,
                int width, PartType type) {
            int innerHalfExtent = halfExtent - width;
            int innerCornerCut = Math.max(0, cornerCut - width);
            for (int x = -halfExtent; x <= halfExtent; x++) {
                for (int z = -halfExtent; z <= halfExtent; z++) {
                    if (insideOctagon(x, z, halfExtent, cornerCut)
                            && !insideOctagon(x, z, innerHalfExtent, innerCornerCut)) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void filledDiamond(int y, int radius, PartType type) {
            filledDiamondAt((int) Math.round(centerX), y,
                    (int) Math.round(centerZ), radius, type);
        }

        void filledDiamondAt(int centerX, int y, int centerZ,
                int radius, PartType type) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (Math.abs(x - centerX) + Math.abs(z - centerZ) <= radius) {
                        put(x, y, z, type);
                    }
                }
            }
        }

        void diamondRing(int y, int innerRadius, int outerRadius, PartType type) {
            for (int x = (int) Math.round(centerX) - outerRadius;
                    x <= (int) Math.round(centerX) + outerRadius; x++) {
                for (int z = (int) Math.round(centerZ) - outerRadius;
                        z <= (int) Math.round(centerZ) + outerRadius; z++) {
                    int distance = Math.abs(x - (int) Math.round(centerX))
                            + Math.abs(z - (int) Math.round(centerZ));
                    if (distance >= innerRadius && distance <= outerRadius) {
                        put(x, y, z, type);
                    }
                }
            }
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

        private static boolean insideOctagon(int x, int z, int halfExtent, int cornerCut) {
            if (halfExtent < 0) {
                return false;
            }
            int absoluteX = Math.abs(x);
            int absoluteZ = Math.abs(z);
            return absoluteX <= halfExtent
                    && absoluteZ <= halfExtent
                    && absoluteX + absoluteZ <= halfExtent * 2 - cornerCut;
        }
    }
}
