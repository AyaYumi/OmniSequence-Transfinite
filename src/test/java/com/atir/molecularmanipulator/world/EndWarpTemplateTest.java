package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndWarpTemplateTest {
    private static final Path TEMPLATES = Path.of("src/main/resources/data/molecularmanipulator/structure");

    @BeforeAll
    static void bootstrap() {
        HighEnergyLabWorldgenTest.bootstrap();
    }

    @Test
    void sourceRuinContainsTheCompleteNorthFacingPadAtItsWorldAnchor() throws Exception {
        var template = checkTemplate("end_warp_ruin", EndWarpRuinStructure.SIZE, EndWarpRuinStructure.ORIGIN,
                new BlockPos(20, 22, 20), EndWarpTravel.sourcePad(), false);
        var palette = template.getList("palette", Tag.TAG_COMPOUND);
        var glyphs = new HashSet<Integer>();
        for (var value : template.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) value;
            var state = palette.getCompound(block.getInt("state"));
            assertFalse(state.getString("Name").endsWith("_sign"), "The coordinate clue belongs in the masonry");
            if (!state.getString("Name").equals("molecularmanipulator:warp_coordinate_inscription")) continue;
            int glyph = Integer.parseInt(state.getCompound("Properties").getString("glyph"));
            assertTrue(glyphs.add(glyph));
            var pos = block.getList("pos", Tag.TAG_INT);
            assertEquals(new BlockPos(28, 22, 20 + glyph), new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
            assertEquals("west", state.getCompound("Properties").getString("facing"));
            assertFalse(block.contains("nbt"), "Carved symbols use ordinary static block models");
        }
        assertEquals(java.util.Set.of(0, 1, 2), glyphs);
    }

    @Test
    void attachedLandingContainsTheCompleteReturnPadAtItsWorldAnchor() throws Exception {
        checkTemplate("end_lab_warp_landing", HighEnergyLabStructure.LANDING_SIZE, HighEnergyLabStructure.LANDING_ORIGIN,
                new BlockPos(3, 2, 7), EndWarpTravel.labPad(), true);
    }

    private static CompoundTag checkTemplate(String name, Vec3i expectedSize, BlockPos origin, BlockPos core,
            BlockPos worldCore, boolean returning) throws Exception {
        CompoundTag template;
        try (var stream = Files.newInputStream(TEMPLATES.resolve(name + ".nbt"))) {
            template = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        }
        var size = template.getList("size", Tag.TAG_INT);
        assertEquals(expectedSize, new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)));
        assertEquals(worldCore, origin.offset(core));
        var palette = template.getList("palette", Tag.TAG_COMPOUND);
        var seen = new HashSet<BlockPos>();
        var parts = new HashSet<Integer>();
        int cores = 0;
        for (var value : template.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) value;
            var coordinates = block.getList("pos", Tag.TAG_INT);
            var pos = new BlockPos(coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2));
            assertTrue(seen.add(pos), "Duplicate block at " + pos);
            assertTrue(pos.getX() >= 0 && pos.getX() < expectedSize.getX()
                    && pos.getY() >= 0 && pos.getY() < expectedSize.getY()
                    && pos.getZ() >= 0 && pos.getZ() < expectedSize.getZ(), "Block outside template bounds: " + pos);
            var state = palette.getCompound(block.getInt("state"));
            if (!state.getString("Name").equals("molecularmanipulator:end_warp_pad")) continue;
            var offset = pos.subtract(core);
            assertTrue(Math.abs(offset.getX()) <= 1 && Math.abs(offset.getY()) <= 1 && Math.abs(offset.getZ()) <= 1);
            int part = Integer.parseInt(state.getCompound("Properties").getString("part"));
            assertEquals((offset.getY() + 1) * 9 + (offset.getZ() + 1) * 3 + offset.getX() + 1, part);
            assertTrue(parts.add(part), "Duplicate pad part " + part);
            assertEquals("north", state.getCompound("Properties").getString("facing"));
            if (part == 13) {
                cores++;
                assertEquals(core, pos);
                var nbt = block.getCompound("nbt");
                assertEquals("molecularmanipulator:end_warp_pad", nbt.getString("id"));
                assertTrue(nbt.contains("Return", Tag.TAG_BYTE));
                assertEquals(returning, nbt.getBoolean("Return"));
                for (int glyph = 0; glyph < 3; glyph++) assertTrue(nbt.contains("Glyph" + glyph, Tag.TAG_INT));
                assertTrue(nbt.contains("Pearl", Tag.TAG_BYTE));
            } else {
                assertFalse(block.contains("nbt"), "Only pad part 13 has a block entity");
            }
        }
        assertEquals(27, parts.size());
        assertEquals(1, cores);
        return template;
    }
}
