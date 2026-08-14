package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

public interface OmniCraftingTreeNodeBridge {
    AEKey molecularmanipulator$getWhat();

    long molecularmanipulator$getAmount();

    IPatternDetails.IInput molecularmanipulator$getParentInput();

    Level molecularmanipulator$getLevel();

    boolean molecularmanipulator$canEmit();

    ArrayList<CraftingTreeProcess> molecularmanipulator$getProcesses();

    void molecularmanipulator$setProcesses(ArrayList<CraftingTreeProcess> processes);

    void molecularmanipulator$buildChildPatterns();

    Iterable<InputTemplate> molecularmanipulator$getValidItemTemplates(ICraftingInventory inventory);

    void molecularmanipulator$request(CraftingSimulationState inventory, long requestedAmount,
            KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;
}
