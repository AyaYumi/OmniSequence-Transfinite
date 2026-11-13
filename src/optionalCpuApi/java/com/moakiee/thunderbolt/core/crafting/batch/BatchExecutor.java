package com.moakiee.thunderbolt.core.crafting.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import java.util.IdentityHashMap;
import java.util.Map;

/** Compile-only 2.0.3 ABI; never packaged. The mixin plugin requires a matching installed executor API. */
public final class BatchExecutor {
    private BatchExecutor() {}
    public static BatchRunResult runBatchOnly(int maximum, BatchCpuAccounting.Mode mode,
            CraftingService crafting, IEnergyService energy, BatchJobView job,
            ListCraftingInventory inventory,
            Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> batched,
            Runnable markDirty, Map<AEKey, Long> protectedInputs, int providerLimit,
            long maximumCopies, boolean allowSingle, TickProviderDispatchSchedule schedule) {
        throw new UnsupportedOperationException();
    }
    public record BatchRunResult(long dispatchedCopies, int consumedCpuOps, boolean sawBatchProvider) {}
}
