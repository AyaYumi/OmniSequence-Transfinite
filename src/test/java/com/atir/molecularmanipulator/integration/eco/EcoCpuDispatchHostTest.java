package com.atir.molecularmanipulator.integration.eco;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.EcoCpuDispatchJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.mixin.CraftingCpuLogicMixin;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercises the actual shared CPU host methods; only game infrastructure and mixin accessors are fixtures. */
class EcoCpuDispatchHostTest {
    static { EcoMolecularTestPattern.bootstrap(); }
    private static final BigInteger HUGE = BigInteger.ONE.shiftLeft(100);
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final Class<?> EXACT_HOOKS = exactHooks();
    private Cpu cpu;
    private EcoCpuDispatchJob job;
    private final EcoMolecularTestPattern pattern = new EcoMolecularTestPattern(CONTAINER, key -> RETURNED);
    private Task task;
    private appeng.api.networking.IGrid grid;

    @BeforeEach void setup() throws Exception {
        Class.forName(EcoCpuDispatchTest.class.getName());
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
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchOwner", allocate(OmniComputationCoreBlockEntity.class));
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchAllowance", 100L);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$compatDispatchDeadlineNanos", System.nanoTime() + 30_000_000_000L);
        cpu.link = new Link();
    }
    @Test void sharedHostAndItsDescriptorsLoadWithEveryEcoImplementationUnavailable() throws Exception {
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
            for (var name : java.util.List.of(CraftingCpuLogicMixin.class.getName(), EcoCpuDispatchHost.class.getName(),
                    EcoCpuDispatchLedger.class.getName(), EcoCpuDispatchReceipt.class.getName(), EcoCpuDispatchRunner.class.getName())) {
                var type = Class.forName(name, false, isolated);
                assertNotNull(type.getDeclaredMethods());
            }
            assertNotNull(Class.forName(EcoCpuDispatchLedger.class.getName(), true, isolated).getConstructor().newInstance());
        }
    }
    @Test void allTwelveHostHooksAreConcreteInTheSharedCpu() {
        long hooks = java.util.Arrays.stream(CraftingCpuLogicMixin.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("ecoDispatch$")).count();
        assertEquals(12, hooks);
    }
    @Test void ordinaryCommitAdvancesExactlyCraftCountAndConsumesSharedWorkBudget() throws Exception {
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.valueOf(3)));
        assertNotNull(stage); assertEquals(3, job.waiting.list.get(OUTPUT)); assertEquals(3, job.waiting.list.get(RETURNED));
        assertEquals(10, task.value);
        stage.commit(); stage.commit();
        assertEquals(7, task.value, "No extra AE decrement is part of the independent dispatcher");
        assertEquals(1L, get("molecularmanipulator$dispatchUsed"));
        var tail = cpu.ecoDispatch$stage(receipt(BigInteger.valueOf(7))); tail.commit();
        assertEquals(0, task.value); assertTrue(job.tasks.isEmpty()); assertEquals(2L, get("molecularmanipulator$dispatchUsed"));
        assertEquals(0, cpu.legacyAccepted);
    }
    @Test void exactCommitStoresTheNextWindowWithoutTheLegacyPlusOne() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of()); state.setOutputRemaining(HUGE);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var stage = cpu.ecoDispatch$stage(receipt(HUGE.subtract(BigInteger.valueOf(4))));
        assertNotNull(stage); stage.commit();
        assertEquals(BigInteger.valueOf(4), state.remaining(pattern)); assertEquals(4, task.value);
        assertEquals(HUGE.subtract(BigInteger.valueOf(4)), state.lastBatches().get(OUTPUT).outputAmount());
        assertEquals(0, cpu.legacyAccepted);
    }
    @Test void rollbackAndRetainUseTheActualOrdinaryWaitingLedger() {
        job.waiting.list.set(OUTPUT, 11);
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.valueOf(3)));
        stage.rollback();
        assertEquals(11, job.waiting.list.get(OUTPUT)); assertEquals(0, job.waiting.list.get(RETURNED)); assertEquals(10, task.value);
        stage = cpu.ecoDispatch$stage(receipt(BigInteger.valueOf(3))); stage.retain();
        assertEquals(14, job.waiting.list.get(OUTPUT)); assertEquals(10, task.value); assertTrue(cpu.ecoDispatch$stopped());
    }
    @Test void synchronousBigIntermediateReceptionOwnsAllOverflowBeforeObserverFailures() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, HUGE), Map.of());
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        var stage = cpu.ecoDispatch$stage(receipt(HUGE));
        assertEquals(HUGE, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), RETURNED, HUGE, Actionable.SIMULATE));
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(RETURNED));
        assertEquals(HUGE, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), RETURNED, HUGE, Actionable.MODULATE));
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(RETURNED));
        assertEquals(0, job.waiting.list.get(RETURNED)); assertEquals(BigInteger.ZERO, state.uncreditedOutput(RETURNED));
        assertEquals(HUGE, state.completedOutputs().get(RETURNED));
        stage.commit(); assertTrue(job.tasks.isEmpty());
        assertEquals(BigInteger.ZERO, cpu.ecoDispatch$insertExact(UUID.randomUUID(), RETURNED, HUGE, Actionable.MODULATE));
    }
    @Test void finalReceptionHonorsRequesterCapacityAndDefersCompletionUntilAfterCommit() throws Exception {
        cpu.link.capacity = 3;
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.TEN));
        assertEquals(BigInteger.TEN, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, HUGE, Actionable.SIMULATE));
        assertEquals(0, cpu.link.delivered);
        assertEquals(BigInteger.TEN, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, HUGE, Actionable.MODULATE));
        assertEquals(0, cpu.link.delivered); assertEquals(100, job.remaining);
        invoke("molecularmanipulator$flushEcoFinalStock"); assertEquals(0, cpu.link.delivered, "A staged task cannot finish early");
        stage.commit(); cpu.suspended = true; invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(0, cpu.link.delivered, "Paused jobs must not deliver buffered outputs");
        cpu.suspended = false; invoke("molecularmanipulator$flushEcoFinalStock");
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
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.TEN));
        assertEquals(BigInteger.TEN, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, HUGE, Actionable.MODULATE));
        assertEquals(0, stored[0]); stage.commit(); invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(4, stored[0]); assertEquals(0, cpu.link.delivered); assertEquals(96, job.remaining);
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.valueOf(6), ledger().finalStock().get(OUTPUT));
    }
    @Test void fullRequesterRetainsOwnedProductsAndPauseAcknowledgesNoFurtherOwnership() throws Exception {
        cpu.link.capacity = 0;
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.valueOf(2)));
        assertEquals(BigInteger.TWO, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, HUGE, Actionable.MODULATE));
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.TWO, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(100, job.remaining);
        stage.commit(); cpu.ecoDispatch$pause(null, "uncertain provider");
        assertTrue(cpu.suspended);
        assertEquals(BigInteger.ZERO, cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), RETURNED, HUGE, Actionable.MODULATE));
    }
    @Test void sharedQuotaAndExpiredDeadlineStopAnOuterCpuLoop() throws Exception {
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchAllowance", 1L);
        assertFalse(cpu.ecoDispatch$stopped()); cpu.ecoDispatch$stage(receipt(BigInteger.TWO)).commit();
        assertTrue(cpu.ecoDispatch$stopped());
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchStopped", false);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchAllowance", 100L);
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$compatDispatchDeadlineNanos", System.nanoTime() - 1);
        assertTrue(cpu.ecoDispatch$stopped());
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$dispatchOwner", null);
        assertTrue(cpu.ecoDispatch$stopped(), "Direct calls outside a claimed Omni tick cannot dispatch");
    }
    @Test void simulatedCapacityThenPartialRealAcceptanceCountsOnlyRealDelivery() throws Exception {
        var state = OmniExactCraftingState.create(Map.of(pattern, BigInteger.TEN), Map.of());
        state.setOutputRemaining(BigInteger.valueOf(100));
        set(CraftingCpuLogicMixin.class, cpu, "molecularmanipulator$exactState", state);
        cpu.link.modulateLimit = 2;
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.TEN));
        cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.MODULATE);
        stage.commit();
        invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(2, cpu.link.delivered);
        assertEquals(BigInteger.valueOf(98), state.outputProgress().remaining());
        assertEquals(98, job.remaining);
        assertEquals(BigInteger.valueOf(8), ledger().finalStock().get(OUTPUT));
        assertEquals(0, job.waiting.list.get(OUTPUT));
    }
    @Test void feedbackReserveRemainsSpendableAndReleasesAsTasksAdvance() throws Exception {
        cpu.reserve = BigInteger.valueOf(8);
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.TEN));
        cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT, BigInteger.TEN, Actionable.MODULATE);
        stage.commit();
        invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(2, cpu.link.delivered);
        assertEquals(BigInteger.valueOf(8), cpu.ecoOmni$physicalAmount(OUTPUT));
        var lease = cpu.ecoDispatch$reserveExact(Map.of(OUTPUT, BigInteger.valueOf(8)));
        assertNotNull(lease, "Feedback must remain available to the real input reservation");
        lease.refund();
        cpu.reserve = BigInteger.ZERO;
        invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(10, cpu.link.delivered);
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(90, job.remaining);
    }
    @Test void untaggedCpuKeepsInitialFinalKeyInputUntilItsActualTaskIsAccepted() throws Exception {
        job.output = new GenericStack(MATERIAL, 100);
        cpu.inventory.insert(MATERIAL, 1, Actionable.MODULATE);
        invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(0, cpu.link.delivered);
        assertEquals(BigInteger.ONE, cpu.ecoOmni$physicalAmount(MATERIAL));
        cpu.ecoDispatch$stage(receipt(BigInteger.TEN)).commit();
        invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(1, cpu.link.delivered);
        assertEquals(99, job.remaining);
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(MATERIAL));
    }

    @Test void surplusReceptionClearsAllExpectedWaitingAndNeverOverdeliversGoal() throws Exception {
        job.remaining = 3;
        var stage = cpu.ecoDispatch$stage(receipt(BigInteger.valueOf(4)));
        assertEquals(BigInteger.valueOf(4), cpu.ecoDispatch$insertExact(cpu.link.getCraftingID(), OUTPUT,
                BigInteger.valueOf(4), Actionable.MODULATE));
        stage.commit(); // Six crafts remain, so the unit fixture need not invoke AE2's private finalizer.
        invoke("molecularmanipulator$flushEcoFinalStock");
        assertEquals(3, cpu.link.delivered);
        assertEquals(0, job.remaining);
        assertEquals(0, job.waiting.list.get(OUTPUT));
        assertEquals(BigInteger.ONE, cpu.ecoOmni$physicalAmount(OUTPUT));
        assertFalse(ledger().hasFinalStock());
    }
    @Test void aelisMetadataEnablesExactDispatchWithoutAnEcoPlanOrLargeTaskCount() throws Exception {
        var plan = aelisPlan();
        assertTrue(AelisExactCraftingPlanApi.read(plan).executionRequirement().requiresExactExecution());
        assertFalse(EcoCpuDispatchRunner.class.isInstance(cpu), "The shared host must recognize Aelis without an ECO plan runner");
        cpu.inventory.insert(RETURNED, 123, Actionable.MODULATE);
        var callback = attach(plan, CraftingSubmitResult.successful(cpu.link));
        assertTrue(callback.getReturnValue().successful());
        assertTrue(cpu.ecoDispatch$exactOrder());
        assertEquals(BigInteger.TEN, cpu.ecoOmni$exactState().remaining(pattern));
        assertEquals(10, task.value);
        assertEquals(10, job.remaining);
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$physicalAmount(RETURNED), "Projected infinite stock is discarded");
        assertTrue(save().getCompound(EcoCpuDispatchLedger.NBT_KEY).getBoolean("exactOrder"));
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
        assertFalse(cpu.ecoDispatch$exactOrder());
        assertNull(cpu.ecoOmni$exactState());
        assertEquals(HUGE, cpu.ecoOmni$physicalAmount(MATERIAL));
        assertFalse(save().getCompound(EcoCpuDispatchLedger.NBT_KEY).getBoolean("exactOrder"));
    }
    @Test void rejectedAelisSubmissionDoesNotEnableExactDispatch() throws Exception {
        assertFalse(attach(aelisPlan(), CraftingSubmitResult.INCOMPLETE_PLAN).getReturnValue().successful());
        assertFalse(cpu.ecoDispatch$exactOrder());
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
        if (flag.equals("missingFlag")) data.getCompound(EcoCpuDispatchLedger.NBT_KEY).remove("exactOrder");
        if (flag.equals("legacySave")) data.remove(EcoCpuDispatchLedger.NBT_KEY);
        ledger().exactOrder(!expected);
        nbtHook(CraftingCpuLogicMixin.class, "molecularmanipulator$resetPreviousExactInventory", cpu, data);
        nbtHook(EXACT_HOOKS, "molecularmanipulator$readExactCraftingState", null, cpu, data);
        assertEquals(expected, cpu.ecoDispatch$exactOrder());
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
    @SuppressWarnings("unchecked")
    private CallbackInfoReturnable<ICraftingSubmitResult> attach(ICraftingPlan plan, ICraftingSubmitResult result) throws Exception {
        var field = OmniComputationCoreBlockEntity.class.getDeclaredField("CPU_OWNERS"); field.setAccessible(true);
        var owners = (Map<CraftingCPUCluster, OmniComputationCoreBlockEntity>) field.get(null);
        var cluster = (CraftingCPUCluster) get("cluster");
        owners.put(cluster, (OmniComputationCoreBlockEntity) get("molecularmanipulator$dispatchOwner"));
        var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("trySubmitJob", true, result);
        try {
            EXACT_HOOKS.getDeclaredMethod("molecularmanipulator$attachExactCraftingState", CraftingCpuLogicMixin.class,
                    appeng.api.networking.IGrid.class, ICraftingPlan.class, appeng.api.networking.security.IActionSource.class,
                    appeng.api.networking.crafting.ICraftingRequester.class, CallbackInfoReturnable.class)
                    .invoke(null, cpu, null, plan, null, null, callback);
            return callback;
        } finally { owners.remove(cluster); }
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
    /**
     * Run the compiled private hooks as nestmates. Only AE2 self casts/getInventory calls are adapted
     * to the unmixed fixture; metadata validation, state installation and flag writes stay intact.
     */
    private static Class<?> exactHooks() {
        String mixin = CraftingCpuLogicMixin.class.getName().replace('.', '/');
        String bridge = mixin + "$ExactAttachTest";
        String aeCpu = "appeng/crafting/execution/CraftingCpuLogic";
        var names = Set.of("molecularmanipulator$attachExactCraftingState",
                "molecularmanipulator$readExactCraftingState", "molecularmanipulator$discardProjectedInfiniteInputs");
        try (var stream = CraftingCpuLogicMixin.class.getResourceAsStream("/" + mixin + ".class")) {
            var original = new ClassNode(); new ClassReader(java.util.Objects.requireNonNull(stream)).accept(original, 0);
            var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, bridge, null, "java/lang/Object", null);
            for (var method : original.methods) {
                if (!names.contains(method.name)) continue;
                method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                method.desc = "(L" + mixin + ";" + method.desc.substring(1);
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                            && cast.desc.equals(aeCpu) && cast.getNext() instanceof MethodInsnNode next
                            && next.name.equals("getInventory")) cast.desc = mixin;
                    if (instruction instanceof MethodInsnNode call) {
                        if (call.owner.equals(aeCpu) && call.name.equals("getInventory")) call.owner = mixin;
                        if (call.owner.equals(mixin) && names.contains(call.name)) {
                            call.setOpcode(Opcodes.INVOKESTATIC); call.owner = bridge;
                            call.desc = "(L" + mixin + ";" + call.desc.substring(1);
                        }
                    }
                }
                method.accept(writer);
            }
            writer.visitEnd();
            return MethodHandles.privateLookupIn(CraftingCpuLogicMixin.class, MethodHandles.lookup())
                    .defineHiddenClass(writer.toByteArray(), true, MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private EcoCpuDispatchLedger ledger() throws Exception {
        return (EcoCpuDispatchLedger) get("molecularmanipulator$ecoLedger");
    }
    private EcoCpuDispatchReceipt receipt(BigInteger count) {
        return new EcoCpuDispatchReceipt(cpu.link.getCraftingID(), pattern, count,
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
        Link link; boolean suspended; int legacyAccepted; BigInteger reserve = BigInteger.ZERO;
        @Override public BigInteger ecoDispatch$reservedInput(AEKey key) { return reserve; }
        public ListCraftingInventory getInventory() { return inventory; }
        public ICraftingLink getLastLink() { return link; }
        public void setJobSuspended(boolean value) { suspended = value; }
        public boolean isJobSuspended() { return suspended; }
        public boolean isOmniMaterialAllocator() { return true; }
        public long getWaitingFor(AEKey key) { return 0; }
        public void ecoOmni$accepted(IPatternDetails pattern, BigInteger count) { legacyAccepted++; fail("Legacy phase hook used"); }
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
