package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 1.20.1 equivalent of the newer entity pre-tick event. */
@Mixin(Entity.class)
public abstract class SingularityEntityCollisionMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void singularity$carry(CallbackInfo ci){SingularityMotionWorld.carry((Entity)(Object)this);}
}
