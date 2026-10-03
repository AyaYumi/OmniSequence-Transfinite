package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.level.LevelEvent;
import java.util.*;

/** Index large bodies independently of vanilla's small-entity section search radius. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class SingularityMotionWorld {
    private static final Map<Level, Set<SingularityAssemblyEntity>> BODIES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<SingularityAssemblyEntity> CARRIAGE = new ThreadLocal<>();
    private SingularityMotionWorld() {}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof SingularityAssemblyEntity body) BODIES.computeIfAbsent(event.getLevel(), ignored -> new HashSet<>()).add(body);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof SingularityAssemblyEntity body && BODIES.containsKey(event.getLevel())) BODIES.get(event.getLevel()).remove(body);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof Level level) BODIES.remove(level); }
    public static Collection<SingularityAssemblyEntity> bodies(Level level) { return BODIES.getOrDefault(level, Set.of()); }
    /** EntityGetter's 1.20.1 default implementation, retained before adding large indexed bodies. */
    public static List<VoxelShape> vanillaEntityCollisions(Level level, Entity entity, AABB bounds) {
        if (bounds.getSize() < 1.0E-7D) return List.of();
        java.util.function.Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        var entities = level.getEntities(entity, bounds.inflate(1.0E-7D), predicate);
        if (entities.isEmpty()) return List.of();
        var result = new ArrayList<VoxelShape>(entities.size());
        for (var other : entities) result.add(Shapes.create(other.getBoundingBox()));
        return result;
    }
    public static List<VoxelShape> collisions(Level level, Entity entity, AABB bounds, List<VoxelShape> original) {
        if (entity instanceof SingularityAssemblyEntity || bodies(level).isEmpty()) return original;
        List<VoxelShape> result = null;
        for (var body : bodies(level)) {
            if (body.isRemoved() || !body.configured() || body == CARRIAGE.get() || !body.getBoundingBox().inflate(.2).intersects(bounds)) continue;
            for (var box : body.collisionBoxes(bounds, 0)) {
                if (result == null) result = new ArrayList<>(original);
                result.add(Shapes.create(box));
            }
        }
        return result == null ? original : result;
    }
    public static boolean supported(Entity entity) {
        for (var body : bodies(entity.level())) if (!body.isRemoved() && body.configured() && body.supports(entity, 0)) return true;
        return false;
    }
    public static void carry(Entity rider) {
        if (rider instanceof SingularityAssemblyEntity || rider.isPassenger() || rider.noPhysics || rider.getDeltaMovement().y > .1) return;
        // The local player's ordinary movement packet transports its predicted carriage.
        // Applying the same displacement again on ServerPlayer would double it.
        if (rider instanceof net.minecraft.server.level.ServerPlayer && !(rider instanceof net.minecraftforge.common.util.FakePlayer)) return;
        if (rider.level().isClientSide() && !rider.isControlledByLocalInstance()) return;
        for (var body : bodies(rider.level())) {
            if (body.isRemoved() || !body.configured() || !body.getBoundingBox().inflate(.3).intersects(rider.getBoundingBox()) || !body.supports(rider, -1)) continue;
            var delta = body.carriage(rider);
            if (delta.lengthSqr() > 1 || delta.lengthSqr() < 1e-14) return;
            CARRIAGE.set(body);
            try { rider.move(MoverType.SELF, delta); rider.setOnGround(true); rider.fallDistance = 0; }
            finally { CARRIAGE.remove(); }
            return;
        }
    }
}
