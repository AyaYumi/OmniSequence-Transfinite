package com.atir.molecularmanipulator.blockentity;

import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pearl-white genesis chamber with three transparent field walls and an open front.
 * Only the current blueprint and its service sockets are supported.
 */
public final class MatterFabricationStructure {
    public static final int RADIUS = MatterPearlGeometry.RADIUS;

    public static final int CONTROLLER_Y = 2;
    public static final int CONTROLLER_Z = -15;
    public static final int EFFECT_CENTER_Y = MatterPearlGeometry.CENTER_Y;
    public static final int STRUCTURE_HEIGHT = MatterPearlGeometry.MAX_Y - MatterPearlGeometry.MIN_Y + 1;
    public static final OptionalPart ITEM_INPUT = new OptionalPart(-8, 1, -15,
            ModContent.MATTER_FABRICATION_ITEM_INPUT.get());
    public static final OptionalPart ITEM_OUTPUT = new OptionalPart(-4, 1, -15,
            ModContent.MATTER_FABRICATION_ITEM_OUTPUT.get());
    public static final OptionalPart PATTERN_ASSEMBLY = new OptionalPart(0, 1, -15,
            ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get());
    public static final OptionalPart FLUID_INPUT = new OptionalPart(4, 1, -15,
            ModContent.MATTER_FABRICATION_FLUID_INPUT.get());
    public static final OptionalPart FLUID_OUTPUT = new OptionalPart(8, 1, -15,
            ModContent.MATTER_FABRICATION_FLUID_OUTPUT.get());
    private static final List<OptionalPart> OPTIONAL_PARTS = List.of(
            ITEM_INPUT, ITEM_OUTPUT, FLUID_INPUT, FLUID_OUTPUT, PATTERN_ASSEMBLY);
    private static final List<Part> PARTS = MatterPearlGeometry.createParts();
    private static final List<Part> PATTERN_ASSEMBLY_BAYS = createPatternAssemblyBays();
    private static final Map<LocalPos, Part> CURRENT_PARTS_BY_POS = indexParts(PARTS);
    private static final List<Part> DISMANTLE_PARTS = createDismantleParts();

    private static final Map<StructureLayout, List<Part>> CHUNK_CORNERS = createChunkCorners();

    private static Map<StructureLayout, List<Part>> createChunkCorners() {
        var result = new java.util.EnumMap<StructureLayout, List<Part>>(StructureLayout.class);
        for (var layout : StructureLayout.values()) {
            if (!layout.isFormed()) continue;
            var blueprint = parts(layout);
            int minX = blueprint.stream().mapToInt(Part::x).min().orElse(0);
            int maxX = blueprint.stream().mapToInt(Part::x).max().orElse(0);
            int minZ = blueprint.stream().mapToInt(Part::z).min().orElse(0);
            int maxZ = blueprint.stream().mapToInt(Part::z).max().orElse(0);
            result.put(layout, List.of(new Part(minX, 0, minZ, PartType.CASING),
                    new Part(maxX, 0, maxZ, PartType.CASING)));
        }
        return result;
    }

    public static Set<ChunkPos> chunkFootprint(
            BlockPos controller, Direction facing, StructureLayout layout) {
        var corners = CHUNK_CORNERS.get(layout);
        if (corners == null) return java.util.Set.of();
        return MultiblockChunkLoading.rectangle(worldPos(controller, facing, corners.get(0)),
                worldPos(controller, facing, corners.get(1)));
    }

    private MatterFabricationStructure() {
    }

    public static List<Part> parts() {
        return PARTS;
    }

    /** All current-layout casing positions that may host a pattern assembly. */
    public static List<Part> patternAssemblyBays() {
        return PATTERN_ASSEMBLY_BAYS;
    }

    public static List<Part> parts(StructureLayout layout) {
        return layout == StructureLayout.CURRENT ? PARTS : List.of();
    }

    /** Returns a layout only after every required block of that blueprint matches. */
    public static StructureLayout detectLayout(Level level, BlockPos controller, Direction facing) {
        return matchesCompleteLayout(level, controller, facing, StructureLayout.CURRENT)
                ? StructureLayout.CURRENT : StructureLayout.NONE;
    }

