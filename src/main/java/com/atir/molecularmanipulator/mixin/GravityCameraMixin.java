package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import com.atir.molecularmanipulator.client.GravityViewTransition;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class GravityCameraMixin {
    @Shadow private Entity entity;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Unique private float omnisequence$partialTickTime;
    @Shadow private BlockGetter level;
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Unique private final GravityViewTransition omnisequence$transition = new GravityViewTransition();
    @Unique private Entity omnisequence$trackedEntity;
    @Unique private boolean omnisequence$baseRotation;
    @Unique private double omnisequence$renderTime;

    @Inject(method = "setup", at = @At("HEAD"))
    private void omnisequence$frame(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
            float partialTick, CallbackInfo ci) {
        if (omnisequence$trackedEntity != entity) {
            omnisequence$transition.reset();
            omnisequence$trackedEntity = entity;
        }
        omnisequence$baseRotation = true;
        omnisequence$partialTickTime = partialTick;
        omnisequence$renderTime = entity.tickCount + partialTick;
    }

    @Inject(method = "setRotation(FF)V", at = @At("TAIL"))
    private void omnisequence$rotate(float yaw, float pitch, CallbackInfo ci) {
        if (entity == null) return;
        rotation.premul(GravityFrame.rotation(GravityController.direction(entity)));
        rotation.set(omnisequence$baseRotation
                ? omnisequence$transition.beginFrame(GravityController.direction(entity), rotation, omnisequence$renderTime)
                : omnisequence$transition.apply(rotation, omnisequence$renderTime));
        omnisequence$baseRotation = false;
        new Vector3f(0, 0, 1).rotate(rotation, forwards);
        new Vector3f(0, 1, 0).rotate(rotation, up);
        new Vector3f(1, 0, 0).rotate(rotation, left);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void omnisequence$eye(Camera camera, double x, double y, double z) {
        var target = entity.getPosition(omnisequence$partialTickTime).add(GravityFrame.up(GravityController.direction(entity))
                .scale(Mth.lerp(omnisequence$partialTickTime, eyeHeightOld, eyeHeight)));
        var eye = omnisequence$transition.eye(target, omnisequence$renderTime);
        // A delayed viewpoint must never pass through the surface while the physical body turns at a corner.
        if (eye.distanceToSqr(target) > 1.0E-8) {
            var hit = level.clip(new ClipContext(target, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) {
                var offset = eye.subtract(target);
                double distance = Math.max(0, hit.getLocation().distanceTo(target) - .08);
                eye = target.add(offset.normalize().scale(distance));
            }
        }
        omnisequence$transition.recordEye(eye);
        setPosition(eye.x, eye.y, eye.z);
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void omnisequence$reset(CallbackInfo ci) {
        omnisequence$transition.reset();
        omnisequence$trackedEntity = null;
    }
}
