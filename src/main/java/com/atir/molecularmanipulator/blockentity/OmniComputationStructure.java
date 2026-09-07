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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmniComputationStructure {
    public static final int WIDTH = OmniCrownGeometry.RADIUS * 2 + 1;
    public static final int MIN_Y = OmniCrownGeometry.MIN_Y;
    public static final int MAX_Y = OmniCrownGeometry.MAX_Y;
    public static final int HEIGHT = MAX_Y - MIN_Y + 1;
    public static final int MIN_X = -OmniCrownGeometry.RADIUS;
    public static final int MAX_X = OmniCrownGeometry.RADIUS;
    public static final int MIN_Z = MIN_X;
    public static final int MAX_Z = MAX_X;

    public static final int CONTROLLER_X = 0;
    public static final int CONTROLLER_Y = 2;
    public static final int CONTROLLER_Z = -15;
    public static final int EFFECT_X = 0;
    public static final int EFFECT_Y = 17;
    public static final int EFFECT_Z = 0;
    public static final int CURRENT_CONTROLLER_X = EFFECT_X;
    public static final int CURRENT_CONTROLLER_Y = EFFECT_Y;
    public static final int CURRENT_CONTROLLER_Z = -10;

    public static final int VISUAL_CENTER_X = 0;
    public static final int VISUAL_CENTER_Y = EFFECT_Y;
    public static final int VISUAL_CENTER_Z = 0;
    public static final int RELOCATED_ENTANGLER_X = 0;
    public static final int RELOCATED_ENTANGLER_Y = 9;
    public static final int RELOCATED_ENTANGLER_Z = -12;

    private static final List<Part> PARTS = OmniCrownGeometry.createParts();
    private static final List<Part> LEGACY_PARTS = Legacy139Blueprints.load("omni_computation",
            (x, y, z, type) -> new Part(x, y, z, PartType.valueOf(type)));
    private static final List<Part> LEGACY_MIGRATION_PARTS = createMigrationParts(LEGACY_PARTS);
    private static final Map<LocalPos, Part> PART_LOOKUP = createLookup(PARTS);
    private static final Map<StructureLayout, LayoutBounds> LAYOUT_BOUNDS = createLayoutBounds();

    private OmniComputationStructure() {
    }

    public static List<Part> parts() {
        return PARTS;
    }

    public static List<Part> parts(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9 ? LEGACY_PARTS : PARTS;
    }

    public static List<Part> dismantleParts() {
        return dismantleParts(StructureLayout.CURRENT);
    }

    public static List<Part> dismantleParts(StructureLayout layout) {
        if (layout == StructureLayout.INCOMPLETE) {
            return List.of();
        }
        return parts(layout).stream()
                .filter(part -> part.type() != PartType.AIR && part.type() != PartType.CONTROLLER)
                .toList();
    }

    public static List<Part> buildParts(StructureLayout layout) {
        return layout.requiresUpdate() ? migrationParts(layout) : PARTS;
    }

    public static List<Part> migrationParts(StructureLayout fromLayout) {
        return fromLayout.requiresUpdate() ? LEGACY_MIGRATION_PARTS : List.of();
    }

    public static int visualCenterY(StructureLayout layout) { return EFFECT_Y; }

    public static Part partAt(int x, int y, int z) {
        return PART_LOOKUP.get(new LocalPos(x, y, z));
    }

    public static BlockPos worldPos(BlockPos controller, Direction facing, Part part) {
        return worldPos(controller, facing, part, StructureLayout.CURRENT);
    }

    public static BlockPos worldPos(BlockPos controller, Direction facing,
            Part part, StructureLayout layout) {
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return controller.relative(right, part.x() - controllerX(layout))
                .relative(Direction.UP, part.y() - controllerY(layout))
                .relative(back, part.z() - controllerZ(layout));
    }

    public static Vec3 worldPoint(BlockPos controller, Direction facing,
            double localX, double localY, double localZ) {
        return worldPoint(controller, facing, localX, localY, localZ,
                StructureLayout.CURRENT);
    }

    public static Vec3 worldPoint(BlockPos controller, Direction facing,
            double localX, double localY, double localZ, StructureLayout layout) {
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return Vec3.atCenterOf(controller).add(
                right.getStepX() * (localX - controllerX(layout))
                        + back.getStepX() * (localZ - controllerZ(layout)),
                localY - controllerY(layout),
                right.getStepZ() * (localX - controllerX(layout))
                        + back.getStepZ() * (localZ - controllerZ(layout)));
    }

    public static boolean isWithinBuildHeight(Level level, BlockPos controller) {
        return isWithinBuildHeight(level, controller, StructureLayout.CURRENT);
    }

    public static boolean isWithinBuildHeight(Level level, BlockPos controller,
            StructureLayout layout) {
        var bounds = LAYOUT_BOUNDS.get(layout);
        int worldMinY = controller.getY() + bounds.minY() - controllerY(layout);
        int worldMaxY = controller.getY() + bounds.maxY() - controllerY(layout);
        return worldMinY >= level.getMinBuildHeight()
                && worldMaxY < level.getMaxBuildHeight();
    }

    public static Set<ChunkPos> chunkFootprint(
            BlockPos controller, Direction facing, StructureLayout layout) {
        return chunkFootprint(controller, facing, layout, layout);
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
        return areRequiredChunksLoaded(level, controller, facing,
                StructureLayout.CURRENT);
    }

    public static boolean areRequiredChunksLoaded(Level level, BlockPos controller,
            Direction facing, StructureLayout layout) {
        var bounds = LAYOUT_BOUNDS.get(layout);
        var corners = List.of(
                worldPos(controller, facing,
                        new Part(bounds.minX(), 0, bounds.minZ(), PartType.CASING), layout),
                worldPos(controller, facing,
                        new Part(bounds.maxX(), 0, bounds.minZ(), PartType.CASING), layout),
                worldPos(controller, facing,
                        new Part(bounds.minX(), 0, bounds.maxZ(), PartType.CASING), layout),
                worldPos(controller, facing,
                        new Part(bounds.maxX(), 0, bounds.maxZ(), PartType.CASING), layout));
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
        var current = inspectLayout(level, controller, facing, PARTS, StructureLayout.CURRENT);
        if (current.formed()) return current;
        var legacy = inspectLayout(level, controller, facing, LEGACY_PARTS, StructureLayout.LEGACY_1_3_9);
        if (legacy.formed()) return legacy;
        return new Inspection(current.total(), current.correct(), current.missing(), current.conflicts(), false, StructureLayout.INCOMPLETE);
    }

    private static Inspection inspectLayout(Level level, BlockPos controller, Direction facing,
            List<Part> parts, StructureLayout layout) {
        if (!isWithinBuildHeight(level, controller, layout)
                || !areRequiredChunksLoaded(level, controller, facing, layout)) {
            return new Inspection(parts.size(), 0, parts.size(), 0, false, layout);
        }
        int correct = 0;
        int missing = 0;
        int conflicts = 0;
        for (var part : parts) {
            var state = level.getBlockState(worldPos(controller, facing, part, layout));
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
            case AE_FLUIX -> AEBlocks.FLUIX_BLOCK.block();
            case CYAN_STAINED_GLASS -> Blocks.CYAN_STAINED_GLASS;
            case BLUE_STAINED_GLASS -> Blocks.BLUE_STAINED_GLASS;
            case AIR -> Blocks.AIR;
        };
    }

    public static boolean isStructurePart(net.minecraft.world.level.block.state.BlockState state) {
        for (var type : PartType.values()) {
            if (type != PartType.AIR && state.is(block(type))) {
                return true;
            }
        }
        return false;
    }

    public static int countDismantlableBlocks(Level level, BlockPos controller,
            Direction facing, StructureLayout layout) {
        return dismantleEntries(level, controller, facing, layout).size();
    }

    public static List<DismantlePlan.Entry> dismantleEntries(Level level, BlockPos controller,
            Direction facing, StructureLayout layout) {
        var entries = new ArrayList<DismantlePlan.Entry>();
        for (var part : dismantleParts(layout)) {
            var pos = worldPos(controller, facing, part, layout);
            if (pos.equals(controller) || !level.hasChunkAt(pos)) {
                continue;
            }
            var expected = block(part.type());
            if (level.getBlockState(pos).is(expected)) {
                entries.add(new DismantlePlan.Entry(pos, expected));
            }
        }
        return List.copyOf(entries);
    }

    private static List<Part> createMigrationParts(List<Part> sourceParts) {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var part : sourceParts) {
            if (part.type() != PartType.AIR && part.type() != PartType.CONTROLLER) {
                result.put(new LocalPos(part.x(), part.y(), part.z()),
                        new Part(part.x(), part.y(), part.z(), PartType.AIR));
            }
        }
        for (var part : PARTS) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return List.copyOf(result.values());
    }

    private static Map<StructureLayout, LayoutBounds> createLayoutBounds() {
        var result = new java.util.EnumMap<StructureLayout, LayoutBounds>(StructureLayout.class);
        for (var layout : StructureLayout.values()) {
            var blueprint = parts(layout);
            // Explicit AIR belongs to its own blueprint; old cleanup envelopes
            // must never expand the new crown's height or chunk requirements.
            result.put(layout, new LayoutBounds(
                    blueprint.stream().mapToInt(Part::x).min().orElseThrow(),
                    blueprint.stream().mapToInt(Part::y).min().orElseThrow(),
                    blueprint.stream().mapToInt(Part::z).min().orElseThrow(),
                    blueprint.stream().mapToInt(Part::x).max().orElseThrow(),
                    blueprint.stream().mapToInt(Part::y).max().orElseThrow(),
                    blueprint.stream().mapToInt(Part::z).max().orElseThrow()));
        }
        return Map.copyOf(result);
    }

    private static Map<LocalPos, Part> createLookup(List<Part> parts) {
        var result = new LinkedHashMap<LocalPos, Part>();
        for (var part : parts) {
            result.put(new LocalPos(part.x(), part.y(), part.z()), part);
        }
        return Map.copyOf(result);
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
        AE_FLUIX,
        CYAN_STAINED_GLASS,
        BLUE_STAINED_GLASS,
        AIR
    }

    public enum StructureLayout {
        CURRENT, LEGACY_1_3_9, INCOMPLETE;

        public boolean isFormed() { return this != INCOMPLETE; }
        public boolean requiresUpdate() { return this == LEGACY_1_3_9; }

        public static StructureLayout fromSavedName(String name) {
            if (name.equals("PREVIOUS_RADIAL")) return LEGACY_1_3_9;
            try { return valueOf(name); } catch (IllegalArgumentException error) { return INCOMPLETE; }
        }
    }

    private static int controllerX(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9 ? CONTROLLER_X : CURRENT_CONTROLLER_X;
    }

    private static int controllerY(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9 ? CONTROLLER_Y : CURRENT_CONTROLLER_Y;
    }

    private static int controllerZ(StructureLayout layout) {
        return layout == StructureLayout.LEGACY_1_3_9 ? CONTROLLER_Z : CURRENT_CONTROLLER_Z;
    }

    public record Part(int x, int y, int z, PartType type) {
    }

    public record Inspection(int total, int correct, int missing, int conflicts, boolean formed,
            StructureLayout layout) {
    }

    private record LocalPos(int x, int y, int z) {
    }

    private record LayoutBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

}
