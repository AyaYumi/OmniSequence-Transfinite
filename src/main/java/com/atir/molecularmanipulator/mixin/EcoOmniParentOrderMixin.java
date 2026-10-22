package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniParentOrder;
import com.atir.molecularmanipulator.integration.eco.EcoOmniParentOrderCpu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outer lifecycle boundary; loaded by the existing ECO >=21.2.9 optional-mixin gate. */
@Mixin(value = CraftingCpuLogic.class, priority = 800, remap = false)
public abstract class EcoOmniParentOrderMixin implements EcoOmniParentOrderCpu, EcoOmniParentOrder.Host {
    @Shadow @Final private CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Shadow private void postChange(AEKey key) { throw new AssertionError(); }
    @Unique private EcoOmniParentOrder molecularmanipulator$parent;
    @Unique private CraftingLink molecularmanipulator$parentLink;
    @Unique private ICraftingPlan molecularmanipulator$parentSubmittingPlan;
    @Unique private boolean molecularmanipulator$parentChildSubmission;
    @Unique private CompoundTag molecularmanipulator$parentFailedTag;
    @Unique private String molecularmanipulator$parentProblem = "";

    @Unique private CraftingCpuLogic molecularmanipulator$logic() { return (CraftingCpuLogic) (Object) this; }
    @Unique private EcoOmniPlanCpuAccess molecularmanipulator$access() { return (EcoOmniPlanCpuAccess) this; }
    @Override public String ecoOmni$parentProblem() { return molecularmanipulator$parentProblem; }
    @Override public ECOBigOrderProgress ecoOmni$parentProgress() {
        return molecularmanipulator$parent == null ? null : molecularmanipulator$parent.progress(job == null ? 0
                : ((ExactExecutingJobAccessor) job).molecularmanipulator$getRemaining());
    }

    @WrapMethod(method = "trySubmitJob")
    private ICraftingSubmitResult molecularmanipulator$admitParent(IGrid grid, ICraftingPlan plan,
            IActionSource source, ICraftingRequester requester, Operation<ICraftingSubmitResult> original) {
        if ((!molecularmanipulator$parentChildSubmission || plan != molecularmanipulator$parentSubmittingPlan)
                && (molecularmanipulator$parent != null || molecularmanipulator$parentFailedTag != null))
            return CraftingSubmitResult.CPU_BUSY;
        var request = ECOBigOrderRequest.forSubmission(plan);
        if (request == null || molecularmanipulator$parentChildSubmission
                || !((IOmniCraftingCpu) (Object) this).isOmniMaterialAllocator())
            return original.call(grid, plan, source, requester);
        if (grid == null || cluster.getGrid() != grid || cluster.isDestroyed()) return CraftingSubmitResult.CPU_OFFLINE;
        if (source == null || plan.simulation() || plan.bytes() != 0 || !plan.usedItems().isEmpty()
                || !plan.emittedItems().isEmpty() || !plan.missingItems().isEmpty() || !plan.patternTimes().isEmpty()
                || plan.finalOutput() == null || !request.goal().equals(plan.finalOutput().what())
                || plan.finalOutput().amount() != EcoOmniParentOrder.bounded(request.requested()))
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        final EcoOmniParentOrder prepared;
        try {
            // Validate the source and every preview amount before native acceptance creates a job.
            prepared = new EcoOmniParentOrder(java.util.UUID.randomUUID(), request, source);
        } catch (RuntimeException invalid) { return CraftingSubmitResult.INCOMPLETE_PLAN; }
        // The carrier contains no stock or tasks. AE2 creates the real job, requester link and player identity.
        var submitted = EcoOmniParentOrder.submitCarrier(plan, () -> original.call(grid, plan, source, requester));
        if (submitted.successful()) {
            molecularmanipulator$parentLink = (CraftingLink) molecularmanipulator$logic().getLastLink();
            molecularmanipulator$parent = prepared.bind(molecularmanipulator$parentLink.getCraftingID());
            molecularmanipulator$bindStandaloneParent();
            ecoParent$dirty();
        }
        return submitted;
    }

    @WrapOperation(method = "trySubmitJob", at = @At(value = "NEW", target = "(Lnet/minecraft/nbt/CompoundTag;Lappeng/api/networking/crafting/ICraftingCPU;)Lappeng/crafting/CraftingLink;"), require = 1)
    private CraftingLink molecularmanipulator$reuseParentCpuLink(CompoundTag data, ICraftingCPU cpu,
            Operation<CraftingLink> original) {
        return molecularmanipulator$parentChildSubmission ? molecularmanipulator$parentLink : original.call(data, cpu);
    }

