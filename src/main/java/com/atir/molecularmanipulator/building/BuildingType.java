package com.atir.molecularmanipulator.building;

import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation;
import com.atir.molecularmanipulator.starcomputation.StarOrreryCoreStructure;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Stable package identities. All geometry comes from the same blueprints as manual construction. */
public enum BuildingType {
    FABRICATION_WELL("fabrication_well"), SEQUENCE_ARRAY("sequence_array"),
    OMNI_COMPUTATION("omni_computation"), EVENT_HORIZON_SINGULARITY("event_horizon_singularity"),
    // The generic identity remains readable for historical archives.
    STAR_COMPUTATION_ENGINE("star_computation_engine"),
    STAR_ORRERY_L0("star_orrery_l0", 0), STAR_ORRERY_L1("star_orrery_l1", 1),
    STAR_ORRERY_L2("star_orrery_l2", 2), STAR_ORRERY_L3("star_orrery_l3", 3),
    STAR_ORRERY_L4("star_orrery_l4", 4), STAR_ORRERY_L5("star_orrery_l5", 5),
    STAR_ORRERY_L6("star_orrery_l6", 6), STAR_ORRERY_L7("star_orrery_l7", 7),
    STAR_ORRERY_L8("star_orrery_l8", 8), STAR_ORRERY_L9("star_orrery_l9", 9);

