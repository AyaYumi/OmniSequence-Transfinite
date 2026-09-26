package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class MatterRecipeBridgeLightningTest {
    @Test
    void restoresLightningDefaultsOmittedByAddonCodecs() {
        var empty = JsonParser.parseString("{}").getAsJsonObject();
        for (var type : new String[] {"ae2lt:lightning_assembly", "ae2lt:lightning_simulation",
                "ae2lt:overload_processing"}) {
            assertEquals(new MatterRecipeBridge.LightningRequirement("high_voltage", 4),
                    MatterRecipeBridge.lightningRequirement(type, empty));
        }
        assertEquals(new MatterRecipeBridge.LightningRequirement("high_voltage", 0),
                MatterRecipeBridge.lightningRequirement("ae2lt:crystal_catalyzer", empty));
    }

    @Test
    void keepsExplicitTierAndAmount() {
        var json = JsonParser.parseString("""
                {"lightningTier":"extreme_high_voltage","lightningCost":128}
                """).getAsJsonObject();
        assertEquals(new MatterRecipeBridge.LightningRequirement("extreme_high_voltage", 128),
                MatterRecipeBridge.lightningRequirement("ae2lt:overload_processing", json));
        assertNull(MatterRecipeBridge.lightningRequirement("ae2lt:firmament_conversion", json));
    }
}
