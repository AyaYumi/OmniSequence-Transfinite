package com.atir.molecularmanipulator.crafting;

/** Internal timing bridge between composable CPU mixins. */
public interface OmniExactReturnTiming {
    boolean omnisequence$isProfilingReturn();
    void omnisequence$addReturnInventoryTime(long nanos);
    void omnisequence$addReturnAccountingTime(long nanos);
}
