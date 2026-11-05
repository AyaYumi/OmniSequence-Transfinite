package com.atir.molecularmanipulator.building;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** World-owned native NBT snapshots. Neither inventory stacks nor packets contain their block-entity data. */
public final class BuildingArchiveStore {
    public static final int FORMAT = 1, CHUNK_BYTES = 256 * 1024, PAGE_ENTRIES = 64;
    public record Entry(BlockPos offset, BlockState state, @Nullable CompoundTag data) {}
    private final Path root;
    public BuildingArchiveStore(Path root) { this.root = root; }
    public static BuildingArchiveStore forServer(MinecraftServer server) {
        return new BuildingArchiveStore(server.getWorldPath(LevelResource.ROOT).resolve("data/molecularmanipulator/buildings"));
    }
    private Path directory(UUID id) { return root.resolve(id.toString()); }
    public boolean exists(UUID id) { return Files.isRegularFile(directory(id).resolve("manifest.nbt")); }
    public List<UUID> listArchives() throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory).map(path -> {
                try { return UUID.fromString(path.getFileName().toString()); } catch (IllegalArgumentException ignored) { return null; }
            }).filter(Objects::nonNull).filter(this::exists).toList();
        }
    }
    /** Write immutable contents before publishing the manifest that authorizes source removal. */
    public void writeSnapshot(UUID id, List<Entry> entries, HolderLookup.Provider registries) throws IOException {
        var directory = directory(id);
        Files.createDirectories(directory);
        if (Files.exists(directory.resolve("index.nbt"))) throw new IOException("Snapshot already exists: " + id);
        var index = new CompoundTag();
        index.putInt("format", FORMAT);
        var list = new ListTag();
        long total = 0;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var descriptor = new CompoundTag();
            descriptor.putLong("offset", entry.offset().asLong());
            descriptor.put("state", NbtUtils.writeBlockState(entry.state()));
            if (entry.data() != null) {
                var chunks = new ChunkOutput(directory, i);
                try (var output = new DataOutputStream(chunks)) { NbtIo.write(entry.data(), output); }
                descriptor.putInt("chunks", chunks.count);
                descriptor.putLong("bytes", chunks.total);
                total = Math.addExact(total, chunks.total);
            }
            list.add(descriptor);
        }
        index.put("entries", list);
        index.putLong("native_bytes", total);
        atomicNbt(directory.resolve("index.nbt"), index);
    }
    public CompoundTag readIndex(UUID id) throws IOException {
        var index = localNbt(directory(id).resolve("index.nbt"));
        if (index.getInt("format") != FORMAT || !index.contains("entries", Tag.TAG_LIST)) throw new IOException("Unsupported or incomplete building archive index");
        var positions = new HashSet<Long>();
        for (var element : index.getList("entries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            if (!entry.contains("offset", Tag.TAG_LONG) || !entry.contains("state", Tag.TAG_COMPOUND)
                    || !positions.add(entry.getLong("offset"))) throw new IOException("Invalid or duplicate archived block position");
            if (entry.contains("chunks") && (entry.getLong("bytes") <= 0
                    || entry.getInt("chunks") != Math.ceilDiv(entry.getLong("bytes"), CHUNK_BYTES))) throw new IOException("Invalid native byte chunk index");
        }
        return index;
    }
    public List<Entry> readLayout(UUID id, HolderLookup.Provider registries) throws IOException {
        var descriptors = readIndex(id).getList("entries", Tag.TAG_COMPOUND);
        var entries = new ArrayList<Entry>(descriptors.size());
        for (int i = 0; i < descriptors.size(); i++) entries.add(decode(descriptors.getCompound(i), registries, null));
        return List.copyOf(entries);
    }
    public Entry readEntry(UUID id, int index, HolderLookup.Provider registries) throws IOException {
        return readEntry(id, index, readIndex(id).getList("entries", Tag.TAG_COMPOUND).getCompound(index), registries);
    }
    public Entry readEntry(UUID id, int index, CompoundTag descriptor, HolderLookup.Provider registries) throws IOException {
        CompoundTag data = null;
        int count = descriptor.getInt("chunks");
        if (count > 0) {
            var chunks = new ChunkInput(directory(id), index, count);
            try (var input = new DataInputStream(chunks)) {
                data = NbtIo.read(input, NbtAccounter.unlimitedHeap());
                if (chunks.read() != -1 || chunks.total != descriptor.getLong("bytes")) throw new IOException("Native block entity byte length differs from its index");
            }
        }
        return decode(descriptor, registries, data);
    }
    private static Entry decode(CompoundTag descriptor, HolderLookup.Provider registries, CompoundTag data) {
        return new Entry(BlockPos.of(descriptor.getLong("offset")), NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK), descriptor.getCompound("state")), data);
    }
    /** Detail access is explicit and bounded; callers choose one page instead of loading the whole building. */
    public List<Entry> readPage(UUID id, int page, HolderLookup.Provider registries) throws IOException {
        var descriptors = readIndex(id).getList("entries", Tag.TAG_COMPOUND);
        if (page < 0 || page >= Math.ceilDiv(descriptors.size(), PAGE_ENTRIES)) throw new IndexOutOfBoundsException(page);
        var result = new ArrayList<Entry>();
        for (int i = page * PAGE_ENTRIES; i < Math.min(descriptors.size(), (page + 1) * PAGE_ENTRIES); i++)
            result.add(readEntry(id, i, descriptors.getCompound(i), registries));
        return result;
    }
    public List<Entry> readSnapshot(UUID id, HolderLookup.Provider registries) throws IOException {
        var descriptors = readIndex(id).getList("entries", Tag.TAG_COMPOUND);
        var result = new ArrayList<Entry>(descriptors.size());
        for (int i = 0; i < descriptors.size(); i++) result.add(readEntry(id, i, descriptors.getCompound(i), registries));
        return result;
    }
    public CompoundTag readManifest(UUID id) throws IOException {
        var manifest = localNbt(directory(id).resolve("manifest.nbt"));
        if (manifest.getInt("archive_format") != FORMAT) throw new IOException("Unsupported building archive format");
        return manifest;
    }
    public void writeManifest(UUID id, CompoundTag manifest) throws IOException {
        manifest.putInt("archive_format", FORMAT);
        atomicNbt(directory(id).resolve("manifest.nbt"), manifest);
    }
    public static CompoundTag localNbt(Path path) throws IOException { return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()); }
    private static void atomicNbt(Path target, CompoundTag tag) throws IOException {
        Files.createDirectories(target.getParent());
        var temporary = target.resolveSibling(target.getFileName() + ".writing");
        NbtIo.writeCompressed(tag, temporary);
        force(temporary);
        replace(temporary, target);
    }
    private static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private static void replace(Path temporary, Path target) throws IOException {
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static Path chunk(Path directory, int entry, int chunk) { return directory.resolve("entry-" + entry + "-" + chunk + ".nbt.gz"); }
    /** Bound uncompressed serialized bytes, including a single block entity larger than a normal NBT network quota. */
    private static final class ChunkOutput extends OutputStream {
        final Path directory;
        final int entry;
        final byte[] buffer = new byte[CHUNK_BYTES];
        int used, count;
        long total;
        ChunkOutput(Path directory, int entry) { this.directory = directory; this.entry = entry; }
        @Override public void write(int value) throws IOException {
            buffer[used++] = (byte) value; total++;
            if (used == buffer.length) flushChunk();
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            while (length > 0) {
                int amount = Math.min(length, buffer.length - used);
                System.arraycopy(data, offset, buffer, used, amount);
                used += amount; offset += amount; length -= amount; total += amount;
                if (used == buffer.length) flushChunk();
            }
        }
        private void flushChunk() throws IOException {
            if (used == 0) return;
            var target = chunk(directory, entry, count);
            var temporary = target.resolveSibling(target.getFileName() + ".writing");
            try (var gzip = new GZIPOutputStream(Files.newOutputStream(temporary))) { gzip.write(buffer, 0, used); }
            force(temporary); replace(temporary, target); count++; used = 0;
        }
        @Override public void close() throws IOException { flushChunk(); }
    }
    private static final class ChunkInput extends InputStream {
        final Path directory;
        final int entry, count;
        int next;
        long total;
        InputStream current;
        ChunkInput(Path directory, int entry, int count) { this.directory = directory; this.entry = entry; this.count = count; }
        private boolean advance() throws IOException {
            if (current != null) current.close();
            if (next == count) { current = null; return false; }
            current = new GZIPInputStream(Files.newInputStream(chunk(directory, entry, next++)));
            return true;
        }
        @Override public int read() throws IOException {
            while (true) {
                if (current == null && !advance()) return -1;
                int value = current.read(); if (value != -1) { total++; return value; }
                if (!advance()) return -1;
            }
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            while (true) {
                if (current == null && !advance()) return -1;
                int amount = current.read(buffer, offset, length); if (amount != -1) { total += amount; return amount; }
                if (!advance()) return -1;
            }
        }
        @Override public void close() throws IOException { if (current != null) current.close(); }
    }
}
