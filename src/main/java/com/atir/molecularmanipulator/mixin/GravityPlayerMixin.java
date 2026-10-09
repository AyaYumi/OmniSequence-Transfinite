package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Player.class)
public abstract class GravityPlayerMixin implements GravityPlayerAccess {
    @Unique private static final EntityDataAccessor<Byte> omnisequence$DOWN =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    @Unique private boolean omnisequence$localMotion;
    @Unique private BlockPos omnisequence$source;
    @Unique private BlockPos omnisequence$candidate;
    @Unique private int omnisequence$candidateTicks;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void omnisequence$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(omnisequence$DOWN, (byte) Direction.DOWN.get3DDataValue());
    }

    @Override public Direction omnisequence$gravity() {
        return Direction.from3DDataValue(((Player) (Object) this).getEntityData().get(omnisequence$DOWN));
    }
    @Override public void omnisequence$gravity(Direction direction) {
        var player = (Player) (Object) this;
        if (omnisequence$gravity() == direction) return;
        player.getEntityData().set(omnisequence$DOWN, (byte) direction.get3DDataValue());
        player.setBoundingBox(GravityFrame.bounds(direction, player.position(), player.getDimensions(player.getPose())));
    }
    @Override public SynchedEntityData.DataValue<?> omnisequence$gravityData() {
        return new SynchedEntityData.DataValue<>(omnisequence$DOWN.id(), EntityDataSerializers.BYTE,
                (byte) omnisequence$gravity().get3DDataValue());
    }
    @Override public boolean omnisequence$isGravityData(EntityDataAccessor<?> accessor) {
        return omnisequence$DOWN.equals(accessor);
    }
    @Override public boolean omnisequence$localMotion() { return omnisequence$localMotion; }
    @Override public void omnisequence$localMotion(boolean local) { omnisequence$localMotion = local; }
    @Override public BlockPos omnisequence$gravitySource() { return omnisequence$source; }
    @Override public void omnisequence$gravitySource(BlockPos source) { omnisequence$source = source; }
    @Override public BlockPos omnisequence$gravityCandidate() { return omnisequence$candidate; }
    @Override public int omnisequence$gravityCandidateTicks() { return omnisequence$candidateTicks; }
    @Override public void omnisequence$gravityCandidate(BlockPos source, int ticks) {
        omnisequence$candidate = source;
        omnisequence$candidateTicks = ticks;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void omnisequence$validateField(CallbackInfo ci) { GravityController.tick((Player) (Object) this); }

    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    private void omnisequence$pose(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        var player = (Player) (Object) this;
        if (GravityController.rotated(player)) cir.setReturnValue(player.level().noCollision(player,
                GravityFrame.bounds(omnisequence$gravity(), player.position(), player.getDimensions(pose)).deflate(1.0E-7)));
    }

    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void omnisequence$sneak(Vec3 motion, MoverType mover, CallbackInfoReturnable<Vec3> cir) {
        var player = (Player) (Object) this;
        if (GravityController.rotated(player)) cir.setReturnValue(
                mover == MoverType.SELF || mover == MoverType.PLAYER ? GravityCollision.sneak(player, motion) : motion);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void omnisequence$drop(ItemStack stack, boolean around, boolean includeThrower,
            CallbackInfoReturnable<ItemEntity> cir) {
        var player = (Player) (Object) this;
        var item = cir.getReturnValue();
        if (item != null && GravityController.rotated(player)) {
            item.setPos(player.getEyePosition().subtract(GravityFrame.up(omnisequence$gravity()).scale(.3)));
            item.setDeltaMovement(GravityFrame.toWorld(omnisequence$gravity(), item.getDeltaMovement()));
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void omnisequence$save(CompoundTag tag, CallbackInfo ci) {
        tag.putByte("OmniGravityDirection", (byte) omnisequence$gravity().get3DDataValue());
        if (omnisequence$source != null) tag.putLong("OmniGravitySource", omnisequence$source.asLong());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void omnisequence$load(CompoundTag tag, CallbackInfo ci) {
        omnisequence$gravity(Direction.from3DDataValue(tag.getByte("OmniGravityDirection")));
        omnisequence$source = tag.contains("OmniGravitySource") ? BlockPos.of(tag.getLong("OmniGravitySource")) : null;
    }
}
