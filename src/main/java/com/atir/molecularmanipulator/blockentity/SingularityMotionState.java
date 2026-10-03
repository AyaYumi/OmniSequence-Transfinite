package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
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
public final class SingularityMotionState {
    public static final int OFF = 0, RUNNING = 1, PAUSED = 2, DOCKING = 3;
    private final SingularityBlockEntity machine;
    private int mask, mode, geometryVersion = SingularityMotionGeometry.VERSION;
    private UUID token = UUID.randomUUID(), operator;
    private long phase, epoch;
    private boolean loaded, reclaimAfterDock, dockingFrozen;
    private String message = "off";
    private final int[] portable = new int[SingularityStructure.Type.values().length];
    public SingularityMotionState(SingularityBlockEntity machine) { this.machine = machine; }
    public int mask() { return mask; }
    public int mode() { return mode; }
    public String message() { return message; }
    public boolean hasBodies() { return mask != 0; }
    public boolean hasPortable() { return Arrays.stream(portable).anyMatch(n -> n > 0); }
    public boolean owns(SingularityStructure.Part part) { int group = SingularityMotionGeometry.groupOf(part, machine.structureVersion(), geometryVersion); return group >= 0 && (mask & 1 << group) != 0; }
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
    /** Starts motion as part of the resource-collection device lifecycle. */
    public boolean startForCollection(ServerPlayer player) {
        if (!machine.canManage(player) || machine.operation() != SingularityBlockEntity.Operation.IDLE
                || mode == DOCKING || machine.embedRequested()) return false;
        if (mode != RUNNING) toggleInternal(player);
        return mode == RUNNING;
    }

    /** Stops motion as part of the resource-collection device lifecycle. */
    public void stopForCollection(ServerPlayer player) {
        if (hasBodies() && mode != DOCKING) dock(player, false);
    }

    /** Compatibility entry point for old callers; motion now belongs to collection. */
    public void toggle(ServerPlayer player) {
        if (!machine.collectionActive()) return;
        toggleInternal(player);
    }

