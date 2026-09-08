package com.atir.molecularmanipulator.network;

import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.MolecularManipulator;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One client-bound chunk of a decoded pattern search index.
 */
public record PatternSearchIndexPayload(
        int containerId,
        long generation,
        int revision,
        boolean reset,
        boolean complete,
        List<PatternSearchIndexEntry> entries)
 {
    public static final int MAX_ENTRIES_PER_CHUNK = 64;
    public static final int MAX_KEYS_PER_LIST = 256;
    public static final int MAX_TOTAL_KEYS_PER_CHUNK = 4096;
    public static final int MAX_SOURCE_SLOT = 1_000_000;

    public static final com.appliedenhancements.network.PacketCodec<FriendlyByteBuf, PatternSearchIndexPayload> STREAM_CODEC =
            com.appliedenhancements.network.PacketCodec.of(PatternSearchIndexPayload::encode, PatternSearchIndexPayload::decode);

    public PatternSearchIndexPayload {
        if (containerId < 0) {
            throw new IllegalArgumentException("containerId must be non-negative");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        validateEntries(entries);
    }

    public PatternSearchIndexPayload(PatternSearchIndexChunk chunk) {
        this(
                chunk.containerId(),
                chunk.generation(),
                chunk.revision(),
                chunk.reset(),
                chunk.complete(),
                chunk.entries());
    }

    public PatternSearchIndexChunk toChunk() {
        return new PatternSearchIndexChunk(
                containerId,
                generation,
                revision,
                reset,
                complete,
                entries);
    }

    /**
     * Splits a complete snapshot into bounded payloads. The first payload resets the receiver and the last
     * payload marks it complete.
     */
    public static List<PatternSearchIndexPayload> createChunks(
            int containerId,
            long generation,
            int firstRevision,
            List<PatternSearchIndexEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return List.of(new PatternSearchIndexPayload(
                    containerId, generation, firstRevision, true, true, List.of()));
        }

        var partitions = new ArrayList<List<PatternSearchIndexEntry>>();
        var current = new ArrayList<PatternSearchIndexEntry>();
        int currentKeyCount = 0;
        for (var entry : entries) {
            validateEntry(entry);
            int entryKeyCount = entry.keyCount();
            if (!current.isEmpty()
                    && (current.size() >= MAX_ENTRIES_PER_CHUNK
                            || currentKeyCount + entryKeyCount > MAX_TOTAL_KEYS_PER_CHUNK)) {
                partitions.add(List.copyOf(current));
                current.clear();
                currentKeyCount = 0;
            }
            current.add(entry);
            currentKeyCount += entryKeyCount;
        }
        if (!current.isEmpty()) {
            partitions.add(List.copyOf(current));
        }

        var payloads = new ArrayList<PatternSearchIndexPayload>(partitions.size());
        for (int index = 0; index < partitions.size(); index++) {
            int revision = Math.addExact(firstRevision, index);
            payloads.add(new PatternSearchIndexPayload(
                    containerId,
                    generation,
                    revision,
                    index == 0,
                    index == partitions.size() - 1,
                    partitions.get(index)));
        }
        return List.copyOf(payloads);
    }

    private static final String PROTOCOL = "2.0.0-forge-1";
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL =
            net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
                    MolecularManipulator.id("pattern_search"), () -> PROTOCOL,
                    PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.messageBuilder(PatternSearchIndexPayload.class, 0,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> encode(buffer, payload))
                .decoder(PatternSearchIndexPayload::decode)
                .consumerMainThread((payload, context) -> {
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> ClientHandler.accept(payload));
                    context.get().setPacketHandled(true);
                }).add();
    }

    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player,
            PatternSearchIndexPayload payload) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static final class ClientHandler {
        static void accept(PatternSearchIndexPayload payload) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) handle(payload, player);
        }
    }

    private static void encode(FriendlyByteBuf buffer, PatternSearchIndexPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeLong(payload.generation);
        buffer.writeVarInt(payload.revision);
        buffer.writeBoolean(payload.reset);
        buffer.writeBoolean(payload.complete);
        buffer.writeVarInt(payload.entries.size());
        for (var entry : payload.entries) {
            buffer.writeVarInt(entry.sourceSlot());
            writeKeys(buffer, entry.inputs());
            writeKeys(buffer, entry.outputs());
        }
    }

    private static PatternSearchIndexPayload decode(FriendlyByteBuf buffer) {
        int containerId = readBoundedVarInt(buffer, "containerId", Integer.MAX_VALUE);
        long generation = buffer.readLong();
        if (generation < 0) {
            throw new DecoderException("Pattern search generation must be non-negative");
        }
        int revision = readBoundedVarInt(buffer, "revision", Integer.MAX_VALUE);
        boolean reset = buffer.readBoolean();
        boolean complete = buffer.readBoolean();
        int entryCount = readBoundedVarInt(buffer, "entry count", MAX_ENTRIES_PER_CHUNK);

        var entries = new ArrayList<PatternSearchIndexEntry>(entryCount);
        var budget = new KeyDecodeBudget(MAX_TOTAL_KEYS_PER_CHUNK);
        for (int index = 0; index < entryCount; index++) {
            int sourceSlot = readBoundedVarInt(buffer, "source slot", MAX_SOURCE_SLOT);
            var inputs = readKeys(buffer, budget, "input key count");
            var outputs = readKeys(buffer, budget, "output key count");
            entries.add(new PatternSearchIndexEntry(sourceSlot, inputs, outputs));
        }
        return new PatternSearchIndexPayload(
                containerId,
                generation,
                revision,
                reset,
                complete,
                entries);
    }

    private static void writeKeys(FriendlyByteBuf buffer, List<AEKey> keys) {
        buffer.writeVarInt(keys.size());
        for (var key : keys) {
            AEKey.writeKey(buffer, key);
        }
    }

    private static List<AEKey> readKeys(
            FriendlyByteBuf buffer,
            KeyDecodeBudget budget,
            String countName) {
        int count = readBoundedVarInt(buffer, countName, MAX_KEYS_PER_LIST);
        budget.consume(count);
        var keys = new ArrayList<AEKey>(count);
        for (int index = 0; index < count; index++) {
            var key = AEKey.readKey(buffer);
            if (key == null) {
                throw new DecoderException("Unknown AEKey type in pattern search index");
            }
            keys.add(key);
        }
        return List.copyOf(keys);
    }

    private static int readBoundedVarInt(
            FriendlyByteBuf buffer,
            String name,
            int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) {
            throw new DecoderException(
                    "Invalid pattern search " + name + ": " + value + " (maximum " + maximum + ")");
        }
        return value;
    }

    private static void validateEntries(List<PatternSearchIndexEntry> entries) {
        if (entries.size() > MAX_ENTRIES_PER_CHUNK) {
            throw new IllegalArgumentException(
                    "Too many pattern search entries: " + entries.size());
        }
        int totalKeys = 0;
        for (var entry : entries) {
            validateEntry(entry);
            totalKeys = Math.addExact(totalKeys, entry.keyCount());
            if (totalKeys > MAX_TOTAL_KEYS_PER_CHUNK) {
                throw new IllegalArgumentException(
                        "Too many AEKeys in pattern search chunk: " + totalKeys);
            }
        }
    }

    private static void validateEntry(PatternSearchIndexEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.sourceSlot() > MAX_SOURCE_SLOT) {
            throw new IllegalArgumentException(
                    "Pattern search source slot exceeds " + MAX_SOURCE_SLOT);
        }
        if (entry.inputs().size() > MAX_KEYS_PER_LIST
                || entry.outputs().size() > MAX_KEYS_PER_LIST) {
            throw new IllegalArgumentException(
                    "Pattern search entry exceeds " + MAX_KEYS_PER_LIST + " keys per side");
        }
    }

    private static void handle(PatternSearchIndexPayload payload, net.minecraft.world.entity.player.Player player) {
        var menu = player.containerMenu;
        if (menu.containerId == payload.containerId
                && menu instanceof PatternSearchIndexReceiver receiver) {
            receiver.acceptPatternSearchIndexChunk(payload.toChunk());
        }
    }


    private static final class KeyDecodeBudget {
        private int remaining;

        private KeyDecodeBudget(int remaining) {
            this.remaining = remaining;
        }

        private void consume(int count) {
            if (count > remaining) {
                throw new DecoderException(
                        "Pattern search chunk exceeds " + MAX_TOTAL_KEYS_PER_CHUNK + " total AEKeys");
            }
            remaining -= count;
        }
    }
}
