package com.atir.molecularmanipulator.server;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.EntropyArmillaryRings;
import com.atir.molecularmanipulator.blockentity.EntropyArmillaryRings.Probe;
import com.atir.molecularmanipulator.blockentity.EntropyArmillaryRings.Scan;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Pure geometry/order tests for the server-side nine-ring armillary scan. */
class EntropyArmillaryStructureTest {
    private static final BlockPos CORE = new BlockPos(23, 70, -11);
    private static final int[] SLOTS = {24, 32, 40, 48, 56, 64, 72, 80, 88};

    @Test
    void layoutHasNineOrderedRingsAndCumulativeEntropyThresholds() {
        assertEquals(9, EntropyArmillaryRings.RINGS);
        assertEquals(257760L, EntropyArmillaryLayout.MAX_ENTROPY);
        long entropy = 0;
        for (int ring = 0; ring < EntropyArmillaryRings.RINGS; ring++) {
            assertEquals(SLOTS[ring], EntropyArmillaryRings.slotCount(ring), "slot count ring " + ring);
            int tier = EntropyArmillaryRings.tierOf(ring);
            assertEquals(ring / 3, tier, "tier ring " + ring);
            entropy += (long) SLOTS[ring] * (tier == 0 ? 10L : tier == 1 ? 100L : 1000L);
            assertEquals(entropy, EntropyArmillaryLayout.entropyForRings(ring + 1), "entropy prefix " + (ring + 1));
            assertEquals(entropy, EntropyArmillaryLayout.threshold(ring + 1), "threshold " + (ring + 1));
        }
        assertEquals(0L, EntropyArmillaryLayout.entropyForRings(0));
        assertEquals(257760L, EntropyArmillaryLayout.entropyForRings(9));
        assertEquals(240, EntropyArmillaryLayout.threshold(1));
        assertEquals(960, EntropyArmillaryLayout.threshold(3));
        assertEquals(17760, EntropyArmillaryLayout.threshold(6));
        assertEquals(89760, EntropyArmillaryLayout.threshold(7));
        assertEquals(257760, EntropyArmillaryLayout.threshold(9));
    }

