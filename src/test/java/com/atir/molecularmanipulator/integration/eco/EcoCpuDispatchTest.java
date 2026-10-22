package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimization;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationRegistry;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.crafting.OmniExactInventory;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

class EcoCpuDispatchTest {
    static {
        EcoMolecularTestPattern.bootstrap();
        try { appeng.api.stacks.AEKeyTypesInternal.getRegistry(); }
        catch (IllegalStateException uninitialized) {
            var registry = new net.minecraft.core.MappedRegistry<appeng.api.stacks.AEKeyType>(
                    appeng.api.stacks.AEKeyType.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
            appeng.api.stacks.AEKeyTypesInternal.setRegistry(registry);
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.items());
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.fluids());
            registry.freeze();
        }
        try {
            var sentinel = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
            sentinel.setAccessible(true);
            sentinel.set(appeng.core.definitions.AEItems.MISSING_CONTENT,
                    net.neoforged.neoforge.registries.DeferredItem.createItem(ResourceLocation.withDefaultNamespace("barrier")));
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final AEItemKey CATALYST = AEItemKey.of(Items.STICK);
    private static final ResourceLocation ADAPTER = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "dispatch_test");

    @BeforeAll static void register() {
        ECOExecutionOptimizationRegistry.register(ADAPTER, 1001, new ECOExecutionOptimizationAdapter() {
            public boolean supports(IPatternDetails pattern) { return pattern instanceof EcoMolecularTestPattern; }
            public ECOExecutionOptimization describe(IPatternDetails pattern) { return ECOExecutionOptimization.VERIFIED_ASSEMBLER; }
        });
    }
    @AfterAll static void unregister() { ECOExecutionOptimizationRegistry.unregister(ADAPTER); }

    @Test void normalBatchUsesRealInventoryAndStagesWaitingBeforeDispatch() throws Exception {
        var f = normal(BigInteger.valueOf(73));
        var provider = new Provider(f.host);
        f.host.onStage = () -> assertEquals(73, f.host.window.list.get(MATERIAL), "Preview must leave the first copy in the CPU");
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(BigInteger.ZERO, f.host.remaining);
        assertEquals(BigInteger.valueOf(73), f.host.waiting.get(OUTPUT));
        assertEquals(1, provider.pushes);
        assertEquals(1, f.host.commits);
        assertEquals(0, f.host.refunds);
        assertFalse(f.dispatch.suspended());
        assertTrue(f.network.paid > 0);
    }

