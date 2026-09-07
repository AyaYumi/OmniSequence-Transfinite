package com.atir.molecularmanipulator.blockentity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.Part;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;
import net.minecraft.world.phys.Vec3;

/** Four open crystal-feather fans around a low, suspended astrolabe ring. */
public final class FrostFeatherGeometry {
    public static final int RADIUS = 30;
    public static final int HEIGHT = 33;
    public static final int CORE_Y = 18;
    public static final int RING_Y = 13;
    public static final double RING_RADIUS = 14.0;

    public record Orbit(double radius, double yawDegrees, double tiltDegrees) {
    }

    private static final List<Orbit> ORBITS = List.of(new Orbit(RING_RADIUS, 0.0, 0.0));
    private static final List<Feather> FEATHERS = List.of(
            new Feather(-10, 5, 9, 16), new Feather(13, 5.5, 12, 22),
            new Feather(37, 6, 15, 27), new Feather(61, 5.5, 13, 23),
            new Feather(84, 5, 10.5, 18), new Feather(108, 4.5, 8, 15));

    /** Shared leaf centerline, in structure coordinates before quadrant mirroring. */
    public record Feather(double degrees, double stem, double length, double tipY) {
        public Vec3 point(double t) {
            double neckY = 13.4 + (tipY - 15.0) * 0.18;
            var point = fanPoint(Math.toRadians(degrees), stem + length * t,
                    neckY + (tipY - neckY) * t + Math.sin(t * Math.PI) * 0.8, 0.0);
            return new Vec3(point.x(), point.y(), point.z());
        }
    }

    public static List<Feather> feathers() {
        return FEATHERS;
    }
    private static final int FRAME = 10;
    private static final int INLAY = 20;
    private static final int TRIM = 30;
    private static final int JEWEL = 40;
    private static final int REQUIRED = 100;

    private FrostFeatherGeometry() {
    }

    public static List<Orbit> orbits() {
        return ORBITS;
    }

