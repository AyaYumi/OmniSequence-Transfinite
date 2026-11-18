package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

/** Optional dispatcher entry point with integration-independent method descriptors. */
public interface OmniCpuDispatchRunner {
    int omniDispatch$execute(int maximum, CraftingService crafting, IEnergyService energy, Level level);
    default boolean omniDispatch$defersSingle(IPatternDetails pattern, ICraftingProvider provider) { return false; }
    default int omniDispatch$executeDeferredSingle(int maximum, CraftingService crafting,
            IEnergyService energy, Level level) { return -1; }
    boolean omniDispatch$isExactPlan(ICraftingPlan plan);
    default java.math.BigInteger omniDispatch$storageBytes(ICraftingPlan plan) {
        return java.math.BigInteger.valueOf(plan.bytes());
    }
    /** Existing parent orders may advance their next physical child after a level downgrade. */
    default boolean omniDispatch$continuingJob() { return false; }
    void omniDispatch$suspend(String reason);
}
