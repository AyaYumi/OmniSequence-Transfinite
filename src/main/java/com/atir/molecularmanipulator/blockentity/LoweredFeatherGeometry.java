package com.atir.molecularmanipulator.blockentity;

import java.util.List;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.Part;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;

/** Swap the central controller with its supporting crystal without moving the building. */
public final class LoweredFeatherGeometry {
    public static final int RADIUS = DecoratedFeatherGeometry.RADIUS;
    public static final int MIN_Y = DecoratedFeatherGeometry.MIN_Y;
    public static final int MAX_Y = DecoratedFeatherGeometry.MAX_Y;
    public static final int HEIGHT = DecoratedFeatherGeometry.HEIGHT;
    public static final int CORE_Y = DecoratedFeatherGeometry.CORE_Y;
    public static final int RING_Y = DecoratedFeatherGeometry.RING_Y;
    public static final int CONTROLLER_X = 0;
    public static final int CONTROLLER_Y = 10;
    public static final int CONTROLLER_Z = 0;

    private LoweredFeatherGeometry() {
    }

    public static List<FrostFeatherGeometry.Orbit> orbits() {
        return DecoratedFeatherGeometry.orbits();
    }

    public static List<Part> createParts() {
        return DecoratedFeatherGeometry.createParts().stream().map(part -> {
            if (part.x() == 0 && part.z() == 0) {
                if (part.y() == 10) return new Part(0, 10, 0, PartType.CASING);
                if (part.y() == 11) return new Part(0, 11, 0, PartType.COIL);
            }
            return part;
        }).toList();
    }
}
