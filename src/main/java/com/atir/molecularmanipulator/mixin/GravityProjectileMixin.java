package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve the ordinary eye-origin and aiming contracts when a player's head points sideways. */
@Mixin(Projectile.class)
public abstract class GravityProjectileMixin {
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void omnisequence$origin(Entity owner, CallbackInfo ci) {
        var projectile = (Projectile) (Object) this;
        if (owner == null || !GravityController.rotated(owner) || projectile.tickCount != 0) return;
        var eye = owner.getEyePosition();
        // Vanilla arrow/thrown constructors use owner X/Z and eye Y. Already positioned projectiles keep their origin.
        if (Math.abs(projectile.getX() - owner.getX()) < 1.0E-6
                && Math.abs(projectile.getZ() - owner.getZ()) < 1.0E-6
                && Math.abs(projectile.getY() - eye.y) < 1) {
            projectile.setPos(eye.add(GravityFrame.toWorld(GravityController.direction(owner),
                    new Vec3(0, projectile.getY() - eye.y, 0))));
        }
    }

    @WrapMethod(method = "shootFromRotation")
    private void omnisequence$aim(Entity shooter, float pitch, float yaw, float roll, float speed, float inaccuracy,
            Operation<Void> original) {
        if (!GravityController.rotated(shooter)) { original.call(shooter, pitch, yaw, roll, speed, inaccuracy); return; }
        var projectile = (Projectile) (Object) this;
        float radians = (float) (Math.PI / 180);
        var local = new Vec3(-Mth.sin(yaw * radians) * Mth.cos(pitch * radians),
                -Mth.sin((pitch + roll) * radians), Mth.cos(yaw * radians) * Mth.cos(pitch * radians));
        var down = GravityController.direction(shooter);
        var aim = GravityFrame.toWorld(down, local);
        projectile.shoot(aim.x, aim.y, aim.z, speed, inaccuracy);
        var inherited = GravityFrame.toLocal(down, shooter.getKnownMovement());
        if (shooter.onGround()) inherited = new Vec3(inherited.x, 0, inherited.z);
        projectile.setDeltaMovement(projectile.getDeltaMovement().add(GravityFrame.toWorld(down, inherited)));
    }
}
