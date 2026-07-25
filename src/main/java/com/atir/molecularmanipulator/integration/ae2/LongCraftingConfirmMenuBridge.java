package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;

public interface LongCraftingConfirmMenuBridge {
    boolean molecularmanipulator$planLong(AEKey what, long amount, CalculationStrategy strategy);
}
