package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class StarOrreryCoreStructureTest {
    @Test
    void centralBodyHasTwentyTwoCasingsAndFourHorizontalFacePorts() {
        var members = StarOrreryCoreStructure.members();
        assertEquals(26, members.size());
        assertEquals(26, members.stream().map(StarOrreryCoreStructure.Member::offset).distinct().count());
        assertEquals(22, members.stream().filter(member -> member.material() == StarOrreryCoreStructure.Material.CASING).count());
        assertEquals(4, members.stream().filter(member -> member.material() == StarOrreryCoreStructure.Material.OBSERVATION_PORT).count());
        for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))) {
            var material = StarOrreryCoreStructure.materialAt(offset);
            if (offset.equals(BlockPos.ZERO)) assertNull(material, "The controller is supplied separately");
            else {
                boolean port = offset.getY() == 0 && Math.abs(offset.getX()) + Math.abs(offset.getZ()) == 1;
                assertEquals(port ? StarOrreryCoreStructure.Material.OBSERVATION_PORT : StarOrreryCoreStructure.Material.CASING, material);
                assertTrue(members.contains(new StarOrreryCoreStructure.Member(offset.immutable(), material)));
            }
        }
        for (var offset : new BlockPos[] {new BlockPos(2, 0, 0), new BlockPos(0, -2, 0), new BlockPos(0, 0, 2),
                new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(0, Integer.MAX_VALUE, 0)}) {
            assertNull(StarOrreryCoreStructure.materialAt(offset), "Old columns and unrelated neighbours are not central members");
        }
    }

    @Test
    void everyCentralCellMustHaveItsExactMaterialAtTheTranslatedPosition() {
        var core = new BlockPos(15, 87, 16);
        var blocks = new HashMap<BlockPos, StarOrreryCoreStructure.Material>();
        for (var member : StarOrreryCoreStructure.members()) blocks.put(core.offset(member.offset()), member.material());
        assertEquals(new StarOrreryCoreStructure.Scan(true, true, null, null), scan(core, blocks));
        for (var member : StarOrreryCoreStructure.members()) {
            var pos = core.offset(member.offset());
            var original = blocks.remove(pos);
            assertEquals(new StarOrreryCoreStructure.Scan(false, true, pos, original), scan(core, blocks));
            blocks.put(pos, original == StarOrreryCoreStructure.Material.CASING
                    ? StarOrreryCoreStructure.Material.OBSERVATION_PORT : StarOrreryCoreStructure.Material.CASING);
            assertEquals(new StarOrreryCoreStructure.Scan(false, true, pos, original), scan(core, blocks));
            blocks.put(pos, original);
        }
    }

    @Test
    void unknownChunksDeferFormationButNeverHideAKnownMissingPart() {
        var members = StarOrreryCoreStructure.members();
        var unknown = members.getFirst();
        var missing = members.getLast();
        assertEquals(new StarOrreryCoreStructure.Scan(false, false, unknown.offset(), unknown.material()),
                StarOrreryCoreStructure.scan(BlockPos.ZERO, (pos, material) -> pos.equals(unknown.offset())
                        ? StarOrreryCoreStructure.Probe.UNLOADED : StarOrreryCoreStructure.Probe.MATCH));
        assertEquals(new StarOrreryCoreStructure.Scan(false, true, missing.offset(), missing.material()),
                StarOrreryCoreStructure.scan(BlockPos.ZERO, (pos, material) -> pos.equals(unknown.offset())
                        ? StarOrreryCoreStructure.Probe.UNLOADED : pos.equals(missing.offset())
                        ? StarOrreryCoreStructure.Probe.OTHER : StarOrreryCoreStructure.Probe.MATCH));
    }

    private static StarOrreryCoreStructure.Scan scan(BlockPos core, HashMap<BlockPos, StarOrreryCoreStructure.Material> blocks) {
        return StarOrreryCoreStructure.scan(core, (pos, expected) -> blocks.get(pos) == expected
                ? StarOrreryCoreStructure.Probe.MATCH : StarOrreryCoreStructure.Probe.OTHER);
    }
}