    private void toggleInternal(ServerPlayer player) {
        if (!machine.canManage(player) || machine.operation() != SingularityBlockEntity.Operation.IDLE || mode == DOCKING || machine.embedRequested()) return;
        operator = player.getUUID(); machine.setRecoveryOwner(player);
        // Stopping an already-running carriage must never be blocked by a
        // later layout migration. Freeze it first; migration can then finish
        // while the bodies are stationary.
        if (mode == RUNNING) {
            phase = clock(); epoch = now(); mode = PAUSED; message = "paused";
            configureBodies(); changed();
            return;
        }
        if (!migrateFixedJoints()) return;
        if (!hasBodies()) { activate(player); return; }
        if (!ModConfig.FORCE_LOAD_CHUNKS.get()) { fail("chunks_required"); return; }
        if (!allLoaded()) { fail("unloaded"); return; }
        epoch = now(); mode = RUNNING;
        if (blockedAhead()) { mode = PAUSED; fail("obstacle"); return; }
        message = "running";
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
            if (!level.hasChunkAt(pos) || !SingularityStructure.matches(level.getBlockState(pos), part, machine.facing())) { fail("incomplete"); return; }
            if (!machine.allowed(player, pos) || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled()) { fail("protected"); return; }
        }
        var removed = new ArrayList<SingularityStructure.Part>();
        for (var part : parts) {
            if (!level.setBlock(home(part), Blocks.AIR.defaultBlockState(), 2)) {
                for (var previous : removed) level.setBlock(home(previous), SingularityStructure.state(previous, machine.facing()), 2);
                fail("blocked"); return;
            }
            removed.add(part);
        }
        mask = SingularityMotionGeometry.ALL_GROUPS; token = UUID.randomUUID(); phase = 0; epoch = now();
        mode = RUNNING; message = "running"; loaded = true; dockingFrozen = false;
        if (blockedAhead()) { mode = PAUSED; message = "obstacle"; }
        configureBodies(); machine.scheduleInspection(); changed();
    }
    public void dock(ServerPlayer player, boolean reclaim) {
        if (!machine.canManage(player) || !hasBodies() || mode == DOCKING) return;
        operator = player.getUUID(); machine.setRecoveryOwner(player); reclaimAfterDock = reclaim;
        if (!migrateFixedJoints()) return;
        phase = clock(); epoch = now(); mode = DOCKING; message = "docking"; dockingFrozen = false;
        if (blockedAhead()) freezeDocking("dock_obstacle");
        configureBodies(); changed();
    }
    public void serverTick() {
        if (!hasBodies()) return;
        if (!migrateFixedJoints()) return;
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
            for (var group : SingularityMotionGeometry.groups(machine.structureVersion())) {
                var pose = SingularityMotionGeometry.dockingPose(group.id(), phase, dockingElapsed());
                if (Math.abs(pose.angle()) > 1e-8 || Math.abs(pose.lift()) > 1e-8) return;
            }
            restore();
        }
    }
    /** Restore blocks accidentally included by the old bounding-box classifier before creating new bodies. */
    private boolean migrateFixedJoints() {
        if (geometryVersion == SingularityMotionGeometry.VERSION || !hasBodies()) return true;
        if (!allLoaded()) { migrationWait("unloaded"); return false; }
        var level = (ServerLevel) machine.getLevel();
        var player = operator == null ? null : level.getServer().getPlayerList().getPlayer(operator);
        if (player == null || player.level() != level || !player.mayBuild()) { migrationWait("owner_offline"); return false; }
        var fixed = movingParts().stream().filter(p -> SingularityMotionGeometry.groupOf(p, machine.structureVersion()) < 0).toList();
        for (var part : fixed) {
            var pos = home(part);
            if (!level.getBlockState(pos).isAir()) { migrationWait("blocked"); return false; }
            if (!machine.allowed(player, pos)) { migrationWait("protected"); return false; }
        }
        var placed = new ArrayList<BlockSnapshot>();
        for (var part : fixed) {
            var pos = home(part); var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            boolean placedBlock = level.setBlock(pos, SingularityStructure.state(part, machine.facing()), 2);
            if (placedBlock) placed.add(snapshot);
            if (!placedBlock || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), player)).isCanceled()) {
                for (var previous : placed) previous.restore(2);
                migrationWait("protected"); return false;
            }
        }
        geometryVersion = SingularityMotionGeometry.VERSION;
        message = mode == RUNNING ? "running" : mode == DOCKING ? "docking" : "paused";
        machine.scheduleInspection(); changed();
        return true;
    }
    private void migrationWait(String reason) {
        if (!message.equals(reason)) fail(reason);
    }
    private void freezeDocking(String reason) {
        if (dockingFrozen && message.equals(reason)) return;
        restoredDockingElapsed = dockingElapsed(); dockingFrozen = true; message = reason;
        configureBodies(); changed();
    }
    private SingularityMotionGeometry.Pose futurePose(int group, int ticks) {
        return mode == DOCKING ? SingularityMotionGeometry.dockingPose(group, phase, dockingElapsed() + ticks)
                : SingularityMotionGeometry.runningPose(group, clock() + ticks);
    }
    /** A component-sized check every ten ticks, looking farther ahead than the next check. */
    private boolean blockedAhead() {
        var level = machine.getLevel();
        var origin = SingularityMotionGeometry.origin(machine.getBlockPos(), machine.facing(), machine.structureVersion());
        for (var group : SingularityMotionGeometry.groups(machine.structureVersion())) {
            if ((mask & 1 << group.id()) == 0) continue;
            var checked = new HashSet<BlockPos>();
            for (var part : group.parts()) {
                AABB bounds = null;
                for (int step = 0; step <= 12; step += 3) {
                    var pose = futurePose(group.id(), step);
                    var sample = SingularityMotionGeometry.transform(new AABB(part.pos()), p -> SingularityMotionGeometry.toWorld(p, origin, machine.facing(), pose));
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
        if (SingularityStructure.isCompatibilityCollectionNode(machine.getBlockPos(), machine.facing(), world,
                machine.structureVersion()))
            return machine.getLevel().getBlockState(world).is(SingularityContent.COLLECTION_NODE.get());
        var part = SingularityStructure.partAt(machine.getBlockPos(), machine.facing(), world, machine.structureVersion());
        return part != null && SingularityMotionGeometry.groupOf(part, machine.structureVersion()) < 0
                && SingularityStructure.matches(machine.getLevel().getBlockState(world), part, machine.facing());
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
            boolean placedBlock = level.setBlock(pos, SingularityStructure.state(part, machine.facing()), 2);
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
    private BlockPos home(SingularityStructure.Part part) { return machine.worldPos(part); }
    private List<SingularityStructure.Part> movingParts() {
        return SingularityMotionGeometry.groups(machine.structureVersion(), geometryVersion).stream()
                .filter(g -> !hasBodies() || (mask & 1 << g.id()) != 0).flatMap(g -> g.parts().stream()).toList();
    }
    private UUID bodyId(int group) { return UUID.nameUUIDFromBytes((token + ":" + group).getBytes(StandardCharsets.UTF_8)); }
    private void configureBodies() {
        if (!(machine.getLevel() instanceof ServerLevel level) || !hasBodies() || geometryVersion != SingularityMotionGeometry.VERSION) return;
        for (var group : SingularityMotionGeometry.groups(machine.structureVersion())) {
            if ((mask & 1 << group.id()) == 0) continue;
            var id = bodyId(group.id()); var entity = level.getEntity(id);
            int bodyMode = mode == DOCKING && dockingFrozen ? 4 : mode;
            long bodyEpoch = bodyMode == 4 ? restoredDockingElapsed : epoch;
            if (entity instanceof SingularityAssemblyEntity body) body.configure(machine.getBlockPos(), machine.facing(), group.id(), token, bodyMode, phase, bodyEpoch, machine.structureVersion());
            else if (entity == null) {
                var body = new SingularityAssemblyEntity(SingularityContent.ASSEMBLY.get(), level); body.setUUID(id);
                body.configure(machine.getBlockPos(), machine.facing(), group.id(), token, bodyMode, phase, bodyEpoch, machine.structureVersion()); level.addFreshEntity(body);
            }
        }
    }
    public void discardBodies() {
        if (machine.getLevel() == null) return;
        for (var body : List.copyOf(SingularityMotionWorld.bodies(machine.getLevel())))
            if (body.configured() && body.controller().equals(machine.getBlockPos()) && body.token().equals(token)) body.discard();
    }
    public int[] portableCounts() {
        var result = portable.clone();
        for (var group : SingularityMotionGeometry.groups(machine.structureVersion(), geometryVersion)) if ((mask & 1 << group.id()) != 0)
            for (var part : group.parts()) result[part.type().ordinal()]++;
        return result;
    }
    public void loadPortable(int[] counts) {
        clear(); for (int i = 0; i < Math.min(counts.length, portable.length); i++) portable[i] = Math.clamp(counts[i], 0, 1_000_000);
    }
    public ItemStack nextPortableStack() {
        for (int i = 0; i < portable.length; i++) if (portable[i] > 0) {
            int amount = Math.min(64, portable[i]); portable[i] -= amount; changed();
            return new ItemStack(SingularityStructure.block(SingularityStructure.Type.values()[i]), amount);
        }
        return ItemStack.EMPTY;
    }
    public boolean takePortable(SingularityStructure.Type type) {
        if (portable[type.ordinal()] <= 0) return false;
        portable[type.ordinal()]--; changed(); return true;
    }
    public void clear() {
        discardBodies(); mask = mode = 0; phase = epoch = restoredDockingElapsed = 0;
        geometryVersion = SingularityMotionGeometry.VERSION;
        loaded = reclaimAfterDock = dockingFrozen = false; message = "off"; Arrays.fill(portable, 0);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", geometryVersion); tag.putInt("mask", mask);
        tag.putInt("mode", mode); tag.putUUID("token", token); if (operator != null) tag.putUUID("operator", operator);
        tag.putLong("phase", machine.getLevel() == null ? phase : clock());
        tag.putLong("dockingElapsed", mode == DOCKING ? dockingElapsed() : 0);
        tag.putBoolean("dockingFrozen", dockingFrozen); tag.putString("message", message);
        tag.putBoolean("reclaim", reclaimAfterDock); SingularityStructure.writeMaterialCounts(tag, "portable", portable); SingularityStructure.writeMaterialCounts(tag, "ownedMaterials", portableCounts());
        return tag;
    }
    private long restoredDockingElapsed;
    public void load(CompoundTag tag) {
        int storedVersion = tag.getInt("version");
        if (storedVersion != 2 && storedVersion != SingularityMotionGeometry.VERSION) { loadPortable(SingularityStructure.readMaterialCounts(tag, "ownedMaterials")); return; }
        geometryVersion = storedVersion;
        mask = tag.getInt("mask") & SingularityMotionGeometry.ALL_GROUPS; mode = Math.clamp(tag.getInt("mode"), 0, 3);
        if (mask == 0) geometryVersion = SingularityMotionGeometry.VERSION;
        token = tag.hasUUID("token") ? tag.getUUID("token") : UUID.randomUUID(); operator = tag.hasUUID("operator") ? tag.getUUID("operator") : null;
        phase = Math.max(0, tag.getLong("phase")); restoredDockingElapsed = Math.max(0, tag.getLong("dockingElapsed"));
        reclaimAfterDock = tag.getBoolean("reclaim"); var stored = SingularityStructure.readMaterialCounts(tag, "portable");
        Arrays.fill(portable, 0); dockingFrozen = tag.getBoolean("dockingFrozen");
        for (int i = 0; i < Math.min(stored.length, portable.length); i++) portable[i] = Math.max(0, stored[i]);
        loaded = false; message = mode == RUNNING ? "running" : mode == PAUSED ? "paused" : mode == DOCKING ? "docking" : "off";
        if (tag.contains("message")) message = tag.getString("message");
    }
}
