package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingServiceBridge;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Inject(method = "findSuitableCraftingCPU", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$preferOmniForBatchPlans(ICraftingPlan job,
            boolean prioritizePower, IActionSource source,
            MutableObject<UnsuitableCpus> unsuitable,
            CallbackInfoReturnable<CraftingCPUCluster> callback) {
        var selected = callback.getReturnValue();
        if (job == null || job.simulation()
                || selected != null && (OmniComputationCoreBlockEntity.ownerOf(selected) != null
                        || selected.isPreferredFor(source))
                || !molecularmanipulator$hasBatchPlan(job)) {
            return;
        }

        for (var cpu : craftingCPUClusters) {
            var owner = OmniComputationCoreBlockEntity.ownerOf(cpu);
            if (owner != null && owner.isStructureFormed() && cpu.isActive()
                    && !cpu.isBusy() && cpu.getAvailableStorage() >= job.bytes()
                    && cpu.canBeAutoSelectedFor(source)) {
                callback.setReturnValue(cpu);
                return;
            }
        }
    }

    @Unique
    private boolean molecularmanipulator$hasBatchPlan(ICraftingPlan job) {
        try {
            for (var entry : job.patternTimes().entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 1) {
                    continue;
                }
                for (var provider : ((CraftingService) (Object) this).getProviders(entry.getKey())) {
                    if (MolecularBatchCraftingProvider.supports(provider, entry.getKey())) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }
        return false;
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$redirectBusyOmniCpu(ICraftingPlan job,
            ICraftingRequester requestingMachine, ICraftingCPU target, boolean prioritizePower,
            IActionSource source, CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (job.simulation() || !(target instanceof CraftingCPUCluster selected) || !selected.isBusy()) {
            return;
        }
        var owner = OmniComputationCoreBlockEntity.ownerOf(selected);
        if (owner == null || !owner.isStructureFormed()) {
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
