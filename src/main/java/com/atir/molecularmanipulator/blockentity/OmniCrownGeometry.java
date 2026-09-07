package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.Part;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.PartType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Three complete nested orbits around an open spherical computation cage. */
public final class OmniCrownGeometry {
    public static final int CENTER_Y = 17;
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 34;
    public static final int RADIUS = 32;
    public static final double CORE_RADIUS = 10.5;
    private static final List<Orbit> ORBITS = List.of(
            new Orbit(31.0, 0.0, 1.0, 0.0, false),
            new Orbit(23.5, 0.5, Math.sqrt(0.5), 0.5, false),
            new Orbit(23.5, -0.5, Math.sqrt(0.5), -0.5, false));

    private OmniCrownGeometry() {}

    public record Orbit(double radius, double nx, double ny, double nz, boolean segmented) {
        /** Relative to the star; the client draws its light on exactly this circle. */
        public Vec3 point(double phase) {
            var normal = new Vec3(nx, ny, nz).normalize();
            var first = new Vec3(normal.z, 0.0, -normal.x);
            first = first.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : first.normalize();
            return first.scale(Math.cos(phase) * radius)
                    .add(normal.cross(first).scale(Math.sin(phase) * radius));
        }
        public boolean includes(double phase) { return true; }
    }

    public static List<Orbit> orbits() { return ORBITS; }

    public static List<Part> createParts() {
        var builder = new Builder();
        addOuterRing(builder);
        addInclinedRings(builder);
        addSphericalCage(builder);
        // The six stations flank the front instead of inserting a tray across it.
        for (int index = 0; index < 6; index++) {
            double angle = index * Math.PI / 3.0;
            addSpoke(builder, angle);
            addStation(builder, angle, index);
        }
        addController(builder);
        for (int x = -4; x <= 4; x++) for (int dy = -4; dy <= 4; dy++) for (int z = -4; z <= 4; z++) {
            if (x * x + dy * dy + z * z <= 16) builder.put(x, CENTER_Y + dy, z, PartType.AIR);
        }
        builder.put(-5, CENTER_Y, 0, PartType.DATA_ENTANGLER);
        builder.put(5, CENTER_Y, 0, PartType.OUTPUT_NODE);
        return builder.build();
    }

