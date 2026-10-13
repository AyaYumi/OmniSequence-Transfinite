package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import com.atir.molecularmanipulator.client.GravityViewTransition;
import com.atir.molecularmanipulator.integration.AntarchyGravityCompat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    @Shadow private float partialTickTime;
    @Shadow private BlockGetter level;
    @Unique private final GravityViewTransition omnisequence$transition = new GravityViewTransition();
    @Unique private Entity omnisequence$trackedEntity;
    @Unique private boolean omnisequence$baseRotation;
    @Unique private boolean omnisequence$anchorPending;
    @Unique private boolean omnisequence$ownsAnchor;
    @Unique private boolean omnisequence$externalView;
    @Unique private double omnisequence$renderTime;

    @Inject(method = "setup", at = @At("HEAD"))
    private void omnisequence$frame(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
            float partialTick, CallbackInfo ci) {
        if (omnisequence$trackedEntity != entity) {
            omnisequence$transition.reset();
            omnisequence$trackedEntity = entity;
        }
        omnisequence$baseRotation = true;
        omnisequence$anchorPending = true;
        omnisequence$renderTime = entity.tickCount + partialTick;
        omnisequence$externalView = AntarchyGravityCompat.controlsGravity(entity);
        if (omnisequence$externalView) omnisequence$transition.reset();
        omnisequence$ownsAnchor = !omnisequence$externalView && (GravityController.rotated(entity)
                || omnisequence$transition.active(omnisequence$renderTime));
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void omnisequence$endFrame(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
            float partialTick, CallbackInfo ci) {
        omnisequence$anchorPending = false;
        if (omnisequence$externalView) {
            // Store the base view, as beginFrame runs before the front-view reversal in our own frames.
            var base = detached && mirrored ? new Quaternionf(rotation).rotateY((float) Math.PI) : rotation;
            omnisequence$transition.beginFrame(Direction.DOWN, base, omnisequence$renderTime);
        }
    }

    @Inject(method = "setRotation(FFF)V", at = @At("TAIL"))
    private void omnisequence$rotate(float yaw, float pitch, float roll, CallbackInfo ci) {
        if (entity == null || omnisequence$externalView) return;
        rotation.premul(GravityFrame.rotation(GravityController.direction(entity)));
        rotation.set(omnisequence$baseRotation
                ? omnisequence$transition.beginFrame(GravityController.direction(entity), rotation, omnisequence$renderTime)
                : omnisequence$transition.apply(rotation, omnisequence$renderTime));
        omnisequence$baseRotation = false;
        new Vector3f(0, 0, -1).rotate(rotation, forwards);
        new Vector3f(0, 1, 0).rotate(rotation, up);
        new Vector3f(-1, 0, 0).rotate(rotation, left);
    }

    @WrapMethod(method = "setPosition(DDD)V")
    private void omnisequence$eye(double x, double y, double z, Operation<Void> original) {
        // The first position in setup is the eye anchor, including calls redirected by other gravity mods.
        // Later positions belong to third-person movement and must retain the caller's coordinates.
        boolean anchor = omnisequence$anchorPending;
        omnisequence$anchorPending = false;
        if (!anchor || !omnisequence$ownsAnchor) {
            original.call(x, y, z);
            if (anchor) omnisequence$transition.recordEye(new Vec3(x, y, z));
            return;
        }
        var target = entity.getPosition(partialTickTime).add(GravityFrame.up(GravityController.direction(entity))
                .scale(Mth.lerp(partialTickTime, eyeHeightOld, eyeHeight)));
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
        original.call(eye.x, eye.y, eye.z);
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void omnisequence$reset(CallbackInfo ci) {
        omnisequence$transition.reset();
        omnisequence$trackedEntity = null;
        omnisequence$anchorPending = false;
    }
}
