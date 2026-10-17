package com.atir.molecularmanipulator.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MolecularAutoCraftRetryTest {
    @Test
    void blockedSlotsBackOffButSettingsChangesWakeThemImmediately() {
        var retry = new MolecularAutoCraftRetry();
        assertTrue(retry.ready(100));
        retry.blocked(100);
        assertFalse(retry.ready(104));
        assertTrue(retry.ready(105));
        retry.blocked(105);
        assertFalse(retry.ready(114));
        assertTrue(retry.ready(115));
        retry.blocked(115);
        assertFalse(retry.ready(134));
        assertTrue(retry.ready(135));
        retry.blocked(135);
        assertTrue(retry.ready(155));
        retry.reset();
        assertTrue(retry.ready(136));
        retry.blocked(136);
        assertTrue(retry.ready(141));
    }
}
