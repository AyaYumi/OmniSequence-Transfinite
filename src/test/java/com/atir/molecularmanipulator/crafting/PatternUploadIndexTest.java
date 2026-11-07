package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

/**
 * Execute the production comparison/capacity algorithms with real ItemStack components and AE2 inventories.
 * Registered AE2 patterns, grid admission and actual encode hooks are covered by the isolated GameTests instead.
 * The same event-maintained index is used by production; no source or bytecode-shape assertions are needed.
 */
class PatternUploadIndexTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void encoderOnlyCustomDataMatchesAStackWithNoCustomData() throws ReflectiveOperationException {
        var plain = new ItemStack(Items.MAP);
        var encoded = new ItemStack(Items.MAP);
        var tag = new CompoundTag();
        tag.putString("encodePlayer", "Different encoder");
        encoded.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(matches(plain, encoded));
        assertTrue(matches(encoded, plain));
        assertNull(withoutEncoder(encoded).get(DataComponents.CUSTOM_DATA),
                "Removing the only metadata key must not leave an empty CUSTOM_DATA component that breaks equality");
        assertNotNull(encoded.get(DataComponents.CUSTOM_DATA), "Normalization cannot mutate the real terminal item");
    }

    @Test void distinctEncodersAndPhysicalCountsDoNotCreateFalseNonDuplicates() throws ReflectiveOperationException {
        var stored = pattern("Alice", "minecraft:oak_planks", 32);
        var incoming = pattern("Bob", "minecraft:oak_planks", 1);
        var storedBefore = stored.copy();
        var incomingBefore = incoming.copy();
        assertTrue(matches(stored, incoming));
        assertTrue(matches(incoming, stored));
        assertTrue(ItemStack.matches(storedBefore, stored));
        assertTrue(ItemStack.matches(incomingBefore, incoming));
        assertEquals(32, stored.getCount());
        assertEquals("Alice", stored.get(DataComponents.CUSTOM_DATA).copyTag().getString("encodePlayer"));
        assertEquals("Bob", incoming.get(DataComponents.CUSTOM_DATA).copyTag().getString("encodePlayer"));
    }

    @Test void recipeDataAndAllNonEncoderComponentsRemainSignificant() throws ReflectiveOperationException {
        var original = pattern("Alice", "minecraft:oak_planks", 1);
        assertFalse(matches(original, pattern("Bob", "minecraft:birch_planks", 1)),
                "Different recipe payloads must never recycle a user's distinct pattern");
        var named = original.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this separate"));
        assertFalse(matches(original, named), "A custom name is not encoder metadata");
        var differentQuantity = original.copy();
        var data = differentQuantity.get(DataComponents.CUSTOM_DATA).copyTag();
        data.putLong("recipe_output_amount", 4_294_967_297L);
        differentQuantity.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        assertFalse(matches(original, differentQuantity), "Full long recipe data must not be dropped or truncated");
        assertTrue(ItemStack.matches(original, pattern("Alice", "minecraft:oak_planks", 1)));
    }

    @Test void onlyTheTopLevelEncoderKeyIsIgnoredAndOtherCustomDataIsPreserved() throws ReflectiveOperationException {
        var first = pattern("Alice", "minecraft:oak_planks", 1);
        var tag = first.get(DataComponents.CUSTOM_DATA).copyTag();
        var nested = new CompoundTag();
        nested.putString("encodePlayer", "part of recipe data, not the top-level encoder");
        tag.put("recipe_details", nested);
        first.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var second = first.copy();
        var changed = second.get(DataComponents.CUSTOM_DATA).copyTag();
        changed.getCompound("recipe_details").putString("encodePlayer", "a different recipe detail");
        second.set(DataComponents.CUSTOM_DATA, CustomData.of(changed));
        assertFalse(matches(first, second));
        var normalized = withoutEncoder(first);
        var normalizedTag = normalized.get(DataComponents.CUSTOM_DATA).copyTag();
        assertFalse(normalizedTag.contains("encodePlayer"));
        assertEquals("minecraft:oak_planks", normalizedTag.getString("recipe"));
        assertEquals(4_294_967_296L, normalizedTag.getLong("recipe_output_amount"));
        assertEquals(nested, normalizedTag.getCompound("recipe_details"));
    }

    @Test void emptyAndDifferentItemsAreNotDuplicates() throws ReflectiveOperationException {
        var stack = new ItemStack(Items.MAP);
        assertFalse(matches(ItemStack.EMPTY, stack));
        assertFalse(matches(stack, ItemStack.EMPTY));
        assertFalse(matches(ItemStack.EMPTY, ItemStack.EMPTY));
        assertFalse(matches(stack, new ItemStack(Items.PAPER)));
    }

    @Test void duplicateScanHonorsActiveSlotBoundaryWithoutMutatingStoredItems() throws ReflectiveOperationException {
        var inventory = new AppEngInternalInventory(3);
        var stored = pattern("Alice", "minecraft:oak_planks", 1);
        var incoming = pattern("Bob", "minecraft:oak_planks", 1);
        inventory.setItemDirect(0, pattern("Alice", "minecraft:birch_planks", 1));
        inventory.setItemDirect(2, stored.copy());
        assertFalse(contains(inventory, incoming, 0));
        assertFalse(contains(inventory, incoming, 2), "Inactive pages cannot trigger duplicate recycling");
        assertTrue(contains(inventory, incoming, 3));
        assertTrue(ItemStack.matches(stored, inventory.getStackInSlot(2)));
        assertTrue(inventory.getStackInSlot(1).isEmpty());
        assertEquals("Bob", incoming.get(DataComponents.CUSTOM_DATA).copyTag().getString("encodePlayer"));
    }

    @Test void capacityPreflightUsesRealAe2SimulationAndNeverWritesOrConsumesItems() throws ReflectiveOperationException {
        var inventory = new AppEngInternalInventory(2);
        inventory.setMaxStackSize(0, 1);
        inventory.setMaxStackSize(1, 1);
        var occupied = new ItemStack(Items.PAPER);
        var incoming = pattern("Alice", "minecraft:oak_planks", 1);
        var before = incoming.copy();
        inventory.setItemDirect(0, occupied.copy());
        assertFalse(canInsert(inventory, incoming, 1), "An empty inactive slot is not capacity");
        assertTrue(canInsert(inventory, incoming, 2), "One active free slot fits exactly one real item");
        assertFalse(canInsert(inventory, incoming.copyWithCount(2), 2), "Simulation must account for the complete remainder");
        assertTrue(ItemStack.matches(occupied, inventory.getStackInSlot(0)));
        assertTrue(inventory.getStackInSlot(1).isEmpty(), "Preflight must not stage a real item");
        assertTrue(ItemStack.matches(before, incoming), "The caller still owns its unchanged physical item");
    }

    private static ItemStack pattern(String encoder, String recipe, int count) {
        // Vanilla carrier avoids requiring mod registry bootstrap in a plain JUnit JVM.
        var stack = new ItemStack(Items.MAP, count);
        var tag = new CompoundTag();
        tag.putString("encodePlayer", encoder);
        tag.putString("recipe", recipe);
        tag.putLong("recipe_output_amount", 4_294_967_296L);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static boolean matches(ItemStack stored, ItemStack incoming) {
        var key = PatternUploadIndex.keyOf(stored);
        return key != null && key.equals(PatternUploadIndex.keyOf(incoming));
    }

    private static ItemStack withoutEncoder(ItemStack stack) {
        return PatternUploadIndex.withoutEncoder(stack);
    }

    private static boolean contains(InternalInventory inventory, ItemStack stack, int slots) {
        return new PatternUploadIndex().contains(inventory, slots, stack);
    }

    private static boolean canInsert(InternalInventory inventory, ItemStack stack, int slots) {
        int slot = new PatternUploadIndex().firstEmptySlot(inventory, slots);
        return slot >= 0 && inventory.insertItem(slot, stack.copy(), true).isEmpty();
    }

    @Test void repeatedQueriesAndSingleSlotChangesNeverRescanTheLibrary() {
        var inventory = new CountingInventory(10_800);
        var index = new PatternUploadIndex();
        var stack = pattern("Alice", "minecraft:oak_planks", 1);
        inventory.setItemDirect(0, stack.copy());
        inventory.setItemDirect(1, stack.copy());
        assertTrue(index.contains(inventory, 10_800, stack));
        assertEquals(10_800, inventory.reads, "One lazy initial scan, not a per-query scan");
        inventory.reads = 0;
        for (int i = 0; i < 1_000; i++) {
            assertTrue(index.contains(inventory, 10_800, stack));
            assertEquals(2, index.firstEmptySlot(inventory, 10_800));
        }
        assertEquals(0, inventory.reads, "Repeated duplicate/space checks read no inventory slots");
        inventory.setItemDirect(0, ItemStack.EMPTY);
        index.slotChanged(inventory, 0);
        assertTrue(index.contains(inventory, 10_800, stack), "The other physical duplicate remains indexed");
        assertEquals(0, index.firstEmptySlot(inventory, 10_800));
        inventory.setItemDirect(1, ItemStack.EMPTY);
        index.slotChanged(inventory, 1);
        assertFalse(index.contains(inventory, 10_800, stack));
        assertEquals(2, inventory.reads, "Only the two changed slots were read");
        index.invalidate();
        assertFalse(index.contains(inventory, 10_800, stack));
        assertEquals(10_802, inventory.reads, "Explicit NBT invalidation rebuilds on demand");
    }

    @Test void liveCapacityChangesAndReplacedInventoriesCannotUseAnOldIndex() {
        var inventory = new CountingInventory(3);
        var index = new PatternUploadIndex();
        var stack = pattern("Alice", "minecraft:oak_planks", 1);
        inventory.setItemDirect(2, stack);
        assertTrue(index.contains(inventory, 3, stack));
        assertFalse(index.contains(inventory, 2, stack));
        assertTrue(index.contains(inventory, 3, stack));
        assertFalse(index.contains(new AppEngInternalInventory(3), 3, stack));
    }

    private static final class CountingInventory extends AppEngInternalInventory {
        int reads;
        CountingInventory(int size) { super(size); }
        @Override public ItemStack getStackInSlot(int slot) {
            reads++;
            return super.getStackInSlot(slot);
        }
    }
}
