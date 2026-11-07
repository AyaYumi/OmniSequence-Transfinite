package com.atir.molecularmanipulator.server;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.StarOrreryRings;
import com.atir.molecularmanipulator.blockentity.StarOrreryRings.Probe;
import com.atir.molecularmanipulator.blockentity.StarOrreryRings.Scan;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Pure geometry/order tests for the server-side nine-ring star orrery scan. */
class StarOrreryStructureTest {
    private static final BlockPos CORE = new BlockPos(23, 70, -11);
    private static final int[] SLOTS = {24, 32, 40, 48, 56, 64, 72, 80, 88};

    @Test
    void layoutHasNineOrderedRingsAndCumulativeEntropyThresholds() {
        assertEquals(9, StarOrreryRings.RINGS);
        assertEquals(257760L, StarOrreryLayout.MAX_ENTROPY);
        long entropy = 0;
        for (int ring = 0; ring < StarOrreryRings.RINGS; ring++) {
            assertEquals(SLOTS[ring], StarOrreryRings.slotCount(ring), "slot count ring " + ring);
            int tier = StarOrreryRings.tierOf(ring);
            assertEquals(ring / 3, tier, "tier ring " + ring);
            entropy += (long) SLOTS[ring] * (tier == 0 ? 10L : tier == 1 ? 100L : 1000L);
            assertEquals(entropy, StarOrreryLayout.entropyForRings(ring + 1), "entropy prefix " + (ring + 1));
            assertEquals(entropy, StarOrreryLayout.threshold(ring + 1), "threshold " + (ring + 1));
        }
        assertEquals(0L, StarOrreryLayout.entropyForRings(0));
        assertEquals(257760L, StarOrreryLayout.entropyForRings(9));
        assertEquals(240, StarOrreryLayout.threshold(1));
        assertEquals(960, StarOrreryLayout.threshold(3));
        assertEquals(17760, StarOrreryLayout.threshold(6));
        assertEquals(89760, StarOrreryLayout.threshold(7));
        assertEquals(257760, StarOrreryLayout.threshold(9));
    }

    @Test
    void rotationForEachHorizontalFacingMatchesTheNorthBaseline() {
        assertEquals(net.minecraft.world.level.block.Rotation.NONE,
                StarOrreryLayout.rotationFor(Direction.NORTH));
        assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                StarOrreryLayout.rotationFor(Direction.EAST));
        assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_180,
                StarOrreryLayout.rotationFor(Direction.SOUTH));
        assertEquals(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90,
                StarOrreryLayout.rotationFor(Direction.WEST));
    }

    @Test
    void everyDirectionUsesTheSameRotatedLayoutWithoutDuplicateSlots() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var positions = new HashSet<BlockPos>();
            for (int ring = 0; ring < StarOrreryRings.RINGS; ring++) {
                for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                    var offset = StarOrreryLayout.offset(facing, ring, slot);
                    var expected = CORE.offset(offset);
                    assertEquals(expected, StarOrreryRings.slot(CORE, facing, ring, slot),
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
            for (int lit = 0; lit <= StarOrreryRings.RINGS; lit++) {
                var blocks = completeRings(facing, lit);
                assertEquals(new Scan(lit, true), scan(facing, blocks, Set.of(), Set.of()),
                        facing + " lit=" + lit);
            }
        }
    }

    @Test
    void breakingAnyRingDropsExactlyToTheRingBeforeIt() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var full = completeRings(facing, StarOrreryRings.RINGS);
            for (int ring = 0; ring < StarOrreryRings.RINGS; ring++) {
                var broken = new HashMap<>(full);
                broken.remove(StarOrreryLayout.slot(CORE, facing, ring,
                        ring % StarOrreryRings.slotCount(ring)));
                assertEquals(new Scan(ring, true), scan(facing, broken, Set.of(), Set.of()),
                        facing + " broken ring " + ring);
            }
        }
    }

    @Test
    void unknownChunksDeferButKnownHolesWinInsideTheSameRing() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var blocks = completeRings(facing, 4);
            var unknown = StarOrreryLayout.slot(CORE, facing, 1, 0);
            assertEquals(new Scan(1, false), scan(facing, blocks, Set.of(unknown), Set.of()),
                    "unloaded ring must stay unresolved");

            var hole = StarOrreryLayout.slot(CORE, facing, 1, 1);
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
        var unknown = StarOrreryLayout.slot(CORE, facing, 2, 3);
        var queried = new HashSet<BlockPos>();
        var result = StarOrreryRings.scan(CORE, facing, (pos, ring) -> {
            queried.add(pos);
            if (pos.equals(unknown)) return Probe.UNLOADED;
            return blocks.getOrDefault(pos, Probe.OTHER);
        });
        assertEquals(new Scan(2, false), result);
        for (int ring = 3; ring < StarOrreryRings.RINGS; ring++) {
            for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                assertFalse(queried.contains(StarOrreryLayout.slot(CORE, facing, ring, slot)),
                        "must not probe outer ring " + ring);
            }
        }
    }

    @Test
    void chunkFootprintIsBoundedAndContainsEveryActualSlotOfThePrefix() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int rings = 0; rings <= StarOrreryRings.RINGS; rings++) {
                var chunks = StarOrreryRings.chunksFor(CORE, facing, rings);
                var required = new HashSet<>(com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation.chunksFor(CORE));
                required.add(new ChunkPos(CORE));
                for (var member : com.atir.molecularmanipulator.starcomputation.StarOrreryCoreStructure.members())
                    required.add(new ChunkPos(CORE.offset(member.offset())));
                assertTrue(chunks.contains(new ChunkPos(CORE)), "core chunk");
                for (int ring = 0; ring < rings; ring++) {
                    for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                        var chunk = new ChunkPos(StarOrreryLayout.slot(CORE, facing, ring, slot));
                        required.add(chunk);
                        assertTrue(chunks.contains(chunk), facing + " ring " + ring + " slot " + slot);
                    }
                }
                assertEquals(required, chunks, "Only the central body, physical foundation and actual prefix slots receive tickets");
                assertTrue(chunks.size() <= 27 + 504 + 928, "fixed layout is bounded");
            }
            assertEquals(StarOrreryRings.chunksFor(CORE, facing, 0), StarOrreryRings.chunksFor(CORE, facing, -1));
            assertEquals(StarOrreryRings.chunksFor(CORE, facing, 9), StarOrreryRings.chunksFor(CORE, facing, 10));
        }
    }

    private static Map<BlockPos, Probe> completeRings(Direction facing, int rings) {
        var blocks = new HashMap<BlockPos, Probe>();
        for (int ring = 0; ring < rings; ring++) {
            for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                blocks.put(StarOrreryLayout.slot(CORE, facing, ring, slot), Probe.MATCH);
            }
        }
        return blocks;
    }

    private static Scan scan(Direction facing, Map<BlockPos, Probe> blocks,
            Set<BlockPos> unloaded, Set<BlockPos> knownHoles) {
        return StarOrreryRings.scan(CORE, facing, (pos, ring) -> {
            if (unloaded.contains(pos)) return Probe.UNLOADED;
            if (knownHoles.contains(pos)) return Probe.OTHER;
            return blocks.getOrDefault(pos, Probe.OTHER);
        });
    }
}
