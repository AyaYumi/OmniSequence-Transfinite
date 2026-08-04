package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.atir.molecularmanipulator.integration.extendedae.MolecularMatrixCluster;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TileAssemblerMatrixPattern.class, remap = false)
public abstract class TileAssemblerMatrixPatternMixin implements MolecularBatchCraftingProvider {
    @Override
    public boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern)) {
            return false;
        }
        var tile = (TileAssemblerMatrixPattern) (Object) this;
        return tile.getCluster() instanceof MolecularMatrixCluster cluster
                && cluster.molecularmanipulator$hasCore();
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return AssemblerMatrixMolecularCoreBlockEntity.VIRTUAL_PARALLEL_LIMIT;
    }

    @Override
    public boolean molecularmanipulator$supportsReusableBatching(
            IPatternDetails patternDetails) {
        return molecularmanipulator$supportsBatching(patternDetails);
    }
}
