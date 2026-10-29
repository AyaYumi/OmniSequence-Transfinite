package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.integration.ae2.OmniNativeDispatchScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Correct only native calls admitted by Omni; ordinary AE CPUs retain Lightning's policy. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.provider.ProviderWirelessDispatch", remap = false)
public abstract class LightningWirelessOmniDispatchMixin {
    @WrapOperation(method = "dispatchFairBatch", at = @At(value = "INVOKE",
            target = "Lcom/moakiee/ae2lt/logic/provider/DispatchFairnessScheduler$Pass;allowance(Ljava/lang/Object;)J"))
    private long molecularmanipulator$shareCurrentCpuWindow(@Coerce Object pass, Object target,
            Operation<Long> original, @Local(name = "remaining") long remaining,
            @Local(name = "attemptBudget") int budget, @Local(name = "attempts") int attempts,
            @Local(argsOnly = true, index = 6) boolean fastMode) {
        if (!OmniNativeDispatchScope.active() || !fastMode) return original.call(pass, target);
        // Native calls offer the CPU's CURRENT remaining window. Historical
        // fairness ceilings can grow larger than it and hand the entire slice
        // to one target, leaving other ready targets idle. Lease only an equal
        // current share; keep the provider's own success and refusal accounting.
        long share = remaining <= 0 ? 0 : 1 + (remaining - 1) / Math.max(1, budget - attempts);
        ((LightningWirelessFairPassAccessor) pass).molecularmanipulator$raiseAllowance(target, share);
        return share;
    }
}
