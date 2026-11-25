package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutputReceiver;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.MolecularExternalScaledPattern;
import com.atir.molecularmanipulator.crafting.OmniNativeSmartDoubling;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.crafting.OmniCpuResources;
import com.atir.molecularmanipulator.integration.ae2.OmniCpuStorage;
import com.atir.molecularmanipulator.crafting.OmniExactInventory;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.appliedenhancements.api.AelisCycleRuntimeController;
import com.appliedenhancements.runtime.AelisCycleDispatchScope;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchHost;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchLedger;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchReceipt;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchRunner;
import com.atir.molecularmanipulator.crafting.MatterCraftingJobContext;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityDispatcher;
import com.atir.molecularmanipulator.crafting.dispatch.OmniDispatchAllowance;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 1200)
public abstract class CraftingCpuLogicMixin implements IOmniCraftingCpu, OmniCpuStorage,
        com.appliedenhancements.api.AelisExactCraftingCpu, EcoOmniPlanCpuAccess {
    @Unique private static final String MOLECULARMANIPULATOR_STORAGE_TAG = "molecularmanipulator:cpuStorageBytes";
    @Unique private BigInteger molecularmanipulator$allocatedStorage = BigInteger.ZERO;

    @Override public BigInteger omni$occupiedStorageBytes() {
        var amounts = new HashMap<>(aelis$getStoredOutputs());
        molecularmanipulator$dispatchLedger.finalStock().forEach((key, amount) -> amounts.merge(key, amount, BigInteger::add));
        var physical = BigInteger.ZERO;
        for (var entry : amounts.entrySet()) {
            physical = physical.add(OmniCpuResources.itemBytes(entry.getValue(), entry.getKey().getAmountPerByte()));
        }
        return job == null ? physical : physical.max(molecularmanipulator$allocatedStorage);
    }

    @WrapMethod(method = "trySubmitJob")
    private ICraftingSubmitResult molecularmanipulator$admitSharedStorage(IGrid grid, ICraftingPlan plan,
            IActionSource source, ICraftingRequester requester,
            Operation<ICraftingSubmitResult> original) {
        var owner = OmniComputationCoreBlockEntity.ownerOf(cluster);
        if (owner == null) return original.call(grid, plan, source, requester);
        if (job != null) return appeng.crafting.execution.CraftingSubmitResult.CPU_BUSY;
        boolean continuing = this instanceof OmniCpuDispatchRunner runner && runner.omniDispatch$continuingJob();
        if (!owner.canAcceptNewJob(cluster) && !continuing)
            return appeng.crafting.execution.CraftingSubmitResult.CPU_BUSY;
        final BigInteger required;
        try {
            var bytes = this instanceof OmniCpuDispatchRunner runner
                    ? runner.omniDispatch$storageBytes(plan) : BigInteger.valueOf(plan.bytes());
            var initial = BigInteger.ZERO;
            for (var entry : plan.usedItems()) {
                initial = initial.add(OmniCpuResources.itemBytes(
                        BigInteger.valueOf(entry.getLongValue()), entry.getKey().getAmountPerByte()));
            }
            if (bytes == null || bytes.signum() < 0)
                return appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN;
            required = bytes.max(initial);
        } catch (RuntimeException | LinkageError invalid) {
            return appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN;
        }
        try (var reservation = owner.reserveCpuStorage(cluster, required)) {
            if (reservation == null) return appeng.crafting.execution.CraftingSubmitResult.CPU_TOO_SMALL;
            var previous = molecularmanipulator$allocatedStorage;
            molecularmanipulator$allocatedStorage = reservation.bytes();
            boolean accepted = false;
            try {
                var result = original.call(grid, plan, source, requester);
                accepted = result.successful();
                return result;
            } finally {
                if (!accepted) molecularmanipulator$allocatedStorage = previous;
            }
        }
    }

    @Unique private final OmniCpuDispatchLedger molecularmanipulator$dispatchLedger = new OmniCpuDispatchLedger();
    @Shadow public abstract ListCraftingInventory getInventory();
    @Shadow public abstract appeng.api.networking.crafting.ICraftingLink getLastLink();
    @Shadow public abstract void setJobSuspended(boolean suspended);
    @Shadow public abstract boolean isJobSuspended();
    @Shadow public abstract int executeCrafting(int maximum, CraftingService crafting, IEnergyService energy, Level level);

    @Shadow private void finishJob(boolean success) { throw new AssertionError(); }
    @Unique private final OmniExactInventory molecularmanipulator$exactInventory = new OmniExactInventory();
    @Unique private static final String MOLECULARMANIPULATOR_EXACT_INVENTORY_TAG = "molecularmanipulator:exactInventory";
    @Override public boolean hasExactStoredItems() { return !molecularmanipulator$exactInventory.isEmpty(); }
    @Override public void clearExactStoredItems() { molecularmanipulator$exactInventory.clear(); }

    @WrapOperation(method = "insert", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/inv/ListCraftingInventory;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)V"))
    private void molecularmanipulator$storeExactIntermediate(ListCraftingInventory inventory,
            AEKey key, long amount, Actionable mode, Operation<Void> original) {
        if (mode != Actionable.MODULATE || !molecularmanipulator$ownsJobMaterials()) {
            original.call(inventory, key, amount, mode);
            return;
        }
        molecularmanipulator$exactInventory.insertWithoutNotification(inventory, key, BigInteger.valueOf(amount));
        molecularmanipulator$notifyOwnedOutput(key);
    }

    @Inject(method = "storeItems", at = @At("HEAD"))
    private void molecularmanipulator$exposeExactRefunds(CallbackInfo callback) {
        if (job == null && molecularmanipulator$exactInventory.refill(getInventory())) cluster.markDirty();
    }

    @Inject(method = "storeItems", at = @At("RETURN"))
    private void molecularmanipulator$retainExactRefundWindow(CallbackInfo callback) {
        if (job == null && molecularmanipulator$exactInventory.refill(getInventory())) cluster.markDirty();
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$waitForExactRefunds(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (molecularmanipulator$dispatchLedger.paused() || molecularmanipulator$dispatchLedger.staging()
                || !molecularmanipulator$exactInventory.isEmpty()) callback.setReturnValue(
                appeng.crafting.execution.CraftingSubmitResult.CPU_BUSY);
    }
    @Override public Map<AEKey, BigInteger> aelis$getActiveOutputs() {
        var state = molecularmanipulator$exactState;
        if (state == null || job == null) return Map.of();
        var result = new HashMap<AEKey, BigInteger>(state.uncreditedOutputs());
        var waiting = ((ExactExecutingJobAccessor) job).molecularmanipulator$getWaitingFor();
        for (var entry : waiting.list) {
            if (entry.getLongValue() > 0) result.merge(entry.getKey(),
                    BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
        }
        return Map.copyOf(result);
    }
    @Override public Map<AEKey, BigInteger> aelis$getStoredOutputs() {
        var result = new HashMap<AEKey, BigInteger>(molecularmanipulator$exactInventory.overflow());
        for (var entry : ((CraftingCpuLogic) (Object) this).getInventory().list) {
            if (entry.getLongValue() > 0) result.merge(entry.getKey(),
                    BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
        }
        return Map.copyOf(result);
    }
    @Override public Map<AEKey, BigInteger> aelis$getCompletedOutputs() {
        return molecularmanipulator$exactState == null || job == null ? Map.of()
                : molecularmanipulator$exactState.completedOutputs();
    }
    @Override public Map<AEKey, com.appliedenhancements.api.AelisCraftingBatch> aelis$getLastBatches() {
        return molecularmanipulator$exactState == null || job == null ? Map.of()
                : molecularmanipulator$exactState.lastBatches();
    }

    @WrapMethod(method = "getAllItems")
    private void molecularmanipulator$includeCompletedRows(KeyCounter items, Operation<Void> original) {
        original.call(items);
        for (var key : aelis$getLastBatches().keySet()) {
            if (items.get(key) == 0) items.add(key, 1);
        }
    }
    @Override public Map<AEKey, java.math.BigInteger> aelis$getPendingOutputs() {
        var state = molecularmanipulator$exactState;
        if (state == null || job == null) return Map.of();
        return com.appliedenhancements.runtime.ExactCraftingStatus.pending(
                molecularmanipulator$getTasks(job).keySet(), state::remaining);
    }
    @Override public java.math.BigInteger aelis$getRemainingOutput() {
        return molecularmanipulator$exactState == null || molecularmanipulator$exactState.outputProgress() == null
                ? null : molecularmanipulator$exactState.outputProgress().remaining();
    }

    @Unique private boolean molecularmanipulator$deliveringFinal;

    @WrapMethod(method = "insert")
    private long molecularmanipulator$settleNativeReturn(AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (molecularmanipulator$deliveringFinal || molecularmanipulator$dispatchLedger.paused()) return 0L;
        if (job == null || key == null || mode != Actionable.MODULATE || !molecularmanipulator$ownsJobMaterials()) {
            return original.call(key, amount, mode);
        }
        var currentJob = job;
        var waiting = ((ExactExecutingJobAccessor) currentJob).molecularmanipulator$getWaitingFor();
        long before = waiting.list.get(key);
        long accepted = original.call(key, amount, mode);
        long settled = before - waiting.list.get(key);
        if (job == currentJob && settled > 0) {
            var state = molecularmanipulator$exactState;
            molecularmanipulator$dispatchLedger.received(key);
            // Refill after AELIS observes the real long-window withdrawal for its cycle phase.
            molecularmanipulator$dispatchLedger.refill(waiting, state, key);
            if (state != null) state.recordCompleted(key, settled);
            molecularmanipulator$notifyOwnedOutput(key);
        }
        return accepted;
    }

    @WrapOperation(method = "insert", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingLink;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long molecularmanipulator$ownExactFinalOutput(appeng.crafting.CraftingLink link,
            AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (!molecularmanipulator$ownsJobMaterials()) return original.call(link, key, amount, mode);
        // AE2 has withdrawn this expected amount from waiting. Take ownership of all of it,
        // including feedback and surplus, before acknowledging the producer. This call can be
        // inside NetworkStorage.insert, so destination delivery must wait for the CPU tick.
        if (mode == Actionable.MODULATE) {
            molecularmanipulator$exactInventory.insertWithoutNotification(getInventory(), key, BigInteger.valueOf(amount));
            molecularmanipulator$notifyOwnedOutput(key);
        }
        return amount;
    }

    @WrapOperation(method = "insert", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(JJ)J"))
    private long molecularmanipulator$deferFinalDelivery(long zero, long remaining, Operation<Long> original) {
        // Accounting production does not deliver the goal. Do not let AE2's receipt path deduct it.
        return molecularmanipulator$ownsJobMaterials() ? ((ExactExecutingJobAccessor) job).molecularmanipulator$getRemaining()
                : original.call(zero, remaining);
    }

    @Unique
    private boolean molecularmanipulator$ownsJobMaterials() {
        return molecularmanipulator$exactState != null || isOmniMaterialAllocator();
    }
    @Unique
    private static final String MOLECULARMANIPULATOR_EXACT_STATE_TAG =
            "molecularmanipulator:exactCraftingState";
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow @Final private int[] usedOps;
    @Shadow
    public abstract long getWaitingFor(AEKey template);
    @Shadow private void postChange(AEKey key) { throw new AssertionError(); }

    @Unique private ICraftingInventory molecularmanipulator$exactInventoryView(ICraftingInventory window) {
        return molecularmanipulator$exactInventory.wrap(window, key -> {
            postChange(key);
            cluster.markDirty();
        });
    }

    @Override
    public boolean isOmniMaterialAllocator() {
        return OmniComputationCoreBlockEntity.ownerOf(cluster) != null;
    }

    @Unique
    private static volatile Field molecularmanipulator$tasksField;
    @Unique
    private static volatile Field molecularmanipulator$taskValueField;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionAvailable = true;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionFailureLogged;
    @Unique private boolean molecularmanipulator$migrateLoadedTasks;
    @Unique
    private OmniExactCraftingState molecularmanipulator$exactState;

    @Inject(method = "trySubmitJob", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$attachExactCraftingState(
            IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (!callback.getReturnValue().successful() || !isOmniMaterialAllocator()) return;
        molecularmanipulator$migrateLoadedTasks = false;
        molecularmanipulator$dispatchLedger.exactOrder(
                this instanceof OmniCpuDispatchRunner runner && runner.omniDispatch$isExactPlan(plan));
        if (ecoOmni$managed()) return; // ECO binds the original task identities to its phase schedule.
        try {
            var metadata = AelisExactCraftingPlanApi.read(plan);
            if (metadata.executionRequirement().requiresExactExecution()) {
                // Initial extraction populated a long projection of the infinite inputs. Never refund it as stock.
                molecularmanipulator$discardProjectedInfiniteInputs(metadata.infiniteInputs().keySet());
                molecularmanipulator$normalizeExternalScaledTasks(job, metadata.patternTimes(),
                        metadata.infiniteInputs(), metadata.finalOutputAmount());
                molecularmanipulator$dispatchLedger.exactOrder(true);
            } else {
                molecularmanipulator$normalizeExternalScaledTasks(job, null, Map.of(), null);
            }
        } catch (RuntimeException | LinkageError exception) {
            RateLimitedLog.error("Could not bind exact Omni crafting tasks; refunding submitted physical inputs",
                    exception);
            ((CraftingCpuLogic) (Object) this).cancel();
            callback.setReturnValue(
                    appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void molecularmanipulator$writeExactCraftingState(
            CompoundTag data, HolderLookup.Provider registries,
            CallbackInfo callback) {
        data.putString(MOLECULARMANIPULATOR_STORAGE_TAG, molecularmanipulator$allocatedStorage.toString());
        data.put(OmniCpuDispatchLedger.NBT_KEY, molecularmanipulator$dispatchLedger.write(registries));
        data.put(MOLECULARMANIPULATOR_EXACT_INVENTORY_TAG, molecularmanipulator$exactInventory.write(registries));
        if (job != null && molecularmanipulator$exactState != null) {
            data.put(MOLECULARMANIPULATOR_EXACT_STATE_TAG,
                    molecularmanipulator$exactState.write(registries));
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void molecularmanipulator$readExactCraftingState(
            CompoundTag data, HolderLookup.Provider registries,
            CallbackInfo callback) {
        // Older jobs had no persisted capacity accounting. Retain them conservatively until completion.
        molecularmanipulator$allocatedStorage = job == null ? BigInteger.ZERO : BigInteger.valueOf(Long.MAX_VALUE);
        if (data.contains(MOLECULARMANIPULATOR_STORAGE_TAG, Tag.TAG_STRING)) {
            try {
                molecularmanipulator$allocatedStorage = new BigInteger(data.getString(MOLECULARMANIPULATOR_STORAGE_TAG))
                        .max(BigInteger.ZERO);
            } catch (NumberFormatException invalid) {
                // Preserve the existing job and conservative allocation instead of discarding its inventory.
            }
        }
        molecularmanipulator$exactInventory.read(data.getList(MOLECULARMANIPULATOR_EXACT_INVENTORY_TAG,
                Tag.TAG_COMPOUND), registries);
        molecularmanipulator$exactInventory.refill(getInventory());
        molecularmanipulator$exactState = null;
        molecularmanipulator$migrateLoadedTasks = job != null;
        if (job != null && molecularmanipulator$dispatchLedger.paused()) setJobSuspended(true);
        if (!data.contains(MOLECULARMANIPULATOR_EXACT_STATE_TAG,
                Tag.TAG_COMPOUND)) {
            return;
        }
        // The primary cluster restores its NBT before the core registers CPU_OWNERS.
        // The explicit saved-state tag is authoritative; do not silently lose its ledger.
        if (job == null) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                    "Discarded exact Omni crafting state without a live job");
            return;
        }
        try {
            var state = OmniExactCraftingState.read(
                    data.getCompound(MOLECULARMANIPULATOR_EXACT_STATE_TAG),
                    registries).rebind(
                            molecularmanipulator$getTasks(job).keySet(),
                            cluster.getLevel());
            if (state.outputProgress() == null) state.setOutputRemaining(java.math.BigInteger.valueOf(
                    ((ExactExecutingJobAccessor) job).molecularmanipulator$getRemaining()));
            molecularmanipulator$exactState = state;
            if (!molecularmanipulator$normalizeExternalScaledTasks(job, null, Map.of(), null)) {
                molecularmanipulator$installExactState(state);
            }
            ((ExactExecutingJobAccessor) job).molecularmanipulator$setRemaining(state.outputProgress().window());
            molecularmanipulator$migrateLoadedTasks = false;
        } catch (RuntimeException | LinkageError exception) {
            molecularmanipulator$exactState = null;
            if (ecoOmni$managed()) {
                com.atir.molecularmanipulator.diagnostics.RateLimitedLog.error(
                        "Could not restore managed CPU exact state; saved job is retained and suspended", exception);
                return;
            }
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.error(
                    "Could not restore exact Omni crafting execution; "
                            + "the job was cancelled to prevent long truncation",
                    exception);
            ((CraftingCpuLogic) (Object) this).cancel();
        }
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void molecularmanipulator$resetPreviousExactInventory(CompoundTag data,
            HolderLookup.Provider registries, CallbackInfo callback) {
        if (molecularmanipulator$capacityDispatcher != null) molecularmanipulator$capacityDispatcher.clear();
        // The base loader may cancel an invalid saved job and attempt a refund.
        // Never combine that new physical window with this object's old overflow.
        molecularmanipulator$dispatchLedger.read(data.getCompound(OmniCpuDispatchLedger.NBT_KEY), registries);
        molecularmanipulator$exactState = null;
        molecularmanipulator$migrateLoadedTasks = false;
        if (molecularmanipulator$dispatchLedger.paused() && this instanceof OmniCpuDispatchRunner runner)
            runner.omniDispatch$suspend("Saved CPU dispatch accounting requires reconciliation");
        molecularmanipulator$exactInventory.clear();
    }

    @Inject(method = "finishJob", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$holdDispatchAccounting(boolean success, CallbackInfo callback) {
        if (!success) {
            molecularmanipulator$dispatchLedger.refundFinal((key, amount) ->
                    molecularmanipulator$exactInventory.insertWithoutNotification(getInventory(), key, amount));
            setJobSuspended(false);
            return;
        }
        if (molecularmanipulator$dispatchLedger.paused() || molecularmanipulator$dispatchLedger.staging()
                || molecularmanipulator$dispatchLedger.hasFinalStock()) {
            callback.cancel();
            return;
        }
        if (job != null && molecularmanipulator$ownsJobMaterials()
                && (molecularmanipulator$executing || job == molecularmanipulator$deferredCancel
                    || !((ExactExecutingJobAccessor) job).molecularmanipulator$getWaitingFor().list.isEmpty()
                    || (molecularmanipulator$exactState != null
                        && !molecularmanipulator$exactState.uncreditedOutputs().isEmpty())
                    || !molecularmanipulator$getTasks(job).isEmpty())) {
            callback.cancel();
        }
    }

    @Inject(method = "finishJob", at = @At("RETURN"))
    private void molecularmanipulator$clearExactCraftingState(
            boolean success, CallbackInfo callback) {
        molecularmanipulator$dispatchLedger.refundFinal((key, amount) ->
                molecularmanipulator$exactInventory.insertWithoutNotification(getInventory(), key, amount));
        molecularmanipulator$exactState = null;
        if (molecularmanipulator$capacityDispatcher != null) molecularmanipulator$capacityDispatcher.clear();
        molecularmanipulator$migrateLoadedTasks = false;
    }

    @Unique private OmniDispatchAllowance molecularmanipulator$allowance;
    @Unique private ExecutingCraftingJob molecularmanipulator$dispatchJob;
    @Unique private ExecutingCraftingJob molecularmanipulator$deferredCancel;
    @Unique private OmniCapacityDispatcher molecularmanipulator$capacityDispatcher;
    @Unique private ICraftingInventory molecularmanipulator$nativeMaterial;
    @Unique private IPatternDetails molecularmanipulator$nativePattern;
    @Unique private Object molecularmanipulator$nativeTask;
    @Unique private ICraftingProvider molecularmanipulator$nativeProvider;
    @Unique private int molecularmanipulator$nativeAccepted;
    @Unique private boolean molecularmanipulator$ticking;
    @Unique private boolean molecularmanipulator$executePermit;
    @Unique private boolean molecularmanipulator$executing;
    @Unique private long molecularmanipulator$lastCapacityHistoryTick = Long.MIN_VALUE;

    @WrapMethod(method = "tickCraftingLogic")
    private void molecularmanipulator$dispatchTick(IEnergyService energy, CraftingService crafting,
            Operation<Void> original) {
        if (molecularmanipulator$ticking) return;
        molecularmanipulator$ticking = true;
        try {
            original.call(energy, crafting);
        } finally {
            try {
                if (job != molecularmanipulator$deferredCancel) molecularmanipulator$flushFinalStock();
                if (job != null && job == molecularmanipulator$deferredCancel) {
                    ((CraftingCpuLogic) (Object) this).cancel();
                }
            } finally {
                var allowance = molecularmanipulator$allowance;
                molecularmanipulator$allowance = null;
                molecularmanipulator$dispatchJob = null;
                molecularmanipulator$deferredCancel = null;
                molecularmanipulator$ticking = false;
                if (allowance != null) allowance.close();
            }
        }
    }

    // This point is reached only after AE2's active, live job, cancellation and suspension checks.
    @Inject(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"), cancellable = true)
    private void molecularmanipulator$routeOwnedCpu(IEnergyService energy, CraftingService crafting,
            CallbackInfo callback) {
        var owner = OmniComputationCoreBlockEntity.ownerOf(cluster);
        if (owner == null) return;
        if (molecularmanipulator$dispatchLedger.paused() || ecoOmni$paused()
                || !molecularmanipulator$migrateLoadedTasks()) {
            callback.cancel();
            return;
        }

        long tick = TickHandler.instance().getCurrentTick();
        var allowance = owner.claimDispatchAllowance(cluster, tick);
        molecularmanipulator$allowance = allowance;
        molecularmanipulator$dispatchJob = job;
        if (allowance.capacityMode()) {
            callback.cancel();
            if (molecularmanipulator$lastCapacityHistoryTick != tick) {
                molecularmanipulator$lastCapacityHistoryTick = tick;
                // Capacity dispatch does not spend the native AE2 operation history.
                usedOps[2] = usedOps[1];
                usedOps[1] = usedOps[0];
                usedOps[0] = 0;
            }
            if (allowance.remainingCopies() > 0) {
                if (molecularmanipulator$capacityDispatcher == null) {
                    molecularmanipulator$capacityDispatcher = new OmniCapacityDispatcher();
                }
                molecularmanipulator$executing = true;
                try (var context = MatterCraftingJobContext.open(this::getLastLink);
                        var cycle = AelisCycleDispatchScope.open(this::molecularmanipulator$aelisRuntime)) {
                    molecularmanipulator$capacityDispatcher.tick(this, allowance, crafting, energy, cluster.getLevel());
                } finally {
                    molecularmanipulator$executing = false;
                }
            }
        }
    }

    @WrapOperation(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"))
    private int molecularmanipulator$physicalDispatchBoundary(CraftingCpuLogic self, int maximum,
            CraftingService crafting, IEnergyService energy, Level level, Operation<Integer> original) {
        var allowance = molecularmanipulator$allowance;
        if (allowance == null) {
            return isOmniMaterialAllocator() ? 0 : original.call(self, maximum, crafting, energy, level);
        }
        if (allowance.capacityMode() || molecularmanipulator$executing || omniDispatch$stopped()) return 0;
        int limit = (int) Math.min(maximum, allowance.remainingCopies());
        // Skip foreign tick call-site wrappers. AE2 retains the real return value and usedOps history.
        if (limit <= 0) return 0;
        molecularmanipulator$executing = true;
        try (var context = MatterCraftingJobContext.open(this::getLastLink)) {
            return molecularmanipulator$executeNative(limit, crafting, energy, level);
        } finally {
            molecularmanipulator$executing = false;
        }
    }

    @Unique
    private int molecularmanipulator$executeNative(int maximum, CraftingService crafting,
            IEnergyService energy, Level level) {
        molecularmanipulator$nativeAccepted = 0;
        molecularmanipulator$executePermit = true;
        try {
            return executeCrafting(maximum, crafting, energy, level);
        } catch (RuntimeException | Error failure) {
            // A foreign extraction, provider or AE2 accounting hook may have taken ownership.
            omniDispatch$pause(null, "Uncertain native crafting dispatch: " + failure);
            RateLimitedLog.error("Native dispatch suspended for Omni job {}", omniDispatch$jobId(), failure);
            if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
            return molecularmanipulator$nativeAccepted;
        } finally {
            molecularmanipulator$executePermit = false;
            molecularmanipulator$nativeAccepted = 0;
            molecularmanipulator$nativeMaterial = null;
        }
    }
    @Override public int omniDispatch$executeNativeSingle(IPatternDetails pattern, ICraftingProvider provider,
            CraftingService crafting, IEnergyService energy, Level level) {
        var allowance = molecularmanipulator$allowance;
        if (allowance == null || !allowance.capacityMode() || !molecularmanipulator$executing
                || molecularmanipulator$nativePattern != null || allowance.remainingCopies() <= 0
                || omniDispatch$stopped()) return 0;
        var task = molecularmanipulator$getTasks(job).get(pattern);
        if (task == null) return 0;
        molecularmanipulator$nativePattern = pattern;
        molecularmanipulator$nativeTask = task;
        molecularmanipulator$nativeProvider = provider;
        try {
            return molecularmanipulator$executeNative(1, crafting, energy, level);
        } finally {
            molecularmanipulator$nativePattern = null;
            molecularmanipulator$nativeTask = null;
            molecularmanipulator$nativeProvider = null;
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$requireOwnedDispatch(int maximum, CraftingService crafting,
            IEnergyService energy, Level level, CallbackInfoReturnable<Integer> callback) {
        if (molecularmanipulator$allowance == null && !isOmniMaterialAllocator()) return;
        if (!molecularmanipulator$executePermit || omniDispatch$stopped()
                || molecularmanipulator$allowance.capacityMode() && molecularmanipulator$nativePattern == null) {
            callback.setReturnValue(0);
            return;
        }
        molecularmanipulator$executePermit = false;
    }

    @Inject(method = "cancel", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$recordReusableBatchCancellation(
            CallbackInfo callback) {
        if (molecularmanipulator$deliveringFinal || (molecularmanipulator$executing && job == molecularmanipulator$dispatchJob)) {
            molecularmanipulator$deferredCancel = job;
            callback.cancel();
            return;
        }
        var link = getLastLink();
        if (link != null) {
            MolecularBatchCancellationData.markCanceled(
                    cluster.getLevel(), link.getCraftingID());
        }
    }

    @Unique
    private boolean molecularmanipulator$nativeDispatch() {
        return molecularmanipulator$executing && molecularmanipulator$allowance != null
                && (!molecularmanipulator$allowance.capacityMode() || molecularmanipulator$nativePattern != null);
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    private Iterator<Map.Entry<IPatternDetails, Object>> molecularmanipulator$nativeCandidates(
            Set<Map.Entry<IPatternDetails, Object>> tasks,
            Operation<Iterator<Map.Entry<IPatternDetails, Object>>> original) {
        var pattern = molecularmanipulator$nativePattern;
        if (pattern != null) {
            return Collections.singleton(Map.entry(pattern, molecularmanipulator$nativeTask)).iterator();
        }
        return molecularmanipulator$nativeDispatch() && ecoOmni$managed()
                ? ecoOmni$candidates(molecularmanipulator$getTasks(job)).iterator() : original.call(tasks);
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V"))
    private void molecularmanipulator$removeNativeTask(Iterator<?> tasks, Operation<Void> original) {
        var pattern = molecularmanipulator$nativePattern;
        if (pattern == null) {
            original.call(tasks);
        } else {
            molecularmanipulator$getTasks(molecularmanipulator$dispatchJob)
                    .remove(pattern, molecularmanipulator$nativeTask);
        }
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> molecularmanipulator$nativeProviders(CraftingService crafting,
            IPatternDetails pattern, Operation<Iterable<ICraftingProvider>> original) {
        var provider = molecularmanipulator$nativeProvider;
        return provider == null ? original.call(crafting, pattern) : List.of(provider);
    }

    @ModifyExpressionValue(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Ljava/util/Iterator;hasNext()Z", ordinal = 0))
    private boolean molecularmanipulator$stopNativeTraversal(boolean hasNext,
            @Local(argsOnly = true) int maximum, @Local(ordinal = 1) int pushed) {
        // AE2 can continue taskLoop after exhausting a task, before comparing pushed with maximum.
        return hasNext && (!molecularmanipulator$nativeDispatch()
                || molecularmanipulator$canPushNative(maximum, pushed));
    }

    @Unique
    private boolean molecularmanipulator$canPushNative(int maximum, int pushed) {
        return pushed < maximum && molecularmanipulator$allowance.remainingCopies() > 0 && !omniDispatch$stopped();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] molecularmanipulator$extractNativeInputs(IPatternDetails pattern, ICraftingInventory inventory,
            Level level, KeyCounter outputs, KeyCounter containers, Operation<KeyCounter[]> original) {
        if (!molecularmanipulator$nativeDispatch()) return original.call(pattern, inventory, level, outputs, containers);
        if (omniDispatch$stopped() || molecularmanipulator$allowance.remainingCopies() <= 0) return null;
        var material = molecularmanipulator$nativeMaterial(inventory);
        var guarded = ecoOmni$guard(pattern, material);
        if (guarded == null) return null;
        var inputs = ecoOmni$extract(pattern, guarded, level, outputs, containers,
                () -> original.call(pattern, guarded, level, outputs, containers));
        if (inputs != null && !molecularmanipulator$hasWaitingForSingleCraftRoom(outputs, containers)) {
            CraftingCpuHelper.reinjectPatternInputs(material, inputs);
            outputs.reset();
            containers.reset();
            return null;
        }
        return inputs;
    }

    @Unique
    private ICraftingInventory molecularmanipulator$nativeMaterial(ICraftingInventory window) {
        if (molecularmanipulator$nativeMaterial != null) return molecularmanipulator$nativeMaterial;
        var state = molecularmanipulator$exactState;
        if (state == null && molecularmanipulator$exactInventory.isEmpty()) return window;
        var physical = molecularmanipulator$exactInventoryView(window);
        return molecularmanipulator$nativeMaterial = state == null ? physical : state.wrap(physical);
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;reinjectPatternInputs(Lappeng/crafting/inv/ICraftingInventory;[Lappeng/api/stacks/KeyCounter;)V"))
    private void molecularmanipulator$reinjectNativeInputs(ICraftingInventory inventory, KeyCounter[] inputs,
            Operation<Void> original) {
        original.call(molecularmanipulator$nativeDispatch() ? molecularmanipulator$nativeMaterial(inventory) : inventory,
                inputs);
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean molecularmanipulator$pushNativeRecipe(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, Operation<Boolean> original, @Local(argsOnly = true) int maximum,
            @Local(ordinal = 1) int pushed, @Local(ordinal = 0) KeyCounter outputs,
            @Local(ordinal = 1) KeyCounter containers) {
        if (!molecularmanipulator$nativeDispatch()) return original.call(provider, pattern, inputs);
        if (!molecularmanipulator$canPushNative(maximum, pushed)
                || !molecularmanipulator$hasWaitingForSingleCraftRoom(outputs, containers)) return false;

        var state = molecularmanipulator$exactState;
        var task = state == null ? null : molecularmanipulator$getTasks(job).get(pattern);
        if (state != null && (task == null || !molecularmanipulator$reflectionAvailable)) return false;
        var next = state == null ? null : state.nextRemaining(pattern, BigInteger.ONE);
        var spent = state == null ? null : molecularmanipulator$unitInputAmounts(inputs);
        boolean accepted;
        if (molecularmanipulator$nativePattern == null) {
            accepted = ecoOmni$pushProvider(provider, pattern, inputs, () -> original.call(provider, pattern, inputs));
        } else {
            if (molecularmanipulator$nativePattern != pattern || molecularmanipulator$nativeProvider != provider) return false;
            // Keep Lightning and DE's provider and accounting hooks around this one call.
            accepted = ecoOmni$withProviderScope(() -> original.call(provider, pattern, inputs));
        }
        if (!accepted) return false;

        // AE2 accounts power, waiting outputs and one decrement after this returns.
        molecularmanipulator$nativeAccepted++;
        molecularmanipulator$allowance.recordAccepted(BigInteger.ONE);
        try {
            if (state != null) {
                long nextWindow = OmniExactCraftingState.window(next);
                if (!molecularmanipulator$setTaskValue(task, nextWindow + 1)) {
                    throw new IllegalStateException("Could not advance exact crafting task window");
                }
                state.commit(pattern, next);
                state.recordBatch(pattern, BigInteger.ONE, spent);
            }
            ecoOmni$accepted(pattern, BigInteger.ONE);
        } catch (RuntimeException | LinkageError failure) {
            omniDispatch$pause(null, "Native recipe accepted but its exact progress could not be committed");
            RateLimitedLog.error("Native recipe accounting failed for Omni job {}", omniDispatch$jobId(), failure);
        }
        return true;
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/energy/IEnergyService;extractAEPower(DLappeng/api/config/Actionable;Lappeng/api/config/PowerMultiplier;)D"))
    private double molecularmanipulator$useHeldNativePower(IEnergyService energy, double amount, Actionable mode,
            PowerMultiplier multiplier, Operation<Double> original) {
        if (!molecularmanipulator$nativeDispatch() || multiplier != PowerMultiplier.CONFIG) {
            return original.call(energy, amount, mode, multiplier);
        }
        return omniDispatch$dispatchEnergy().extractNative(amount, mode,
                missing -> original.call(energy, missing, mode, multiplier));
    }

    @Unique
    private boolean molecularmanipulator$hasWaitingForSingleCraftRoom(KeyCounter outputs, KeyCounter containers) {
        var waiting = ((ExactExecutingJobAccessor) job).molecularmanipulator$getWaitingFor().list;
        for (var entry : outputs) {
            long output = entry.getLongValue();
            long container = containers.get(entry.getKey());
            long alreadyWaiting = waiting.get(entry.getKey());
            if (output <= 0 || container < 0 || alreadyWaiting < 0
                    || output > Long.MAX_VALUE - alreadyWaiting
                    || container > Long.MAX_VALUE - alreadyWaiting - output) return false;
        }
        for (var entry : containers) {
            if (outputs.get(entry.getKey()) > 0) continue;
            long alreadyWaiting = waiting.get(entry.getKey());
            if (entry.getLongValue() <= 0 || alreadyWaiting < 0
                    || entry.getLongValue() > Long.MAX_VALUE - alreadyWaiting) return false;
        }
        return true;
    }

    @Unique
    private MolecularExternalScaledPattern.Unwrapped molecularmanipulator$unwrapCountedTask(IPatternDetails pattern) {
        var current = pattern;
        long multiplier = 1;
        var visited = new IdentityHashMap<IPatternDetails, Boolean>();
        while (visited.put(current, Boolean.TRUE) == null) {
            IPatternDetails next;
            long scale;
            var nativeScale = OmniNativeSmartDoubling.resolveScale(current);
            if (nativeScale != null) {
                next = nativeScale.original();
                scale = nativeScale.multiplier();
            } else if (current instanceof com.github.appliedenhancements.integration.ae2.AelisScaledPattern scaled) {
                next = scaled.appliedenhancements$originalPattern();
                scale = scaled.appliedenhancements$operationsPerPush();
            } else {
                if (OmniNativeSmartDoubling.isNativeScaledPattern(current)) {
                    throw new IllegalStateException("Could not inspect a saved scaled crafting task");
                }
                return new MolecularExternalScaledPattern.Unwrapped(current, multiplier);
            }
            if (next == null || scale <= 0) throw new IllegalStateException("Invalid counted CPU scaled task");
            multiplier = Math.multiplyExact(multiplier, scale);
            current = next;
        }
        throw new IllegalStateException("Cyclic counted CPU scaled task");
    }

    @Unique
    private boolean molecularmanipulator$migrateLoadedTasks() {
        if (!molecularmanipulator$migrateLoadedTasks) return true;
        molecularmanipulator$migrateLoadedTasks = false;
        try {
            molecularmanipulator$normalizeExternalScaledTasks(job, null, Map.of(), null);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            omniDispatch$pause(null, "Could not migrate saved scaled crafting tasks");
            RateLimitedLog.error("Saved Omni task migration failed for job {}", omniDispatch$jobId(), failure);
            return false;
        }
    }

    /** Migration only: convert already-scaled rows before the CPU can dispatch any recipe. */
    @Unique
    private boolean molecularmanipulator$normalizeExternalScaledTasks(ExecutingCraftingJob currentJob,
            Map<IPatternDetails, BigInteger> submittedCounts, Map<AEKey, BigInteger> infiniteInputs,
            BigInteger submittedGoal) {
        if (ecoOmni$managed()) return false;
        var tasks = molecularmanipulator$getTasks(currentJob);
        if (!molecularmanipulator$reflectionAvailable) throw new IllegalStateException("AE2 tasks are inaccessible");

        var bindings = new IdentityHashMap<IPatternDetails, MolecularExternalScaledPattern.Unwrapped>();
        boolean scaled = false;
        for (var pattern : tasks.keySet()) {
            var binding = molecularmanipulator$unwrapCountedTask(pattern);
            bindings.put(pattern, binding);
            scaled |= binding.patternDetails() != pattern;
        }
        if (!scaled && submittedCounts == null) return false;
        if (submittedCounts != null && !tasks.keySet().containsAll(submittedCounts.keySet())) {
            throw new IllegalStateException("Submitted exact recipes do not match the CPU task identities");
        }

        var previous = molecularmanipulator$exactState;
        var replacement = new LinkedHashMap<IPatternDetails, Object>();
        var counts = new LinkedHashMap<IPatternDetails, BigInteger>();
        for (var entry : tasks.entrySet()) {
            var pattern = entry.getKey();
            var binding = bindings.get(pattern);
            var task = entry.getValue();
            long window = molecularmanipulator$getTaskValue(task);
            if (!molecularmanipulator$reflectionAvailable || window < 0) {
                throw new IllegalStateException("Invalid AE2 task progress");
            }
            var count = submittedCounts != null ? submittedCounts.getOrDefault(pattern, BigInteger.ZERO)
                    : previous != null ? previous.remaining(pattern) : BigInteger.valueOf(window);
            if (count.signum() < 0 || (count.signum() == 0 && window != 0)) {
                throw new IllegalStateException("Invalid exact count for projected CPU task");
            }
            if (count.signum() == 0) continue;
            replacement.putIfAbsent(binding.patternDetails(), task);
            if (previous == null) {
                counts.merge(binding.patternDetails(), count.multiply(BigInteger.valueOf(binding.multiplier())),
                        BigInteger::add);
            }
        }

        var access = (ExactExecutingJobAccessor) currentJob;
        if (previous != null) {
            var prepared = previous.normalizeTasks(bindings);
            molecularmanipulator$installExactWindows(replacement, prepared);
            previous.applyNormalizedTasks(prepared); // Native callback tokens retain the ledger identity.
        } else if (!counts.isEmpty()) {
            var prepared = OmniExactCraftingState.create(counts, infiniteInputs);
            prepared.setOutputRemaining(submittedGoal != null ? submittedGoal
                    : BigInteger.valueOf(access.molecularmanipulator$getRemaining()));
            molecularmanipulator$installExactWindows(replacement, prepared);
            molecularmanipulator$exactState = prepared;
            access.molecularmanipulator$setRemaining(prepared.outputProgress().window());
        } else if (submittedCounts != null) {
            throw new IllegalStateException("Exact plan has no positive recipe counts");
        }
        tasks.clear();
        tasks.putAll(replacement);
        cluster.markDirty();
        return true;
    }


    @Override public boolean omniDispatch$enabled() {
        return isOmniMaterialAllocator();
    }
    @Override public UUID omniDispatch$jobId() {
        var link = job == null ? null : getLastLink();
        return link == null ? null : link.getCraftingID();
    }
    @Override public boolean omniDispatch$exactOrder() { return molecularmanipulator$dispatchLedger.exactOrder(); }
    @Override public boolean omniDispatch$stopped() {
        var link = getLastLink();
        return job == null || job != molecularmanipulator$dispatchJob || job == molecularmanipulator$deferredCancel
                || !omniDispatch$enabled() || !cluster.isActive() || link == null || link.isCanceled()
                || molecularmanipulator$dispatchLedger.paused() || isJobSuspended() || ecoOmni$paused();
    }
    @Override public ListCraftingInventory omniDispatch$inventory() {
        var inventory = getInventory();
        if (OmniCpuDispatchLedger.refillPhysical(molecularmanipulator$exactInventory, inventory)) cluster.markDirty();
        return inventory;
    }
    @Override public ICraftingInventory omniDispatch$physicalView() {
        return molecularmanipulator$exactInventory.wrap(getInventory());
    }
    @Override public BigInteger omniDispatch$remaining(IPatternDetails pattern) {
        if (job == null) return BigInteger.ZERO;
        if (molecularmanipulator$exactState != null) return molecularmanipulator$exactState.remaining(pattern);
        var task = molecularmanipulator$getTasks(job).get(pattern);
        return task == null ? BigInteger.ZERO : BigInteger.valueOf(Math.max(0, molecularmanipulator$getTaskValue(task)));
    }
    @Override public OmniCpuDispatchHost.Lease omniDispatch$reserveExact(Map<AEKey, BigInteger> inputs) {
        if (omniDispatch$stopped()) return null;
        return OmniCpuDispatchLedger.reserve(molecularmanipulator$exactInventory, getInventory(), inputs);
    }
    @Override public OmniCpuDispatchHost.Accounting omniDispatch$stage(OmniCpuDispatchReceipt receipt) {
        if (receipt == null || !receipt.jobId().equals(omniDispatch$jobId()) || omniDispatch$stopped()) return null;
        var currentJob = job;
        var state = molecularmanipulator$exactState;
        var tasks = molecularmanipulator$getTasks(currentJob);
        var task = tasks.get(receipt.pattern());
        if (task == null || !molecularmanipulator$reflectionAvailable) return null;
        var remaining = omniDispatch$remaining(receipt.pattern());
        if (remaining.compareTo(receipt.crafts()) < 0) return null;
        if (!molecularmanipulator$reflectionAvailable) return null;
        var waiting = ((ExactExecutingJobAccessor) currentJob).molecularmanipulator$getWaitingFor();
        return molecularmanipulator$dispatchLedger.stage(waiting, state, receipt, actual -> {
            if (job != currentJob || state != molecularmanipulator$exactState || tasks.get(actual.pattern()) != task
                    || !omniDispatch$remaining(actual.pattern()).equals(remaining)) {
                throw new IllegalStateException("CPU task changed during submission");
            }
            var next = remaining.subtract(actual.crafts());
            long nextWindow = state == null ? next.longValueExact() : OmniExactCraftingState.window(next);
            if (!molecularmanipulator$setTaskValue(task, nextWindow)) {
                throw new IllegalStateException("Cannot commit CPU task progress");
            }
            if (state != null) {
                state.commit(actual.pattern(), next);
                state.recordBatch(actual.pattern(), actual.crafts(), actual.inputs());
            }
            if (next.signum() == 0) tasks.remove(actual.pattern());
            for (var key : actual.outputs().keySet()) molecularmanipulator$notifyOwnedOutput(key);
            for (var key : actual.remainders().keySet()) {
                if (!actual.outputs().containsKey(key)) molecularmanipulator$notifyOwnedOutput(key);
            }
            cluster.markDirty();
        });
    }
    @Override public void omniDispatch$markDirty() { cluster.markDirty(); }
    @Override public OmniCpuDispatchLedger.Energy omniDispatch$dispatchEnergy() {
        return molecularmanipulator$dispatchLedger.energy();
    }
    @Override public void omniDispatch$pause(OmniCpuDispatchReceipt receipt, String reason) {
        molecularmanipulator$dispatchLedger.pause(reason);
        if (this instanceof OmniCpuDispatchRunner runner) runner.omniDispatch$suspend(reason);
        setJobSuspended(true);
        cluster.markDirty();
    }
    @Override public BigInteger omniDispatch$insertExact(UUID jobId, AEKey key, BigInteger offered, Actionable mode) {
        if (!molecularmanipulator$mayReceive(jobId) || molecularmanipulator$nativeDispatch()
                || key == null || offered == null || offered.signum() <= 0 || mode == null) return BigInteger.ZERO;
        var access = (ExactExecutingJobAccessor) job;
        var state = molecularmanipulator$exactState;
        var waiting = access.molecularmanipulator$getWaitingFor();
        var runtime = mode == Actionable.MODULATE ? molecularmanipulator$aelisRuntime() : null;
        var cycleReturned = runtime == null ? BigInteger.ZERO
                : molecularmanipulator$dispatchLedger.available(waiting, state, key).min(offered);
        var accepted = molecularmanipulator$dispatchLedger.receive(waiting, state,
                molecularmanipulator$exactInventory, getInventory(), key, offered, mode);
        if (mode == Actionable.MODULATE && accepted.signum() > 0) {
            if (runtime != null && cycleReturned.signum() > 0) runtime.recordReturned(key, cycleReturned);
            molecularmanipulator$notifyOwnedOutput(key);
        }
        return accepted;
    }
    @Unique
    private boolean molecularmanipulator$mayReceive(UUID jobId) {
        var link = job == null ? null : getLastLink();
        if (jobId == null || link == null || !jobId.equals(link.getCraftingID()) || link.isCanceled() || link.isDone()
                || molecularmanipulator$deliveringFinal || job == molecularmanipulator$deferredCancel
                || molecularmanipulator$dispatchLedger.paused() || ecoOmni$paused() || isJobSuspended()
                || !omniDispatch$enabled() || !cluster.isActive()) return false;
        var level = cluster.getLevel();
        var server = level == null ? null : level.getServer();
        return server != null && server.isSameThread();
    }

    // AELIS keeps this runtime on the CPU, including after NBT restore. Its scope exposes it only during executeCrafting.
    @Unique private static final Field molecularmanipulator$aelisRuntimeField = molecularmanipulator$findAelisRuntimeField();

    @Unique
    private static Field molecularmanipulator$findAelisRuntimeField() {
        try {
            var field = CraftingCpuLogic.class.getDeclaredField("appliedenhancements$cycleRuntime");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException unavailable) {
            return null;
        }
    }

    @Unique
    private AelisCycleRuntimeController molecularmanipulator$aelisRuntime() {
        try {
            return molecularmanipulator$aelisRuntimeField == null ? null
                    : (AelisCycleRuntimeController) molecularmanipulator$aelisRuntimeField.get(this);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("AELIS cycle runtime is inaccessible", failure);
        }
    }

    @Unique private void molecularmanipulator$notifyOwnedOutput(AEKey key) {
        try { postChange(key); cluster.markDirty(); }
        catch (RuntimeException | Error failure) {
            RateLimitedLog.error("Owned output settled; CPU observer notification failed", failure);
        }
    }
    @Unique private long molecularmanipulator$deliverFinalOutput(AEKey key, long amount, Actionable mode) {
        var link = getLastLink();
        if (job == null || link == null || link.isCanceled()) return 0;
        molecularmanipulator$deliveringFinal = true;
        try {
            if (link.isStandalone()) {
                var grid = cluster.getGrid();
                return grid == null ? 0 : grid.getStorageService().getInventory().insert(key, amount, mode, cluster.getSrc());
            }
            return ((appeng.crafting.CraftingLink) link).insert(key, amount, mode);
        } finally { molecularmanipulator$deliveringFinal = false; }
    }
    @Unique private BigInteger molecularmanipulator$reservedFinalInput(AEKey key) {
        var pendingInputs = BigInteger.ZERO;
        for (var pattern : molecularmanipulator$getTasks(job).keySet()) {
            pendingInputs = pendingInputs.add(OmniCpuDispatchLedger.reservedInput(pattern,
                    omniDispatch$remaining(pattern), key));
        }
        var runtime = molecularmanipulator$aelisRuntime();
        if (runtime != null && runtime.hasActiveSeedProtection()) {
            pendingInputs = pendingInputs.max(BigInteger.valueOf(runtime.requiredRetainedAmount(key)));
        }
        return pendingInputs.max(omniDispatch$reservedInput(key));
    }

    @Unique private void molecularmanipulator$flushFinalStock() {
        if (job == null || job == molecularmanipulator$deferredCancel || !omniDispatch$enabled() || !cluster.isActive()
                || molecularmanipulator$dispatchLedger.paused() || molecularmanipulator$dispatchLedger.staging()
                || ecoOmni$paused() || isJobSuspended() || getLastLink().isCanceled()) return;
        try {
            var current = (ExactExecutingJobAccessor) job;
            var finalKey = current.molecularmanipulator$getFinalOutput().what();
            var exact = molecularmanipulator$exactState;
            var remaining = exact == null || exact.outputProgress() == null
                    ? BigInteger.valueOf(current.molecularmanipulator$getRemaining()) : exact.outputProgress().remaining();
            var owned = molecularmanipulator$dispatchLedger.finalStock().getOrDefault(finalKey, BigInteger.ZERO);
            var deliverable = molecularmanipulator$exactInventory.amount(getInventory(), finalKey)
                    .subtract(molecularmanipulator$reservedFinalInput(finalKey)).max(BigInteger.ZERO)
                    .min(remaining.subtract(owned).max(BigInteger.ZERO)).min(BigInteger.valueOf(Long.MAX_VALUE));
            if (deliverable.signum() > 0) {
                var lease = OmniCpuDispatchLedger.reserve(molecularmanipulator$exactInventory, getInventory(),
                        Map.of(finalKey, deliverable));
                if (lease == null) throw new IllegalStateException("Final physical stock changed during reservation");
                molecularmanipulator$dispatchLedger.ownFinal(finalKey, deliverable);
                lease.commit();
                cluster.markDirty();
            }
            molecularmanipulator$dispatchLedger.flushFinal(this::molecularmanipulator$deliverFinalOutput, (key, amount) -> {
                var access = (ExactExecutingJobAccessor) job;
                var state = molecularmanipulator$exactState;
                long next = state == null || state.outputProgress() == null
                        ? Math.max(0, access.molecularmanipulator$getRemaining() - amount)
                        : state.outputProgress().delivered(amount);
                access.molecularmanipulator$setRemaining(next);
                molecularmanipulator$notifyOwnedOutput(key);
            }, cluster::markDirty);
            if (((ExactExecutingJobAccessor) job).molecularmanipulator$getRemaining() == 0
                    && molecularmanipulator$getTasks(job).isEmpty() && !molecularmanipulator$dispatchLedger.hasFinalStock()) {
                finishJob(true);
                if (job == null) cluster.updateOutput(null);
            } else if (job != null) {
                var access = (ExactExecutingJobAccessor) job;
                cluster.updateOutput(new GenericStack(access.molecularmanipulator$getFinalOutput().what(),
                        access.molecularmanipulator$getRemaining()));
            }
        } catch (RuntimeException | Error failure) {
            omniDispatch$pause(null, "Uncertain final output delivery: " + failure);
            RateLimitedLog.error("CPU final output delivery suspended; ownership intent retained", failure);
            if (failure instanceof Error error && !(error instanceof LinkageError) && !(error instanceof AssertionError))
                throw error;
        }
    }

    @Override public OmniExactCraftingState ecoOmni$exactState() { return molecularmanipulator$exactState; }
    @Override public Map<IPatternDetails, Object> ecoOmni$tasks() {
        return job == null ? Map.of() : molecularmanipulator$getTasks(job);
    }
    @Override public void ecoOmni$installExactState(OmniExactCraftingState state) {
        molecularmanipulator$exactState = state;
        if (state != null) {
            molecularmanipulator$installExactState(state);
            if (state.outputProgress() != null) ((ExactExecutingJobAccessor) job)
                    .molecularmanipulator$setRemaining(state.outputProgress().window());
        }
    }
    @Override public BigInteger ecoOmni$physicalAmount(AEKey key) {
        return molecularmanipulator$exactInventory.amount(getInventory(), key);
    }
    @Override public void ecoOmni$storePhysical(AEKey key, BigInteger amount) {
        molecularmanipulator$exactInventory.insert(getInventory(), key, amount);
        postChange(key);
        cluster.markDirty();
    }
    @Override public void ecoOmni$queueEmitted(AEKey key, BigInteger amount) {
        var state = molecularmanipulator$exactState;
        if (state == null || job == null) throw new IllegalStateException("CPU emitted material has no exact ledger");
        state.queueOutput(key, amount);
        long credit = state.claimOutputWindow(key, getWaitingFor(key));
        if (credit > 0) ((ExactExecutingJobAccessor) job).molecularmanipulator$getWaitingFor()
                .insert(key, credit, Actionable.MODULATE);
        postChange(key);
        cluster.markDirty();
    }

    @Override public void ecoOmni$restoreRefundSnapshot(CompoundTag data, HolderLookup.Provider registries) {
        var restored = new OmniExactInventory();
        restored.read(data.getList(MOLECULARMANIPULATOR_EXACT_INVENTORY_TAG, Tag.TAG_COMPOUND), registries);
        var stock = new LinkedHashMap<AEKey, Long>();
        for (var value : data.getList("inventory", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var key = AEKey.fromTagGeneric(registries, entry);
            long amount = entry.getLong("#");
            if (key == null || amount < 0 || stock.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Cannot safely refund undecodable CPU physical inventory");
            }
        }
        // Validate everything before replacing either physical ledger. The failed decode may have
        // already refilled overflow into the window; resetting both avoids a duplicate refund.
        var window = getInventory();
        window.list.clear();
        stock.forEach(window.list::set);
        molecularmanipulator$exactInventory.read(restored.write(registries), registries);
        molecularmanipulator$exactState = null;
    }

    @Unique
    private void molecularmanipulator$installExactState(OmniExactCraftingState state) {
        if (job == null) throw new IllegalStateException("Exact crafting job is missing");
        molecularmanipulator$installExactWindows(molecularmanipulator$getTasks(job), state);
    }

    @Unique
    private void molecularmanipulator$installExactWindows(Map<IPatternDetails, Object> tasks, OmniExactCraftingState state) {
        if (!molecularmanipulator$reflectionAvailable) {
            throw new IllegalStateException(
                    "AE2 crafting tasks are inaccessible");
        }
        var seenDefinitions = new java.util.HashSet<appeng.api.stacks.AEItemKey>();
        for (var entry : tasks.entrySet()) {
            var definition = entry.getKey().getDefinition();
            var remaining = state.remaining(entry.getKey());
            if (definition == null || remaining.signum() <= 0
                    || !seenDefinitions.add(definition)) {
                throw new IllegalStateException(
                        "Exact crafting tasks do not match the AE2 job");
            }
        }
        if (!seenDefinitions.equals(state.taskDefinitions())) {
            throw new IllegalStateException(
                    "Exact crafting plan contains unmatched tasks");
        }
        for (var entry : tasks.entrySet()) {
            if (!molecularmanipulator$setTaskValue(entry.getValue(), OmniExactCraftingState.window(state.remaining(entry.getKey())))) {
                throw new IllegalStateException("AE2 exact task progress is inaccessible");
            }
        }
    }

    @Unique
    private void molecularmanipulator$discardProjectedInfiniteInputs(Set<AEKey> infiniteKeys) {
        if (infiniteKeys.isEmpty()) return;
        var inventory = getInventory();
        for (var key : infiniteKeys) {
            if (inventory.list.get(key) != 0) {
                inventory.list.remove(key);
            }
        }
        inventory.list.removeZeros();
    }

    @Override public ExactBatchToken omniDispatch$bigIntegerToken(Map<AEKey, BigInteger> outputsPerCraft) {
        return omniDispatch$stopped() ? null : new NativeBigIntegerBatchCallback(outputsPerCraft);
    }

    /**
     * The capacity dispatcher stages declared outputs. This receipt transfers live intermediate stock
     * and corrects the waiting window for additional dynamic outputs from the provider.
     */
    @Unique
    private final class NativeBigIntegerBatchCallback implements ExactBatchToken, OmniBigIntegerOutputReceiver {
        private final OmniExactCraftingState acceptedState = molecularmanipulator$exactState;
        private final UUID jobId = omniDispatch$jobId();
        private final Map<AEKey, BigInteger> outputsPerCraft;
        private BigInteger acceptedCrafts;
        private List<OmniBigIntegerOutput> earlyOutputs;
        private final Set<AEKey> changedKeys = new LinkedHashSet<>();
        private boolean terminal;

        @Override
        public BigInteger transferOutput(IGrid sourceGrid, AEKey key, BigInteger offered,
                java.util.function.Consumer<BigInteger> debitSource) {
            if (sourceGrid == null || cluster.getGrid() != sourceGrid || !live() || key == null
                    || offered == null || offered.signum() <= 0) return BigInteger.ZERO;
            var access = (ExactExecutingJobAccessor) job;
            // Final products must still honor requester/network storage acceptance.
            if (key.matches(access.molecularmanipulator$getFinalOutput())) {
                return BigInteger.ZERO;
            }
            var waiting = access.molecularmanipulator$getWaitingFor();
            var accepted = molecularmanipulator$dispatchLedger.available(waiting, acceptedState, key).min(offered);
            if (accepted.signum() <= 0) return BigInteger.ZERO;
            debitSource.accept(accepted);
            settle(waiting, key, accepted);
            return accepted;
        }

        @Override
        public Map<AEKey, BigInteger> transferOutputs(IGrid sourceGrid,
                Map<AEKey, BigInteger> offered,
                java.util.function.Consumer<Map<AEKey, BigInteger>> debitSource) {
            if (sourceGrid == null || cluster.getGrid() != sourceGrid || !live() || offered == null) return Map.of();
            var access = (ExactExecutingJobAccessor) job;
            var waiting = access.molecularmanipulator$getWaitingFor();
            var accepted = new LinkedHashMap<AEKey, BigInteger>();
            for (var entry : offered.entrySet()) {
                AEKey key = entry.getKey();
                BigInteger amount = entry.getValue();
                if (key == null || amount == null || amount.signum() <= 0
                        || key.matches(access.molecularmanipulator$getFinalOutput())) {
                    continue;
                }
                var take = molecularmanipulator$dispatchLedger.available(waiting, acceptedState, key).min(amount);
                if (take.signum() > 0) accepted.put(key, take);
            }
            if (accepted.isEmpty()) return Map.of();
            Map<AEKey, BigInteger> snapshot = Map.copyOf(accepted);
            if (debitSource != null) debitSource.accept(snapshot);
            accepted.forEach((key, amount) -> settle(waiting, key, amount));
            return snapshot;
        }

        private boolean live() {
            return !terminal && acceptedState == molecularmanipulator$exactState && molecularmanipulator$mayReceive(jobId);
        }

        private void settle(ListCraftingInventory waiting, AEKey key, BigInteger accepted) {
            molecularmanipulator$exactInventory.insertWithoutNotification(getInventory(), key, accepted);
            molecularmanipulator$dispatchLedger.consume(waiting, acceptedState, key, accepted);
            var runtime = molecularmanipulator$aelisRuntime();
            if (runtime != null) runtime.recordReturned(key, accepted);
            changedKeys.add(key);
            cluster.markDirty();
        }

        private void flushChangedKeys() {
            for (AEKey changed : changedKeys) {
                molecularmanipulator$notifyOwnedOutput(changed);
            }
            changedKeys.clear();
        }

        NativeBigIntegerBatchCallback(Map<AEKey, BigInteger> outputsPerCraft) {
            this.outputsPerCraft = outputsPerCraft;
        }

        @Override
        public void confirmAccepted(BigInteger crafts) {
            if (terminal || acceptedCrafts != null) return;
            acceptedCrafts = crafts;
            if (earlyOutputs != null) {
                var outputs = earlyOutputs;
                earlyOutputs = null;
                onOutputs(outputs);
            }
        }

        @Override
        public void onOutputs(List<OmniBigIntegerOutput> outputs) {
            if (terminal || earlyOutputs != null) return;
            if (acceptedCrafts == null) {
                // Providers can finish inside push, before reporting how many copies they actually accepted.
                earlyOutputs = outputs == null ? List.of() : List.copyOf(outputs);
                return;
            }
            boolean credit = live();
            terminal = true;
            if (credit && outputs != null && !outputs.isEmpty()) {
                var produced = new HashMap<AEKey, BigInteger>();
                for (var output : outputs) produced.merge(output.key(), output.amount(), BigInteger::add);
                var waiting = ((ExactExecutingJobAccessor) job).molecularmanipulator$getWaitingFor();
                produced.forEach((key, amount) -> {
                    var staged = outputsPerCraft.getOrDefault(key, BigInteger.ZERO).multiply(acceptedCrafts);
                    var additional = amount.subtract(staged);
                    if (additional.signum() > 0) {
                        molecularmanipulator$dispatchLedger.queueWaiting(waiting, acceptedState, key, additional);
                        changedKeys.add(key);
                    }
                });
            }
            flushChangedKeys();
        }

        @Override
        public void onCancelled(BigInteger plannedAmount) {
            if (!terminal) {
                terminal = true;
                earlyOutputs = null;
                flushChangedKeys();
            }
        }

    }
    @Unique
    private static Map<AEKey, BigInteger> molecularmanipulator$unitInputAmounts(KeyCounter[] inputs) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var slot : inputs) {
            for (var entry : slot) {
                if (entry.getLongValue() > 0) result.merge(entry.getKey(),
                        BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        return result;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static Map<IPatternDetails, Object> molecularmanipulator$getTasks(ExecutingCraftingJob job) {
        if (!molecularmanipulator$reflectionAvailable) {
            return Map.of();
        }
        try {
            var field = molecularmanipulator$tasksField;
            if (field == null) {
                field = job.getClass().getDeclaredField("tasks");
                field.setAccessible(true);
                molecularmanipulator$tasksField = field;
            }
            return (Map<IPatternDetails, Object>) field.get(job);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return Map.of();
        }
    }

    @Unique
    private static long molecularmanipulator$getTaskValue(Object task) {
        try {
            var field = molecularmanipulator$getTaskValueField(task);
            return field.getLong(task);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return 0;
        }
    }

    @Unique
    private static boolean molecularmanipulator$setTaskValue(Object task, long value) {
        try {
            var field = molecularmanipulator$getTaskValueField(task);
            field.setLong(task, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return false;
        }
    }

    @Unique
    private static Field molecularmanipulator$getTaskValueField(Object task) throws NoSuchFieldException {
        var field = molecularmanipulator$taskValueField;
        if (field == null) {
            field = task.getClass().getDeclaredField("value");
            field.setAccessible(true);
            molecularmanipulator$taskValueField = field;
        }
        return field;
    }

    @Unique
    private static void molecularmanipulator$disableReflection(Exception exception) {
        molecularmanipulator$reflectionAvailable = false;
        if (!molecularmanipulator$reflectionFailureLogged) {
            molecularmanipulator$reflectionFailureLogged = true;
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.error(
                    "Could not access AE2 crafting task progress", exception);
        }
    }

}
