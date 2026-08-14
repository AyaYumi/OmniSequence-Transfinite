package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingServiceBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = CraftingService.class, remap = false)
public abstract class OmniCraftingServiceMixin implements OmniCraftingServiceBridge {
    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Inject(method = "updateCPUClusters", at = @At("RETURN"))
    private void molecularmanipulator$appendOmniCpus(CallbackInfo callback) {
        molecularmanipulator$refreshOmniCpus();
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void molecularmanipulator$keepSpareCpu(ICraftingPlan job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource source,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        molecularmanipulator$refreshOmniCpus();
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$redirectUnavailableOmniCpu(ICraftingPlan job,
            ICraftingRequester requestingMachine, ICraftingCPU target, boolean prioritizePower,
            IActionSource source, CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (job.simulation() || !(target instanceof CraftingCPUCluster selected)) {
            return;
        }
        var owner = OmniComputationCoreBlockEntity.ownerOf(selected);
        if (owner == null || !owner.isStructureFormed()) {
            return;
        }
        if (OmniComputationCoreBlockEntity.isCpuReadyForSubmission(selected)) {
            return;
        }
        var replacement = owner.getOrCreateIdleCpu((OmniCraftingServiceBridge) this);
        if (replacement == null || replacement == selected) {
            return;
        }
        callback.setReturnValue(replacement.submitJob(grid, job, source, requestingMachine));
        owner.ensureSpareAndRegister((OmniCraftingServiceBridge) this);
    }

    @Override
    public void molecularmanipulator$registerOmniCpu(CraftingCPUCluster cluster) {
        if (cluster == null || cluster.isDestroyed()) {
            return;
        }
        craftingCPUClusters.add(cluster);
        var link = cluster.craftingLogic.getLastLink();
        if (link instanceof CraftingLink craftingLink) {
            addLink(craftingLink);
        }
    }

    @Override
    public void molecularmanipulator$unregisterOmniCpu(CraftingCPUCluster cluster) {
        craftingCPUClusters.remove(cluster);
    }

    private void molecularmanipulator$refreshOmniCpus() {
        var bridge = (OmniCraftingServiceBridge) this;
        for (var controller : grid.getMachines(OmniComputationCoreBlockEntity.class)) {
            if (controller.isStructureFormed()) {
                controller.ensureSpareAndRegister(bridge);
            }
        }
    }

}
