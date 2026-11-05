package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HighEnergyLabStructureTest {
    private static CompoundTag template;
    private static Map<BlockPos, CompoundTag> blocks;

    @BeforeAll
    static void load() throws Exception {
        try (var stream = Files.newInputStream(Path.of(
                "src/main/resources/data/molecularmanipulator/structure/twin_singularity_observation_ring.nbt"))) {
            template = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        }
        blocks = new HashMap<>();
        var palette = template.getList("palette", Tag.TAG_COMPOUND);
        for (var tag : template.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) tag;
            var pos = block.getList("pos", Tag.TAG_INT);
            var previous = blocks.put(new BlockPos(pos.getInt(0),pos.getInt(1),pos.getInt(2)),
                    palette.getCompound(block.getInt("state")));
            assertNull(previous, "Duplicate block position");
        }
    }

    @Test
    void buildingUsesVanillaArchitectureAndTheApprovedPlatformInsideItsDeclaredBounds() {
        assertEquals(3955, template.getInt("DataVersion"));
        assertEquals(87443, blocks.size());
        var size = template.getList("size", Tag.TAG_INT);
        assertEquals(163,size.getInt(0)); assertEquals(73,size.getInt(1)); assertEquals(169,size.getInt(2));
        for (var entry : blocks.entrySet()) {
            var pos = entry.getKey();
            assertTrue(pos.getX() >= 0 && pos.getX() < 163 && pos.getY() >= 0 && pos.getY() < 73
                    && pos.getZ() >= 0 && pos.getZ() < 169);
            var name = entry.getValue().getString("Name");
            assertTrue(name.startsWith("minecraft:") || java.util.Set.of(
                    "molecularmanipulator:twin_singularity_observation_ring_power_interface", "molecularmanipulator:twin_singularity_observation_ring_platform",
                    "molecularmanipulator:twin_singularity_observation_ring_input_cradle", "molecularmanipulator:twin_singularity_observation_ring_output_cradle").contains(name));
            assertNotEquals("minecraft:redstone_lamp", name);
            assertNotEquals("minecraft:lever", name);
        }
    }

    @Test
    void spaceBetweenTheHolesContainsNoCentralPrism() {
        for (var pos : blocks.keySet()) {
            var local = pos.offset(-83,-34,-87);
            assertFalse(Math.abs(local.getX()) <= 3 && local.getY() >= 7 && local.getY() <= 19
                    && local.getZ() >= -2 && local.getZ() <= 4, "Central prism remains: " + local);
        }
        assertEquals(0, blocks.values().stream()
                .filter(state -> state.getString("Name").equals("minecraft:amethyst_block")).count(),
                "The old receiver is replaced by the approved low platform");
    }

    @Test
    void singularitiesHaveNoBlockBodyOrRadiationShell() {
        for (var pos : blocks.keySet()) {
            var local = pos.offset(-83,-34,-87);
            for (int cx : new int[] {-13,13}) {
                assertFalse(Math.abs(local.getX()-cx) <= 8 && Math.abs(local.getY()-12) <= 8
                        && Math.abs(local.getZ()) <= 8, "Block at singularity: " + local);
            }
        }
    }

    @Test
    void frontPlatformHasAnEmptyPowerInterfaceAndAnAccessibleCableFace() {
        var inlet = blocks.get(new BlockPos(83,37,134));
        assertEquals("molecularmanipulator:twin_singularity_observation_ring_power_interface",inlet.getString("Name"));
        assertEquals("false",inlet.getCompound("Properties").getString("lit"));
        assertEquals("south",inlet.getCompound("Properties").getString("facing"));
        assertFalse(blocks.containsKey(new BlockPos(83,37,133)), "Rear cable connection must be clear");
        var entries = template.getList("blocks", Tag.TAG_COMPOUND);
        int interfaces = 0;
        for (var entry : entries) {
            var block = (CompoundTag) entry;
            var pos = block.getList("pos", Tag.TAG_INT);
            if (pos.getInt(0) != 83 || pos.getInt(1) != 37 || pos.getInt(2) != 134) continue;
            interfaces++;
            assertEquals("molecularmanipulator:twin_singularity_observation_ring_power_interface", block.getCompound("nbt").getString("id"));
            assertEquals(0, block.getCompound("nbt").getDouble("internalCurrentPower"));
        }
        assertEquals(1,interfaces);
        for (int x : new int[] {-6,6}) for (int y : new int[] {2,3}) for (int z = 40; z <= 51; z++) {
            assertFalse(blocks.containsKey(new BlockPos(x+83,y+34,z+87)), "Side aisle blocked");
        }
    }

    @Test
    void theApprovedPlatformHasTwoCradlesAndEveryPhysicalPavingSection() {
        var inlet = new BlockPos(83,37,134);
        for (var part : com.atir.molecularmanipulator.block.LabPlatformGeometry.PARTS) {
            var state = blocks.get(inlet.offset(part.offset()));
            assertNotNull(state);
            assertEquals("molecularmanipulator:twin_singularity_observation_ring_platform", state.getString("Name"));
            assertEquals(Integer.toString(part.index()), state.getCompound("Properties").getString("part"));
            assertEquals("south", state.getCompound("Properties").getString("facing"));
            assertEquals("false", state.getCompound("Properties").getString("lit"));
        }
        assertEquals("molecularmanipulator:twin_singularity_observation_ring_input_cradle", blocks.get(inlet.offset(-2,0,1)).getString("Name"));
        assertEquals("molecularmanipulator:twin_singularity_observation_ring_output_cradle", blocks.get(inlet.offset(2,0,1)).getString("Name"));
        assertEquals(1, template.getList("blocks", Tag.TAG_COMPOUND).stream()
                .filter(value -> ((CompoundTag)value).contains("nbt")).count(), "Only the supply console has a block entity");
    }

    @Test
    void onePersistentVisualAnchorKeepsTheHolePositionsAndTargetsTheNewInlet() {
        var entities = template.getList("entities",Tag.TAG_COMPOUND);
        assertEquals(1,entities.size());
        var entry = entities.getCompound(0);
        var tag = entry.getCompound("nbt");
        assertEquals("molecularmanipulator:twin_singularity_observation_ring_effects",tag.getString("id"));
        assertFalse(tag.hasUUID("UUID"), "Each template placement must get its own identity");
        var pos = entry.getList("pos",Tag.TAG_DOUBLE);
        assertEquals(83.5,pos.getDouble(0)); assertEquals(38.5,pos.getDouble(1)); assertEquals(137.5,pos.getDouble(2));
        assertTrue(tag.getBoolean("NoGravity"));
        assertEquals(0,tag.getList("Rotation",Tag.TAG_FLOAT).getFloat(0));
        assertArrayEquals(new int[] {0,-1,-3}, tag.getIntArray("PowerOffset"));
    }
}
