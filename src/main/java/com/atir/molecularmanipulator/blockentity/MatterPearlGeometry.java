package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.Part;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.PartType;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** A pearl-white fabrication pavilion with layered terraces, buttresses and an open scanning crown. */
public final class MatterPearlGeometry {
    public static final int RADIUS = 20;
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 26;
    public static final int CENTER_Y = 13;
    public static final int RIM_Y = 3;
    public static final double RIM_RADIUS = 8.0;
    public static final int FIELD_HALF_WIDTH = 5;
    public static final int FIELD_MIN_Y = 5;
    public static final int FIELD_MAX_Y = 21;
    private static final int FRAME = 10;
    private static final int WINDOW = 20;
    private static final int CONDUIT = 30;
    private static final int NODE = 40;
    private static final int REQUIRED = 100;

    private MatterPearlGeometry() {
    }

    public static List<BlockPos> fieldEmitters() {
        return List.of(new BlockPos(-4, 4, -4), new BlockPos(4, 4, -4),
                new BlockPos(-4, 4, 4), new BlockPos(4, 4, 4));
    }

    public static BlockPos scannerPoint() {
        return new BlockPos(0, 22, 0);
    }

    public static List<BlockPos> crownConduits(int side) {
        return java.util.stream.IntStream.rangeClosed(2, 8)
                .mapToObj(step -> new BlockPos(side * (4 + step), 24, 3 + (int)Math.round(step * 0.6)))
                .toList();
    }

    /** The gold platform edge is a clipped square, not a circular orbit. */
    public static List<BlockPos> platformOutline() {
        return List.of(new BlockPos(8, 3, 5), new BlockPos(5, 3, 8),
                new BlockPos(-5, 3, 8), new BlockPos(-8, 3, 5),
                new BlockPos(-8, 3, -5), new BlockPos(-5, 3, -8),
                new BlockPos(5, 3, -8), new BlockPos(8, 3, -5));
    }

    public static List<Part> createParts() {
        var builder = new Builder();
        addBase(builder);
        addCentralPlatform(builder);
        addSideWall(builder, -1);
        addSideWall(builder, 1);
        addRearWall(builder);
        addScanner(builder);
        addTerraceDetails(builder);
        addPlatformCollar(builder);
        addCrownBraces(builder);
        addConsole(builder);
        for (var emitter : fieldEmitters()) builder.put(emitter, PartType.CORE, NODE);
        builder.put(0, 2, -15, PartType.CONTROLLER, REQUIRED);
        builder.put(0, 2, -14, PartType.COIL, REQUIRED);
        for (int z : new int[] {-6, 0, 6}) {
            builder.put(-15, 1, z, PartType.CASING, REQUIRED);
            builder.put(15, 1, z, PartType.CASING, REQUIRED);
        }
        for (int x : new int[] {-6, 0, 6}) builder.put(x, 1, 15, PartType.CASING, REQUIRED);
        return builder.build();
    }

