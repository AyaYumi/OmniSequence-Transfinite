package com.atir.molecularmanipulator.mixin;

import java.util.List;

import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftingPlanSummary.class, remap = false)
public interface CraftingPlanSummaryAccessor {
    @Mutable
    @Accessor("entries")
    void molecularmanipulator$setEntries(List<CraftingPlanSummaryEntry> entries);
}
