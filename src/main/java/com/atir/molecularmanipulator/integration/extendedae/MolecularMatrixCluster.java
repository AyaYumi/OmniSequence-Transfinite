package com.atir.molecularmanipulator.integration.extendedae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;

public interface MolecularMatrixCluster {
    void molecularmanipulator$registerCore(AssemblerMatrixMolecularCoreBlockEntity core);

    boolean molecularmanipulator$hasCore();

    long molecularmanipulator$getBatchLimit();

    long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails, KeyCounter unitOutputs);

    void molecularmanipulator$flushOutputsAfterCpuAccounting();
}
