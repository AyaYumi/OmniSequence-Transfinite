package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.crafting.MatterCraftingJobContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers ordinary pushes and optional Thunderbolt/ECO dispatches that bypass executeCrafting. */
@Pseudo
@Mixin(targets = {
        "appeng.crafting.execution.CraftingCpuLogic",
        "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
        "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic"
}, priority = 1200, remap = false)
public abstract class MatterCpuJobContextMixin {
    @WrapMethod(method = "tickCraftingLogic")
    private void molecularmanipulator$withMatterJob(IEnergyService energy, CraftingService crafting,
            Operation<Void> original) {
        try (var scope = MatterCraftingJobContext.openCpu(this)) {
            original.call(energy, crafting);
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void molecularmanipulator$cancelMatterJob(CallbackInfo callback) {
        MatterCraftingJobContext.markCanceled(this);
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void molecularmanipulator$abortMatterJob(boolean completed, CallbackInfo callback) {
        if (!completed) MatterCraftingJobContext.markCanceled(this);
    }
}
