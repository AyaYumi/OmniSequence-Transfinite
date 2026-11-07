package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.StarOrreryRings;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class StarOrreryLayoutTest {
    @Test
    void everyFacingHas504UniqueSlotsAndAnIdenticalTierProgression() {
        int[] thresholds = {0,240,560,960,5760,11360,17760,89760,169760,257760};
        var core = new BlockPos(127,100,-63);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var unique = new HashSet<BlockPos>();
            long entropy = 0;
            for (int ring = 0; ring < 9; ring++) {
                assertEquals(24 + ring * 8, StarOrreryLayout.slotCount(ring));
                assertEquals(ring / 3, StarOrreryLayout.tierOf(ring));
                for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                    var north = StarOrreryLayout.offset(Direction.NORTH, ring, slot);
                    var pos = StarOrreryLayout.slot(core, facing, ring, slot);
                    assertEquals(core.offset(north.rotate(StarOrreryLayout.rotationFor(facing))), pos);
                    assertTrue(unique.add(pos));
                    assertTrue(pos.distSqr(core) < 34 * 34);
                }
                entropy += StarOrreryLayout.slotCount(ring) * new int[]{10,100,1000}[ring/3];
                assertEquals(thresholds[ring+1], entropy);
                assertEquals(entropy, StarOrreryLayout.entropyForRings(ring+1));
            }
            assertEquals(504,unique.size());
        }
        assertEquals(Rotation.NONE, StarOrreryLayout.rotationFor(Direction.NORTH));
        assertEquals(Rotation.CLOCKWISE_90, StarOrreryLayout.rotationFor(Direction.EAST));
        assertEquals(257760, StarOrreryLayout.MAX_ENTROPY);
        assertThrows(IllegalArgumentException.class, () -> StarOrreryLayout.rotationFor(Direction.UP));
    }

    @Test
    void allMissingSlotsLowerExactlyTheirRingAndNoLaterRingIsProbed() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int ring = 0; ring < 9; ring++) {
                for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                    var missing = StarOrreryLayout.offset(facing, ring, slot);
                    int outer = ring;
                    var result = StarOrreryRings.scan(BlockPos.ZERO,facing,(pos,r) -> {
                        assertTrue(r <= outer);
                        return pos.equals(missing) ? StarOrreryRings.Probe.OTHER : StarOrreryRings.Probe.MATCH;
                    });
                    assertEquals(new StarOrreryRings.Scan(ring,true),result);
                }
            }
        }
    }

    @Test
    void unloadedMembersDoNotMeanAirButKnownHolesWin() {
        var count = new AtomicInteger();
        assertEquals(new StarOrreryRings.Scan(3,false), StarOrreryRings.scan(BlockPos.ZERO,Direction.NORTH,
                (p,r) -> r == 3 && count.getAndIncrement() == 0 ? StarOrreryRings.Probe.UNLOADED : StarOrreryRings.Probe.MATCH));
        count.set(0);
        assertEquals(new StarOrreryRings.Scan(3,true), StarOrreryRings.scan(BlockPos.ZERO,Direction.NORTH,
                (p,r) -> r < 3 ? StarOrreryRings.Probe.MATCH : count.getAndIncrement() == 0
                        ? StarOrreryRings.Probe.UNLOADED : StarOrreryRings.Probe.OTHER));
    }

    @Test
    void footprintIncludesTheFoundationAndCentralBodyThenAddsActualRingSlots() {
        var core = new BlockPos(0,100,0);
        var required = new HashSet<>(StarOrreryFoundation.chunksFor(core));
        required.add(new ChunkPos(core));
        for (var member : StarOrreryCoreStructure.members()) required.add(new ChunkPos(core.offset(member.offset())));
        assertEquals(required, StarOrreryRings.chunksFor(core,Direction.NORTH,0));
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            var shifted = new BlockPos(x, 100, z);
            var footprint = StarOrreryRings.chunksFor(shifted, Direction.NORTH, 0);
            for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1)))
                assertTrue(footprint.contains(new ChunkPos(shifted.offset(offset))), "Cross-chunk central members must retain recovery tickets");
        }
        var all = StarOrreryRings.chunksFor(core,Direction.NORTH,9);
        for (int ring = 0; ring < 9; ring++) for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
            assertTrue(all.contains(new ChunkPos(StarOrreryLayout.slot(core,Direction.NORTH,ring,slot))));
        }
        assertEquals(240,StarOrreryLayout.threshold(1));
    }
}
