package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.Part;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.StructureLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class LoweredFeatherGeometryTest {
    @Test
    void onlyTheControllerAndTheCrystalImmediatelyBelowItExchangePlaces() {
        var previous = DecoratedFeatherGeometry.createParts();
        var parts = LoweredFeatherGeometry.createParts();
        assertEquals(parts, MolecularCenterStructure.parts());
        assertEquals(previous.size(), parts.size());
        var oldByPosition = previous.stream().collect(Collectors.toMap(p -> List.of(p.x(), p.y(), p.z()), p -> p));
        var currentByPosition = parts.stream().collect(Collectors.toMap(p -> List.of(p.x(), p.y(), p.z()), p -> p));
        assertEquals(oldByPosition.keySet(), currentByPosition.keySet());
        for (var part : previous) {
            PartType expected = part.x() == 0 && part.z() == 0 && part.y() == 10 ? PartType.CASING
                    : part.x() == 0 && part.z() == 0 && part.y() == 11 ? PartType.COIL : part.partType();
            assertEquals(new Part(part.x(), part.y(), part.z(), expected), currentByPosition.get(List.of(part.x(), part.y(), part.z())),
                    "Every other block, including the interaction and cable-access channels, must remain unchanged");
        }
        assertEquals(previous.stream().collect(Collectors.groupingBy(Part::partType, Collectors.counting())),
                parts.stream().collect(Collectors.groupingBy(Part::partType, Collectors.counting())),
                "Exchanging controller and coil must not increase the material requirement");
        assertEquals(List.of(new Part(0, 10, 0, PartType.CASING)), parts.stream().filter(MolecularCenterStructure::isController).toList());
        assertEquals(List.of(new Part(0, 25, 0, PartType.CORE)), parts.stream().filter(p -> p.partType() == PartType.CORE).toList());
        assertTrue(parts.contains(new Part(0, 18, 0, PartType.AIR)));
        assertEquals(4, parts.stream().mapToInt(Part::y).min().orElseThrow());
        assertEquals(32, parts.stream().mapToInt(Part::y).max().orElseThrow());
    }



    @Test
    void currentHeightLimitsUseSixBlocksBelowAndTwentyTwoAbove() {
        int[][] dimensions = {{-64, 320, -58, 297}, {0, 256, 6, 233}, {0, 384, 6, 361}};
        var parts = LoweredFeatherGeometry.createParts();
        for (var bounds : dimensions) {
            int minimum = MolecularCenterStructure.minimumControllerY(bounds[0]);
            int maximum = MolecularCenterStructure.maximumControllerY(bounds[1]);
            assertEquals(bounds[2], minimum);
            assertEquals(bounds[3], maximum);
            assertTrue(parts.stream().allMatch(p -> minimum + p.y() - 10 >= bounds[0]));
            assertTrue(parts.stream().allMatch(p -> maximum + p.y() - 10 < bounds[1]));
            assertTrue(parts.stream().anyMatch(p -> minimum - 1 + p.y() - 10 < bounds[0]));
            assertTrue(parts.stream().anyMatch(p -> maximum + 1 + p.y() - 10 >= bounds[1]));
        }
    }

    @Test
    void exportCurrentLoweredCoordinatesForVisualInspection() throws Exception {
        Path output = Path.of("build/reports/lowered-feather/parts.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, MolecularCenterStructure.parts().stream()
                .map(p -> "[" + p.x() + "," + p.y() + "," + p.z() + ",\"" + p.partType() + "\"]")
                .collect(Collectors.joining(",\n", "[\n", "\n]\n")));
    }
}
