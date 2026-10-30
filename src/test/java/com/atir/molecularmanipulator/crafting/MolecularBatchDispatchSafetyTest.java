package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MolecularBatchDispatchSafetyTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null)
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private final AEItemKey result = AEItemKey.of(Items.DIAMOND);
    private final AEItemKey returnedTool = AEItemKey.of(Items.IRON_SWORD);
    private final AEItemKey previousTool = AEItemKey.of(Items.STONE_SWORD);

    @Test
    void resetToolPrototypesDoNotInvalidateAnExactlyAccountedBatch() {
        var expected = new KeyCounter();
        expected.add(previousTool, 1);
        expected.reset();
        expected.add(result, 5000);
        expected.add(returnedTool, 1);
        var observed = new KeyCounter();
        observed.add(result, 5000);
        observed.add(returnedTool, 1);
        assertTrue(MolecularBatchDispatchSafety.amountsMatch(expected, observed));
        assertTrue(MolecularBatchDispatchSafety.amountsMatch(observed, expected));
    }

    @ParameterizedTest
    @ValueSource(longs = {4999, 5001})
    void missingOrSurplusProductsStillInvalidateTheBatch(long actual) {
        var expected = new KeyCounter();
        expected.add(result, 5000);
        expected.add(returnedTool, 1);
        var observed = new KeyCounter();
        observed.add(result, actual);
        observed.add(returnedTool, 1);
        assertFalse(MolecularBatchDispatchSafety.amountsMatch(expected, observed));
    }

    @Test
    void anUnexpectedPositiveRemainderCannotReplaceAZeroPrototype() {
        var expected = new KeyCounter();
        expected.add(previousTool, 1);
        expected.reset();
        expected.add(result, 5000);
        var observed = new KeyCounter();
        observed.add(result, 5000);
        observed.add(returnedTool, 1);
        assertFalse(MolecularBatchDispatchSafety.amountsMatch(expected, observed));
    }
}
