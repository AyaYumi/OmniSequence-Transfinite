package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.Part;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.PartType;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.StructureLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class OmniCrownStructureTest {
    @Test
    void currentBlueprintHasItsOwnLowEnvelopeAndNoHistoricalAirTail() {
        var parts = OmniComputationStructure.parts();
        assertFalse(parts.isEmpty());
        assertEquals(65, OmniComputationStructure.WIDTH);
        assertEquals(35, OmniComputationStructure.HEIGHT);
        assertEquals(0, parts.stream().mapToInt(Part::y).min().orElseThrow());
        assertEquals(34, parts.stream().mapToInt(Part::y).max().orElseThrow());
        assertTrue(parts.stream().allMatch(part -> Math.abs(part.x()) <= OmniCrownGeometry.RADIUS
                && Math.abs(part.z()) <= OmniCrownGeometry.RADIUS));
        assertEquals(parts.size(), new HashSet<>(parts.stream().map(OmniCrownStructureTest::position).toList()).size());
        assertEquals(1, parts.stream().filter(part -> part.type() == PartType.CONTROLLER).count());
        assertEquals(PartType.CONTROLLER, OmniComputationStructure.partAt(0, 17, -10).type());
        assertEquals(PartType.AIR, OmniComputationStructure.partAt(0, 17, 0).type());
        assertFalse(parts.stream().anyMatch(part -> part.y() > 34 || part.y() < 0),
                "Old tower cleanup coordinates belong only to migration queues");
    }









    @Test
    void largestOuterOrbitIsCompleteAndHorizontalWithTwoNestedFortyFiveDegreeOrbits() {
        var orbits = OmniCrownGeometry.orbits();
        assertEquals(3, orbits.size());
        var outer = orbits.get(0);
        assertTrue(outer.radius() > orbits.get(1).radius() + 5.0);
        assertTrue(outer.radius() > orbits.get(2).radius() + 5.0);
        assertEquals(23.5, orbits.get(1).radius(), 1.0E-9);
        assertEquals(23.5, orbits.get(2).radius(), 1.0E-9);
        for (int index = 1; index < 3; index++) {
            var orbit = orbits.get(index);
            double normalLength = Math.sqrt(orbit.nx() * orbit.nx() + orbit.ny() * orbit.ny() + orbit.nz() * orbit.nz());
            assertEquals(Math.sqrt(0.5), Math.abs(orbit.ny()) / normalLength, 1.0E-7,
                    "Inner orbit must be inclined 45 degrees to the horizontal plane");
        }
        var first = orbits.get(1);
        var second = orbits.get(2);
        double firstLength = Math.sqrt(first.nx() * first.nx() + first.ny() * first.ny() + first.nz() * first.nz());
        double secondLength = Math.sqrt(second.nx() * second.nx() + second.ny() * second.ny() + second.nz() * second.nz());
        double normalDot = (first.nx() * second.nx() + first.ny() * second.ny() + first.nz() * second.nz())
                / (firstLength * secondLength);
        assertEquals(0.0, normalDot, 1.0E-7, "The two inner orbital planes must be perpendicular");
        for (int sample = 0; sample <= 1440; sample++) {
            double phase = Math.PI * 2.0 * sample / 1440.0;
            for (var orbit : orbits) assertTrue(orbit.includes(phase), "No ring may keep the preceding design's segment gaps");
            var point = outer.point(phase);
            assertEquals(0.0, point.y, 1.0E-8);
            assertEquals(outer.radius(), Math.hypot(point.x, point.z), 1.0E-8);
            boolean covered = false;
            // At half-block boundaries either adjacent voxel may own the point;
            // this checks the built ring rather than only its ideal orbit flag.
            for (int x = (int) Math.floor(point.x - 0.5000001); x <= Math.ceil(point.x + 0.5000001); x++) {
                for (int z = (int) Math.floor(point.z - 0.5000001); z <= Math.ceil(point.z + 0.5000001); z++) {
                    if (Math.abs(x - point.x) > 0.5000001 || Math.abs(z - point.z) > 0.5000001) continue;
                    var block = OmniComputationStructure.partAt(x, OmniCrownGeometry.CENTER_Y, z);
                    covered |= block != null && block.type() != PartType.AIR;
                }
            }
            assertTrue(covered, "Outer ring has a physical gap at phase " + phase);
        }
    }

    private static BlockPos position(Part part) {
        return new BlockPos(part.x(), part.y(), part.z());
    }
}
