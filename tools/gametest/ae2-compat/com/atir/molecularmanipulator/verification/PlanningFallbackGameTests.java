package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.CraftingSimulationState;
import com.github.appliedenhancements.integration.ae2.AelisBigIntegerCraftingTracker;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class PlanningFallbackGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "planning-transfer-fallback")
    public static void repeatedTransferCloseRetainsExactWork(GameTestHelper helper) {
        var state = new CraftingSimulationState() {
            @Override protected long simulateExtractParent(AEKey key, long amount) { return 0; }
            @Override protected Iterable<AEKey> findFuzzyParent(AEKey key) { return List.of(); }
        };
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1))), helper.getLevel());
        var tracker = (AelisBigIntegerCraftingTracker) state;
        tracker.appliedenhancements$endProjectedCraftingTransfer();
        tracker.appliedenhancements$beginProjectedCraftingTransfer();
        tracker.appliedenhancements$recordBigIntegerCrafting(pattern, 100);
        tracker.appliedenhancements$endProjectedCraftingTransfer();
        tracker.appliedenhancements$endProjectedCraftingTransfer();
        tracker.appliedenhancements$recordBigIntegerCrafting(pattern, 2);
        helper.assertTrue(tracker.appliedenhancements$getBigIntegerPatternTimes().equals(
                java.util.Map.of(pattern, BigInteger.TWO)),
                "Repeated cleanup must neither suppress exact work nor count projected transfers twice");
        helper.succeed();
    }
}
