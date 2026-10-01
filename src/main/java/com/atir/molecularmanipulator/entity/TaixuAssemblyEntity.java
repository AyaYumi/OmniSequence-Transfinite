package com.atir.molecularmanipulator.entity;

import com.atir.molecularmanipulator.blockentity.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** A kinematic body. Its controller owns the blocks; this regenerable entity owns collision and rendering. */
public final class TaixuAssemblyEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> ASSEMBLY = SynchedEntityData.defineId(TaixuAssemblyEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final Map<BlockPos, List<AABB>> collisionCache = new HashMap<>();
    private long cachedTick = Long.MIN_VALUE;
    public TaixuAssemblyEntity(EntityType<? extends TaixuAssemblyEntity> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(ASSEMBLY, new CompoundTag()); }
    public void configure(BlockPos controller, Direction facing, int group, UUID token, int mode, long phase, long epoch) {
        configure(controller, facing, group, token, mode, phase, epoch, TaixuStructure.VERSION);
    }
    public void configure(BlockPos controller, Direction facing, int group, UUID token, int mode, long phase, long epoch, int version) {
        var data = new CompoundTag(); data.putLong("controller", controller.asLong()); data.putInt("facing", facing.get3DDataValue());
        data.putInt("group", group); data.putUUID("token", token); data.putInt("mode", mode); data.putLong("phase", phase); data.putLong("epoch", epoch);
        data.putInt("layout", version);
        entityData.set(ASSEMBLY, data); var origin = origin(); setPos(origin.x, origin.y, origin.z); updateBounds(); collisionCache.clear();
    }
    public boolean configured() { return entityData.get(ASSEMBLY).hasUUID("token"); }
    public BlockPos controller() { return BlockPos.of(entityData.get(ASSEMBLY).getLong("controller")); }
    public Direction facing() { return Direction.from3DDataValue(entityData.get(ASSEMBLY).getInt("facing")); }
    public int groupId() { return Math.clamp(entityData.get(ASSEMBLY).getInt("group"), 0, TaixuMotionGeometry.GROUP_COUNT - 1); }
    public UUID token() { return entityData.get(ASSEMBLY).getUUID("token"); }
    public int motionMode() { return entityData.get(ASSEMBLY).getInt("mode"); }
    public double motionAge(double partialTick) {
        var data = entityData.get(ASSEMBLY);
        return data.getLong("phase") + (motionMode() == 1
                ? Math.max(0, level().getGameTime() + partialTick - data.getLong("epoch")) : 0);
    }
    public int structureVersion() { return entityData.get(ASSEMBLY).contains("layout") ? entityData.get(ASSEMBLY).getInt("layout") : TaixuStructure.LEGACY_VERSION; }
    public Vec3 origin() { return TaixuMotionGeometry.origin(controller(), facing(), structureVersion()); }
    public TaixuMotionGeometry.Pose pose(double partialTick) {
        var data = entityData.get(ASSEMBLY); long phase = data.getLong("phase");
        double elapsed = Math.max(0, level().getGameTime() + partialTick - data.getLong("epoch"));
        return switch (data.getInt("mode")) {
            case 1 -> TaixuMotionGeometry.runningPose(groupId(), phase + elapsed);
            case 3 -> TaixuMotionGeometry.dockingPose(groupId(), phase, elapsed);
            case 4 -> TaixuMotionGeometry.dockingPose(groupId(), phase, data.getLong("epoch"));
            default -> TaixuMotionGeometry.runningPose(groupId(), phase);
        };
    }
    public Vec3 toWorld(Vec3 local, TaixuMotionGeometry.Pose pose) { return TaixuMotionGeometry.toWorld(local, origin(), facing(), pose); }
    public Vec3 toLocal(Vec3 world, TaixuMotionGeometry.Pose pose) { return TaixuMotionGeometry.toLocal(world, origin(), facing(), pose); }
    @Override protected AABB makeBoundingBox() {
        // Position packets call setPos/lerpTo between client ticks. The registered
        // 1x1 size is only a spawn placeholder; rebuilding from it hides whole
        // rings/towers until the next tick. Preserve the physical component here.
        // Entity's constructor calls this before synchronized data exists.
        if (entityData == null || !configured()) return super.makeBoundingBox();
        return TaixuMotionGeometry.transform(TaixuMotionGeometry.group(groupId(), structureVersion()).bounds(),
                p -> toWorld(p, pose(0))).inflate(.05);
    }
    private void updateBounds() {
        if (configured()) setBoundingBox(makeBoundingBox());
    }
    @Override public void tick() {
        super.tick();
        if (!configured()) return;
        if (!level().isClientSide() && level().hasChunkAt(controller())) {
            if (!(level().getBlockEntity(controller()) instanceof TaixuBlockEntity owner) || !owner.motion().owns(groupId(), token())) { discard(); return; }
        }
        updateBounds();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (ASSEMBLY.equals(key)) { collisionCache.clear(); updateBounds(); } }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return true; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }

    public List<AABB> collisionBoxes(AABB query, double partialTick) {
        if (!configured()) return List.of();
        var pose = pose(partialTick); var group = TaixuMotionGeometry.group(groupId(), structureVersion());
        var local = TaixuMotionGeometry.transform(query.inflate(.01), p -> toLocal(p, pose));
        if (!group.bounds().inflate(.01).intersects(local)) return List.of();
        if (cachedTick != level().getGameTime()) { cachedTick = level().getGameTime(); collisionCache.clear(); }
        var result = new ArrayList<AABB>();
        for (var pos : BlockPos.betweenClosed((int) Math.floor(local.minX), (int) Math.floor(local.minY), (int) Math.floor(local.minZ),
                (int) Math.floor(local.maxX), (int) Math.floor(local.maxY), (int) Math.floor(local.maxZ))) {
            var part = group.index().get(pos); if (part == null) continue;
            List<AABB> boxes;
            if (partialTick == 0) boxes = collisionCache.computeIfAbsent(part.pos(), ignored -> transformedBoxes(part, pose));
            else boxes = transformedBoxes(part, pose);
            for (var box : boxes) if (box.intersects(query)) result.add(box);
        }
        return result;
    }
    private List<AABB> transformedBoxes(TaixuStructure.Part part, TaixuMotionGeometry.Pose pose) {
        var result = new ArrayList<AABB>();
        var shape = TaixuStructure.state(part, Direction.NORTH).getCollisionShape(level(), part.pos());
        boolean axisAligned = Math.abs(pose.angle() % 90) < 1.0e-8;
        // Fine conservative cells bound the rotated edges without filling the ring's hollow centre.
        int steps = axisAligned ? 1 : 4;
        for (var box : shape.toAabbs()) for (int x = 0; x < steps; x++) for (int z = 0; z < steps; z++) {
            double dx = (box.maxX - box.minX) / steps, dz = (box.maxZ - box.minZ) / steps;
            var cell = new AABB(box.minX + dx * x, box.minY, box.minZ + dz * z,
                    box.minX + dx * (x + 1), box.maxY, box.minZ + dz * (z + 1)).move(part.x(), part.y(), part.z());
            result.add(TaixuMotionGeometry.transform(cell, p -> toWorld(p, pose)));
        }
        return List.copyOf(result);
    }
    public boolean supports(Entity rider, double partialTick) {
        var body = rider.getBoundingBox();
        var feet = new AABB(body.minX + .03, body.minY - .14, body.minZ + .03, body.maxX - .03, body.minY + .02, body.maxZ - .03);
        for (var box : collisionBoxes(feet, partialTick)) if (Math.abs(box.maxY - body.minY) < .14) return true;
        return false;
    }
    public Vec3 carriage(Entity rider) {
        return toWorld(toLocal(rider.position(), pose(-1)), pose(0)).subtract(rider.position());
    }
}
