package com.atir.molecularmanipulator.integration.extendedaeplus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class MatterFabricationProviderUploadTest {
    @Test void fabricationCategoryKeysResolveWithoutLookingAtProviderNames() {
        for (var key : List.of("molecularmanipulator:matter_fabrication",
                "molecularmanipulator:matter_fabrication_processing", "matter_fabrication",
                "matter_fabrication_processing")) {
            assertTrue(MatterFabricationProviderUpload.isFabricationCategory(key), key);
        }
        assertFalse(MatterFabricationProviderUpload.isFabricationCategory(null));
        assertFalse(MatterFabricationProviderUpload.isFabricationCategory("minecraft:crafting"));
        assertFalse(MatterFabricationProviderUpload.isFabricationCategory("my_matter_fabrication_provider"));
    }

    @Test void automaticTargetIsAComponentTokenNotATranslatedOrRenamedProviderString() {
        var target = MatterFabricationProviderUpload.automaticTarget();
        assertTrue(MatterFabricationProviderUpload.isAutomaticTarget(target.copy()));
        assertTrue(MatterFabricationProviderUpload.isAutomaticTarget(
                Component.translatable("gui.molecularmanipulator.fabrication.jei_title")));
        assertFalse(MatterFabricationProviderUpload.isAutomaticTarget(Component.literal(target.getString())),
                "A provider with an identical visible custom name is not the category token");
        assertFalse(MatterFabricationProviderUpload.isAutomaticTarget(Component.empty()));
        assertFalse(MatterFabricationProviderUpload.isAutomaticTarget(null));
    }

    @Test void missingOrUnownedMachineCannotBeAnAutomaticUploadDestination() {
        assertFalse(MatterFabricationProviderUpload.accepts(null, null));
    }
}
