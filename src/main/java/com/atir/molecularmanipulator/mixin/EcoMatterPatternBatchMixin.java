package com.atir.molecularmanipulator.mixin;

import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import com.atir.molecularmanipulator.integration.eco.EcoMatterBatchAdapter;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;

/** Optional interface injection: only the public ECO provider API is used. */
@Mixin(value = MatterFabricationPatternLogic.class, remap = false)
public abstract class EcoMatterPatternBatchMixin implements ECOFastPathDispatchProvider {
    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return EcoMatterBatchAdapter.prepare((MatterFabricationPatternLogic) (Object) this, context);
    }

    @Override
    public ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        return EcoMatterBatchAdapter.prepareExact((MatterFabricationPatternLogic) (Object) this, context, requested);
    }
}
