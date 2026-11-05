package com.atir.molecularmanipulator.building;

import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.registry.ModContent;
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
    OMNI_COMPUTATION("omni_computation"), STAR_COMPUTATION_ENGINE("star_computation_engine");

    public static final int TEMPLATE_VERSION = 1;
    public final String id;
    private List<BuildingArchiveStore.Entry> template;
    BuildingType(String id) { this.id = id; }
    public String translationKey() { return "building.molecularmanipulator." + id; }
    public static BuildingType byId(String id) {
        return Arrays.stream(values()).filter(type -> type.id.equals(id)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Unknown folded building: " + id));
    }
    public static BuildingType forController(net.minecraft.world.level.block.entity.BlockEntity entity) {
        if (entity instanceof MatterFabricationBlockEntity) return FABRICATION_WELL;
        if (entity instanceof MolecularCenterBlockEntity) return SEQUENCE_ARRAY;
        if (entity instanceof OmniComputationCoreBlockEntity core && !core.isSingleBlock()) return OMNI_COMPUTATION;
        if (entity instanceof StarComputationEngineCoreBlockEntity) return STAR_COMPUTATION_ENGINE;
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
            case STAR_COMPUTATION_ENGINE -> {
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                    var block = x == 0 && y == 0 && z == 0 ? ModContent.STAR_COMPUTATION_ENGINE_CORE.get()
                            : y == 0 && Math.abs(x) + Math.abs(z) == 1 ? ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get()
                            : ModContent.STAR_COMPUTATION_ENGINE_CASING.get();
                    put(result, new BlockPos(x, y, z), block.defaultBlockState());
                }
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
