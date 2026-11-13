package com.atir.molecularmanipulator.integration.ae2;

import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Loaded hubs only. Listing never scans worlds, requests chunks or creates loading tickets. */
public final class SequenceWirelessHubs {
    private static final Map<MinecraftServer, Map<UUID, SequenceWirelessHubBlockEntity>> LOADED = new WeakHashMap<>();
    private SequenceWirelessHubs() {}

    public static void add(SequenceWirelessHubBlockEntity hub) {
        if (hub.getLevel() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level.getServer(), ignored -> new HashMap<>()).put(hub.getHubId(), hub);
        }
    }

    public static void remove(SequenceWirelessHubBlockEntity hub) {
        if (hub.getLevel() instanceof ServerLevel level) {
            var hubs = LOADED.get(level.getServer());
            if (hubs != null) hubs.remove(hub.getHubId(), hub);
        }
    }

    public static List<SequenceWirelessHubBlockEntity> loaded(MinecraftServer server) {
        var hubs = LOADED.get(server);
        return hubs == null ? List.of() : hubs.values().stream()
                .filter(hub -> hub.binding().resolve(server) == hub).toList();
    }
}
