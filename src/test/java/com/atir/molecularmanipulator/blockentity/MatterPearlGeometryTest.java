package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.Part;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.PartType;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.StructureLayout;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MatterPearlGeometryTest {
    @Test
    void chamberUsesItsOwnFortyOneByTwentySevenEnvelopeAndTheExistingPalette() {
        var parts = MatterPearlGeometry.createParts();
        assertEquals(parts.size(), index(parts).size());
        assertEquals(-20, parts.stream().mapToInt(Part::x).min().orElseThrow());
        assertEquals(20, parts.stream().mapToInt(Part::x).max().orElseThrow());
        assertEquals(-20, parts.stream().mapToInt(Part::z).min().orElseThrow());
        assertEquals(20, parts.stream().mapToInt(Part::z).max().orElseThrow());
        assertEquals(0, parts.stream().mapToInt(Part::y).min().orElseThrow());
        assertEquals(26, parts.stream().mapToInt(Part::y).max().orElseThrow());
        var palette = EnumSet.of(PartType.CONTROLLER, PartType.CASING, PartType.GLASS,
                PartType.COIL, PartType.STABILIZER, PartType.CORE);
        assertTrue(parts.stream().allMatch(part -> palette.contains(part.type())));
        assertEquals(1, parts.stream().filter(part -> part.type() == PartType.CONTROLLER).count());
        assertTrue(parts.stream().filter(part -> part.type() == PartType.CORE).count() <= 36);
    }

    @Test
    void layeredPavilionRetainsItsWindowsAndUnobstructedCentralEntrance() {
        var parts = MatterPearlGeometry.createParts();
        var index = index(parts);
        for (var pos : List.of(new BlockPos(-15, 10, 0), new BlockPos(15, 10, 0), new BlockPos(5, 10, 12))) {
            assertEquals(PartType.GLASS, index.get(pos).type(), "A large window must remain in each field wall");
        }
        assertEquals(PartType.CASING, index.get(new BlockPos(0, 26, 12)).type());
        assertEquals(PartType.CASING, index.get(new BlockPos(-15, 24, 10)).type());
        assertEquals(PartType.CASING, index.get(new BlockPos(15, 24, 10)).type());
        assertTrue(parts.stream().noneMatch(part -> part.y() >= 5 && part.z() < -8),
                "The entrance must not acquire a tall front wall");
        for (int x = -5; x <= 5; x++) for (int y = 6; y <= 21; y++) for (int z = -15; z < -5; z++) {
            assertNull(index.get(new BlockPos(x, y, z)), "The view from the console to the fabrication field stays open");
        }
        assertEquals(PartType.STABILIZER, index.get(new BlockPos(18, 10, 0)).type(),
                "The outer rib must have an inset vertical conductor");
        assertEquals(PartType.CASING, index.get(new BlockPos(10, 23, 7)).type(),
                "The scanning crown is tied to the upper side frame");
        assertEquals(PartType.GLASS, index.get(new BlockPos(11, 2, -8)).type(),
                "The broad terrace must have recessed instrument windows");
        assertEquals(PartType.GLASS, index.get(new BlockPos(2, 22, 0)).type(),
                "The enlarged scanner contains a clear lens around its existing emitter");
    }

    @Test
    void centralSlicesHaveTheirEntireClearVolumeAndSharedEmittersAreRealBlocks() {
        var parts = MatterPearlGeometry.createParts();
        assertTrue(parts.stream().noneMatch(part -> Math.abs(part.x()) <= 5 && Math.abs(part.z()) <= 5
                && part.y() >= 5 && part.y() <= 21));
        var index = index(parts);
        assertEquals(4, MatterPearlGeometry.fieldEmitters().size());
        for (var emitter : MatterPearlGeometry.fieldEmitters()) {
            assertEquals(PartType.CORE, index.get(emitter).type());
            assertEquals(PartType.CASING, index.get(emitter.below()).type());
            assertNull(index.get(emitter.above()));
        }
        assertEquals(new BlockPos(0, 22, 0), MatterPearlGeometry.scannerPoint());
        assertEquals(PartType.CORE, index.get(MatterPearlGeometry.scannerPoint()).type());
        assertNull(index.get(MatterPearlGeometry.scannerPoint().below()));
        for (var corner : MatterPearlGeometry.platformOutline()) assertEquals(PartType.COIL, index.get(corner).type());
        for (int side : new int[] {-1, 1}) for (var conduit : MatterPearlGeometry.crownConduits(side)) {
            assertEquals(PartType.COIL, index.get(conduit).type(), "Crown light paths must sit on real conductor blocks");
        }
    }

    @Test
    void fourPlatformCollarsHaveThreeCenterServicePositionsEach() {
        var index = index(MatterPearlGeometry.createParts());
        for (int side : new int[] {-1, 1}) {
            for (int tangent = -1; tangent <= 1; tangent++) {
                assertEquals(PartType.CASING, index.get(new BlockPos(side * 8, 4, tangent)).type());
                assertEquals(PartType.CASING, index.get(new BlockPos(tangent, 4, side * 8)).type());
            }
            for (int tangent : new int[] {-2, 2}) {
                assertEquals(PartType.CASING, index.get(new BlockPos(side * 8, 4, tangent)).type());
                assertEquals(PartType.CASING, index.get(new BlockPos(tangent, 4, side * 8)).type());
            }
        }
    }

    @Test
    void allNineServiceSlotsHaveThreeWideChannelsThroughTheExpandedPlatform() {
        var index = index(MatterPearlGeometry.createParts());
        for (int bay : new int[] {-6, 0, 6}) {
            for (int side : new int[] {-1, 1}) {
                assertEquals(PartType.CASING, index.get(new BlockPos(side * 15, 1, bay)).type());
                for (int outward = 16; outward <= 20; outward++) for (int y = 1; y <= 3; y++) {
                    for (int tangent = -1; tangent <= 1; tangent++) {
                        assertNull(index.get(new BlockPos(side * outward, y, bay + tangent)),
                                "Side service channel must stay clear all the way to the new outer edge");
                    }
                }
                assertNotNull(index.get(new BlockPos(side * 20, 0, bay)), "The service channel must retain its floor");
            }
            assertEquals(PartType.CASING, index.get(new BlockPos(bay, 1, 15)).type());
            for (int outward = 16; outward <= 20; outward++) for (int y = 1; y <= 3; y++) {
                for (int tangent = -1; tangent <= 1; tangent++) assertNull(index.get(new BlockPos(bay + tangent, y, outward)));
            }
        }
        assertEquals(PartType.CONTROLLER, index.get(new BlockPos(0, 2, -15)).type());
        assertEquals(PartType.COIL, index.get(new BlockPos(0, 2, -14)).type());
        for (int z = -20; z <= -16; z++) for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) assertNull(index.get(new BlockPos(x, y, z)));
        }
    }



    private static Map<BlockPos, Part> index(List<Part> parts) {
        var result = new HashMap<BlockPos, Part>();
        for (var part : parts) assertNull(result.put(new BlockPos(part.x(), part.y(), part.z()), part));
        return result;
    }
}
