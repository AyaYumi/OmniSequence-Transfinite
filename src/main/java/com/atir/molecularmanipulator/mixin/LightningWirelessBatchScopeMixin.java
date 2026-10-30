package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.integration.ae2.OmniNativeDispatchScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic", remap = false)
public abstract class LightningWirelessBatchScopeMixin {
    @WrapMethod(method = "pushBatch(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;J)J")
    private long molecularmanipulator$authorizeNativeProvider(IPatternDetails pattern, KeyCounter[] inputs,
            long copies, Operation<Long> original) {
        try (var scope = OmniNativeDispatchScope.enterProvider(this, pattern)) {
            return original.call(pattern, inputs, copies);
        }
    }
}
