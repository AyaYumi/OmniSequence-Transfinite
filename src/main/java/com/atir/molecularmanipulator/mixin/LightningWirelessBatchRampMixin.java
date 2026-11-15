package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.integration.ae2.OmniNativeDispatchScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Accelerates successful native probes admitted by Omni's current provider window. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.provider.ProviderTarget", remap = false)
public abstract class LightningWirelessBatchRampMixin {
    @WrapOperation(method = "pushSameTickRamp", at = @At(value = "INVOKE",
            target = "Lcom/moakiee/ae2lt/logic/provider/ProviderTarget;saturatingDouble(I)I"),
            require = 2, allow = 2)
    private static int molecularmanipulator$growFullyAcceptedBatch(int chunk, Operation<Integer> original) {
        if (!OmniNativeDispatchScope.active()) return original.call(chunk);
        // Both native growth sites follow full insertion of an unclipped chunk.
        // Keep native request limits, rejection/backoff, and owned-copy accounting.
        return (int) Math.min(Integer.MAX_VALUE, chunk * 8L);
    }

    @WrapOperation(method = {"pushPatternStep(Lappeng/api/crafting/IPatternDetails;JJZZ"
            + "Ljava/util/function/BooleanSupplier;Ljava/util/function/IntFunction;)"
            + "Lcom/moakiee/ae2lt/logic/provider/ProviderTarget$BatchStepResult;",
            "reservoirRefillCapacity", "batchStepCandidate", "batchStepProvenChunk",
            "hasReservoirBatchState", "reopenReservoirTailAudit", "batchStepRampAllowance"},
            at = @At(value = "INVOKE",
                    target = "Lcom/moakiee/ae2lt/logic/provider/ProviderTarget$BatchStepState;expireIfIdle(JZ)Z"),
            require = 7, allow = 7)
    private boolean molecularmanipulator$retainProbeAcrossJobs(@Coerce Object state, long tick,
            boolean attempted, Operation<Boolean> original) {
        return !OmniNativeDispatchScope.active() && original.call(state, tick, attempted);
    }
}
