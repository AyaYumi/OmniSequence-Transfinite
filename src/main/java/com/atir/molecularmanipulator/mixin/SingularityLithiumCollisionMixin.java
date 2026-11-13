package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lithium defers movement collisions and bypasses EntityGetter.getEntityCollisions. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions", remap = false)
public abstract class SingularityLithiumCollisionMixin {
    @Inject(method = "appendEntityCollisions", at = @At("TAIL"), remap = false)
    private static void singularity$appendMovingSurfaces(List<VoxelShape> collisions, Level level,
            Entity entity, AABB bounds, CallbackInfo ci) {
        var moving = SingularityMotionWorld.collisions(level, entity, bounds, List.of());
        if (!moving.isEmpty()) collisions.addAll(moving);
    }
}
