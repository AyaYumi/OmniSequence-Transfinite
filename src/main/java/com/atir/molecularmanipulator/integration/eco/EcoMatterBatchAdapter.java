package com.atir.molecularmanipulator.integration.eco;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** ECO 21.2.9 transactions backed by the well's durable, per-key long material queue. */
public final class EcoMatterBatchAdapter {
    private EcoMatterBatchAdapter() {}

    public static ECOFastPathDispatchProvider.Preparation prepare(
            MatterFabricationPatternLogic logic, ECOBatchDispatchContext context) {
        var prepared = prepare(logic, context, Long.MAX_VALUE);
        if (prepared == null) return null;
        return new ECOFastPathDispatchProvider.Preparation(prepared.maxCrafts(), null, false, batch -> {
            if (!batch.remainingTotal().isEmpty()) return false;
            try {
                return prepared.commit(inputs(batch),
                        MatterFabricationPatternLogic.batchInputs(batch.outputTotal()),
                        batch.craftCount(), context.craftingJobId());
            } catch (ArithmeticException | IllegalArgumentException error) { return false; }
        }, true);
    }

    public static ECOFastPathDispatchProvider.ExactPreparation prepareExact(
            MatterFabricationPatternLogic logic, ECOBatchDispatchContext context, BigInteger requested) {
        if (requested == null || requested.signum() <= 0 || context == null || context.craftingJobId() == null) return null;
        // No arbitrary dispatch window: use all capacity representable by the queue and
        // its expected outputs. ECO retains every unaccepted copy in its exact inventory.
        long bounded = requested.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        var prepared = prepare(logic, context, bounded);
        if (prepared == null) return null;
        long count = prepared.maxCrafts();
        // ECOExactPreparation has a no-argument callback. ECO reserves the context's
        // complete scaled vector before calling it; the preparation owns immutable unit
        // snapshots and rechecks the well, pattern, catalysts and capacity at commit.
        return new ECOFastPathDispatchProvider.ExactPreparation(BigInteger.valueOf(count),
                () -> prepared.commitReserved(count, context.craftingJobId()));
    }

    private static MatterFabricationPatternLogic.CountedBatch prepare(
            MatterFabricationPatternLogic logic, ECOBatchDispatchContext context, long requested) {
        if (context == null || !context.containerItems().isEmpty()) return null;
        try {
            if (!MatterFabricationPatternLogic.batchInputs(context.outputs()).equals(
                    MatterFabricationPatternLogic.batchInputs(context.pattern().getOutputs()))) return null;
            return logic.prepareCountedBatch(context.pattern(),
                    MatterFabricationPatternLogic.batchInputs(context.inputItems()), requested);
        } catch (ArithmeticException | IllegalArgumentException error) { return null; }
    }

    /** Check exact values before touching ECO's inputTotal() long materialization. */
    static Map<AEKey, Long> inputs(ECOFastPathDispatchProvider.Batch batch) {
        var exact = batch.exactInputTotal();
        if (exact == null) throw new IllegalArgumentException("Missing exact input vector");
        if (exact.isEmpty()) return MatterFabricationPatternLogic.batchInputs(batch.inputTotal());
        var values = new LinkedHashMap<AEKey, Long>();
        for (var entry : exact.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().signum() <= 0)
                throw new IllegalArgumentException("Invalid exact input");
            values.put(entry.getKey(), entry.getValue().longValueExact());
        }
        if (!values.equals(MatterFabricationPatternLogic.batchInputs(batch.inputTotal())))
            throw new IllegalArgumentException("Inconsistent long and exact inputs");
        return values;
    }
}
