package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.crafting.MatterCraftingJobContext;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchStepResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counted admissions must retain the specific Trinity virtual CPU job, not just the pattern. */
@Pseudo
@Mixin(targets = "com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCpuLogic",
        priority = 1200, remap = false)
public abstract class TrinityMatterCpuJobContextMixin {
    @WrapMethod(method = "dispatchStep")
    private CraftingDispatchStepResult molecularmanipulator$withMatterJob(IEnergyService energy,
            CraftingService crafting, CraftingDispatchWindow window, CraftingDispatchBudget budget,
            Operation<CraftingDispatchStepResult> original) {
        try (var scope = MatterCraftingJobContext.openCpu(this)) {
            return original.call(energy, crafting, window, budget);
        }
    }

    @WrapMethod(method = "tickCraftingLogic")
    private void molecularmanipulator$withTickMatterJob(IEnergyService energy, CraftingService crafting,
            CraftingDispatchWindow window, CraftingDispatchBudget budget, Operation<Void> original) {
        try (var scope = MatterCraftingJobContext.openCpu(this)) {
            original.call(energy, crafting, window, budget);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void molecularmanipulator$cancelMatterJob(boolean completed, CallbackInfo callback) {
        // Hook actual termination, not cancel() which can defer during a reusable-input transaction.
        if (!completed) MatterCraftingJobContext.markCanceled(this);
    }
}
