package com.atir.molecularmanipulator.integration.eco;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

/** Optional dispatcher entry point. Every descriptor is available without ECO installed. */
public interface EcoCpuDispatchRunner {
    int ecoDispatch$execute(int maximum, CraftingService crafting, IEnergyService energy, Level level);
    boolean ecoDispatch$isExactPlan(ICraftingPlan plan);
    default java.math.BigInteger ecoDispatch$storageBytes(ICraftingPlan plan) {
        return java.math.BigInteger.valueOf(plan.bytes());
    }
    /** Existing parent orders may advance their next physical child after a level downgrade. */
    default boolean ecoDispatch$continuingJob() { return false; }
    void ecoDispatch$suspend(String reason);
}
