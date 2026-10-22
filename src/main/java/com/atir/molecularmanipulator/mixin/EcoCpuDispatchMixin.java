package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatch;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchAccess;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchRunner;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Optional ECO 21.2.9 boundary; call-site dispatch must enclose foreign batch dispatchers. */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1200)
public abstract class EcoCpuDispatchMixin implements EcoCpuDispatchAccess, EcoCpuDispatchRunner {
    @Shadow private int executeCrafting(int maxPatterns, CraftingService crafting,
            IEnergyService energy, Level level) { throw new AssertionError(); }

    /** Outer call-site boundary also encloses Thunderbolt's pre-execute batch dispatcher. */
    @WrapOperation(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"))
    private int molecularmanipulator$dispatchOwnedCpu(CraftingCpuLogic self, int maxPatterns,
            CraftingService crafting, IEnergyService energy, Level level, Operation<Integer> original) {
        // Every owned CPU dispatch must pass the shared exact/phase/material accounting entry.
        // Thunderbolt can otherwise mutate AE2 tasks before executeCrafting is entered at all.
        return ((EcoOmniPlanCpuAccess) this).ecoDispatch$enabled()
                ? executeCrafting(maxPatterns, crafting, energy, level)
                : original.call(self, maxPatterns, crafting, energy, level);
    }

    @Unique private EcoCpuDispatch molecularmanipulator$ecoDispatcher;
    @Override public EcoCpuDispatch ecoDispatch$dispatcher() {
        if (molecularmanipulator$ecoDispatcher == null) molecularmanipulator$ecoDispatcher = new EcoCpuDispatch();
        return molecularmanipulator$ecoDispatcher;
    }

    @Override public int ecoDispatch$execute(int maxPatterns, CraftingService crafting,
            IEnergyService energy, Level level) {
        return ecoDispatch$dispatcher().execute((EcoOmniPlanCpuAccess) this, maxPatterns, crafting, energy, level);
    }
    @Override public boolean ecoDispatch$isExactPlan(ICraftingPlan plan) {
        return plan instanceof ECOExactCraftingPlan;
    }
    @Override public void ecoDispatch$suspend(String reason) {
        ecoDispatch$dispatcher().suspend(reason);
    }
    @WrapMethod(method = "tickCraftingLogic")
    private void molecularmanipulator$holdUncertainEco(IEnergyService energy, CraftingService crafting, Operation<Void> original) {
        if (!ecoDispatch$dispatcher().suspended()) original.call(energy, crafting);
    }
    @WrapMethod(method = "trySubmitJob")
    private ICraftingSubmitResult molecularmanipulator$blockUncertainReplacement(IGrid grid, ICraftingPlan plan,
            IActionSource source, ICraftingRequester requester, Operation<ICraftingSubmitResult> original) {
        return ecoDispatch$dispatcher().suspended() ? CraftingSubmitResult.CPU_BUSY
                : original.call(grid, plan, source, requester);
    }
    @WrapMethod(method = "finishJob")
    private void molecularmanipulator$preserveUncertainJob(boolean success, Operation<Void> original) {
        if (!ecoDispatch$dispatcher().suspended()) original.call(success);
    }
    @WrapMethod(method = "cancel")
    private void molecularmanipulator$preserveUncertainCancellation(Operation<Void> original) {
        if (!ecoDispatch$dispatcher().suspended()) original.call();
    }
    @WrapMethod(method = "storeItems")
    private void molecularmanipulator$preserveUncertainStock(Operation<Void> original) {
        if (!ecoDispatch$dispatcher().suspended()) original.call();
    }
    @WrapMethod(method = "insert")
    private long molecularmanipulator$preserveUncertainOutput(AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (ecoDispatch$dispatcher().suspended()) return 0L;
        // CraftingServiceStorage calls this while NetworkStorage is already inserting.
        // Keep the original entry so the shared link operation can take physical ownership;
        // it defers destination delivery until the CPU tick, preserving feedback and tails.
        // Never redirect this entry through the UUID router or probe network capacity here:
        // nested NetworkStorage insertion returns zero under its mountsInUse guard.
        return original.call(key, amount, mode);
    }
    @WrapMethod(method = "readFromNBT")
    private void molecularmanipulator$readEcoDispatch(CompoundTag tag, HolderLookup.Provider registries, Operation<Void> original) {
        if (tag.contains(EcoCpuDispatch.NBT_KEY)) ecoDispatch$dispatcher().read(tag.getCompound(EcoCpuDispatch.NBT_KEY));
        else molecularmanipulator$ecoDispatcher = null;
        original.call(tag, registries);
    }
    @WrapMethod(method = "writeToNBT")
    private void molecularmanipulator$writeEcoDispatch(CompoundTag tag, HolderLookup.Provider registries, Operation<Void> original) {
        original.call(tag, registries);
        if (molecularmanipulator$ecoDispatcher != null)
            tag.put(EcoCpuDispatch.NBT_KEY, molecularmanipulator$ecoDispatcher.write(registries));
    }
}
