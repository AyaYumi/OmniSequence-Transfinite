package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Retain vanilla friction, effects, sprinting and jump attributes in the player's local frame. */
@Mixin(LivingEntity.class)
public abstract class GravityLivingMotionMixin {
    @WrapMethod(method = "travel")
    private void omnisequence$travel(Vec3 input, Operation<Void> original) {
        var entity = (LivingEntity) (Object) this;
        if (!GravityController.rotated(entity)) { original.call(input); return; }
        var down = GravityController.direction(entity);
        var access = (GravityPlayerAccess) entity;
        if (access.omnisequence$localMotion()) { original.call(input); return; }
        access.omnisequence$localMotion(true);
        entity.setDeltaMovement(GravityFrame.toLocal(down, entity.getDeltaMovement()));
        try { original.call(input); }
        finally {
            entity.setDeltaMovement(GravityFrame.toWorld(down, entity.getDeltaMovement()));
            access.omnisequence$localMotion(false);
        }
    }

    @WrapMethod(method = "jumpFromGround")
    private void omnisequence$jump(Operation<Void> original) {
        var entity = (LivingEntity) (Object) this;
        if (!GravityController.rotated(entity)) { original.call(); return; }
        if (((GravityPlayerAccess) entity).omnisequence$localMotion()) { original.call(); return; }
        var down = GravityController.direction(entity);
        entity.setDeltaMovement(GravityFrame.toLocal(down, entity.getDeltaMovement()));
        try { original.call(); }
        finally { entity.setDeltaMovement(GravityFrame.toWorld(down, entity.getDeltaMovement())); }
    }
}
