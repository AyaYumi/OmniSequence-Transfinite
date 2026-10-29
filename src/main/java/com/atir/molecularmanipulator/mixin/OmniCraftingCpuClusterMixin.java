package com.atir.molecularmanipulator.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.OmniCpuResources;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class OmniCraftingCpuClusterMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$pauseWhenIncomplete(CallbackInfoReturnable<Boolean> callback) {
        var owner = OmniComputationCoreBlockEntity.ownerOf((CraftingCPUCluster) (Object) this);
        if (owner != null && !owner.isStructureFormed()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$sharedStorage(CallbackInfoReturnable<Long> callback) {
        var cpu = (CraftingCPUCluster) (Object) this;
        var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
        if (owner != null) callback.setReturnValue(owner.availableCpuStorage(cpu));
    }

    @Inject(method = "getCoProcessors", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$levelParallelism(CallbackInfoReturnable<Integer> callback) {
        var owner = OmniComputationCoreBlockEntity.ownerOf((CraftingCPUCluster) (Object) this);
        if (owner != null) callback.setReturnValue(OmniCpuResources.coProcessors(owner.getParallelismLimit()));
    }

    @Inject(method = "canBeAutoSelectedFor", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$selectEligibleLane(appeng.api.networking.security.IActionSource source,
            CallbackInfoReturnable<Boolean> callback) {
        var cpu = (CraftingCPUCluster) (Object) this;
        var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
        if (owner != null && !owner.canAcceptNewJob(cpu)) callback.setReturnValue(false);
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$laneName(CallbackInfoReturnable<Component> callback) {
        var cpu = (CraftingCPUCluster) (Object) this;
        var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
        if (owner != null) {
            callback.setReturnValue(owner.cpuDisplayName(cpu));
        }
    }
}
