package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import cn.dancingsnow.neoecoae.grid.crafting.planner.ae2.ECOExactCraftingPlan;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchHost;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchRunner;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchAccess;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchJournal;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Optional NeoECO plan admission and legacy dispatch ownership migration. */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1200)
public abstract class EcoCpuDispatchMixin implements EcoCpuDispatchAccess, OmniCpuDispatchRunner {
    @Shadow private ExecutingCraftingJob job;
    @Unique private Tag molecularmanipulator$legacyEcoDispatch;
    @Unique private boolean molecularmanipulator$sharedDispatchPaused;

    @Override public boolean ecoOmni$dispatchPaused() {
        return molecularmanipulator$sharedDispatchPaused || molecularmanipulator$legacyEcoDispatch != null;
    }

    @Override public boolean omniDispatch$isExactPlan(ICraftingPlan plan) {
        return plan instanceof ECOExactCraftingPlan;
    }
    @Override public java.math.BigInteger omniDispatch$storageBytes(ICraftingPlan plan) {
        if (!EcoOmniPlanBridge.owns(plan)) return java.math.BigInteger.valueOf(plan.bytes());
        var prepared = EcoOmniPlanBridge.prepare(plan);
        if (prepared.theoreticalBytes().signum() < 0) throw new IllegalArgumentException("Negative ECO storage allocation");
        var amounts = new java.util.HashMap<>(prepared.deferredStock());
        for (var entry : plan.usedItems()) {
            amounts.merge(entry.getKey(), java.math.BigInteger.valueOf(entry.getLongValue()), java.math.BigInteger::add);
        }
        var initial = java.math.BigInteger.ZERO;
        for (var entry : amounts.entrySet()) {
            initial = initial.add(com.atir.molecularmanipulator.crafting.OmniCpuResources.itemBytes(
                    entry.getValue(), entry.getKey().getAmountPerByte()));
        }
        return prepared.theoreticalBytes().max(initial);
    }

    @Override public void omniDispatch$suspend(String reason) {
        molecularmanipulator$sharedDispatchPaused = true;
    }

    @WrapMethod(method = "finishJob")
    private void molecularmanipulator$preserveUncertainJob(boolean success, Operation<Void> original) {
        if (success && ecoOmni$dispatchPaused()) return;
        original.call(success);
        if (!success && job == null) {
            molecularmanipulator$legacyEcoDispatch = null;
            molecularmanipulator$sharedDispatchPaused = false;
        }
    }
    @WrapMethod(method = "insert")
    private long molecularmanipulator$preserveUncertainOutput(AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (ecoOmni$dispatchPaused()) return 0L;
        // CraftingServiceStorage calls this while NetworkStorage is already inserting.
        // Keep the original entry so the shared link operation can take physical ownership;
        // it defers destination delivery until the CPU tick, preserving feedback and tails.
        // Never redirect this entry through the UUID router or probe network capacity here:
        // nested NetworkStorage insertion returns zero under its mountsInUse guard.
        return original.call(key, amount, mode);
    }
    @WrapMethod(method = "readFromNBT")
    private void molecularmanipulator$readEcoDispatch(CompoundTag tag, HolderLookup.Provider registries, Operation<Void> original) {
        var legacy = EcoCpuDispatchJournal.read(tag.get(EcoCpuDispatchJournal.NBT_KEY));
        molecularmanipulator$legacyEcoDispatch = legacy.held();
        molecularmanipulator$sharedDispatchPaused = false;
        original.call(tag, registries);
        // The shared ledger restores its own balance during the original call.
        if (legacy.credit().signum() > 0)
            ((OmniCpuDispatchHost) this).omniDispatch$dispatchEnergy().addCredit(legacy.credit());
    }
    @WrapMethod(method = "writeToNBT")
    private void molecularmanipulator$writeEcoDispatch(CompoundTag tag, HolderLookup.Provider registries, Operation<Void> original) {
        tag.remove(EcoCpuDispatchJournal.NBT_KEY);
        original.call(tag, registries);
        // A failed plan restore can write the original NBT verbatim, including its old balance.
        if (!tag.contains(EcoCpuDispatchJournal.NBT_KEY) && molecularmanipulator$legacyEcoDispatch != null)
            tag.put(EcoCpuDispatchJournal.NBT_KEY, molecularmanipulator$legacyEcoDispatch.copy());
    }
}
