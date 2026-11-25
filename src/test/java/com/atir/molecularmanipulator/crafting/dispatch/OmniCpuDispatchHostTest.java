package com.atir.molecularmanipulator.crafting.dispatch;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.EcoCpuDispatchJob;
import appeng.me.service.CraftingService;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern;
import com.atir.molecularmanipulator.mixin.CraftingCpuLogicMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercises the actual shared CPU host methods; only game infrastructure and mixin accessors are fixtures. */
class OmniCpuDispatchHostTest {
    static {
        EcoMolecularTestPattern.bootstrapKeyCodec();
    }
    private static final BigInteger HUGE = BigInteger.ONE.shiftLeft(100);
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private Cpu cpu;
    private EcoCpuDispatchJob job;
    private final EcoMolecularTestPattern pattern = new EcoMolecularTestPattern(CONTAINER, key -> RETURNED);
    private Task task;
    private appeng.api.networking.IGrid grid;

    @BeforeEach void setup() throws Exception {
        cpu = new Cpu(); job = allocate(EcoCpuDispatchJob.class);
        job.tasks = new LinkedHashMap<>(); task = new Task(); task.value = 10; job.tasks.put(pattern, task);
        job.waiting = new ListCraftingInventory(key -> {}); job.remaining = 100;
        job.output = new GenericStack(OUTPUT, 100);
        var cluster = allocate(CraftingCPUCluster.class);
        var core = allocate(Core.class);
        core.node = (IGridNode) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isActive" -> true;
                    case "getGrid" -> grid;
                    default -> null;
                });
        var level = (ServerLevel) EcoMolecularTestPattern.level();
        var server = allocate(DedicatedServer.class);
        set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
        set(ServerLevel.class, level, "server", server);
        core.setLevel(level);
        set(CraftingCPUCluster.class, cluster, "machineSrc", new MachineSource(core));
        set(CraftingCPUCluster.class, cluster, "status", new java.util.ArrayList<>());
        set(CraftingCpuLogicMixin.class, cpu, "job", job);
        set(CraftingCpuLogicMixin.class, cpu, "cluster", cluster);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$tasksField", null);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$taskValueField", null);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$reflectionAvailable", true);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchJob", job);
        cpu.link = new Link();
        cpu.waiting = job.waiting.list;
    }

    @Test void nativePushLimitStopsTheNextTaskEvenWhenAe2SkipsItsLoopLimitComparison() throws Exception {
        var allowance = nativeAllowance(2);
        var boundary = CraftingCpuLogicMixin.class.getDeclaredMethod(
                "molecularmanipulator$stopNativeTraversal", boolean.class, int.class, int.class);
        boundary.setAccessible(true);
        assertEquals(true, boundary.invoke(cpu, true, 1, 0));
        assertEquals(false, boundary.invoke(cpu, true, 1, 1));
        assertEquals(2, allowance.remainingCopies(), "AE2's native limit can be lower than the lane allowance");
    }

    @Test void nativeProviderReceivesOneRecipeAndPreservesTheExactRollingWindow() throws Exception {
        var allowance = nativeAllowance(2);
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of());
        cpu.ecoOmni$installExactState(state);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        Operation<Boolean> original = args -> {
            assertSame(pattern, args[1]);
            var holder = (KeyCounter[]) args[2];
            assertEquals(1, holder[0].get(CONTAINER));
            assertEquals(1, holder[1].get(MATERIAL));
            calls.incrementAndGet();
            return true;
        };
        var hook = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$pushNativeRecipe",
                ICraftingProvider.class, IPatternDetails.class, KeyCounter[].class, Operation.class,
                int.class, int.class, KeyCounter.class, KeyCounter.class);
        hook.setAccessible(true);
        var outputs = new KeyCounter(); outputs.add(OUTPUT, 1);
        var containers = new KeyCounter(); containers.add(RETURNED, 1);
        assertEquals(true, hook.invoke(cpu, null, pattern, pattern.first(), original, 1, 0, outputs, containers));
        assertEquals(1, calls.get());
        assertEquals(1, cpu.legacyAccepted);
        assertEquals(1, allowance.remainingCopies());
        assertEquals(HUGE.subtract(BigInteger.ONE), state.remaining(pattern));
        assertEquals(OmniExactCraftingState.MAX_WINDOW, --task.value, "AE2 performs its original decrement once");
        assertEquals(false, hook.invoke(cpu, null, pattern, pattern.first(), original, 1, 1, outputs, containers));
        assertEquals(1, calls.get());
    }

    @Test void capacityCompatibilityRunsOnlyTheSelectedNativeTaskAndProvider() throws Exception {
        var allowance = nativeAllowance(2);
        allowance.capacity = true;
        var other = new EcoMolecularTestPattern(MATERIAL, key -> null);
        job.tasks.put(other, new Task());
        task.value = 1;
        var provider = (ICraftingProvider) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ICraftingProvider.class}, (proxy, method, args) -> {
                    throw new AssertionError("The native push hook supplies the provider operation");
                });
        Operation<Object> unexpected = args -> { throw new AssertionError("Unscoped native traversal"); };
        var candidates = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$nativeCandidates",
                Set.class, Operation.class);
        var providers = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$nativeProviders",
                CraftingService.class, IPatternDetails.class, Operation.class);
        var remove = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$removeNativeTask",
                java.util.Iterator.class, Operation.class);
        var push = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$pushNativeRecipe",
                ICraftingProvider.class, IPatternDetails.class, KeyCounter[].class, Operation.class,
                int.class, int.class, KeyCounter.class, KeyCounter.class);
        candidates.setAccessible(true);
        providers.setAccessible(true);
        remove.setAccessible(true);
        push.setAccessible(true);
        cpu.nativeCall = maximum -> {
            assertEquals(1, maximum);
            try {
                var entries = (java.util.Iterator<?>) candidates.invoke(cpu, job.tasks.entrySet(), unexpected);
                var selected = (Map.Entry<?, ?>) entries.next();
                assertSame(pattern, selected.getKey());
                assertSame(task, selected.getValue());
                assertFalse(entries.hasNext());
                var targets = (Iterable<?>) providers.invoke(cpu, null, pattern, unexpected);
                var it = targets.iterator();
                assertSame(provider, it.next());
                assertFalse(it.hasNext());

                var outputs = counter(OUTPUT, 1);
                var containers = counter(RETURNED, 1);
                Operation<Boolean> actual = args -> args[0] == provider && args[1] == pattern;
                assertEquals(false, push.invoke(cpu, provider, other, pattern.first(), unexpected,
                        maximum, 0, outputs, containers));
                assertEquals(true, push.invoke(cpu, provider, pattern, pattern.first(), actual,
                        maximum, 0, outputs, containers));
                assertEquals(0, --task.value);
                remove.invoke(cpu, entries, unexpected);
                return 1;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        };
        assertEquals(1, cpu.omniDispatch$executeNativeSingle(pattern, provider, null, null, null));
        assertEquals(1, allowance.remainingCopies());
        assertEquals(1, cpu.legacyAccepted);
        assertEquals(Set.of(other), job.tasks.keySet());
        assertNull(get("molecularmanipulator$nativePattern"));
    }

    @Test void sharedHostAndItsDescriptorsLoadWithoutTheOptionalImplementation() throws Exception {
        var urls = java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .filter(path -> !path.contains("neoecoae-"))
                .map(path -> {
                    try { return java.nio.file.Path.of(path).toUri().toURL(); }
                    catch (java.net.MalformedURLException failure) { throw new AssertionError(failure); }
                }).toArray(java.net.URL[]::new);
        try (var isolated = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("cn.dancingsnow.neoecoae.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        }) {
            for (var name : java.util.List.of(CraftingCpuLogicMixin.class.getName(), OmniCpuDispatchHost.class.getName(),
                    OmniCpuDispatchLedger.class.getName(), OmniCpuDispatchReceipt.class.getName(), OmniCpuDispatchRunner.class.getName())) {
                var type = Class.forName(name, false, isolated);
                assertNotNull(type.getDeclaredMethods());
            }
            assertNotNull(Class.forName(OmniCpuDispatchLedger.class.getName(), true, isolated).getConstructor().newInstance());
        }
    }
    @Test void allHostHooksAreConcreteInTheSharedCpu() {
        for (var name : java.util.List.of("omniDispatch$inventory", "omniDispatch$physicalView",
                "omniDispatch$remaining", "omniDispatch$dispatchEnergy", "omniDispatch$bigIntegerToken",
                "omniDispatch$reserveExact", "omniDispatch$stage", "omniDispatch$insertExact")) {
            assertTrue(java.util.Arrays.stream(CraftingCpuLogicMixin.class.getDeclaredMethods())
                    .anyMatch(method -> method.getName().equals(name)), name);
        }
    }

    @Test void physicalExtractionKeepsInputsUntilOneRecipeFitsTheWaitingWindow() throws Exception {
        nativeAllowance(2);
        cpu.inventory.list.add(CONTAINER, 1);
        cpu.inventory.list.add(MATERIAL, 1);
        cpu.waiting.set(OUTPUT, Long.MAX_VALUE - 4);
        var outputs = new appeng.api.stacks.KeyCounter();
        var containers = new appeng.api.stacks.KeyCounter();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<appeng.api.stacks.KeyCounter[]> extract = args -> {
            var inventory = (appeng.crafting.inv.ICraftingInventory) args[1];
            assertEquals(1, inventory.extract(CONTAINER, 1, Actionable.MODULATE));
            assertEquals(1, inventory.extract(MATERIAL, 1, Actionable.MODULATE));
            outputs.add(OUTPUT, 4);
            containers.add(OUTPUT, 1); // A returned container can share the pattern output's key.
            return pattern.first();
        };
        var hook = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$extractNativeInputs",
                IPatternDetails.class, appeng.crafting.inv.ICraftingInventory.class, net.minecraft.world.level.Level.class,
                KeyCounter.class, KeyCounter.class, Operation.class);
        hook.setAccessible(true);
        assertNull(hook.invoke(cpu, pattern, cpu.inventory, EcoMolecularTestPattern.level(), outputs, containers, extract));
        assertEquals(1, cpu.inventory.list.get(CONTAINER));
        assertEquals(1, cpu.inventory.list.get(MATERIAL));
        assertEquals(0, outputs.get(OUTPUT));
        assertEquals(0, containers.get(OUTPUT));
        cpu.waiting.set(OUTPUT, Long.MAX_VALUE - 5);
        assertNotNull(hook.invoke(cpu, pattern, cpu.inventory, EcoMolecularTestPattern.level(), outputs, containers, extract),
                "The exact boundary still admits one physical recipe");
        assertEquals(0, cpu.inventory.list.get(CONTAINER));
        assertEquals(0, cpu.inventory.list.get(MATERIAL));
        assertEquals(4, outputs.get(OUTPUT));
        assertEquals(1, containers.get(OUTPUT));
    }

    @Test void migrationKeepsEcoPhasePatternIdentitiesPinned() throws Exception {
        var scaled = new com.atir.molecularmanipulator.crafting.MolecularScaledPattern(pattern, 4);
        job.tasks.clear();
        job.tasks.put(scaled, task);
        cpu.managed = true;
        assertFalse(normalize());
        assertSame(task, job.tasks.get(scaled), "ECO dispatches its original atomic recipe units under the physical quota");
        assertEquals(10, task.value);
    }

    @Test void scaledOrderMigratesPastTheLongRangeWithoutPausingOrTruncation() throws Exception {
        var scaled = new com.atir.molecularmanipulator.crafting.MolecularScaledPattern(pattern, 4);
        job.tasks.clear();
        job.tasks.put(scaled, task);
        task.value = Long.MAX_VALUE;
        assertTrue(normalize());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(4)),
                cpu.ecoOmni$exactState().remaining(pattern));
        assertEquals(OmniExactCraftingState.MAX_WINDOW, task.value);
        assertEquals(BigInteger.valueOf(100), cpu.ecoOmni$exactState().outputProgress().remaining());
        assertSame(task, job.tasks.get(pattern));
        assertFalse(normalize(), "Base tasks are never scaled twice");
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(4)),
                cpu.ecoOmni$exactState().remaining(pattern));
    }

    @Test void nativeOutputCallbackSurvivesPhysicalTaskNormalization() throws Exception {
        var scaled = new com.atir.molecularmanipulator.crafting.MolecularScaledPattern(pattern, 4);
        job.tasks.clear();
        job.tasks.put(scaled, task);
        var state = OmniExactCraftingState.create(Map.of(scaled, BigInteger.TEN), Map.of());
        state.setOutputRemaining(HUGE);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var callback = nativeCallback();
        assertTrue(normalize());
        assertSame(state, cpu.ecoOmni$exactState(), "In-flight native callbacks must keep their original ledger identity");
        assertEquals(BigInteger.valueOf(40), state.remaining(pattern));
        assertEquals(40, task.value);
        assertEquals(HUGE, state.outputProgress().remaining());
        callback.onOutputs(java.util.List.of(new com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput(
                RETURNED, BigInteger.ONE)));
        assertEquals(1, job.waiting.list.get(RETURNED));
    }

    @Test void ordinaryOrderAcceptsAnExactProvidersAdditionalOutputOnce() throws Exception {
        var callback = nativeCallback();
        var outputs = java.util.List.of(new com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput(RETURNED, HUGE));
        callback.onOutputs(outputs);
        callback.onOutputs(outputs);
        assertEquals(Long.MAX_VALUE, job.waiting.list.get(RETURNED));
        assertEquals(HUGE, ledger().available(job.waiting, null, RETURNED));
        assertEquals(10, task.value);
    }

    @Test void nativeBatchCancellationIsTerminalAndDoesNotCreditLateOutputs() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of());
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var callback = nativeCallback();
        callback.onCancelled(BigInteger.ONE);
        callback.onCancelled(BigInteger.ONE);
        callback.onOutputs(java.util.List.of(new com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput(
                RETURNED, BigInteger.ONE)));
        assertEquals(HUGE, state.remaining(pattern));
        assertEquals(BigInteger.ZERO, state.uncreditedOutput(RETURNED));
        assertTrue(state.completedOutputs().isEmpty());
    }

    @Test void synchronousProductionUsesTheAcceptedPrefixAndOnlyActualByproducts() throws Exception {
        var callback = cpu.omniDispatch$bigIntegerToken(Map.of(OUTPUT, BigInteger.TWO));
        var forecast = List.of(new OmniBigIntegerOutput(OUTPUT, BigInteger.TEN), new OmniBigIntegerOutput(RETURNED, HUGE));
        callback.onAdmitted(forecast);
        var produced = List.of(new OmniBigIntegerOutput(OUTPUT, BigInteger.TWO),
                new OmniBigIntegerOutput(OUTPUT, BigInteger.valueOf(3)), new OmniBigIntegerOutput(RETURNED, BigInteger.ONE));
        callback.onOutputs(produced);
        assertEquals(BigInteger.ZERO, ledger().available(job.waiting, null, RETURNED));
        callback.confirmAccepted(BigInteger.TWO);
        callback.onOutputs(produced);
        assertEquals(BigInteger.ONE, ledger().available(job.waiting, null, OUTPUT));
        assertEquals(BigInteger.ONE, ledger().available(job.waiting, null, RETURNED));
    }

    private OmniCpuDispatchHost.ExactBatchToken nativeCallback() {
        var callback = cpu.omniDispatch$bigIntegerToken(Map.of(OUTPUT, BigInteger.ONE));
        callback.confirmAccepted(BigInteger.ONE);
        return callback;
    }

    @Test void ordinaryCommitAdvancesExactlyCraftCount() throws Exception {
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(3)));
        assertNotNull(stage); assertEquals(3, job.waiting.list.get(OUTPUT)); assertEquals(3, job.waiting.list.get(RETURNED));
        assertEquals(10, task.value);
        stage.commit(); stage.commit();
        assertEquals(7, task.value, "No extra AE decrement is part of the independent dispatcher");
        var tail = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(7))); tail.commit();
        assertEquals(0, task.value); assertTrue(job.tasks.isEmpty());
        assertEquals(0, cpu.legacyAccepted);
    }
    @Test void actualAcceptanceCommitsOnlyAcceptedCopiesAndWithdrawsExcessWaiting() {
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(5)));
        assertNotNull(stage);
        stage.commit(receipt(BigInteger.TWO));
        assertEquals(8, task.value);
        assertEquals(2, job.waiting.list.get(OUTPUT));
        assertEquals(2, job.waiting.list.get(RETURNED));
        assertEquals(0, cpu.legacyAccepted);
    }
    @Test void exactCommitStoresTheNextWindowWithoutTheLegacyPlusOne() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of()); state.setOutputRemaining(HUGE);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var stage = cpu.omniDispatch$stage(receipt(HUGE.subtract(BigInteger.valueOf(4))));
        assertNotNull(stage); stage.commit();
        assertEquals(BigInteger.valueOf(4), state.remaining(pattern)); assertEquals(4, task.value);
        assertEquals(HUGE.subtract(BigInteger.valueOf(4)), state.lastBatches().get(OUTPUT).outputAmount());
        assertEquals(0, cpu.legacyAccepted);
    }
    @Test void rollbackAndRetainUseTheActualOrdinaryWaitingLedger() {
        job.waiting.list.set(OUTPUT, 11);
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(3)));
        stage.rollback();
        assertEquals(11, job.waiting.list.get(OUTPUT)); assertEquals(0, job.waiting.list.get(RETURNED)); assertEquals(10, task.value);
        stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(3))); stage.retain();
        assertEquals(14, job.waiting.list.get(OUTPUT)); assertEquals(10, task.value); assertTrue(cpu.omniDispatch$stopped());
    }
    @Test void synchronousBigIntermediateReceptionOwnsAllOverflowBeforeObserverFailures() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of());
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var stage = cpu.omniDispatch$stage(receipt(HUGE));
        assertEquals(HUGE, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), RETURNED, HUGE, Actionable.SIMULATE));
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(RETURNED));
        assertEquals(HUGE, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), RETURNED, HUGE, Actionable.MODULATE));
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(RETURNED));
        assertEquals(0, job.waiting.list.get(RETURNED)); assertEquals(BigInteger.ZERO, state.uncreditedOutput(RETURNED));
        assertEquals(HUGE, state.completedOutputs().get(RETURNED));
        stage.commit(); assertTrue(job.tasks.isEmpty());
        assertEquals(BigInteger.ZERO, cpu.omniDispatch$insertExact(UUID.randomUUID(), RETURNED, HUGE, Actionable.MODULATE));
    }
    @Test void finalReceptionHonorsRequesterCapacityAndDefersCompletionUntilAfterCommit() throws Exception {
        cpu.link.capacity = 3;
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.TEN));
        assertEquals(BigInteger.TEN, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.SIMULATE));
        assertEquals(0, cpu.link.delivered);
        assertEquals(BigInteger.TEN, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.MODULATE));
        assertEquals(0, cpu.link.delivered); assertEquals(100, job.remaining);
        invoke("molecularmanipulator$flushFinalStock"); assertEquals(0, cpu.link.delivered, "A staged task cannot finish early");
        stage.commit(); cpu.suspended = true; invoke("molecularmanipulator$flushFinalStock");
        assertEquals(0, cpu.link.delivered, "Paused jobs must not deliver buffered outputs");
        cpu.suspended = false; invoke("molecularmanipulator$flushFinalStock");
        assertEquals(3, cpu.link.delivered); assertEquals(97, job.remaining);
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.valueOf(7), ledger().finalStock().get(OUTPUT));
    }
    @Test void standaloneFinalReceptionHonorsNetworkStorageCapacity() throws Exception {
        cpu.link.standalone = true;
        long[] capacity = {4}, stored = {0};
        var storage = (appeng.api.storage.MEStorage) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{appeng.api.storage.MEStorage.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("insert")) return null;
                    long accepted = Math.min((long) args[1], capacity[0]);
                    if (args[2] == Actionable.MODULATE) { capacity[0] -= accepted; stored[0] += accepted; }
                    return accepted;
                });
        var service = (appeng.api.networking.storage.IStorageService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{appeng.api.networking.storage.IStorageService.class},
                (proxy, method, args) -> method.getName().equals("getInventory") ? storage : null);
        grid = (appeng.api.networking.IGrid) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{appeng.api.networking.IGrid.class},
                (proxy, method, args) -> method.getName().equals("getStorageService") ? service : null);
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.TEN));
        assertEquals(BigInteger.TEN, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.MODULATE));
        assertEquals(0, stored[0]); stage.commit(); invoke("molecularmanipulator$flushFinalStock");
        assertEquals(4, stored[0]); assertEquals(0, cpu.link.delivered); assertEquals(96, job.remaining);
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.valueOf(6), ledger().finalStock().get(OUTPUT));
    }
    @Test void fullRequesterRetainsOwnedProductsAndPauseAcknowledgesNoFurtherOwnership() throws Exception {
        cpu.link.capacity = 0;
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(2)));
        assertEquals(BigInteger.TWO, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TWO, Actionable.MODULATE));
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.TWO, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(100, job.remaining);
        stage.commit(); cpu.omniDispatch$pause(null, "uncertain provider");
        assertTrue(cpu.suspended);
        assertEquals(BigInteger.ZERO, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), RETURNED, HUGE, Actionable.MODULATE));
    }
    @Test void directStageOutsideAnOwnedTickIsRejectedButInFlightProductsCanReturn() throws Exception {
        cpu.omniDispatch$stage(receipt(BigInteger.TWO)).commit();
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchJob", null);
        assertTrue(cpu.omniDispatch$stopped());
        assertNull(cpu.omniDispatch$stage(receipt(BigInteger.ONE)));
        assertEquals(BigInteger.TWO, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), RETURNED,
                BigInteger.TWO, Actionable.MODULATE));
    }
    @Test void simulatedCapacityThenPartialRealAcceptanceCountsOnlyRealDelivery() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, BigInteger.TEN), Map.of());
        state.setOutputRemaining(BigInteger.valueOf(100));
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        cpu.link.modulateLimit = 2;
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.TEN));
        cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.MODULATE);
        stage.commit();
        invoke("molecularmanipulator$flushFinalStock");
        assertEquals(2, cpu.link.delivered);
        assertEquals(BigInteger.valueOf(98), state.outputProgress().remaining());
        assertEquals(98, job.remaining);
        assertEquals(BigInteger.valueOf(8), ledger().finalStock().get(OUTPUT));
        assertEquals(0, job.waiting.list.get(OUTPUT));
    }
    @Test void feedbackReserveRemainsSpendableAndReleasesAsTasksAdvance() throws Exception {
        cpu.reserve = BigInteger.valueOf(8);
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.TEN));
        cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.MODULATE);
        stage.commit();
        invoke("molecularmanipulator$flushFinalStock");
        assertEquals(2, cpu.link.delivered);
        assertEquals(BigInteger.valueOf(8), cpu.ecoOmni$physicalAmount(OUTPUT));
        var lease = cpu.omniDispatch$reserveExact(Map.of(OUTPUT, BigInteger.valueOf(8)));
        assertNotNull(lease, "Feedback must remain available to the real input reservation");
        lease.refund();
        cpu.reserve = BigInteger.ZERO;
        invoke("molecularmanipulator$flushFinalStock");
        assertEquals(10, cpu.link.delivered);
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(90, job.remaining);
    }
    @Test void untaggedCpuKeepsInitialFinalKeyInputUntilItsActualTaskIsAccepted() throws Exception {
        job.output = new GenericStack(MATERIAL, 100);
        cpu.inventory.insert(MATERIAL, 1, Actionable.MODULATE);
        invoke("molecularmanipulator$flushFinalStock");
        assertEquals(0, cpu.link.delivered);
        assertEquals(BigInteger.ONE, cpu.ecoOmni$physicalAmount(MATERIAL));
        cpu.omniDispatch$stage(receipt(BigInteger.TEN)).commit();
        invoke("molecularmanipulator$flushFinalStock");
        assertEquals(1, cpu.link.delivered);
        assertEquals(99, job.remaining);
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(MATERIAL));
    }

    @Test void surplusReceptionClearsAllExpectedWaitingAndNeverOverdeliversGoal() throws Exception {
        job.remaining = 3;
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(4)));
        assertEquals(BigInteger.valueOf(4), cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT,
                BigInteger.valueOf(4), Actionable.MODULATE));
        stage.commit(); // Six crafts remain, so the unit fixture need not invoke AE2's private finalizer.
        invoke("molecularmanipulator$flushFinalStock");
        assertEquals(3, cpu.link.delivered);
        assertEquals(0, job.remaining);
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.ONE, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertFalse(ledger().hasFinalStock());
    }
    @Test void workstationByproductsCanArriveBeforeExpectedOutputWithoutAdvancingTasks() throws Exception {
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.valueOf(4)));
        var id = cpu.link.getCraftingID();
        assertEquals(HUGE, cpu.omniDispatch$insertExact(id, MATERIAL, HUGE, Actionable.SIMULATE));
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(HUGE, cpu.omniDispatch$insertExact(id, MATERIAL, HUGE, Actionable.MODULATE));
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(4, job.waiting.list.get(OUTPUT));
        assertEquals(10, task.value);
        assertEquals(100, job.remaining);
        assertEquals(BigInteger.valueOf(7), cpu.omniDispatch$insertExact(id, OUTPUT,
                BigInteger.valueOf(7), Actionable.MODULATE));
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.valueOf(7), cpu.ecoOmni$physicalAmount(OUTPUT));
        stage.commit();
        assertEquals(6, task.value);
        assertEquals(0, cpu.link.delivered, "Taking physical ownership must not deliver the goal reentrantly");
    }

    @Test void unexpectedSynchronousOutputPreventsRollbackEvenWithoutMatchingWaitingKey() throws Exception {
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.ONE));
        assertEquals(BigInteger.ONE, cpu.omniDispatch$insertExact(cpu.link.getCraftingID(), MATERIAL,
                BigInteger.ONE, Actionable.MODULATE));
        assertThrows(IllegalStateException.class, stage::rollback);
        assertEquals(BigInteger.ONE, cpu.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(1, job.waiting.list.get(OUTPUT));
        stage.retain();
        assertTrue(ledger().paused());
    }

    @Test void aboveLongSurplusIsRetainedButOnlyExpectedProductsCompleteWaiting() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, BigInteger.TEN), Map.of());
        state.setOutputRemaining(BigInteger.valueOf(100));
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var stage = cpu.omniDispatch$stage(receipt(BigInteger.TEN));
        var id = cpu.link.getCraftingID();
        assertEquals(HUGE, cpu.omniDispatch$insertExact(id, OUTPUT, HUGE, Actionable.SIMULATE));
        assertEquals(10, job.waiting.list.get(OUTPUT));
        assertEquals(HUGE, cpu.omniDispatch$insertExact(id, OUTPUT, HUGE, Actionable.MODULATE));
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(BigInteger.TEN, state.completedOutputs().get(OUTPUT));
        assertEquals(BigInteger.ZERO, state.uncreditedOutput(OUTPUT));
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.valueOf(100), state.outputProgress().remaining());
        stage.commit();
        var data = save();
        nbtHook(CraftingCpuLogicMixin.class, "molecularmanipulator$resetPreviousExactInventory", cpu, data);
        nbtHook(CraftingCpuLogicMixin.class, "molecularmanipulator$readExactCraftingState", cpu, data);
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(BigInteger.ZERO, cpu.omniDispatch$insertExact(UUID.randomUUID(), OUTPUT, HUGE, Actionable.MODULATE));
        set(CraftingCpuLogicMixin.class, cpu, "job", null);
        assertEquals(BigInteger.ZERO, cpu.omniDispatch$insertExact(id, OUTPUT, HUGE, Actionable.MODULATE));
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(OUTPUT));
    }

    @Test void aelisMetadataEnablesExactDispatchWithoutAnEcoPlanOrLargeTaskCount() throws Exception {
        var plan = aelisPlan();
        assertTrue(AelisExactCraftingPlanApi.read(plan).executionRequirement().requiresExactExecution());
        assertFalse(OmniCpuDispatchRunner.class.isInstance(cpu), "The shared host must recognize Aelis without an ECO plan runner");
        cpu.inventory.insert(RETURNED, 123, Actionable.MODULATE);
        var callback = attach(plan, CraftingSubmitResult.successful(cpu.link));
        assertTrue(callback.getReturnValue().successful());
        assertTrue(cpu.omniDispatch$exactOrder());
        assertEquals(BigInteger.TEN, cpu.ecoOmni$exactState().remaining(pattern));
        assertEquals(10, task.value);
        assertEquals(10, job.remaining);
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(RETURNED), "Projected infinite stock is discarded");
        assertTrue(save().getCompound(OmniCpuDispatchLedger.NBT_KEY).getBoolean("exactOrder"));
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinarySubmissionClearsPreviousExactFlagDespiteHugeStock(boolean attachOrdinaryMetadata) throws Exception {
        ledger().exactOrder(true);
        storePhysical(MATERIAL, HUGE);
        ICraftingPlan plan = ordinaryPlan();
        if (attachOrdinaryMetadata) plan = AelisExactCraftingPlanApi.attachExecutionMetadata(
                plan, BigInteger.TEN, Map.of(pattern, BigInteger.TEN), Map.of());
        assertFalse(AelisExactCraftingPlanApi.read(plan).executionRequirement().requiresExactExecution());
        assertTrue(attach(plan, CraftingSubmitResult.successful(cpu.link)).getReturnValue().successful());
        assertFalse(cpu.omniDispatch$exactOrder());
        assertNull(cpu.ecoOmni$exactState());
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(MATERIAL));
        assertFalse(save().getCompound(OmniCpuDispatchLedger.NBT_KEY).getBoolean("exactOrder"));
    }
    @Test void rejectedAelisSubmissionDoesNotEnableExactDispatch() throws Exception {
        assertFalse(attach(aelisPlan(), CraftingSubmitResult.INCOMPLETE_PLAN).getReturnValue().successful());
        assertFalse(cpu.omniDispatch$exactOrder());
        assertNull(cpu.ecoOmni$exactState());
    }
    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "missingFlag", "legacySave"})
    void restoringExactStateAndHugeInventoryHonorsOnlyTheSavedDispatchFlag(String flag) throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of());
        state.setOutputRemaining(HUGE);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        storePhysical(MATERIAL, HUGE);
        boolean expected = flag.equals("true");
        ledger().exactOrder(expected);
        var data = save();
        if (flag.equals("missingFlag")) data.getCompound(OmniCpuDispatchLedger.NBT_KEY).remove("exactOrder");
        if (flag.equals("legacySave")) data.remove(OmniCpuDispatchLedger.NBT_KEY);
        ledger().exactOrder(!expected);
        nbtHook(CraftingCpuLogicMixin.class, "molecularmanipulator$resetPreviousExactInventory", cpu, data);
        nbtHook(CraftingCpuLogicMixin.class, "molecularmanipulator$readExactCraftingState", cpu, data);
        assertEquals(expected, cpu.omniDispatch$exactOrder());
        assertNotSame(state, cpu.ecoOmni$exactState());
        assertEquals(HUGE, cpu.ecoOmni$exactState().remaining(pattern));
        assertEquals(HUGE, cpu.ecoOmni$exactState().outputProgress().remaining());
        assertEquals(OmniExactCraftingState.MAX_WINDOW, task.value);
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(MATERIAL));
    }
    private void storePhysical(AEKey key, BigInteger amount) throws Exception {
        ((com.atir.molecularmanipulator.crafting.OmniExactInventory) get("molecularmanipulator$exactInventory"))
                .insertWithoutNotification(cpu.inventory, key, amount);
    }
    private ICraftingPlan ordinaryPlan() {
        return new CraftingPlan(new GenericStack(OUTPUT, 10), 1, false, false,
                new appeng.api.stacks.KeyCounter(), new appeng.api.stacks.KeyCounter(),
                new appeng.api.stacks.KeyCounter(), Map.of(pattern, 10L));
    }
    private ICraftingPlan aelisPlan() {
        return AelisExactCraftingPlanApi.attachExecutionMetadata(ordinaryPlan(), BigInteger.TEN,
                Map.of(pattern, BigInteger.TEN), Map.of(RETURNED, HUGE));
    }
    private CallbackInfoReturnable<ICraftingSubmitResult> attach(ICraftingPlan plan, ICraftingSubmitResult result) throws Exception {
        var hook = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$attachExactCraftingState",
                appeng.api.networking.IGrid.class, ICraftingPlan.class, appeng.api.networking.security.IActionSource.class,
                appeng.api.networking.crafting.ICraftingRequester.class, CallbackInfoReturnable.class);
        hook.setAccessible(true);
        var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("trySubmitJob", true, result);
        hook.invoke(cpu, null, plan, null, null, callback);
        return callback;
    }
    private CompoundTag save() throws Exception {
        var data = new CompoundTag();
        nbtHook(CraftingCpuLogicMixin.class, "molecularmanipulator$writeExactCraftingState", cpu, data);
        return data;
    }
    private static void nbtHook(Class<?> type, String name, Object receiver, Object... args) throws Exception {
        var parameters = new java.util.ArrayList<Object>(java.util.List.of(args));
        parameters.add(REGISTRIES); parameters.add(new CallbackInfo(name, false));
        var method = java.util.Arrays.stream(type.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        method.setAccessible(true); method.invoke(receiver, parameters.toArray());
    }
    private boolean normalize() throws Exception {
        var hook = CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$normalizeExternalScaledTasks",
                appeng.crafting.execution.ExecutingCraftingJob.class, Map.class, Map.class, BigInteger.class);
        hook.setAccessible(true);
        return (boolean) hook.invoke(cpu, job, null, Map.of(), null);
    }

    private NativeAllowance nativeAllowance(long copies) throws Exception {
        var allowance = new NativeAllowance(copies);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$allowance", allowance);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$executing", true);
        return allowance;
    }

    private static final class NativeAllowance implements OmniDispatchAllowance {
        private long copies;
        private boolean capacity;
        private NativeAllowance(long copies) { this.copies = copies; }
        public boolean capacityMode() { return capacity; }
        public long remainingCopies() { return copies; }
        public boolean tryWork() { throw new AssertionError("Native dispatch uses AE2's working loop"); }
        public boolean trySingle() { throw new AssertionError("The capacity dispatcher owns the single-call budget"); }
        public void recordAccepted(BigInteger crafts) { copies -= crafts.longValueExact(); }
        public void close() { }
    }

    private OmniCpuDispatchLedger ledger() throws Exception {
        return (OmniCpuDispatchLedger) get("molecularmanipulator$dispatchLedger");
    }
    private OmniCpuDispatchReceipt receipt(BigInteger count) {
        return new OmniCpuDispatchReceipt(cpu.link.getCraftingID(), pattern, count,
                Map.of(MATERIAL, count), Map.of(OUTPUT, count), Map.of(RETURNED, count), false);
    }
    private Object get(String name) throws Exception {
        var field = CraftingCpuLogicMixin.class.getDeclaredField(name); field.setAccessible(true); return field.get(cpu);
    }
    private void invoke(String name) throws Exception {
        var method = CraftingCpuLogicMixin.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(cpu);
    }
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    private static final class Task { long value; }
    private static final class Cpu extends CraftingCpuLogicMixin {
        final ListCraftingInventory inventory = new ListCraftingInventory(key -> {});
        KeyCounter waiting;
        IntUnaryOperator nativeCall;
        Link link; boolean suspended; boolean managed; int legacyAccepted; BigInteger reserve = BigInteger.ZERO;
        @Override public boolean ecoOmni$managed() { return managed; }
        @Override public BigInteger omniDispatch$reservedInput(AEKey key) { return reserve; }
        public ListCraftingInventory getInventory() { return inventory; }
        public ICraftingLink getLastLink() { return link; }
        public void setJobSuspended(boolean value) { suspended = value; }
        public boolean isJobSuspended() { return suspended; }
        public int executeCrafting(int maximum, appeng.me.service.CraftingService crafting,
                appeng.api.networking.energy.IEnergyService energy, net.minecraft.world.level.Level level) {
            if (nativeCall == null) throw new AssertionError("The host fixture does not execute the AE2 loop");
            return nativeCall.applyAsInt(maximum);
        }
        public boolean isOmniMaterialAllocator() { return true; }
        public long getWaitingFor(AEKey key) { return waiting.get(key); }
        public void ecoOmni$accepted(IPatternDetails pattern, BigInteger count) { legacyAccepted++; }
    }
    private static final class Core extends CraftingBlockEntity {
        IGridNode node;
        Core() { super(null, BlockPos.ZERO, null); }
        @Override public IGridNode getActionableNode() { return node; }
        @Override public void saveChanges() { }
    }
    private static final class Link extends CraftingLink {
        long capacity = Long.MAX_VALUE, delivered, modulateLimit = Long.MAX_VALUE;
        boolean standalone;
        @Override public boolean isStandalone() { return standalone; }
        Link() { super(tag(), (appeng.api.networking.crafting.ICraftingCPU) null); }
        @Override public long insert(AEKey key, long amount, Actionable mode) {
            long accepted = Math.min(amount, Math.min(capacity, mode == Actionable.MODULATE ? modulateLimit : Long.MAX_VALUE));
            if (mode == Actionable.MODULATE) { delivered += accepted; capacity -= accepted; }
            return accepted;
        }
        private static CompoundTag tag() {
            var tag = new CompoundTag(); tag.putUUID("craftId", UUID.randomUUID()); tag.putBoolean("req", false); return tag;
        }
    }
}
