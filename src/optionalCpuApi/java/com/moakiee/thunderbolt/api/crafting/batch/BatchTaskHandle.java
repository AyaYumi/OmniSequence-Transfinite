package com.moakiee.thunderbolt.api.crafting.batch;

import appeng.api.crafting.IPatternDetails;

/** Compile-only 2.0.3 ABI; never packaged. The mixin plugin requires a matching installed executor API. */
public interface BatchTaskHandle {
    IPatternDetails details();
    long getValue();
    void setValue(long value);
}
