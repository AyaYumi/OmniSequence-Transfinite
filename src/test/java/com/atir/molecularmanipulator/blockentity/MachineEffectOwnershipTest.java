package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ownership uses exact inverse blueprint coordinates, including old anchors and all rotations. */
class MachineEffectOwnershipTest {
    @Test void everyMolecularCoreResolvesBackToItsSpecificController() {
        var candidates = MachineEffectLevels.molecularCoreCandidates();
        assertFalse(candidates.isEmpty());
        assertEquals(candidates.size(), candidates.stream().distinct().count());
        for (var anchor : new BlockPos[]{new BlockPos(10, 30, 90), new BlockPos(-220, -40, 181)}) {
            for (var layout : MolecularCenterStructure.StructureLayout.values()) {
                if (!layout.isFormed()) continue;
                for (var part : MolecularCenterStructure.parts(layout)) {
                    if (part.partType() != MolecularCenterStructure.PartType.CORE) continue;
                    for (var facing : Direction.Plane.HORIZONTAL) {
                        var worldPart = MolecularCenterStructure.worldPos(anchor, facing, part, layout);
                        assertTrue(candidates.stream().anyMatch(candidate -> candidate.layout() == layout
                                && candidate.facing() == facing && worldPart.subtract(candidate.offset()).equals(anchor)),
                                layout + " " + facing + " " + part);
                    }
                }
            }
        }
    }

    @Test void singularityCoreAndCollectionNodesResolveAllThreeVersionsAndAllRotations() {
        for (var type : new SingularityStructure.Type[]{SingularityStructure.Type.CORE,
                SingularityStructure.Type.COLLECTION_NODE}) {
            var candidates = MachineEffectLevels.singularityCandidates(type);
            assertFalse(candidates.isEmpty());
            assertEquals(candidates.size(), candidates.stream().distinct().count());
            for (int version : new int[]{SingularityStructure.LEGACY_VERSION,
                    SingularityStructure.EMBEDDED_VERSION, SingularityStructure.VERSION}) {
                int parts = 0;
                for (var part : SingularityStructure.parts(version)) {
                    if (part.type() != type) continue;
                    parts++;
                    for (var facing : Direction.Plane.HORIZONTAL) {
                        var controller = new BlockPos(-160, 40, 301);
                        var worldPart = SingularityStructure.worldPos(controller, facing, part, version);
                        assertTrue(candidates.stream().anyMatch(candidate -> candidate.version() == version
                                && candidate.facing() == facing
                                && worldPart.subtract(candidate.offset()).equals(controller)),
                                type + " " + version + " " + facing + " " + part);
                    }
                }
                assertEquals(type == SingularityStructure.Type.CORE ? 1 : 8, parts);
            }
            assertEquals(type == SingularityStructure.Type.CORE ? 12 : 96, candidates.size(),
                    "Only effect-bearing positions are candidates; no structure-volume scan");
        }
    }
}
