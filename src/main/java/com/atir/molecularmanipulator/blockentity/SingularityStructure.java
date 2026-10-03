package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

import java.util.*;

/** Versioned, sparse blueprint shared by inspection, construction, JEI and projection. */
public final class SingularityStructure {
    public static final int VERSION = 4, EMBEDDED_VERSION = 3, LEGACY_VERSION = 2;
    public static final int WIDTH = 97, HEIGHT = 129;
    public static final BlockPos CONTROLLER = new BlockPos(0, 65, -7);
    public static final BlockPos LEGACY_CONTROLLER = new BlockPos(0, 39, -34);
    public enum Type { CONTROLLER, CASING, GILDED, PILLAR, RING, GLASS, CONDUIT,
        COLLECTION_NODE, CORE, STABILIZER, SPIRE, STAIRS, SLAB }
    static final int MATERIAL_VERSION = 2;
    static int[] readMaterialCounts(net.minecraft.nbt.CompoundTag tag, String key) {
        var stored = tag.getIntArray(key);
        var result = new int[Type.values().length];
        boolean legacy = tag.getInt("singularityMaterialVersion") < MATERIAL_VERSION;
        for (int oldIndex = 0; oldIndex < stored.length; oldIndex++) {
            // The removed port occupied index 11; stairs and slabs followed it.
            if (legacy && oldIndex == 11) continue;
            int index = legacy && oldIndex > 11 ? oldIndex - 1 : oldIndex;
            if (index < result.length) result[index] = Math.clamp(stored[oldIndex], 0, 1_000_000);
        }
        return result;
    }
    static void writeMaterialCounts(net.minecraft.nbt.CompoundTag tag, String key, int[] counts) {
        tag.putInt("singularityMaterialVersion", MATERIAL_VERSION);
        tag.putIntArray(key, counts);
    }
    public record Part(int x, int y, int z, Type type, Direction direction, boolean upper) {
        public BlockPos pos() { return new BlockPos(x, y, z); }
    }
    private static final List<Part> PARTS = create(false, true), EMBEDDED_PARTS = create(false, false), LEGACY_PARTS = create(true, false);
    private static final Map<BlockPos, Part> INDEX = index(PARTS), EMBEDDED_INDEX = index(EMBEDDED_PARTS), LEGACY_INDEX = index(LEGACY_PARTS);
    private static final Map<Type, Integer> COUNTS = count(PARTS), EMBEDDED_COUNTS = count(EMBEDDED_PARTS), LEGACY_COUNTS = count(LEGACY_PARTS);
    private static final List<BlockPos> CLEARANCE = clearance();
    private SingularityStructure() {}
    public static List<Part> parts() { return PARTS; }
    public static List<Part> parts(int version) { return version == LEGACY_VERSION ? LEGACY_PARTS : version == EMBEDDED_VERSION ? EMBEDDED_PARTS : PARTS; }
    public static BlockPos anchor(int version) { return version == LEGACY_VERSION ? LEGACY_CONTROLLER : CONTROLLER; }
    private static Map<BlockPos, Part> index(List<Part> parts) {
        return parts.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Part::pos, p -> p));
    }
    public static Part partAt(BlockPos controller, Direction facing, BlockPos world) {
        return partAt(controller, facing, world, VERSION);
    }
    public static Part partAt(BlockPos controller, Direction facing, BlockPos world, int version) {
        Rotation inverse = switch (facing) { case EAST -> Rotation.COUNTERCLOCKWISE_90; case WEST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180; default -> Rotation.NONE; };
        return (version == LEGACY_VERSION ? LEGACY_INDEX : version == EMBEDDED_VERSION ? EMBEDDED_INDEX : INDEX).get(world.subtract(controller).rotate(inverse).offset(anchor(version)));
    }
    public static Map<Type, Integer> counts() { return COUNTS; }
    public static Map<Type, Integer> counts(int version) { return version == LEGACY_VERSION ? LEGACY_COUNTS : version == EMBEDDED_VERSION ? EMBEDDED_COUNTS : COUNTS; }
    public static int[] crystalChambers(int tower) {
        int top = tower % 2 == 0 ? 105 : 92;
        return new int[]{35, 54, 54 + (top - 62) / 2, top - 7};
    }
    /**
     * The capture node sits on the inward end of each tower landing, aligned
     * with the spoke that leads back to the central core.  The tower centres
     * are on radius 41 while the spoke/landing joint is on radius 38.
     */
    static BlockPos collectionNodePos(int tower) {
        // The capture node sits directly above the striped centre conduit of
        // every spoke, including the four diagonal bridges.
        return collectionNodeCenterPos(tower);
    }
    static BlockPos collectionNodeCenterPos(int tower) {
        double angle = Math.PI * tower / 4;
        return new BlockPos((int) Math.round(Math.sin(angle) * 38), 65,
                (int) Math.round(Math.cos(angle) * 38));
    }
    static BlockPos collectionNodePos(int tower, int version) {
        return version >= VERSION ? collectionNodePos(tower) : legacyCollectionNodePos(tower);
    }
    private static BlockPos legacyCollectionNodePos(int tower) {
        double angle = Math.PI * tower / 4;
        return new BlockPos((int) Math.round(Math.sin(angle) * 41), 65,
                (int) Math.round(Math.cos(angle) * 41) - 3);
    }
    /** Positions occupied by nodes in layouts shipped before the side-lane adjustment. */
    static List<BlockPos> compatibilityCollectionNodePositions(int version) {
        if (version < VERSION) return List.of();
        var positions = new LinkedHashSet<BlockPos>();
        for (int tower = 0; tower < 8; tower++) {
            positions.add(collectionNodeCenterPos(tower));
            positions.add(legacyCollectionNodePos(tower));
        }
        for (int tower = 0; tower < 8; tower++) positions.remove(collectionNodePos(tower));
        return List.copyOf(positions);
    }
    static boolean isCompatibilityCollectionNode(BlockPos local, int version) {
        return compatibilityCollectionNodePositions(version).contains(local);
    }
    public static boolean isCompatibilityCollectionNode(BlockPos controller, Direction facing, BlockPos world, int version) {
        Rotation inverse = switch (facing) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
        var local = world.subtract(controller).rotate(inverse).offset(anchor(version));
        return isCompatibilityCollectionNode(local, version);
    }
    /** Authored tower blocks only; the landing and collection node are stationary access joints. */
    static List<Part> towerBodyParts(int tower, int version) {
        var b = new Blueprint();
        int x = SingularityMotionGeometry.towerX(tower), z = SingularityMotionGeometry.towerZ(tower);
        int top = tower % 2 == 0 ? 105 : 92;
        if (version >= VERSION) b.suspendedTower(x, z, tower, top);
        else {
            b.tower(x, z, 47, top);
            for (int y = 25; y < 47; y++) b.put(x, y, z, y % 5 == 0 ? Type.GILDED : Type.PILLAR);
            b.put(x, 24, z, Type.SPIRE, Direction.DOWN, false);
        }
        b.parts.entrySet().removeIf(e -> e.getKey().getY() == 64
                && Math.abs(e.getKey().getX() - x) <= 2 && Math.abs(e.getKey().getZ() - z) <= 2);
        b.parts.remove(collectionNodePos(tower, version));
        if (version >= VERSION) compatibilityCollectionNodePositions(version).forEach(b.parts::remove);
        return List.copyOf(b.parts.values());
    }
    public static List<BlockPos> requiredAir() { return CLEARANCE; }
    public static boolean isController(Part part) { return part.type == Type.CONTROLLER; }
    public static Block block(Type type) { return SingularityContent.PALETTE.get(type.ordinal()).get(); }
    public static List<ItemStack> materials() {
        return Arrays.stream(Type.values()).filter(t -> t != Type.CONTROLLER && COUNTS.getOrDefault(t, 0) > 0)
                .map(t -> new ItemStack(block(t), COUNTS.getOrDefault(t, 0))).toList();
    }
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
    public static BlockPos worldPos(BlockPos controller, Direction facing, Part part) {
        return worldPos(controller, facing, part.pos());
    }
    public static BlockPos worldPos(BlockPos controller, Direction facing, BlockPos local) {
        return worldPos(controller, facing, local, VERSION);
    }
    public static BlockPos worldPos(BlockPos controller, Direction facing, Part part, int version) {
        return worldPos(controller, facing, part.pos(), version);
    }
    public static BlockPos worldPos(BlockPos controller, Direction facing, BlockPos local, int version) {
        return local.subtract(anchor(version)).rotate(rotation(facing)).offset(controller);
    }
    public static Set<ChunkPos> chunks(BlockPos controller, Direction facing) {
        return chunks(controller, facing, VERSION);
    }
    public static Set<ChunkPos> chunks(BlockPos controller, Direction facing, int version) {
        return MultiblockChunkLoading.rectangle(
                worldPos(controller, facing, new BlockPos(-48, 0, -48), version),
                worldPos(controller, facing, new BlockPos(48, 128, 48), version));
    }
    public static boolean fits(Level level, BlockPos controller, Direction facing) {
        return fits(level, controller, facing, VERSION);
    }
    public static boolean fits(Level level, BlockPos controller, Direction facing, int version) {
        for (int x : new int[]{-48, 48}) for (int z : new int[]{-48, 48}) {
            var low = worldPos(controller, facing, new BlockPos(x, 0, z), version);
            var high = low.above(128);
            if (level.isOutsideBuildHeight(low) || level.isOutsideBuildHeight(high)
                    || !level.getWorldBorder().isWithinBounds(low)) return false;
        }
        return true;
    }
    public static BlockState state(Part part, Direction facing) {
        var state = block(part.type).defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING))
            state = state.setValue(HorizontalDirectionalBlock.FACING, part.direction);
        if (state.hasProperty(RotatedPillarBlock.AXIS))
            state = state.setValue(RotatedPillarBlock.AXIS, part.direction.getAxis());
        if (state.hasProperty(DirectionalBlock.FACING))
            state = state.setValue(DirectionalBlock.FACING, part.direction);
        if (state.hasProperty(StairBlock.HALF))
            state = state.setValue(StairBlock.HALF, part.upper ? Half.TOP : Half.BOTTOM);
        if (state.hasProperty(SlabBlock.TYPE))
            state = state.setValue(SlabBlock.TYPE, part.upper ? SlabType.TOP : SlabType.BOTTOM);
        return state.rotate(rotation(facing));
    }
    public static boolean matches(BlockState actual, Part part, Direction facing) {
        var expected = state(part, facing);
        if (!actual.is(expected.getBlock())) return false;
        // Stair corners are derived from neighbours. All authored placement properties still matter.
        for (var property : expected.getProperties()) {
            if (property != StairBlock.SHAPE && !actual.getValue(property).equals(expected.getValue(property))) return false;
        }
        return true;
    }
    public record Inspection(int correct, int missing, int conflicts, int unloaded,
                             Map<Type, Integer> needed, BlockPos problem) {
        public boolean formed() { return missing == 0 && conflicts == 0 && unloaded == 0; }
    }
    public static Inspection inspect(Level level, BlockPos controller, Direction facing) {
        return inspect(level, controller, facing, part -> false);
    }
    public static Inspection inspect(Level level, BlockPos controller, Direction facing, java.util.function.Predicate<Part> assembled) {
        return inspect(level, controller, facing, assembled, VERSION);
    }
    public static Inspection inspect(Level level, BlockPos controller, Direction facing, java.util.function.Predicate<Part> assembled, int version) {
        int correct = 0, missing = 0, conflicts = 0, unloaded = 0;
        var needed = new EnumMap<Type, Integer>(Type.class);
        BlockPos problem = null;
        for (var part : parts(version)) {
            if (assembled.test(part)) { correct++; continue; }
            var pos = worldPos(controller, facing, part, version);
            if (!level.hasChunkAt(pos)) { unloaded++; continue; }
            var actual = level.getBlockState(pos);
            if (matches(actual, part, facing)) { correct++; continue; }
            if (actual.canBeReplaced()) missing++; else conflicts++;
            if (!actual.is(block(part.type))) needed.merge(part.type, 1, Integer::sum);
            if (problem == null) problem = pos;
        }
        for (var local : CLEARANCE) {
            var pos = worldPos(controller, facing, local, version);
            if (!level.hasChunkAt(pos)) { unloaded++; continue; }
            if (!level.getBlockState(pos).isAir()) { conflicts++; if (problem == null) problem = pos; }
        }
        return new Inspection(correct, missing, conflicts, unloaded, Map.copyOf(needed), problem);
    }
    private static Map<Type, Integer> count(List<Part> parts) {
        var counts = new EnumMap<Type, Integer>(Type.class);
        parts.forEach(p -> counts.merge(p.type, 1, Integer::sum));
        return Collections.unmodifiableMap(counts);
    }
    private static List<BlockPos> clearance() {
        var air = new ArrayList<BlockPos>();
        for (int y = 57; y <= 119; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || y != 64 || z != 0) air.add(new BlockPos(x, y, z));
        return List.copyOf(air);
    }
    private static List<Part> create(boolean legacy, boolean suspended) {
        var b = new Blueprint();
        b.ring(30, 36); b.ring(48, 64); b.ring(32, 96);
        // Eight spokes and tower clusters give the rings a readable silhouette at a distance.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            int tx = (int) Math.round(Math.sin(angle) * 41), tz = (int) Math.round(Math.cos(angle) * 41);
            for (int r = 5; r <= 38; r++) {
                int x = (int) Math.round(Math.sin(angle) * r), z = (int) Math.round(Math.cos(angle) * r);
                for (int w = -1; w <= 1; w++) {
                    int dx = Math.abs(tx) > Math.abs(tz) ? 0 : w;
                    int dz = Math.abs(tx) > Math.abs(tz) ? w : 0;
                    b.put(x + dx, 63, z + dz, w == 0 ? Type.GILDED : Type.CASING);
                    b.put(x + dx, 64, z + dz, w == 0 ? Type.CONDUIT : Type.SLAB,
                            Math.abs(tx) > Math.abs(tz) ? Direction.EAST : Direction.SOUTH, false);
                }
            }
            int tip = (i % 2 == 0) ? 105 : 92;
            if (suspended) b.suspendedTower(tx, tz, i, tip);
            else {
                b.tower(tx, tz, 47, tip);
                for (int y = 25; y < 47; y++) b.put(tx, y, tz, y % 5 == 0 ? Type.GILDED : Type.PILLAR);
                b.put(tx, 24, tz, Type.SPIRE, Direction.DOWN, false);
            }
            if (suspended) compatibilityCollectionNodePositions(VERSION).forEach(b.parts::remove);
            var node = suspended ? collectionNodePos(i) : legacyCollectionNodePos(i);
            b.put(node.getX(), node.getY(), node.getZ(), Type.COLLECTION_NODE);
        }
        if (suspended) b.suspendedAxis();
        else {
        // Preserve classic geometry for existing buildings, apart from retired ports.
        for (int x : new int[]{-3, 3}) for (int z : new int[]{-3, 3}) {
            for (int y = 14; y <= 120; y++)
                b.put(x, y, z, y % 16 == 0 ? Type.STABILIZER : y % 4 == 0 ? Type.GILDED : Type.PILLAR);
            b.put(x, 13, z, Type.SPIRE, Direction.DOWN, false);
        }
        for (int y : new int[]{16, 36, 56, 64, 80, 96, 120}) b.ring(7, y);
        }
        for (int y = 1; y <= 13; y++) {
            int r = Math.min(4, 1 + y / 4);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                if (Math.abs(x) + Math.abs(z) <= r) b.put(x, y, z, y % 4 == 0 ? Type.GILDED : Type.CASING);
        }
        b.put(0, 0, 0, Type.SPIRE, Direction.DOWN, false);
        if (!suspended) for (int y = 121; y < 128; y++) {
            int r = (128 - y) / 2;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                if (Math.abs(x) + Math.abs(z) <= r) b.put(x, y, z, y % 2 == 0 ? Type.GILDED : Type.CASING);
        }
        b.put(0, 128, 0, Type.SPIRE, Direction.UP, false);
        if (legacy) {
            // Keep old saves usable until their owner chooses the embedded layout.
            for (int x = -4; x <= 4; x++) for (int z = -36; z <= -32; z++) b.put(x, 38, z, Type.CASING);
            for (int x : new int[]{-3, -2, 2, 3}) b.parts.remove(new BlockPos(x, 39, -34));
            for (int x = -2; x <= 2; x++) {
                b.put(x, 37, -31, Type.STAIRS, Direction.NORTH, false);
                b.put(x, 38, -32, Type.STAIRS, Direction.NORTH, false);
            }
        } else {
            // Leave the retired port sockets empty.
            for (int x : new int[]{-3, -2, 2, 3}) {
                b.parts.remove(new BlockPos(x, 65, -6));
            }
        }
        var anchor = legacy ? LEGACY_CONTROLLER : CONTROLLER;
        b.put(anchor.getX(), anchor.getY(), anchor.getZ(), Type.CONTROLLER);
        for (var air : clearance()) b.parts.remove(air);
        b.put(0, 64, 0, Type.CORE);
        return b.parts.values().stream().sorted(Comparator.comparingInt(Part::y)
                .thenComparingInt(Part::z).thenComparingInt(Part::x)).toList();
    }
    private static final class Blueprint {
        final Map<BlockPos, Part> parts = new HashMap<>();
        void put(int x, int y, int z, Type type) { put(x, y, z, type, type == Type.PILLAR || type == Type.CONDUIT || type == Type.SPIRE ? Direction.UP : Direction.NORTH, false); }
        void put(int x, int y, int z, Type type, Direction direction, boolean upper) {
            parts.put(new BlockPos(x, y, z), new Part(x, y, z, type, direction, upper));
        }
        void ring(int radius, int y) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > radius || r < radius - 3) continue;
                var outward = Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST)
                        : (z > 0 ? Direction.SOUTH : Direction.NORTH);
                put(x, y - 1, z, r > radius - 1 ? Type.GILDED : Type.CASING);
                put(x, y, z, r > radius - 1 || r < radius - 2 ? Type.RING : Type.GLASS, outward, false);
                if (r > radius - 0.6) put(x, y + 1, z, Type.SLAB);
            }
        }
        void tower(int cx, int cz, int bottom, int top) {
            for (int y = bottom; y < top - 4; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                var type = y == bottom || y % 8 == 0 ? Type.GILDED
                        : Math.abs(x) == 2 && Math.abs(z) == 2 ? Type.PILLAR : Type.GLASS;
                put(cx + x, y, cz + z, type);
            }
            for (int y = top - 4; y < top; y++) {
                int r = (top - y) / 2;
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                    put(cx + x, y, cz + z, x == 0 && z == 0 ? Type.CONDUIT : Type.CASING);
            }
            put(cx, top, cz, Type.SPIRE, Direction.UP, false);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) put(cx + x, 64, cz + z, Type.GLASS);
        }
        void collar(int cx, int cz, int y, int radius, boolean split) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                double r = Math.hypot(x, z);
                if (r > radius + .35 || r < radius - 1.15 || split && (Math.abs(x) <= 1 || Math.abs(z) <= 1)) continue;
                put(cx+x, y, cz+z, Type.CASING);
                put(cx+x, y+1, cz+z, Type.SLAB);
                if ((Math.abs(x) == Math.abs(z)) || x == 0 || z == 0) put(cx+x, y, cz+z, Type.GILDED);
            }
        }
        void suspendedTower(int cx, int cz, int index, int top) {
            for (int mid : crystalChambers(index)) {
                collar(cx, cz, mid-5, 3, false);
                collar(cx, cz, mid+5, 3, false);
                for (int y=mid-4; y<mid+5; y++) for (int s : new int[]{-1,1}) {
                    put(cx+s*3,y,cz, y==mid ? Type.STABILIZER : Type.PILLAR);
                    put(cx,y,cz+s*3, y==mid ? Type.STABILIZER : Type.PILLAR);
                    for (int t : new int[]{-1,1}) {
                        put(cx+s*2,y,cz+t*2, y==mid ? Type.GILDED : Type.GLASS);
                    }
                }
                put(cx,mid-4,cz,Type.SPIRE); put(cx,mid+4,cz,Type.SPIRE,Direction.DOWN,false);
            }
            // A fixed-sized landing preserves the collection node and access joint.
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) put(cx+x,64,cz+z,Type.GLASS);
            for (int s : new int[]{-1,1}) for (int y=top-11;y<=top-2;y++) {
                int r=y<top-7 ? 3+(y-(top-11))/3 : 4;
                put(cx+s*r,y,cz,y==top-2?Type.GILDED:Type.CASING);
                put(cx,y,cz+s*r,y==top-2?Type.GILDED:Type.CASING);
            }
            put(cx,24,cz,Type.SPIRE,Direction.DOWN,false);
            put(cx,top,cz,Type.SPIRE);
        }
        void suspendedAxis() {
            ring(7,64); // The embedded terminal and its walkable dais remain fixed.
            int[] heights={18,38,55,91,111,121}, radii={5,7,9,10,7,5};
            for(int i=0;i<heights.length;i++) collar(0,0,heights[i],radii[i],true);
            for(int[] span : new int[][]{{18,38,5,7},{38,55,7,9},{91,111,10,7},{111,121,7,5}}) {
                for(int y=span[0]+1;y<span[1];y++) {
                    int r=(int)Math.round(span[2]+(span[3]-span[2])*(y-span[0])/(double)(span[1]-span[0]));
                    // Four diagonal buttresses leave the axial beam and the central crystal exposed.
                    int q=(int)Math.round(r/Math.sqrt(2));
                    for(int sx : new int[]{-1,1}) for(int sz : new int[]{-1,1}) {
                        put(sx*q,y,sz*q,Type.PILLAR);
                        put(sx*(q+1),y,sz*q,Type.CASING);
                        if(y%4==0) put(sx*q,y,sz*(q+1),Type.GILDED);
                    }
                }
            }
            for(int sx : new int[]{-1,1}) for(int sz : new int[]{-1,1}) {
                put(sx*7,55,sz*7,Type.STABILIZER);
                put(sx*7,91,sz*7,Type.STABILIZER);
                put(sx*3,122,sz*3,Type.GILDED);
                put(sx*3,123,sz*3,Type.SPIRE);
            }
        }
    }
}
