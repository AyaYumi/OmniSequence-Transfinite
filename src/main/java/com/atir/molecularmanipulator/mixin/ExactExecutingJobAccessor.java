package com.atir.molecularmanipulator.mixin;

import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.api.stacks.GenericStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExactExecutingJobAccessor {
    @Accessor("remainingAmount") long molecularmanipulator$getRemaining();
    @Accessor("remainingAmount") void molecularmanipulator$setRemaining(long amount);
    @Accessor("waitingFor") ListCraftingInventory molecularmanipulator$getWaitingFor();
    @Accessor("finalOutput") GenericStack molecularmanipulator$getFinalOutput();
}
