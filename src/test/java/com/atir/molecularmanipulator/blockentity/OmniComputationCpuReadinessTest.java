package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OmniComputationCpuReadinessTest {
    @Test
    void onlyCleanLiveIdleCpusCanAcceptAJob() {
        assertTrue(OmniComputationCoreBlockEntity.isCpuReadyForSubmission(
                false, false, true));

        assertFalse(OmniComputationCoreBlockEntity.isCpuReadyForSubmission(
                true, false, true));
        assertFalse(OmniComputationCoreBlockEntity.isCpuReadyForSubmission(
                false, true, true));
        assertFalse(OmniComputationCoreBlockEntity.isCpuReadyForSubmission(
                false, false, false));
    }
}
