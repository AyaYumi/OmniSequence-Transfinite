package cn.dancingsnow.neoecoae.api.me.provider;

/** Compile-only ABI declaration; excluded from runtime and release JARs. */
public interface ECOFastPathDispatchProvider {
    Preparation eco$prepareFastPath(ECOBatchDispatchContext context);
    record Preparation(long capacity, cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator statefulCalculator,
            boolean statefulCalculatorRequired, java.util.function.Predicate<Batch> dispatch) {}
    record Batch(long craftCount, java.util.List<appeng.api.stacks.GenericStack> inputTotal,
            java.util.List<appeng.api.stacks.GenericStack> outputTotal, java.util.List<appeng.api.stacks.GenericStack> remainingTotal,
            java.util.Map<appeng.api.stacks.AEKey, java.math.BigInteger> exactInputTotal) {}
}
