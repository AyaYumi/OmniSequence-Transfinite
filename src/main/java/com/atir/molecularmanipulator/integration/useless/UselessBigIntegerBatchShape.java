package com.atir.molecularmanipulator.integration.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Converts smart-doubling task units to the native API's base recipe units. */
record UselessBigIntegerBatchShape(IPatternDetails pattern, KeyCounter[] prototype,
                                  BigInteger operationsPerTask) {
    static UselessBigIntegerBatchShape resolve(IPatternDetails pattern, KeyCounter[] prototype,
            Class<?> scaledType, Method original, Method multiplier) throws ReflectiveOperationException {
        var seen = Collections.newSetFromMap(new IdentityHashMap<IPatternDetails, Boolean>());
        BigInteger operations = BigInteger.ONE;
        while (scaledType.isInstance(pattern)) {
            if (!seen.add(pattern)) throw new IllegalArgumentException("Cyclic scaled pattern");
            long factor = ((Number) multiplier.invoke(pattern)).longValue();
            if (factor <= 0) throw new IllegalArgumentException("Invalid scaled pattern multiplier");
            operations = operations.multiply(BigInteger.valueOf(factor));
            pattern = (IPatternDetails) original.invoke(pattern);
        }
        if (pattern == null) throw new IllegalArgumentException("Missing original pattern");
        var unit = new KeyCounter[prototype.length];
        for (int slot = 0; slot < prototype.length; slot++) {
            unit[slot] = new KeyCounter();
            for (var entry : prototype[slot]) {
                var divided = BigInteger.valueOf(entry.getLongValue()).divideAndRemainder(operations);
                if (divided[0].signum() <= 0 || divided[1].signum() != 0) {
                    throw new IllegalArgumentException("Scaled inputs are not a whole base recipe prototype");
                }
                unit[slot].add(entry.getKey(), divided[0].longValueExact());
            }
        }
        return new UselessBigIntegerBatchShape(pattern, unit, operations);
    }

    BigInteger nativeCount(BigInteger tasks) { return tasks.multiply(operationsPerTask); }
    BigInteger wholeTasks(BigInteger nativeCount) { return nativeCount.divide(operationsPerTask); }
    BigInteger minimumOutput() {
        var amount = BigInteger.ZERO;
        for (var output : pattern.getOutputs()) if (output.amount() > 0) amount = amount.add(BigInteger.valueOf(output.amount()));
        return amount.multiply(operationsPerTask).max(BigInteger.ONE);
    }
}
