package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

/**
 * Exact aggregate remainder contract for one synchronous molecular provider push.
 * This class deliberately has no ECO dependencies: the batcher also loads when
 * ECO is absent. The ordinary AE2 selected-input path remains consumable-only.
 */
public final class EcoMolecularRemainderContext implements AutoCloseable {
    private static final ThreadLocal<EcoMolecularRemainderContext> CURRENT = new ThreadLocal<>();

    private final IPatternDetails pattern;
    private final KeyCounter[] inputs;
    private final KeyCounter remainders;

    private EcoMolecularRemainderContext(IPatternDetails pattern, KeyCounter[] inputs,
            KeyCounter remainders) {
        this.pattern = pattern;
        this.inputs = inputs;
        this.remainders = new KeyCounter();
        this.remainders.addAll(remainders);
    }

    static EcoMolecularRemainderContext open(IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter remainders) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested ECO molecular remainder contract");
        }
        var scope = new EcoMolecularRemainderContext(pattern, inputs, remainders);
        CURRENT.set(scope);
        return scope;
    }

    @Nullable
    public static KeyCounter expected(IPatternDetails pattern, KeyCounter[] inputs) {
        var scope = CURRENT.get();
        if (scope == null || scope.pattern != pattern || scope.inputs != inputs) {
            return null;
        }
        var copy = new KeyCounter();
        copy.addAll(scope.remainders);
        return copy;
    }

    public static boolean matches(KeyCounter expected, Iterable<? extends
            it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey>> actual) {
        var remaining = new KeyCounter();
        remaining.addAll(expected);
        for (var entry : actual) {
            if (entry.getKey() == null || entry.getLongValue() <= 0
                    || remaining.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
            remaining.remove(entry.getKey(), entry.getLongValue());
        }
        remaining.removeZeros();
        return remaining.isEmpty();
    }

    @Override
    public void close() {
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
    }
}
