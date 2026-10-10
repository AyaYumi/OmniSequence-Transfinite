package com.atir.molecularmanipulator.integration.cpu;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import com.fish_dan_.data_energistics.api.crafting.dispatch.*;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import java.util.List;
import java.util.OptionalLong;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Public counted-admission contract registered through Data Energistics' provider registry. */
public final class DataEnergisticsMatterPatternAdapter implements CountedCraftingProviderAdapter {
    private final MatterFabricationPatternLogic logic;

    private DataEnergisticsMatterPatternAdapter(MatterFabricationPatternLogic logic) { this.logic = logic; }

    static Runnable register(MatterFabricationPatternLogic logic) {
        var adapter = new DataEnergisticsMatterPatternAdapter(logic);
        CountedCraftingProviderAdapters.register(logic, adapter);
        return () -> CountedCraftingProviderAdapters.unregister(logic, adapter);
    }

    @Override
    public ObjectList<CountedCraftingCapacity> captureCapacityFast(IPatternDetails pattern, KeyCounter[] prototype, long requested) {
        var batch = prepare(pattern, prototype, requested);
        long capacity = batch == null ? 0 : batch.maxCrafts();
        return new ObjectArrayList<>(List.of(new CountedCraftingCapacity(CountedCraftingTarget.provider(),
                CountedCraftingRoutingMode.AGGREGATE, OptionalLong.of(capacity), OptionalLong.of(capacity))));
    }

    @Override
    public CountedCraftingAdmission prepareBatch(IPatternDetails pattern, KeyCounter[] prototype, long requested) {
        var batch = prepare(pattern, prototype, requested);
        if (batch == null) return null;
        return new CountedCraftingAdmission() {
            private boolean used, transferred;
            @Override public long count() { return batch.maxCrafts(); }
            @Override public boolean hasTransferredInputOwnership() { return transferred; }
            @Override public boolean commit(KeyCounter[] inputs) {
                if (used) return false;
                used = true;
                transferred = inputs == prototype && batch.commitPrototype(inputs, count());
                return transferred;
            }
        };
    }

    private MatterFabricationPatternLogic.CountedBatch prepare(IPatternDetails pattern, KeyCounter[] prototype, long requested) {
        try {
            return logic.prepareCountedBatch(pattern, MatterFabricationPatternLogic.batchInputs(prototype), requested);
        } catch (ArithmeticException | IllegalArgumentException error) { return null; }
    }
}