    public static final int TEMPLATE_VERSION = 1;
    private static final List<BuildingType> ORDERABLE = List.of(FABRICATION_WELL, SEQUENCE_ARRAY,
            OMNI_COMPUTATION, EVENT_HORIZON_SINGULARITY, STAR_ORRERY_L1, STAR_ORRERY_L2, STAR_ORRERY_L3);
    public final String id;
    private final int orreryLevel;
    private List<BuildingArchiveStore.Entry> template;
    BuildingType(String id) { this(id, 0); }
    BuildingType(String id, int orreryLevel) { this.id = id; this.orreryLevel = orreryLevel; }
    public String translationKey() { return "building.molecularmanipulator." + id; }
    public static List<BuildingType> orderable() { return ORDERABLE; }
    public int orreryLevel() { return orreryLevel; }
    public boolean isStarOrrery() { return this == STAR_COMPUTATION_ENGINE || this == STAR_ORRERY_L0 || orreryLevel > 0; }
    public static BuildingType starOrrery(int level) {
        return switch (level) {
            case 0 -> STAR_ORRERY_L0; case 1 -> STAR_ORRERY_L1; case 2 -> STAR_ORRERY_L2;
            case 3 -> STAR_ORRERY_L3; case 4 -> STAR_ORRERY_L4; case 5 -> STAR_ORRERY_L5;
            case 6 -> STAR_ORRERY_L6; case 7 -> STAR_ORRERY_L7; case 8 -> STAR_ORRERY_L8;
            case 9 -> STAR_ORRERY_L9;
            default -> throw new IllegalArgumentException("Star orrery level must be L0-L9: " + level);
        };
    }
    public static BuildingType byId(String id) {
        return Arrays.stream(values()).filter(type -> type.id.equals(id)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Unknown folded building: " + id));
    }
    public static BuildingType forController(net.minecraft.world.level.block.entity.BlockEntity entity) {
        if (entity instanceof MatterFabricationBlockEntity) return FABRICATION_WELL;
        if (entity instanceof MolecularCenterBlockEntity) return SEQUENCE_ARRAY;
        if (entity instanceof OmniComputationCoreBlockEntity core && !core.isSingleBlock()) return OMNI_COMPUTATION;
        if (entity instanceof SingularityBlockEntity singularity && !singularity.isSingleBlock()) return EVENT_HORIZON_SINGULARITY;
        if (entity instanceof StarComputationEngineCoreBlockEntity core)
            return core.isStarOrrery() ? starOrrery(core.getLitRings()) : STAR_COMPUTATION_ENGINE;
        return null;
    }
    public synchronized List<BuildingArchiveStore.Entry> template() {
        if (template != null) return template;
        var result = new LinkedHashMap<BlockPos, BuildingArchiveStore.Entry>();
        switch (this) {
            case FABRICATION_WELL -> {
                for (var part : MatterFabricationStructure.parts()) {
                    put(result, MatterFabricationStructure.worldPos(BlockPos.ZERO, Direction.NORTH, part),
                            MatterFabricationStructure.partState(part.type()));
                }
            }
            case SEQUENCE_ARRAY -> {
                for (var part : MolecularCenterStructure.parts()) {
                    var pos = MolecularCenterStructure.worldPos(BlockPos.ZERO, Direction.NORTH, part);
                    put(result, pos, pos.equals(BlockPos.ZERO) ? ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState()
                            : MolecularCenterStructure.partState(part.partType()));
                }
            }
            case OMNI_COMPUTATION -> {
                for (var part : OmniComputationStructure.parts()) {
                    put(result, OmniComputationStructure.worldPos(BlockPos.ZERO, Direction.NORTH, part),
                            OmniComputationStructure.block(part.type()).defaultBlockState());
                }
            }
            case EVENT_HORIZON_SINGULARITY -> {
                for (var part : SingularityStructure.parts()) {
                    var offset = SingularityStructure.worldPos(BlockPos.ZERO, Direction.NORTH, part);
                    // Keep authored pillar axes, spire directions, slab halves and other placement properties.
                    result.put(offset, new BuildingArchiveStore.Entry(offset, SingularityStructure.state(part, Direction.NORTH), null));
                }
                for (var local : SingularityStructure.requiredAir())
                    put(result, SingularityStructure.worldPos(BlockPos.ZERO, Direction.NORTH, local), Blocks.AIR.defaultBlockState());
            }
            case STAR_COMPUTATION_ENGINE, STAR_ORRERY_L0, STAR_ORRERY_L4, STAR_ORRERY_L5,
                    STAR_ORRERY_L6, STAR_ORRERY_L7, STAR_ORRERY_L8, STAR_ORRERY_L9 ->
                    throw new IllegalStateException("This building identity requires a sealed archive; order a star orrery L1-L3 package for construction");
            case STAR_ORRERY_L1, STAR_ORRERY_L2, STAR_ORRERY_L3 -> {
                for (var member : StarOrreryCoreStructure.members()) {
                    var block = member.material() == StarOrreryCoreStructure.Material.CASING
                            ? ModContent.STAR_COMPUTATION_ENGINE_CASING.get() : ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get();
                    put(result, member.offset(), block.defaultBlockState());
                }
                for (var member : StarOrreryFoundation.members()) {
                    var block = member.material() == StarOrreryFoundation.Material.PLINTH
                            ? ModContent.STAR_ORRERY_ENGRAVED_PLINTH.get() : ModContent.STAR_ORRERY_GILDED_PLINTH.get();
                    put(result, member.offset(), block.defaultBlockState());
                }
                for (int ring = 0; ring < orreryLevel; ring++) {
                    var anchor = switch (StarOrreryLayout.tierOf(ring)) {
                        case 0 -> ModContent.REFERENCE_STAR_POSITION_ANCHOR.get();
                        case 1 -> ModContent.QUANTUM_STAR_POSITION_ANCHOR.get();
                        default -> ModContent.TERMINAL_STAR_POSITION_ANCHOR.get();
                    };
                    for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++)
                        put(result, StarOrreryLayout.offset(Direction.NORTH, ring, slot), anchor.defaultBlockState());
                }
                put(result, BlockPos.ZERO, ModContent.STAR_COMPUTATION_ENGINE_CORE.get().defaultBlockState()
                        .setValue(StarComputationEngineCoreBlock.STAR_ORRERY, true));
            }
        }
        template = result.values().stream().sorted(Comparator
                .comparingInt((BuildingArchiveStore.Entry entry) -> entry.offset().equals(BlockPos.ZERO) ? 1 : 0)
                .thenComparingInt(entry -> entry.offset().getY())
                .thenComparingInt(entry -> entry.offset().getX()).thenComparingInt(entry -> entry.offset().getZ())).toList();
        return template;
    }
    private static void put(Map<BlockPos, BuildingArchiveStore.Entry> out, BlockPos pos, BlockState state) {
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        out.put(pos, new BuildingArchiveStore.Entry(pos, state, null));
    }
    /** Includes the controller; totals are never rounded to stacks or replaced with cheaper ingredients. */
    public Map<AEItemKey, Long> materials() {
        var result = new LinkedHashMap<AEItemKey, Long>();
        for (var entry : template()) if (!entry.state().isAir()) {
            var key = AEItemKey.of(entry.state().getBlock().asItem());
            if (key == null || entry.state().getBlock().asItem() == net.minecraft.world.item.Items.AIR)
                throw new IllegalStateException("No construction item for " + entry.state());
            result.merge(key, 1L, Math::addExact);
        }
        return Collections.unmodifiableMap(result);
    }
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case NORTH -> Rotation.NONE; case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180; case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }
    public static Direction facing(BlockState state) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }
    /** Some AE orientation strategies store directions in enum block properties without overriding Block.rotate. */
    public static BlockState rotateState(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) return state;
        BlockState rotated = state.rotate(rotation);
        if (rotated == state) {
            for (var property : state.getProperties()) if (property.getValueClass() == Direction.class) {
                @SuppressWarnings("unchecked") var direction = (net.minecraft.world.level.block.state.properties.Property<Direction>) property;
                var old = state.getValue(direction);
                var next = rotation.rotate(old);
                if (direction.getPossibleValues().contains(next)) rotated = rotated.setValue(direction, next);
            }
        }
        if (state.hasProperty(appeng.block.crafting.PatternProviderBlock.PUSH_DIRECTION)) {
            var push = state.getValue(appeng.block.crafting.PatternProviderBlock.PUSH_DIRECTION);
            if (push.getDirection() != null) rotated = rotated.setValue(appeng.block.crafting.PatternProviderBlock.PUSH_DIRECTION,
                    appeng.block.crafting.PushDirection.fromDirection(rotation.rotate(push.getDirection())));
        }
        return rotated;
    }
}
