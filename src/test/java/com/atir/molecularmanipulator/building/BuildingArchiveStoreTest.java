package com.atir.molecularmanipulator.building;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildingArchiveStoreTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    @TempDir Path root;

    @Test void maximumDistinctPatternsRoundTripBySlotThroughNativeComponentsAndByteChunks() throws Exception {
        var entries = new ArrayList<BuildingArchiveStore.Entry>();
        var patterns = new ListTag();
        var random = new Random(217);
        for (int slot = 0; slot < 10800; slot++) {
            var component = new CompoundTag(); component.putInt("unique_recipe", slot);
            byte[] payload = new byte[1024]; random.nextBytes(payload); component.putByteArray("foreign_component", payload);
            var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(component));
            var item = (CompoundTag) stack.save(REGISTRIES); item.putInt("Slot", slot); patterns.add(item);
        }
        int shardSize = Math.ceilDiv(10800, 14);
        for (int shard = 0; shard < 14; shard++) {
            var content = new CompoundTag(); var slots = new ListTag();
            for (int slot = shard * shardSize; slot < Math.min(10800, (shard + 1) * shardSize); slot++) slots.add(patterns.getCompound(slot));
            content.put("patterns", slots);
            entries.add(new BuildingArchiveStore.Entry(new BlockPos(shard, 0, 0), Blocks.AMETHYST_BLOCK.defaultBlockState(), content));
        }
        var id = UUID.randomUUID(); var store = new BuildingArchiveStore(root); store.writeSnapshot(id, entries, REGISTRIES);
        var manifest = new CompoundTag(); manifest.putString("state", "SEALED"); store.writeManifest(id, manifest);
        assertTrue(store.readIndex(id).getLong("native_bytes") > 2L * 1024 * 1024);
        assertEquals(List.of(id), store.listArchives());
        int restored = 0;
        for (int i = 0; i < 14; i++) {
            var actual = new BuildingArchiveStore(root).readEntry(id, i, REGISTRIES);
            assertEquals(entries.get(i), actual);
            for (var value : actual.data().getList("patterns", Tag.TAG_COMPOUND)) {
                var item = (CompoundTag) value;
                assertEquals(restored, item.getInt("Slot"));
                var stack = ItemStack.parseOptional(REGISTRIES, item);
                assertEquals(restored++, stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("unique_recipe"));
            }
        }
        assertEquals(10800, restored);
        assertTrue(store.readIndex(id).getList("entries", Tag.TAG_COMPOUND).getCompound(0).getInt("chunks") > 1);
        assertEquals(14, store.readLayout(id, REGISTRIES).size());
        assertTrue(store.readLayout(id, REGISTRIES).stream().allMatch(entry -> entry.data() == null));
        assertThrows(java.io.IOException.class, () -> store.writeSnapshot(id, entries, REGISTRIES));
    }
    @Test void singleBlockEntityLargerThan64MiBUsesBoundedChunksAndTrustedDiskReader() throws Exception {
        byte[] large = new byte[65 * 1024 * 1024 + 31];
        for (int i = 0; i < large.length; i += 4093) large[i] = (byte) (i * 31);
        var content = new CompoundTag(); content.putByteArray("foreign_bytes", large);
        var entry = new BuildingArchiveStore.Entry(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), content);
        var store = new BuildingArchiveStore(root); var id = UUID.randomUUID(); store.writeSnapshot(id, List.of(entry), REGISTRIES);
        assertArrayEquals(large, store.readEntry(id, 0, REGISTRIES).data().getByteArray("foreign_bytes"));
        var descriptor = store.readIndex(id).getList("entries", Tag.TAG_COMPOUND).getCompound(0);
        assertTrue(descriptor.getInt("chunks") > 256);
        try (var files = Files.list(root.resolve(id.toString()))) {
            for (var path : files.filter(path -> path.toString().endsWith(".nbt.gz")).toList()) {
                try (var gzip = new java.util.zip.GZIPInputStream(Files.newInputStream(path))) {
                    assertTrue(gzip.readAllBytes().length <= BuildingArchiveStore.CHUNK_BYTES);
                }
            }
        }
    }
    @Test void detailPagesDoNotReadOtherPagesAndManifestsPublishAtomically() throws Exception {
        var entries = new ArrayList<BuildingArchiveStore.Entry>();
        for (int i = 0; i < 130; i++) {
            var data = new CompoundTag(); data.putInt("page_value", i);
            entries.add(new BuildingArchiveStore.Entry(new BlockPos(i, 0, 0), Blocks.CHEST.defaultBlockState(), data));
        }
        var store = new BuildingArchiveStore(root); var id = UUID.randomUUID(); store.writeSnapshot(id, entries, REGISTRIES);
        assertFalse(store.exists(id));
        var state = new CompoundTag(); state.putString("state", "SEALED"); store.writeManifest(id, state);
        state.putString("state", "DEPLOYING"); store.writeManifest(id, state);
        assertEquals("DEPLOYING", new BuildingArchiveStore(root).readManifest(id).getString("state"));
        Files.delete(root.resolve(id.toString()).resolve("entry-0-0.nbt.gz"));
        var page = store.readPage(id, 2, REGISTRIES);
        assertEquals(2, page.size()); assertEquals(128, page.getFirst().data().getInt("page_value"));
        assertThrows(java.io.IOException.class, () -> store.readPage(id, 0, REGISTRIES));
        assertThrows(IndexOutOfBoundsException.class, () -> store.readPage(id, 3, REGISTRIES));
    }
    @Test void corruptAndFutureArchivesFailWithoutReturningPartialNativeContents() throws Exception {
        var data = new CompoundTag(); data.putString("owned_item", "must-not-become-empty");
        var store = new BuildingArchiveStore(root); var id = UUID.randomUUID();
        store.writeSnapshot(id, List.of(new BuildingArchiveStore.Entry(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), data)), REGISTRIES);
        var indexFile = root.resolve(id.toString()).resolve("index.nbt"); var original = store.readIndex(id);
        var future = original.copy(); future.putInt("format", BuildingArchiveStore.FORMAT + 1);
        NbtIo.writeCompressed(future, indexFile); assertThrows(java.io.IOException.class, () -> store.readLayout(id, REGISTRIES));
        var incorrectLength = original.copy(); incorrectLength.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putLong("bytes", 2);
        NbtIo.writeCompressed(incorrectLength, indexFile); assertThrows(java.io.IOException.class, () -> store.readEntry(id, 0, REGISTRIES));
        NbtIo.writeCompressed(original, indexFile);
        var chunk = root.resolve(id.toString()).resolve("entry-0-0.nbt.gz"); var encoded = Files.readAllBytes(chunk);
        Files.write(chunk, Arrays.copyOf(encoded, encoded.length - 5));
        assertThrows(java.io.IOException.class, () -> store.readEntry(id, 0, REGISTRIES));
        Files.write(chunk, encoded); assertEquals(data, store.readEntry(id, 0, REGISTRIES).data());
    }
    @Test void relocateOnlyChangesInfrastructureAndRotatesPortMasksWithoutTouchingForeignComponents() {
        var data = new CompoundTag(); data.putInt("x", 1); data.putInt("y", 2); data.putInt("z", 3);
        data.putLong("fabrication_controller", BlockPos.ZERO.asLong());
        data.putLong("molecular_center_controller_pos", BlockPos.ZERO.asLong());
        var proxy = new CompoundTag(); proxy.putInt("p", 123); proxy.putLong("g", 42); data.put("proxy", proxy);
        var foreign = new CompoundTag(); foreign.putLong("fabrication_controller", 999); foreign.putString("custom_name", "foreign item");
        data.put("components", foreign);
        data.putInt("port_output_sides", 1 << Direction.NORTH.ordinal() | 1 << Direction.UP.ordinal());
        var position = new BlockPos(64, 80, -32); var controller = new BlockPos(48, 75, -40);
        var rotated = BuildingTransfer.rotatePorts(BuildingTransfer.relocated(data, position, controller, 900), Rotation.CLOCKWISE_90);
        assertEquals(position.getX(), rotated.getInt("x")); assertEquals(controller.asLong(), rotated.getLong("fabrication_controller"));
        assertEquals(foreign, rotated.getCompound("components"));
        assertEquals(123, rotated.getCompound("proxy").getInt("p")); assertFalse(rotated.getCompound("proxy").contains("g"));
        assertEquals(1 << Direction.EAST.ordinal() | 1 << Direction.UP.ordinal(), rotated.getInt("port_output_sides"));
        assertEquals(1, data.getInt("x")); assertEquals(42, data.getCompound("proxy").getLong("g"));
    }
}
