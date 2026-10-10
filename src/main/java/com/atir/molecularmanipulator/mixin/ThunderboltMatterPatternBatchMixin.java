package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import org.spongepowered.asm.mixin.Mixin;

/** Thunderbolt's inputs are a reusable single-copy template; pushBatch returns leftovers. */
@Mixin(value = MatterFabricationPatternLogic.class, remap = false)
public abstract class ThunderboltMatterPatternBatchMixin implements IBatchCraftingProvider {
    @Override
    public long getBatchCapacity(IPatternDetails pattern) {
        return ((MatterFabricationPatternLogic) (Object) this).countedBatchCapacity(pattern);
    }

    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] prototype, long requested) {
        if (requested <= 0) return 0;
        try {
            var logic = (MatterFabricationPatternLogic) (Object) this;
            var batch = logic.prepareCountedBatch(pattern, MatterFabricationPatternLogic.batchInputs(prototype), requested);
            if (batch != null && batch.commitPrototype(prototype, batch.maxCrafts())) return requested - batch.maxCrafts();
        } catch (ArithmeticException | IllegalArgumentException error) { }
        return requested;
    }
}
