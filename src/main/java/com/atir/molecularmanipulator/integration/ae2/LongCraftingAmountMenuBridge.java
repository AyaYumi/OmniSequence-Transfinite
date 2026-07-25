package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.stacks.AEKey;

public interface LongCraftingAmountMenuBridge {
    void molecularmanipulator$setWhatToCraftLong(AEKey whatToCraft, long initialAmount);

    void molecularmanipulator$confirmLong(long amount, boolean craftMissingAmount, boolean autoStart);
}
