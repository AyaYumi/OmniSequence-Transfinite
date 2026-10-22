package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.*;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

class MatterPatternBufferCancellationTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(
                    List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        try { AEKeyTypesInternal.getRegistry(); }
        catch (IllegalStateException uninitialized) {
            var keyTypes = new net.minecraft.core.MappedRegistry<AEKeyType>(
                    AEKeyType.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
            AEKeyTypesInternal.setRegistry(keyTypes);
            AEKeyTypesInternal.register(AEKeyType.items());
            AEKeyTypesInternal.register(AEKeyType.fluids());
            keyTypes.freeze();
        }
    }
    private static final AEItemKey PATTERN = AEItemKey.of(Items.PAPER);
    private static final AEKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey COPPER = AEItemKey.of(Items.COPPER_INGOT);
    private static final AEKey WATER = AEFluidKey.of(Fluids.WATER);
    private static final AEKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final ResourceLocation RECIPE = ResourceLocation.parse("test:recipe");
    private static final UUID FIRST = UUID.randomUUID(), SECOND = UUID.randomUUID();
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @BeforeAll static void bindMissingContentMarkerForVanillaSerialization() throws Exception {
        // JUnit does not run AE2's deferred item registration. Bind only its codec sentinel to an
        // existing vanilla item; the real GenericStack/item/fluid codecs still perform the round trip.
        var marker = appeng.core.definitions.AEItems.MISSING_CONTENT;
        Field item = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        item.setAccessible(true);
        item.set(marker, net.neoforged.neoforge.registries.DeferredItem.createItem(
                ResourceLocation.withDefaultNamespace("barrier")));
    }

    private static MatterPatternBuffer buffer() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) ((Unsafe) field.get(null))
                .allocateInstance(MatterFabricationPatternAssemblyBlockEntity.class);
        return new MatterPatternBuffer(assembly);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static Object2LongLinkedOpenHashMap<AEKey> amounts(MatterPatternBuffer buffer, String name) throws Exception {
        return (Object2LongLinkedOpenHashMap<AEKey>) field(buffer, name);
    }

    @SuppressWarnings("unchecked")
    private static void queue(MatterPatternBuffer buffer, UUID id, Map<AEKey, Long> inputs) throws Exception {
        ((List<MatterPatternBuffer.QueuedWork>) field(buffer, "queued"))
                .add(new MatterPatternBuffer.QueuedWork(PATTERN, RECIPE, 10, inputs, id));
        inputs.forEach(amounts(buffer, "inputs")::addTo);
    }

    private static Map<AEKey, Long> contents(MatterPatternBuffer buffer, boolean output) {
        var result = new HashMap<AEKey, Long>();
        for (var stack : buffer.contents(output)) result.put(stack.what(), stack.amount());
        return result;
    }

    private static void active(MatterPatternBuffer buffer, UUID id, Map<AEKey, Long> inputs) throws Exception {
        var active = (MatterFabricationBatch) field(buffer, "active");
        set(active, "recipeId", RECIPE);
        set(active, "crafts", 10L);
        set(active, "duration", 100);
        set(active, "progress", 45);
        @SuppressWarnings("unchecked") var stored = (Map<AEKey, Long>) field(active, "inputs");
        stored.putAll(inputs);
        @SuppressWarnings("unchecked") var outputs = (Map<AEKey, Long>) field(active, "outputsPerCraft");
        outputs.put(OUTPUT, 1L);
        set(buffer, "activeCraftingId", id);
    }

    @Test void samePatternFromTwoTasksMustNotMerge() {
        var work = new MatterPatternBuffer.QueuedWork(PATTERN, RECIPE, 10, Map.of(IRON, 100L), FIRST);
        assertTrue(work.canMerge(PATTERN, RECIPE, FIRST, 2));
        assertFalse(work.canMerge(PATTERN, RECIPE, SECOND, 2));
        assertFalse(work.canMerge(PATTERN, RECIPE, null, 2));
        assertFalse(work.canMerge(PATTERN, RECIPE, FIRST, Long.MAX_VALUE));
    }

    @Test void cancellationRefundsOnlyItsQueuedMaterialsIncludingSharedKeysAndFluids() throws Exception {
        var buffer = buffer();
        queue(buffer, FIRST, Map.of(IRON, 100L, WATER, 2000L));
        queue(buffer, SECOND, Map.of(IRON, 300L, COPPER, 60L, WATER, 7000L));
        queue(buffer, null, Map.of(IRON, 5L)); // Legacy unowned materials must not be guessed.
        assertTrue(buffer.refundCanceledInputs(FIRST::equals));
        assertEquals(Map.of(IRON, 100L, WATER, 2000L), amounts(buffer, "refunds"));
        assertEquals(Map.of(IRON, 305L, COPPER, 60L, WATER, 7000L), amounts(buffer, "inputs"));
        assertEquals(2, buffer.queuedPatterns());
        assertFalse(buffer.refundCanceledInputs(FIRST::equals), "Repeated cancel must not duplicate refunds");
        assertEquals(Map.of(IRON, 405L, COPPER, 60L, WATER, 9000L), contents(buffer, false));
    }

    @Test void cancellationStopsItsActiveBatchButLeavesOtherTaskAndFinishedOutputsIntact() throws Exception {
        var buffer = buffer();
        queue(buffer, FIRST, Map.of(IRON, 100L));
        queue(buffer, SECOND, Map.of(IRON, 300L));
        active(buffer, FIRST, Map.of(IRON, 40L, WATER, 1000L));
        amounts(buffer, "outputs").put(OUTPUT, 12L);
        assertTrue(buffer.refundCanceledInputs(FIRST::equals));
        assertEquals(Map.of(IRON, 140L, WATER, 1000L), amounts(buffer, "refunds"));
        assertEquals(Map.of(IRON, 300L), amounts(buffer, "inputs"));
        assertFalse(buffer.isProcessing());
        assertEquals(Map.of(OUTPUT, 12L), contents(buffer, true));
        assertNull(field(buffer, "activeCraftingId"));
    }

    @Test void cancelingAnotherTaskDoesNotResetActiveProgress() throws Exception {
        var buffer = buffer();
        queue(buffer, FIRST, Map.of(IRON, 100L));
        active(buffer, SECOND, Map.of(IRON, 300L));
        buffer.refundCanceledInputs(FIRST::equals);
        assertTrue(buffer.isProcessing());
        assertEquals(45, field(field(buffer, "active"), "progress"));
        assertEquals(Map.of(IRON, 300L), ((MatterFabricationBatch) field(buffer, "active")).storedInputs());
    }

    @Test void canceledCompletedBatchKeepsOutputDebtAndDoesNotRecreateConsumedInputs() throws Exception {
        var buffer = buffer();
        var active = (MatterFabricationBatch) field(buffer, "active");
        @SuppressWarnings("unchecked") var debt = (Map<AEKey, Long>) field(active, "outputDebt");
        debt.put(OUTPUT, 25L);
        set(buffer, "activeCraftingId", FIRST);
        buffer.refundCanceledInputs(FIRST::equals);
        assertEquals(Map.of(OUTPUT, 25L), contents(buffer, true));
        assertTrue(amounts(buffer, "refunds").isEmpty());
    }

    @Test void partialOrOfflineNetworkReturnRetainsOnlyUndeliveredRefunds() throws Exception {
        var buffer = buffer();
        queue(buffer, FIRST, Map.of(IRON, 100L));
        queue(buffer, SECOND, Map.of(IRON, 300L));
        buffer.refundCanceledInputs(FIRST::equals);
        var refunds = amounts(buffer, "refunds");
        var transfer = new AEKeyTransferScheduler();
        transfer.flush(refunds, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> 0);
        assertEquals(100, refunds.getLong(IRON));
        transfer.flush(refunds, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> Math.min(amount, 40));
        assertEquals(60, refunds.getLong(IRON));
        assertEquals(300, amounts(buffer, "inputs").getLong(IRON));
        transfer.flush(refunds, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> amount);
        assertTrue(refunds.isEmpty());
        assertEquals(Map.of(IRON, 300L), contents(buffer, false));
    }

    @Test void saveReloadKeepsSeparateTaskOwnershipForQueuedAndActiveInputs() throws Exception {
        var buffer = buffer();
        queue(buffer, FIRST, Map.of(IRON, 100L));
        queue(buffer, SECOND, Map.of(IRON, 300L));
        active(buffer, FIRST, Map.of(WATER, 1000L));
        var saved = new CompoundTag();
        buffer.save(saved, REGISTRIES);
        var loaded = buffer();
        loaded.load(saved, REGISTRIES);
        assertFalse(loaded.isUnavailable());
        assertTrue(loaded.refundCanceledInputs(FIRST::equals));
        assertEquals(Map.of(IRON, 100L, WATER, 1000L), amounts(loaded, "refunds"));
        assertEquals(Map.of(IRON, 300L), amounts(loaded, "inputs"));
        assertFalse(loaded.isProcessing());
    }

    @Test void legacyBufferLoadsWithoutInventingTaskOwnership() throws Exception {
        var buffer = buffer();
        queue(buffer, null, Map.of(IRON, 100L));
        var saved = new CompoundTag();
        buffer.save(saved, REGISTRIES);
        saved.getCompound("fabrication_pattern_buffer").putInt("version", 1);
        var loaded = buffer();
        loaded.load(saved, REGISTRIES);
        assertFalse(loaded.isUnavailable());
        assertFalse(loaded.refundCanceledInputs(id -> true));
        assertEquals(Map.of(IRON, 100L), contents(loaded, false));
    }
}