    @WrapMethod(method = "tickCraftingLogic")
    private void molecularmanipulator$tickParent(IEnergyService energy, CraftingService crafting, Operation<Void> original) {
        var parent = molecularmanipulator$parent;
        if (molecularmanipulator$parentFailedTag != null) return;
        if (parent == null) { original.call(energy, crafting); return; }
        if (job == null) { molecularmanipulator$parentProblem = "PARENT_JOB_MISSING"; return; }
        if (molecularmanipulator$parentLink.isCanceled()) { molecularmanipulator$logic().cancel(); return; }
        if (!cluster.isActive() || !((IOmniCraftingCpu) (Object) this).isOmniMaterialAllocator()
                || molecularmanipulator$logic().isJobSuspended() || molecularmanipulator$access().ecoOmni$paused()) return;
        if (parent.running()) original.call(energy, crafting);
        else parent.tick(this);
        if (molecularmanipulator$parent != null) cluster.updateOutput(molecularmanipulator$parent.output());
    }

    @Override public IGrid ecoParent$grid() { return cluster.getGrid(); }
    @Override public net.minecraft.server.MinecraftServer ecoParent$server() {
        var level = cluster.getLevel();
        return level == null ? null : level.getServer();
    }
    @Override public long ecoParent$storage() { return cluster.getAvailableStorage(); }
    @Override public boolean ecoParent$flushInventory() {
        var held = job;
        try {
            job = null;
            molecularmanipulator$logic().storeItems();
            return molecularmanipulator$logic().getInventory().list.isEmpty()
                    && !((IOmniCraftingCpu) (Object) this).hasExactStoredItems();
        } finally { job = held; }
    }
    @Override public ICraftingSubmitResult ecoParent$submitChild(ICraftingPlan plan, IActionSource source) {
        var held = job;
        molecularmanipulator$parentChildSubmission = true;
        molecularmanipulator$parentSubmittingPlan = plan;
        try {
            job = null;
            var submitted = molecularmanipulator$logic().trySubmitJob(ecoParent$grid(), plan, source, null);
            if (!submitted.successful()) job = held;
            return submitted;
        } catch (RuntimeException | Error failure) {
            if (job == null) job = held;
            throw failure;
        } finally {
            molecularmanipulator$parentSubmittingPlan = null;
            molecularmanipulator$parentChildSubmission = false;
        }
    }
    @Override public void ecoParent$dirty() {
        cluster.markDirty();
        if (molecularmanipulator$parent != null) postChange(molecularmanipulator$parent.output().what());
    }

    @WrapMethod(method = "finishJob")
    private void molecularmanipulator$finishParentChild(boolean success, Operation<Void> original) {
        var parent = molecularmanipulator$parent;
        if (parent == null) { original.call(success); return; }
        if (success) {
            var exact = molecularmanipulator$access().ecoOmni$exactState();
            var execution = ((EcoOmniPlanCpu) this).ecoOmni$getExecution();
            // This outer guard runs before the inner exact wrappers clear their completed state.
            if (!parent.running() || exact == null || exact.outputProgress() == null
                    || exact.outputProgress().remaining().signum() != 0 || execution == null || !execution.isComplete()
                    || !((ExactExecutingJobAccessor) job).molecularmanipulator$getWaitingFor().list.isEmpty()
                    || !exact.uncreditedOutputs().isEmpty()) return;
        }
        var completed = job;
        original.call(success);
        // Inner accounting/suspension guards may retain the job. Never advance an uncommitted finish.
        if (job != null) return;
        if (!success) { parent.cancel(); molecularmanipulator$clearParent(); return; }
        if (parent.childDelivered()) { molecularmanipulator$clearParent(); return; }
        // Reuse the actual finished AE2 job as the idle carrier. No fake CPU and no inaccessible constructor.
        job = completed;
        molecularmanipulator$access().ecoOmni$tasks().clear();
        ((ExactExecutingJobAccessor) job).molecularmanipulator$setRemaining(parent.output().amount());
        cluster.updateOutput(parent.output());
        ecoParent$dirty();
    }

    @WrapOperation(method = "finishJob", at = @At(value = "INVOKE", target = "Lappeng/crafting/CraftingLink;markDone()V"), require = 1)
    private void molecularmanipulator$deferParentDone(CraftingLink link, Operation<Void> original) {
        if (molecularmanipulator$parent == null || molecularmanipulator$parent.lastChild()) original.call(link);
    }

