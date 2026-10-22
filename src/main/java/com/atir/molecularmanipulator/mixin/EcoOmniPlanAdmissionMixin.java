package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanAdmission;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ECOExternalCpuSupport.class, remap = false)
public abstract class EcoOmniPlanAdmissionMixin {
    @Inject(method = "accepts", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$acceptOmni(ICraftingCPU cpu, ICraftingPlan plan,
            CallbackInfoReturnable<Boolean> callback) {
        if (EcoOmniPlanAdmission.supports(cpu, plan)) callback.setReturnValue(true);
    }

    @Inject(method = "supportsPlan", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$acceptOmniSubmission(ICraftingPlan plan,
            CallbackInfoReturnable<Boolean> callback) {
        if (EcoOmniPlanAdmission.supportsActive(plan)) callback.setReturnValue(true);
    }
}
