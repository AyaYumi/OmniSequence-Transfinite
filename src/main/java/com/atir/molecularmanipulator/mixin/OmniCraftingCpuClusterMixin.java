package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class OmniCraftingCpuClusterMixin {
    @Inject(method = "canBeAutoSelectedFor", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$skipUnavailableAutoSelection(
            IActionSource source, CallbackInfoReturnable<Boolean> callback) {
        var cpu = (CraftingCPUCluster) (Object) this;
        var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
        if (owner != null && (!owner.isStructureFormed()
                || !OmniComputationCoreBlockEntity.isCpuReadyForSubmission(cpu))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$pauseWhenIncomplete(CallbackInfoReturnable<Boolean> callback) {
        var owner = OmniComputationCoreBlockEntity.ownerOf((CraftingCPUCluster) (Object) this);
        if (owner != null && !owner.isStructureFormed()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$infiniteStorage(CallbackInfoReturnable<Long> callback) {
        if (OmniComputationCoreBlockEntity.ownerOf((CraftingCPUCluster) (Object) this) != null) {
            callback.setReturnValue(OmniComputationCoreBlockEntity.INFINITE_STORAGE);
        }
    }

    @Inject(method = "getCoProcessors", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$infiniteParallelism(CallbackInfoReturnable<Integer> callback) {
        if (OmniComputationCoreBlockEntity.ownerOf((CraftingCPUCluster) (Object) this) != null) {
            callback.setReturnValue(OmniComputationCoreBlockEntity.AE2_PARALLELISM_SENTINEL);
        }
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$laneName(CallbackInfoReturnable<Component> callback) {
        var cpu = (CraftingCPUCluster) (Object) this;
        var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
        if (owner != null) {
            callback.setReturnValue(Component.translatable(
                    "gui.molecularmanipulator.omni.cpu_name", owner.laneName(cpu)));
        }
    }
}