    private static void addBase(Builder builder) {
        for (int y = 0; y <= 2; y++) {
            int radius = RADIUS - (y == 1 ? 2 : y == 2 ? 1 : 0);
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (!insideChamfer(x, z, radius, 6)) continue;
                if (y == 2 && Math.abs(x) <= 6 && Math.abs(z) <= 6) continue;
                builder.put(x, y, z, PartType.CASING, FRAME);
                if (y == 2 && insideChamfer(x, z, 17, 6) && !insideChamfer(x, z, 16, 6)) {
                    // A single quiet gold perimeter line sits flush with the upper terrace.
                    builder.put(x, y, z, PartType.COIL, CONDUIT);
                }
            }
        }
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) == 5) builder.put(x, 1, z, PartType.STABILIZER, WINDOW);
        }
        // The console remains on the front edge of the original service footprint.
        builder.put(-1, 2, -15, PartType.CASING, FRAME);
        builder.put(1, 2, -15, PartType.CASING, FRAME);
        builder.put(0, 3, -15, PartType.CASING, FRAME);
    }

    private static void addCentralPlatform(Builder builder) {
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            if (insideChamfer(x, z, 8, 3) && !insideChamfer(x, z, 6, 2)) {
                builder.put(x, 3, z, insideChamfer(x, z, 7, 3) ? PartType.CASING : PartType.COIL, CONDUIT);
            }
            if (insideChamfer(x, z, 6, 2) && (Math.abs(x) >= 4 || Math.abs(z) >= 4)) {
                builder.put(x, 3, z, PartType.CASING, FRAME);
            }
        }
        for (var emitter : fieldEmitters()) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                builder.put(emitter.offset(x, -1, z), PartType.CASING, FRAME);
                if (Math.abs(x) + Math.abs(z) == 1) builder.put(emitter.offset(x, 0, z), PartType.CASING, FRAME);
            }
        }
    }

    private static void addSideWall(Builder builder, int side) {
        for (int z = -8; z <= 10; z++) {
            int top = 12 + (int) Math.round((z + 8) * 12.0 / 18.0);
            for (int y = 3; y <= top; y++) {
                boolean frame = z == -8 || z == 10 || y == 3 || y >= top - 1;
                builder.put(side * 15, y, z, frame ? PartType.CASING : PartType.GLASS,
                        frame ? FRAME : WINDOW);
                if (frame) builder.put(side * 14, y, z, PartType.CASING, FRAME);
            }
            if (z > -8 && z < 10) builder.put(side * 14, 4, z, PartType.COIL, CONDUIT);
        }
        for (int y = 5; y <= 22; y++) builder.put(side * 14, y, 9, PartType.STABILIZER, CONDUIT);
        builder.put(side * 14, 5, -7, PartType.CORE, NODE);
        builder.put(side * 14, 22, 9, PartType.CORE, NODE);
        for (int z : new int[] {-6, 0, 6}) {
            int crest = 13 + (z + 6) * 2 / 3;
            for (int y = 3; y <= crest; y++) {
                int inset = Math.min(3, (y - 3) * 3 / (crest - 3));
                int x = 18 - inset;
                for (int width = -1; width <= 1; width++) {
                    builder.put(side * x, y, z + width, PartType.CASING, FRAME);
                    builder.put(side * (x + 1), y, z + width, PartType.CASING, FRAME);
                }
                if (y > 4 && y < crest - 1) builder.put(side * (x + 1), y, z, PartType.STABILIZER, CONDUIT);
            }
            for (int x = 14; x <= 17; x++) builder.put(side * x, crest, z, PartType.CASING, FRAME);
            builder.put(side * 17, 4, z, PartType.CORE, NODE);
        }
        // Each window has an inset sill and a three-dimensional upper cornice.
        for (int z = -8; z <= 10; z++) {
            int top = 12 + (int) Math.round((z + 8) * 12.0 / 18.0);
            builder.put(side * 16, top, z, PartType.CASING, FRAME);
            builder.put(side * 13, 3, z, PartType.CASING, FRAME);
        }
        for (int x = 14; x <= 17; x++) for (int z = -9; z <= -7; z++) {
            builder.put(side * x, 3, z, PartType.CASING, FRAME);
            builder.put(side * x, 4, z, PartType.CASING, FRAME);
        }
    }

    private static void addRearWall(Builder builder) {
        for (int x = -12; x <= 12; x++) {
            int top = MAX_Y - Math.max(0, Math.abs(x) - 3);
            for (int y = 3; y <= top; y++) {
                boolean frame = Math.abs(x) >= 11 || y == 3 || y >= top - 1;
                builder.put(x, y, 12, frame ? PartType.CASING : PartType.GLASS,
                        frame ? FRAME : WINDOW);
                if (frame) builder.put(x, y, 13, PartType.CASING, FRAME);
            }
            if (Math.abs(x) < 11) builder.put(x, 4, 11, PartType.COIL, CONDUIT);
            builder.put(x, top, 14, PartType.CASING, FRAME);
        }
        for (int y = 5; y <= 24; y++) builder.put(0, y, 11, PartType.STABILIZER, CONDUIT);
        builder.put(0, 24, 11, PartType.CORE, NODE);
        builder.put(-9, 5, 11, PartType.CORE, NODE);
        builder.put(9, 5, 11, PartType.CORE, NODE);
        for (int side : new int[] {-1, 1}) {
            for (int y = 3; y <= 21; y++) {
                int x = side * (11 - (y - 3) / 7);
                builder.put(x, y, 14, PartType.CASING, FRAME);
                builder.put(x, y, 15, PartType.CASING, FRAME);
                if (y > 5 && y < 20) builder.put(x, y, 15, PartType.STABILIZER, CONDUIT);
            }
        }
        // A compact diamond relief gives the rear crown a deliberate focal ornament.
        for (int x = -2; x <= 2; x++) for (int y = 22; y <= 26; y++) {
            if (Math.abs(x) + Math.abs(y - 24) <= 2) builder.put(x, y, 10, PartType.CASING, FRAME);
        }
        builder.put(0, 24, 10, PartType.CORE, NODE);
    }

    private static void addScanner(Builder builder) {
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            if (insideChamfer(x, z, 5, 3)) builder.put(x, 22, z,
                    Math.max(Math.abs(x), Math.abs(z)) <= 2 ? PartType.GLASS : PartType.CASING, FRAME);
            if (insideChamfer(x, z, 4, 3) && Math.max(Math.abs(x), Math.abs(z)) >= 3) {
                builder.put(x, 23, z, PartType.CASING, FRAME);
            }
            if (Math.abs(x) + Math.abs(z) <= 2) builder.put(x, 24, z, PartType.CASING, FRAME);
        }
        for (int side : new int[] {-1, 1}) {
            builder.put(side * 4, 23, 0, PartType.COIL, CONDUIT);
            builder.put(0, 23, side * 4, PartType.COIL, CONDUIT);
        }
        builder.put(scannerPoint(), PartType.CORE, NODE);
    }

    private static void addTerraceDetails(Builder builder) {
        for (int side : new int[] {-1, 1}) {
            for (int z = -10; z <= 10; z++) {
                for (int x = 10; x <= 12; x++) {
                    boolean frame = x == 10 || x == 12 || z == -10 || z == 10 || Math.floorMod(z + 10, 7) == 0;
                    builder.put(side * x, 2, z, frame ? PartType.CASING : PartType.GLASS, WINDOW);
                    if (!frame) builder.put(side * x, 1, z, PartType.COIL, CONDUIT);
                }
            }
            for (int z = -14; z <= 12; z++) builder.put(side * 13, 3, z, PartType.CASING, FRAME);
            for (int x = 5; x <= 13; x++) {
                builder.put(side * x, 3, -13, PartType.CASING, FRAME);
                builder.put(side * x, 3, 13, PartType.CASING, FRAME);
            }
            for (int sign : new int[] {-1, 1}) {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                    builder.put(side * 14 + dx, 3, sign * 14 + dz, PartType.CASING, FRAME);
                    if (Math.abs(dx) + Math.abs(dz) <= 2) builder.put(side * 14 + dx, 4, sign * 14 + dz, PartType.CASING, FRAME);
                }
                builder.put(side * 14, 4, sign * 14, PartType.CORE, NODE);
            }
        }
        for (int x = -9; x <= 9; x++) for (int z = 9; z <= 10; z++) {
            builder.put(x, 2, z, Math.abs(x) % 6 == 0 ? PartType.CASING : PartType.GLASS, WINDOW);
            builder.put(x, 1, z, PartType.COIL, CONDUIT);
        }
    }

    private static void addPlatformCollar(Builder builder) {
        for (int side : new int[] {-1, 1}) {
            for (int tangent = -2; tangent <= 2; tangent++) {
                builder.put(side * 8, 4, tangent, PartType.CASING, FRAME);
                builder.put(tangent, 4, side * 8, PartType.CASING, FRAME);
                if (Math.abs(tangent) <= 1) {
                    builder.put(side * 7, 5, tangent, PartType.CASING, FRAME);
                    builder.put(tangent, 5, side * 7, PartType.CASING, FRAME);
                }
            }
            builder.put(side * 7, 5, 0, PartType.CORE, NODE);
            builder.put(0, 5, side * 7, PartType.CORE, NODE);
            for (int other : new int[] {-1, 1}) {
                builder.put(side * 6, 4, other * 6, PartType.STABILIZER, CONDUIT);
                builder.put(side * 6, 5, other * 6, PartType.CASING, FRAME);
            }
        }
    }

    private static void addCrownBraces(Builder builder) {
        for (int side : new int[] {-1, 1}) {
            for (int step = 0; step <= 10; step++) {
                int x = 4 + step, z = 3 + (int)Math.round(step * 0.6);
                for (int dz = -1; dz <= 1; dz++) builder.put(side * x, 23, z + dz, PartType.CASING, FRAME);
            }
            for (var conduit : crownConduits(side)) builder.put(conduit, PartType.COIL, CONDUIT);
            for (int x = 12; x <= 14; x++) for (int z = 10; z <= 12; z++) {
                if (x + z >= 24) builder.put(side * x, 23, z, PartType.CASING, FRAME);
            }
            for (int x = 3; x <= 14; x++) {
                int y = 26 - (int)Math.ceil((x - 3) / 4.0);
                builder.put(side * x, y, 13, PartType.CASING, FRAME);
                builder.put(side * x, y, 14, PartType.CASING, FRAME);
                builder.put(side * x, y - 1, 13, PartType.CASING, FRAME);
                builder.put(side * x, y - 1, 14, PartType.CASING, FRAME);
            }
        }
    }

    private static void addConsole(Builder builder) {
        for (int side : new int[] {-1, 1}) {
            for (int z = -17; z <= -12; z++) {
                builder.put(side * 3, 2, z, PartType.CASING, FRAME);
                builder.put(side * 3, 3, z + 1, PartType.CASING, FRAME);
                if (z >= -14) builder.put(side * 2, 3, z, PartType.COIL, CONDUIT);
            }
            builder.put(side * 2, 3, -15, PartType.CORE, NODE);
            builder.put(side * 2, 2, -15, PartType.CASING, FRAME);
        }
    }

    public static boolean isServiceChannel(int x, int y, int z) {
        if (y < 1 || y > 3) return false;
        if (Math.abs(x) <= 1 && z >= -20 && z <= -16) return true;
        if (Math.abs(x) >= 16 && Math.abs(x) <= 20) {
            for (int bay : new int[] {-6, 0, 6}) if (Math.abs(z - bay) <= 1) return true;
        }
        if (z >= 16 && z <= 20) {
            for (int bay : new int[] {-6, 0, 6}) if (Math.abs(x - bay) <= 1) return true;
        }
        return false;
    }

    private static boolean insideChamfer(int x, int z, int radius, int cut) {
        return Math.max(Math.abs(x), Math.abs(z)) <= radius
                && Math.abs(x) + Math.abs(z) <= radius * 2 - cut;
    }

    private record Placed(Part part, int priority) {
    }

    private static final class Builder {
        private final Map<BlockPos, Placed> parts = new LinkedHashMap<>();

        private void put(BlockPos pos, PartType type, int priority) {
            put(pos.getX(), pos.getY(), pos.getZ(), type, priority);
        }

        private void put(int x, int y, int z, PartType type, int priority) {
            if (Math.abs(x) > RADIUS || Math.abs(z) > RADIUS || y < MIN_Y || y > MAX_Y) {
                throw new IllegalArgumentException("Pearl chamber block outside bounds: " + x + "," + y + "," + z);
            }
            if (isServiceChannel(x, y, z)) return;
            if (Math.abs(x) <= FIELD_HALF_WIDTH && Math.abs(z) <= FIELD_HALF_WIDTH
                    && y >= FIELD_MIN_Y && y <= FIELD_MAX_Y) {
                throw new IllegalArgumentException("Pearl chamber construction field must remain empty");
            }
            var pos = new BlockPos(x, y, z);
            var old = parts.get(pos);
            if (old == null || priority >= old.priority()) {
                parts.put(pos, new Placed(new Part(x, y, z, type), priority));
            }
        }

        private List<Part> build() {
            return parts.values().stream().map(Placed::part)
                    .sorted(Comparator.comparingInt(Part::y).thenComparingInt(Part::z).thenComparingInt(Part::x))
                    .toList();
        }
    }
}
