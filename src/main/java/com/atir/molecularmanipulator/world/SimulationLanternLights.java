package com.atir.molecularmanipulator.world;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import com.atir.molecularmanipulator.block.SimulationLanternBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Loaded lanterns only. This index never requests a chunk or retains a closed level. */
public final class SimulationLanternLights {
    public static final float LIGHT_RADIUS = 10F;
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();
    private SimulationLanternLights() {}
    public static synchronized void add(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level, key -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }
    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> positions = LOADED.get(level);
        if (positions != null) positions.remove(pos);
    }
    public static synchronized Collection<BlockPos> positions(Level level) {
        return LOADED.getOrDefault(level, Set.of());
    }

    /** Same three-dimensional light pools as the simulation renderer, with no render-distance/light-count limit. */
    public static boolean illuminates(Level level, Vec3 observer) {
        double radiusSqr = LIGHT_RADIUS * LIGHT_RADIUS;
        for (BlockPos pos : positions(level)) {
            if (observer.distanceToSqr(Vec3.atCenterOf(pos)) > radiusSqr || !level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof SimulationLanternBlock && state.getValue(SimulationLanternBlock.LIT)) return true;
        }
        for (var carrier : level.players()) {
            if (!carrier.isRemoved() && observer.distanceToSqr(carrier.position().add(0D, 1D, 0D)) <= radiusSqr
                    && LanternSimulation.heldHand(carrier, true) != null) return true;
        }
        return false;
    }
}
