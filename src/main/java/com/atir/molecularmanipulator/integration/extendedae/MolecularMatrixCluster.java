package com.atir.molecularmanipulator.integration.extendedae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider.ReusableValidation;

import java.util.UUID;

public interface MolecularMatrixCluster {
    void molecularmanipulator$registerCore(AssemblerMatrixMolecularCoreBlockEntity core);

    boolean molecularmanipulator$hasCore();

    long molecularmanipulator$getBatchLimit();

    long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails, KeyCounter unitOutputs);

    ReusableValidation molecularmanipulator$validateReusableBatch(
            IPatternDetails pattern, MolecularReusableBatchPlan plan, UUID jobId);

    void molecularmanipulator$flushOutputsAfterCpuAccounting();
}
