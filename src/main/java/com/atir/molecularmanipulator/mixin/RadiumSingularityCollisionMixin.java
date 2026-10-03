package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Radium bypasses Level.getEntityCollisions during movement; retain indexed bodies. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.lithium.common.entity.LithiumEntityCollisions", remap = false)
public abstract class RadiumSingularityCollisionMixin {
    @Inject(method = "getEntityWorldBorderCollisionIterable", at = @At("RETURN"), cancellable = true)
    private static void molecularmanipulator$indexedBodyCollisions(EntityGetter getter, Entity entity,
            AABB bounds, boolean border, CallbackInfoReturnable<Iterable<VoxelShape>> callback) {
        if (!(getter instanceof Level level) || SingularityMotionWorld.bodies(level).isEmpty()) return;
        var extra = SingularityMotionWorld.collisions(level, entity, bounds, List.of());
        if (extra.isEmpty()) return;
        var original = callback.getReturnValue();
        // Keep Radium's lazy query until there is actually an indexed body nearby.
        var combined = new ArrayList<VoxelShape>(extra);
        original.forEach(combined::add);
        callback.setReturnValue(combined);
    }
}
