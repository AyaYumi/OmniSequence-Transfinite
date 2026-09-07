package com.atir.molecularmanipulator.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayNumbersTest {
    @Test
    void usesDecimalUnitsThroughExa() {
        assertEquals("0", DisplayNumbers.compact(0L));
        assertEquals("999", DisplayNumbers.compact(999L));
        String[] units = {"K", "M", "G", "T", "P", "E"};
        long value = 1;
        for (String unit : units) {
            value *= 1000;
            assertEquals("1" + unit, DisplayNumbers.compact(value));
            assertEquals("1.25" + unit, DisplayNumbers.compact(value + value / 4));
        }
    }

    @Test
    void roundingCarriesIntoTheNextUnit() {
        assertEquals("999.99K", DisplayNumbers.compact(999_994L));
        assertEquals("1M", DisplayNumbers.compact(999_999L));
        assertEquals("1.1T", DisplayNumbers.compact(1L << 40));
        assertEquals("9.22E", DisplayNumbers.compact(Long.MAX_VALUE));
        assertEquals("-9.22E", DisplayNumbers.compact(Long.MIN_VALUE));
    }

    @Test
    void fullCountsKeepEveryLongDigitAndGroupThousands() {
        assertEquals("1,099,511,627,776", DisplayNumbers.exact(1L << 40));
        assertEquals("9,223,372,036,854,775,807", DisplayNumbers.exact(Long.MAX_VALUE));
        assertEquals("-9,223,372,036,854,775,808", DisplayNumbers.exact(Long.MIN_VALUE));
    }

    @Test
    void powerAndTimeUsePlainDecimalsInTheirDetailedValues() {
        assertEquals("1.23G", DisplayNumbers.compact(1_234_567_890.125));
        assertEquals("1,234,567,890.125", DisplayNumbers.exact(1_234_567_890.125));
        assertEquals("0.001", DisplayNumbers.compact(0.001));
        assertEquals("0.0000001", DisplayNumbers.exact(1e-7));
        assertEquals("∞", DisplayNumbers.compact(Double.POSITIVE_INFINITY));
        assertEquals("—", DisplayNumbers.exact(Double.NaN));
    }
}
