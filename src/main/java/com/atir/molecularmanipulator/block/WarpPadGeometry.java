package com.atir.molecularmanipulator.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision, clicks and dynamic glyphs share the export of the approved model. */
public final class WarpPadGeometry {
    public record Surface(Vec3[] vertices, Vec3 normal, AABB bounds, Vec3 center, double[][] uvCorners) {}
    private static final JsonObject DATA = read();
    public static final Surface[] KEYS = surfaces(DATA.getAsJsonArray("keys"));
    public static final Surface PEARL = surface(DATA.getAsJsonObject("pearl"));
    public static final Surface ACTIVATION = surface(DATA.getAsJsonObject("activation"));
    public static final double[][] GLYPH_UV = pairs(DATA.getAsJsonArray("glyphUV"));
    public static final Vec3 STANDING_OFFSET = vector(DATA.getAsJsonObject("standing").getAsJsonArray("position"));
    private static final VoxelShape[][] SHAPES = shapes();

    private WarpPadGeometry() {}

    public static VoxelShape shape(int part, Direction facing) { return SHAPES[quarterTurns(facing)][part]; }

    public static int partIndex(int dx, int dy, int dz) { return (dy + 1) * 9 + (dz + 1) * 3 + dx + 1; }
    public static int offsetX(int part) { return part % 3 - 1; }
    public static int offsetZ(int part) { return part / 3 % 3 - 1; }
    public static int offsetY(int part) { return part / 9 - 1; }

    public static BlockPos offset(int part) { return new BlockPos(offsetX(part), offsetY(part), offsetZ(part)); }

    public static int quarterTurns(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    /** Clockwise around the root's [0.5,0,0.5] pivot, matching Java model Y rotation. */
    public static Vec3 worldPoint(Vec3 canonical, Direction facing) {
        double x = canonical.x, z = canonical.z;
        return switch (facing) {
            case NORTH -> canonical;
            case EAST -> new Vec3(1 - z, canonical.y, x);
            case SOUTH -> new Vec3(1 - x, canonical.y, 1 - z);
            case WEST -> new Vec3(z, canonical.y, 1 - x);
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    public static Vec3 canonicalPoint(Vec3 worldRelative, Direction facing) {
        return worldPoint(worldRelative, switch (facing) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> facing;
        });
    }

    /** The collision stair-steps may be up to one voxel above the exact tilted key face. */
    public static int keyAt(Vec3 point) {
        for (int i = 0; i < KEYS.length; i++) {
            var key = KEYS[i];
            if (key.bounds.inflate(1D / 16).contains(point)
                    && Math.abs(point.subtract(key.center).dot(key.normal)) < .1) return i;
        }
        return -1;
    }

    public static boolean pearlAt(Vec3 point) { return PEARL.bounds.inflate(1D / 16).contains(point); }
    public static boolean activationAt(Vec3 point) { return ACTIVATION.bounds.inflate(.04, .08, .04).contains(point); }

    private static VoxelShape[][] shapes() {
        var result = new VoxelShape[4][27];
        for (int f = 0; f < 4; f++) java.util.Arrays.fill(result[f], Shapes.empty());
        for (var value : DATA.getAsJsonArray("parts")) {
            var part = value.getAsJsonObject();
            int index = part.get("index").getAsInt();
            var offset = offset(index);
            for (var facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                int f = quarterTurns(facing);
                var rotatedOffset = worldPoint(Vec3.atLowerCornerOf(offset).add(.5, 0, .5), facing).add(-.5, 0, -.5);
                int worldPart = partIndex((int)Math.round(rotatedOffset.x), offset.getY(), (int)Math.round(rotatedOffset.z));
                var shape = Shapes.empty();
                for (var boxValue : part.getAsJsonArray("boxes")) {
                    var box = boxValue.getAsJsonArray();
                    var a = worldPoint(new Vec3(box.get(0).getAsDouble() / 16 + offset.getX(),
                            box.get(1).getAsDouble() / 16 + offset.getY(), box.get(2).getAsDouble() / 16 + offset.getZ()), facing);
                    var b = worldPoint(new Vec3(box.get(3).getAsDouble() / 16 + offset.getX(),
                            box.get(4).getAsDouble() / 16 + offset.getY(), box.get(5).getAsDouble() / 16 + offset.getZ()), facing);
                    shape = Shapes.or(shape, Shapes.box(Math.min(a.x, b.x) - rotatedOffset.x,
                            Math.min(a.y, b.y) - offset.getY(), Math.min(a.z, b.z) - rotatedOffset.z,
                            Math.max(a.x, b.x) - rotatedOffset.x, Math.max(a.y, b.y) - offset.getY(),
                            Math.max(a.z, b.z) - rotatedOffset.z));
                }
                result[f][worldPart] = shape.optimize();
            }
        }
        return result;
    }

    private static JsonObject read() {
        try (var input = WarpPadGeometry.class.getResourceAsStream(
                "/assets/molecularmanipulator/warp_pad/geometry.json")) {
            if (input == null) throw new IllegalStateException("Missing warp-pad geometry resource");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read warp-pad geometry", error);
        }
    }

    private static Surface[] surfaces(JsonArray array) {
        var result = new Surface[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = surface(array.get(i).getAsJsonObject());
        return result;
    }

    private static Surface surface(JsonObject object) {
        var vertices = object.getAsJsonArray("vertices");
        var points = new Vec3[vertices.size()];
        for (int i = 0; i < points.length; i++) points[i] = vector(vertices.get(i).getAsJsonArray());
        var bounds = object.getAsJsonArray("bounds");
        return new Surface(points, vector(object.getAsJsonArray("normal")),
                new AABB(vector(bounds.get(0).getAsJsonArray()), vector(bounds.get(1).getAsJsonArray())),
                vector(object.getAsJsonArray("center")), pairs(object.getAsJsonArray("uvCorners")));
    }

    private static Vec3 vector(JsonArray array) {
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static double[][] pairs(JsonArray array) {
        var result = new double[array.size()][];
        for (int i = 0; i < result.length; i++) {
            var values = array.get(i).getAsJsonArray();
            result[i] = new double[values.size()];
            for (int j = 0; j < values.size(); j++) result[i][j] = values.get(j).getAsDouble();
        }
        return result;
    }
}
