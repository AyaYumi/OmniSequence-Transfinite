package com.atir.molecularmanipulator.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface CraftingCPUClusterAccessor {
    @Invoker("addBlockEntity")
    void molecularmanipulator$addBlockEntity(CraftingBlockEntity blockEntity);

    @Invoker("done")
    void molecularmanipulator$finishCluster();
}
