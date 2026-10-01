package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.entity.TaixuAssemblyEntity;
import com.atir.molecularmanipulator.registry.TaixuContent;
import com.atir.molecularmanipulator.world.TaixuMotionWorld;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Owns material accounting and the conversion between ordinary blocks and regenerable moving bodies. */
public final class TaixuMotionState {
    public static final int OFF = 0, RUNNING = 1, PAUSED = 2, DOCKING = 3;
    private final TaixuBlockEntity machine;
    private int mask, mode;
    private UUID token = UUID.randomUUID(), operator;
    private long phase, epoch;
    private boolean loaded, reclaimAfterDock, dockingFrozen;
    private String message = "off";
    private final int[] portable = new int[TaixuStructure.Type.values().length];
    public TaixuMotionState(TaixuBlockEntity machine) { this.machine = machine; }
    public int mask() { return mask; }
    public int mode() { return mode; }
    public String message() { return message; }
    public boolean hasBodies() { return mask != 0; }
    public boolean hasPortable() { return Arrays.stream(portable).anyMatch(n -> n > 0); }
    public boolean owns(TaixuStructure.Part part) { int group = TaixuMotionGeometry.groupOf(part, machine.structureVersion()); return group >= 0 && (mask & 1 << group) != 0; }
    public boolean owns(int group, UUID token) { return this.token.equals(token) && (mask & 1 << group) != 0; }
    public void setClientMask(int mask) { this.mask = mask; }
    private long now() { return machine.getLevel().getGameTime(); }
    private long clock() { return phase + (mode == RUNNING && loaded ? Math.max(0, now() - epoch) : 0); }
    private long dockingElapsed() { return dockingFrozen || !loaded ? restoredDockingElapsed : Math.max(0, now() - epoch); }
    private void changed() { machine.saveChanges(); machine.markForClientUpdate(); }
    private void fail(String reason) { message = reason; changed(); }
    private boolean allLoaded() {
        for (var chunk : machine.getChunkLoadingChunks()) if (!machine.getLevel().hasChunk(chunk.x, chunk.z)) return false;
        return true;
    }
    public void toggle(ServerPlayer player) {
        if (!machine.canManage(player) || machine.operation() != TaixuBlockEntity.Operation.IDLE || mode == DOCKING || machine.embedRequested()) return;
        operator = player.getUUID(); machine.setRecoveryOwner(player);
        if (!hasBodies()) { activate(player); return; }
        if (mode == RUNNING) { phase = clock(); epoch = now(); mode = PAUSED; message = "paused"; }
        else {
            if (!ModConfig.FORCE_LOAD_CHUNKS.get()) { fail("chunks_required"); return; }
            if (!allLoaded()) { fail("unloaded"); return; }
            epoch = now(); mode = RUNNING;
            if (blockedAhead()) { mode = PAUSED; fail("obstacle"); return; }
            message = "running";
        }
        configureBodies(); changed();
    }
    private void activate(ServerPlayer player) {
        if (!ModConfig.FORCE_LOAD_CHUNKS.get()) { fail("chunks_required"); return; }
        if (!machine.formed() || hasPortable()) { fail("incomplete"); return; }
        var level = machine.getLevel(); var parts = movingParts();
        for (int x : new int[]{-50, 50}) for (int z : new int[]{-50, 50}) {
            if (!level.getWorldBorder().isWithinBounds(machine.worldPos(new BlockPos(x, 64, z)))) {
                fail("border"); return;
            }
        }
        for (var part : parts) {
            var pos = home(part);
            if (!level.hasChunkAt(pos) || !TaixuStructure.matches(level.getBlockState(pos), part, machine.facing())) { fail("incomplete"); return; }
            if (!machine.allowed(player, pos) || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled()) { fail("protected"); return; }
        }
        var removed = new ArrayList<TaixuStructure.Part>();
        for (var part : parts) {
            if (!level.setBlock(home(part), Blocks.AIR.defaultBlockState(), 2)) {
                for (var previous : removed) level.setBlock(home(previous), TaixuStructure.state(previous, machine.facing()), 2);
                fail("blocked"); return;
            }
            removed.add(part);
        }
        mask = TaixuMotionGeometry.ALL_GROUPS; token = UUID.randomUUID(); phase = 0; epoch = now();
        mode = RUNNING; message = "running"; loaded = true; dockingFrozen = false;
        if (blockedAhead()) { mode = PAUSED; message = "obstacle"; }
        configureBodies(); machine.scheduleInspection(); changed();
    }
    public void dock(ServerPlayer player, boolean reclaim) {
        if (!machine.canManage(player) || !hasBodies() || mode == DOCKING) return;
        operator = player.getUUID(); machine.setRecoveryOwner(player); reclaimAfterDock = reclaim;
        phase = clock(); epoch = now(); mode = DOCKING; message = "docking"; dockingFrozen = false;
        if (blockedAhead()) freezeDocking("dock_obstacle");
        configureBodies(); changed();
    }
    public void serverTick() {
        if (!hasBodies()) return;
        if (!loaded) { epoch = now() - (mode == DOCKING ? restoredDockingElapsed : 0); loaded = true; configureBodies(); }
        if (now() % 20 == 0) configureBodies();
        String unavailable = !ModConfig.FORCE_LOAD_CHUNKS.get() ? "chunks_required" : !allLoaded() ? "unloaded" : null;
        if (unavailable != null) {
            if (mode == RUNNING) { phase = clock(); epoch = now(); mode = PAUSED; message = unavailable; configureBodies(); changed(); }
            else if (mode == DOCKING) freezeDocking(unavailable);
            return;
        }
        if (mode == PAUSED && (message.equals("unloaded") || message.equals("chunks_required"))) {
            epoch = now(); mode = RUNNING;
            if (blockedAhead()) { mode = PAUSED; message = "obstacle"; } else message = "running";
            configureBodies(); changed();
        }
        if (mode == RUNNING && now() % 10 == 0 && blockedAhead()) {
            phase = clock(); epoch = now(); mode = PAUSED; message = "obstacle"; configureBodies(); changed();
        }
        if (mode == DOCKING && now() % 10 == 0) {
            if (blockedAhead()) { freezeDocking("dock_obstacle"); return; }
            if (dockingFrozen) {
                epoch = now() - restoredDockingElapsed; dockingFrozen = false; message = "docking"; configureBodies(); changed();
            }
            for (var group : TaixuMotionGeometry.groups(machine.structureVersion())) {
                var pose = TaixuMotionGeometry.dockingPose(group.id(), phase, dockingElapsed());
                if (Math.abs(pose.angle()) > 1e-8 || Math.abs(pose.lift()) > 1e-8) return;
            }
            restore();
        }
    }
    private void freezeDocking(String reason) {
        if (dockingFrozen && message.equals(reason)) return;
        restoredDockingElapsed = dockingElapsed(); dockingFrozen = true; message = reason;
        configureBodies(); changed();
    }
    private TaixuMotionGeometry.Pose futurePose(int group, int ticks) {
        return mode == DOCKING ? TaixuMotionGeometry.dockingPose(group, phase, dockingElapsed() + ticks)
                : TaixuMotionGeometry.runningPose(group, clock() + ticks);
    }
    /** A component-sized check every ten ticks, looking farther ahead than the next check. */
    private boolean blockedAhead() {
        var level = machine.getLevel();
        var origin = TaixuMotionGeometry.origin(machine.getBlockPos(), machine.facing(), machine.structureVersion());
        for (var group : TaixuMotionGeometry.groups(machine.structureVersion())) {
            if ((mask & 1 << group.id()) == 0) continue;
            var checked = new HashSet<BlockPos>();
            for (var part : group.parts()) {
                AABB bounds = null;
                for (int step = 0; step <= 12; step += 3) {
                    var pose = futurePose(group.id(), step);
                    var sample = TaixuMotionGeometry.transform(new AABB(part.pos()), p -> TaixuMotionGeometry.toWorld(p, origin, machine.facing(), pose));
                    bounds = bounds == null ? sample : bounds.minmax(sample);
                }
                bounds = bounds.inflate(.02);
                for (var probe : BlockPos.betweenClosed((int) Math.floor(bounds.minX), (int) Math.floor(bounds.minY), (int) Math.floor(bounds.minZ),
                        (int) Math.floor(bounds.maxX), (int) Math.floor(bounds.maxY), (int) Math.floor(bounds.maxZ))) {
                    if (!checked.add(probe.immutable())) continue;
                    if (!level.hasChunkAt(probe) || !level.getWorldBorder().isWithinBounds(probe)) return true;
                    var state = level.getBlockState(probe);
                    if (state.isAir() || state.getCollisionShape(level, probe).isEmpty()) continue;
                    // Fixed access joints remain stationary and participate in ordinary rider collision.
                    if (isFixedJoint(probe)) continue;
                    return true;
                }
            }
        }
        return false;
    }
    private boolean isFixedJoint(BlockPos world) {
        var part = TaixuStructure.partAt(machine.getBlockPos(), machine.facing(), world, machine.structureVersion());
        return part != null && TaixuMotionGeometry.groupOf(part, machine.structureVersion()) < 0
                && TaixuStructure.matches(machine.getLevel().getBlockState(world), part, machine.facing());
    }
    private void restore() {
        var level = (ServerLevel) machine.getLevel();
        var player = operator == null ? null : level.getServer().getPlayerList().getPlayer(operator);
        if (player == null || player.level() != level || !player.mayBuild()) { fail("owner_offline"); return; }
        var parts = movingParts();
        for (var part : parts) {
            var pos = home(part);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) { fail("blocked"); return; }
            if (!machine.allowed(player, pos)) { fail("protected"); return; }
        }
        var placed = new ArrayList<BlockSnapshot>();
        for (var part : parts) {
            var pos = home(part); var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            boolean placedBlock = level.setBlock(pos, TaixuStructure.state(part, machine.facing()), 2);
            if (placedBlock) placed.add(snapshot);
            if (!placedBlock || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), player)).isCanceled()) {
                for (var previous : placed) previous.restore(2);
                fail("protected"); return;
            }
        }
        mask = 0; mode = OFF; phase = 0; message = "off"; discardBodies();
        machine.scheduleInspection(); changed();
        if (reclaimAfterDock) { reclaimAfterDock = false; machine.beginDismantle(player); }
    }
    private BlockPos home(TaixuStructure.Part part) { return machine.worldPos(part); }
    private List<TaixuStructure.Part> movingParts() { return TaixuMotionGeometry.groups(machine.structureVersion()).stream().flatMap(g -> g.parts().stream()).toList(); }
    private UUID bodyId(int group) { return UUID.nameUUIDFromBytes((token + ":" + group).getBytes(StandardCharsets.UTF_8)); }
    private void configureBodies() {
        if (!(machine.getLevel() instanceof ServerLevel level) || !hasBodies()) return;
        for (var group : TaixuMotionGeometry.groups(machine.structureVersion())) {
            if ((mask & 1 << group.id()) == 0) continue;
            var id = bodyId(group.id()); var entity = level.getEntity(id);
            int bodyMode = mode == DOCKING && dockingFrozen ? 4 : mode;
            long bodyEpoch = bodyMode == 4 ? restoredDockingElapsed : epoch;
            if (entity instanceof TaixuAssemblyEntity body) body.configure(machine.getBlockPos(), machine.facing(), group.id(), token, bodyMode, phase, bodyEpoch, machine.structureVersion());
            else if (entity == null) {
                var body = new TaixuAssemblyEntity(TaixuContent.ASSEMBLY.get(), level); body.setUUID(id);
                body.configure(machine.getBlockPos(), machine.facing(), group.id(), token, bodyMode, phase, bodyEpoch, machine.structureVersion()); level.addFreshEntity(body);
            }
        }
    }
    public void discardBodies() {
        if (machine.getLevel() == null) return;
        for (var body : List.copyOf(TaixuMotionWorld.bodies(machine.getLevel())))
            if (body.configured() && body.controller().equals(machine.getBlockPos()) && body.token().equals(token)) body.discard();
    }
    public int[] portableCounts() {
        var result = portable.clone();
        for (var group : TaixuMotionGeometry.groups(machine.structureVersion())) if ((mask & 1 << group.id()) != 0)
            for (var part : group.parts()) result[part.type().ordinal()]++;
        return result;
    }
    public void loadPortable(int[] counts) {
        clear(); for (int i = 0; i < Math.min(counts.length, portable.length); i++) portable[i] = Math.clamp(counts[i], 0, 1_000_000);
    }
    public ItemStack nextPortableStack() {
        for (int i = 0; i < portable.length; i++) if (portable[i] > 0) {
            int amount = Math.min(64, portable[i]); portable[i] -= amount; changed();
            return new ItemStack(TaixuStructure.block(TaixuStructure.Type.values()[i]), amount);
        }
        return ItemStack.EMPTY;
    }
    public boolean takePortable(TaixuStructure.Type type) {
        if (portable[type.ordinal()] <= 0) return false;
        portable[type.ordinal()]--; changed(); return true;
    }
    public void clear() {
        discardBodies(); mask = mode = 0; phase = epoch = restoredDockingElapsed = 0;
        loaded = reclaimAfterDock = dockingFrozen = false; message = "off"; Arrays.fill(portable, 0);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", TaixuMotionGeometry.VERSION); tag.putInt("mask", mask);
        tag.putInt("mode", mode); tag.putUUID("token", token); if (operator != null) tag.putUUID("operator", operator);
        tag.putLong("phase", machine.getLevel() == null ? phase : clock());
        tag.putLong("dockingElapsed", mode == DOCKING ? dockingElapsed() : 0);
        tag.putBoolean("dockingFrozen", dockingFrozen); tag.putString("message", message);
        tag.putBoolean("reclaim", reclaimAfterDock); tag.putIntArray("portable", portable); tag.putIntArray("ownedMaterials", portableCounts());
        return tag;
    }
    private long restoredDockingElapsed;
    public void load(CompoundTag tag) {
        if (tag.getInt("version") != TaixuMotionGeometry.VERSION) { loadPortable(tag.getIntArray("ownedMaterials")); return; }
        mask = tag.getInt("mask") & TaixuMotionGeometry.ALL_GROUPS; mode = Math.clamp(tag.getInt("mode"), 0, 3);
        token = tag.hasUUID("token") ? tag.getUUID("token") : UUID.randomUUID(); operator = tag.hasUUID("operator") ? tag.getUUID("operator") : null;
        phase = Math.max(0, tag.getLong("phase")); restoredDockingElapsed = Math.max(0, tag.getLong("dockingElapsed"));
        reclaimAfterDock = tag.getBoolean("reclaim"); var stored = tag.getIntArray("portable");
        Arrays.fill(portable, 0); dockingFrozen = tag.getBoolean("dockingFrozen");
        for (int i = 0; i < Math.min(stored.length, portable.length); i++) portable[i] = Math.max(0, stored[i]);
        loaded = false; message = mode == RUNNING ? "running" : mode == PAUSED ? "paused" : mode == DOCKING ? "docking" : "off";
        if (tag.contains("message")) message = tag.getString("message");
    }
}
