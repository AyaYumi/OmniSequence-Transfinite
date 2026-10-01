package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.TaixuMotionWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(EntityGetter.class)
public interface TaixuEntityCollisionMixin {
    @Inject(method = "getEntityCollisions", at = @At("RETURN"), cancellable = true)
    private void taixu$collision(Entity entity, AABB bounds, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (this instanceof Level level) cir.setReturnValue(TaixuMotionWorld.collisions(level, entity, bounds, cir.getReturnValue()));
    }
}
