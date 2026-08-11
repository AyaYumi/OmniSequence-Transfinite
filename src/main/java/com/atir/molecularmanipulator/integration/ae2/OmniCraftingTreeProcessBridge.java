package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeNode;

import java.util.Map;

public interface OmniCraftingTreeProcessBridge {
    IPatternDetails molecularmanipulator$getDetails();

    Map<CraftingTreeNode, Long> molecularmanipulator$getChildNodes();

    boolean molecularmanipulator$hasContainerItems();

    boolean molecularmanipulator$limitsQuantity();

    boolean molecularmanipulator$isPossible();

    void molecularmanipulator$setPossible(boolean possible);
}
