package com.atir.molecularmanipulator.entity;

import com.atir.molecularmanipulator.blockentity.HighEnergyLabPowerInterfaceBlockEntity;
import com.atir.molecularmanipulator.block.LabPlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Persistent visual anchor at the front platform's energy inlet.
 * No blocks, inventory, collision, gravity, energy buffer or item production.
 * The server reads the inlet's paid AE/FE power state and synchronizes it to viewers.
 */
public final class HighEnergyLabEffectsEntity extends Entity {
    private static final EntityDataAccessor<Boolean> POWERED = SynchedEntityData.defineId(
            HighEnergyLabEffectsEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<BlockPos> POWER_OFFSET = SynchedEntityData.defineId(
            HighEnergyLabEffectsEntity.class, EntityDataSerializers.BLOCK_POS);

    public HighEnergyLabEffectsEntity(EntityType<? extends HighEnergyLabEffectsEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POWERED, false);
        builder.define(POWER_OFFSET, BlockPos.ZERO);
    }

    public boolean powered() {
        return entityData.get(POWERED);
    }

    public boolean platform() { return !entityData.get(POWER_OFFSET).equals(BlockPos.ZERO); }

    public BlockPos powerPosition() {
        return blockPosition().offset(LabPlatformGeometry.rotateOffset(entityData.get(POWER_OFFSET), Direction.fromYRot(getYRot())));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            entityData.set(POWERED, level().getBlockEntity(powerPosition())
                    instanceof HighEnergyLabPowerInterfaceBlockEntity inlet && inlet.powered());
        }
    }

    // Base entity data already persists the anchor's position and template rotation.
    // Recompute power after loading instead of persisting a potentially stale on state.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        var offset = tag.getIntArray("PowerOffset");
        entityData.set(POWER_OFFSET, offset.length == 3 ? new BlockPos(offset[0], offset[1], offset[2]) : BlockPos.ZERO);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        var powerOffset = entityData.get(POWER_OFFSET);
        if (!powerOffset.equals(BlockPos.ZERO)) {
            tag.putIntArray("PowerOffset", new int[] {powerOffset.getX(), powerOffset.getY(), powerOffset.getZ()});
        }
    }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512D * 512D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // The visual footprint is large, but the physical entity box must stay small.
        // Do not lose the holes when the front platform itself is outside the frustum.
        return new AABB(-180, -45, -180, 180, 110, 180).move(position());
    }
}
