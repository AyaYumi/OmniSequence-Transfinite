package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class EntropyArmillaryFoundationTest {
    @Test void openRingFrameHasExactMaterialsConnectedBridgesAndNoSolidFloor() {
        var offsets = new HashSet<BlockPos>();
        var materials = new HashMap<EntropyArmillaryFoundation.Material,Integer>();
        var layers = new HashMap<Integer,Integer>();
        for (var member : EntropyArmillaryFoundation.members()) {
            assertTrue(offsets.add(member.offset()));
            int y = member.offset().getY(), x = member.offset().getX(), z = member.offset().getZ();
            assertTrue(y >= -37 && y <= -34);
            assertTrue(x*x + z*z > 25 && x*x + z*z <= 324);
            materials.merge(member.material(),1,Integer::sum);
            layers.merge(y,1,Integer::sum);
        }
        assertEquals(928,offsets.size());
        assertEquals(520,materials.get(EntropyArmillaryFoundation.Material.PLINTH));
        assertEquals(408,materials.get(EntropyArmillaryFoundation.Material.TRIM));
        assertEquals(java.util.Map.of(-37,552,-36,240,-35,68,-34,68),layers);
        assertTrue(offsets.contains(new BlockPos(18,-37,0)),"Outer index ring");
        assertTrue(offsets.contains(new BlockPos(9,-37,0)),"Inner antigravity ring");
        assertTrue(offsets.contains(new BlockPos(13,-36,0)),"Middle dial ring");
        assertTrue(offsets.contains(new BlockPos(6,-34,0)),"Open well lip");
        assertFalse(offsets.contains(new BlockPos(10,-37,10)),"Open space between lower rings");
        assertFalse(offsets.contains(new BlockPos(11,-36,0)),"No solid middle disk");
        for (int y = -38; y <= -33; y++) assertFalse(offsets.contains(new BlockPos(0,y,0)));
        for (int ring=0; ring<9; ring++) for (int slot=0; slot<EntropyArmillaryLayout.slotCount(ring); slot++)
            assertFalse(offsets.contains(EntropyArmillaryLayout.offset(Direction.NORTH,ring,slot)));

        var reached = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        pending.add(offsets.iterator().next());
        while (!pending.isEmpty()) {
            var pos = pending.removeFirst();
            if (!reached.add(pos)) continue;
            for (var direction : Direction.values()) {
                var neighbor = pos.relative(direction);
                if (offsets.contains(neighbor) && !reached.contains(neighbor)) pending.add(neighbor);
            }
        }
        assertEquals(offsets,reached,"All rings and the well collar join through the four narrow bridges");
    }

    @Test void realMaterialRequirementsAreCheckedInAllFourDirections() {
        var core = new BlockPos(-17,100,31);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var expected = new HashMap<BlockPos,EntropyArmillaryFoundation.Material>();
            for (var member : EntropyArmillaryFoundation.members())
                assertNull(expected.put(EntropyArmillaryFoundation.position(core,facing,member),member.material()));
            var good = EntropyArmillaryFoundation.scan(core,facing,(p,m) -> m == expected.get(p)
                    ? EntropyArmillaryFoundation.Probe.MATCH : EntropyArmillaryFoundation.Probe.OTHER);
            assertTrue(good.complete());assertTrue(good.resolved());
            var broken = EntropyArmillaryFoundation.position(core,facing,EntropyArmillaryFoundation.members().get(17));
            expected.remove(broken);
            var bad = EntropyArmillaryFoundation.scan(core,facing,(p,m) -> m == expected.get(p)
                    ? EntropyArmillaryFoundation.Probe.MATCH : EntropyArmillaryFoundation.Probe.OTHER);
            assertFalse(bad.complete());assertTrue(bad.resolved());assertEquals(broken,bad.problem());
            for (var p : expected.keySet()) assertTrue(EntropyArmillaryFoundation.chunksFor(core).contains(new ChunkPos(p)));
        }
    }

    @Test void constantTimeContainmentMatchesEveryRequiredMaterialInEveryDirection() {
        var expected = new HashMap<BlockPos,EntropyArmillaryFoundation.Material>();
        for (var member : EntropyArmillaryFoundation.members()) {
            expected.put(member.offset(),member.material());
            for (var facing : Direction.Plane.HORIZONTAL) {
                var rotated = member.offset().rotate(EntropyArmillaryLayout.rotationFor(facing));
                assertEquals(member.material(),EntropyArmillaryFoundation.materialAt(rotated));
            }
        }
        for (int y=-38; y<=-33; y++) for (int x=-19; x<=19; x++) for (int z=-19; z<=19; z++) {
            var pos = new BlockPos(x,y,z);
            assertEquals(expected.get(pos),EntropyArmillaryFoundation.materialAt(pos),pos.toString());
        }
        assertNull(EntropyArmillaryFoundation.materialAt(new BlockPos(Integer.MAX_VALUE,-37,0)));
        assertNull(EntropyArmillaryFoundation.materialAt(new BlockPos(Integer.MIN_VALUE,-37,0)));
    }

    @Test void retiredTerracesAreRestorationPositionsNotRequirementsOrChunkTickets() {
        var offsets = EntropyArmillaryFoundation.restorationOffsets();
        assertEquals(11084,offsets.size());
        assertEquals(offsets.size(),new HashSet<>(offsets).size());
        for (var member : EntropyArmillaryFoundation.members()) assertTrue(offsets.contains(member.offset()));
        var retired = new BlockPos(30,-38,0);
        assertTrue(offsets.contains(retired));
        assertNull(EntropyArmillaryFoundation.materialAt(retired));
        assertTrue(EntropyArmillaryFoundation.restorationChunksFor(BlockPos.ZERO).contains(new ChunkPos(retired)));
        var shifted = new BlockPos(8,100,8);
        assertFalse(EntropyArmillaryFoundation.chunksFor(shifted).contains(new ChunkPos(shifted.offset(0,-38,-30))));
    }

    @Test void unloadedChunksAreNotMissingButKnownWrongBlocksStillWin() {
        var unknown = EntropyArmillaryFoundation.members().getFirst().offset();
        var wrong = EntropyArmillaryFoundation.members().getLast().offset();
        var scan = EntropyArmillaryFoundation.scan(BlockPos.ZERO,Direction.NORTH,(p,m) -> p.equals(unknown)
                ? EntropyArmillaryFoundation.Probe.UNLOADED : EntropyArmillaryFoundation.Probe.MATCH);
        assertFalse(scan.resolved());assertFalse(scan.complete());assertEquals(unknown,scan.problem());
        var missing = EntropyArmillaryFoundation.scan(BlockPos.ZERO,Direction.NORTH,(p,m) -> p.equals(unknown)
                ? EntropyArmillaryFoundation.Probe.UNLOADED : p.equals(wrong)
                ? EntropyArmillaryFoundation.Probe.OTHER : EntropyArmillaryFoundation.Probe.MATCH);
        assertTrue(missing.resolved());assertFalse(missing.complete());assertEquals(wrong,missing.problem());
    }
}
