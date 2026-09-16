package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forces a complete status snapshot whenever the selected CPU changes jobs. */
@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class CraftingStatusMenuMixin {
    @Shadow @Final private IncrementalUpdateHelper incrementalUpdateHelper;
    @Shadow @Nullable private CraftingCPUCluster cpu;

    @Unique @Nullable
    private Object molecularmanipulator$lastJob;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void molecularmanipulator$refreshChangedJob(CallbackInfo callback) {
        if (cpu == null) {
            molecularmanipulator$lastJob = null;
            return;
        }

        Object currentJob = cpu.craftingLogic.getLastLink();
        if (currentJob == molecularmanipulator$lastJob) {
            return;
        }
        molecularmanipulator$lastJob = currentJob;

        incrementalUpdateHelper.clear();
        var allItems = new KeyCounter();
        cpu.craftingLogic.getAllItems(allItems);
        for (var entry : allItems) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                incrementalUpdateHelper.addChange(entry.getKey());
            }
        }
    }
}
