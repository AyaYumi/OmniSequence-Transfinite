package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryFoundation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * One persistent visual owner per pedestal, not a ticking block entity per foundation block.
 * Inactive records retain only chunks still awaiting restoration, so removing a controller
 * while a foundation chunk is unloaded cannot strand permanently invisible building blocks.
 */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class EntropyArmillaryFoundationVisuals extends SavedData {
    private static final String NAME = "molecularmanipulator_armillary_foundation_visuals";
    private static final Factory<EntropyArmillaryFoundationVisuals> FACTORY =
            new Factory<>(EntropyArmillaryFoundationVisuals::new, EntropyArmillaryFoundationVisuals::load);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<ServerLevel, Set<ChunkPos>> LOADED_CHUNKS = new WeakHashMap<>();

    private static final class Owner {
        final BlockPos core;
        boolean hidden;
        final Set<ChunkPos> pending;

        Owner(BlockPos core, boolean hidden, Set<ChunkPos> pending) {
            this.core = core.immutable();
            this.hidden = hidden;
            this.pending = new HashSet<>(pending);
        }
    }

    private final Map<BlockPos, Owner> owners = new HashMap<>();

    public EntropyArmillaryFoundationVisuals() {}

    private static EntropyArmillaryFoundationVisuals get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Called only after core/base validation, or to undo an earlier visual claim. */
    public static void setHidden(ServerLevel level, BlockPos core, boolean hidden) {
        var data = get(level);
        var owner = data.owners.get(core);
        if (hidden) {
            if (owner == null || !owner.hidden) {
                owner = new Owner(core, true, EntropyArmillaryFoundation.restorationChunksFor(core));
                data.owners.put(owner.core, owner);
                data.setDirty();
            }
        } else {
            if (owner == null) return;
            if (owner.hidden) {
                owner.hidden = false;
                owner.pending.addAll(EntropyArmillaryFoundation.restorationChunksFor(core));
                data.setDirty();
            }
        }
        data.reconcile(level, owner, null);
    }

    /** Overlapping physical foundations stay hidden if any formed pedestal still needs them. */
    boolean hiddenAt(BlockPos pos) {
        for (var owner : owners.values()) {
            if (owner.hidden && EntropyArmillaryFoundation.materialAt(pos.subtract(owner.core)) != null) return true;
        }
        return false;
    }

    private void reconcile(ServerLevel level, Owner owner, @Nullable ChunkPos onlyChunk) {
        var restored = new HashSet<ChunkPos>();
        // Include the previous solid terraces so upgrades cannot leave their
        // obsolete formed=true blocks invisible outside the new ring frame.
        for (var offset : EntropyArmillaryFoundation.restorationOffsets()) {
            var pos = owner.core.offset(offset);
            var chunk = new ChunkPos(pos);
            if (onlyChunk != null && !onlyChunk.equals(chunk) || !owner.pending.contains(chunk)
                    || !level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof EntropyArmillaryFoundationBlock) {
                boolean hidden = hiddenAt(pos);
                if (state.getValue(EntropyArmillaryFoundationBlock.FORMED) != hidden) {
                    level.setBlock(pos, state.setValue(EntropyArmillaryFoundationBlock.FORMED, hidden), FLAGS);
                }
            }
            if (!owner.hidden) restored.add(chunk);
        }
        if (!restored.isEmpty()) {
            owner.pending.removeAll(restored);
            if (owner.pending.isEmpty()) owners.remove(owner.core);
            setDirty();
        }
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Promotion may fire before block entities are installed, even on the
        // server thread (where Server.execute could run inline). Wait for the
        // level's post-tick instead of treating a not-yet-installed core as air.
        synchronized (LOADED_CHUNKS) {
            LOADED_CHUNKS.computeIfAbsent(level, key -> new HashSet<>()).add(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void levelTicked(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<ChunkPos> chunks;
        synchronized (LOADED_CHUNKS) {
            chunks = LOADED_CHUNKS.remove(level);
        }
        if (chunks != null) {
            var data = get(level);
            for (var chunk : chunks) data.chunkReady(level, chunk);
        }
    }

    void chunkReady(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) return;
        for (var owner : List.copyOf(owners.values())) {
            boolean controllerChunk = new ChunkPos(owner.core).equals(chunk);
            if (!controllerChunk && !owner.pending.contains(chunk)) continue;
            if (owner.hidden && level.hasChunkAt(owner.core)) {
                if (!(level.getBlockEntity(owner.core) instanceof StarComputationEngineCoreBlockEntity core)
                        || !core.isArmillary() || !core.isFormed()) {
                    owner.hidden = false;
                    owner.pending.addAll(EntropyArmillaryFoundation.restorationChunksFor(owner.core));
                    setDirty();
                    reconcile(level, owner, null);
                    continue;
                }
            }
            // Loading a missing controller's chunk also repairs foundation chunks
            // that loaded earlier while that controller was still unknown.
            reconcile(level, owner, controllerChunk ? null : chunk);
        }
    }

    public static EntropyArmillaryFoundationVisuals load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new EntropyArmillaryFoundationVisuals();
        for (var value : tag.getList("owners", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (!entry.contains("core", Tag.TAG_LONG)) continue;
            var core = BlockPos.of(entry.getLong("core"));
            boolean hidden = entry.getBoolean("hidden");
            var footprint = EntropyArmillaryFoundation.restorationChunksFor(core);
            var pending = new HashSet<ChunkPos>();
            if (hidden) pending.addAll(footprint);
            else for (long packed : entry.getLongArray("pending")) {
                var chunk = new ChunkPos(packed);
                if (footprint.contains(chunk)) pending.add(chunk);
            }
            if (!pending.isEmpty()) data.owners.put(core, new Owner(core, hidden, pending));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        owners.values().stream().sorted(java.util.Comparator.comparingLong(o -> o.core.asLong())).forEach(owner -> {
            var entry = new CompoundTag();
            entry.putLong("core", owner.core.asLong());
            entry.putBoolean("hidden", owner.hidden);
            if (!owner.hidden) entry.putLongArray("pending", owner.pending.stream().mapToLong(ChunkPos::toLong).sorted().toArray());
            list.add(entry);
        });
        tag.put("owners", list);
        return tag;
    }
}
