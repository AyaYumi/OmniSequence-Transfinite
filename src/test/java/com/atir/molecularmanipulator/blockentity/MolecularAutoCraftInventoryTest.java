package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.execution.CraftingCpuHelper;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MolecularAutoCraftInventoryTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final AEKey LOG = AEItemKey.of(Items.OAK_LOG);

    @Test
    void repeatedPatternsUseSharedIndexInsteadOfEnumeratingMountedStorage() {
        var stock = new Stock();
        var index = new KeyCounter();
        index.add(LOG, 100);
        stock.amounts.put(LOG, 100);
        for (int slot = 0; slot < 9; slot++) {
            var inventory = inventory(stock, index, LOG, 0);
            for (int query = 0; query < 9; query++) {
                assertEquals(List.of(LOG), candidates(inventory.findFuzzyTemplates(LOG)));
            }
        }
        assertEquals(0, stock.enumerations);
        assertEquals(100, stock.amounts.getLong(LOG));
    }

    @Test
    void exactKeyInsertedAfterIndexRefreshIsAvailableImmediately() {
        var stock = new Stock();
        stock.amounts.put(LOG, 12);
        var inventory = inventory(stock, new KeyCounter(), LOG, 5);
        assertEquals(List.of(LOG), candidates(inventory.findFuzzyTemplates(LOG)));
        assertEquals(7, inventory.extract(LOG, 100, Actionable.MODULATE));
        assertEquals(5, stock.amounts.getLong(LOG));
    }

    @Test
    void staleHighIndexCountCannotSpendProtectedOrAlreadyConsumedInputs() {
        var stock = new Stock();
        stock.amounts.put(LOG, 12);
        var index = new KeyCounter();
        index.add(LOG, 1_000_000);
        var inventory = inventory(stock, index, LOG, 5);
        assertEquals(7, inventory.extract(LOG, 100, Actionable.SIMULATE));
        assertEquals(12, stock.amounts.getLong(LOG));
        assertEquals(7, inventory.extract(LOG, 100, Actionable.MODULATE));
        assertEquals(0, inventory.extract(LOG, 100, Actionable.MODULATE));
        assertEquals(5, stock.amounts.getLong(LOG));
        assertEquals(1_000_000, index.get(LOG));
    }

    @Test
    void staleLowIndexCountDoesNotHideLiveStockAboveReserve() {
        var stock = new Stock();
        stock.amounts.put(LOG, 100);
        var index = new KeyCounter();
        index.add(LOG, 1);
        var inventory = inventory(stock, index, LOG, 20);
        assertEquals(List.of(LOG), candidates(inventory.findFuzzyTemplates(LOG)));
        assertEquals(80, inventory.extract(LOG, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(20, stock.amounts.getLong(LOG));
    }

    @Test
    void fuzzyVariantsRemainAvailableAndAe2StillValidatesEachCandidate() {
        var fresh = AEItemKey.of(Items.DIAMOND_PICKAXE);
        var stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setDamageValue(7);
        var damaged = AEItemKey.of(stack);
        var stock = new Stock();
        stock.amounts.put(damaged, 3);
        var index = new KeyCounter();
        index.add(damaged, 3);
        index.add(LOG, 100);
        var input = input(fresh);
        var inventory = new MolecularAutoCrafter.ProtectedNetworkCraftingInventory(
                stock, () -> index, IActionSource.empty(), new IPatternDetails.IInput[]{input},
                new long[]{0}, null, (key, amount) -> fail("Unexpected refund"));
        assertEquals(List.of(fresh, damaged), candidates(inventory.findFuzzyTemplates(fresh)));
        var selected = new ArrayList<AEKey>();
        for (var template : CraftingCpuHelper.getValidItemTemplates(inventory, input, null)) {
            selected.add(template.key());
        }
        assertEquals(List.of(fresh), selected, "Fuzzy membership cannot bypass recipe validity");
        assertEquals(0, stock.enumerations);
    }

    @Test
    void acceptedFuzzyVariantUsesLiveReserveEvenWhenSharedIndexOverstatesStock() {
        var fresh = AEItemKey.of(Items.DIAMOND_PICKAXE);
        var stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setDamageValue(7);
        var damaged = AEItemKey.of(stack);
        var stock = new Stock();
        stock.amounts.put(damaged, 12);
        var index = new KeyCounter();
        index.add(damaged, 1_000_000);
        var input = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(fresh, 1)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey candidate, Level level) { return damaged.equals(candidate); }
            @Override public AEKey getRemainingKey(AEKey template) { return null; }
        };
        var inventory = new MolecularAutoCrafter.ProtectedNetworkCraftingInventory(
                stock, () -> index, IActionSource.empty(), new IPatternDetails.IInput[]{input},
                new long[]{5}, null, (key, amount) -> fail("Unexpected refund"));
        long extracted = 0;
        for (var template : CraftingCpuHelper.getValidItemTemplates(inventory, input, null)) {
            assertEquals(damaged, template.key());
            extracted += CraftingCpuHelper.extractTemplates(inventory, template, 100);
        }
        assertEquals(7, extracted);
        assertEquals(0, stock.amounts.getLong(fresh));
        assertEquals(5, stock.amounts.getLong(damaged));
        assertEquals(1_000_000, index.get(damaged));
        assertEquals(0, stock.enumerations);
    }

    @Test
    void indexRefreshAndLiveExtractionRemainVisibleToAnExistingWrapper() {
        var stock = new Stock();
        var index = new KeyCounter();
        var inventory = inventory(stock, index, LOG, 0);
        assertEquals(0, inventory.extract(LOG, 1, Actionable.MODULATE));
        stock.amounts.put(LOG, 4);
        index.add(LOG, 4);
        assertEquals(List.of(LOG), candidates(inventory.findFuzzyTemplates(LOG)));
        assertEquals(4, inventory.extract(LOG, 4, Actionable.MODULATE));
        assertEquals(4, index.get(LOG), "Never mutate AE2's shared counter");
    }

    @Test
    void unavailableIndexStillAllowsExactLiveInputsWithoutAFullScan() {
        var stock = new Stock();
        stock.amounts.put(LOG, 10);
        var inventory = new MolecularAutoCrafter.ProtectedNetworkCraftingInventory(
                stock, () -> { throw new IllegalStateException("Listing unavailable"); },
                IActionSource.empty(), new IPatternDetails.IInput[]{input(LOG)}, new long[]{2},
                null, (key, amount) -> fail("Unexpected refund"));
        assertEquals(List.of(LOG), candidates(inventory.findFuzzyTemplates(LOG)));
        assertEquals(8, inventory.extract(LOG, 10, Actionable.MODULATE));
        assertEquals(0, stock.enumerations);
    }

    @Test
    void partialInputRefundStillTransfersOnlyUnacceptedItemsToEscrow() {
        var stock = new Stock();
        stock.insertionLimit = 3;
        var refunds = new Object2LongOpenHashMap<AEKey>();
        var inventory = new MolecularAutoCrafter.ProtectedNetworkCraftingInventory(
                stock, KeyCounter::new, IActionSource.empty(), new IPatternDetails.IInput[]{input(LOG)},
                new long[]{0}, null, (key, amount) -> refunds.addTo(key, amount));
        inventory.insert(LOG, 10, Actionable.SIMULATE);
        assertEquals(0, stock.amounts.getLong(LOG));
        inventory.insert(LOG, 10, Actionable.MODULATE);
        assertEquals(3, stock.amounts.getLong(LOG));
        assertEquals(7, refunds.getLong(LOG));
    }

    private static MolecularAutoCrafter.ProtectedNetworkCraftingInventory inventory(
            Stock stock, KeyCounter index, AEKey key, long reserve) {
        return new MolecularAutoCrafter.ProtectedNetworkCraftingInventory(stock, () -> index,
                IActionSource.empty(), new IPatternDetails.IInput[]{input(key)}, new long[]{reserve},
                null, (what, amount) -> fail("Unexpected refund"));
    }

    private static IPatternDetails.IInput input(AEKey key) {
        return new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(key, 1)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey candidate, Level level) { return key.equals(candidate); }
            @Override public AEKey getRemainingKey(AEKey template) { return null; }
        };
    }

    private static List<AEKey> candidates(Iterable<AEKey> values) {
        var result = new ArrayList<AEKey>();
        values.forEach(result::add);
        return result;
    }

    private static final class Stock implements MEStorage {
        final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        int enumerations;
        long insertionLimit = Long.MAX_VALUE;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, insertionLimit);
            if (mode == Actionable.MODULATE) amounts.addTo(key, accepted);
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, amounts.getLong(key));
            if (mode == Actionable.MODULATE) amounts.addTo(key, -accepted);
            return accepted;
        }

        @Override
        public void getAvailableStacks(KeyCounter counter) {
            enumerations++;
            fail("Passive pattern must use AE2's shared candidate index");
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("Auto-crafter indexed inventory test");
        }
    }
}
