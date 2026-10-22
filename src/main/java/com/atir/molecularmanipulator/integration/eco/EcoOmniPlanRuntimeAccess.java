package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime.DispatchCandidate;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.IntFunction;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Implemented on ECO's runtime by our optional mixin; no ECO implementation is embedded. */
public interface EcoOmniPlanRuntimeAccess {
    void ecoOmni$bindRemaining(IntFunction<BigInteger> remaining);
    BigInteger ecoOmni$exactAllowance(DispatchCandidate candidate, BigInteger remaining);
    void ecoOmni$acceptedExact(DispatchCandidate candidate, BigInteger count, KeyCounter[] unitInputs);
    Map<AEKey, Long> ecoOmni$protectedSeed(DispatchCandidate candidate);
    long ecoOmni$limitCycleBatch(DispatchCandidate candidate, KeyCounter[] unitInputs,
            ListCraftingInventory physicalSnapshot, long upper);
    void ecoOmni$write(CompoundTag tag, HolderLookup.Provider registries);
}
