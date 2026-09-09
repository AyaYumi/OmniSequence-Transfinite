package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BlueprintRetentionTest {
    @Test void allCurrentBuildingsRemainExactlyTheSame() throws Exception {
        assertEquals("6a360d922c0549b2928ac16c20da7a2b1645b8e8f3eaf2e78ef97cdd9a3f2c78", hash(MolecularCenterStructure.parts().stream().map(p->p.x()+","+p.y()+","+p.z()+","+p.partType())));
        assertEquals("d4545197adafa56080595a19e7ab4984fd646fb030220fabc2dd9c06be643769", hash(OmniComputationStructure.parts().stream().map(p->p.x()+","+p.y()+","+p.z()+","+p.type())));
        assertEquals("bb385802d918841963efc2e183ec3f6d5f1ff9477ab4bbad51d0cba7611d1a33", hash(MatterPearlGeometry.createParts().stream().map(p->p.x()+","+p.y()+","+p.z()+","+p.type())));
    }

    @Test void retainedLegacyBlueprintsMatchOfficialRelease139() throws Exception {
        assertEquals("307c63881448708da46fd022a8961ac45cf8874983a4746409694bde7cc86788", hash(MolecularCenterStructure.parts(MolecularCenterStructure.StructureLayout.LEGACY_1_3_9).stream().map(p->p.x()+","+p.y()+","+p.z()+","+p.partType())));
        assertEquals("0b818b30843c0cfadee396bc5f7aab5924c4c5377f4dbef7934e2c4f5db47241", hash(OmniComputationStructure.parts(OmniComputationStructure.StructureLayout.LEGACY_1_3_9).stream().map(p->p.x()+","+p.y()+","+p.z()+","+p.type())));
    }

    @Test void onlyCurrentAndOfficialLegacyAreRegistered() {
        assertEquals(3,MolecularCenterStructure.StructureLayout.values().length);
        assertEquals(3,OmniComputationStructure.StructureLayout.values().length);
        assertEquals(2,MatterFabricationStructure.StructureLayout.values().length);
        assertEquals(MolecularCenterStructure.StructureLayout.LEGACY_1_3_9,MolecularCenterStructure.StructureLayout.fromSavedName("PALACE"));
        assertEquals(OmniComputationStructure.StructureLayout.LEGACY_1_3_9,OmniComputationStructure.StructureLayout.fromSavedName("PREVIOUS_RADIAL"));
        assertEquals(MolecularCenterStructure.StructureLayout.INCOMPLETE,MolecularCenterStructure.StructureLayout.fromSavedName("DECORATED_FEATHER"));
        assertEquals(OmniComputationStructure.StructureLayout.INCOMPLETE,OmniComputationStructure.StructureLayout.fromSavedName("PREVIOUS_COMPACT_CROWN"));
        assertEquals(MatterFabricationStructure.StructureLayout.NONE,MatterFabricationStructure.StructureLayout.fromSavedName("PREVIOUS_FLOATING_IRIS"));
    }

    private static String hash(java.util.stream.Stream<String> parts) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(parts.sorted().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8)));
    }
}
