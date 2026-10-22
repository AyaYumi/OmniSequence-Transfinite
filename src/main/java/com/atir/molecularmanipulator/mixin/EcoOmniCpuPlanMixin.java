package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanAdmission;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanBridge;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanExecution;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Loaded only with ECO 21.2.9. The shared CPU never links an ECO implementation class. */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class EcoOmniCpuPlanMixin implements EcoOmniPlanCpu, EcoOmniPlanCpuAccess {
    @Shadow @Final private CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Shadow private void finishJob(boolean success) { throw new AssertionError(); }
    @Unique private EcoOmniPlanBridge.Prepared molecularmanipulator$ecoPrepared;
    @Unique private EcoOmniPlanExecution molecularmanipulator$ecoExecution;
    @Unique private EcoOmniPlanExecution.Dispatch molecularmanipulator$ecoDispatch;
    @Unique private KeyCounter[] molecularmanipulator$ecoUnitInputs;
    @Unique private BigInteger molecularmanipulator$ecoLimit = BigInteger.ZERO;
    @Unique private CompoundTag molecularmanipulator$ecoFailedSnapshot;
    @Unique private HolderLookup.Provider molecularmanipulator$ecoFailedRegistries;
    @Unique private boolean molecularmanipulator$ecoRestoring;
    @Unique private boolean molecularmanipulator$ecoSuspended;
    @Unique private long molecularmanipulator$ecoRefillTick = Long.MIN_VALUE;

    @Override public EcoOmniPlanExecution ecoOmni$getExecution() { return molecularmanipulator$ecoExecution; }
    @Override public void ecoOmni$setExecution(EcoOmniPlanExecution execution) {
        molecularmanipulator$ecoExecution = execution;
        molecularmanipulator$ecoDispatch = null;
        molecularmanipulator$ecoUnitInputs = null;
        molecularmanipulator$ecoLimit = BigInteger.ZERO;
    }
    @Override public boolean ecoOmni$managed() {
        return molecularmanipulator$ecoPrepared != null || molecularmanipulator$ecoExecution != null
                || molecularmanipulator$ecoRestoring || molecularmanipulator$ecoFailedSnapshot != null;
    }
    @Override public BigInteger ecoDispatch$reservedInput(AEKey key) {
        return molecularmanipulator$ecoExecution == null ? BigInteger.ZERO
                : molecularmanipulator$ecoExecution.reservedInputAmount(key);
    }

    @Override public boolean ecoOmni$paused() {
        return molecularmanipulator$ecoSuspended || molecularmanipulator$ecoRestoring
                || molecularmanipulator$ecoFailedSnapshot != null;
    }

    @WrapMethod(method = "trySubmitJob")
    private ICraftingSubmitResult molecularmanipulator$submitEcoPlan(IGrid grid, ICraftingPlan plan,
            IActionSource source, ICraftingRequester requester, Operation<ICraftingSubmitResult> original) {
        if (ecoOmni$paused()) return CraftingSubmitResult.CPU_BUSY;
        if (!((IOmniCraftingCpu) (Object) this).isOmniMaterialAllocator()) {
            return original.call(grid, plan, source, requester);
        }
        if (!EcoOmniPlanBridge.owns(plan)) {
            return EcoOmniPlanAdmission.submittingForeign(plan,
                    () -> original.call(grid, plan, source, requester));
        }
        // Validate and construct the scheduler before AE2 can extract even one item.
        final EcoOmniPlanBridge.Prepared prepared;
        final EcoOmniPlanExecution execution;
        try {
            prepared = EcoOmniPlanBridge.prepare(plan);
            execution = prepared.start(plan.patternTimes().keySet());
        } catch (RuntimeException | LinkageError invalid) {
            RateLimitedLog.error("Rejected invalid ECO plan before initial Omni extraction", invalid);
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        }
        molecularmanipulator$ecoPrepared = prepared;
        try {
            var result = EcoOmniPlanAdmission.submitting(prepared, () -> original.call(grid, plan, source, requester));
            if (!result.successful()) return result;
            try {
                ecoOmni$installExactState(prepared.newExactState());
                // Bind to the actual submitted AE2 task identities; never a projected planner copy.
                if (!ecoOmni$tasks().keySet().equals(plan.patternTimes().keySet())) {
                    throw new IllegalStateException("ECO submitted tasks were rewritten by another planner");
                }
                ecoOmni$setExecution(execution);
                molecularmanipulator$ecoRefillTick = Long.MIN_VALUE;
                cluster.markDirty();
                return result;
            } catch (RuntimeException | LinkageError invalid) {
                RateLimitedLog.error("Could not attach validated ECO plan; refunding submitted physical inputs", invalid);
                ((CraftingCpuLogic) (Object) this).cancel();
                return CraftingSubmitResult.INCOMPLETE_PLAN;
            }
        } finally {
            molecularmanipulator$ecoPrepared = null;
        }
    }

    @WrapOperation(method = "trySubmitJob", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingPlan;bytes()J"))
    private long molecularmanipulator$validatedEcoStorage(ICraftingPlan plan, Operation<Long> original) {
        return molecularmanipulator$ecoPrepared != null && molecularmanipulator$ecoPrepared.original() == plan
                ? 0L : original.call(plan);
    }

    @Override public Set<Map.Entry<IPatternDetails, Object>> ecoOmni$candidates(Map<IPatternDetails, Object> tasks) {
        return ecoOmni$paused() || molecularmanipulator$ecoExecution == null ? Set.of()
                : molecularmanipulator$ecoExecution.candidateEntries(tasks);
    }

    @Override public ICraftingInventory ecoOmni$guard(IPatternDetails pattern, ICraftingInventory physical) {
        if (!ecoOmni$managed()) return physical;
        molecularmanipulator$ecoDispatch = null;
        molecularmanipulator$ecoUnitInputs = null;
        molecularmanipulator$ecoLimit = BigInteger.ZERO;
        if (ecoOmni$paused() || molecularmanipulator$ecoExecution == null) return null;
        var dispatch = molecularmanipulator$ecoExecution.begin(pattern);
        if (dispatch == null) return null;
        molecularmanipulator$ecoDispatch = dispatch;
        return dispatch.guard(physical, this::ecoOmni$physicalAmount);
    }

    @Override public KeyCounter[] ecoOmni$extract(IPatternDetails pattern, ICraftingInventory physical, Level level,
            KeyCounter outputs, KeyCounter containers, Supplier<KeyCounter[]> ordinary) {
        if (!ecoOmni$managed()) return ordinary.get();
        var dispatch = molecularmanipulator$ecoDispatch;
        if (dispatch == null || ecoOmni$paused()) return null;
        var inputs = dispatch.extractInputs(physical, level, outputs, containers);
        if (inputs == null) return null;
        var units = new LinkedHashMap<AEKey, BigInteger>();
        molecularmanipulator$ecoUnitInputs = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            var copy = molecularmanipulator$ecoUnitInputs[slot] = new KeyCounter();
            for (var entry : inputs[slot]) {
                copy.add(entry.getKey(), entry.getLongValue());
                units.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        // Reconstruct physical stock before the one prototype was reserved. Cache before expansion.
        molecularmanipulator$ecoLimit = dispatch.limit(molecularmanipulator$ecoUnitInputs,
                key -> ecoOmni$physicalAmount(key).add(units.getOrDefault(key, BigInteger.ZERO)), dispatch.maximum());
        if (molecularmanipulator$ecoLimit.signum() > 0) return inputs;
        CraftingCpuHelper.reinjectPatternInputs(physical, inputs);
        outputs.reset();
        containers.reset();
        return null;
    }

    @Override public BigInteger ecoOmni$limit(IPatternDetails pattern, BigInteger requested) {
        if (!ecoOmni$managed()) return requested;
        return ecoOmni$paused() || molecularmanipulator$ecoDispatch == null ? BigInteger.ZERO
                : requested.min(molecularmanipulator$ecoLimit);
    }

    @Override public boolean ecoOmni$pushProvider(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, Supplier<Boolean> ordinary) {
        if (!ecoOmni$managed()) return ordinary.get();
        if (ecoOmni$paused() || molecularmanipulator$ecoDispatch == null) return false;
        try (var scope = molecularmanipulator$ecoDispatch.openProviderScope()) {
            return ordinary.get();
        }
    }

    @Override public void ecoOmni$accepted(IPatternDetails pattern, BigInteger count) {
        if (!ecoOmni$managed()) return;
        if (molecularmanipulator$ecoDispatch == null || molecularmanipulator$ecoUnitInputs == null
                || count.compareTo(molecularmanipulator$ecoLimit) > 0) {
            throw new IllegalStateException("ECO provider acceptance has no validated input reservation");
        }
        molecularmanipulator$ecoDispatch.accepted(count, molecularmanipulator$ecoUnitInputs);
        molecularmanipulator$ecoDispatch = null;
        molecularmanipulator$ecoUnitInputs = null;
        // The shared exact commit marks the CPU dirty after committing both ledgers.
    }

    @WrapMethod(method = "tickCraftingLogic")
    private void molecularmanipulator$tickEcoPlan(IEnergyService energy, CraftingService crafting, Operation<Void> original) {
        if (ecoOmni$paused()) return;
        var execution = molecularmanipulator$ecoExecution;
        if (execution != null && job != null && cluster.isActive()) {
            // Core ownership is registered after NBT restoration. Wait for verification before extracting.
            if (!((IOmniCraftingCpu) (Object) this).isOmniMaterialAllocator()) return;
            long tick = TickHandler.instance().getCurrentTick();
            var grid = cluster.getGrid();
            if (grid != null && tick != molecularmanipulator$ecoRefillTick) {
                molecularmanipulator$ecoRefillTick = tick;
                IActionSource materialSource = cluster.getSrc();
                var playerId = ((EcoOmniPlanJobAccessor) job).ecoOmni$playerId();
                if (playerId != null) {
                    var level = cluster.getLevel();
                    var player = level == null || level.getServer() == null ? null
                            : appeng.api.features.IPlayerRegistry.getConnected(level.getServer(), playerId);
                    if (player == null) { original.call(energy, crafting); return; }
                    materialSource = IActionSource.ofPlayer(player, materialSource.machine().orElse(null));
                }
                final var refillSource = materialSource;
                if (execution.refillDeferred((key, requested) -> grid.getStorageService().getInventory()
                                .extract(key, requested, Actionable.MODULATE, refillSource),
                        this::ecoOmni$storePhysical, this::ecoOmni$queueEmitted)) cluster.markDirty();
            }
        }
        original.call(energy, crafting);
        if (molecularmanipulator$ecoExecution != null && job != null && molecularmanipulator$ecoExecution.isComplete()
                && ecoOmni$exactState() != null && ecoOmni$exactState().outputProgress() != null
                && ecoOmni$exactState().outputProgress().remaining().signum() == 0) finishJob(true);
    }

    @WrapMethod(method = "finishJob")
    private void molecularmanipulator$finishEcoJob(boolean success, Operation<Void> original) {
        if (success && molecularmanipulator$ecoExecution != null && !molecularmanipulator$ecoExecution.isComplete()) return;
        original.call(success);
        // An inner ownership/waiting guard may defer completion. Preserve its live runtime.
        if (job != null || molecularmanipulator$ecoRestoring) return;
        ecoOmni$setExecution(null);
        molecularmanipulator$ecoFailedSnapshot = null;
        molecularmanipulator$ecoFailedRegistries = null;
        molecularmanipulator$ecoSuspended = false;
        molecularmanipulator$ecoRefillTick = Long.MIN_VALUE;
    }

    @WrapMethod(method = "cancel")
    private void molecularmanipulator$cancelEcoJob(Operation<Void> original) {
        boolean held = molecularmanipulator$ecoFailedSnapshot != null;
        if (held) {
            try {
                ecoOmni$restoreRefundSnapshot(molecularmanipulator$ecoFailedSnapshot, molecularmanipulator$ecoFailedRegistries);
            } catch (RuntimeException | LinkageError failure) {
                RateLimitedLog.error("ECO physical refund could not be decoded; retaining original NBT and suspended job", failure);
                return;
            }
            molecularmanipulator$ecoFailedSnapshot = null;
            molecularmanipulator$ecoFailedRegistries = null;
            molecularmanipulator$ecoSuspended = false;
        }
        original.call();
        // A failed base decode may leave no job for AE2.cancel() to finish.
        if (held && job == null) {
            molecularmanipulator$ecoFailedSnapshot = null;
            molecularmanipulator$ecoSuspended = false;
            ecoOmni$setExecution(null);
            ecoOmni$installExactState(null);
            ((CraftingCpuLogic) (Object) this).storeItems();
            cluster.markDirty();
        }
    }

    @WrapMethod(method = "readFromNBT")
    private void molecularmanipulator$restoreEcoJob(CompoundTag data, HolderLookup.Provider registries, Operation<Void> original) {
        ecoOmni$setExecution(null);
        molecularmanipulator$ecoFailedSnapshot = null;
        molecularmanipulator$ecoFailedRegistries = null;
        molecularmanipulator$ecoSuspended = false;
        molecularmanipulator$ecoRefillTick = Long.MIN_VALUE;
        if (!data.contains(EcoOmniPlanExecution.NBT_KEY, Tag.TAG_COMPOUND)) {
            original.call(data, registries);
            return;
        }
        // Keep an immutable recovery copy until *both* exact and ECO restoration succeed.
        molecularmanipulator$ecoFailedSnapshot = data.copy();
        molecularmanipulator$ecoFailedRegistries = registries;
        molecularmanipulator$ecoRestoring = true;
        try {
            original.call(data, registries);
            if (job == null || ecoOmni$exactState() == null) throw new IllegalStateException("ECO saved job has no exact ledger");
            var exactRemaining = new LinkedHashMap<IPatternDetails, BigInteger>();
            ecoOmni$tasks().keySet().forEach(pattern -> exactRemaining.put(pattern, ecoOmni$exactState().remaining(pattern)));
            ecoOmni$setExecution(EcoOmniPlanExecution.read(data.getCompound(EcoOmniPlanExecution.NBT_KEY), registries,
                    cluster.getLevel(), ecoOmni$tasks().keySet(), exactRemaining));
            molecularmanipulator$ecoFailedSnapshot = null;
            molecularmanipulator$ecoFailedRegistries = null;
        } catch (RuntimeException | LinkageError failure) {
            molecularmanipulator$ecoSuspended = true;
            RateLimitedLog.error("Suspended ECO Omni job after restore failure; original NBT retained for recovery", failure);
        } finally {
            molecularmanipulator$ecoRestoring = false;
        }
    }

    @WrapOperation(method = "readFromNBT", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;finishJob(Z)V"))
    private void molecularmanipulator$preserveInvalidEcoJob(CraftingCpuLogic cpu, boolean success, Operation<Void> original) {
        if (molecularmanipulator$ecoRestoring) throw new IllegalStateException("Saved ECO output could not be decoded");
        original.call(cpu, success);
    }

    @WrapMethod(method = "writeToNBT")
    private void molecularmanipulator$saveEcoJob(CompoundTag data, HolderLookup.Provider registries, Operation<Void> original) {
        if (molecularmanipulator$ecoFailedSnapshot != null) {
            data.merge(molecularmanipulator$ecoFailedSnapshot.copy());
            return;
        }
        original.call(data, registries);
        if (job != null && molecularmanipulator$ecoExecution != null) {
            data.put(EcoOmniPlanExecution.NBT_KEY, molecularmanipulator$ecoExecution.write(registries));
        } else data.remove(EcoOmniPlanExecution.NBT_KEY);
    }

    @Inject(method = "storeItems", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$holdFailedEcoStock(CallbackInfo callback) {
        if (molecularmanipulator$ecoFailedSnapshot != null) callback.cancel();
    }
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$holdFailedEcoOutputs(AEKey key, long amount, Actionable mode,
            CallbackInfoReturnable<Long> callback) {
        if (molecularmanipulator$ecoFailedSnapshot != null) callback.setReturnValue(0L);
    }
}