    @WrapOperation(method = {"trySubmitJob", "finishJob"}, at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;notifyJobOwner(Lappeng/crafting/execution/ExecutingCraftingJob;Lappeng/core/network/clientbound/CraftingJobStatusPacket$Status;)V"), require = 2)
    private void molecularmanipulator$notifyOnlyParent(CraftingCpuLogic logic, ExecutingCraftingJob current,
            CraftingJobStatusPacket.Status status, Operation<Void> original) {
        if (molecularmanipulator$parentChildSubmission) return;
        if (status == CraftingJobStatusPacket.Status.FINISHED && molecularmanipulator$parent != null
                && !molecularmanipulator$parent.lastChild()) return;
        original.call(logic, current, status);
    }

    @WrapMethod(method = "cancel")
    private void molecularmanipulator$cancelParent(Operation<Void> original) {
        if (molecularmanipulator$parentChildSubmission && molecularmanipulator$parent != null) {
            // Exact attachment can reject after native extraction creates a child. It has not dispatched yet.
            // Refund that physical admission without canceling the accepted parent's UUID/lifecycle.
            job = null;
            ((EcoOmniPlanCpu) this).ecoOmni$setExecution(null);
            molecularmanipulator$access().ecoOmni$installExactState(null);
            molecularmanipulator$logic().storeItems();
            cluster.markDirty();
            return;
        }
        if (molecularmanipulator$parent != null) molecularmanipulator$parent.stopPlanning();
        original.call();
        if (job == null) molecularmanipulator$clearParent();
    }

    @Unique private void molecularmanipulator$clearParent() {
        molecularmanipulator$parent = null;
        molecularmanipulator$parentLink = null;
        molecularmanipulator$parentFailedTag = null;
        molecularmanipulator$parentProblem = "";
        cluster.markDirty();
    }
    @Unique private void molecularmanipulator$bindStandaloneParent() {
        if (molecularmanipulator$parentLink.isStandalone())
            molecularmanipulator$parentLink.setNexus(new CraftingLinkNexus(molecularmanipulator$parentLink.getCraftingID()));
    }

    @Inject(method = "hasJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$reserveParentCpu(CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$parent != null || molecularmanipulator$parentFailedTag != null) callback.setReturnValue(true);
    }
    @Inject(method = "getLastLink", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$keepParentLinkVisible(CallbackInfoReturnable<appeng.api.networking.crafting.ICraftingLink> callback) {
        if (molecularmanipulator$parentLink != null) callback.setReturnValue(molecularmanipulator$parentLink);
    }
    @Inject(method = "getFinalJobOutput", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$showParentOutput(CallbackInfoReturnable<GenericStack> callback) {
        if (molecularmanipulator$parent != null && !molecularmanipulator$parent.running())
            callback.setReturnValue(molecularmanipulator$parent.output());
    }
    @WrapMethod(method = "getPendingOutputs")
    private long molecularmanipulator$parentPending(AEKey key, Operation<Long> original) {
        long pending = original.call(key);
        if (molecularmanipulator$parent == null) return pending;
        var exact = molecularmanipulator$parent.pending().get(key);
        return exact == null ? pending : Math.max(pending, EcoOmniParentOrder.bounded(exact));
    }
    @WrapMethod(method = "getAllItems")
    private void molecularmanipulator$parentPreview(KeyCounter out, Operation<Void> original) {
        original.call(out);
        if (molecularmanipulator$parent != null) molecularmanipulator$parent.pending().forEach((key, amount) ->
                out.set(key, Math.max(out.get(key), EcoOmniParentOrder.bounded(amount))));
    }

    @WrapMethod(method = "writeToNBT")
    private void molecularmanipulator$saveParent(CompoundTag data, HolderLookup.Provider registries, Operation<Void> original) {
        original.call(data, registries);
        if (molecularmanipulator$parentFailedTag != null)
            data.put(EcoOmniParentOrder.NBT_KEY, molecularmanipulator$parentFailedTag.copy());
        else if (molecularmanipulator$parent != null) data.put(EcoOmniParentOrder.NBT_KEY, molecularmanipulator$parent.write(registries));
        else data.remove(EcoOmniParentOrder.NBT_KEY);
    }
    @WrapMethod(method = "readFromNBT")
    private void molecularmanipulator$loadParent(CompoundTag data, HolderLookup.Provider registries, Operation<Void> original) {
        if (molecularmanipulator$parent != null) molecularmanipulator$parent.stopPlanning();
        molecularmanipulator$parent = null;
        molecularmanipulator$parentLink = null;
        molecularmanipulator$parentProblem = "";
        molecularmanipulator$parentFailedTag = data.contains(EcoOmniParentOrder.NBT_KEY)
                ? data.getCompound(EcoOmniParentOrder.NBT_KEY).copy() : null;
        original.call(data, registries);
        if (molecularmanipulator$parentFailedTag == null) return;
        try {
            var parent = EcoOmniParentOrder.read(molecularmanipulator$parentFailedTag, registries);
            var link = molecularmanipulator$logic().getLastLink();
            if (job == null || link == null || !parent.id().equals(link.getCraftingID()))
                throw new IllegalArgumentException("Parent job/link identity missing or changed");
            if (parent.running() && (molecularmanipulator$access().ecoOmni$exactState() == null
                    || ((EcoOmniPlanCpu) this).ecoOmni$getExecution() == null))
                throw new IllegalArgumentException("Parent child lost its exact execution state");
            molecularmanipulator$parent = parent;
            molecularmanipulator$parentLink = (CraftingLink) link;
            molecularmanipulator$bindStandaloneParent();
            molecularmanipulator$parentFailedTag = null;
        } catch (RuntimeException | LinkageError failure) {
            molecularmanipulator$parentProblem = failure.toString();
            molecularmanipulator$logic().setJobSuspended(true);
            org.slf4j.LoggerFactory.getLogger(EcoOmniParentOrder.class).warn("Suspended saved ECO Omni parent; original parent tag retained", failure);
        }
    }
}
