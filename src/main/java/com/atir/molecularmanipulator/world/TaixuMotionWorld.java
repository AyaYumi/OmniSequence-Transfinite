package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.entity.TaixuAssemblyEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

/** Index large bodies independently of vanilla's small-entity section search radius. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class TaixuMotionWorld {
    private static final Map<Level, Set<TaixuAssemblyEntity>> BODIES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<TaixuAssemblyEntity> CARRIAGE = new ThreadLocal<>();
    private TaixuMotionWorld() {}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof TaixuAssemblyEntity body) BODIES.computeIfAbsent(event.getLevel(), ignored -> new HashSet<>()).add(body);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof TaixuAssemblyEntity body && BODIES.containsKey(event.getLevel())) BODIES.get(event.getLevel()).remove(body);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof Level level) BODIES.remove(level); }
    public static Collection<TaixuAssemblyEntity> bodies(Level level) { return BODIES.getOrDefault(level, Set.of()); }
    public static List<VoxelShape> collisions(Level level, Entity entity, AABB bounds, List<VoxelShape> original) {
        if (entity instanceof TaixuAssemblyEntity || bodies(level).isEmpty()) return original;
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
    @SubscribeEvent public static void carry(EntityTickEvent.Pre event) {
        var rider = event.getEntity();
        if (rider instanceof TaixuAssemblyEntity || rider.isPassenger() || rider.noPhysics || rider.getDeltaMovement().y > .1) return;
        // The local player's ordinary movement packet transports its predicted carriage.
        // Applying the same displacement again on ServerPlayer would double it.
        if (rider instanceof net.minecraft.server.level.ServerPlayer && !(rider instanceof net.neoforged.neoforge.common.util.FakePlayer)) return;
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
