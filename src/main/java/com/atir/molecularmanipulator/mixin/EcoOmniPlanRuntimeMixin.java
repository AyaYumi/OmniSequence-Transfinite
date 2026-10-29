package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime.DispatchCandidate;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanRuntimeAccess;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ECOExecutionRuntime.class, remap = false)
public abstract class EcoOmniPlanRuntimeMixin implements EcoOmniPlanRuntimeAccess {
    @Unique private IntFunction<BigInteger> molecularmanipulator$ecoRemaining;

    @Override public void ecoOmni$bindRemaining(IntFunction<BigInteger> remaining) {
        molecularmanipulator$ecoRemaining = remaining;
    }

    // ECO's Map overload aggregates equal patterns. An Omni job has a separate
    // exact ledger per ECO task ID, including two owners of the same pattern.
    @Inject(method = "taskRemaining", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$ecoTaskRemaining(int taskId,
            Map<IPatternDetails, Long> ignored, CallbackInfoReturnable<Long> callback) {
        var remaining = molecularmanipulator$ecoRemaining != null ? molecularmanipulator$ecoRemaining
                : com.atir.molecularmanipulator.integration.eco.EcoOmniPlanRuntimeScope.remaining();
        if (remaining != null) {
            callback.setReturnValue(remaining.apply(taskId)
                    .max(BigInteger.ZERO).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact());
        }
    }

    @Override @Invoker("exactAllowance")
    public abstract BigInteger ecoOmni$exactAllowance(DispatchCandidate candidate, BigInteger remaining);
    @Override @Invoker("onAcceptedExact")
    public abstract void ecoOmni$acceptedExact(DispatchCandidate candidate, BigInteger count, KeyCounter[] inputs);
    @Override @Invoker("protectedStartupSeed")
    public abstract Map<AEKey, Long> ecoOmni$protectedSeed(DispatchCandidate candidate);
    @Override @Invoker("limitCycleBatch")
    public abstract long ecoOmni$limitCycleBatch(DispatchCandidate candidate, KeyCounter[] inputs,
            ListCraftingInventory inventory, long upper);
    @Override @Invoker("writeToNBT")
    public abstract void ecoOmni$write(CompoundTag tag, HolderLookup.Provider registries);
}
