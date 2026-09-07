package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StructureUpdateConfirmationTest {
    @Test void firstAndRapidSecondClickNeverStartAnUpdate() {
        var confirmation = new StructureUpdateConfirmation();
        assertFalse(confirmation.click());
        assertTrue(confirmation.isArmed());
        assertFalse(confirmation.click());
        for (int tick = 0; tick < StructureUpdateConfirmation.DELAY_TICKS; tick++) confirmation.tick(true);
        assertTrue(confirmation.click());
        assertFalse(confirmation.isArmed());
        assertFalse(confirmation.click());
    }

    @Test void timeoutOrCancellationRequiresTwoFreshClicks() {
        var confirmation = new StructureUpdateConfirmation();
        confirmation.click();
        for (int tick = 0; tick < StructureUpdateConfirmation.TIMEOUT_TICKS; tick++) confirmation.tick(true);
        assertFalse(confirmation.isArmed());
        assertFalse(confirmation.click());
        confirmation.cancel();
        assertFalse(confirmation.click());
        confirmation.tick(false);
        assertFalse(confirmation.isArmed());
    }
}
