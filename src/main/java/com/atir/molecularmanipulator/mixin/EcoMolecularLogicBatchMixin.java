package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import com.atir.molecularmanipulator.integration.eco.EcoMolecularFastPathAdapter;
import org.spongepowered.asm.mixin.Mixin;

/** Requires ECO 21.2.9 API; gate by version in the main mixin plugin. */
@Mixin(targets = {
        "com.atir.molecularmanipulator.blockentity.MolecularManipulatorLogic",
        "com.atir.molecularmanipulator.blockentity.MolecularCenterLogic"
}, remap = false)
public abstract class EcoMolecularLogicBatchMixin implements ECOFastPathDispatchProvider {
    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return EcoMolecularFastPathAdapter.prepare((ICraftingProvider) (Object) this, context);
    }
}
