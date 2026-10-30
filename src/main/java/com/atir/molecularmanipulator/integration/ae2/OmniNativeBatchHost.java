package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** CPU-owned accounting boundary for optional, leftover-returning batch executors. */
public interface OmniNativeBatchHost {
    boolean omniNative$virtualCpu();
    void omniNative$prefer(IPatternDetails pattern);
    UUID omniNative$jobId();
    Map<IPatternDetails, ?> omniNative$tasks();
    ListCraftingInventory omniNative$inventory();
    ListCraftingInventory omniNative$waitingFor();
    boolean omniNative$stopped();
    long omniNative$maximumCopies();
    long omniNative$taskWindow(IPatternDetails pattern);
    void omniNative$accepted(IPatternDetails pattern, long copies);
    void omniNative$expect(AEKey key, long amount);
    void omniNative$fail(String reason, Throwable cause);
    void omniNative$markDirty();
    long omniNative$workAvailable();
    void omniNative$attempted(long work);

    /** Implemented only when the optional executor's mixin is enabled. */
    interface Runner {
        int omniNative$run(int maximum, CraftingService crafting, IEnergyService energy, Level level);
    }
}
