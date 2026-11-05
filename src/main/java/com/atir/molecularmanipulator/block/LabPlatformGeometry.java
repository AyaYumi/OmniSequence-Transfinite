package com.atir.molecularmanipulator.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Baked models, collision and structure placement share the approved platform export. */
public final class LabPlatformGeometry {
    public record Part(int index, BlockPos offset) {}
    private static final JsonObject DATA = read();
    public static final BlockPos INLET = position(DATA.getAsJsonArray("labInlet"));
    public static final BlockPos ANCHOR = position(DATA.getAsJsonArray("visualAnchor"));
    public static final BlockPos INPUT = position(DATA.getAsJsonObject("cradles").getAsJsonArray("input"));
    public static final BlockPos OUTPUT = position(DATA.getAsJsonObject("cradles").getAsJsonArray("output"));
    public static final Vec3[] SUPPLY_PATH = vectors(DATA.getAsJsonArray("supplyPath"));
    public static final List<Part> PARTS;
    private static final VoxelShape[][] FLOOR = new VoxelShape[49][4];
    private static final java.util.Map<String, VoxelShape[]> EQUIPMENT = new java.util.HashMap<>();

    static {
        for (var shapes : FLOOR) java.util.Arrays.fill(shapes, Shapes.empty());
        var parts = new ArrayList<Part>();
        for (var value : DATA.getAsJsonArray("parts")) {
            var part = value.getAsJsonObject();
            int index = part.get("index").getAsInt();
            parts.add(new Part(index, position(part.getAsJsonArray("offset"))));
            FLOOR[index] = shapes(part.getAsJsonArray("boxes"));
        }
        PARTS = List.copyOf(parts);
        for (var entry : DATA.getAsJsonObject("shapes").entrySet()) {
            EQUIPMENT.put(entry.getKey(), shapes(entry.getValue().getAsJsonArray()));
        }
    }

    private LabPlatformGeometry() {}

    public static VoxelShape floor(int part, Direction facing) { return FLOOR[part][turns(facing)]; }
    public static VoxelShape equipment(String kind, Direction facing) { return EQUIPMENT.get(kind)[turns(facing)]; }
    public static BlockPos partOffset(int part) { return new BlockPos(part % 7 - 3, -1, part / 7 - 2); }

    /** Canonical front is +Z (south), matching the approved lectern and cradle notches. */
    public static int turns(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; case EAST -> 3;
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    public static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        int x = offset.getX(), z = offset.getZ();
        return switch (turns(facing)) {
            case 0 -> offset;
            case 1 -> new BlockPos(-z, offset.getY(), x);
            case 2 -> new BlockPos(-x, offset.getY(), -z);
            default -> new BlockPos(z, offset.getY(), -x);
        };
    }

    private static Vec3 rotate(Vec3 point, int turns) {
        return switch (turns) {
            case 0 -> point;
            case 1 -> new Vec3(1 - point.z, point.y, point.x);
            case 2 -> new Vec3(1 - point.x, point.y, 1 - point.z);
            default -> new Vec3(point.z, point.y, 1 - point.x);
        };
    }

    private static VoxelShape[] shapes(JsonArray boxes) {
        var result = new VoxelShape[4];
        for (int turns = 0; turns < 4; turns++) {
            VoxelShape shape = Shapes.empty();
            for (var value : boxes) {
                var box = value.getAsJsonArray();
                var a = rotate(new Vec3(box.get(0).getAsDouble(), box.get(1).getAsDouble(), box.get(2).getAsDouble()), turns);
                var b = rotate(new Vec3(box.get(3).getAsDouble(), box.get(4).getAsDouble(), box.get(5).getAsDouble()), turns);
                shape = Shapes.or(shape, Shapes.box(Math.min(a.x,b.x), Math.min(a.y,b.y), Math.min(a.z,b.z),
                        Math.max(a.x,b.x), Math.max(a.y,b.y), Math.max(a.z,b.z)));
            }
            result[turns] = shape.optimize();
        }
        return result;
    }

    private static JsonObject read() {
        try (var stream = LabPlatformGeometry.class.getResourceAsStream("/assets/molecularmanipulator/lab_platform/geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing laboratory platform geometry");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not load laboratory platform geometry", error);
        }
    }

    private static BlockPos position(JsonArray value) { return new BlockPos(value.get(0).getAsInt(),value.get(1).getAsInt(),value.get(2).getAsInt()); }
    private static Vec3[] vectors(JsonArray values) {
        var result = new Vec3[values.size()];
        for (int i=0; i<result.length; i++) {
            var value = values.get(i).getAsJsonArray();
            result[i] = new Vec3(value.get(0).getAsDouble(), value.get(1).getAsDouble(), value.get(2).getAsDouble());
        }
        return result;
    }
}