    @Test
    void rotationForEachHorizontalFacingMatchesTheNorthBaseline() {
        assertEquals(net.minecraft.world.level.block.Rotation.NONE,
                EntropyArmillaryLayout.rotationFor(Direction.NORTH));
        assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                EntropyArmillaryLayout.rotationFor(Direction.EAST));
        assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_180,
                EntropyArmillaryLayout.rotationFor(Direction.SOUTH));
        assertEquals(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90,
                EntropyArmillaryLayout.rotationFor(Direction.WEST));
    }

    @Test
    void everyDirectionUsesTheSameRotatedLayoutWithoutDuplicateSlots() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var positions = new HashSet<BlockPos>();
            for (int ring = 0; ring < EntropyArmillaryRings.RINGS; ring++) {
                for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                    var offset = EntropyArmillaryLayout.offset(facing, ring, slot);
                    var expected = CORE.offset(offset);
                    assertEquals(expected, EntropyArmillaryRings.slot(CORE, facing, ring, slot),
                            facing + " ring " + ring + " slot " + slot);
                    assertTrue(positions.add(expected), "duplicate slot " + facing + " " + ring + "/" + slot);
                }
            }
            assertEquals(504, positions.size(), facing.toString());
        }
    }

    @Test
    void everyDirectionScansOnlyAContiguousCompletePrefix() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int lit = 0; lit <= EntropyArmillaryRings.RINGS; lit++) {
                var blocks = completeRings(facing, lit);
                assertEquals(new Scan(lit, true), scan(facing, blocks, Set.of(), Set.of()),
                        facing + " lit=" + lit);
            }
        }
    }

    @Test
    void breakingAnyRingDropsExactlyToTheRingBeforeIt() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var full = completeRings(facing, EntropyArmillaryRings.RINGS);
            for (int ring = 0; ring < EntropyArmillaryRings.RINGS; ring++) {
                var broken = new HashMap<>(full);
                broken.remove(EntropyArmillaryLayout.slot(CORE, facing, ring,
                        ring % EntropyArmillaryRings.slotCount(ring)));
                assertEquals(new Scan(ring, true), scan(facing, broken, Set.of(), Set.of()),
                        facing + " broken ring " + ring);
            }
        }
    }

    @Test
    void unknownChunksDeferButKnownHolesWinInsideTheSameRing() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var blocks = completeRings(facing, 4);
            var unknown = EntropyArmillaryLayout.slot(CORE, facing, 1, 0);
            assertEquals(new Scan(1, false), scan(facing, blocks, Set.of(unknown), Set.of()),
                    "unloaded ring must stay unresolved");

            var hole = EntropyArmillaryLayout.slot(CORE, facing, 1, 1);
            var withHole = new HashMap<>(blocks);
            withHole.remove(hole);
            assertEquals(new Scan(1, true), scan(facing, withHole, Set.of(unknown), Set.of()),
                    "a known hole proves the ring incomplete even beside an unknown slot");
        }
    }

    @Test
    void scanNeverProbesPastTheFirstUnresolvedRing() {
        var facing = Direction.WEST;
        var blocks = completeRings(facing, 6);
        var unknown = EntropyArmillaryLayout.slot(CORE, facing, 2, 3);
        var queried = new HashSet<BlockPos>();
        var result = EntropyArmillaryRings.scan(CORE, facing, (pos, ring) -> {
            queried.add(pos);
            if (pos.equals(unknown)) return Probe.UNLOADED;
            return blocks.getOrDefault(pos, Probe.OTHER);
        });
        assertEquals(new Scan(2, false), result);
        for (int ring = 3; ring < EntropyArmillaryRings.RINGS; ring++) {
            for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                assertFalse(queried.contains(EntropyArmillaryLayout.slot(CORE, facing, ring, slot)),
                        "must not probe outer ring " + ring);
            }
        }
    }

    @Test
    void chunkFootprintIsBoundedAndContainsEveryActualSlotOfThePrefix() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int rings = 0; rings <= 3; rings++) {
                var chunks = EntropyArmillaryRings.chunksFor(CORE, facing, rings);
                assertTrue(chunks.contains(new ChunkPos(CORE)), "core chunk");
                for (int ring = 0; ring < rings; ring++) {
                    for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                        assertTrue(chunks.contains(new ChunkPos(
                                EntropyArmillaryLayout.slot(CORE, facing, ring, slot))),
                                facing + " ring " + ring + " slot " + slot);
                    }
                }
                assertTrue(chunks.size() <= 1 + 24 + 32 + 40, "fixed layout is bounded");
                assertEquals(new HashSet<>(chunks), chunks, "no duplicate ChunkPos");
            }
            var required = new HashSet<>(com.atir.molecularmanipulator.starcomputation.EntropyArmillaryFoundation.chunksFor(CORE));
            for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1})
                required.add(new ChunkPos(CORE.offset(x,0,z)));
            required.add(new ChunkPos(CORE));
            assertEquals(required, EntropyArmillaryRings.chunksFor(CORE, facing, 0));
        }
    }

    private static Map<BlockPos, Probe> completeRings(Direction facing, int rings) {
        var blocks = new HashMap<BlockPos, Probe>();
        for (int ring = 0; ring < rings; ring++) {
            for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                blocks.put(EntropyArmillaryLayout.slot(CORE, facing, ring, slot), Probe.MATCH);
            }
        }
        return blocks;
    }

    private static Scan scan(Direction facing, Map<BlockPos, Probe> blocks,
            Set<BlockPos> unloaded, Set<BlockPos> knownHoles) {
        return EntropyArmillaryRings.scan(CORE, facing, (pos, ring) -> {
            if (unloaded.contains(pos)) return Probe.UNLOADED;
            if (knownHoles.contains(pos)) return Probe.OTHER;
            return blocks.getOrDefault(pos, Probe.OTHER);
        });
    }
}
