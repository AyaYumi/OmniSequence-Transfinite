package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.DamageGroup;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputMode;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputPlan;
import com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

class MolecularReusableBatchOwnershipTest {
    static { EcoMolecularTestPattern.bootstrap(); }
    private static HolderLookup.Provider testRegistries;
    @Test
    void realBatcherAcceptsFullReusableOwnershipAndJobRoundTrips() throws Exception {
        var id = UUID.randomUUID();
        var tool = tool(2, 5, UUID.randomUUID());
        var pattern = new EcoMolecularTestPattern(tool, key -> worn(key, 6));
        var inputs = pattern.supplied(7);
        var batcher = new MolecularCraftingBatcher();
        var job = batcher.prepareReusable(pattern, inputs, level(), id, pattern.plan(7));
        assertNotNull(job);
        assertEquals(2, inputs[0].get(tool), "Preparation owns nothing");
        assertEquals(7, inputs[1].get(MATERIAL));
        assertEquals(id, job.craftingId());
        assertEquals(7, job.projectedPrimaryOutputs().getLong(OUTPUT));
        var registries = registries();
        job.advance(5);
        var checkpoint = job.cancellationRefunds();
        assertEquals(2, checkpoint.getLong(MATERIAL));
        var partial = worn(tool, 6);
        assertEquals(1, checkpoint.getLong(partial));
        var restored = MolecularReusableBatchJob.readFromTag(job.writeToTag(registries), registries);
        assertNotNull(restored);
        assertEquals(id, restored.craftingId());
        assertEquals(5, restored.completedCrafts());
        assertEquals(checkpoint, restored.cancellationRefunds());
        assertEquals(job.projectedFinalRemainders(), restored.projectedFinalRemainders());
        restored.advance(2);
        assertTrue(restored.isComplete());
        var terminal = worn(worn(worn(tool, 6), 6), 6);
        assertEquals(1, restored.completedRemainders().getLong(terminal));
    }

    @Test
    void longWearQuoteAndReloadPreserveThePhysicalToolAtCancellation() throws Exception {
        int crafts = 4_097;
        int durability = 6_000;
        var identity = UUID.randomUUID();
        var tool = tool(0, durability, identity);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var pattern = new EcoMolecularTestPattern(tool, key -> {
            calls.incrementAndGet();
            return worn(key, durability);
        });
        var inventory = new appeng.crafting.inv.ListCraftingInventory(ignored -> {});
        inventory.list.add(MATERIAL, crafts - 1);
        var outputs = counter(OUTPUT, 1);
        var returns = pattern.remainders();
        var quote = MolecularBatchCraftingExtractor.previewReusable(pattern, inventory, cost -> true,
                level(), pattern.first(), outputs, returns, crafts);
        assertNotNull(quote);
        assertEquals(crafts, quote.craftCount());
        assertTrue(calls.get() < 32, "A capacity quote must not evaluate thousands of intermediate states");
        assertEquals(1, quote.inputs()[0].get(tool));
        assertEquals(crafts, quote.inputs()[1].get(MATERIAL));
        assertEquals(crafts - 1, inventory.list.get(MATERIAL));
        var finalTool = tool(crafts, durability, identity);
        assertEquals(1, returns.get(finalTool));
        assertEquals(1, returns.size(), "Only the final tool state belongs in the batch receipt");

        var primary = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEKey>();
        primary.put(OUTPUT, 1);
        var job = MolecularReusableBatchJob.create(identity, pattern.getDefinition(), quote.reusablePlan(), quote.inputs(), primary);
        assertNotNull(job);
        int completed = 3_100;
        job.advance(completed);
        var regs = registries();
        var saved = job.writeToTag(regs);
        var currentTool = tool(completed, durability, identity);
        var restored = MolecularReusableBatchJob.readFromTag(saved, regs);
        assertNotNull(restored);
        assertEquals(1, restored.cancellationRefunds().getLong(currentTool));
        assertEquals(crafts - completed, restored.cancellationRefunds().getLong(MATERIAL));
        assertEquals(1, restored.projectedFinalRemainders().getLong(finalTool));

        var malformed = saved.copy();
        long impossibleUses = durability + 2L;
        malformed.putLong("total_crafts", impossibleUses);
        var inputs = malformed.getList("inputs", 10);
        var group = inputs.getCompound(0).getList("damage_groups", 10).getCompound(0);
        group.putLong("uses_per_tool", impossibleUses);
        group.remove("final_key");
        inputs.getCompound(1).putLong("remaining_amount", impossibleUses - completed);
        assertNull(MolecularReusableBatchJob.readFromTag(malformed, regs));
    }

