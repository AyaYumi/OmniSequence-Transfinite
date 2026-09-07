package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/** Persistent, fully ticking tickets, owned separately by each multiblock controller. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class MultiblockChunkLoading {
    public static final TicketController CONTROLLER = new TicketController(
            MolecularManipulator.id("multiblock"), MultiblockChunkLoading::validateTickets);
    private static final Map<ServerLevel, Map<BlockPos, Set<ChunkPos>>> ACTIVE = new WeakHashMap<>();

    private MultiblockChunkLoading() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static Set<ChunkPos> rectangle(BlockPos first, BlockPos opposite) {
        var chunks = new HashSet<ChunkPos>();
        for (int x = Math.min(first.getX(), opposite.getX()) >> 4;
                x <= Math.max(first.getX(), opposite.getX()) >> 4; x++) {
            for (int z = Math.min(first.getZ(), opposite.getZ()) >> 4;
                    z <= Math.max(first.getZ(), opposite.getZ()) >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    public static Set<ChunkPos> requiredChunks(BlockEntity machine) {
        if (machine instanceof MolecularCenterBlockEntity center) return center.getChunkLoadingChunks();
        if (machine instanceof OmniComputationCoreBlockEntity omni) return omni.getChunkLoadingChunks();
        if (machine instanceof MatterFabricationBlockEntity matter) return matter.getChunkLoadingChunks();
        return Set.of();
    }

    public static void maintain(BlockEntity machine) {
        if (!(machine.getLevel() instanceof ServerLevel level) || machine.isRemoved()) return;
        var required = requiredChunks(machine);
        var owner = machine.getBlockPos();
        if (!required.isEmpty()) {
            required = new HashSet<>(required);
            required.add(new ChunkPos(owner));
        }
        var existing = ownedChunks(level, owner);
        if (existing.equals(required)) return;
        if (required.isEmpty()) {
            release(level, owner);
            return;
        }
        ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>()).put(owner.immutable(), Set.copyOf(required));
        // Acquire the new footprint before releasing an old layout's tickets.
        for (var chunk : required) {
            if (!existing.contains(chunk)) CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, true, true);
        }
        for (var chunk : existing) {
            if (!required.contains(chunk)) CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, false, true);
        }
    }

    public static Set<ChunkPos> ownedChunks(ServerLevel level, BlockPos owner) {
        return ACTIVE.getOrDefault(level, Map.of()).getOrDefault(owner, Set.of());
    }

    /** Only call for actual block removal; chunk unload and server shutdown retain tickets. */
    public static void release(ServerLevel level, BlockPos owner) {
        var chunks = ownedChunks(level, owner);
        var owners = ACTIVE.get(level);
        if (owners != null) {
            owners.remove(owner);
            if (owners.isEmpty()) ACTIVE.remove(level);
        }
        for (var chunk : chunks) {
            CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, false, true);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        // NeoForge saves and restores the tickets. Only drop the in-memory index.
        if (event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level);
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        for (var entry : helper.getBlockTickets().entrySet()) {
            var machine = level.getBlockEntity(entry.getKey());
            if (!(machine instanceof MolecularCenterBlockEntity
                    || machine instanceof OmniComputationCoreBlockEntity
                    || machine instanceof MatterFabricationBlockEntity)) {
                helper.removeAllTickets(entry.getKey());
            } else {
                // Runtime formation has not been checked yet. Keep the saved ticking
                // footprint until all its chunks are restored, then normal ticks reconcile it.
                for (long chunk : entry.getValue().nonTicking()) helper.removeTicket(entry.getKey(), chunk, false);
                var chunks = new HashSet<ChunkPos>();
                for (long chunk : entry.getValue().ticking()) chunks.add(new ChunkPos(chunk));
                ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>()).put(entry.getKey(), Set.copyOf(chunks));
            }
        }
        for (var owner : helper.getEntityTickets().keySet()) helper.removeAllTickets(owner);
    }
}
