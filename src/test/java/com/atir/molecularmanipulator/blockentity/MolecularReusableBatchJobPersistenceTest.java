package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MolecularReusableBatchJobPersistenceTest {
    @Test
    void missingDamageGroupFinalKeyMeansTheToolDisappears() {
        assertTrue(MolecularReusableBatchJob
                .isValidDamageGroupFinalKey(false, null));
    }

    @Test
    void presentButUnreadableDamageGroupFinalKeyIsRejected() {
        assertFalse(MolecularReusableBatchJob
                .isValidDamageGroupFinalKey(true, null));
    }
}
