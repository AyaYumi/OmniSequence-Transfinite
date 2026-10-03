package com.atir.molecularmanipulator.verification;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.CraftingSimulationState;
import com.appliedenhancements.runtime.CraftingPlannerIntervention;
import com.appliedenhancements.storage.InfinitePlanningInventory;
import com.github.appliedenhancements.integration.ae2.AelisBigIntegerCraftingTracker;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises transformed AE classes with the production Applied JAR, not a helper approximation. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class NativeArithmeticGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40)
    public static void removedNativeRejectionKeepsExactAndInfiniteAccounting(GameTestHelper helper) {
        for (AEKey key : List.of(AEItemKey.of(Items.COBBLESTONE), AEFluidKey.of(Fluids.WATER))) {
            var nativeState = new State(key, Long.MAX_VALUE);
            nativeState.insert(key, 80_000, Actionable.MODULATE);
            helper.assertTrue(nativeState.extract(key, 1, Actionable.SIMULATE) == 1,
                    "Paired native inventory must accept sentinel plus byproduct without Applied rejection");
            nativeState.emitItems(key, Long.MAX_VALUE);
            nativeState.emitItems(key, 80_000);
            var finite = new State(key, 42);
            finite.insert(key, 8, Actionable.MODULATE);
            helper.assertTrue(finite.extract(key, 50, Actionable.MODULATE) == 50
                    && finite.extract(key, 1, Actionable.SIMULATE) == 0, "Finite supply must remain finite");
            var marked = new State(key, Long.MAX_VALUE);
            var infinite = (InfinitePlanningInventory) (Object) marked;
            infinite.appliedenhancements$infiniteKeys().add(key);
            helper.assertTrue(marked.extract(key, Long.MAX_VALUE, Actionable.MODULATE) == Long.MAX_VALUE,
                    "Marked infinite source must supply the requested amount");
            marked.insert(key, 80_000, Actionable.MODULATE);
            marked.extract(key, 80_000, Actionable.MODULATE);
            helper.assertTrue(infinite.appliedenhancements$infiniteUsedAmounts().get(key)
                    .equals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(80_000))),
                    "Infinite returns must preserve exact consumption without a false shortage");
        }
        var state = new State(AEItemKey.of(Items.DIAMOND), 0);
        var pattern = pattern();
        try (var scope = CraftingPlannerIntervention.openExplicit()) {
            state.addCrafting(pattern, Long.MAX_VALUE);
            state.addCrafting(pattern, 80_000);
        }
        helper.assertTrue(((AelisBigIntegerCraftingTracker) (Object) state).appliedenhancements$getBigIntegerPatternTimes()
                .get(pattern).equals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(80_000))),
                "Removing rejection must retain exact native task accumulation");
        System.out.println("NATIVE_ARITHMETIC_NO_REJECTION_PASS sentinel=true byproduct=true finite=true infiniteExact=true nativeTaskExact=true");
        helper.succeed();
    }
    private static IPatternDetails pattern() {
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        return (IPatternDetails) Proxy.newProxyInstance(NativeArithmeticGameTests.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class}, (p,m,a) -> switch(m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "getOutputs" -> List.of(output);
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getDefinition" -> AEItemKey.of(Items.PAPER);
                    default -> throw new AssertionError(m.getName());
                });
    }
    private static final class State extends CraftingSimulationState {
        private final AEKey key;
        private final long stock;
        State(AEKey key, long stock) { this.key = key; this.stock = stock; }
        @Override protected long simulateExtractParent(AEKey key, long amount) {
            return this.key.equals(key) ? Math.min(stock, amount) : 0;
        }
        @Override protected Iterable<AEKey> findFuzzyParent(AEKey key) { return List.of(this.key); }
    }
}
