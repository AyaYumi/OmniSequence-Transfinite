package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MolecularCenter139RestoreTest {
    // Generated independently from release 25dd485's unmodified geometry methods.
    private static final String RELEASE_139_SHA256 =
            "307c63881448708da46fd022a8961ac45cf8874983a4746409694bde7cc86788";

    @Test
    void retiredPalaceBlueprintExactlyMatchesRelease139() throws Exception {
        var parts = blueprint("LEGACY_PARTS");
        String canonical = parts.stream()
                .map(p -> p.x() + "," + p.y() + "," + p.z() + "," + p.partType())
                .sorted().collect(Collectors.joining("\n"));
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        assertEquals(4793, parts.size());
        assertEquals(RELEASE_139_SHA256, actual);
        var counts = new HashMap<MolecularCenterStructure.PartType, Long>();
        for (var part : parts) counts.merge(part.partType(), 1L, Long::sum);
        assertEquals(Map.of(
                MolecularCenterStructure.PartType.CASING, 196L,
                MolecularCenterStructure.PartType.GLASS, 156L,
                MolecularCenterStructure.PartType.COIL, 52L,
                MolecularCenterStructure.PartType.STABILIZER, 452L,
                MolecularCenterStructure.PartType.CORE, 1L,
                MolecularCenterStructure.PartType.AE_QUARTZ, 2900L,
                MolecularCenterStructure.PartType.AE_VIBRANT_GLASS, 529L,
                MolecularCenterStructure.PartType.AE_FLUIX, 506L,
                MolecularCenterStructure.PartType.AIR, 1L), counts);
    }

    @Test
    void palaceBoundsAndCoreAnchorsMatch139() throws Exception {
        var parts = blueprint("LEGACY_PARTS");
        assertEquals(-15, parts.stream().mapToInt(MolecularCenterStructure.Part::x).min().orElseThrow());
        assertEquals(15, parts.stream().mapToInt(MolecularCenterStructure.Part::x).max().orElseThrow());
        assertEquals(-15, parts.stream().mapToInt(MolecularCenterStructure.Part::z).min().orElseThrow());
        assertEquals(15, parts.stream().mapToInt(MolecularCenterStructure.Part::z).max().orElseThrow());
        assertEquals(0, parts.stream().mapToInt(MolecularCenterStructure.Part::y).min().orElseThrow());
        assertEquals(45, parts.stream().mapToInt(MolecularCenterStructure.Part::y).max().orElseThrow());
        assertEquals(29, MolecularCenterStructure.visualCoreY(
                MolecularCenterStructure.StructureLayout.LEGACY_1_3_9));
        assertTrue(parts.contains(new MolecularCenterStructure.Part(0, 29, 0,
                MolecularCenterStructure.PartType.AIR)));
        assertTrue(parts.contains(new MolecularCenterStructure.Part(0, 36, 0,
                MolecularCenterStructure.PartType.CORE)));
        assertTrue(parts.stream().anyMatch(p -> MolecularCenterStructure.isController(
                p, MolecularCenterStructure.StructureLayout.LEGACY_1_3_9)));
    }





    @Test
    void palaceUpgradeTouchesOnlySourceAndTargetBlueprints() throws Exception {
        assertSourceMigration("LEGACY_PARTS", MolecularCenterStructure.StructureLayout.LEGACY_1_3_9);
    }

    private void assertSourceMigration(String sourceName,
            MolecularCenterStructure.StructureLayout sourceLayout) throws Exception {
        var crown = blueprint(sourceName);
        var target = MolecularCenterStructure.parts();
        var work = MolecularCenterStructure.updateWorkParts(sourceLayout);
        var expected = new HashSet<String>();
        var sourcePositions = new HashSet<String>();
        var targetByPosition = new HashMap<String, MolecularCenterStructure.Part>();
        for (var part : crown) {
            if (part.partType() != MolecularCenterStructure.PartType.AIR
                    && !MolecularCenterStructure.isController(part, sourceLayout)) {
                expected.add(key(part));
                sourcePositions.add(key(part));
            }
        }
        for (var part : target) {
            expected.add(key(part));
            targetByPosition.put(key(part), part);
        }
        Set<String> actual = work.stream().map(MolecularCenter139RestoreTest::key)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
        assertEquals(actual.size(), work.size(), "Migration coordinates must be unique");
        int removals = 0;
        for (var part : work) {
            if (targetByPosition.containsKey(key(part))) {
                assertEquals(targetByPosition.get(key(part)), part);
            } else {
                assertTrue(sourcePositions.contains(key(part)));
                assertEquals(MolecularCenterStructure.PartType.AIR, part.partType());
                removals++;
            }
        }
        assertTrue(removals > 0, "Retired-only crown blocks must be eligible for opt-in recovery");
        assertTrue(MolecularCenterStructure.updateWorkParts(
                MolecularCenterStructure.StructureLayout.CURRENT).isEmpty());
        assertTrue(MolecularCenterStructure.updateWorkParts(
                MolecularCenterStructure.StructureLayout.INCOMPLETE).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<MolecularCenterStructure.Part> blueprint(String name) throws Exception {
        Field field = MolecularCenterStructure.class.getDeclaredField(name);
        field.setAccessible(true);
        return (List<MolecularCenterStructure.Part>) field.get(null);
    }

    private static String key(MolecularCenterStructure.Part part) {
        return part.x() + "," + part.y() + "," + part.z();
    }
}
