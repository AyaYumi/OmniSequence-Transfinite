package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** Validated, reversible removal followed by the normal resumable construction loop. */
final class TaixuSuspendedUpgrade {
    static void begin(TaixuBlockEntity machine, ServerPlayer player) {
        if (machine.structureVersion() != TaixuStructure.EMBEDDED_VERSION || machine.motion().hasBodies()
                || machine.motion().hasPortable() || machine.operation() != TaixuBlockEntity.Operation.IDLE) return;
        var level = machine.getLevel(); var facing = machine.facing();
        var scan = TaixuStructure.inspect(level, machine.getBlockPos(), facing, p -> false, machine.structureVersion());
        if (!scan.formed()) { machine.embeddingFailed(scan.unloaded() > 0 ? TaixuBlockEntity.Status.UNLOADED : TaixuBlockEntity.Status.CONFLICT, scan.problem()); return; }
        var old = new HashMap<BlockPos, TaixuStructure.Part>();
        var next = new HashMap<BlockPos, TaixuStructure.Part>();
        machine.structureParts().forEach(p -> old.put(p.pos(), p));
        TaixuStructure.parts().forEach(p -> next.put(p.pos(), p));
        // Preflight new sockets as well as removals; never erase an unrelated block.
        for (var part : next.values()) if (!old.containsKey(part.pos())) {
            var pos = machine.worldPos(part);
            if (!level.hasChunkAt(pos)) { machine.embeddingFailed(TaixuBlockEntity.Status.UNLOADED, pos); return; }
            if (!level.getBlockState(pos).isAir()) { machine.embeddingFailed(TaixuBlockEntity.Status.CONFLICT, pos); return; }
            if (!machine.allowed(player, pos)) { machine.embeddingFailed(TaixuBlockEntity.Status.PROTECTED, pos); return; }
        }
        var obsolete = machine.structureParts().stream().filter(p -> !p.equals(next.get(p.pos()))).toList();
        for (var part : obsolete) {
            var pos = machine.worldPos(part);
            if (!machine.allowed(player, pos) || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled()) {
                machine.embeddingFailed(TaixuBlockEntity.Status.PROTECTED, pos); return;
            }
        }
        int[] refunds = new int[TaixuStructure.Type.values().length];
        var snapshots = new ArrayList<BlockSnapshot>();
        for (var part : obsolete) {
            var pos = machine.worldPos(part);
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                Collections.reverse(snapshots); snapshots.forEach(s -> s.restore(Block.UPDATE_CLIENTS));
                machine.embeddingFailed(TaixuBlockEntity.Status.CONFLICT, pos); return;
            }
            snapshots.add(snapshot); refunds[part.type().ordinal()]++;
        }
        // Materials stay owned by the terminal, are reused first, and survive full inventories/reload.
        machine.beginSuspendedConstruction(player, refunds);
        for (var snapshot : snapshots) level.updateNeighborsAt(snapshot.getPos(), Blocks.AIR);
    }
}
