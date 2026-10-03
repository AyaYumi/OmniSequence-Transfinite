package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Could not register Singularity distance-culling compatibility. "
                    + "Allow molecularmanipulator:singularity_assembly and event_horizon_singularity_hub in Sodium Extras.", ex);
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
        MolecularManipulator.LOGGER.info("Singularity: registered {} in Sodium Extras {}", id, field);
    }
}
