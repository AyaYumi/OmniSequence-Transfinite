package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.CraftingSimulationState;
import com.atir.molecularmanipulator.crafting.CraftingPlanAmountLimits;
import com.atir.molecularmanipulator.integration.ae2.CraftingPlanOverflowBridge;
import com.atir.molecularmanipulator.integration.ae2.CraftingSimulationOverflowBridge;
import com.atir.molecularmanipulator.integration.ae2.KeyCounterOverflowBridge;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingSimulationState.class, remap = false)
public abstract class CraftingSimulationStateOverflowMixin
        implements CraftingSimulationOverflowBridge {
    @Shadow
    private double bytes;

    @Shadow
    @Final
    private Map<IPatternDetails, Long> crafts;

    @Shadow
    @Final
    private KeyCounter requiredExtract;

    @Shadow
    @Final
    private KeyCounter emittedItems;

    @Unique
    private boolean molecularmanipulator$unrepresentableTotal;

    @Inject(method = "addBytes", at = @At("HEAD"))
    private void molecularmanipulator$detectByteOverflow(
            double amount, CallbackInfo callback) {
        if (CraftingPlanAmountLimits.bytesExceedLongCapacity(bytes, amount)) {
            molecularmanipulator$unrepresentableTotal = true;
        }
    }

    @Inject(method = "addCrafting", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$detectCraftCountOverflow(
            IPatternDetails pattern, long amount, CallbackInfo callback) {
        if (amount < 0) {
            molecularmanipulator$unrepresentableTotal = true;
            callback.cancel();
            return;
        }

        long current = crafts.getOrDefault(pattern, 0L);
        if (CraftingPlanAmountLimits.additionOverflows(current, amount)) {
            molecularmanipulator$unrepresentableTotal = true;
            crafts.put(pattern, Long.MAX_VALUE);
            callback.cancel();
        }
    }

    @Inject(method = "applyDiff", at = @At("HEAD"))
    private void molecularmanipulator$propagateOverflowToParent(
            CraftingSimulationState parent, CallbackInfo callback) {
        if (molecularmanipulator$hasUnrepresentableTotal()) {
            ((CraftingSimulationOverflowBridge) parent)
                    .molecularmanipulator$markUnrepresentableTotal();
        }
    }

    @Inject(method = "buildCraftingPlan", at = @At("RETURN"))
    private static void molecularmanipulator$markUnsafePlan(
            CraftingSimulationState state, CraftingCalculation calculation,
            long requestedAmount, CallbackInfoReturnable<CraftingPlan> callback) {
        boolean missingOverflow = ((KeyCounterOverflowBridge) (Object) calculation.getMissingItems())
                .molecularmanipulator$hasUnrepresentableAmount();
        if (((CraftingSimulationOverflowBridge) state).molecularmanipulator$hasUnrepresentableTotal()
                || missingOverflow) {
            ((CraftingPlanOverflowBridge) (Object) callback.getReturnValue())
                    .molecularmanipulator$markUnrepresentableTotal();
        }
    }

    @Override
    public boolean molecularmanipulator$hasUnrepresentableTotal() {
        return molecularmanipulator$unrepresentableTotal
                || ((KeyCounterOverflowBridge) (Object) requiredExtract)
                        .molecularmanipulator$hasUnrepresentableAmount()
                || ((KeyCounterOverflowBridge) (Object) emittedItems)
                        .molecularmanipulator$hasUnrepresentableAmount();
    }

    @Override
    public void molecularmanipulator$markUnrepresentableTotal() {
        molecularmanipulator$unrepresentableTotal = true;
    }
}
