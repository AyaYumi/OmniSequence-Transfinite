package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import com.appliedenhancements.runtime.ExactScaledTaskReconciliation;
import com.atir.molecularmanipulator.crafting.LegacyAelisTaskReconciliation;
import java.math.BigInteger;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applied 1.1.1 owns this compatibility itself; only the legacy implementation is replaced. */
@Mixin(value = ExactScaledTaskReconciliation.class, remap = false)
public abstract class LegacyAelisTaskReconciliationMixin {
    @Inject(method = "reconcile(Ljava/util/Map;Ljava/util/Map;)Lcom/appliedenhancements/runtime/ExactScaledTaskReconciliation$Result;",
            at = @At("HEAD"), cancellable = true)
    private static void omnisequence$reconcileNativeBatches(Map<IPatternDetails, Long> projected,
            Map<IPatternDetails, BigInteger> exact,
            CallbackInfoReturnable<ExactScaledTaskReconciliation.Result> callback) {
        callback.setReturnValue(LegacyAelisTaskReconciliation.reconcile(projected, exact));
    }
}
