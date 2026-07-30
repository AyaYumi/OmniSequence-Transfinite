package com.atir.molecularmanipulator.network;

import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.MolecularManipulator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A bounded, chunked server-to-client projection of the searchable keys in the
 * molecular center's pattern inventory.
 */
public record PatternSearchIndexPayload(
        int containerId,
        int revision,
        boolean reset,
        boolean complete,
        List<Entry> entries) {
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_ENTRIES_PER_PACKET = 64;
    private static final int MAX_KEYS_PER_ENTRY_PART = 64;
    private static final int MAX_KEYS_PER_PACKET = 256;
    private static final int MAX_ENTRY_PART_BYTES = 64 * 1024;
    private static final int MAX_PACKET_KEY_BYTES = 256 * 1024;
    private static final int MAX_SINGLE_KEY_BYTES = 64 * 1024;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MolecularManipulator.id("pattern_search"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public PatternSearchIndexPayload {
        entries = List.copyOf(entries);
    }

    public record Entry(int sourceSlot, List<AEKey> keys) {
        public Entry {
            keys = List.copyOf(keys);
        }
    }

    public static void register() {
        CHANNEL.registerMessage(
                0,
                PatternSearchIndexPayload.class,
                PatternSearchIndexPayload::encode,
                PatternSearchIndexPayload::decode,
                PatternSearchIndexPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendChunked(ServerPlayer player, int containerId, int revision,
            List<Entry> index) {
        var entryParts = new ArrayList<SizedEntry>();
        for (var entry : index) {
            if (entry.keys().isEmpty()) {
                entryParts.add(new SizedEntry(entry, 0));
                continue;
            }
            var partKeys = new ArrayList<AEKey>(MAX_KEYS_PER_ENTRY_PART);
            int partBytes = 0;
            for (var originalKey : entry.keys()) {
                var sizedKey = makeLightweight(originalKey);
                if (sizedKey == null) {
                    MolecularManipulator.LOGGER.debug(
                            "Skipping an oversized AE key in molecular center search slot {}",
                            entry.sourceSlot());
                    continue;
                }
                if (!partKeys.isEmpty()
                        && (partKeys.size() >= MAX_KEYS_PER_ENTRY_PART
                                || partBytes + sizedKey.encodedBytes() > MAX_ENTRY_PART_BYTES)) {
                    entryParts.add(new SizedEntry(
                            new Entry(entry.sourceSlot(), partKeys),
                            partBytes));
                    partKeys = new ArrayList<>(MAX_KEYS_PER_ENTRY_PART);
                    partBytes = 0;
                }
                partKeys.add(sizedKey.key());
                partBytes += sizedKey.encodedBytes();
            }
            if (!partKeys.isEmpty()) {
                entryParts.add(new SizedEntry(
                        new Entry(entry.sourceSlot(), partKeys),
                        partBytes));
            }
        }

        var chunks = new ArrayList<List<Entry>>();
        var chunk = new ArrayList<Entry>(MAX_ENTRIES_PER_PACKET);
        int chunkKeys = 0;
        int chunkBytes = 0;
        for (var sizedEntry : entryParts) {
            var entry = sizedEntry.entry();
            int entryKeys = entry.keys().size();
            if (!chunk.isEmpty()
                    && (chunk.size() >= MAX_ENTRIES_PER_PACKET
                            || chunkKeys + entryKeys > MAX_KEYS_PER_PACKET
                            || chunkBytes + sizedEntry.encodedBytes() > MAX_PACKET_KEY_BYTES)) {
                chunks.add(List.copyOf(chunk));
                chunk.clear();
                chunkKeys = 0;
                chunkBytes = 0;
            }
            chunk.add(entry);
            chunkKeys += entryKeys;
            chunkBytes += sizedEntry.encodedBytes();
        }
        if (!chunk.isEmpty()) {
            chunks.add(List.copyOf(chunk));
        }
        if (chunks.isEmpty()) {
            chunks.add(List.of());
        }

        for (int indexNumber = 0; indexNumber < chunks.size(); indexNumber++) {
            var payload = new PatternSearchIndexPayload(
                    containerId,
                    revision,
                    indexNumber == 0,
                    indexNumber + 1 == chunks.size(),
                    chunks.get(indexNumber));
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    private static SizedKey makeLightweight(AEKey originalKey) {
        if (originalKey == null) {
            return null;
        }
        AEKey key = originalKey.dropSecondary();
        int encodedBytes = encodedSize(key);
        if (encodedBytes < 0 || encodedBytes > MAX_SINGLE_KEY_BYTES) {
            return null;
        }
        return new SizedKey(key, encodedBytes);
    }

    private static int encodedSize(AEKey key) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AEKey.writeKey(buffer, key);
            return buffer.readableBytes();
        } catch (RuntimeException exception) {
            return -1;
        } finally {
            buffer.release();
        }
    }

    private static void encode(PatternSearchIndexPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeInt(payload.revision());
        buffer.writeBoolean(payload.reset());
        buffer.writeBoolean(payload.complete());
        buffer.writeVarInt(payload.entries().size());
        for (var entry : payload.entries()) {
            buffer.writeVarInt(entry.sourceSlot());
            buffer.writeVarInt(entry.keys().size());
            for (var key : entry.keys()) {
                AEKey.writeKey(buffer, key);
            }
        }
    }

    private static PatternSearchIndexPayload decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int revision = buffer.readInt();
        boolean reset = buffer.readBoolean();
        boolean complete = buffer.readBoolean();
        int entryCount = readBoundedCount(buffer, MAX_ENTRIES_PER_PACKET, "entries");
        var entries = new ArrayList<Entry>(entryCount);
        int totalKeys = 0;
        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            int sourceSlot = buffer.readVarInt();
            int keyCount = readBoundedCount(buffer, MAX_KEYS_PER_ENTRY_PART, "keys");
            totalKeys += keyCount;
            if (totalKeys > MAX_KEYS_PER_PACKET) {
                throw new DecoderException("Pattern search index packet contains too many keys");
            }
            var keys = new ArrayList<AEKey>(keyCount);
            for (int keyIndex = 0; keyIndex < keyCount; keyIndex++) {
                AEKey key = AEKey.readKey(buffer);
                if (key == null) {
                    throw new DecoderException("Pattern search index contains an unknown AE key");
                }
                keys.add(key);
            }
            entries.add(new Entry(sourceSlot, keys));
        }
        return new PatternSearchIndexPayload(containerId, revision, reset, complete, entries);
    }

    private static int readBoundedCount(FriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new DecoderException("Invalid pattern search " + label + " count: " + count);
        }
        return count;
    }

    private static void handle(PatternSearchIndexPayload payload,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.atir.molecularmanipulator.client.PatternSearchIndexClientHandler
                        .handle(payload)));
        context.setPacketHandled(true);
    }

    private record SizedEntry(Entry entry, int encodedBytes) {
    }

    private record SizedKey(AEKey key, int encodedBytes) {
    }
}
