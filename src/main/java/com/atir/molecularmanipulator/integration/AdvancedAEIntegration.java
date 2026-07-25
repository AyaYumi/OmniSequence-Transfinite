package com.atir.molecularmanipulator.integration;

import net.neoforged.fml.ModList;

public final class AdvancedAEIntegration {
    public static final String MOD_ID = "advanced_ae";

    private AdvancedAEIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