    private static void addOuterRing(Builder builder) {
        // Continuous annulus: no angular gaps, offset plate or front cut-out.
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            double radius = Math.hypot(x, z);
            if (radius < 30.0 || radius > 32.05) continue;
            builder.put(x, CENTER_Y, z, PartType.CASING);
            if (radius >= 30.5 && radius <= 31.6) builder.put(x, CENTER_Y - 1, z, PartType.AE_FLUIX);
        }
        for (int index = 0; index < 24; index++) {
            builder.polar(index * Math.PI / 12.0, 31.0, CENTER_Y, 0,
                    index % 4 == 0 ? PartType.ENERGY_STABILIZER : PartType.PATTERN_MATRIX);
        }
    }

    private static void addInclinedRings(Builder builder) {
        for (int ring = 1; ring < ORBITS.size(); ring++) {
            var orbit = ORBITS.get(ring);
            for (int step = 0; step < 1440; step++) {
                var point = orbit.point(Math.PI * 2.0 * step / 1440.0);
                builder.point(point, PartType.CASING);
                builder.point(point.scale((orbit.radius() - 0.8) / orbit.radius()), PartType.AE_FLUIX);
            }
            for (int station = 0; station < 16; station++) {
                builder.point(orbit.point(station * Math.PI / 8.0),
                        station % 4 == 0 ? PartType.ENERGY_STABILIZER : PartType.PATTERN_MATRIX);
            }
        }
    }

    private static void addSphericalCage(Builder builder) {
        // Six meridians and three thin latitude rings make a readable hollow sphere.
        for (int rib = 0; rib < 6; rib++) {
            double azimuth = rib * Math.PI / 3.0;
            for (int step = 0; step <= 360; step++) {
                double latitude = -Math.PI / 2.0 + Math.PI * step / 360.0;
                builder.polar(azimuth, Math.cos(latitude) * CORE_RADIUS,
                        CENTER_Y + Math.sin(latitude) * CORE_RADIUS, 0, PartType.CASING);
            }
        }
        for (int dy : new int[] {-5, 0, 5}) {
            double radius = Math.sqrt(CORE_RADIUS * CORE_RADIUS - dy * dy);
            for (int step = 0; step < 720; step++) {
                builder.polar(Math.PI * 2.0 * step / 720.0, radius, CENTER_Y + dy, 0,
                        dy == 0 ? PartType.AE_FLUIX : PartType.CASING);
            }
        }
        for (int sx : new int[] {-1, 1}) for (int sy : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) {
            builder.put(sx * 6, CENTER_Y + sy * 6, sz * 6,
                    sx * sy * sz > 0 ? PartType.PARALLEL_MATRIX : PartType.STORAGE_MATRIX);
            builder.put(sx * 6, CENTER_Y + sy * 5, sz * 6, PartType.CASING);
            builder.put(sx * 6, CENTER_Y + sy * 6, sz * 5, PartType.CASING);
            builder.put(sx * 5, CENTER_Y + sy * 6, sz * 6, PartType.CASING);
            if (sz > 0) builder.put(sx * 5, CENTER_Y + sy * 5, 7, PartType.GLASS);
        }
        for (int sign : new int[] {-1, 1}) {
            int y = CENTER_Y + sign * 10;
            builder.put(0, y, 0, PartType.ENERGY_STABILIZER);
            for (int side : new int[] {-1, 1}) {
                builder.put(side, y, 0, PartType.CASING);
                builder.put(0, y, side, PartType.CASING);
            }
            builder.put(0, CENTER_Y + sign * 11, 0, PartType.PATTERN_MATRIX);
        }
    }

    private static void addSpoke(Builder builder, double angle) {
        for (double radius = 10.5; radius <= 30.0; radius += 0.20) {
            builder.polar(angle, radius, CENTER_Y, 0, PartType.CASING);
            if (radius >= 12.0 && radius <= 19.0) builder.polar(angle, radius, CENTER_Y - 1, 0, PartType.AE_FLUIX);
        }
        for (int side : new int[] {-1, 1}) {
            builder.polar(angle, 10.5, CENTER_Y, side, PartType.CASING);
            builder.polar(angle, 10.5, CENTER_Y + 1, side, PartType.PATTERN_MATRIX);
        }
        builder.polar(angle, 10.5, CENTER_Y + 1, 0, PartType.ENERGY_STABILIZER);
    }

    private static void addStation(Builder builder, double angle, int index) {
        for (int tangent = -2; tangent <= 2; tangent++) for (int radial = 29; radial <= 31; radial++) {
            builder.polar(angle, radial, CENTER_Y - 1, tangent, PartType.CASING);
            if (Math.abs(tangent) < 2) builder.polar(angle, radial, CENTER_Y, tangent,
                    radial == 30 ? PartType.AE_FLUIX : PartType.CASING);
        }
        builder.polar(angle, 30, CENTER_Y + 1, 0,
                index % 2 == 0 ? PartType.PARALLEL_MATRIX : PartType.STORAGE_MATRIX);
        builder.polar(angle, 29, CENTER_Y + 1, 0, PartType.ENERGY_STABILIZER);
        double[][] curve = {{31, 18}, {31, 19}, {30.8, 20}, {30, 21}, {29.5, 22}, {29, 23}, {28, 24}};
        for (var point : curve) {
            builder.polar(angle, point[0], point[1], 0, PartType.AE_FLUIX);
            for (int side : new int[] {-1, 1}) builder.polar(angle, point[0], point[1], side, PartType.CASING);
        }
        builder.polar(angle, 30, 19, 0, PartType.GLASS);
        builder.polar(angle, 28, 25, 0, PartType.ENERGY_STABILIZER);
        builder.polar(angle, 28, 26, 0, PartType.CRYSTAL_PYLON);
        builder.polar(angle, 28, 27, 0, PartType.CRYSTAL_PYLON);
        for (int side : new int[] {-1, 1}) builder.polar(angle, 30, 15, side, PartType.CASING);
        builder.polar(angle, 30, 15, 0, PartType.ENERGY_STABILIZER);
        builder.polar(angle, 30, 14, 0, PartType.DATA_ENTANGLER);
        builder.polar(angle, 30, 13, 0, PartType.GLASS);
        builder.polar(angle, 30, 12, 0, PartType.CRYSTAL_PYLON);
        builder.polar(angle, 30, 11, 0, PartType.CRYSTAL_PYLON);
    }

    private static void addController(Builder builder) {
        // The controller belongs to the sphere, never a separate outer-ring platform.
        for (int side : new int[] {-1, 0, 1}) {
            builder.put(side, CENTER_Y - 1, -10, PartType.CASING);
            builder.put(side, CENTER_Y + 1, -10, PartType.CASING);
        }
        builder.put(-1, CENTER_Y, -10, PartType.CASING);
        builder.put(1, CENTER_Y, -10, PartType.CASING);
        builder.put(0, CENTER_Y, -10, PartType.CONTROLLER);
        for (int x = -1; x <= 1; x++) for (int y = CENTER_Y; y <= CENTER_Y + 2; y++) for (int z = -13; z <= -11; z++) {
            builder.put(x, y, z, PartType.AIR);
        }
    }

    private static final class Builder {
        private final Map<BlockPos, Part> parts = new LinkedHashMap<>();
        void put(int x, int y, int z, PartType type) {
            if (Math.abs(x) > RADIUS || Math.abs(z) > RADIUS || y < MIN_Y || y > MAX_Y) {
                throw new IllegalArgumentException("Crown block outside bounds: " + x + "," + y + "," + z);
            }
            parts.put(new BlockPos(x, y, z), new Part(x, y, z, type));
        }
        void point(Vec3 relative, PartType type) {
            put(roundCoordinate(relative.x), CENTER_Y + roundCoordinate(relative.y), roundCoordinate(relative.z), type);
        }
        void polar(double angle, double radius, double y, double tangent, PartType type) {
            put(roundCoordinate(Math.cos(angle) * radius - Math.sin(angle) * tangent), roundCoordinate(y),
                    roundCoordinate(Math.sin(angle) * radius + Math.cos(angle) * tangent), type);
        }
        private static int roundCoordinate(double value) {
            return (int) Math.copySign(Math.floor(Math.abs(value) + 0.5 + 1.0E-9), value);
        }
        List<Part> build() {
            var result = new ArrayList<>(parts.values());
            result.sort(Comparator.comparingInt(Part::y).thenComparingInt(Part::z).thenComparingInt(Part::x));
            return List.copyOf(result);
        }
    }
}
