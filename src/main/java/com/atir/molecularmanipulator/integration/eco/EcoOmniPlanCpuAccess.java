package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Optional CPU hooks. Deliberately contains no ECO types, including in method descriptors. */
public interface EcoOmniPlanCpuAccess extends EcoCpuDispatchHost {
    OmniExactCraftingState ecoOmni$exactState();
    void ecoOmni$installExactState(OmniExactCraftingState state);
    Map<IPatternDetails, Object> ecoOmni$tasks();
    BigInteger ecoOmni$physicalAmount(AEKey key);
    void ecoOmni$storePhysical(AEKey key, BigInteger amount);
    void ecoOmni$queueEmitted(AEKey key, BigInteger amount);
    void ecoOmni$restoreRefundSnapshot(net.minecraft.nbt.CompoundTag data, net.minecraft.core.HolderLookup.Provider registries);

    default boolean ecoOmni$managed() { return false; }
    default boolean ecoOmni$paused() { return false; }
    default Set<Map.Entry<IPatternDetails, Object>> ecoOmni$candidates(Map<IPatternDetails, Object> tasks) {
        return tasks.entrySet();
    }
    default ICraftingInventory ecoOmni$guard(IPatternDetails pattern, ICraftingInventory physical) { return physical; }
    default KeyCounter[] ecoOmni$extract(IPatternDetails pattern, ICraftingInventory physical, Level level,
            KeyCounter outputs, KeyCounter containers, Supplier<KeyCounter[]> ordinary) { return ordinary.get(); }
    default BigInteger ecoOmni$limit(IPatternDetails pattern, BigInteger requested) { return requested; }
    default boolean ecoOmni$pushProvider(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, Supplier<Boolean> ordinary) { return ordinary.get(); }
    default void ecoOmni$accepted(IPatternDetails pattern, BigInteger count) {}
}
