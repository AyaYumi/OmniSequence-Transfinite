package com.atir.molecularmanipulator.integration.eco;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the actual 21.2.9 Batch accessor, including its exact-only long materialization. */
class EcoMatterBatchAdapterTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final AEKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey COPPER = AEItemKey.of(Items.COPPER_INGOT);

    @Test void exactOnlyVectorPreservesLongMaximumAndOtherKeys() {
        var exact = Map.of(IRON, BigInteger.valueOf(Long.MAX_VALUE), COPPER, BigInteger.valueOf(3_000_000_000L));
        var batch = new ECOFastPathDispatchProvider.Batch(1026, List.of(), List.of(), List.of(), exact);
        assertEquals(Map.of(IRON, Long.MAX_VALUE, COPPER, 3_000_000_000L), EcoMatterBatchAdapter.inputs(batch));
    }

    @Test void exactOnlyOverflowCannotWrapToValidLongMaterial() {
        var batch = new ECOFastPathDispatchProvider.Batch(1, List.of(), List.of(), List.of(),
                Map.of(IRON, BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE)));
        assertThrows(ArithmeticException.class, () -> EcoMatterBatchAdapter.inputs(batch));
    }

    @Test void longVectorCannotHideExactExcessOrMissingKeys() {
        var small = List.of(new GenericStack(IRON, 1));
        var overflow = new ECOFastPathDispatchProvider.Batch(1, small, List.of(), List.of(),
                Map.of(IRON, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertThrows(ArithmeticException.class, () -> EcoMatterBatchAdapter.inputs(overflow));
        var missing = new ECOFastPathDispatchProvider.Batch(1, small, List.of(), List.of(),
                Map.of(IRON, BigInteger.ONE, COPPER, BigInteger.ONE));
        assertThrows(IllegalArgumentException.class, () -> EcoMatterBatchAdapter.inputs(missing));
        var mismatch = new ECOFastPathDispatchProvider.Batch(1, small, List.of(), List.of(), Map.of(IRON, BigInteger.TWO));
        assertThrows(IllegalArgumentException.class, () -> EcoMatterBatchAdapter.inputs(mismatch));
    }

    @Test void duplicatedLongEntriesMustMatchExactTotalWithoutSaturation() {
        var valid = new ECOFastPathDispatchProvider.Batch(1,
                List.of(new GenericStack(IRON, 3_000_000_000L), new GenericStack(IRON, 5)), List.of(), List.of(),
                Map.of(IRON, BigInteger.valueOf(3_000_000_005L)));
        assertEquals(Map.of(IRON, 3_000_000_005L), EcoMatterBatchAdapter.inputs(valid));
        var overflow = new ECOFastPathDispatchProvider.Batch(1,
                List.of(new GenericStack(IRON, Long.MAX_VALUE), new GenericStack(IRON, 1)), List.of(), List.of());
        assertThrows(ArithmeticException.class, () -> EcoMatterBatchAdapter.inputs(overflow));
    }

    @Test void zeroAndNegativeExactInputsAreNotOwnedMaterials() {
        for (var amount : List.of(BigInteger.ZERO, BigInteger.valueOf(-1))) {
            var batch = new ECOFastPathDispatchProvider.Batch(1, List.of(), List.of(), List.of(), Map.of(IRON, amount));
            assertThrows(IllegalArgumentException.class, () -> EcoMatterBatchAdapter.inputs(batch));
        }
    }

    @Test void materializingBoundedLeaseLeavesRealExactInventoryExcessIntact() {
        var inventory = new ECOExactInventory(ignored -> {}); inventory.setEnabled(true);
        var original = BigInteger.ONE.shiftLeft(96).add(BigInteger.valueOf(123));
        inventory.restore(Map.of(IRON, original));
        var batch = new ECOFastPathDispatchProvider.Batch(1026, List.of(), List.of(), List.of(),
                Map.of(IRON, BigInteger.valueOf(3_078_000_000_000L)));
        var inputs = EcoMatterBatchAdapter.inputs(batch);
        assertTrue(inventory.debit(batch.exactInputTotal()));
        assertEquals(Map.of(IRON, 3_078_000_000_000L), inputs);
        assertEquals(original.subtract(batch.exactInputTotal().get(IRON)), inventory.amount(IRON));
        inventory.restore(batch.exactInputTotal());
        assertEquals(original, inventory.amount(IRON));
    }
}
