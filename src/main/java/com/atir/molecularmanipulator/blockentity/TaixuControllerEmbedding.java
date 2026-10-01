package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** Moves only authored terminal parts; the rest of the building stays at its original world coordinates. */
final class TaixuControllerEmbedding {
    private record Change(BlockPos pos, TaixuStructure.Part oldPart, TaixuStructure.Part newPart) { }

    static void move(TaixuBlockEntity machine, ServerPlayer player) {
        if (machine.structureVersion() != TaixuStructure.LEGACY_VERSION || machine.motion().hasBodies()
                || machine.motion().hasPortable() || machine.operation() != TaixuBlockEntity.Operation.IDLE) return;
        var level = machine.getLevel();
        var facing = machine.facing();
        var scan = TaixuStructure.inspect(level, machine.getBlockPos(), facing, part -> false, machine.structureVersion());
        if (!scan.formed()) {
            machine.embeddingFailed(scan.unloaded() > 0 ? TaixuBlockEntity.Status.UNLOADED : TaixuBlockEntity.Status.CONFLICT, scan.problem());
            return;
        }
        var oldAnchor = machine.getBlockPos();
        var target = machine.worldPos(TaixuStructure.CONTROLLER);
        var oldParts = new HashMap<BlockPos, TaixuStructure.Part>();
        var newParts = new HashMap<BlockPos, TaixuStructure.Part>();
        machine.structureParts().forEach(part -> oldParts.put(part.pos(), part));
        TaixuStructure.parts(TaixuStructure.EMBEDDED_VERSION).forEach(part -> newParts.put(part.pos(), part));
        var positions = new HashSet<>(oldParts.keySet()); positions.addAll(newParts.keySet());
        var changes = new ArrayList<Change>();
        for (var local : positions) {
            var oldPart = oldParts.get(local); var newPart = newParts.get(local);
            if (!Objects.equals(oldPart, newPart)) changes.add(new Change(machine.worldPos(local), oldPart, newPart));
        }
        // Remove the old controller last, so failed placements leave its live state untouched.
        changes.sort(Comparator.comparingInt(change -> change.pos().equals(oldAnchor) ? 2
                : change.pos().equals(target) ? 1 : 0));
        for (var change : changes) {
            var pos = change.pos();
            if (!level.hasChunkAt(pos)) { machine.embeddingFailed(TaixuBlockEntity.Status.UNLOADED, pos); return; }
            if (change.oldPart() == null ? !level.getBlockState(pos).isAir()
                    : !TaixuStructure.matches(level.getBlockState(pos), change.oldPart(), facing)) {
                machine.embeddingFailed(TaixuBlockEntity.Status.CONFLICT, pos); return;
            }
            if (!machine.allowed(player, pos) || change.oldPart() != null
                    && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled()) {
                machine.embeddingFailed(TaixuBlockEntity.Status.PROTECTED, pos); return;
            }
        }
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        var refunds = new int[TaixuStructure.Type.values().length];
        var snapshots = new ArrayList<BlockSnapshot>();
        for (var change : changes) {
            var pos = change.pos();
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            var previous = level.getBlockState(pos);
            var expected = change.newPart() == null ? Blocks.AIR.defaultBlockState() : TaixuStructure.state(change.newPart(), facing);
            // Transfer inventory through saved data, never through ordinary removal drops.
            if (pos.equals(oldAnchor)) machine.getInternalInventory().clear();
            boolean placed = level.setBlock(pos, expected, Block.UPDATE_CLIENTS);
            if (placed) snapshots.add(snapshot);
            boolean rejected = placed && change.newPart() != null
                    && NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, previous, player)).isCanceled();
            if (!placed || rejected) {
                Collections.reverse(snapshots);
                for (var rollback : snapshots) rollback.restore(Block.UPDATE_CLIENTS);
                // setBlock can fail without replacing the old controller.
                if (level.getBlockEntity(oldAnchor) instanceof TaixuBlockEntity restored) {
                    restored.loadTag(saved, level.registryAccess());
                    restored.embeddingFailed(rejected ? TaixuBlockEntity.Status.PROTECTED : TaixuBlockEntity.Status.CONFLICT, pos);
                }
                return;
            }
            if (change.oldPart() != null && change.oldPart().type() != TaixuStructure.Type.CONTROLLER
                    && change.oldPart().type() != TaixuStructure.Type.RESOURCE_PORT)
                refunds[change.oldPart().type().ordinal()]++;
        }
        var relocated = (TaixuBlockEntity) level.getBlockEntity(target);
        saved.putInt("x", target.getX()); saved.putInt("y", target.getY()); saved.putInt("z", target.getZ());
        saved.putInt("taixuVersion", TaixuStructure.EMBEDDED_VERSION); saved.putInt("taixuLayout", TaixuStructure.EMBEDDED_VERSION);
        saved.putBoolean("taixuEmbedRequested", false); saved.putInt("cursor", 0);
        saved.putUUID("owner", player.getUUID());
        if (!player.getAbilities().instabuild) saved.getCompound("taixuMotion").putIntArray("portable", refunds);
        relocated.loadTag(saved, level.registryAccess());
        relocated.embeddingComplete();
        for (var change : changes) level.updateNeighborsAt(change.pos(), level.getBlockState(change.pos()).getBlock());
        // The old platform is gone; put its operator on the existing central walkway.
        var standing = relocated.worldPos(new BlockPos(0, 65, -8)).getBottomCenter();
        player.connection.teleport(standing.x, standing.y, standing.z, player.getYRot(), player.getXRot());
    }
}
