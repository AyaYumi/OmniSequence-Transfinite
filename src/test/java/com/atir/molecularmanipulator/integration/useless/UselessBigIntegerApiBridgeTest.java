package com.atir.molecularmanipulator.integration.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UselessBigIntegerApiBridgeTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static final AEItemKey INPUT = AEItemKey.of(Items.COAL);
    static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    static final IPatternDetails BASE = new Pattern(1);

    // The public UselessMod API unwraps patterns and counts BASE operations.
    // These fixtures intentionally model that contract, including array identity.
    public interface Scaled {
        IPatternDetails getOriginal();
        long getOperationsPerPush();
    }
    public static class Pattern implements IPatternDetails {
        final long factor;
        Pattern(long factor) { this.factor = factor; }
        public AEItemKey getDefinition() { return AEItemKey.of(Items.STONE); }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, factor)); }
    }
    public static class Wrapped extends Pattern implements Scaled {
        Wrapped(long factor) { super(factor); }
        public IPatternDetails getOriginal() { return BASE; }
        public long getOperationsPerPush() { return factor; }
    }
    public record Capacity(BigInteger accepted) {
        public boolean isAvailable() { return accepted.signum() > 0; }
    }
    public static class Provider implements ICraftingProvider {
        final Target target = new Target();
        public Target bigIntegerTarget() { return target; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(BASE); }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return false; }
        public boolean isBusy() { return false; }
    }
    public static class Target {
        BigInteger limit = new BigInteger("999999999999999999999999999999");
        BigInteger produced = BigInteger.ZERO;
        boolean reject;
        boolean shrink;
        boolean shrinkOnce;
        public Capacity capacity(IPatternDetails pattern, KeyCounter[] prototype, BigInteger requested) {
            assertSame(BASE, pattern, "native API requires unwrapped pattern");
            assertEquals(9, prototype[0].get(INPUT), "native API requires one base recipe");
            return new Capacity(requested.min(limit));
        }
        public Batch admit(IPatternDetails pattern, KeyCounter[] prototype, BigInteger requested, Object binding) {
            var accepted = capacity(pattern, prototype, requested).accepted();
            boolean partial = shrink || shrinkOnce;
            shrinkOnce = false;
            return new Batch(this, prototype, partial ? accepted.subtract(BigInteger.ONE) : accepted);
        }
    }
    public record Batch(Target target, KeyCounter[] prototype, BigInteger count) {
        public boolean commit(KeyCounter[] actual) {
            assertSame(prototype, actual, "commit must use the admitted array");
            if (target.reject) return false;
            target.produced = target.produced.add(count);
            for (var counter : actual) counter.clear();
            return true;
        }
    }
    static UselessBigIntegerApiBridge.TargetAdapter adapter(Provider p) throws Exception {
        return new UselessBigIntegerApiBridge.TargetAdapter(p,
                Provider.class.getMethod("bigIntegerTarget"),
                Target.class.getMethod("capacity", IPatternDetails.class, KeyCounter[].class, BigInteger.class),
                Target.class.getMethod("admit", IPatternDetails.class, KeyCounter[].class, BigInteger.class, Object.class),
                Batch.class.getMethod("commit", KeyCounter[].class),
                Capacity.class.getMethod("accepted"), Capacity.class.getMethod("isAvailable"),
                Batch.class.getMethod("count"), null, Scaled.class,
                Scaled.class.getMethod("getOriginal"), Scaled.class.getMethod("getOperationsPerPush"), null);
    }
    static KeyCounter[] inputs(long factor) {
        var counter = new KeyCounter(); counter.add(INPUT, Math.multiplyExact(9, factor));
        return new KeyCounter[]{counter};
    }
    @Test void scaledBatchProducesExactlyWhatCpuExpectsBeyondLong() throws Exception {
        long multiplier = Long.MAX_VALUE / 10;
        var pattern = new Wrapped(multiplier);
        var p = new Provider(); var adapter = adapter(p); var inputs = inputs(multiplier);
        var tasks = BigInteger.valueOf(2_222_222);
        assertEquals(tasks, adapter.getMaximumBigIntegerCrafts(pattern, inputs, tasks));
        assertEquals(9 * multiplier, inputs[0].get(INPUT), "capacity must not consume inputs");
        assertTrue(adapter.pushBigIntegerCraftingPattern(pattern, tasks, inputs));
        var expected = tasks.multiply(BigInteger.valueOf(pattern.getOutputs().getFirst().amount()));
        assertTrue(expected.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertEquals(expected, p.target.produced, "machine output must equal CPU waiting ledger");
        assertTrue(inputs[0].isEmpty());
    }
    @Test void capacityRoundsDownToWholeScaledTasks() throws Exception {
        var p = new Provider(); p.target.limit = BigInteger.valueOf(29);
        assertEquals(BigInteger.TWO, adapter(p).getMaximumBigIntegerCrafts(new Wrapped(10), inputs(10), BigInteger.TEN));
        p.target.limit = BigInteger.valueOf(9);
        assertEquals(BigInteger.ZERO, adapter(p).getMaximumBigIntegerCrafts(new Wrapped(10), inputs(10), BigInteger.TEN));
    }
    @Test void rejectedAndShrunkenAdmissionsPreserveCallerInputs() throws Exception {
        var p = new Provider(); var adapter = adapter(p); var inputs = inputs(10);
        p.target.reject = true;
        assertFalse(adapter.pushBigIntegerCraftingPattern(new Wrapped(10), BigInteger.TWO, inputs));
        assertEquals(90, inputs[0].get(INPUT));
        p.target.reject = false; p.target.shrink = true;
        assertFalse(adapter.pushBigIntegerCraftingPattern(new Wrapped(10), BigInteger.TWO, inputs));
        assertEquals(90, inputs[0].get(INPUT));
        assertEquals(BigInteger.ZERO, p.target.produced);
    }

    @Test void transientPartialAdmissionRetriesAtWholeScaledTaskBoundary() throws Exception {
        var p = new Provider();
        var adapter = adapter(p);
        var inputs = inputs(10);
        p.target.shrinkOnce = true;

        assertTrue(adapter.pushBigIntegerCraftingPattern(new Wrapped(10), BigInteger.TWO, inputs));
        assertEquals(BigInteger.TEN, p.target.produced,
                "the bridge must retry with one complete scaled task");
        assertTrue(inputs[0].isEmpty());
    }
    @Test void unscaledPatternsKeepTheirUnits() throws Exception {
        var p = new Provider(); var adapter = adapter(p);
        assertTrue(adapter.pushBigIntegerCraftingPattern(BASE, BigInteger.TEN, inputs(1)));
        assertEquals(BigInteger.TEN, p.target.produced);
    }
    @Test void nonDivisiblePrototypeIsRejectedWithoutConsumption() throws Exception {
        var p = new Provider(); var adapter = adapter(p); var inputs = inputs(10);
        inputs[0].add(INPUT, 1);
        assertEquals(BigInteger.ZERO, adapter.getMaximumBigIntegerCrafts(new Wrapped(10), inputs, BigInteger.TEN));
        assertFalse(adapter.pushBigIntegerCraftingPattern(new Wrapped(10), BigInteger.TEN, inputs));
        assertEquals(91, inputs[0].get(INPUT));
    }
}