    public static boolean matchesCompleteLayout(Level level, BlockPos controller,
            Direction facing, StructureLayout layout) {
        if (!layout.isFormed()) {
            return false;
        }
        for (var part : parts(layout)) {
            var pos = worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)
                    || !matchesLayoutPart(level.getBlockState(pos), part, layout)) {
                return false;
            }
        }
        return true;
    }

    /** A partial site is safe to infer only if every plausible layout yields the same remaining blocks. */
    public static StructureLayout inferDismantleLayout(Level level, BlockPos controller, Direction facing) {
        StructureLayout result = StructureLayout.NONE;
        Map<BlockPos, net.minecraft.world.level.block.Block> remaining = null;
        for (var layout : StructureLayout.values()) {
            if (!layout.isFormed()) {
                continue;
            }
            var matched = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.Block>();
            for (var part : parts(layout)) {
                if (isController(part)) {
                    continue;
                }
                var pos = worldPos(controller, facing, part);
                if (!level.hasChunkAt(pos)) {
                    return StructureLayout.NONE;
                }
                var state = level.getBlockState(pos);
                if (!state.isAir() && matchesLayoutPart(state, part, layout)) {
                    matched.put(pos, state.getBlock());
                }
            }
            if (matched.isEmpty()) {
                continue;
            }
            if (remaining != null && !remaining.equals(matched)) {
                return StructureLayout.NONE;
            }
            remaining = matched;
            result = layout;
        }
        return result;
    }

    /** Capture only actual members of one proven blueprint, including its legal service substitutions. */
    public static DismantlePlan captureDismantlePlan(Level level, BlockPos controller,
            Direction facing, StructureLayout layout) {
        if (!layout.isFormed()) {
            return null;
        }
        var entries = new ArrayList<DismantlePlan.Entry>();
        for (var part : parts(layout)) {
            if (isController(part)) {
                continue;
            }
            var pos = worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            var state = level.getBlockState(pos);
            if (!pos.equals(controller) && !state.isAir()
                    && !state.is(ModContent.MATTER_FABRICATION_CONTROLLER.get())
                    && matchesLayoutPart(state, part, layout)) {
                entries.add(new DismantlePlan.Entry(pos, state.getBlock()));
            }
        }
        return DismantlePlan.create(entries);
    }

    /**
     * Covers both the current frame and every earlier 31 x 31 fabrication-well
     * layout. Dismantling still removes only recognized fabrication blocks.
     */
    public static List<Part> dismantleParts() {
        return DISMANTLE_PARTS;
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

    public static BlockPos worldPos(BlockPos controller, Direction facing, OptionalPart part) {
        return worldPos(controller, facing, new Part(part.x(), part.y(), part.z(), PartType.CASING));
    }

    public static BlockState partState(PartType type) {
        return switch (type) {
            case CONTROLLER -> ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState();
            case CASING -> ModContent.MATTER_FABRICATION_CASING.get().defaultBlockState();
            case GLASS -> ModContent.MATTER_FABRICATION_GLASS.get().defaultBlockState();
            case COIL -> ModContent.MATTER_FABRICATION_COIL.get().defaultBlockState();
            case STABILIZER -> ModContent.MATTER_FABRICATION_STABILIZER.get().defaultBlockState();
            case CORE -> ModContent.MATTER_FABRICATION_CORE.get().defaultBlockState();
            case AE_QUARTZ -> AEBlocks.QUARTZ_BLOCK.block().defaultBlockState();
            case AE_FLUIX -> AEBlocks.FLUIX_BLOCK.block().defaultBlockState();
            case AE_VIBRANT_GLASS -> AEBlocks.QUARTZ_VIBRANT_GLASS.block().defaultBlockState();
            case VANILLA_QUARTZ -> Blocks.QUARTZ_BLOCK.defaultBlockState();
            case SMOOTH_QUARTZ -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            case QUARTZ_PILLAR -> Blocks.QUARTZ_PILLAR.defaultBlockState();
            case QUARTZ_BRICKS -> Blocks.QUARTZ_BRICKS.defaultBlockState();
            case DARK_FRAME -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            case CYAN_CONDUIT -> Blocks.CYAN_CONCRETE.defaultBlockState();
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
            if (matches(current, part)) {
                correct++;
            } else if (current.canBeReplaced()) {
                missing++;
            } else {
                conflicts++;
            }
        }
        return new Inspection(PARTS.size(), correct, missing, conflicts);
    }

    public static boolean matches(BlockState state, Part part) {
        return state.is(partState(part.type()).getBlock()) || matchesOptionalPart(state, part)
                || isFrontServiceRow(part) && isAnyOptionalBlock(state);
    }

    public static boolean matchesLayoutPart(BlockState state, Part part, StructureLayout layout) {
        return layout == StructureLayout.CURRENT && matches(state, part);
    }

    public static boolean isValidCurrentBlockAt(BlockState state, Part sourcePart) {
        var current = CURRENT_PARTS_BY_POS.get(
                new LocalPos(sourcePart.x(), sourcePart.y(), sourcePart.z()));
        return current != null && matches(state, current);
    }

    public static boolean isStructurePart(BlockState state) {
        if (state.is(ModContent.MATTER_FABRICATION_CONTROLLER.get())
                || state.is(ModContent.MATTER_FABRICATION_CASING.get())
                || state.is(ModContent.MATTER_FABRICATION_GLASS.get())
                || state.is(ModContent.MATTER_FABRICATION_COIL.get())
                || state.is(ModContent.MATTER_FABRICATION_STABILIZER.get())
                || state.is(ModContent.MATTER_FABRICATION_CORE.get())) {
            return true;
        }
        for (var optional : OPTIONAL_PARTS) {
            if (state.is(optional.block())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Common AE2/vanilla decoration is removable only when it exactly matches
     * the current blueprint coordinate. Dedicated fabrication blocks remain
     * eligible throughout the legacy cleanup scan.
     */
    public static boolean isDismantleMatch(BlockState state, Part part) {
        return matches(state, part) || isStructurePart(state);
    }

    public static boolean isMigrationMatch(BlockState state, Part part,
            StructureLayout layout) {
        return matchesLayoutPart(state, part, layout);
    }

    private static boolean matchesOptionalPart(BlockState state, Part part) {
        if (!isCurrentServiceBay(part)) {
            return false;
        }
        for (var optional : OPTIONAL_PARTS) {
            if (state.is(optional.block())) {
                return true;
            }
        }
        return false;
    }

    /**
     * All five service blocks share the original sockets and the current front
     * terrace row, leaving the central entrance clear.
     */
    public static boolean isPatternAssemblyBay(Part part) {
        return isPatternAssemblyBay(part, StructureLayout.CURRENT);
    }

    private static boolean isPatternAssemblyBay(Part part, StructureLayout layout) {
        return layout == StructureLayout.CURRENT && isCurrentServiceBay(part);
    }

    /** The front-facing casing row has twelve blocks on each side of the entrance. */
    private static boolean isFrontServiceRow(Part part) {
        return part.type() == PartType.CASING && part.y() == 2 && part.z() == -19
                && Math.abs(part.x()) >= 2 && Math.abs(part.x()) <= 13;
    }

    private static boolean isCurrentServiceBay(Part part) {
        return isFrontServiceRow(part) || isCentralCollarServiceBay(part);
    }

    private static boolean isCentralCollarServiceBay(Part part) {
        if (part.type() != PartType.CASING || part.y() != 4) return false;
        for (int side : new int[] {-1, 1}) for (int tangent = -2; tangent <= 2; tangent++) {
            if (part.x() == side * 8 && part.z() == tangent
                    || part.x() == tangent && part.z() == side * 8) return true;
        }
        return false;
    }

    private static boolean isAnyOptionalBlock(BlockState state) {
        for (var optional : OPTIONAL_PARTS) {
            if (state.is(optional.block())) {
                return true;
            }
        }
        return false;
    }

    private static List<Part> createDismantleParts() { return PARTS; }

    private static Map<LocalPos, Part> indexParts(List<Part> parts) {
        Map<LocalPos, Part> result = new LinkedHashMap<>();
        for (var part : parts) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return Map.copyOf(result);
    }

    private static List<Part> createPatternAssemblyBays() {
        var result = new ArrayList<Part>();
        for (var part : PARTS) {
            if (isPatternAssemblyBay(part, StructureLayout.CURRENT)) {
                result.add(part);
            }
        }
        result.sort(java.util.Comparator
                .comparingInt(Part::z)
                .thenComparingInt(Part::x)
                .thenComparingInt(Part::y));
        return List.copyOf(result);
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
        CORE,
        AE_QUARTZ,
        AE_FLUIX,
        AE_VIBRANT_GLASS,
        VANILLA_QUARTZ,
        SMOOTH_QUARTZ,
        QUARTZ_PILLAR,
        QUARTZ_BRICKS,
        DARK_FRAME,
        CYAN_CONDUIT
    }

    public enum StructureLayout {
        NONE, CURRENT;
        public boolean isFormed() { return this == CURRENT; }
        public boolean requiresUpdate() { return false; }
        public static StructureLayout fromSavedName(String name) {
            return "CURRENT".equals(name) ? CURRENT : NONE;
        }
    }

    public record Part(int x, int y, int z, PartType type) {
    }

    public record OptionalPart(int x, int y, int z, net.minecraft.world.level.block.Block block) {
    }

    private record LocalPos(int x, int y, int z) {
    }

    public record Inspection(int total, int correct, int missing, int conflicts) {
        public boolean formed() {
            return correct == total;
        }
    }
}
