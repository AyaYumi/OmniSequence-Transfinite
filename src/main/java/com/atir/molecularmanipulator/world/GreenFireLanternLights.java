package com.atir.molecularmanipulator.world;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded lanterns only. This index never requests a chunk or retains a closed level. */
public final class GreenFireLanternLights {
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();
    private GreenFireLanternLights() {}
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
}
