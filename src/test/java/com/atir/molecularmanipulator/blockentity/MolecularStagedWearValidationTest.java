package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.DamageGroup;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputMode;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputPlan;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.junit.jupiter.api.Test;

import static com.atir.molecularmanipulator.blockentity.MolecularCraftingBatcher.WEAR_STATES_PER_TICK;
import static com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider.ReusableValidation.*;
import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

class MolecularStagedWearValidationTest {
    static { EcoMolecularTestPattern.bootstrap(); }

    @Test void oneWholeOrderWaitsForEveryStateAndItsReceiptCanOnlyBePreparedOnce() throws Exception {
        int crafts = 3_037;
        var initial = tool(7, 5_000, UUID.randomUUID());
        var recipe = new Recipe(initial, 5_000);
        var batcher = new MolecularCraftingBatcher();
        var job = UUID.randomUUID();
        var level = level();
        var recipes = new Object();
        var stock = new KeyCounter[] {counter(initial, 1), counter(MATERIAL, crafts)};
        var first = recipe.delegate.first();
        int readyTick = -1;
        MolecularReusableBatchPlan ready = null;
        for (int tick = 0; tick < crafts; tick++) {
            var candidate = partial(initial, crafts); // The CPU rebuilds its stock-based quote each tick.
            int before = recipe.assemblies;
            var result = batcher.validateReusable(recipe, candidate, job, level, recipes, tick);
            assertTrue(recipe.assemblies - before <= WEAR_STATES_PER_TICK);
            if (result == READY) {
                ready = candidate;
                readyTick = tick;
                break;
            }
            assertEquals(PENDING, result);
            int reached = recipe.assemblies;
            for (int repeat = 0; repeat < 4; repeat++) {
                assertEquals(PENDING, batcher.validateReusable(recipe, candidate, job, level, recipes, tick));
                assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, candidate, level, recipes, tick));
            }
            assertEquals(reached, recipe.assemblies);
            assertEquals(1, stock[0].get(initial));
            assertEquals(crafts, stock[1].get(MATERIAL));
        }
        assertNotNull(ready);
        assertEquals(crafts, recipe.assemblies);
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, UUID.randomUUID(), ready, level, recipes, readyTick));
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, partial(initial, crafts), level, recipes, readyTick));
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, ready, level, recipes, readyTick + 1));
        assertNull(new MolecularCraftingBatcher().takeVerifiedReusable(recipe, stock, first, job, ready, level, recipes, readyTick));
        var owned = batcher.takeVerifiedReusable(recipe, stock, first, job, ready, level, recipes, readyTick);
        assertNotNull(owned);
        assertEquals(crafts, owned.totalCrafts());
        assertEquals(crafts, owned.projectedPrimaryOutputs().getLong(OUTPUT));
        assertEquals(1, owned.projectedFinalRemainders().getLong(after(initial, crafts)));
        assertEquals(initial, AEItemKey.of(batcher.getCraftingGrid().getItem(0)));
        assertEquals(crafts, recipe.assemblies, "Taking ownership must not rerun the recipe");
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, ready, level, recipes, readyTick));
    }

    @Test void invariantOnlyProposalIsReadyWithOneActualCraftCheck() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var recipe = new EcoMolecularTestPattern(catalyst, key -> key);
        var plan = recipe.plan(5_000);
        var batcher = new MolecularCraftingBatcher();
        var level = level();
        var recipes = new Object();
        var job = UUID.randomUUID();
        assertEquals(READY, batcher.validateReusable(recipe, plan, job, level, recipes, 0));
        var owned = batcher.takeVerifiedReusable(recipe, stock(plan), recipe.first(), job, plan, level, recipes, 0);
        assertNotNull(owned);
        assertEquals(1, owned.projectedFinalRemainders().getLong(catalyst));
        assertEquals(5_000, owned.projectedPrimaryOutputs().getLong(OUTPUT));
    }

    @Test void incompatibleIntermediateSlotPrimaryOrRemainderPreventsTheHandoff() throws Exception {
        int crafts = 3_014;
        var initial = tool(0, 10_000, UUID.randomUUID());
        var level = level();
        var recipes = new Object();
        var plan = partial(initial, crafts);
        for (var fault : Fault.values()) {
            var recipe = new Recipe(initial, 10_000);
            recipe.fault = fault;
            recipe.faultAt = 2_737;
            var batcher = new MolecularCraftingBatcher();
            var job = UUID.randomUUID();
            var result = PENDING;
            for (int tick = 0; result == PENDING && tick <= crafts / WEAR_STATES_PER_TICK + 1; tick++) {
                result = batcher.validateReusable(recipe, plan, job, level, recipes, tick);
            }
            assertEquals(REJECTED, result, fault.name());
            assertTrue(recipe.assemblies >= recipe.faultAt && recipe.assemblies <= recipe.faultAt + 1);
            var stock = new KeyCounter[] {counter(initial, 1), counter(MATERIAL, crafts)};
            assertNull(batcher.takeVerifiedReusable(recipe, stock, recipe.delegate.first(), job, plan, level, recipes, 0));
            assertEquals(crafts, stock[1].get(MATERIAL));
        }
    }

    @Test void changedSelectionPatternLevelRecipeViewAndReloadDiscardEarlierStates() throws Exception {
        var initial = tool(0, 1_000, UUID.randomUUID());
        var level = level();
        var recipes = new Object();
        var original = partial(initial, 2 * WEAR_STATES_PER_TICK);
        for (int change = 0; change < 5; change++) {
            var batcher = new MolecularCraftingBatcher();
            var recipe = new Recipe(initial, 1_000);
            var job = UUID.randomUUID();
            assertEquals(PENDING, batcher.validateReusable(recipe, original, job, level, recipes, 0));
            assertEquals(WEAR_STATES_PER_TICK, recipe.assemblies);
            var next = change == 0 ? partial(after(initial, 1), original.craftCount()) : original;
            var nextPattern = change == 1 ? new Recipe(initial, 1_000) : recipe;
            var nextLevel = change == 2 ? level() : level;
            Object nextView = change == 3 ? new Object() : recipes;
            if (change == 4) PatternRecipeReloads.onDatapackSync(new OnDatapackSyncEvent(null, null));
            nextPattern.fault = Fault.PRIMARY;
            nextPattern.faultAt = 3;
            assertEquals(REJECTED, batcher.validateReusable(nextPattern, next, job, nextLevel, nextView, 1));
        }
    }

    @Test void stockAndRecipeViewAreRecheckedEvenAfterValidationFinishes() throws Exception {
        var initial = tool(0, 200, UUID.randomUUID());
        var recipe = new Recipe(initial, 200);
        var batcher = new MolecularCraftingBatcher();
        var job = UUID.randomUUID();
        var level = level();
        var recipes = new Object();
        var plan = partial(initial, 6);
        var stock = new KeyCounter[] {counter(initial, 1), counter(MATERIAL, 6)};
        var first = recipe.delegate.first();
        assertEquals(READY, batcher.validateReusable(recipe, plan, job, level, recipes, 1));
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, plan, level, new Object(), 1));
        assertEquals(READY, batcher.validateReusable(recipe, plan, job, level, recipes, 2));
        stock[1].remove(MATERIAL, 1);
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, plan, level, recipes, 2));
        assertEquals(5, stock[1].get(MATERIAL));
        stock[1].add(MATERIAL, 1);
        assertEquals(READY, batcher.validateReusable(recipe, plan, job, level, recipes, 3));
        first[0] = counter(after(initial, 1), 1);
        assertNull(batcher.takeVerifiedReusable(recipe, stock, first, job, plan, level, recipes, 3));
        assertEquals(1, stock[0].get(initial));
        assertEquals(6, stock[1].get(MATERIAL));
    }

    @Test void overlappingGroupsShareChecksAndCannotDisagreeOnAClaimedBreak() throws Exception {
        var initial = tool(0, 5_000, UUID.randomUUID());
        var recipe = new Recipe(initial, 5_000);
        var batcher = new MolecularCraftingBatcher();
        var level = level();
        var recipes = new Object();
        var whole = new DamageGroup(initial, 2, 1_900, after(initial, 1_900));
        var overlap = new DamageGroup(after(initial, 300), 17, 1_600, after(initial, 1_900));
        var plan = plan(List.of(whole, overlap));
        assertNotNull(batcher.prepareReusable(recipe, stock(plan), level, UUID.randomUUID(), plan));
        assertEquals(1_900, recipe.assemblies);
        for (var wrongBreak : List.of(new DamageGroup(initial, 1, 300, null),
                new DamageGroup(after(initial, 280), 1, 20, null), new DamageGroup(initial, 1, 1_900, null))) {
            for (var groups : List.of(List.of(whole, wrongBreak), List.of(wrongBreak, whole))) {
                assertEquals(REJECTED, batcher.validateReusable(recipe, plan(groups), UUID.randomUUID(), level, recipes, 0));
            }
        }
    }

    @Test void concurrentOrdersShareTheTickBudgetAndBothAdvance() throws Exception {
        int crafts = WEAR_STATES_PER_TICK * 3;
        var initial = tool(0, 5_000, UUID.randomUUID());
        var first = new Recipe(initial, 5_000);
        var second = new Recipe(initial, 5_000);
        var plan = partial(initial, crafts);
        var batcher = new MolecularCraftingBatcher();
        var level = level();
        var recipes = new Object();
        var firstId = UUID.randomUUID();
        var secondId = UUID.randomUUID();
        var a = PENDING;
        var b = PENDING;
        for (int tick = 0; tick < 10 && (a == PENDING || b == PENDING); tick++) {
            int before = first.assemblies + second.assemblies;
            a = batcher.validateReusable(first, plan, firstId, level, recipes, tick);
            b = batcher.validateReusable(second, plan, secondId, level, recipes, tick);
            assertTrue(first.assemblies + second.assemblies - before <= WEAR_STATES_PER_TICK);
            if (tick == 1) assertTrue(first.assemblies > 0 && second.assemblies > 0);
        }
        assertEquals(READY, a);
        assertEquals(READY, b);
        assertEquals(crafts, first.assemblies);
        assertEquals(crafts, second.assemblies);
    }

    @Test void durabilityNearIntMaxStartsInOneSliceAndDirectCallersKeepTheirBound() throws Exception {
        var initial = tool(0, Integer.MAX_VALUE, UUID.randomUUID());
        var recipe = new Recipe(initial, Integer.MAX_VALUE);
        var plan = partial(initial, Integer.MAX_VALUE - 1L);
        var batcher = new MolecularCraftingBatcher();
        var level = level();
        assertEquals(PENDING, batcher.validateReusable(recipe, plan, UUID.randomUUID(), level, new Object(), 0));
        assertEquals(WEAR_STATES_PER_TICK, recipe.assemblies);
        var stock = stock(plan);
        assertNull(batcher.prepareReusable(recipe, stock, level, UUID.randomUUID(), plan));
        assertTrue(recipe.assemblies <= WEAR_STATES_PER_TICK
                + com.atir.molecularmanipulator.crafting.MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS);
        assertEquals(Integer.MAX_VALUE - 1L, stock[1].get(MATERIAL));
    }

    private static MolecularReusableBatchPlan partial(AEItemKey initial, long crafts) {
        return plan(List.of(new DamageGroup(initial, 1, crafts, after(initial, crafts))));
    }

    private static MolecularReusableBatchPlan plan(List<DamageGroup> groups) {
        var pool = InputPlan.deterministicDamage(groups);
        return new MolecularReusableBatchPlan(pool.coveredCrafts(), new InputPlan[] {
                pool, new InputPlan(MATERIAL, 1, InputMode.CONSUMABLE, null)});
    }

    private static KeyCounter[] stock(MolecularReusableBatchPlan plan) {
        var slots = plan.inputs();
        var result = new KeyCounter[slots.length];
        for (int slot = 0; slot < slots.length; slot++) {
            result[slot] = new KeyCounter();
            slots[slot].addProvidedInputs(result[slot], plan.craftCount());
        }
        return result;
    }

    private static AEItemKey after(AEItemKey tool, long uses) {
        var stack = tool.toStack();
        stack.setDamageValue(Math.toIntExact(stack.getDamageValue() + uses));
        return AEItemKey.of(stack);
    }

    private enum Fault { INPUT_RULE, INPUT_REMAINDER, GRID_RULE, PRIMARY, GRID_REMAINDER }

    private static final class Recipe implements IMolecularAssemblerSupportedPattern {
        final EcoMolecularTestPattern delegate;
        final IInput[] inputs;
        Fault fault;
        int faultAt = -1;
        int assemblies;

        Recipe(AEItemKey initial, int breakDamage) {
            delegate = new EcoMolecularTestPattern(initial, key -> worn(key, breakDamage));
            inputs = delegate.getInputs().clone();
            var tool = inputs[0];
            inputs[0] = new IInput() {
                public GenericStack[] getPossibleInputs() { return tool.getPossibleInputs(); }
                public long getMultiplier() { return tool.getMultiplier(); }
                public boolean isValid(AEKey key, Level level) {
                    return !(fault == Fault.INPUT_RULE && atFault(key)) && tool.isValid(key, level);
                }
                public AEKey getRemainingKey(AEKey key) {
                    return fault == Fault.INPUT_REMAINDER && atFault(key) ? RETURNED : tool.getRemainingKey(key);
                }
            };
        }

        boolean atFault(AEKey key) {
            return key instanceof AEItemKey item && item.getReadOnlyStack().getDamageValue() == faultAt;
        }

        public AEItemKey getDefinition() { return delegate.getDefinition(); }
        public IInput[] getInputs() { return inputs; }
        public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
        public boolean isSlotEnabled(int slot) { return delegate.isSlotEnabled(slot); }
        public boolean isItemValid(int slot, AEItemKey key, Level level) {
            return !(slot == 0 && fault == Fault.GRID_RULE && atFault(key));
        }
        public void fillCraftingGrid(KeyCounter[] holders, CraftingGridAccessor grid) {
            delegate.fillCraftingGrid(holders, grid);
        }
        public ItemStack assemble(CraftingInput grid, Level level) {
            assemblies++;
            var output = delegate.assemble(grid, level);
            if (fault == Fault.PRIMARY && grid.getItem(0).getDamageValue() == faultAt) {
                output.set(DataComponents.CUSTOM_NAME, Component.literal("changed primary"));
            }
            return output;
        }
        public NonNullList<ItemStack> getRemainingItems(CraftingInput grid) {
            var result = delegate.getRemainingItems(grid);
            if (fault == Fault.GRID_REMAINDER && grid.getItem(0).getDamageValue() == faultAt) {
                result.getFirst().remove(DataComponents.CUSTOM_DATA);
            }
            return result;
        }
    }
}
