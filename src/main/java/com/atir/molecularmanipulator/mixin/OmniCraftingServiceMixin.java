package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.OmniCraftingSnapshotContext;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingServiceBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Mixin(value = CraftingService.class, remap = false)
public abstract class OmniCraftingServiceMixin implements OmniCraftingServiceBridge {
    @Unique
    private static final int MOLECULARMANIPULATOR_PLAN_CACHE_SIZE = 64;
    @Unique
    private static final long MOLECULARMANIPULATOR_PLAN_CACHE_TICKS = 100;

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private NetworkCraftingProviders craftingProviders;

    @Unique
    private final Map<OmniCalculationKey, Future<ICraftingPlan>>
            molecularmanipulator$runningCalculations = new HashMap<>();
    @Unique
    private final LinkedHashMap<OmniCalculationKey, CachedOmniPlan>
            molecularmanipulator$completedPlans = new LinkedHashMap<>(16, 0.75F, true);

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Inject(method = "updateCPUClusters", at = @At("RETURN"))
    private void molecularmanipulator$appendOmniCpus(CallbackInfo callback) {
        molecularmanipulator$refreshOmniCpus();
    }

    @WrapMethod(method = "beginCraftingCalculation")
    private Future<ICraftingPlan> molecularmanipulator$calculateWithOmniCore(
            Level level, ICraftingSimulationRequester requester, AEKey what, long amount,
            CalculationStrategy strategy, Operation<Future<ICraftingPlan>> original) {
        var controller = molecularmanipulator$findMaterialCalculationController();
        if (controller == null) {
            return original.call(level, requester, what, amount, strategy);
        }

        var storageSnapshot = molecularmanipulator$captureStorageSnapshot(requester);
        var key = molecularmanipulator$calculationKey(
                requester, what, amount, strategy, storageSnapshot);
        long gameTime = level.getGameTime();
        synchronized (molecularmanipulator$runningCalculations) {
            molecularmanipulator$collectFinishedCalculations(gameTime);

            var cached = molecularmanipulator$completedPlans.get(key);
            if (cached != null && gameTime - cached.completedAtTick()
                    <= MOLECULARMANIPULATOR_PLAN_CACHE_TICKS) {
                controller.recordMaterialCalculationCacheHit();
                return java.util.concurrent.CompletableFuture.completedFuture(cached.plan());
            }

            var running = molecularmanipulator$runningCalculations.get(key);
            if (running != null) {
                controller.recordMaterialCalculationCacheHit();
                return new NonCancellingFuture<>(running);
            }

            var source = requester.getActionSource();
            boolean playerRequest = source != null && source.player().isPresent();
            Future<ICraftingPlan> created;
            if (playerRequest) {
                OmniCraftingSnapshotContext.set(storageSnapshot);
            }
            try {
                created = original.call(level, requester, what, amount, strategy);
            } finally {
                if (playerRequest) {
                    OmniCraftingSnapshotContext.clear();
                }
            }
            molecularmanipulator$runningCalculations.put(key, created);
            return new NonCancellingFuture<>(created);
        }
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void molecularmanipulator$keepSpareCpu(ICraftingPlan job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource source,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        molecularmanipulator$refreshOmniCpus();
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

    @Unique
    private OmniComputationCoreBlockEntity molecularmanipulator$findMaterialCalculationController() {
        for (var controller : grid.getMachines(OmniComputationCoreBlockEntity.class)) {
            if (controller.isMaterialCalculationEnabled()) {
                return controller;
            }
        }
        return null;
    }

    @Unique
    private OmniCalculationKey molecularmanipulator$calculationKey(
            ICraftingSimulationRequester requester, AEKey what, long amount,
            CalculationStrategy strategy, KeyCounter storageSnapshot) {
        long storageSum = 0x6A09E667F3BCC909L;
        long storageXor = 0xBB67AE8584CAA73BL;
        int storedTypes = 0;
        for (var entry : storageSnapshot) {
            long value = ((long) entry.getKey().hashCode() << 32) ^ entry.getLongValue();
            long mixed = molecularmanipulator$mix64(value);
            storageSum += mixed;
            storageXor ^= Long.rotateLeft(mixed, entry.getKey().hashCode() & 63);
            storedTypes++;
        }

        int requesterIdentity = requester.getGridNode() == null
                ? System.identityHashCode(requester)
                : System.identityHashCode(requester.getGridNode());
        var source = requester.getActionSource();
        if (source != null) {
            if (source.player().isPresent()) {
                requesterIdentity = 31 * requesterIdentity
                        + source.player().get().getUUID().hashCode();
            } else if (source.machine().isPresent()) {
                requesterIdentity = 31 * requesterIdentity
                        + System.identityHashCode(source.machine().get());
            }
        }

        return new OmniCalculationKey(what, amount, strategy, requesterIdentity,
                storageSum, storageXor, storedTypes,
                craftingProviders.getLastModifiedOnTick(),
                craftingProviders.getCraftableKeys().size(),
                craftingProviders.getEmittableKeys().size());
    }

    @Unique
    private KeyCounter molecularmanipulator$captureStorageSnapshot(
            ICraftingSimulationRequester requester) {
        var storage = grid.getStorageService();
        var source = requester.getActionSource();
        if (source != null && source.player().isPresent()) {
            return storage.getInventory().getAvailableStacks();
        }

        var snapshot = new KeyCounter();
        for (var entry : storage.getCachedInventory()) {
            if (entry.getLongValue() > 0) {
                snapshot.add(entry.getKey(), entry.getLongValue());
            }
        }
        return snapshot;
    }

    @Unique
    private void molecularmanipulator$collectFinishedCalculations(long gameTime) {
        Iterator<Map.Entry<OmniCalculationKey, Future<ICraftingPlan>>> iterator =
                molecularmanipulator$runningCalculations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var future = entry.getValue();
            if (!future.isDone()) {
                continue;
            }
            iterator.remove();
            if (future.isCancelled()) {
                continue;
            }
            try {
                var plan = future.get();
                molecularmanipulator$completedPlans.put(
                        entry.getKey(), new CachedOmniPlan(plan, gameTime));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | CancellationException ignored) {
            }
        }

        molecularmanipulator$completedPlans.entrySet().removeIf(entry ->
                gameTime - entry.getValue().completedAtTick()
                        > MOLECULARMANIPULATOR_PLAN_CACHE_TICKS);
        while (molecularmanipulator$completedPlans.size()
                > MOLECULARMANIPULATOR_PLAN_CACHE_SIZE) {
            var oldest = molecularmanipulator$completedPlans.entrySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    @Unique
    private static long molecularmanipulator$mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record OmniCalculationKey(AEKey what, long amount, CalculationStrategy strategy,
            int requesterIdentity, long storageSum, long storageXor, int storedTypes,
            long patternRevision, int craftableTypes, int emittableTypes) {
    }

    private record CachedOmniPlan(ICraftingPlan plan, long completedAtTick) {
    }

    private static final class NonCancellingFuture<T> implements Future<T> {
        private final Future<T> delegate;
        private volatile boolean cancelled;

        private NonCancellingFuture(Future<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || delegate.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            if (cancelled) {
                throw new CancellationException();
            }
            return delegate.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            if (cancelled) {
                throw new CancellationException();
            }
            return delegate.get(timeout, unit);
        }
    }
}
