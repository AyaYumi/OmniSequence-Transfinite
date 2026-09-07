package com.atir.molecularmanipulator.blockentity;

import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
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
    public static final int WIDTH = CenteredFeatherGeometry.RADIUS * 2 + 1;
    public static final int HEIGHT = CenteredFeatherGeometry.HEIGHT;
    public static final int MIN_X = -CenteredFeatherGeometry.RADIUS;
    public static final int MAX_X = CenteredFeatherGeometry.RADIUS;
    public static final int MIN_Z = MIN_X;
    public static final int MAX_Z = MAX_X;
    // Retired blueprints must not grow when the current crown's envelope changes.

    public static final int CENTER_Z = 0;
    /** Shared pipeline-port coordinate for the 1.21.1 branch layout. */
    public static final int FOUNDATION_Y = 0;
    /** Historical center retained for all pre-crown layouts. */
    public static final int CORE_Y = 29;
    public static final int CURRENT_CORE_Y = CenteredFeatherGeometry.CORE_Y;
    public static final int CONTROLLER_X = LoweredFeatherGeometry.CONTROLLER_X;
    public static final int CONTROLLER_Y = LoweredFeatherGeometry.CONTROLLER_Y;
    public static final int CONTROLLER_Z = LoweredFeatherGeometry.CONTROLLER_Z;
    private static final int HISTORICAL_CONTROLLER_X = 0;
    private static final int HISTORICAL_CONTROLLER_Y = 13;
    private static final int HISTORICAL_CONTROLLER_Z = -15;
    public static final int CURRENT_MIN_Y = CenteredFeatherGeometry.MIN_Y;
    public static final int CURRENT_MAX_Y = CenteredFeatherGeometry.MAX_Y;
    public static final int CURRENT_SIZE = HEIGHT;

    public static final double VISUAL_CENTER_X = 0.0;
    public static final double VISUAL_CENTER_Z = 0.0;

    private static final List<Part> PARTS = createParts();
    private static final List<Part> LEGACY_PARTS = Legacy139Blueprints.load("sequence_array",
            (x, y, z, type) -> new Part(x, y, z, PartType.valueOf(type)));
    private static final Part VISUAL_CENTER_PART = new Part(0, CURRENT_CORE_Y, 0, PartType.AIR);
    private static final Part UPPER_CORE_PART = new Part(0, CURRENT_CORE_Y + 7, 0, PartType.CORE);
    private static final Map<LocalPos, Part> PART_LOOKUP = createLookup();
    private static final Map<StructureLayout, Map<LocalPos, Part>> LAYOUT_LOOKUPS = createLayoutLookups();
    private static final Map<StructureLayout, LayoutBounds> LAYOUT_BOUNDS = createLayoutBounds();
    private static final List<Part> WORK_PARTS = createWorkParts();
    private static final Map<Part, List<Part>> HISTORICAL_OCCUPANCY_PARTS = createHistoricalOccupancyParts();

    private MolecularCenterStructure() {
    }

    public static List<Part> parts() {
        return PARTS;
    }

    public static List<Part> parts(StructureLayout layout) {
        return partsFor(layout);
    }

    /** Snapshot only blocks belonging to one identified build (or its pending upgrade). */
    public static DismantlePlan createDismantlePlan(Level level, BlockPos controller, Direction facing,
            StructureLayout layout, boolean includeUpgradeTarget) {
        if (!layout.isFormed() || !(includeUpgradeTarget
                ? areUpdateChunksLoaded(level, controller, facing, layout)
                : areRequiredChunksLoaded(level, controller, facing, layout))) {
            return null;
        }
        var entries = new java.util.ArrayList<DismantlePlan.Entry>();
        var candidates = includeUpgradeTarget ? List.of(layout, StructureLayout.CURRENT) : List.of(layout);
        for (var candidate : candidates) {
            for (var part : partsFor(candidate)) {
                if (part.partType() == PartType.AIR || isController(part, candidate)) {
                    continue;
                }
                var pos = worldPos(controller, facing, part, layout);
                if (pos.equals(controller)) {
                    continue;
                }
                var state = level.getBlockState(pos);
                if (!state.is(partBlock(part.partType()))) {
                    continue;
                }
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null && !(blockEntity instanceof MolecularCenterShellBlockEntity)) {
                    continue;
                }
                entries.add(new DismantlePlan.Entry(pos, state.getBlock()));
            }
        }
        return DismantlePlan.create(entries);
    }

    public static int visualCoreY(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9 ? CORE_Y : CURRENT_CORE_Y;
    }

    public static List<Part> workParts() {
        return WORK_PARTS;
    }

    /** Conservatively distinguish an isolated old controller from a partial old build. */
    public static boolean isHistoricalAreaEmpty(Level level, BlockPos controller, Direction facing) {
        return isHistoricalAreaEmpty(level, controller, facing, StructureLayout.LEGACY_1_3_9);
    }

    public static boolean isHistoricalAreaEmpty(Level level, BlockPos controller, Direction facing,
            StructureLayout anchorLayout) {
        var parts = HISTORICAL_OCCUPANCY_PARTS.get(controllerPart(anchorLayout));
        if (parts == null) {
            return false;
        }
        for (var part : parts) {
            var pos = worldPos(controller, facing, part, anchorLayout);
            if (!level.hasChunkAt(pos)) {
                return false;
            }
        }
        for (var part : parts) {
            var pos = worldPos(controller, facing, part, anchorLayout);
            if (pos.equals(controller)) {
                continue;
            }
            var state = level.getBlockState(pos);
            if (isStructurePart(state) || !state.isAir() && !state.canBeReplaced()
                    || level.getBlockEntity(pos) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a deterministic, source-specific migration queue. Old-only
     * positions are cleared first and current-layout targets overwrite shared
     * positions. This deliberately excludes every unrelated historical
     * layout so an update cannot sweep player decoration merely because it
     * occupies a coordinate used by some other retired design.
     */
    public static List<Part> updateWorkParts(StructureLayout sourceLayout) {
        var sourceParts = partsFor(sourceLayout);
        if (!sourceLayout.requiresUpdate() || sourceParts.isEmpty()) {
            return List.of();
        }
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var source : sourceParts) {
            if (isController(source, sourceLayout) || source.partType() == PartType.AIR) {
                continue;
            }
            var cleanup = new Part(source.x(), source.y(), source.z(), PartType.AIR);
            result.put(new LocalPos(cleanup.x(), cleanup.y(), cleanup.z()), cleanup);
        }
        for (var target : PARTS) {
            result.put(new LocalPos(target.x(), target.y(), target.z()), target);
        }
        return List.copyOf(result.values());
    }

    /** Returns true only when the state is the exact block expected by the detected source layout. */
    public static boolean matchesSourcePart(StructureLayout sourceLayout, Part workPart, BlockState state) {
        var lookup = LAYOUT_LOOKUPS.get(sourceLayout);
        if (lookup == null) {
            return false;
        }
        var source = lookup.get(new LocalPos(workPart.x(), workPart.y(), workPart.z()));
        return source != null
                && source.partType() != PartType.AIR
                && state.is(partBlock(source.partType()));
    }

    public static boolean isWithinUpdateHeight(Level level, BlockPos controller,
            StructureLayout sourceLayout) {
        int targetY = controller.getY() + CONTROLLER_Y - controllerPart(sourceLayout).y();
        return isWithinBuildHeight(level, new BlockPos(controller.getX(), targetY, controller.getZ()))
                && isWithinLayoutHeight(level, controller, sourceLayout);
    }

    public static Part controllerPart(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9
                ? new Part(HISTORICAL_CONTROLLER_X, HISTORICAL_CONTROLLER_Y, HISTORICAL_CONTROLLER_Z, PartType.CASING)
                : new Part(CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CASING);
    }

    public static BlockPos relocatedControllerPos(BlockPos controller, Direction facing, StructureLayout sourceLayout) {
        return worldPos(controller, facing, controllerPart(StructureLayout.CURRENT), sourceLayout);
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
        return worldPos(controller, facing, part, StructureLayout.CURRENT);
    }

    public static BlockPos worldPos(BlockPos controller, Direction facing, Part part, StructureLayout layout) {
        var anchor = controllerPart(layout);
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return controller.relative(right, part.x() - anchor.x())
                .relative(Direction.UP, part.y() - anchor.y())
                .relative(back, part.z() - anchor.z());
    }

    public static Vec3 worldPoint(BlockPos controller, Direction facing,
            double localX, double localY, double localZ) {
        return worldPoint(controller, facing, localX, localY, localZ, StructureLayout.CURRENT);
    }

    public static Vec3 worldPoint(BlockPos controller, Direction facing,
            double localX, double localY, double localZ, StructureLayout layout) {
        var anchor = controllerPart(layout);
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return Vec3.atCenterOf(controller).add(
                right.getStepX() * (localX - anchor.x()) + back.getStepX() * (localZ - anchor.z()),
                localY - anchor.y(),
                right.getStepZ() * (localX - anchor.x()) + back.getStepZ() * (localZ - anchor.z()));
    }

    public static boolean isWithinBuildHeight(Level level, BlockPos controller) {
        return isWithinBuildHeight(level, controller, StructureLayout.CURRENT);
    }

    public static boolean isWithinBuildHeight(Level level, BlockPos controller, StructureLayout layout) {
        return LAYOUT_BOUNDS.containsKey(layout)
                && controller.getY() >= minimumControllerY(level.getMinBuildHeight(), layout)
                && controller.getY() <= maximumControllerY(level.getMaxBuildHeight(), layout);
    }

    public static int minimumControllerY(int minimumBuildHeight) {
        return minimumControllerY(minimumBuildHeight, StructureLayout.CURRENT);
    }

    public static int minimumControllerY(int minimumBuildHeight, StructureLayout layout) {
        return minimumBuildHeight + controllerPart(layout).y() - LAYOUT_BOUNDS.get(layout).minY();
    }

    public static int maximumControllerY(int maximumBuildHeightExclusive) {
        return maximumControllerY(maximumBuildHeightExclusive, StructureLayout.CURRENT);
    }

    public static int maximumControllerY(int maximumBuildHeightExclusive, StructureLayout layout) {
        return maximumBuildHeightExclusive - 1 - LAYOUT_BOUNDS.get(layout).maxY() + controllerPart(layout).y();
    }

    public static Set<ChunkPos> chunkFootprint(
            BlockPos controller, Direction facing, StructureLayout layout, StructureLayout anchor) {
        var bounds = LAYOUT_BOUNDS.get(layout);
        if (bounds == null) return java.util.Set.of();
        return MultiblockChunkLoading.rectangle(
                worldPos(controller, facing, new Part(bounds.minX(), 0, bounds.minZ(), PartType.CASING), anchor),
                worldPos(controller, facing, new Part(bounds.maxX(), 0, bounds.maxZ(), PartType.CASING), anchor));
    }

    public static boolean areRequiredChunksLoaded(Level level, BlockPos controller, Direction facing) {
        return areRequiredChunksLoaded(level, controller, facing, StructureLayout.CURRENT);
    }

    public static boolean areRequiredChunksLoaded(Level level, BlockPos controller, Direction facing,
            StructureLayout layout) {
        var bounds = LAYOUT_BOUNDS.get(layout);
        if (bounds == null) {
            return false;
        }
        var first = worldPos(controller, facing, new Part(bounds.minX(), 0, bounds.minZ(), PartType.CASING), layout);
        var second = worldPos(controller, facing, new Part(bounds.maxX(), 0, bounds.minZ(), PartType.CASING), layout);
        var third = worldPos(controller, facing, new Part(bounds.minX(), 0, bounds.maxZ(), PartType.CASING), layout);
        var fourth = worldPos(controller, facing, new Part(bounds.maxX(), 0, bounds.maxZ(), PartType.CASING), layout);
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

    public static boolean areUpdateChunksLoaded(Level level, BlockPos controller, Direction facing,
            StructureLayout sourceLayout) {
        return areRequiredChunksLoaded(level, relocatedControllerPos(controller, facing, sourceLayout), facing)
                && areRequiredChunksLoaded(level, controller, facing, sourceLayout);
    }

    public static boolean matches(Level level, BlockPos controller, Direction facing) {
        return detectLayout(level, controller, facing).isFormed();
    }

    public static StructureLayout detectLayout(Level level, BlockPos controller, Direction facing) {
        for (var layout : List.of(StructureLayout.CURRENT, StructureLayout.LEGACY_1_3_9)) {
            if (areRequiredChunksLoaded(level, controller, facing, layout)
                    && isWithinLayoutHeight(level, controller, layout)
                    && matchesParts(level, controller, facing, partsFor(layout), layout)) return layout;
        }
        return StructureLayout.INCOMPLETE;
    }

    private static boolean isWithinLayoutHeight(Level level, BlockPos controller,
            StructureLayout layout) {
        return isWithinBuildHeight(level, controller, layout);
    }

    private static List<Part> partsFor(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9 ? LEGACY_PARTS : PARTS;
    }

    private static boolean matchesParts(Level level, BlockPos controller, Direction facing, List<Part> parts,
            StructureLayout layout) {
        for (var part : parts) {
            if (isController(part, layout)) {
                continue;
            }
            // AIR entries are construction and migration hints, not physical
            // multiblock parts. Occupying the visual center must not unform an
            // otherwise complete structure.
            if (part.partType() == PartType.AIR) {
                continue;
            }
            var state = level.getBlockState(worldPos(controller, facing, part, layout));
            if (!state.is(partBlock(part.partType()))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isController(Part part) {
        return isController(part, StructureLayout.CURRENT);
    }

    public static boolean isController(Part part, StructureLayout layout) {
        var anchor = controllerPart(layout);
        return part.x() == anchor.x() && part.y() == anchor.y() && part.z() == anchor.z();
    }

    /** Verify a finished blueprint before moving the controller, while both socket blocks are still reserved. */
    public static boolean matchesUpdateTarget(Level level, BlockPos controller, Direction facing,
            StructureLayout sourceLayout) {
        for (var part : PARTS) {
            if (part.partType() == PartType.AIR || isController(part) || isController(part, sourceLayout)) {
                continue;
            }
            if (!level.getBlockState(worldPos(controller, facing, part, sourceLayout)).is(partBlock(part.partType()))) {
                return false;
            }
        }
        return true;
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
        return LoweredFeatherGeometry.createParts();
    }

    private static Map<LocalPos, Part> createLookup() {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var part : PARTS) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return Map.copyOf(result);
    }

    private static Map<StructureLayout, LayoutBounds> createLayoutBounds() {
        var result = new java.util.EnumMap<StructureLayout, LayoutBounds>(StructureLayout.class);
        for (var layout : StructureLayout.values()) {
            var parts = partsFor(layout);
            if (parts.isEmpty()) {
                continue;
            }
            result.put(layout, new LayoutBounds(
                    parts.stream().mapToInt(Part::x).min().orElseThrow(),
                    parts.stream().mapToInt(Part::x).max().orElseThrow(),
                    parts.stream().mapToInt(Part::y).min().orElseThrow(),
                    parts.stream().mapToInt(Part::y).max().orElseThrow(),
                    parts.stream().mapToInt(Part::z).min().orElseThrow(),
                    parts.stream().mapToInt(Part::z).max().orElseThrow()));
        }
        return Map.copyOf(result);
    }

    private static Map<StructureLayout, Map<LocalPos, Part>> createLayoutLookups() {
        var result = new java.util.EnumMap<StructureLayout, Map<LocalPos, Part>>(StructureLayout.class);
        for (var layout : StructureLayout.values()) {
            if (layout == StructureLayout.INCOMPLETE) {
                continue;
            }
            var lookup = new LinkedHashMap<LocalPos, Part>();
            for (var part : partsFor(layout)) {
                lookup.put(new LocalPos(part.x(), part.y(), part.z()), part);
            }
            result.put(layout, Map.copyOf(lookup));
        }
        return Map.copyOf(result);
    }

    private static Map<Part, List<Part>> createHistoricalOccupancyParts() {
        return Map.of(controllerPart(StructureLayout.LEGACY_1_3_9), LEGACY_PARTS.stream()
                .filter(part -> part.partType() != PartType.AIR && !isController(part, StructureLayout.LEGACY_1_3_9)).toList());
    }

    private static List<Part> createWorkParts() {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var old : LEGACY_PARTS) {
            if (old.partType() != PartType.AIR) result.put(new LocalPos(old.x(), old.y(), old.z()),
                    new Part(old.x(), old.y(), old.z(), PartType.AIR));
        }
        for (var part : PARTS) result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        return List.copyOf(result.values());
    }

    public enum StructureLayout {
        CURRENT, LEGACY_1_3_9, INCOMPLETE;

        public boolean isFormed() { return this != INCOMPLETE; }
        public boolean requiresUpdate() { return this == LEGACY_1_3_9; }

        public static StructureLayout fromSavedName(String name) {
            if (name.equals("PALACE")) return LEGACY_1_3_9;
            try { return valueOf(name); } catch (IllegalArgumentException error) { return INCOMPLETE; }
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

    private record LayoutBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

}
