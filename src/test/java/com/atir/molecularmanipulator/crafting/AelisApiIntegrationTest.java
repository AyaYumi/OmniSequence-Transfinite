package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AelisApiIntegrationTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static IPatternDetails pattern() {
        return new IPatternDetails() {
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            public IInput[] getInputs() { return new IInput[0]; }
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(AEItemKey.of(Items.STONE), 2));
            }
        };
    }

    private static CraftingPlan plan(Map<IPatternDetails, Long> tasks) {
        return new CraftingPlan(new GenericStack(AEItemKey.of(Items.STONE), 1),
                1, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), tasks);
    }

    @Test void cumulativeOutputRequiresExactExecutionEvenWhenTaskCountFitsLong() {
        var metadata = AelisExactCraftingPlanApi.read(plan(Map.of(pattern(), Long.MAX_VALUE)));
        assertFalse(metadata.executionRequirement().patternTimes());
        assertTrue(metadata.executionRequirement().craftedAmounts());
        assertTrue(metadata.executionRequirement().requiresExactExecution());
    }

    @Test void rewrittenScaledPlanRetainsCompleteLedgerAndRemainder() {
        var original = pattern();
        var count = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        var source = AelisExactCraftingPlanApi.attachExecutionMetadata(
                plan(Map.of(original, Long.MAX_VALUE)), BigInteger.ONE,
                Map.of(original, count), Map.of());
        var scaled = new MolecularScaledPattern(original, 7);
        var rewritten = AelisCycleExecutionApi.copyMetadata(source, plan(Map.of(scaled, 1L)));
        var tasks = AelisExactCraftingPlanApi.read(rewritten).patternTimes();
        assertEquals(count.divide(BigInteger.valueOf(7)), tasks.get(scaled));
        assertEquals(count.remainder(BigInteger.valueOf(7)), tasks.get(original));
        assertEquals(tasks, AelisExactCraftingPlanApi.read(
                AelisCycleExecutionApi.copyMetadata(rewritten, rewritten)).patternTimes());
    }
}
