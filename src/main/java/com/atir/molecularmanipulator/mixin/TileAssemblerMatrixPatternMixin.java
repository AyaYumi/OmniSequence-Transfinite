package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.atir.molecularmanipulator.integration.extendedae.MolecularMatrixCluster;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

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
        var tile = (TileAssemblerMatrixPattern) (Object) this;
        return tile.getCluster() instanceof MolecularMatrixCluster cluster
                ? cluster.molecularmanipulator$getBatchLimit() : 0;
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails, KeyCounter unitOutputs) {
        var tile = (TileAssemblerMatrixPattern) (Object) this;
        if (!tile.isFormed() || !tile.getMainNode().isActive() || !tile.getAvailablePatterns().contains(patternDetails)) {
            return 0;
        }
        return tile.getCluster() instanceof MolecularMatrixCluster cluster
                ? cluster.molecularmanipulator$getBatchLimit(patternDetails, unitOutputs) : 0;
    }

    @Override
    public boolean molecularmanipulator$supportsReusableBatching(
            IPatternDetails patternDetails) {
        return molecularmanipulator$supportsBatching(patternDetails);
    }

    @Override
    public ReusableValidation molecularmanipulator$validateReusableBatch(
            IPatternDetails pattern, MolecularReusableBatchPlan plan, UUID jobId) {
        var tile = (TileAssemblerMatrixPattern) (Object) this;
        return tile.isFormed() && tile.getMainNode().isActive() && tile.getAvailablePatterns().contains(pattern)
                && tile.getCluster() instanceof MolecularMatrixCluster cluster
                ? cluster.molecularmanipulator$validateReusableBatch(pattern, plan, jobId) : ReusableValidation.REJECTED;
    }

    @Override
    public void molecularmanipulator$flushOutputsAfterCpuAccounting() {
        var tile = (TileAssemblerMatrixPattern) (Object) this;
        if (tile.getCluster() instanceof MolecularMatrixCluster cluster) {
            cluster.molecularmanipulator$flushOutputsAfterCpuAccounting();
        }
    }
}
