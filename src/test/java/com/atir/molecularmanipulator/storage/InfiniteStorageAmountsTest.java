package com.atir.molecularmanipulator.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.util.ReadableNumberConverter;
import org.junit.jupiter.api.Test;

class InfiniteStorageAmountsTest {
    @Test
    void usesAe2CompactLongMaximumText() {
        assertEquals(
                ReadableNumberConverter.format(Long.MAX_VALUE, 4),
                InfiniteStorageAmounts.DISPLAY_TEXT);
        assertEquals(
                ReadableNumberConverter.format(Long.MAX_VALUE, 3),
                InfiniteStorageAmounts.LARGE_FONT_DISPLAY_TEXT);
    }
}
