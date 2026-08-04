package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeProcessBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface OmniCraftingTreeProcessAccessor extends OmniCraftingTreeProcessBridge {
    @Override
    @Accessor("details")
    IPatternDetails molecularmanipulator$getDetails();

    @Override
    @Accessor("nodes")
    Map<CraftingTreeNode, Long> molecularmanipulator$getChildNodes();

    @Override
    @Accessor("containerItems")
    boolean molecularmanipulator$hasContainerItems();

    @Override
    @Accessor("limitQty")
    boolean molecularmanipulator$limitsQuantity();

    @Override
    @Accessor("possible")
    boolean molecularmanipulator$isPossible();

    @Override
    @Accessor("possible")
    void molecularmanipulator$setPossible(boolean possible);
}
