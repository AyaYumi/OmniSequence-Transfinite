package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class OmniExactCraftingStateTest {
    @Test void downgradeExpandsScaledExactTasksWithoutLosingWaitingCreditsOrFinalProgress() {
        var original = pattern(PATTERN);
        var scaled = new MolecularScaledPattern(original, 16);
        var huge = BigInteger.ONE.shiftLeft(80);
        var state = OmniExactCraftingState.create(Map.of(scaled, huge), Map.of(INFINITE, BigInteger.ONE));
        state.setOutputRemaining(huge.multiply(BigInteger.valueOf(16)));
        state.queueOutput(FINITE, huge);
        var normalized = state.normalizeTasks(Map.of(scaled,
                new MolecularExternalScaledPattern.Unwrapped(original, 16)));
        assertEquals(huge.multiply(BigInteger.valueOf(16)), normalized.remaining(original));
        assertEquals(huge.multiply(BigInteger.valueOf(16)), normalized.outputProgress().remaining());
        assertEquals(huge, normalized.uncreditedOutput(FINITE));
        assertTrue(normalized.infiniteKeys().contains(INFINITE));
        assertEquals(huge, state.remaining(scaled), "Normalization must be prepared before replacing live task identities");
    }

    @Test void ordinarySplitScaledTasksExpandTheirOwnCountsBeforeDefinitionsMerge() {
        var original = pattern(PATTERN);
        var seven = new MolecularScaledPattern(original, 7);
        var four = new MolecularScaledPattern(original, 4);
        var tasks = Map.<IPatternDetails, BigInteger>of(seven, BigInteger.TWO, four, BigInteger.ONE,
                original, BigInteger.ONE);
        var state = OmniExactCraftingState.createNormalized(tasks, Map.of(
                seven, new MolecularExternalScaledPattern.Unwrapped(original, 7),
                four, new MolecularExternalScaledPattern.Unwrapped(original, 4),
                original, new MolecularExternalScaledPattern.Unwrapped(original, 1)));
        assertEquals(BigInteger.valueOf(19), state.remaining(original), "7*2 + 4*1 + 1, not a merged count times each scale");
        assertEquals(java.util.Set.of(PATTERN), state.taskDefinitions());
        state.commit(original, state.nextRemaining(original, 1));
        assertEquals(BigInteger.valueOf(18), state.remaining(original));
        assertEquals(BigInteger.TWO, tasks.get(seven), "Preparing normalization must not mutate the source tasks");
    }

    @Test void ordinaryScaledTaskExpansionDoesNotOverflowLong() {
        var original = pattern(PATTERN);
        var scaled = new MolecularScaledPattern(original, 16);
        var count = BigInteger.valueOf(Long.MAX_VALUE);
        var state = OmniExactCraftingState.createNormalized(Map.of(scaled, count), Map.of(
                scaled, new MolecularExternalScaledPattern.Unwrapped(original, 16)));
        assertEquals(count.multiply(BigInteger.valueOf(16)), state.remaining(original));
        assertEquals(OmniExactCraftingState.MAX_WINDOW, OmniExactCraftingState.window(state.remaining(original)));
    }

    @Test void applyingNormalizedTasksPreservesLiveCallbackLedgerAndAllOutputAccounting() {
        var original = pattern(PATTERN);
        var scaled = new MolecularScaledPattern(original, 16);
        var state = OmniExactCraftingState.create(Map.of(scaled, BigInteger.TWO), Map.of());
        state.setOutputRemaining(BigInteger.valueOf(32));
        var progress = state.outputProgress();
        state.queueOutput(FINITE, BigInteger.TEN);
        state.recordCompleted(FINITE, BigInteger.valueOf(3));
        state.applyNormalizedTasks(state.normalizeTasks(Map.of(scaled,
                new MolecularExternalScaledPattern.Unwrapped(original, 16))));
        assertEquals(BigInteger.valueOf(32), state.remaining(original));
        assertTrue(progress == state.outputProgress(), "Do not detach an asynchronous provider's final-output progress");
        assertEquals(BigInteger.TEN, state.uncreditedOutput(FINITE));
        assertEquals(BigInteger.valueOf(3), state.completedOutputs().get(FINITE));
        state.queueOutput(FINITE, BigInteger.ONE);
        assertEquals(BigInteger.valueOf(11), state.uncreditedOutput(FINITE));
    }

    @Test void latestBatchReplacesRatherThanAccumulatesPreviousPush() {
        var pattern = new IPatternDetails() {
            public AEItemKey getDefinition() { return PATTERN; }
            public IInput[] getInputs() { return new IInput[0]; }
            public java.util.List<appeng.api.stacks.GenericStack> getOutputs() {
                return java.util.List.of(new appeng.api.stacks.GenericStack(FINITE, 4));
            }
        };
        var state = OmniExactCraftingState.create(Map.of(pattern, BigInteger.valueOf(100)), Map.of());
        state.recordBatch(pattern, BigInteger.TEN, Map.of(INFINITE, BigInteger.valueOf(90)));
        state.recordBatch(pattern, BigInteger.TWO, Map.of(INFINITE, BigInteger.valueOf(18)));
        var batch = state.lastBatches().get(FINITE);
        assertEquals(BigInteger.valueOf(8), batch.outputAmount());
        assertEquals(BigInteger.valueOf(18), batch.inputs().get(INFINITE));
        assertEquals(batch, state.rebind(java.util.List.of(pattern), null).lastBatches().get(FINITE));
    }
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final AEItemKey PATTERN = AEItemKey.of(Items.STONE);
    private static final AEItemKey INFINITE = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey FINITE = AEItemKey.of(Items.IRON_INGOT);

    @Test
    void advancesAcrossLongWindowsOnlyAfterCommit() {
        var amount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var pattern = pattern(PATTERN);
        var state = OmniExactCraftingState.create(
                Map.of(pattern, amount), Map.of(INFINITE, amount));

        assertEquals(OmniExactCraftingState.MAX_WINDOW,
                OmniExactCraftingState.window(state.remaining(pattern)));
        var next = state.nextRemaining(pattern,
                OmniExactCraftingState.MAX_WINDOW);
        assertEquals(amount, state.remaining(pattern),
                "Preparing a provider attempt must not consume the exact ledger");
        assertEquals(BigInteger.TWO, next);

        state.commit(pattern, next);
        assertEquals(2, OmniExactCraftingState.window(state.remaining(pattern)));
        var finished = state.nextRemaining(pattern, 2);
        state.commit(pattern, finished);
        assertTrue(state.isComplete());
    }

    @Test
    void rejectsOverConsumption() {
        var pattern = pattern(PATTERN);
        var state = OmniExactCraftingState.create(
                Map.of(pattern, BigInteger.ONE), Map.of());

        assertThrows(IllegalStateException.class,
                () -> state.nextRemaining(pattern, 2));
    }

    @Test
    void completedTaskLedgerCanStillWaitForExactFinalOutput() {
        var pattern = pattern(PATTERN);
        var amount = new BigInteger("99999999999999999999");
        var state = OmniExactCraftingState.create(Map.of(pattern, BigInteger.ONE), Map.of());
        state.setOutputRemaining(amount);
        state.commit(pattern, state.nextRemaining(pattern, 1));
        assertTrue(state.isComplete());
        assertFalse(state.outputProgress().complete());
        var rebound = state.rebind(java.util.List.of(), null);
        assertEquals(amount, rebound.outputProgress().remaining());
        assertEquals(Long.MAX_VALUE, rebound.outputProgress().window());
    }

    @Test
    void infiniteWrapperDoesNotMutateDelegateOrInferFromLongMax() {
        var delegate = new ListCraftingInventory(key -> {});
        delegate.insert(FINITE, 7, Actionable.MODULATE);
        var inventory = new OmniInfiniteCraftingInventory(
                delegate, java.util.Set.of(INFINITE));

        assertEquals(Long.MAX_VALUE,
                inventory.extract(INFINITE, Long.MAX_VALUE,
                        Actionable.MODULATE));
        inventory.insert(INFINITE, Long.MAX_VALUE, Actionable.MODULATE);
        assertEquals(0, delegate.list.get(INFINITE));

        assertEquals(7, inventory.extract(FINITE, Long.MAX_VALUE,
                Actionable.MODULATE));
        assertEquals(0, delegate.list.get(FINITE));
        assertFalse(inventory.findFuzzyTemplates(FINITE).iterator().hasNext());

        var templates = new ArrayList<AEKey>();
        inventory.findFuzzyTemplates(INFINITE).forEach(templates::add);
        assertEquals(java.util.List.of(INFINITE), templates);
    }

    @Test
    void bigIntegerDispatchRequiresEveryActualInputToBeInfinite() {
        var pattern = pattern(PATTERN);
        var state = OmniExactCraftingState.create(
                Map.of(pattern, BigInteger.TEN),
                Map.of(INFINITE, BigInteger.TEN));
        var infinite = new KeyCounter();
        infinite.add(INFINITE, 9);
        var finite = new KeyCounter();
        finite.add(FINITE, 1);

        assertTrue(state.hasOnlyInfiniteInputs(new KeyCounter[] {infinite}));
        assertFalse(state.hasOnlyInfiniteInputs(
                new KeyCounter[] {infinite, finite}));
        assertFalse(state.hasOnlyInfiniteInputs(new KeyCounter[0]));
    }

    @Test
    void replenishesBigIntegerOutputsOneLongWindowAtATime() {
        var pattern = pattern(PATTERN);
        var state = OmniExactCraftingState.create(
                Map.of(pattern, BigInteger.ONE), Map.of());
        var total = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.TWO)
                .add(BigInteger.valueOf(7));

        state.queueOutput(INFINITE, 1, total);
        assertEquals(Long.MAX_VALUE,
                state.claimOutputWindow(INFINITE, 0));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(
                        BigInteger.valueOf(7)),
                state.uncreditedOutput(INFINITE));
        assertEquals(0, state.claimOutputWindow(
                INFINITE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,
                state.claimOutputWindow(INFINITE, 0));
        assertEquals(7, state.claimOutputWindow(INFINITE, 0));
        assertEquals(BigInteger.ZERO, state.uncreditedOutput(INFINITE));
    }

    @Test
    void acceptsAlreadyScaledNativeCallbackOutput() {
        var pattern = pattern(PATTERN);
        var state = OmniExactCraftingState.create(
                Map.of(pattern, BigInteger.ONE), Map.of());
        var total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);

        state.queueOutput(INFINITE, total);
        assertEquals(Long.MAX_VALUE,
                state.claimOutputWindow(INFINITE, 0));
        assertEquals(BigInteger.TEN, state.uncreditedOutput(INFINITE));
    }

    private static IPatternDetails pattern(AEItemKey definition) {
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return definition; }
            @Override public IInput[] getInputs() { return new IInput[0]; }
            @Override public java.util.List<appeng.api.stacks.GenericStack> getOutputs() {
                return java.util.List.of();
            }
        };
    }
}
