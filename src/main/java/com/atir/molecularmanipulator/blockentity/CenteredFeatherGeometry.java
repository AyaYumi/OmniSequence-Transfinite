package com.atir.molecularmanipulator.blockentity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.Part;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;

/** Local revision of the feather crown with a central controller and a short crystal pendant. */
public final class CenteredFeatherGeometry {
    public static final int RADIUS = 30;
    public static final int MIN_Y = 4;
    public static final int MAX_Y = 32;
    public static final int HEIGHT = 29;
    public static final int CORE_Y = 18;
    public static final int RING_Y = 13;
    public static final int CONTROLLER_X = 0;
    public static final int CONTROLLER_Y = 11;
    public static final int CONTROLLER_Z = 0;

    private CenteredFeatherGeometry() {
    }

    public static List<FrostFeatherGeometry.Orbit> orbits() {
        return FrostFeatherGeometry.orbits();
    }

    public static List<Part> createParts() {
        var parts = new LinkedHashMap<Position, Part>();
        for (var part : FrostFeatherGeometry.createParts()) {
            // Only the old, central 17-block pendant is replaced; the bowed ribs stay intact.
            if (part.y() <= 8 && Math.abs(part.x()) <= 1 && Math.abs(part.z()) <= 1) {
                continue;
            }
            put(parts, part.x(), part.y(), part.z(), part.partType());
        }
        // Keep the original ring socket as an ME casing port after the controller moves.
        put(parts, 0, 13, -15, PartType.CASING);
        put(parts, CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CASING);
        addShortPendant(parts);
        return parts.values().stream()
                .sorted(Comparator.comparingInt(Part::y).thenComparingInt(Part::z).thenComparingInt(Part::x))
                .toList();
    }

    private static void addShortPendant(Map<Position, Part> parts) {
        // A continuous violet crystal narrows into one point instead of a chain of colored beads.
        for (int y = 4; y <= 8; y++) {
            put(parts, 0, y, 0, PartType.AE_FLUIX);
        }
        for (int[] direction : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            int dx = direction[0];
            int dz = direction[1];
            // At y=8 the space between each claw and the core remains open.
            for (int y = 6; y <= 8; y++) {
                put(parts, dx * 2, y, dz * 2, PartType.AE_QUARTZ);
            }
            put(parts, dx, 7, dz, PartType.AE_FLUIX);
            put(parts, dx, 6, dz, PartType.AE_FLUIX);
            put(parts, dx, 5, dz, PartType.AE_QUARTZ);
        }
    }

    private static void put(Map<Position, Part> parts, int x, int y, int z, PartType type) {
        if (Math.abs(x) > RADIUS || Math.abs(z) > RADIUS || y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("Centered feather part outside envelope: " + x + "," + y + "," + z);
        }
        parts.put(new Position(x, y, z), new Part(x, y, z, type));
    }

    private record Position(int x, int y, int z) {
    }
}
