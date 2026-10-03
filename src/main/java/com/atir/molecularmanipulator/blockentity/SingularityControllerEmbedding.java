package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import java.util.*;

/** Moves only authored terminal parts; the rest of the building stays at its original world coordinates. */
final class SingularityControllerEmbedding {
    private record Change(BlockPos pos, SingularityStructure.Part oldPart, SingularityStructure.Part newPart) { }

    static void move(SingularityBlockEntity machine, ServerPlayer player) {
        if (machine.structureVersion() != SingularityStructure.LEGACY_VERSION || machine.motion().hasBodies()
                || machine.motion().hasPortable() || machine.operation() != SingularityBlockEntity.Operation.IDLE) return;
        var level = machine.getLevel();
        var facing = machine.facing();
        var scan = SingularityStructure.inspect(level, machine.getBlockPos(), facing, part -> false, machine.structureVersion());
        if (!scan.formed()) {
            machine.embeddingFailed(scan.unloaded() > 0 ? SingularityBlockEntity.Status.UNLOADED : SingularityBlockEntity.Status.CONFLICT, scan.problem());
            return;
        }
        var oldAnchor = machine.getBlockPos();
        var target = machine.worldPos(SingularityStructure.CONTROLLER);
        var oldParts = new HashMap<BlockPos, SingularityStructure.Part>();
        var newParts = new HashMap<BlockPos, SingularityStructure.Part>();
        machine.structureParts().forEach(part -> oldParts.put(part.pos(), part));
        SingularityStructure.parts(SingularityStructure.EMBEDDED_VERSION).forEach(part -> newParts.put(part.pos(), part));
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
            if (!level.hasChunkAt(pos)) { machine.embeddingFailed(SingularityBlockEntity.Status.UNLOADED, pos); return; }
            if (change.oldPart() == null ? !level.getBlockState(pos).isAir()
                    : !SingularityStructure.matches(level.getBlockState(pos), change.oldPart(), facing)) {
                machine.embeddingFailed(SingularityBlockEntity.Status.CONFLICT, pos); return;
            }
            if (!machine.allowed(player, pos) || change.oldPart() != null
                    && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player))) {
                machine.embeddingFailed(SingularityBlockEntity.Status.PROTECTED, pos); return;
            }
        }
        var saved = machine.saveWithFullMetadata();
        var refunds = new int[SingularityStructure.Type.values().length];
        var snapshots = new ArrayList<BlockSnapshot>();
        for (var change : changes) {
            var pos = change.pos();
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            var previous = level.getBlockState(pos);
            var expected = change.newPart() == null ? Blocks.AIR.defaultBlockState() : SingularityStructure.state(change.newPart(), facing);
            // Transfer inventory through saved data, never through ordinary removal drops.
            if (pos.equals(oldAnchor)) machine.getInternalInventory().clear();
            boolean placed = level.setBlock(pos, expected, Block.UPDATE_CLIENTS);
            if (placed) snapshots.add(snapshot);
            boolean rejected = placed && change.newPart() != null
                    && MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, previous, player));
            if (!placed || rejected) {
                Collections.reverse(snapshots);
                for (var rollback : snapshots) rollback.restore(true, false);
                // setBlock can fail without replacing the old controller.
                if (level.getBlockEntity(oldAnchor) instanceof SingularityBlockEntity restored) {
                    restored.loadTag(saved);
                    restored.embeddingFailed(rejected ? SingularityBlockEntity.Status.PROTECTED : SingularityBlockEntity.Status.CONFLICT, pos);
                }
                return;
            }
            if (change.oldPart() != null && change.oldPart().type() != SingularityStructure.Type.CONTROLLER)
                refunds[change.oldPart().type().ordinal()]++;
        }
        var relocated = (SingularityBlockEntity) level.getBlockEntity(target);
        saved.putInt("x", target.getX()); saved.putInt("y", target.getY()); saved.putInt("z", target.getZ());
        saved.putInt("singularityVersion", SingularityStructure.EMBEDDED_VERSION); saved.putInt("singularityLayout", SingularityStructure.EMBEDDED_VERSION);
        saved.putBoolean("singularityEmbedRequested", false); saved.putInt("cursor", 0);
        saved.putUUID("owner", player.getUUID());
        if (!player.getAbilities().instabuild) SingularityStructure.writeMaterialCounts(saved.getCompound("singularityMotion"), "portable", refunds);
        relocated.loadTag(saved);
        relocated.embeddingComplete();
        for (var change : changes) level.updateNeighborsAt(change.pos(), level.getBlockState(change.pos()).getBlock());
        // The old platform is gone; put its operator on the existing central walkway.
        var standing = net.minecraft.world.phys.Vec3.atBottomCenterOf(relocated.worldPos(new BlockPos(0, 65, -8)));
        player.connection.teleport(standing.x, standing.y, standing.z, player.getYRot(), player.getXRot());
    }
}
