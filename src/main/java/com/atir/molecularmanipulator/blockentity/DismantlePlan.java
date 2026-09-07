package com.atir.molecularmanipulator.blockentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A snapshot of real, caller-owned blocks, traversed one complete layer at a time. */
public final class DismantlePlan {
    public record Entry(BlockPos pos, Block block) {
        public Entry {
            pos = Objects.requireNonNull(pos).immutable();
            Objects.requireNonNull(block);
        }

        public boolean matches(BlockState state) {
            return !state.isAir() && state.is(block);
        }
    }

    private final List<Entry> entries;
    private final List<ChunkRequirement> requiredChunks;
    private int cursor;

    private DismantlePlan(List<Entry> entries, int cursor) {
        this.entries = List.copyOf(entries);
        this.cursor = Math.max(0, Math.min(cursor, entries.size()));
        var chunks = new LinkedHashMap<Long, ChunkRequirement>();
        for (int index = 0; index < entries.size(); index++) {
            var pos = entries.get(index).pos();
            chunks.put(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), new ChunkRequirement(pos, index));
        }
        requiredChunks = List.copyOf(chunks.values());
    }

    /** Callers supply only blocks belonging to the selected structure, never historical search areas. */
    public static DismantlePlan create(Collection<Entry> candidates) {
        var byPosition = new LinkedHashMap<BlockPos, Entry>();
        for (var entry : candidates) {
            if (!entry.block().defaultBlockState().isAir()) {
                byPosition.putIfAbsent(entry.pos(), entry);
            }
        }
        return new DismantlePlan(orderedPositions(byPosition.keySet()).stream()
                .map(byPosition::get).toList(), 0);
    }

    // Package-private so the spatial order can be tested without bootstrapping block registries.
    static List<BlockPos> orderedPositions(Collection<BlockPos> positions) {
        var ordered = new ArrayList<>(positions.stream().map(BlockPos::immutable).distinct().toList());
        ordered.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        int row = 0;
        for (int start = 0; start < ordered.size();) {
            var first = ordered.get(start);
            if (start == 0 || first.getY() != ordered.get(start - 1).getY()) {
                row = 0;
            }
            int end = start + 1;
            while (end < ordered.size() && ordered.get(end).getY() == first.getY()
                    && ordered.get(end).getZ() == first.getZ()) {
                end++;
            }
            if ((row & 1) != 0) {
                Collections.reverse(ordered.subList(start, end));
            }
            row++;
            start = end;
        }
        return List.copyOf(ordered);
    }

    public boolean isComplete() {
        return cursor >= entries.size();
    }

    public Entry current() {
        return entries.get(cursor);
    }

    public void advance() {
        if (!isComplete()) cursor++;
    }

    public int total() {
        return entries.size();
    }

    public int completed() {
        return cursor;
    }

    public int remaining() {
        return entries.size() - cursor;
    }

    public Set<ChunkPos> remainingChunks() {
        var result = new HashSet<ChunkPos>();
        for (var chunk : requiredChunks) {
            if (chunk.lastTargetIndex() >= cursor) result.add(new ChunkPos(chunk.pos()));
        }
        return result;
    }

    public boolean remainingChunksLoaded(Level level) {
        // Check each still-needed chunk once, not every remaining block on every tick.
        for (var chunk : requiredChunks) {
            if (chunk.lastTargetIndex() >= cursor && !level.hasChunkAt(chunk.pos())) return false;
        }
        return true;
    }

    private record ChunkRequirement(BlockPos pos, int lastTargetIndex) {
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt("cursor", cursor);
        var saved = new ListTag();
        for (var entry : entries) {
            var target = new CompoundTag();
            target.putLong("pos", entry.pos().asLong());
            target.putString("block", BuiltInRegistries.BLOCK.getKey(entry.block()).toString());
            saved.add(target);
        }
        tag.put("entries", saved);
        return tag;
    }

    /** Preserve the saved traversal order and cursor; never replay a removed prefix after reload. */
    public static DismantlePlan load(CompoundTag tag) {
        if (tag == null || tag.getInt("version") != 1) {
            return new DismantlePlan(List.of(), 0);
        }
        var saved = tag.getList("entries", Tag.TAG_COMPOUND);
        int savedCursor = Math.max(0, Math.min(tag.getInt("cursor"), saved.size()));
        int restoredCursor = 0;
        var entries = new ArrayList<Entry>();
        var seen = new HashSet<BlockPos>();
        for (int index = 0; index < saved.size(); index++) {
            var target = saved.getCompound(index);
            if (!target.contains("pos", Tag.TAG_LONG) || !target.contains("block", Tag.TAG_STRING)) continue;
            var id = ResourceLocation.tryParse(target.getString("block"));
            var block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null || block.defaultBlockState().isAir()) continue;
            var pos = BlockPos.of(target.getLong("pos"));
            if (!seen.add(pos)) continue;
            entries.add(new Entry(pos, block));
            if (index < savedCursor) restoredCursor++;
        }
        return new DismantlePlan(entries, restoredCursor);
    }
}
