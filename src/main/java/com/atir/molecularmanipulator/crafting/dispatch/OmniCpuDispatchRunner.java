package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

/** Optional dispatcher entry point with integration-independent method descriptors. */
public interface OmniCpuDispatchRunner {
    int omniDispatch$execute(int maximum, CraftingService crafting, IEnergyService energy, Level level);
    boolean omniDispatch$isExactPlan(ICraftingPlan plan);
    default java.math.BigInteger omniDispatch$storageBytes(ICraftingPlan plan) {
        return java.math.BigInteger.valueOf(plan.bytes());
    }
    /** Existing parent orders may advance their next physical child after a level downgrade. */
    default boolean omniDispatch$continuingJob() { return false; }
    void omniDispatch$suspend(String reason);
}
