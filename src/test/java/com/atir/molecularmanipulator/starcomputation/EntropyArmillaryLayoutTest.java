package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.EntropyArmillaryRings;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class EntropyArmillaryLayoutTest {
    @Test
    void everyFacingHas504UniqueSlotsAndAnIdenticalTierProgression() {
        int[] thresholds = {0,240,560,960,5760,11360,17760,89760,169760,257760};
        var core = new BlockPos(127,100,-63);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var unique = new HashSet<BlockPos>();
            long entropy = 0;
            for (int ring = 0; ring < 9; ring++) {
                assertEquals(24 + ring * 8, EntropyArmillaryLayout.slotCount(ring));
                assertEquals(ring / 3, EntropyArmillaryLayout.tierOf(ring));
                for (int slot = 0; slot < EntropyArmillaryLayout.slotCount(ring); slot++) {
                    var north = EntropyArmillaryLayout.offset(Direction.NORTH, ring, slot);
                    var pos = EntropyArmillaryLayout.slot(core, facing, ring, slot);
                    assertEquals(core.offset(north.rotate(EntropyArmillaryLayout.rotationFor(facing))), pos);
                    assertTrue(unique.add(pos));
                    assertTrue(pos.distSqr(core) < 34 * 34);
                }
                entropy += EntropyArmillaryLayout.slotCount(ring) * new int[]{10,100,1000}[ring/3];
                assertEquals(thresholds[ring+1], entropy);
                assertEquals(entropy, EntropyArmillaryLayout.entropyForRings(ring+1));
            }
            assertEquals(504,unique.size());
        }
        assertEquals(Rotation.NONE, EntropyArmillaryLayout.rotationFor(Direction.NORTH));
        assertEquals(Rotation.CLOCKWISE_90, EntropyArmillaryLayout.rotationFor(Direction.EAST));
        assertEquals(257760, EntropyArmillaryLayout.MAX_ENTROPY);
        assertThrows(IllegalArgumentException.class, () -> EntropyArmillaryLayout.rotationFor(Direction.UP));
    }

    @Test
    void allMissingSlotsLowerExactlyTheirRingAndNoLaterRingIsProbed() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int ring = 0; ring < 9; ring++) {
                for (int slot = 0; slot < EntropyArmillaryLayout.slotCount(ring); slot++) {
                    var missing = EntropyArmillaryLayout.offset(facing, ring, slot);
                    int outer = ring;
                    var result = EntropyArmillaryRings.scan(BlockPos.ZERO,facing,(pos,r) -> {
                        assertTrue(r <= outer);
                        return pos.equals(missing) ? EntropyArmillaryRings.Probe.OTHER : EntropyArmillaryRings.Probe.MATCH;
                    });
                    assertEquals(new EntropyArmillaryRings.Scan(ring,true),result);
                }
            }
        }
    }

    @Test
    void unloadedMembersDoNotMeanAirButKnownHolesWin() {
        var count = new AtomicInteger();
        assertEquals(new EntropyArmillaryRings.Scan(3,false), EntropyArmillaryRings.scan(BlockPos.ZERO,Direction.NORTH,
                (p,r) -> r == 3 && count.getAndIncrement() == 0 ? EntropyArmillaryRings.Probe.UNLOADED : EntropyArmillaryRings.Probe.MATCH));
        count.set(0);
        assertEquals(new EntropyArmillaryRings.Scan(3,true), EntropyArmillaryRings.scan(BlockPos.ZERO,Direction.NORTH,
                (p,r) -> r < 3 ? EntropyArmillaryRings.Probe.MATCH : count.getAndIncrement() == 0
                        ? EntropyArmillaryRings.Probe.UNLOADED : EntropyArmillaryRings.Probe.OTHER));
    }

    @Test
    void footprintIncludesTheShellAcrossChunkCorners() {
        var core = new BlockPos(0,100,0);
        var chunks = EntropyArmillaryRings.chunksFor(core,Direction.NORTH,0);
        assertTrue(chunks.contains(new ChunkPos(-1,-1)));
        assertTrue(chunks.contains(new ChunkPos(0,0)));
        var all = EntropyArmillaryRings.chunksFor(core,Direction.NORTH,9);
        for (int ring = 0; ring < 9; ring++) for (int slot = 0; slot < EntropyArmillaryLayout.slotCount(ring); slot++) {
            assertTrue(all.contains(new ChunkPos(EntropyArmillaryLayout.slot(core,Direction.NORTH,ring,slot))));
        }
        // Opt-in progression does not silently replace the existing column thresholds.
        assertEquals(90,StarComputationBenefits.threshold(1));
        assertEquals(240,EntropyArmillaryLayout.threshold(1));
    }
}
