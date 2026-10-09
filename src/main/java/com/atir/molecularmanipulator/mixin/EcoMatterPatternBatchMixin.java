package com.atir.molecularmanipulator.mixin;

import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import org.spongepowered.asm.mixin.Mixin;

/** Optional interface injection: only the public ECO provider API is used. */
@Mixin(value = MatterFabricationPatternLogic.class, remap = false)
public abstract class EcoMatterPatternBatchMixin implements ECOFastPathDispatchProvider {
    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        if (context == null || !context.containerItems().isEmpty()) return null;
        var logic = (MatterFabricationPatternLogic) (Object) this;
        try {
            if (!MatterFabricationPatternLogic.batchInputs(context.outputs()).equals(
                    MatterFabricationPatternLogic.batchInputs(context.pattern().getOutputs()))) return null;
            var prepared = logic.prepareCountedBatch(context.pattern(),
                    MatterFabricationPatternLogic.batchInputs(context.inputItems()), Long.MAX_VALUE);
            if (prepared == null) return null;
            return new Preparation(prepared.maxCrafts(), null, false, batch -> {
                if (!batch.remainingTotal().isEmpty()) return false;
                try {
                    var inputs = MatterFabricationPatternLogic.batchInputs(batch.inputTotal());
                    // Exact requests exceeding the durable queue's long representation are rejected.
                    if (!batch.exactInputTotal().isEmpty() && (batch.exactInputTotal().size() != inputs.size()
                            || !inputs.entrySet().stream().allMatch(entry -> java.math.BigInteger.valueOf(entry.getValue())
                                    .equals(batch.exactInputTotal().get(entry.getKey()))))) return false;
                    return prepared.commit(inputs, MatterFabricationPatternLogic.batchInputs(batch.outputTotal()), batch.craftCount());
                } catch (ArithmeticException | IllegalArgumentException error) { return false; }
            });
        } catch (ArithmeticException | IllegalArgumentException error) { return null; }
    }
}