    @Test void statefulInvariantIsConsumedAndReturnedOnceForManyCrafts() throws Exception {
        var f = fixture(new EcoMolecularTestPattern(CATALYST, key -> key), BigInteger.valueOf(29), 1, 29);
        var provider = new Provider(f.host);
        provider.stateful = true;
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.ONE, f.host.last.inputs().get(CATALYST));
        assertEquals(BigInteger.ONE, f.host.last.remainders().get(CATALYST));
        assertEquals(BigInteger.valueOf(29), f.host.last.inputs().get(MATERIAL));
        assertEquals(BigInteger.valueOf(29), f.host.last.crafts());
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(CATALYST));
    }

    @Test void returnedContainersUseActualEcoStatefulContract() throws Exception {
        var f = fixture(new EcoMolecularTestPattern(CONTAINER, ignored -> RETURNED), BigInteger.valueOf(23), 23, 23);
        var provider = new Provider(f.host);
        provider.stateful = true;
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.valueOf(23), f.host.last.remainders().get(RETURNED));
        assertEquals(BigInteger.valueOf(23), f.host.last.inputs().get(CONTAINER));
        assertFalse(f.host.last.exact());
    }

    @Test void exactFxKeepsUuidAndCountsBeyondLongWithoutCallbackRegistry() throws Exception {
        var count = BigInteger.ONE.shiftLeft(96).add(BigInteger.valueOf(17));
        var f = normal(count);
        f.host.exactOrder = true;
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        assertEquals(1, execute(f, provider));
        assertEquals(count, provider.exactCount);
        assertEquals(f.host.id, provider.job);
        assertTrue(f.host.last.exact());
        assertEquals(count, f.host.last.inputs().get(MATERIAL));
        assertEquals(count, f.host.waiting.get(OUTPUT));
        assertEquals(BigInteger.ZERO, f.host.remaining);
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(1, provider.pushes);
    }

    @Test void nativeExactNeverAdmitsMoreThanRealMaterials() throws Exception {
        var f = normal(BigInteger.valueOf(100));
        f.host.stock.extract(f.host.window, MATERIAL, BigInteger.valueOf(95), Actionable.MODULATE);
        f.host.exactOrder = true;
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.valueOf(5), provider.exactCount);
        assertEquals(BigInteger.valueOf(95), f.host.remaining);
    }

    @Test void exactOrderMustBeExplicitEvenWithHugePhysicalInventory() throws Exception {
        var f = normal(BigInteger.ONE.shiftLeft(70));
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        assertEquals(1, execute(f, provider));
        assertEquals(0, provider.exactPrepares);
        assertFalse(f.host.last.exact());
        assertTrue(f.host.remaining.signum() > 0);
    }

    @Test void exactRejectionRefundsAllInputsAndReusesPersistentEnergyCredit() throws Exception {
        var f = normal(BigInteger.valueOf(7));
        f.host.exactOrder = true;
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        provider.mode = Mode.REJECT;
        assertEquals(-1, execute(f, provider));
        assertEquals(BigInteger.valueOf(7), f.host.ecoOmni$physicalAmount(MATERIAL));
        assertTrue(f.host.waiting.isEmpty());
        assertEquals(0, f.host.commits);
        assertFalse(f.dispatch.suspended());
        double charged = f.network.paid;
        var saved = f.dispatch.write(REGISTRIES);
        assertTrue(new BigDecimal(saved.getString("credit")).signum() > 0);
        f.dispatch.read(saved);
        provider.mode = Mode.ACCEPT;
        assertEquals(1, execute(f, provider));
        assertEquals(charged, f.network.paid, "Previously paid energy must not be charged again");
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(MATERIAL));
    }

    @Test void exactIndeterminateRetainsEveryInputAndStopsReplayAcrossReload() throws Exception {
        var count = BigInteger.ONE.shiftLeft(72);
        var f = normal(count);
        f.host.exactOrder = true;
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        provider.mode = Mode.INDETERMINATE;
        assertEquals(0, execute(f, provider));
        assertTrue(f.dispatch.suspended());
        assertTrue(f.host.paused);
        assertEquals(0, f.host.refunds);
        assertEquals(0, f.host.commits);
        assertEquals(count, f.host.remaining);
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(MATERIAL));
        var saved = f.dispatch.write(REGISTRIES);
        assertEquals(count.toString(), saved.getCompound("intent").getString("crafts"));
        assertEquals(f.host.id, saved.getCompound("intent").getUUID("job"));
        var restored = new EcoCpuDispatch();
        restored.read(saved);
        assertTrue(restored.suspended());
        assertEquals(0, restored.execute(f.host, 1, service(provider), f.network.service, level()));
        assertEquals(1, provider.pushes);
        assertEquals(saved.getCompound("intent"), restored.write(REGISTRIES).getCompound("intent"));
    }

    @Test void statefulIndeterminateDoesNotRefundToolOrConsumables() throws Exception {
        var f = fixture(new EcoMolecularTestPattern(CATALYST, key -> key), BigInteger.valueOf(11), 1, 11);
        var provider = new Provider(f.host);
        provider.stateful = true;
        provider.mode = Mode.INDETERMINATE;
        assertEquals(0, execute(f, provider));
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(CATALYST));
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(BigInteger.ONE, f.host.waiting.get(CATALYST));
        assertEquals(BigInteger.valueOf(11), f.host.remaining);
        assertEquals(0, f.host.commits);
        assertTrue(f.dispatch.suspended());
    }

    @Test void ordinaryProviderExceptionUsesEcoRollbackAndLeavesNoUncertainIntent() throws Exception {
        var f = normal(BigInteger.valueOf(9));
        var provider = new Provider(f.host);
        provider.mode = Mode.EXCEPTION;
        assertEquals(-1, execute(f, provider));
        assertEquals(BigInteger.valueOf(9), f.host.ecoOmni$physicalAmount(MATERIAL));
        assertTrue(f.host.waiting.isEmpty());
        assertFalse(f.dispatch.suspended());
        assertFalse(f.dispatch.write(REGISTRIES).contains("intent"));
    }

    @Test void failureAfterAcceptanceNeverRefundsOrAdvancesTheTaskTwice() throws Exception {
        var f = normal(BigInteger.valueOf(13));
        f.host.failCommit = true;
        var provider = new Provider(f.host);
        assertEquals(0, execute(f, provider));
        assertTrue(f.dispatch.suspended());
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(BigInteger.valueOf(13), f.host.waiting.get(OUTPUT));
        assertEquals(BigInteger.valueOf(13), f.host.remaining);
        assertEquals(0, f.host.refunds);
        assertEquals(0, execute(f, provider));
        assertEquals(1, provider.pushes);
    }

    @Test void providerAssertionFailureAlsoSuspendsInsteadOfReplaying() throws Exception {
        var f = normal(BigInteger.valueOf(3));
        var provider = new Provider(f.host);
        provider.mode = Mode.ASSERTION;
        assertEquals(0, execute(f, provider));
        assertTrue(f.dispatch.suspended());
        assertEquals(0, f.host.commits);
        assertEquals(1, provider.pushes);
    }

    @Test void incompleteHostDefaultsDoNotEnterTheDispatcher() throws Exception {
        var f = normal(BigInteger.valueOf(3));
        f.host.enabled = false;
        var provider = new Provider(f.host);
        assertEquals(-1, execute(f, provider));
        assertEquals(0, provider.pushes);
    }

    @Test void corruptCreditPreservesOriginalTagAndStopsDispatch() throws Exception {
        var f = normal(BigInteger.valueOf(3));
        var saved = new CompoundTag();
        saved.putInt("version", 1);
        saved.putString("credit", "broken");
        saved.putString("unknown", "preserve me");
        f.dispatch.read(saved);
        assertTrue(f.dispatch.suspended());
        assertEquals(saved, f.dispatch.write(REGISTRIES));
        assertEquals(0, execute(f, new Provider(f.host)));
    }

    @Test void interruptedIntentIsNotAutomaticallyRefundedEvenWithoutSuspendedFlag() throws Exception {
        var f = normal(BigInteger.valueOf(5));
        var journal = new EcoCpuDispatchJournal();
        journal.arm(new EcoCpuDispatchReceipt(f.host.id, f.host.pattern, BigInteger.valueOf(5),
                Map.of(MATERIAL, BigInteger.valueOf(5)), Map.of(OUTPUT, BigInteger.valueOf(5)), Map.of(), true));
        var saved = journal.write(REGISTRIES);
        assertFalse(saved.getBoolean("suspended"));
        f.dispatch.read(saved);
        assertTrue(f.dispatch.suspended());
        assertEquals(0, execute(f, new Provider(f.host)));
        assertEquals(BigInteger.valueOf(5), f.host.ecoOmni$physicalAmount(MATERIAL));
    }

    @Test void exactEnergyUsesCeilingDebitAndRetainsEveryRefundedFraction() {
        var bank = new EcoCpuDispatchEnergy();
        var network = new Network();
        var count = BigInteger.ONE.shiftLeft(80).add(BigInteger.valueOf(3));
        var cost = new BigDecimal(0.1).multiply(new BigDecimal(count));
        var reservation = bank.reserve(network.service, 0.1, count);
        assertNotNull(reservation);
        assertTrue(new BigDecimal(network.paid).compareTo(cost) >= 0);
        assertEquals(0, bank.credit().add(cost).compareTo(new BigDecimal(network.paid)));
        reservation.refund();
        reservation.refund();
        assertEquals(0, bank.credit().compareTo(new BigDecimal(network.paid)));
        var tag = new CompoundTag();
        bank.write(tag);
        var restored = new EcoCpuDispatchEnergy();
        restored.read(tag);
        double paid = network.paid;
        assertNotNull(restored.reserve(network.service, 0.1, count));
        assertEquals(paid, network.paid);
    }

    @Test void partialEnergyDebitBecomesCreditWithoutDeliveringMaterials() throws Exception {
        var f = normal(BigInteger.valueOf(4));
        f.network.partial = true;
        var provider = new Provider(f.host);
        assertEquals(-1, execute(f, provider));
        assertEquals(0, provider.pushes);
        assertEquals(BigInteger.valueOf(4), f.host.ecoOmni$physicalAmount(MATERIAL));
        assertTrue(f.host.waiting.isEmpty());
        assertTrue(new BigDecimal(f.dispatch.write(REGISTRIES).getString("credit")).signum() > 0);
    }

    @Test void uuidOutputBridgeTransfersWholeBigIntegerAndSimulationDoesNotMutate() throws Exception {
        var count = BigInteger.ONE.shiftLeft(90);
        var f = normal(count);
        f.host.waiting.put(OUTPUT, count);
        var logic = allocate(OutputLogic.class);
        logic.host = f.host;
        logic.dispatch = f.dispatch;
        var cluster = allocate(CraftingCPUCluster.class);
        var field = CraftingCPUCluster.class.getDeclaredField("craftingLogic");
        field.setAccessible(true);
        field.set(cluster, logic);
        var cpus = List.<appeng.api.networking.crafting.ICraftingCPU>of(cluster);
        assertNull(EcoCpuDispatchOutput.insert(cpus, UUID.randomUUID(), OUTPUT, count, Actionable.MODULATE));
        assertEquals(count, EcoCpuDispatchOutput.insert(cpus, f.host.id, OUTPUT, count, Actionable.SIMULATE));
        assertEquals(count, f.host.waiting.get(OUTPUT));
        assertEquals(BigInteger.ZERO, f.host.ecoOmni$physicalAmount(OUTPUT));
        assertEquals(count, EcoCpuDispatchOutput.insert(cpus, f.host.id, OUTPUT, count, Actionable.MODULATE));
        assertEquals(count, f.host.ecoOmni$physicalAmount(OUTPUT));
        assertTrue(f.host.waiting.isEmpty());
        var paused = new CompoundTag(); paused.putInt("version", 1); paused.putBoolean("suspended", true);
        f.dispatch.read(paused);
        f.host.waiting.put(OUTPUT, count);
        assertEquals(BigInteger.ZERO, EcoCpuDispatchOutput.insert(cpus, f.host.id, OUTPUT, count, Actionable.MODULATE));
        assertEquals(count, f.host.waiting.get(OUTPUT));
    }

    @Test void dispatcherUsesOwnPhaseTokenAndCommitsEachPhaseExactlyOnce() throws Exception {
        var f = normal(BigInteger.valueOf(8));
        f.host.exactOrder = true;
        attachPlan(f.host, 3, 5);
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.valueOf(3), provider.exactCount);
        assertEquals(Map.of(0, BigInteger.ZERO, 1, BigInteger.valueOf(5)), f.host.execution.remainingByTask());
        assertEquals(BigInteger.valueOf(5), f.host.remaining);
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.valueOf(5), provider.exactCount);
        assertTrue(f.host.execution.isComplete());
        assertEquals(2, f.host.commits);
        assertEquals(0, f.host.legacyPhaseCommits);
    }

    @Test void phaseDispatcherDoesNotMultiplyReusableToolMaterialLimit() throws Exception {
        var f = fixture(new EcoMolecularTestPattern(CATALYST, key -> key), BigInteger.valueOf(31), 1, 31);
        attachPlan(f.host, 31);
        var provider = new Provider(f.host);
        provider.stateful = true;
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.valueOf(31), f.host.last.crafts());
        assertEquals(BigInteger.ONE, f.host.last.inputs().get(CATALYST));
        assertTrue(f.host.execution.isComplete());
        assertEquals(0, f.host.legacyPhaseCommits);
    }

    @Test void phaseIsNotCommittedForIndeterminateNativeAcceptance() throws Exception {
        var f = normal(BigInteger.valueOf(7));
        f.host.exactOrder = true;
        attachPlan(f.host, 7);
        var provider = new Provider(f.host);
        provider.nativeExact = true;
        provider.mode = Mode.INDETERMINATE;
        assertEquals(0, execute(f, provider));
        assertEquals(Map.of(0, BigInteger.valueOf(7)), f.host.execution.remainingByTask());
        assertEquals(0, f.host.commits);
        assertEquals(0, f.host.legacyPhaseCommits);
    }

    @Test void statefulDagBatchStopsAtAllocatedVariantBoundary() throws Exception {
        var f = fixture(new EcoMolecularTestPattern(CATALYST, key -> key), BigInteger.valueOf(5), 1, 5);
        var stack = new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("second run"));
        var variant = AEItemKey.of(stack);
        f.host.ecoOmni$storePhysical(variant, BigInteger.valueOf(3));
        var allocation = new cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation(1, List.of(
                new cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation.Run(MATERIAL, 1, 2),
                new cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation.Run(variant, 1, 3)));
        attachPlan(f.host, List.of(allocation), 5);
        var provider = new Provider(f.host); provider.stateful = true;
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.TWO, f.host.last.crafts());
        assertEquals(BigInteger.TWO, f.host.last.inputs().get(MATERIAL));
        assertFalse(f.host.last.inputs().containsKey(variant));
        assertEquals(BigInteger.ONE, f.host.ecoDispatch$insertExact(f.host.id, CATALYST, BigInteger.ONE, Actionable.MODULATE));
        assertEquals(1, execute(f, provider));
        assertEquals(BigInteger.valueOf(3), f.host.last.crafts());
        assertEquals(BigInteger.valueOf(3), f.host.last.inputs().get(variant));
        assertFalse(f.host.last.inputs().containsKey(MATERIAL));
        assertEquals(BigInteger.valueOf(3), f.host.ecoOmni$physicalAmount(MATERIAL));
        assertTrue(f.host.execution.isComplete());
    }

    @Test void reloadingAStateWithoutDispatchTagDoesNotInheritAnEarlierPause() throws Exception {
        var optional = new com.atir.molecularmanipulator.mixin.EcoCpuDispatchMixin() {};
        optional.ecoDispatch$suspend("previous job"); assertTrue(optional.ecoDispatch$dispatcher().suspended());
        var read = com.atir.molecularmanipulator.mixin.EcoCpuDispatchMixin.class.getDeclaredMethod(
                "molecularmanipulator$readEcoDispatch", CompoundTag.class, HolderLookup.Provider.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
        read.setAccessible(true);
        read.invoke(optional, new CompoundTag(), REGISTRIES,
                (com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void>) args -> null);
        assertFalse(optional.ecoDispatch$dispatcher().suspended());
    }

    @Test void ordinaryEnergyCoversTheExactPerCopyProduct() throws Exception {
        var f = normal(BigInteger.valueOf(73)); var provider = new Provider(f.host);
        assertEquals(1, execute(f, provider));
        double unit = appeng.crafting.execution.CraftingCpuHelper.calculatePatternPower(f.host.pattern.first());
        var expected = new BigDecimal(unit).multiply(new BigDecimal(73));
        assertTrue(new BigDecimal(f.network.paid).compareTo(expected) >= 0);
    }

    @Test void unsupportedJournalVersionPreservesOriginalRecoveryPayload() throws Exception {
        var f = normal(BigInteger.valueOf(3));
        var saved = new CompoundTag();
        saved.putInt("version", 9);
        saved.putString("futureOwnershipLedger", "keep exactly");
        f.dispatch.read(saved);
        assertTrue(f.dispatch.suspended());
        assertEquals(saved, f.dispatch.write(REGISTRIES));
    }

    @Test void outputMixinNamesAndDescriptorsMatchActualEco219Methods() throws Exception {
        var router = bytecode("cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingServiceMixin");
        assertTrue(router.methods.stream().anyMatch(method -> method.name.equals("neoecoae$insertExactIntoCpuForJob")
                && method.desc.equals("(Ljava/util/UUID;Lappeng/api/stacks/AEKey;Ljava/math/BigInteger;Lappeng/api/config/Actionable;)Ljava/math/BigInteger;")));
        assertTrue(router.methods.stream().anyMatch(method -> method.name.equals("neoecoae$insertIntoCpuForJob")
                && method.desc.equals("(Ljava/util/UUID;Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J")));
        var adapter = bytecode("cn/dancingsnow/neoecoae/compat/omnisequence/ECOOmniSequenceBatchAdapter");
        assertTrue(adapter.methods.stream().anyMatch(method -> method.name.equals("prepare")
                && method.desc.equals("(Lappeng/api/networking/crafting/ICraftingProvider;Lnet/minecraft/world/level/Level;Lcom/atir/molecularmanipulator/api/crafting/OmniBatchProbe;)Lcom/atir/molecularmanipulator/api/crafting/OmniBatchAdmission;")));
        // HEAD is explicitly permitted to inject into a method merged by a higher-priority mixin.
        var head = allocate(org.spongepowered.asm.mixin.injection.points.MethodHead.class);
        assertTrue(head.checkPriority(1000, 900));
    }

    private static org.objectweb.asm.tree.ClassNode bytecode(String name) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = EcoCpuDispatchTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream);
            new org.objectweb.asm.ClassReader(stream).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
        }
        return node;
    }

    /** Reuse the repository's real-ECO runtime fixture, which replaces mixin invokers only in tests. */
    private static void attachPlan(Host host, long... counts) throws Exception {
        attachPlan(host, List.of(), counts);
    }
    private static void attachPlan(Host host,
            List<cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation> allocations,
            long... counts) throws Exception {
        var tasks = new java.util.ArrayList<cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.TaskSpec>();
        var phases = new java.util.ArrayList<cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.PhaseSpec>();
        var schedule = new java.util.ArrayList<cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.ComponentExecutionPhase>();
        var totals = new LinkedHashMap<Integer, BigInteger>();
        long total = 0;
        var identity = cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.patternIdentityFor(host.pattern);
        for (int id = 0; id < counts.length; id++) {
            tasks.add(new cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.TaskSpec(id, identity,
                    host.pattern, cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.PatternRuntimeInfo.from(host.pattern),
                    counts[id], id, cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.TaskKind.DAG, allocations));
            phases.add(new cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.PhaseSpec(id, id,
                    cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.Type.DAG, List.of(id),
                    List.of(), id == 0 ? List.of() : List.of(id - 1), Map.of(), Map.of()));
            schedule.add(new cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.ComponentExecutionPhase(id,
                    cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.Type.DAG, java.util.Set.of(host.pattern), List.of()));
            totals.put(id, BigInteger.valueOf(counts[id]));
            total += counts[id];
        }
        var signature = new cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.Signature(OUTPUT, total,
                Map.of(identity, total), Map.of(MATERIAL, total), Map.of(), Map.of());
        var plan = new cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan(signature,
                cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode.PHASED_DAG,
                tasks, phases, new cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule(schedule));
        var factory = EcoOmniPlanExecutionTest.class.getDeclaredMethod("fixture",
                cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan.class, Map.class, Map.class, Map.class);
        factory.setAccessible(true);
        var fixture = factory.invoke(null, plan, totals, Map.of(), Map.of());
        var execution = fixture.getClass().getDeclaredMethod("execution"); execution.setAccessible(true);
        host.execution = (EcoOmniPlanExecution) execution.invoke(fixture);
    }

    private static int execute(Fixture f, Provider provider) throws Exception {
        return f.dispatch.execute(f.host, 1, service(provider), f.network.service, level());
    }
    private static Fixture normal(BigInteger count) {
        var host = new Host(new EcoMolecularTestPattern(CATALYST, ignored -> null), count);
        host.ecoOmni$storePhysical(CATALYST, count);
        host.ecoOmni$storePhysical(MATERIAL, count);
        return new Fixture(host, new EcoCpuDispatch(), new Network());
    }
    private static Fixture fixture(EcoMolecularTestPattern pattern, BigInteger count, long tools, long material) {
        var host = new Host(pattern, count);
        host.ecoOmni$storePhysical(pattern.getInputs()[0].getPossibleInputs()[0].what(), BigInteger.valueOf(tools));
        host.ecoOmni$storePhysical(MATERIAL, BigInteger.valueOf(material));
        return new Fixture(host, new EcoCpuDispatch(), new Network());
    }
    private record Fixture(Host host, EcoCpuDispatch dispatch, Network network) { }
    private enum Mode { ACCEPT, REJECT, EXCEPTION, INDETERMINATE, ASSERTION }

    private static final class Provider implements ICraftingProvider, ECOFastPathDispatchProvider {
        final Host host;
        Mode mode = Mode.ACCEPT;
        boolean stateful;
        boolean nativeExact;
        int pushes;
        int exactPrepares;
        UUID job;
        BigInteger exactCount;
        Provider(Host host) { this.host = host; }
        public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
            var physicalBefore = host.ecoOmni$physicalAmount(MATERIAL);
            ECOStatefulBatchCalculator calculator = null;
            if (stateful) {
                var model = EcoMolecularBatchModel.analyze(context);
                assertNotNull(model);
                var cache = new ECOCraftingFastPathCache(ECOCraftingFastPathCache.MIN_CACHE_SIZE);
                cache.putPositive(context.execution().key(), context.outputs(), context.containerItems(),
                        context.inputItems(), 0, model.calculatorModel());
                calculator = ECOStatefulBatchCalculator.create(cache.lookup(context.execution(), 0,
                        AE2PatternIntrospection.reloadGeneration()).recipe(), context.execution());
                assertNotNull(calculator);
            }
            return new Preparation(Long.MAX_VALUE, calculator, stateful, batch -> {
                job = context.craftingJobId();
                return dispatch(physicalBefore);
            });
        }
        public ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
            exactPrepares++;
            if (!nativeExact) return null;
            exactCount = requested;
            job = context.craftingJobId();
            var physicalBefore = host.ecoOmni$physicalAmount(MATERIAL);
            return new ExactPreparation(requested, () -> dispatch(physicalBefore));
        }
        private boolean dispatch(BigInteger physicalBefore) {
            pushes++;
            assertEquals(host.id, job);
            assertTrue(host.waiting.getOrDefault(OUTPUT, BigInteger.ZERO).signum() > 0, "Waiting must exist before submit");
            assertEquals(physicalBefore.subtract(host.last.inputs().getOrDefault(MATERIAL, BigInteger.ZERO)), host.ecoOmni$physicalAmount(MATERIAL),
                    "ECO must own every delivered material before dispatch");
            return switch (mode) {
                case ACCEPT -> true;
                case REJECT -> false;
                case EXCEPTION -> throw new IllegalStateException("guaranteed rejection");
                case INDETERMINATE -> throw new ECOIndeterminateBatchException("ownership may have committed", null);
                case ASSERTION -> throw new AssertionError("unexpected provider failure");
            };
        }
        public boolean isBusy() { return false; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(host.pattern); }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { throw new AssertionError("Legacy provider lane used"); }
    }

    private static final class Host implements EcoOmniPlanCpuAccess, EcoOmniPlanCpu {
        final UUID id = UUID.randomUUID();
        final EcoMolecularTestPattern pattern;
        final ListCraftingInventory window = new ListCraftingInventory(key -> {});
        final OmniExactInventory stock = new OmniExactInventory();
        final Map<AEKey, BigInteger> waiting = new LinkedHashMap<>();
        BigInteger remaining;
        EcoCpuDispatchReceipt last;
        EcoOmniPlanExecution execution;
        int legacyPhaseCommits;
        boolean exactOrder;
        boolean paused;
        boolean enabled = true;
        boolean failCommit;
        int commits;
        int refunds;
        Runnable onStage = () -> {};
        Host(EcoMolecularTestPattern pattern, BigInteger remaining) { this.pattern = pattern; this.remaining = remaining; }
        public boolean ecoDispatch$enabled() { return enabled; }
        public UUID ecoDispatch$jobId() { return id; }
        public boolean ecoDispatch$exactOrder() { return exactOrder; }
        public ListCraftingInventory ecoDispatch$inventory() { return window; }
        public ICraftingInventory ecoDispatch$physicalView() { return stock.wrap(window); }
        public BigInteger ecoDispatch$remaining(IPatternDetails pattern) { return remaining; }
        public Lease ecoDispatch$reserveExact(Map<AEKey, BigInteger> inputs) {
            for (var entry : inputs.entrySet()) if (ecoOmni$physicalAmount(entry.getKey()).compareTo(entry.getValue()) < 0) return null;
            inputs.forEach((key, amount) -> assertEquals(amount, stock.extract(window, key, amount, Actionable.MODULATE)));
            return new Lease() {
                boolean closed;
                public void commit() { closed = true; }
                public void refund() {
                    if (closed) return;
                    closed = true; refunds++;
                    inputs.forEach(Host.this::ecoOmni$storePhysical);
                }
            };
        }
        public Accounting ecoDispatch$stage(EcoCpuDispatchReceipt receipt) {
            onStage.run();
            last = receipt;
            receipt.waiting().forEach((key, amount) -> waiting.merge(key, amount, BigInteger::add));
            return new Accounting() {
                boolean settled;
                public void commit() {
                    if (settled) throw new AssertionError("Double settlement");
                    if (failCommit) throw new IllegalStateException("Cannot update task window");
                    settled = true; commits++; remaining = remaining.subtract(receipt.crafts());
                }
                public void rollback() {
                    if (settled) throw new AssertionError("Rollback after ownership transfer");
                    settled = true;
                    receipt.waiting().forEach((key, amount) -> {
                        var rest = waiting.get(key).subtract(amount);
                        if (rest.signum() == 0) waiting.remove(key); else waiting.put(key, rest);
                    });
                }
                public void retain() { settled = true; }
            };
        }
        public void ecoDispatch$markDirty() { }
        public void ecoDispatch$pause(EcoCpuDispatchReceipt receipt, String reason) { paused = true; }
        public BigInteger ecoDispatch$insertExact(UUID job, AEKey key, BigInteger amount, Actionable mode) {
            if (!id.equals(job)) return BigInteger.ZERO;
            var take = amount.min(waiting.getOrDefault(key, BigInteger.ZERO));
            if (mode == Actionable.MODULATE && take.signum() > 0) {
                var rest = waiting.get(key).subtract(take);
                if (rest.signum() == 0) waiting.remove(key); else waiting.put(key, rest);
                ecoOmni$storePhysical(key, take);
            }
            return take;
        }
        public boolean ecoOmni$managed() { return execution != null; }
        public EcoOmniPlanExecution ecoOmni$getExecution() { return execution; }
        public void ecoOmni$setExecution(EcoOmniPlanExecution value) { execution = value; }
        public void ecoOmni$accepted(IPatternDetails pattern, BigInteger count) {
            legacyPhaseCommits++;
            throw new AssertionError("Dispatcher must not commit a second phase token via the legacy CPU hook");
        }
        public OmniExactCraftingState ecoOmni$exactState() { return null; }
        public void ecoOmni$installExactState(OmniExactCraftingState state) { throw new UnsupportedOperationException(); }
        public Map<IPatternDetails, Object> ecoOmni$tasks() { return Map.of(pattern, new Object()); }
        public BigInteger ecoOmni$physicalAmount(AEKey key) { return stock.amount(window, key); }
        public void ecoOmni$storePhysical(AEKey key, BigInteger amount) { stock.insert(window, key, amount); }
        public void ecoOmni$queueEmitted(AEKey key, BigInteger amount) { throw new UnsupportedOperationException(); }
        public void ecoOmni$restoreRefundSnapshot(CompoundTag data, HolderLookup.Provider registries) { throw new UnsupportedOperationException(); }
    }

    private static final class Network {
        double paid;
        boolean partial;
        final IEnergyService service = (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IEnergyService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("extractAEPower")) {
                        double request = (double) args[0];
                        if (args[1] == Actionable.MODULATE) {
                            double actual = partial ? request / 2 : request;
                            paid += actual;
                            return actual;
                        }
                        return request;
                    }
                    if (method.getReturnType() == double.class) return 0D;
                    if (method.getReturnType() == boolean.class) return true;
                    return null;
                });
    }
    private static Service service(Provider provider) throws Exception {
        var service = allocate(Service.class); service.provider = provider; return service;
    }
    private static final class Service extends CraftingService {
        Provider provider;
        Service() { super(null, null, null); }
        @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) { return List.of(provider); }
    }
    private static final class OutputLogic extends CraftingCpuLogic implements EcoCpuDispatchHost, EcoCpuDispatchAccess {
        Host host;
        EcoCpuDispatch dispatch;
        OutputLogic() { super(null); }
        public boolean ecoDispatch$enabled() { return true; }
        public UUID ecoDispatch$jobId() { return host.id; }
        public EcoCpuDispatch ecoDispatch$dispatcher() { return dispatch; }
        public BigInteger ecoDispatch$insertExact(UUID id, AEKey key, BigInteger amount, Actionable mode) {
            return host.ecoDispatch$insertExact(id, key, amount, mode);
        }
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}
