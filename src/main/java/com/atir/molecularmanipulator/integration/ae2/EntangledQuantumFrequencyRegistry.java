package com.atir.molecularmanipulator.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class EntangledQuantumFrequencyRegistry {
    private static final Map<MinecraftServer, Map<Long, GlobalPos>> CLAIMS = new WeakHashMap<>();

    private EntangledQuantumFrequencyRegistry() {
    }

    public static boolean claim(ServerLevel level, BlockPos position, long frequency) {
        var claims = CLAIMS.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
        var owner = GlobalPos.of(level.dimension(), position);
        var claimedBy = claims.get(frequency);
        if (claimedBy != null && !claimedBy.equals(owner)) {
            return false;
        }
        claims.put(frequency, owner);
        return true;
    }

    public static void release(ServerLevel level, BlockPos position, long frequency) {
        var claims = CLAIMS.get(level.getServer());
        if (claims == null) {
            return;
        }
        claims.remove(frequency, GlobalPos.of(level.dimension(), position));
        if (claims.isEmpty()) {
            CLAIMS.remove(level.getServer());
        }
    }
}
