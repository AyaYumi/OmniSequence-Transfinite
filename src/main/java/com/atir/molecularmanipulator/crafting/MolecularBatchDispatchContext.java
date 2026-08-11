package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Synchronous bridge for metadata that AE2's ICraftingProvider API does not
 * carry. A scope exists only around one explicit provider push.
 */
public final class MolecularBatchDispatchContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private MolecularBatchDispatchContext() {
    }

    public static Scope open(@Nullable UUID craftingId, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter[] firstInputs, long craftCount,
            @Nullable MolecularReusableBatchPlan reusablePlan) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested molecular batch dispatch");
        }
        CURRENT.set(new Context(craftingId, pattern, inputs, firstInputs,
                craftCount, reusablePlan));
        return new Scope();
    }

    @Nullable
    public static Context current(IPatternDetails pattern, KeyCounter[] inputs) {
        var context = CURRENT.get();
        return context != null && context.pattern == pattern
                && context.inputs == inputs ? context : null;
    }

    public record Context(@Nullable UUID craftingId, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter[] firstInputs, long craftCount,
            @Nullable MolecularReusableBatchPlan reusablePlan) {
        public Context {
            if (pattern == null || inputs == null || firstInputs == null
                    || inputs.length != firstInputs.length || craftCount <= 1
                    || reusablePlan != null
                            && (craftingId == null
                                    || reusablePlan.craftCount() != craftCount)) {
                throw new IllegalArgumentException("Incomplete molecular batch context");
            }
        }
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                CURRENT.remove();
            }
        }
    }
}
