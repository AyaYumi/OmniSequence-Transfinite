package com.atir.molecularmanipulator.research;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** One-time ownership split for worlds saved before the six-stage research tree. */
final class ResearchStageMigration {
    static final int VERSION = 1;

    private ResearchStageMigration() {}

    static void migrate(Map<ResourceLocation, Integer> counts, int savedVersion) {
        if (savedVersion >= VERSION) return;
        for (var entry : Map.copyOf(counts).entrySet()) completedLegacyTask(counts, entry.getKey(), entry.getValue());
        // Never invent either III-stage completion from an old machine-import research.
        // Crystal proliferation is a new ability and must be researched normally.
    }

    static void completedLegacyTask(Map<ResourceLocation, Integer> counts, ResourceLocation source, int count) {
        if (source.equals(id("ae_foundation"))) {
            copy(counts, count, "star_marrow_materials");
            copy(counts, count, "eae_materials");
            copy(counts, count, "quantum_materials");
        } else if (source.equals(id("omni_computation"))) copy(counts, count, "transfinite_fabrication");
        else if (source.equals(id("event_horizon"))) copy(counts, count, "stellar_fabrication");
    }

    private static void copy(Map<ResourceLocation, Integer> counts, int count, String to) {
        if (count > 0) counts.merge(id(to), Math.min(9, count), Math::max);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("molecularmanipulator", "research/" + path);
    }
}
