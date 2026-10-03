package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;
import javax.annotation.Nullable;

/** Query the indexed large bodies through the same method used by vanilla movement. */
@Mixin(Level.class)
public abstract class SingularityLevelCollisionMixin implements EntityGetter {
    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB bounds) {
        return SingularityMotionWorld.collisions((Level)(Object)this, entity, bounds,
                SingularityMotionWorld.vanillaEntityCollisions((Level)(Object)this, entity, bounds));
    }
}
