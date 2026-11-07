package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.atir.molecularmanipulator.ForgeTestBootstrap;
import com.atir.molecularmanipulator.crafting.MolecularRemainderContext;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputMode;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputPlan;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Integration-independent ownership cases from the legacy reusable-batch regression suite. */
class MolecularReusableBatchOwnershipTest {
    static { ForgeTestBootstrap.bootstrapAeKeyTypes(); }
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey CONTAINER = AEItemKey.of(Items.MILK_BUCKET);
    private static final AEItemKey RETURNED = AEItemKey.of(Items.BUCKET);

    @Test void linearContainerBatchRequiresExactContractAndPreservesInputsOnFailure() {
        var pattern = new Pattern(CONTAINER, ignored -> RETURNED);
        var inputs = pattern.supplied(500);
        var batcher = new MolecularCraftingBatcher();
        assertFalse(batcher.prepareSelected(pattern, inputs, null, 500, pattern.supplied(1), 500));
        assertEquals(500, inputs[0].get(CONTAINER));
        try (var scope = MolecularRemainderContext.open(pattern, inputs, counter(RETURNED, 500))) {
            assertTrue(batcher.prepareSelected(pattern, inputs, null, 500, pattern.supplied(1), 500));
        }
        assertEquals(500, batcher.getPrimaryOutputAmounts().getLong(OUTPUT));
        assertEquals(500, batcher.getRemainderOutputAmounts().getLong(RETURNED));
        assertEquals(500, inputs[0].get(CONTAINER));
        try (var scope = MolecularRemainderContext.open(pattern, inputs, counter(RETURNED, 499))) {
            assertFalse(batcher.prepareSelected(pattern, inputs, null, 500, pattern.supplied(1), 500));
        }
        assertEquals(500, inputs[0].get(CONTAINER));
        assertEquals(500, inputs[1].get(MATERIAL));
        assertTrue(batcher.getOutputAmounts().isEmpty());
        assertNull(MolecularRemainderContext.expected(pattern, inputs));
    }

    @Test void realBatcherAcceptsFullReusableOwnershipAndJobRoundTrips() {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new Pattern(catalyst, key -> key);
        var inputs = new KeyCounter[] {counter(catalyst, 1), counter(MATERIAL, 17)};
        var plan = new MolecularReusableBatchPlan(17, new InputPlan[] {
                new InputPlan(catalyst, 1, InputMode.INVARIANT_REUSABLE, catalyst),
                new InputPlan(MATERIAL, 1, InputMode.CONSUMABLE, null)
        });
        var id = UUID.randomUUID();
        var job = new MolecularCraftingBatcher().prepareReusable(pattern, inputs, null, id, plan);
        assertNotNull(job);
        assertEquals(1, inputs[0].get(catalyst), "Preparation owns nothing");
        assertEquals(17, inputs[1].get(MATERIAL));
        assertEquals(id, job.craftingId());
        job.advance(2);
        var restored = MolecularReusableBatchJob.readFromTag(job.writeToTag());
        assertNotNull(restored);
        assertEquals(id, restored.craftingId());
        assertEquals(2, restored.completedCrafts());
        assertEquals(15, restored.cancellationRefunds().getLong(MATERIAL));
        assertEquals(1, restored.cancellationRefunds().getLong(catalyst));
        assertEquals(job.projectedFinalRemainders(), restored.projectedFinalRemainders());
        restored.advance(15);
        assertTrue(restored.isComplete());
        assertEquals(1, restored.completedRemainders().getLong(catalyst));
        assertNull(MolecularReusableBatchJob.readFromTag(restored.writeToTag()),
                "Completed work must never deserialize as a runnable job");
    }

    @Test void aggregateRemainderScopeIsIdentityBoundAndDefensivelyCopied() {
        var pattern = new Pattern(CONTAINER, ignored -> RETURNED);
        var inputs = pattern.supplied(5);
        var expected = counter(RETURNED, 5);
        try (var scope = MolecularRemainderContext.open(pattern, inputs, expected)) {
            expected.clear();
            var copy = MolecularRemainderContext.expected(pattern, inputs);
            assertNotNull(copy);
            assertEquals(5, copy.get(RETURNED));
            copy.clear();
            assertEquals(5, MolecularRemainderContext.expected(pattern, inputs).get(RETURNED));
            assertNull(MolecularRemainderContext.expected(new Pattern(CONTAINER, ignored -> RETURNED), inputs));
            assertNull(MolecularRemainderContext.expected(pattern, inputs.clone()));
            assertThrows(IllegalStateException.class,
                    () -> MolecularRemainderContext.open(pattern, inputs, counter(RETURNED, 5)));
        }
        assertNull(MolecularRemainderContext.expected(pattern, inputs));
        try (var scope = MolecularRemainderContext.open(pattern, inputs, counter(RETURNED, 5))) {
            assertTrue(MolecularRemainderContext.matches(counter(RETURNED, 5), counter(RETURNED, 5)));
            assertFalse(MolecularRemainderContext.matches(counter(RETURNED, 5), counter(RETURNED, 4)));
            assertFalse(MolecularRemainderContext.matches(counter(RETURNED, 5), counter(MATERIAL, 5)));
        }
    }

    private static KeyCounter counter(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static final class Pattern implements IMolecularAssemblerSupportedPattern {
        private final AEItemKey ingredient;
        private final Function<AEKey, AEKey> transition;
        Pattern(AEItemKey ingredient, Function<AEKey, AEKey> transition) {
            this.ingredient = ingredient;
            this.transition = transition;
        }
        KeyCounter[] supplied(long crafts) {
            return new KeyCounter[] {counter(ingredient, crafts), counter(MATERIAL, crafts)};
        }
        public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        public IInput[] getInputs() {
            return new IInput[] {input(ingredient, transition), input(MATERIAL, ignored -> null)};
        }
        public GenericStack[] getOutputs() { return new GenericStack[] {new GenericStack(OUTPUT, 1)}; }
        public ItemStack assemble(net.minecraft.world.Container input, Level level) { return OUTPUT.toStack(); }
        public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
            var result = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
            for (int slot = 0; slot < input.getContainerSize(); slot++) {
                var stack = input.getItem(slot);
                if (!stack.isEmpty() && stack.is(ingredient.getItem())) {
                    var remainder = transition.apply(AEItemKey.of(stack));
                    if (remainder instanceof AEItemKey item) result.set(slot, item.toStack());
                }
            }
            return result;
        }
        public boolean isItemValid(int slot, AEItemKey key, Level level) { return true; }
        public boolean isSlotEnabled(int slot) { return slot < 2; }
        public void fillCraftingGrid(KeyCounter[] holders, CraftingGridAccessor grid) {
            for (int slot = 0; slot < holders.length; slot++) {
                var entry = holders[slot].getFirstEntry();
                if (entry == null || entry.getLongValue() <= 0) throw new IllegalArgumentException();
                grid.set(slot, ((AEItemKey) entry.getKey()).toStack());
                holders[slot].remove(entry.getKey(), 1);
            }
        }
        private static IInput input(AEItemKey template, Function<AEKey, AEKey> transition) {
            return new IInput() {
                public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(template, 1)}; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey candidate, Level level) { return template.equals(candidate); }
                public AEKey getRemainingKey(AEKey candidate) { return transition.apply(candidate); }
            };
        }
    }
}
