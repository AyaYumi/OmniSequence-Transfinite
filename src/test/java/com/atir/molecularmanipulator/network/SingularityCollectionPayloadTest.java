package com.atir.molecularmanipulator.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Exercises the real Forge wire codec without a game server, mod registrations, or dynamic registries. */
class SingularityCollectionPayloadTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void chunksRespect128ItemBoundariesWithoutTrailingPackets() {
        assertEquals(128, SingularityCollectionPayload.MAX_ITEMS_PER_CHUNK);
        var registered = vanillaItems();
        assertTrue(registered.size() >= 257);
        for (int size : new int[] {0, 1, 127, 128, 129, 255, 256, 257}) {
            var expected = registered.subList(0, size);
            var chunks = SingularityCollectionPayload.chunks(7, 42, expected);
            assertEquals(Math.max(1, (size + 127) / 128), chunks.size(), "Snapshot size " + size);
            int offset = 0;
            for (var chunk : chunks) {
                assertEquals(7, chunk.containerId());
                assertEquals(42L, chunk.generation());
                assertEquals(size, chunk.total());
                assertEquals(offset, chunk.offset());
                assertEquals(Math.min(128, size - offset), chunk.items().size());
                offset += chunk.items().size();
            }
            assertEquals(size, offset);
            assertEquals(expected, chunks.stream().flatMap(chunk -> chunk.items().stream()).toList());
        }
    }

    @Test
    void numericCodecRoundTripsWholeVanillaSnapshotInOrderWithinHardByteBudget() {
        var expected = new ArrayList<>(vanillaItems());
        Collections.reverse(expected); // Neither the codec nor the receiver may sort registry ids.
        assertTrue(expected.size() > 2 * 128, "Use distinct registered items, not repeated dummy entries");
        var chunks = SingularityCollectionPayload.chunks(Integer.MAX_VALUE, Long.MAX_VALUE, expected);
        assertEquals((expected.size() + 127) / 128, chunks.size());
        var decodedItems = new ArrayList<Item>();
        var receiver = new SingularityCollectionState();
        var initiallyVisible = receiver.resources();
        for (int index = 0; index < chunks.size(); index++) {
            var chunk = chunks.get(index);
            var buffer = newBuffer();
            try {
                SingularityCollectionPayload.STREAM_CODEC.encode(buffer, chunk);
                // Four VarInts, one long, and at most 128 numeric ids (five bytes each).
                assertTrue(buffer.readableBytes() <= 28 + 128 * 5, "No unbounded string or stack data on the wire");
                int expectedBytes = FriendlyByteBuf.getVarIntSize(chunk.containerId()) + Long.BYTES
                        + FriendlyByteBuf.getVarIntSize(chunk.offset()) + FriendlyByteBuf.getVarIntSize(chunk.total())
                        + FriendlyByteBuf.getVarIntSize(chunk.items().size());
                for (var item : chunk.items()) expectedBytes += FriendlyByteBuf.getVarIntSize(BuiltInRegistries.ITEM.getId(item));
                assertEquals(expectedBytes, buffer.readableBytes());
                assertEquals(chunk.containerId(), buffer.readVarInt());
                assertEquals(chunk.generation(), buffer.readLong());
                assertEquals(chunk.offset(), buffer.readVarInt());
                assertEquals(chunk.total(), buffer.readVarInt());
                assertEquals(chunk.items().size(), buffer.readVarInt());
                for (var item : chunk.items()) assertEquals(BuiltInRegistries.ITEM.getId(item), buffer.readVarInt());
                assertFalse(buffer.isReadable(), "The packet contains only metadata and numeric item ids");

                buffer.readerIndex(0);
                var decoded = SingularityCollectionPayload.STREAM_CODEC.decode(buffer);
                assertEquals(chunk, decoded);
                assertFalse(buffer.isReadable());
                decodedItems.addAll(decoded.items());
                assertTrue(receiver.accept(decoded));
                if (index + 1 < chunks.size()) assertSame(initiallyVisible, receiver.resources());
            } finally {
                buffer.release();
            }
        }
        assertEquals(expected, decodedItems);
        assertEquals(expected, receiver.resources().stream().map(ItemStack::getItem).toList());
        assertTrue(receiver.resources().stream().allMatch(stack -> !stack.isEmpty() && stack.getCount() == 1));
    }

    @Test
    void emptySnapshotRoundTripsAsOneSmallPacketAndClearsExistingResources() {
        var receiver = new SingularityCollectionState();
        assertTrue(receiver.accept(new SingularityCollectionPayload(7, 41, 0, 1, List.of(Items.DIAMOND))));
        var previous = receiver.resources();
        var chunks = SingularityCollectionPayload.chunks(7, 42, List.of());
        assertEquals(1, chunks.size(), "An empty snapshot still needs a clearing packet");
        var buffer = newBuffer();
        try {
            SingularityCollectionPayload.STREAM_CODEC.encode(buffer, chunks.get(0));
            assertEquals(12, buffer.readableBytes());
            var decoded = SingularityCollectionPayload.STREAM_CODEC.decode(buffer);
            assertEquals(new SingularityCollectionPayload(7, 42, 0, 0, List.of()), decoded);
            assertFalse(buffer.isReadable());
            assertTrue(receiver.accept(decoded));
            assertTrue(receiver.resources().isEmpty());
            assertNotSame(previous, receiver.resources());
            assertEquals(Items.DIAMOND, previous.get(0).getItem());
        } finally {
            buffer.release();
        }
    }

    @Test
    void payloadMakesADefensiveUnmodifiableCopyOfCallerItems() {
        var input = new ArrayList<>(List.of(Items.DIAMOND, Items.PAPER));
        var payload = new SingularityCollectionPayload(7, 0, 0, 2, input);
        input.set(0, Items.COAL);
        input.clear();
        assertEquals(List.of(Items.DIAMOND, Items.PAPER), payload.items());
        assertThrows(UnsupportedOperationException.class, () -> payload.items().add(Items.COAL));
        assertThrows(UnsupportedOperationException.class, () -> payload.items().set(0, Items.COAL));
        var buffer = newBuffer();
        try {
            SingularityCollectionPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, SingularityCollectionPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void chunkFactoryCopiesEverySubListAndProtectsReturnedList() {
        var expected = List.copyOf(vanillaItems().subList(0, 257));
        var input = new ArrayList<>(expected);
        var chunks = SingularityCollectionPayload.chunks(7, 0, input);
        input.set(0, Items.DIAMOND);
        input.set(128, Items.PAPER);
        input.set(256, Items.COAL);
        input.clear();
        assertEquals(expected, chunks.stream().flatMap(chunk -> chunk.items().stream()).toList());
        assertThrows(UnsupportedOperationException.class, () -> chunks.remove(0));
        for (var chunk : chunks) assertThrows(UnsupportedOperationException.class, () -> chunk.items().clear());
    }

    @Test
    void constructorRejectsInvalidBoundsAndNullItems() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(-1, 0, 0, 1, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, -1, 0, 1, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, -1, 1, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, -1, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, BuiltInRegistries.ITEM.size() + 1, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 2, 1, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, 129, Collections.nCopies(129, Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 1, 2, List.of(Items.STONE, Items.PAPER))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, 0, List.of(Items.STONE))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SingularityCollectionPayload(7, 0, 1, 1, List.of())),
                () -> assertThrows(NullPointerException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, 1, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SingularityCollectionPayload(7, 0, 0, 1, Collections.singletonList(null))));
    }

    @Test
    void invalidMetadataIsRejectedBeforeReadingAnyItemIds() {
        var cases = List.of(
                new Header("negative container", -1, 0, 0, 1, 1),
                new Header("negative generation", 7, -1, 0, 1, 1),
                new Header("minimum generation", 7, Long.MIN_VALUE, 0, 1, 1),
                new Header("negative offset", 7, 0, -1, 1, 1),
                new Header("offset beyond total", 7, 0, 2, 1, 1),
                new Header("huge offset", 7, 0, Integer.MAX_VALUE, 1, 1),
                new Header("negative total", 7, 0, 0, -1, 1),
                new Header("total beyond registry", 7, 0, 0, BuiltInRegistries.ITEM.size() + 1, 1),
                new Header("huge total", 7, 0, 0, Integer.MAX_VALUE, 1),
                new Header("negative count", 7, 0, 0, 1, -1),
                new Header("minimum count", 7, 0, 0, 1, Integer.MIN_VALUE),
                new Header("count beyond chunk limit", 7, 0, 0, 129, 129),
                new Header("huge count", 7, 0, 0, BuiltInRegistries.ITEM.size(), Integer.MAX_VALUE),
                new Header("count beyond remaining", 7, 0, 1, 2, 2),
                new Header("nonempty past end", 7, 0, 2, 2, 1),
                new Header("nonempty zero total", 7, 0, 0, 0, 1),
                new Header("empty nonzero total", 7, 0, 0, 1, 0),
                new Header("empty continuation", 7, 0, 1, 1, 0));
        for (var header : cases) {
            var buffer = newBuffer();
            try {
                writeHeader(buffer, header.containerId(), header.generation(), header.offset(), header.total(), header.count());
                int endOfHeader = buffer.writerIndex();
                buffer.writeInt(0x10203040);
                // A tiny header advertising Integer.MAX_VALUE entries must be rejected, not allocated/read.
                assertThrows(DecoderException.class, () -> SingularityCollectionPayload.STREAM_CODEC.decode(buffer), header.name());
                assertEquals(endOfHeader, buffer.readerIndex(), header.name());
                assertEquals(0x10203040, buffer.readInt(), "Item body must remain untouched: " + header.name());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void invalidRegistryIdsAreRejectedRatherThanDefaultingToAir() {
        int validId = BuiltInRegistries.ITEM.getId(Items.DIAMOND);
        for (int invalidId : new int[] {-1, Integer.MIN_VALUE, BuiltInRegistries.ITEM.size(),
                BuiltInRegistries.ITEM.size() + 1, Integer.MAX_VALUE}) {
            var buffer = newBuffer();
            try {
                writeHeader(buffer, 7, 0, 0, 2, 2);
                buffer.writeVarInt(invalidId);
                int endOfInvalidId = buffer.writerIndex();
                buffer.writeVarInt(validId);
                assertThrows(DecoderException.class, () -> SingularityCollectionPayload.STREAM_CODEC.decode(buffer),
                        "Unknown registry id " + invalidId);
                assertEquals(endOfInvalidId, buffer.readerIndex(), "Stop at the first invalid id");
                assertEquals(validId, buffer.readVarInt());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void overlongVarIntsInMetadataAndItemIdsAreRejectedWithoutReadingFollowingData() {
        var valid = newBuffer();
        try {
            SingularityCollectionPayload.STREAM_CODEC.encode(valid,
                    new SingularityCollectionPayload(7, 0, 0, 1, List.of(Items.DIAMOND)));
            var positions = new ArrayList<Integer>();
            positions.add(valid.readerIndex());
            valid.readVarInt();
            valid.readLong();
            for (int field = 0; field < 4; field++) {
                positions.add(valid.readerIndex()); // offset, total, count, item id
                valid.readVarInt();
            }
            for (int position : positions) {
                var malformed = newBuffer();
                try {
                    malformed.writeBytes(valid, 0, position);
                    for (int byteIndex = 0; byteIndex < 6; byteIndex++) malformed.writeByte(0x80);
                    int endOfOverlongValue = malformed.writerIndex();
                    malformed.writeInt(0x10203040);
                    assertThrows(RuntimeException.class, () -> SingularityCollectionPayload.STREAM_CODEC.decode(malformed));
                    assertEquals(endOfOverlongValue, malformed.readerIndex());
                    assertEquals(0x10203040, malformed.readInt());
                } finally {
                    malformed.release();
                }
            }
        } finally {
            valid.release();
        }
    }

    @Test
    void everyTruncatedPrefixOfAFullChunkIsRejected() {
        var valid = newBuffer();
        try {
            SingularityCollectionPayload.STREAM_CODEC.encode(valid,
                    new SingularityCollectionPayload(7, 0, 0, 128, vanillaItems().subList(0, 128)));
            for (int length = 0; length < valid.writerIndex(); length++) {
                var truncated = newBuffer();
                try {
                    truncated.writeBytes(valid, 0, length);
                    assertThrows(RuntimeException.class, () -> SingularityCollectionPayload.STREAM_CODEC.decode(truncated),
                            "Truncated packet length " + length);
                } finally {
                    truncated.release();
                }
            }
        } finally {
            valid.release();
        }
    }

    private static FriendlyByteBuf newBuffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static List<Item> vanillaItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
                .toList();
    }

    private static void writeHeader(FriendlyByteBuf buffer, int containerId, long generation,
            int offset, int total, int count) {
        buffer.writeVarInt(containerId);
        buffer.writeLong(generation);
        buffer.writeVarInt(offset);
        buffer.writeVarInt(total);
        buffer.writeVarInt(count);
    }

    private record Header(String name, int containerId, long generation, int offset, int total, int count) {}
}
