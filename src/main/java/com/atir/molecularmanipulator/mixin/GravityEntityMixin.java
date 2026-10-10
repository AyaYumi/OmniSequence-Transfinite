package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
public abstract class GravityEntityMixin {
    @Inject(method = "canEnterPose", at = @At("HEAD"), cancellable = true)
    private void omnisequence$pose(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        var player = (Entity) (Object) this;
        if (GravityController.rotated(player)) cir.setReturnValue(player.level().noCollision(player,
                GravityFrame.bounds(GravityController.direction(player), player.position(), player.getDimensions(pose)).deflate(1.0E-7)));
    }

    @Inject(method = "makeBoundingBox", at = @At("HEAD"), cancellable = true)
    private void omnisequence$body(CallbackInfoReturnable<AABB> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(GravityFrame.bounds(GravityController.direction(entity),
                entity.position(), entity.getDimensions(entity.getPose())));
    }

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void omnisequence$eye(CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(entity.position().add(
                GravityFrame.up(GravityController.direction(entity)).scale(entity.getEyeHeight())));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void omnisequence$eyeInterpolated(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(entity.getPosition(partialTick).add(
                GravityFrame.up(GravityController.direction(entity)).scale(entity.getEyeHeight())));
    }

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void omnisequence$eyeY(CallbackInfoReturnable<Double> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(entity.getEyePosition().y);
    }

    @Inject(method = "calculateViewVector", at = @At("RETURN"), cancellable = true)
    private void omnisequence$look(float pitch, float yaw, CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(GravityFrame.toWorld(GravityController.direction(entity), cir.getReturnValue()));
    }

    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void omnisequence$supportPosition(float offset, CallbackInfoReturnable<BlockPos> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(BlockPos.containing(entity.position().subtract(
                GravityFrame.up(GravityController.direction(entity)).scale(offset))));
    }

    @Inject(method = "checkSupportingBlock", at = @At("HEAD"), cancellable = true)
    private void omnisequence$support(boolean grounded, Vec3 movement, CallbackInfo ci) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) {
            entity.mainSupportingBlockPos = grounded ? java.util.Optional.of(entity.getOnPosLegacy()) : java.util.Optional.empty();
            ci.cancel();
        }
    }

    @WrapMethod(method = "move")
    private void omnisequence$movement(MoverType type, Vec3 movement, Operation<Void> original) {
        var entity = (Entity) (Object) this;
        if (!GravityController.rotated(entity)) { original.call(type, movement); return; }
        var access = (GravityPlayerAccess) entity;
        var down = GravityController.direction(entity);
        boolean alreadyLocal = access.omnisequence$localMotion();
        if (!alreadyLocal) {
            access.omnisequence$localMotion(true);
            entity.setDeltaMovement(GravityFrame.toLocal(down, entity.getDeltaMovement()));
        }
        try { original.call(type, alreadyLocal ? movement : GravityFrame.toLocal(down, movement)); }
        finally {
            if (!alreadyLocal) {
                entity.setDeltaMovement(GravityFrame.toWorld(down, entity.getDeltaMovement()));
                access.omnisequence$localMotion(false);
            }
        }
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"))
    private void omnisequence$position(Entity entity, double x, double y, double z) {
        if (GravityController.rotated(entity)) {
            entity.setPos(entity.position().add(GravityFrame.toWorld(GravityController.direction(entity),
                    new Vec3(x - entity.getX(), y - entity.getY(), z - entity.getZ()))));
        } else entity.setPos(x, y, z);
    }

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void omnisequence$collision(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity) (Object) this;
        if (GravityController.rotated(entity)) cir.setReturnValue(GravityCollision.collide((Player) entity, movement));
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void omnisequence$syncBody(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        var entity = (Entity) (Object) this;
        if (entity instanceof GravityPlayerAccess access && access.omnisequence$isGravityData(accessor))
            entity.setBoundingBox(GravityFrame.bounds(GravityController.direction(entity),
                entity.position(), entity.getDimensions(entity.getPose())));
    }
}