    @Test
    void toolPoolFindsStockAfterHundredsOfUnavailableVariants() throws Exception {
        var identity = new UUID(0, 1);
        var lastUse = tool(4, 5, identity);
        var fresh = tool(0, 5, identity);
        var pattern = new EcoMolecularTestPattern(lastUse, key -> worn(key, 5));
        var variants = new java.util.ArrayList<AEKey>();
        for (int i = 0; i < 300; i++) variants.add(tool(0, 5, new UUID(1, i)));
        variants.add(fresh);
        var inventory = new ICraftingInventory() {
            @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) { return variants; }
            @Override public long extract(AEKey key, long amount, Actionable mode) {
                assertEquals(Actionable.SIMULATE, mode);
                return Math.min(amount, fresh.equals(key) ? 20 : MATERIAL.equals(key) ? 100 : 0);
            }
            @Override public void insert(AEKey key, long amount, Actionable mode) { fail("Preview wrote to CPU inventory"); }
        };
        var outputs = counter(OUTPUT, 1);
        var returns = pattern.remainders();
        var quote = MolecularBatchCraftingExtractor.previewReusable(pattern, inventory, cost -> cost <= 122,
                level(), pattern.first(), outputs, returns, 101);
        assertNotNull(quote);
        assertEquals(101, quote.craftCount());
        assertEquals(1, quote.inputs()[0].get(lastUse));
        assertEquals(20, quote.inputs()[0].get(fresh));
        assertEquals(101, outputs.get(OUTPUT));
        assertTrue(returns.isEmpty());
    }

    @Test
    void realBatcherRejectsMismatchWithoutConsumingAnyMaterials() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
        var inputs = pattern.supplied(10);
        inputs[1].add(MATERIAL, 1);
        var batcher = new MolecularCraftingBatcher();
        assertNull(batcher.prepareReusable(pattern, inputs, level(), UUID.randomUUID(), pattern.plan(10)));
        assertEquals(11, inputs[1].get(MATERIAL));
        assertEquals(1, inputs[0].get(catalyst));
    }

    @Test
    void linearContainerBatchRequiresExactContractAndPreservesInputsOnFailure() throws Exception {
        var pattern = new EcoMolecularTestPattern(CONTAINER, ignored -> RETURNED);
        var inputs = pattern.supplied(500);
        var batcher = new MolecularCraftingBatcher();
        assertFalse(batcher.prepareSelected(pattern, inputs, level(), 500, pattern.first(), 500));
        assertEquals(500, inputs[0].get(CONTAINER));
        try (var scope = pattern.remainderScope(inputs, 500)) {
            assertTrue(batcher.prepareSelected(pattern, inputs, level(), 500, pattern.first(), 500));
        }
        assertEquals(500, batcher.getPrimaryOutputAmounts().getLong(OUTPUT));
        assertEquals(500, batcher.getRemainderOutputAmounts().getLong(RETURNED));
        assertEquals(500, inputs[0].get(CONTAINER));
        try (var scope = pattern.remainderScope(inputs, 499)) {
            assertFalse(batcher.prepareSelected(pattern, inputs, level(), 500, pattern.first(), 500));
        }
        assertEquals(500, inputs[0].get(CONTAINER));
        assertEquals(500, inputs[1].get(MATERIAL));
        assertTrue(batcher.getOutputAmounts().isEmpty());
    }

    @Test
    void mixedInitialDamagePoolRoundTripsEveryCancellationCheckpoint() throws Exception {
        var identity = UUID.randomUUID();
        var selected = tool(2, 5, identity);
        var lastUse = tool(4, 5, identity);
        var fresh = tool(0, 5, identity);
        var pattern = new EcoMolecularTestPattern(selected, key -> worn(key, 5));
        var plans = new InputPlan[] {
            InputPlan.deterministicDamage(java.util.List.of(
                    new DamageGroup(lastUse, 3, 1, null),
                    new DamageGroup(selected, 1, 3, null),
                    new DamageGroup(fresh, 1, 2, selected))),
            new InputPlan(MATERIAL, 1, InputMode.CONSUMABLE, null)
        };
        var plan = new MolecularReusableBatchPlan(8, plans);
        var provided = new KeyCounter[] {new KeyCounter(), new KeyCounter()};
        for (int index = 0; index < plans.length; index++) plans[index].addProvidedInputs(provided[index], 8);
        var id = UUID.randomUUID();
        var job = new MolecularCraftingBatcher().prepareReusable(pattern, provided, level(), id, plan);
        assertNotNull(job);
        var registries = registries();
        for (int checkpoint = 0; checkpoint < 8; checkpoint++) {
            var restored = MolecularReusableBatchJob.readFromTag(job.writeToTag(registries), registries);
            assertNotNull(restored, "checkpoint=" + checkpoint);
            assertEquals(id, restored.craftingId());
            assertEquals(job.cancellationRefunds(), restored.cancellationRefunds());
            assertEquals(8 - checkpoint, restored.cancellationRefunds().getLong(MATERIAL));
            var expected = plan.expectedRemainders(checkpoint);
            for (var entry : expected) assertEquals(entry.getLongValue(),
                    restored.cancellationRefunds().getLong(entry.getKey()));
            assertEquals(1, restored.projectedFinalRemainders().getLong(selected));
            assertEquals(checkpoint, restored.completedCrafts());
            job.advance(1);
        }
        assertEquals(1, job.completedRemainders().getLong(selected));
        assertNull(MolecularReusableBatchJob.readFromTag(job.writeToTag(registries), registries),
                "Completed work must never deserialize as a runnable job");
    }

    @Test
    void malformedPersistentGroupsAndOverflowAreQuarantined() throws Exception {
        var initial = tool(2, 5, UUID.randomUUID());
        var pattern = new EcoMolecularTestPattern(initial, key -> worn(key, 6));
        var job = new MolecularCraftingBatcher().prepareReusable(pattern, pattern.supplied(7),
                level(), UUID.randomUUID(), pattern.plan(7));
        assertNotNull(job);
        var registries = registries();
        var valid = job.writeToTag(registries);
        var unreadableFinal = valid.copy();
        unreadableFinal.getList("inputs", 10).getCompound(0).getList("damage_groups", 10)
                .getCompound(1).put("final_key", new net.minecraft.nbt.CompoundTag());
        assertNull(MolecularReusableBatchJob.readFromTag(unreadableFinal, registries));
        var emptyGroups = valid.copy();
        emptyGroups.getList("inputs", 10).getCompound(0).put("damage_groups", new net.minecraft.nbt.ListTag());
        assertNull(MolecularReusableBatchJob.readFromTag(emptyGroups, registries));
        var wrongCount = valid.copy();
        wrongCount.getList("inputs", 10).getCompound(0).getList("damage_groups", 10)
                .getCompound(0).putLong("count", 2);
        assertNull(MolecularReusableBatchJob.readFromTag(wrongCount, registries));
        var overflow = valid.copy();
        overflow.putLong("completed_crafts", 6);
        overflow.getList("inputs", 10).getCompound(1).putLong("remaining_amount", 1);
        var huge = appeng.api.stacks.GenericStack.writeTag(registries,
                new appeng.api.stacks.GenericStack(OUTPUT, Long.MAX_VALUE));
        overflow.getList("primary_outputs", 10).set(0, huge);
        assertNull(MolecularReusableBatchJob.readFromTag(overflow, registries),
                "Full projected primary output must fit, even near the last checkpoint");
        assertNotNull(MolecularReusableBatchJob.readFromTag(valid, registries));
    }

    @Test
    void legacySingleToolMigratesAndInvalidInvariantCurrentKeyIsRejected() throws Exception {
        var initial = tool(2, 5, UUID.randomUUID());
        var pattern = new EcoMolecularTestPattern(initial, key -> worn(key, 6));
        var job = new MolecularCraftingBatcher().prepareReusable(pattern, pattern.supplied(3),
                level(), UUID.randomUUID(), pattern.plan(3));
        assertNotNull(job);
        job.advance(1);
        var registries = registries();
        var legacy = job.writeToTag(registries);
        var input = legacy.getList("inputs", 10).getCompound(0);
        input.remove("damage_groups");
        input.putLong("remaining_amount", 1);
        input.put("current_key", appeng.api.stacks.GenericStack.writeTag(registries,
                new appeng.api.stacks.GenericStack(worn(initial, 6), 1)));
        input.put("final_key", appeng.api.stacks.GenericStack.writeTag(registries,
                new appeng.api.stacks.GenericStack(worn(worn(worn(initial, 6), 6), 6), 1)));
        var restored = MolecularReusableBatchJob.readFromTag(legacy, registries);
        assertNotNull(restored);
        assertEquals(job.cancellationRefunds(), restored.cancellationRefunds());
        assertEquals(job.projectedFinalRemainders(), restored.projectedFinalRemainders());
        var catalyst = AEItemKey.of(Items.STICK);
        var invariant = new EcoMolecularTestPattern(catalyst, key -> key);
        var invariantJob = new MolecularCraftingBatcher().prepareReusable(invariant, invariant.supplied(3),
                level(), UUID.randomUUID(), invariant.plan(3));
        assertNotNull(invariantJob);
        var corrupt = invariantJob.writeToTag(registries);
        corrupt.getList("inputs", 10).getCompound(0).put("current_key",
                appeng.api.stacks.GenericStack.writeTag(registries,
                        new appeng.api.stacks.GenericStack(AEItemKey.of(Items.BONE), 1)));
        assertNull(MolecularReusableBatchJob.readFromTag(corrupt, registries));
    }

    @Test
    void ordinaryAndSelectedOverQuotaBatchesRetainEveryInputEvenWithACachedPlan() throws Exception {
        var ingredient = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(ingredient, ignored -> null);
        var inputs = new KeyCounter[] {counter(ingredient, 4097), counter(MATERIAL, 4097)};
        var batcher = new MolecularCraftingBatcher();
        assertFalse(batcher.prepare(pattern, inputs, level(), 4096));
        assertEquals(4097, inputs[0].get(ingredient));
        assertEquals(4097, inputs[1].get(MATERIAL));
        assertTrue(batcher.prepare(pattern, inputs, level(), 4097));
        assertFalse(batcher.prepare(pattern, inputs, level(), 4096));
        assertFalse(batcher.prepareSelected(pattern, inputs, level(), 4096, pattern.first(), 4097));
        assertEquals(4097, inputs[0].get(ingredient));
        assertEquals(4097, inputs[1].get(MATERIAL));
    }

    @Test
    void passiveCompatiblePreparationKeepsLongCraftCountsAndExactOwnership() throws Exception {
        var ingredient = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(ingredient, ignored -> null);
        long crafts = Integer.MAX_VALUE + 73L;
        var inputs = new KeyCounter[] {counter(ingredient, crafts), counter(MATERIAL, crafts)};
        var batcher = new MolecularCraftingBatcher();
        assertTrue(batcher.prepareSelected(pattern, inputs, level(),
                Long.MAX_VALUE, pattern.first(), crafts));
        assertEquals(crafts, batcher.getCraftCount());
        assertEquals(crafts, batcher.getPrimaryOutputAmounts().getLong(OUTPUT));
        assertEquals(crafts, inputs[0].get(ingredient), "Preparation does not acquire caller inputs");
        assertEquals(crafts, inputs[1].get(MATERIAL));
        batcher.consumeInputs(inputs);
        assertTrue(inputs[0].isEmpty());
        assertTrue(inputs[1].isEmpty());
    }

    @Test
    void existingReusableJobSharesRemainingQuotaAndPersistsPartialOwnership() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
        var job = new MolecularCraftingBatcher().prepareReusable(pattern, pattern.supplied(17),
                level(), UUID.randomUUID(), pattern.plan(17));
        assertNotNull(job);
        var budget = new MolecularAssemblerTickBudget();
        assertTrue(budget.tryConsume(100, 4, 2), "Earlier ordinary or passive dispatch");
        long step = job.nextStep(budget.remaining(100, 4));
        assertEquals(2, step);
        assertTrue(budget.tryConsume(100, 4, step));
        job.advance(step);
        assertEquals(0, job.nextStep(budget.remaining(100, 4)));
        var registries = registries();
        var restored = MolecularReusableBatchJob.readFromTag(job.writeToTag(registries), registries);
        assertNotNull(restored);
        assertEquals(2, restored.completedCrafts());
        assertEquals(15, restored.cancellationRefunds().getLong(MATERIAL));
        assertEquals(1, restored.cancellationRefunds().getLong(catalyst));
        // Removing the engine reduces execution to one recipe without losing the owned batch.
        step = restored.nextStep(budget.remaining(101, 1));
        assertEquals(1, step);
        assertTrue(budget.tryConsume(101, 1, step));
        restored.advance(step);
        assertEquals(3, restored.completedCrafts());
        assertEquals(14, restored.cancellationRefunds().getLong(MATERIAL));
        assertEquals(0, restored.nextStep(budget.remaining(101, 1)));
        step = restored.nextStep(budget.remaining(102, Long.MAX_VALUE));
        assertEquals(14, step);
        assertTrue(budget.tryConsume(102, Long.MAX_VALUE, step));
        restored.advance(step);
        assertTrue(restored.isComplete());
        assertEquals(1, restored.completedRemainders().getLong(catalyst));
    }

    private static HolderLookup.Provider registries() throws Exception {
        if (testRegistries != null) return testRegistries;
        try { appeng.api.stacks.AEKeyTypesInternal.getRegistry(); }
        catch (IllegalStateException uninitialized) {
            var keyTypes = new net.minecraft.core.MappedRegistry<appeng.api.stacks.AEKeyType>(
                    appeng.api.stacks.AEKeyType.REGISTRY_KEY,
                    com.mojang.serialization.Lifecycle.stable(), false);
            appeng.api.stacks.AEKeyTypesInternal.setRegistry(keyTypes);
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.items());
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.fluids());
            keyTypes.freeze();
        }
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT,
                net.neoforged.neoforge.registries.DeferredItem.createItem(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("barrier")));
        return testRegistries = HolderLookup.Provider.create(
                BuiltInRegistries.REGISTRY.stream().map(registry -> registry.asLookup()));
    }

    @Test
    void recipeAssemblyDisagreementRejectsReusableContract() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var encoded = new EcoMolecularTestPattern(catalyst, key -> key);
        var actual = new EcoMolecularTestPattern(catalyst, ignored -> AEItemKey.of(Items.BONE));
        var supplied = encoded.supplied(8);
        assertNull(new MolecularCraftingBatcher().prepareReusable(actual, supplied, level(),
                UUID.randomUUID(), encoded.plan(8)));
        assertEquals(1, supplied[0].get(catalyst));
        assertEquals(8, supplied[1].get(MATERIAL));
    }
}
