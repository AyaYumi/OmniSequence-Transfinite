package com.atir.molecularmanipulator.integration.eco;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.MATERIAL;
import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.OUTPUT;
import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.RETURNED;
import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.counter;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimization;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationRegistry;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath.state.ECOStatefulBatchCalculator;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchHost;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchLedger;
import com.atir.molecularmanipulator.mixin.EcoCpuDispatchMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

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
                    net.neoforged.neoforge.registries.DeferredItem.createItem(
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace("barrier")));
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final AEItemKey CATALYST = AEItemKey.of(Items.STICK);
    private static final BigInteger SIX = BigInteger.valueOf(6);
    private final UUID job = UUID.randomUUID();

    @Test void linearAdmissionUsesTheAllocatedPrototypeAndSubmitsOneScaledVector() {
        var provider = new FastProvider();
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> RETURNED);
        var request = request(provider, pattern, BigInteger.TEN, pattern.remainders());
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertEquals(SIX, admission.maximumCrafts());
            assertFalse(admission.acceptsPartial());
            assertEquals(job, provider.context.craftingJobId());
            assertSame(pattern, provider.context.pattern());
            assertEquals(List.of(List.of(new GenericStack(CATALYST, 1)), List.of(new GenericStack(MATERIAL, 1))),
                    provider.context.inputs());
            assertEquals(0, provider.calls);
            assertEquals(BigInteger.valueOf(3), admission.submit(BigInteger.valueOf(3)));
            assertEquals(3, provider.batch.craftCount());
            assertEquals(Set.of(new GenericStack(CATALYST, 3), new GenericStack(MATERIAL, 3)),
                    Set.copyOf(provider.batch.inputTotal()));
            assertEquals(List.of(new GenericStack(OUTPUT, 3)), provider.batch.outputTotal());
            assertEquals(List.of(new GenericStack(RETURNED, 3)), provider.batch.remainingTotal());
            assertEquals(1, request.unitInputs()[0].get(CATALYST));
            assertEquals(1, request.unitRemainders().get(RETURNED));
            assertThrows(IllegalStateException.class, () -> admission.submit(BigInteger.ONE));
            assertEquals(1, provider.calls);
        }
    }

    @Test void inputlessGeneratorCommitsItsAdvertisedExactCapacity() {
        var pattern = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return OUTPUT; }
            @Override public IInput[] getInputs() { return new IInput[0]; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
        };
        var provider = new FastProvider();
        provider.exact = true;
        var count = BigInteger.ONE.shiftLeft(80);
        var request = new OmniCapacityRequest(job, provider, pattern, new KeyCounter[0],
                counter(OUTPUT, 1), new KeyCounter(), count, null);
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertEquals(count, admission.maximumCrafts());
            assertEquals(count, admission.submit(count));
        }
        assertTrue(provider.context.inputItems().isEmpty());
        assertEquals(1, provider.calls);
    }

    @Test void fixedExactPreparationUsesTheFullBigIntegerCountAndNeedsNoLongLease() {
        var count = BigInteger.ONE.shiftLeft(90);
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> null);
        var provider = new FastProvider();
        provider.exact = true;
        try (var admission = EcoCpuDispatch.prepare(request(provider, pattern, count, new KeyCounter()))) {
            assertNotNull(admission);
            assertEquals(count, admission.maximumCrafts());
            assertEquals(job, provider.context.craftingJobId());
            assertEquals(count, admission.submit(count));
            assertEquals(1, provider.calls);
            assertNull(provider.batch);
        }
        try (var admission = EcoCpuDispatch.prepare(request(provider, pattern, count, new KeyCounter()))) {
            assertNotNull(admission);
            assertEquals(BigInteger.ZERO, admission.submit(count.subtract(BigInteger.ONE)));
            assertEquals(1, provider.calls, "A fixed provider callback cannot transfer fewer copies than it prepared");
        }
    }

    @Test void normalBusRecipeUsesItsOfferEvenWhenNoStatefulCalculatorIsNeeded() {
        var provider = new FastProvider();
        provider.stateful = true;
        provider.capacity = 40_000;
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> RETURNED);
        var count = BigInteger.valueOf(provider.capacity);
        try (var admission = EcoCpuDispatch.prepare(request(provider, pattern, count, pattern.remainders()))) {
            assertNotNull(admission);
            assertEquals(count, admission.maximumCrafts());
            assertEquals(count, admission.submit(count));
        }
        assertEquals(List.of(new GenericStack(RETURNED, provider.capacity)), provider.batch.remainingTotal());
        assertEquals(1, provider.calls);
    }

    @Test void exactInputAwareOfferAcceptsTwoLongMaxMaterialsInOneLongBatch() {
        var provider = new FastProvider();
        provider.capacity = Long.MAX_VALUE;
        var count = BigInteger.valueOf(Long.MAX_VALUE);
        // The same key is drawn from two different slots in every craft.
        var pattern = new EcoMolecularTestPattern(MATERIAL, ignored -> RETURNED);
        var request = request(provider, pattern, count, pattern.remainders());
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE / 2), admission.maximumCrafts());
        }
        provider.exactInputs = true;
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertEquals(count, admission.maximumCrafts());
            assertEquals(count, admission.submit(count));
        }
        assertEquals(Map.of(MATERIAL, count.multiply(BigInteger.TWO)), provider.batch.exactInputTotal());
        assertEquals(List.of(new GenericStack(OUTPUT, Long.MAX_VALUE)), provider.batch.outputTotal());
        assertEquals(List.of(new GenericStack(RETURNED, Long.MAX_VALUE)), provider.batch.remainingTotal());
        assertEquals(1, request.unitInputs()[0].get(MATERIAL));
        assertEquals(1, request.unitInputs()[1].get(MATERIAL));
        assertEquals(1, provider.calls);
    }

    @Test void aContainerAndOutputOfTheSameKeyShareOneNativeLongCounter() {
        var provider = new FastProvider();
        provider.capacity = Long.MAX_VALUE;
        provider.exactInputs = true;
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> OUTPUT);
        var offered = BigInteger.valueOf(Long.MAX_VALUE);
        var expected = offered.divide(BigInteger.TWO);
        try (var admission = EcoCpuDispatch.prepare(request(provider, pattern, offered, pattern.remainders()))) {
            assertNotNull(admission);
            assertEquals(expected, admission.maximumCrafts());
            assertEquals(expected, admission.submit(expected));
        }
        var stack = new GenericStack(OUTPUT, expected.longValueExact());
        assertEquals(List.of(stack), provider.batch.outputTotal());
        assertEquals(List.of(stack), provider.batch.remainingTotal());
        assertEquals(1, provider.calls);
    }

    @Test void calculatedOfferMustUseTheSameInputsAndReturnsAsTheOmniReservation() throws Exception {
        var registration = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "eco_cpu_linear_offer_test");
        ECOExecutionOptimizationRegistry.register(registration, 1000, new ECOExecutionOptimizationAdapter() {
            public boolean supports(IPatternDetails pattern) { return pattern instanceof EcoMolecularTestPattern; }
            public ECOExecutionOptimization describe(IPatternDetails pattern) { return ECOExecutionOptimization.VERIFIED_ASSEMBLER; }
        });
        try {
            var level = EcoMolecularTestPattern.level();
            var count = BigInteger.valueOf(25_000);
            for (var pattern : List.of(new EcoMolecularTestPattern(EcoMolecularTestPattern.CONTAINER, ignored -> RETURNED),
                    new EcoMolecularTestPattern(CATALYST, key -> key))) {
                var provider = new FastProvider();
                provider.capacity = count.longValueExact();
                provider.stateful = true;
                var context = ECOBatchDispatchContext.create(pattern, pattern.first(), counter(OUTPUT, 1),
                        pattern.remainders(), level, job);
                provider.calculator = ECOFastPathFacade.prepareStatefulCalculator(context,
                        EcoMolecularBatchModel.analyze(context).calculatorModel());
                assertNotNull(provider.calculator);
                var request = new OmniCapacityRequest(job, provider, pattern, pattern.first(), counter(OUTPUT, 1),
                        pattern.remainders(), count, level);
                try (var admission = EcoCpuDispatch.prepare(request)) {
                    if (pattern.remainders().get(CATALYST) != 0) {
                        assertNull(admission, "The calculator reserved one physical catalyst for the entire batch");
                        assertEquals(0, provider.calls);
                    } else {
                        assertNotNull(admission);
                        assertEquals(count, admission.maximumCrafts());
                        assertEquals(count, admission.submit(count));
                        assertEquals(List.of(new GenericStack(RETURNED, 25_000)), provider.batch.remainingTotal());
                        assertEquals(1, provider.calls);
                    }
                }
            }
        } finally {
            ECOExecutionOptimizationRegistry.unregister(registration);
        }
    }

    @Test void statefulPreparationCannotReplaceThePhysicalPrototypeOrItsReturns() {
        var provider = new FastProvider();
        provider.stateful = true;
        var pattern = new EcoMolecularTestPattern(CATALYST, key -> key);
        assertNull(EcoCpuDispatch.prepare(request(provider, pattern, BigInteger.TEN, pattern.remainders())));
        assertEquals(0, provider.calls);
    }

    @Test void rejectionAndUnknownOwnershipNeverRunASecondProviderCallback() {
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> null);
        var provider = new FastProvider();
        provider.accept = false;
        var request = request(provider, pattern, BigInteger.TEN, new KeyCounter());
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertEquals(BigInteger.ZERO, admission.submit(SIX));
            assertThrows(IllegalStateException.class, () -> admission.submit(SIX));
            assertEquals(1, provider.calls);
        }
        provider.uncertain = new IllegalStateException("Provider may own materials");
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertSame(provider.uncertain, assertThrows(IllegalStateException.class, () -> admission.submit(SIX)));
            assertThrows(IllegalStateException.class, () -> admission.submit(SIX));
            assertEquals(2, provider.calls);
        }
    }

    @Test void parallelProviderExposesKnownFullAndUsesDistinctScaledHoldersWhenReady() {
        var provider = new ParallelProvider();
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> null);
        var request = request(provider, pattern, BigInteger.TEN, new KeyCounter());
        try (var full = EcoCpuDispatch.prepare(request)) {
            assertNotNull(full);
            assertEquals(BigInteger.ZERO, full.maximumCrafts());
            assertEquals(0, provider.calls);
        }
        provider.slots = 2;
        try (var admission = EcoCpuDispatch.prepare(request)) {
            assertNotNull(admission);
            assertEquals(BigInteger.TWO, admission.maximumCrafts());
            assertEquals(BigInteger.TWO, admission.submit(BigInteger.TWO));
        }
        assertEquals(job, provider.job);
        assertEquals(1, request.unitInputs()[0].get(CATALYST));
        assertTrue(EcoCpuDispatch.pushSingle(provider, pattern, pattern.first(), job, () -> fail("Vanilla push")));
        assertEquals(2, provider.calls);
        assertEquals(job, provider.job);
    }

    @Test void registeredSingleUsesTheJobUuidAndRejectsWithoutFallingThrough() {
        var provider = new RegisteredProvider();
        var pattern = new EcoMolecularTestPattern(CATALYST, ignored -> null);
        ECOParallelCraftingProviders.registerFastPath(RegisteredProvider.class, value -> context -> null,
                (value, sentPattern, inputs, id) -> {
                    assertSame(pattern, sentPattern);
                    assertEquals(1, inputs[1].get(MATERIAL));
                    value.job = id;
                    return false;
                });
        assertFalse(EcoCpuDispatch.pushSingle(provider, pattern, pattern.first(), job, () -> fail("Vanilla push")));
        assertEquals(job, provider.job);
        assertTrue(EcoCpuDispatch.pushSingle(new PlainProvider(), pattern, pattern.first(), job, () -> true));
    }

    @Test void prepaidLegacyEnergyMigratesOnceAndUncertainMaterialIntentRemainsOpaque() throws Exception {
        var old = legacy(1, "12.375");
        var intent = new CompoundTag();
        intent.putUUID("job", job);
        intent.putString("unknownOwnershipVector", "preserve me");
        old.put("intent", intent);
        var saved = new CompoundTag();
        saved.put(EcoCpuDispatchJournal.NBT_KEY, old);
        var cpu = new LegacyCpu();
        cpu.load(saved);
        assertTrue(cpu.ecoOmni$dispatchPaused());
        var migrated = cpu.save();
        var shared = migrated.getCompound(OmniCpuDispatchLedger.NBT_KEY);
        assertEquals("12.375", shared.getString("dispatchEnergy"));
        var held = migrated.getCompound(EcoCpuDispatchJournal.NBT_KEY);
        assertEquals(intent, held.getCompound("intent"));
        assertFalse(held.contains("credit"));
        assertEquals("12.375", old.getString("credit"));
        cpu.load(migrated);
        assertEquals(migrated, cpu.save());
        cpu.load(new CompoundTag());
        assertFalse(cpu.ecoOmni$dispatchPaused());
    }

    @Test void badLegacyBalancesAndUnknownVersionsKeepTheirRecoveryPayloads() throws Exception {
        var cpu = new LegacyCpu();
        for (var value : List.of(legacy(1, "invalid"), legacy(1, "-2"), legacy(9, "1.5"))) {
            var saved = new CompoundTag();
            saved.put(EcoCpuDispatchJournal.NBT_KEY, value);
            cpu.load(saved);
            assertTrue(cpu.ecoOmni$dispatchPaused());
            assertEquals(value, cpu.save().get(EcoCpuDispatchJournal.NBT_KEY));
        }
    }

    @Test void exactReturnsUseTheOwningUuidAndSimulationLeavesOwnershipUntouched() throws Exception {
        var count = BigInteger.ONE.shiftLeft(90);
        var logic = allocate(OutputLogic.class);
        logic.job = job;
        logic.received = BigInteger.ZERO;
        var cluster = allocate(CraftingCPUCluster.class);
        var field = CraftingCPUCluster.class.getDeclaredField("craftingLogic");
        field.setAccessible(true);
        field.set(cluster, logic);
        var cpus = List.<ICraftingCPU>of(cluster);
        assertNull(EcoCpuDispatchOutput.insert(cpus, UUID.randomUUID(), OUTPUT, count, Actionable.MODULATE));
        assertEquals(count, EcoCpuDispatchOutput.insert(cpus, job, OUTPUT, count, Actionable.SIMULATE));
        assertEquals(BigInteger.ZERO, logic.received);
        assertEquals(count, EcoCpuDispatchOutput.insert(cpus, job, OUTPUT, count, Actionable.MODULATE));
        assertEquals(count, logic.received);
        logic.paused = true;
        assertEquals(BigInteger.ZERO, EcoCpuDispatchOutput.insert(cpus, job, OUTPUT, BigInteger.ONE, Actionable.MODULATE));
        assertEquals(count, logic.received);
    }

    private OmniCapacityRequest request(ICraftingProvider provider, EcoMolecularTestPattern pattern,
            BigInteger maximum, KeyCounter remainders) {
        return new OmniCapacityRequest(job, provider, pattern, pattern.first(), counter(OUTPUT, 1), remainders, maximum, null);
    }

    private static CompoundTag legacy(int version, String credit) {
        var result = new CompoundTag();
        result.putInt("version", version);
        result.putString("credit", credit);
        return result;
    }

    private static class PlainProvider implements ICraftingProvider {
        public boolean isBusy() { return false; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return fail("Unexpected vanilla push"); }
    }

    private static final class RegisteredProvider extends PlainProvider implements ECOJobAwareCraftingProvider {
        UUID job;
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, UUID job) {
            return fail("Registered provider owns this dispatch");
        }
    }

    private static final class FastProvider extends PlainProvider implements ECOFastPathDispatchProvider {
        ECOBatchDispatchContext context;
        Batch batch;
        RuntimeException uncertain;
        int calls;
        boolean exact;
        boolean stateful;
        long capacity = 6;
        boolean exactInputs;
        ECOStatefulBatchCalculator calculator;
        boolean accept = true;

        public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
            this.context = context;
            return new Preparation(capacity, calculator, stateful, batch -> {
                calls++;
                this.batch = batch;
                if (uncertain != null) throw uncertain;
                return accept;
            }, exactInputs);
        }

        public ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger maximum) {
            if (!exact) return null;
            this.context = context;
            return new ExactPreparation(maximum, () -> {
                calls++;
                return accept;
            });
        }
    }

    private static final class ParallelProvider extends PlainProvider implements ECOParallelCraftingProvider {
        UUID job;
        int slots;
        int calls;

        public int eco$getAvailableParallelSlots() { return slots; }
        public boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputs, long count, UUID job) {
            calls++;
            this.job = job;
            assertEquals(count, inputs[0].get(CATALYST));
            assertEquals(count, inputs[1].get(MATERIAL));
            inputs[0].reset();
            return true;
        }
    }

    private static final class LegacyCpu extends EcoCpuDispatchMixin implements OmniCpuDispatchHost {
        private final OmniCpuDispatchLedger ledger = new OmniCpuDispatchLedger();

        public OmniCpuDispatchLedger.Energy omniDispatch$dispatchEnergy() { return ledger.energy(); }

        void load(CompoundTag tag) throws Exception {
            nbtHandler("readEcoDispatch").invoke(this, tag, REGISTRIES, (Operation<Void>) args -> {
                ledger.read(tag.getCompound(OmniCpuDispatchLedger.NBT_KEY), REGISTRIES);
                return null;
            });
        }

        CompoundTag save() throws Exception {
            var tag = new CompoundTag();
            nbtHandler("writeEcoDispatch").invoke(this, tag, REGISTRIES, (Operation<Void>) args -> {
                tag.put(OmniCpuDispatchLedger.NBT_KEY, ledger.write(REGISTRIES));
                return null;
            });
            return tag;
        }

        private static Method nbtHandler(String name) throws Exception {
            var method = EcoCpuDispatchMixin.class.getDeclaredMethod("molecularmanipulator$" + name,
                    CompoundTag.class, HolderLookup.Provider.class, Operation.class);
            method.setAccessible(true);
            return method;
        }
    }

    private static final class OutputLogic extends CraftingCpuLogic implements OmniCpuDispatchHost, EcoCpuDispatchAccess {
        UUID job;
        BigInteger received;
        boolean paused;

        OutputLogic() { super(null); }
        public boolean omniDispatch$enabled() { return true; }
        public UUID omniDispatch$jobId() { return job; }
        public boolean ecoOmni$dispatchPaused() { return paused; }

        public BigInteger omniDispatch$insertExact(UUID id, AEKey key, BigInteger amount, Actionable mode) {
            assertEquals(job, id);
            assertEquals(OUTPUT, key);
            if (mode == Actionable.MODULATE) received = received.add(amount);
            return amount;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}
