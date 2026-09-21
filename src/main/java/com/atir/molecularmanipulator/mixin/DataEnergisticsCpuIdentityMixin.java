package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGridNode;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps colocated Omni CPU lanes distinct in Data Energistics' candidate selection. */
@Mixin(value = CraftingService.class, priority = 1100, remap = false)
public abstract class DataEnergisticsCpuIdentityMixin {
    @Dynamic("Added by Data Energistics CraftingServiceMixin")
    @Inject(method = "dataEnergistics$nativeCpuStableIdentity", at = @At("HEAD"),
            cancellable = true, require = 0)
    private static void molecularmanipulator$usePersistentLaneIdentity(CraftingCPUCluster cpu,
            IGridNode node, CallbackInfoReturnable<String> callback) {
        var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
        if (owner != null && owner.laneId(cpu) >= 0L) {
            callback.setReturnValue(owner.laneStableId(cpu));
        }
    }
}
