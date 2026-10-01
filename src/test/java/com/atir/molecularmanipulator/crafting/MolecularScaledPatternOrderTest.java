package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class MolecularScaledPatternOrderTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private final AEItemKey a = AEItemKey.of(Items.COAL);
    private final AEItemKey b = AEItemKey.of(Items.IRON_INGOT);
    private final AEItemKey c = AEItemKey.of(Items.GOLD_INGOT);

    private IPatternDetails pattern(List<GenericStack> sequence) {
        // AE2's item registry is bound in the transformed server smoke test. Here the
        // collaborator supplies the two callback contracts (sparse / actual holders).
        var amounts = new LinkedHashMap<AEKey, Long>();
        sequence.forEach(stack -> amounts.merge(stack.what(), stack.amount(), Math::addExact));
        var inputs = amounts.entrySet().stream().map(entry -> new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(entry.getKey(), entry.getValue())}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return entry.getKey().equals(key); }
            public AEKey getRemainingKey(AEKey key) { return null; }
        }).toArray(IPatternDetails.IInput[]::new);
        return new IPatternDetails() {
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            public IInput[] getInputs() { return inputs; }
            public List<GenericStack> getOutputs() { return List.of(new GenericStack(c, 1)); }
            public boolean supportsPushInputsToExternalInventory() { return true; }
            public void pushInputsToExternalInventory(KeyCounter[] holders, PatternInputSink sink) {
                if (sequence.size() != inputs.length) sequence.forEach(stack -> sink.pushInput(stack.what(), stack.amount()));
                else for (var holder : holders) for (var entry : holder) sink.pushInput(entry.getKey(), entry.getLongValue());
            }
        };
    }

    private KeyCounter holder(AEKey key, long amount) {
        var holder = new KeyCounter();
        holder.add(key, amount);
        return holder;
    }

    private KeyCounter[] holders(IPatternDetails pattern, long crafts) {
        var inputs = pattern.getInputs();
        var result = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            var possible = inputs[i].getPossibleInputs()[0];
            result[i] = holder(possible.what(), possible.amount() * inputs[i].getMultiplier() * crafts);
        }
        return result;
    }

    @Test void sparseRecipeKeepsNonAdjacentDuplicatesAndDeliversEveryBatchMaterial() {
        var base = pattern(List.of(new GenericStack(b, 1), new GenericStack(a, 2), new GenericStack(b, 3)));
        var actual = holders(base, 5);
        var delivered = new ArrayList<GenericStack>();
        new MolecularScaledPattern(base, 5).pushInputsToExternalInventory(actual,
                (key, amount) -> delivered.add(new GenericStack(key, amount)));
        assertEquals(List.of(new GenericStack(b, 5), new GenericStack(a, 10), new GenericStack(b, 15)), delivered);
        var totals = new KeyCounter();
        delivered.forEach(stack -> totals.add(stack.what(), stack.amount()));
        assertEquals(20, totals.get(b));
        assertEquals(10, totals.get(a));
        var remaining = new KeyCounter();
        for (var holder : actual) remaining.addAll(holder);
        assertEquals(20, remaining.get(b));
        assertEquals(10, remaining.get(a));
    }

    @Test void condensedNativeCallbackDoesNotMultiplyAnAlreadyScaledHolderAgain() {
        var base = pattern(List.of(new GenericStack(b, 1), new GenericStack(a, 2)));
        var delivered = new ArrayList<GenericStack>();
        new MolecularScaledPattern(base, 7).pushInputsToExternalInventory(holders(base, 7),
                (key, amount) -> delivered.add(new GenericStack(key, amount)));
        assertEquals(List.of(new GenericStack(b, 7), new GenericStack(a, 14)), delivered);
    }

    @Test void largeSparseTotalsDoNotOverflowDuringOrderProjection() {
        var base = pattern(List.of(new GenericStack(a, 1), new GenericStack(b, 1), new GenericStack(a, 1)));
        var delivered = new ArrayList<GenericStack>();
        new MolecularScaledPattern(base, 2).pushInputsToExternalInventory(
                new KeyCounter[]{holder(a, Long.MAX_VALUE - 1), holder(b, 2)},
                (key, amount) -> delivered.add(new GenericStack(key, amount)));
        assertEquals(List.of(new GenericStack(a, (Long.MAX_VALUE - 1) / 2),
                new GenericStack(b, 2), new GenericStack(a, (Long.MAX_VALUE - 1) / 2)), delivered);
    }

    @Test void malformedHolderIsRejectedBeforeAnyMaterialIsSent() {
        var base = pattern(List.of(new GenericStack(a, 1), new GenericStack(b, 1)));
        var delivered = new ArrayList<GenericStack>();
        assertThrows(IllegalArgumentException.class, () -> new MolecularScaledPattern(base, 2)
                .pushInputsToExternalInventory(new KeyCounter[]{holder(a, 2), holder(b, -1)},
                        (key, amount) -> delivered.add(new GenericStack(key, amount))));
        assertTrue(delivered.isEmpty());
    }
}