    public static List<Part> createParts() {
        var builder = new Builder();
        addMainRing(builder);
        var quadrant = new Builder();
        addFan(quadrant);
        addBowedRib(quadrant);
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                builder.mirror(quadrant, xSign, zSign);
            }
        }
        addInnerJewel(builder);
        addTopStar(builder);
        addPendant(builder);
        builder.put(0, CORE_Y, 0, PartType.AIR, REQUIRED);
        builder.put(0, CORE_Y + 7, 0, PartType.CORE, REQUIRED);
        builder.put(0, 13, -15, PartType.CASING, REQUIRED);
        return builder.parts();
    }

    private static void addMainRing(Builder builder) {
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double radius = Math.hypot(x, z);
                if (radius >= 12.2 && radius <= 15.7) {
                    builder.put(x, RING_Y, z, radius >= 13.5 && radius <= 14.6
                            ? PartType.AE_FLUIX : PartType.AE_QUARTZ, FRAME);
                }
                if (radius >= 12.7 && radius <= 15.25) {
                    builder.put(x, RING_Y - 1, z, PartType.AE_QUARTZ, FRAME);
                }
            }
        }
        for (int[] direction : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            int radialX = direction[0];
            int radialZ = direction[1];
            for (int tangent = -2; tangent <= 2; tangent++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (Math.abs(tangent) + Math.abs(dy) == 2) {
                        builder.put(radialX * 15 - radialZ * tangent, RING_Y + dy,
                                radialZ * 15 + radialX * tangent, PartType.CASING, TRIM);
                    }
                }
            }
            builder.put(radialX * 15, RING_Y + 1, radialZ * 15, PartType.COIL, JEWEL);
            builder.put(radialX * 16, RING_Y, radialZ * 16, PartType.AE_VIBRANT_GLASS, JEWEL);
            builder.put(radialX * 16, RING_Y - 1, radialZ * 16, PartType.STABILIZER, TRIM);
            builder.put(radialX * 16, RING_Y - 2, radialZ * 16, PartType.AE_QUARTZ, FRAME);
        }
    }

    private static void addFan(Builder builder) {
        // Separate directions, raised centers and uneven lengths create a fan, not a plate.
        for (int feather = 0; feather < FEATHERS.size(); feather++) {
            var shape = FEATHERS.get(feather);
            addFeather(builder, Math.toRadians(shape.degrees()), shape.stem(), shape.length(),
                    shape.tipY(), feather);
        }
        // The root is the only richly packed part of the ornament.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance <= 3) {
                    builder.put(10 + dx, RING_Y, 10 + dz, PartType.AE_QUARTZ, FRAME);
                }
                if (distance == 3) {
                    builder.put(10 + dx, RING_Y + 1, 10 + dz, PartType.CASING, TRIM);
                } else if (distance <= 1) {
                    builder.put(10 + dx, RING_Y + 1, 10 + dz, PartType.AE_FLUIX, INLAY);
                }
            }
        }
        builder.put(10, RING_Y + 2, 10, PartType.COIL, JEWEL);
        builder.put(10, RING_Y + 3, 10, PartType.AE_VIBRANT_GLASS, JEWEL);
        builder.put(12, RING_Y + 1, 10, PartType.STABILIZER, TRIM);
        builder.put(10, RING_Y + 1, 12, PartType.STABILIZER, TRIM);
        builder.put(11, RING_Y - 1, 11, PartType.AE_FLUIX, INLAY);
    }

    private static void addFeather(Builder builder, double angle, double stemLength,
            double leafLength, double tipY, int index) {
        double neckY = 13.4 + (tipY - 15.0) * 0.18;
        Point root = new Point(10.0, 13.0, 10.0);
        Point neck = fanPoint(angle, stemLength, neckY, 0.0);
        Point bend = fanPoint(angle, stemLength * 0.58, 12.7, 0.0);
        builder.line(root, bend, 0.72, PartType.AE_QUARTZ, FRAME);
        builder.line(bend, neck, 0.68, PartType.AE_QUARTZ, FRAME);
        builder.put(neck, PartType.CASING, TRIM);
        double bank = index % 2 == 0 ? 0.32 : -0.32;
        for (int sample = 0; sample <= 120; sample++) {
            double t = sample / 120.0;
            double distance = stemLength + leafLength * t;
            double y = neckY + (tipY - neckY) * t + Math.sin(t * Math.PI) * 0.8;
            double halfWidth = Math.pow(Math.sin(t * Math.PI), 0.8) * (index == 2 ? 1.9 : 1.7);
            // The last two blocks converge into a single pointed quill instead of a blunt pair.
            halfWidth *= Math.min(1.0, (1.0 - t) / 0.15);
            for (int side : new int[] { -1, 1 }) {
                Point edge = fanPoint(angle, distance, y + side * halfWidth * bank, side * halfWidth);
                builder.ball(edge, 0.6, PartType.AE_QUARTZ, FRAME);
                // Sparse pale clasps decorate the blue edge without alternating every block.
                if (Math.abs(t - 0.24) < 0.018 || Math.abs(t - 0.69) < 0.018) {
                    builder.put(edge, PartType.CASING, TRIM);
                }
            }
            if (t >= 0.23 && t <= 0.76) {
                Point spine = fanPoint(angle, distance, y + 0.35, 0.0);
                PartType type = t > 0.40 && t < 0.52 ? PartType.GLASS : PartType.AE_FLUIX;
                builder.put(spine, type, INLAY);
            }
            if (t >= 0.80 && t <= 0.88) {
                builder.put(fanPoint(angle, distance, y, 0.0), PartType.AE_VIBRANT_GLASS, JEWEL);
            }
        }
        builder.put(fanPoint(angle, stemLength + leafLength, tipY, 0.0),
                PartType.AE_VIBRANT_GLASS, JEWEL);
    }

    private static Point fanPoint(double angle, double distance, double y, double tangent) {
        return new Point(10.0 + Math.cos(angle) * distance - Math.sin(angle) * tangent, y,
                10.0 + Math.sin(angle) * distance + Math.cos(angle) * tangent);
    }

    private static void addBowedRib(Builder builder) {
        double axis = Math.sqrt(0.5);
        for (int sample = 0; sample <= 100; sample++) {
            double t = sample / 100.0;
            double radius = 14.0 - 11.6 * t;
            double y = 13.0 - 5.0 * Math.sin(t * Math.PI * 0.75) + 0.5 * t;
            for (int side : new int[] { -1, 1 }) {
                double tangent = side * 1.15;
                builder.ball(new Point((radius - tangent) * axis, y, (radius + tangent) * axis),
                        0.6, PartType.AE_QUARTZ, FRAME);
            }
        }
        for (double t : new double[] { 0.28, 0.73 }) {
            double radius = 14.0 - 11.6 * t;
            double y = 13.0 - 5.0 * Math.sin(t * Math.PI * 0.75) + 0.5 * t;
            builder.line(new Point((radius - 1.5) * axis, y, (radius + 1.5) * axis),
                    new Point((radius + 1.5) * axis, y, (radius - 1.5) * axis),
                    0.48, PartType.CASING, TRIM);
            builder.put(new Point(radius * axis, y + 0.7, radius * axis), PartType.AE_VIBRANT_GLASS, JEWEL);
        }
    }

    private static void addInnerJewel(Builder builder) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int distance = Math.abs(x) + Math.abs(z);
                if (distance == 2) {
                    builder.put(x, 9, z, PartType.CASING, TRIM);
                } else if (distance == 1) {
                    builder.put(x, 9, z, PartType.AE_QUARTZ, FRAME);
                }
            }
        }
        builder.put(0, 9, 0, PartType.AE_FLUIX, INLAY);
        builder.put(0, 10, 0, PartType.COIL, JEWEL);
        builder.put(0, 11, 0, PartType.AE_VIBRANT_GLASS, JEWEL);
    }

    private static void addTopStar(Builder builder) {
        Point center = new Point(0, 28, 0);
        for (int sign : new int[] { -1, 1 }) {
            builder.line(center, new Point(sign * 3, 28, 0), 0.5, PartType.AE_QUARTZ, FRAME);
            builder.line(center, new Point(0, 28, sign * 3), 0.5, PartType.AE_QUARTZ, FRAME);
            builder.line(center, new Point(0, 28 + sign * 4, 0), 0.5, PartType.AE_QUARTZ, FRAME);
            builder.put(sign * 3, 28, 0, PartType.AE_VIBRANT_GLASS, JEWEL);
            builder.put(0, 28, sign * 3, PartType.AE_VIBRANT_GLASS, JEWEL);
            builder.put(0, 28 + sign * 4, 0, PartType.AE_VIBRANT_GLASS, JEWEL);
            for (int vertical : new int[] { -1, 1 }) {
                builder.put(sign, 28 + vertical, 0, PartType.CASING, TRIM);
                builder.put(0, 28 + vertical, sign, PartType.CASING, TRIM);
            }
        }
        builder.put(0, 28, 0, PartType.COIL, JEWEL);
        builder.put(0, 27, 0, PartType.AE_FLUIX, INLAY);
        builder.put(0, 29, 0, PartType.AE_FLUIX, INLAY);
    }

    private static void addPendant(Builder builder) {
        builder.put(0, 8, 0, PartType.STABILIZER, TRIM);
        builder.put(0, 7, 0, PartType.AE_QUARTZ, FRAME);
        builder.put(0, 6, 0, PartType.AE_FLUIX, INLAY);
        builder.put(0, 5, 0, PartType.COIL, JEWEL);
        for (int[] direction : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            builder.put(direction[0], 5, direction[1], PartType.CASING, TRIM);
            builder.put(direction[0], 4, direction[1], PartType.AE_QUARTZ, FRAME);
        }
        builder.put(0, 4, 0, PartType.AE_FLUIX, INLAY);
        builder.put(0, 3, 0, PartType.AE_FLUIX, INLAY);
        builder.put(0, 2, 0, PartType.AE_VIBRANT_GLASS, JEWEL);
        builder.put(0, 1, 0, PartType.AE_QUARTZ, FRAME);
        builder.put(0, 0, 0, PartType.AE_VIBRANT_GLASS, JEWEL);
    }

    private record Point(double x, double y, double z) {
    }

    private record Position(int x, int y, int z) {
    }

    private record PlacedPart(Part part, int priority) {
    }

    private static final class Builder {
        private final Map<Position, PlacedPart> placed = new LinkedHashMap<>();

        private void put(Point point, PartType type, int priority) {
            put((int) Math.round(point.x()), (int) Math.round(point.y()),
                    (int) Math.round(point.z()), type, priority);
        }

        private void put(int x, int y, int z, PartType type, int priority) {
            if (Math.abs(x) > RADIUS || Math.abs(z) > RADIUS || y < 0 || y >= HEIGHT) {
                throw new IllegalArgumentException("Frost feather part outside envelope: " + x + "," + y + "," + z);
            }
            var position = new Position(x, y, z);
            var previous = placed.get(position);
            // An ornament owns its crossings; equal priorities retain their first placement.
            if (previous == null || priority > previous.priority()) {
                placed.put(position, new PlacedPart(new Part(x, y, z, type), priority));
            }
        }

        private void mirror(Builder source, int xSign, int zSign) {
            for (var entry : source.placed.values()) {
                Part part = entry.part();
                put(part.x() * xSign, part.y(), part.z() * zSign, part.partType(), entry.priority());
            }
        }

        private void ball(Point center, double radius, PartType type, int priority) {
            boolean any = false;
            for (int x = (int) Math.ceil(center.x() - radius); x <= Math.floor(center.x() + radius); x++) {
                for (int y = (int) Math.ceil(center.y() - radius); y <= Math.floor(center.y() + radius); y++) {
                    for (int z = (int) Math.ceil(center.z() - radius); z <= Math.floor(center.z() + radius); z++) {
                        double dx = x - center.x();
                        double dy = y - center.y();
                        double dz = z - center.z();
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                            put(x, y, z, type, priority);
                            any = true;
                        }
                    }
                }
            }
            if (!any) {
                put(center, type, priority);
            }
        }

        private void line(Point first, Point second, double radius, PartType type, int priority) {
            double dx = second.x() - first.x();
            double dy = second.y() - first.y();
            double dz = second.z() - first.z();
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 4.0));
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                ball(new Point(first.x() + dx * t, first.y() + dy * t, first.z() + dz * t),
                        radius, type, priority);
            }
        }

        private List<Part> parts() {
            return placed.values().stream().map(PlacedPart::part)
                    .sorted(Comparator.comparingInt(Part::y).thenComparingInt(Part::z).thenComparingInt(Part::x))
                    .toList();
        }
    }
}
