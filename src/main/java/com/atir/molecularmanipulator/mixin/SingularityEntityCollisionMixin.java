package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(EntityGetter.class)
public interface SingularityEntityCollisionMixin {
    @Inject(method = "getEntityCollisions", at = @At("RETURN"), cancellable = true)
    private void singularity$collision(Entity entity, AABB bounds, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (this instanceof Level level) cir.setReturnValue(SingularityMotionWorld.collisions(level, entity, bounds, cir.getReturnValue()));
    }
}
