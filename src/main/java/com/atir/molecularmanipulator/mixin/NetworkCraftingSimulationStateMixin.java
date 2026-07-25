package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.atir.molecularmanipulator.crafting.OmniCraftingSnapshotContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public abstract class NetworkCraftingSimulationStateMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter molecularmanipulator$reuseOmniSnapshot(MEStorage storage) {
        var snapshot = OmniCraftingSnapshotContext.get();
        return snapshot != null ? snapshot : storage.getAvailableStacks();
    }
}
