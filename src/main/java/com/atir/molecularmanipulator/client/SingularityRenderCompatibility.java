package com.atir.molecularmanipulator.client;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Register our oversized renderers with Sodium Extras' own distance-culling allowlists. */
public final class SingularityRenderCompatibility {
    private SingularityRenderCompatibility() {}

    public static void initialize() {
        if (!ModList.get().isLoaded("sodiumextras")) return;
        try {
            // Optional integration: no Sodium/Iris classes are linked on a normal client or server.
            // This must run before the first world: Sodium Extras caches its per-type decision.
            var config = Class.forName("toni.sodiumextras.EmbyConfig");
            allow(config, "entityWhitelist", "molecularmanipulator:singularity_assembly");
            allow(config, "tileEntityWhitelist", "molecularmanipulator:event_horizon_singularity_hub");
            allow(config, "tileEntityWhitelist", "molecularmanipulator:star_computation_engine_core");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Could not register oversized-renderer distance-culling compatibility. "
                    + "Allow molecularmanipulator:singularity_assembly, event_horizon_singularity_hub and "
                    + "star_computation_engine_core in Sodium Extras.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void allow(Class<?> config, String field, String id) throws ReflectiveOperationException {
        Object value = config.getField(field).get(null);
        if (!(value instanceof ModConfigSpec.ConfigValue<?> option) || !(option.get() instanceof List<?> entries)
                || entries.stream().anyMatch(entry -> !(entry instanceof String))) {
            throw new IllegalStateException("Unsupported Sodium Extras allowlist: " + field);
        }
        if (entries.contains(id) || entries.contains("molecularmanipulator:*")) return;
        var updated = new ArrayList<>((List<String>) entries);
        updated.add(id);
        var listOption = (ModConfigSpec.ConfigValue<List<String>>) option;
        listOption.set(List.copyOf(updated));
        listOption.save();
    }
}
