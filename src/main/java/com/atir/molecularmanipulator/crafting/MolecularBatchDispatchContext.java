package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.util.Objects;
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
        return open(new Context(craftingId, pattern, inputs, firstInputs, craftCount, reusablePlan, null, false));
    }

    /** The receiving machine must consume the proof for this exact plan before taking its inputs. */
    public static Scope openVerifiedReusable(UUID craftingId, IPatternDetails pattern, KeyCounter[] inputs,
            KeyCounter[] firstInputs, long craftCount, MolecularReusableBatchPlan reusablePlan) {
        return open(new Context(craftingId, pattern, inputs, firstInputs, craftCount,
                Objects.requireNonNull(reusablePlan), null, true));
    }

    /** Transport for one fixed, consumptive delivery whose quote used these exact unit outputs. */
    public static Scope openFixed(UUID craftingId, IPatternDetails pattern, KeyCounter[] inputs,
            KeyCounter[] firstInputs, long craftCount, KeyCounter unitOutputs) {
        return open(new Context(Objects.requireNonNull(craftingId), pattern, inputs, firstInputs,
                craftCount, null, Objects.requireNonNull(unitOutputs), false));
    }

    private static Scope open(Context context) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested molecular batch dispatch");
        CURRENT.set(context);
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
            @Nullable MolecularReusableBatchPlan reusablePlan, @Nullable KeyCounter fixedUnitOutputs,
            boolean requiresReusableProof) {
        public Context(@Nullable UUID craftingId, IPatternDetails pattern, KeyCounter[] inputs,
                KeyCounter[] firstInputs, long craftCount, @Nullable MolecularReusableBatchPlan reusablePlan) {
            this(craftingId, pattern, inputs, firstInputs, craftCount, reusablePlan, null, false);
        }

        public Context {
            if (pattern == null || inputs == null || firstInputs == null || inputs.length != firstInputs.length
                    || craftCount <= 0 || fixedUnitOutputs == null && craftCount == 1
                    || requiresReusableProof && reusablePlan == null
                    || fixedUnitOutputs != null && (craftingId == null || reusablePlan != null || fixedUnitOutputs.isEmpty())
                    || reusablePlan != null && (craftingId == null || reusablePlan.craftCount() != craftCount)) {
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
