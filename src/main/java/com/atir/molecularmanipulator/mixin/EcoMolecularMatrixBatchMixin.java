package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import com.atir.molecularmanipulator.integration.eco.EcoMolecularFastPathAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Only opts in matrices that our existing molecular-core capability accepts. */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern",
        remap = false)
public abstract class EcoMolecularMatrixBatchMixin implements ECOFastPathDispatchProvider {
    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return EcoMolecularFastPathAdapter.prepare((ICraftingProvider) (Object) this, context);
    }
}
