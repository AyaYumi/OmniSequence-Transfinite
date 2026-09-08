package com.atir.molecularmanipulator.blockentity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.Part;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;

/** Low ring ornaments and four open crystal seats added to the centered feather crown. */
public final class DecoratedFeatherGeometry {
    public static final int RADIUS = 30;
    public static final int MIN_Y = 4;
    public static final int MAX_Y = 32;
    public static final int HEIGHT = 29;
    public static final int CORE_Y = 18;
    public static final int RING_Y = 13;
    public static final int CONTROLLER_X = 0;
    public static final int CONTROLLER_Y = 11;
    public static final int CONTROLLER_Z = 0;

    private DecoratedFeatherGeometry() {
    }

    public static List<FrostFeatherGeometry.Orbit> orbits() {
        return FrostFeatherGeometry.orbits();
    }

    public static List<Part> createParts() {
        var parts = new LinkedHashMap<Position, Part>();
        for (var part : CenteredFeatherGeometry.createParts()) {
            parts.put(new Position(part.x(), part.y(), part.z()), part);
        }
        addArcWindows(parts);
        addCardinalSeats(parts);
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                addFeatherRootSeat(parts, xSign, zSign);
                addInnerCrystalSeat(parts, xSign, zSign);
            }
        }
        protectAccess(parts);
        return parts.values().stream()
                .sorted(Comparator.comparingInt(Part::y).thenComparingInt(Part::z).thenComparingInt(Part::x))
                .toList();
    }

    private static void addArcWindows(Map<Position, Part> parts) {
        // One short window per open eighth of the rim, with blue gaps at its ends.
        int[][] arc = { { 16, 4 }, { 15, 5 }, { 15, 6 }, { 15, 7 }, { 14, 8 } };
        PartType[] materials = { PartType.CASING, PartType.GLASS, PartType.STABILIZER,
                PartType.GLASS, PartType.CASING };
        for (int xSign : new int[] { -1, 1 }) {
            for (int zSign : new int[] { -1, 1 }) {
                for (boolean swap : new boolean[] { false, true }) {
                    for (int index = 0; index < arc.length; index++) {
                        int x = xSign * arc[index][swap ? 1 : 0];
                        int z = zSign * arc[index][swap ? 0 : 1];
                        put(parts, x, 12, z, materials[index]);
                        if (index == 0 || index == arc.length - 1) {
                            put(parts, x, 13, z, PartType.CASING);
                        }
                    }
                }
            }
        }
    }

    private static void addCardinalSeats(Map<Position, Part> parts) {
        for (int[] direction : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            int radialX = direction[0];
            int radialZ = direction[1];
            // Existing crystal centers at r=15/y=14 remain visible above the small casing seat.
            for (int side : new int[] { -1, 1 }) {
                putRadial(parts, radialX, radialZ, 15, side, 13, PartType.CASING);
                putRadial(parts, radialX, radialZ, 16, side, 13, PartType.CASING);
                putRadial(parts, radialX, radialZ, 16, side, 12, PartType.CASING);
            }
            if (radialZ == -1) {
                // Split this badge around the original front ME socket's cable approach.
                putRadial(parts, radialX, radialZ, 17, -1, 13, PartType.STABILIZER);
                putRadial(parts, radialX, radialZ, 17, 1, 13, PartType.STABILIZER);
            } else {
                putRadial(parts, radialX, radialZ, 17, 0, 13, PartType.STABILIZER);
            }
        }
    }

    private static void addFeatherRootSeat(Map<Position, Part> parts, int xSign, int zSign) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                PartType type = dx == 0 && dz == 0 ? PartType.AE_FLUIX : PartType.CASING;
                if (dx == 1 && dz == 0 || dx == 0 && dz == 1) {
                    type = PartType.GLASS;
                }
                putMirrored(parts, 10 + dx, 14, 10 + dz, xSign, zSign, type);
            }
        }
        putMirrored(parts, 12, 14, 11, xSign, zSign, PartType.CASING);
        putMirrored(parts, 11, 14, 12, xSign, zSign, PartType.CASING);
        putMirrored(parts, 12, 14, 12, xSign, zSign, PartType.STABILIZER);
        // The established COIL at (10,15,10) and its tip stay in their original positions.
    }

    private static void addInnerCrystalSeat(Map<Position, Part> parts, int xSign, int zSign) {
        // A five-block cross attaches to one existing bowed rib; its four corners stay open.
        putMirrored(parts, 4, 10, 4, xSign, zSign, PartType.CASING);
        putMirrored(parts, 3, 10, 4, xSign, zSign, PartType.CASING);
        putMirrored(parts, 4, 10, 3, xSign, zSign, PartType.CASING);
        putMirrored(parts, 5, 10, 4, xSign, zSign, PartType.STABILIZER);
        putMirrored(parts, 4, 10, 5, xSign, zSign, PartType.STABILIZER);
        putMirrored(parts, 4, 11, 4, xSign, zSign, PartType.CASING);
        putMirrored(parts, 3, 11, 4, xSign, zSign, PartType.CASING);
        putMirrored(parts, 4, 11, 3, xSign, zSign, PartType.CASING);
        putMirrored(parts, 5, 11, 4, xSign, zSign, PartType.GLASS);
        putMirrored(parts, 4, 11, 5, xSign, zSign, PartType.GLASS);
        putMirrored(parts, 4, 12, 4, xSign, zSign, PartType.COIL);
        // Short crests and inlays emphasize the arms without joining them into a platform.
        putMirrored(parts, 2, 10, 2, xSign, zSign, PartType.STABILIZER);
        putMirrored(parts, 1, 10, 3, xSign, zSign, PartType.CASING);
        putMirrored(parts, 3, 10, 1, xSign, zSign, PartType.CASING);
        putMirrored(parts, 5, 9, 5, xSign, zSign, PartType.AE_FLUIX);
        putMirrored(parts, 6, 9, 6, xSign, zSign, PartType.AE_FLUIX);
    }

    private static void protectAccess(Map<Position, Part> parts) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 11; y <= 12; y++) {
                for (int z = -3; z <= -1; z++) {
                    parts.remove(new Position(x, y, z));
                }
            }
        }
        // Remove the old glass in front of the retained ring socket, not the socket itself.
        for (int z = -18; z <= -16; z++) {
            parts.remove(new Position(0, 13, z));
        }
        put(parts, CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z, PartType.CASING);
        put(parts, 0, 13, -15, PartType.CASING);
    }

    private static void putRadial(Map<Position, Part> parts, int radialX, int radialZ,
            int radius, int tangent, int y, PartType type) {
        put(parts, radialX * radius - radialZ * tangent, y,
                radialZ * radius + radialX * tangent, type);
    }

    private static void putMirrored(Map<Position, Part> parts, int x, int y, int z,
            int xSign, int zSign, PartType type) {
        put(parts, x * xSign, y, z * zSign, type);
    }

    private static void put(Map<Position, Part> parts, int x, int y, int z, PartType type) {
        if (x * x + z * z > 18 * 18 || y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("Feather decoration outside permitted area: " + x + "," + y + "," + z);
        }
        parts.put(new Position(x, y, z), new Part(x, y, z, type));
    }

    private record Position(int x, int y, int z) {
    }
}
