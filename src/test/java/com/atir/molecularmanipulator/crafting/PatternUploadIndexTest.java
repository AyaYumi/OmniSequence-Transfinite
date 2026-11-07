package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.ForgeTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Executes the production lazy index with AE2 15 keys and real Forge ItemStack NBT. */
class PatternUploadIndexTest {
    static {
        try { ForgeTestBootstrap.bootstrapMinecraft(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
        ForgeTestBootstrap.bootstrapAeKeyTypes();
    }

    @Test void removingTheOnlyEncoderKeyCanonicalizesEmptyTagToNoTag() {
        var plain = new ItemStack(Items.MAP);
        var encoded = plain.copy();
        encoded.getOrCreateTag().putString("encodePlayer", "Different encoder");
        var physicalKey = AEItemKey.of(encoded);
        assertTrue(matches(plain, encoded));
        assertTrue(matches(encoded, plain));
        assertNull(PatternUploadIndex.withoutEncoder(encoded).getTag());
        assertEquals("Different encoder", encoded.getTag().getString("encodePlayer"));
        assertEquals("Different encoder", physicalKey.getTag().getString("encodePlayer"),
                "An interned AEItemKey tag is immutable even while used as an index key");
    }

    @Test void encoderAndPhysicalCountDoNotChangeDuplicateIdentityOrMutateEitherItem() {
        var stored = pattern("Alice", "minecraft:oak_planks", 32);
        var incoming = pattern("Bob", "minecraft:oak_planks", 1);
        var storedBefore = stored.copy();
        var incomingBefore = incoming.copy();
        assertTrue(matches(stored, incoming));
        assertTrue(matches(incoming, stored));
        assertTrue(ItemStack.matches(storedBefore, stored));
        assertTrue(ItemStack.matches(incomingBefore, incoming));
        assertEquals(32, stored.getCount());
        assertEquals("Alice", stored.getTag().getString("encodePlayer"));
        assertEquals("Bob", incoming.getTag().getString("encodePlayer"));
    }

    @Test void recipesNamesLongQuantitiesAndAllNonEncoderNbtRemainSignificant() {
        var original = pattern("Alice", "minecraft:oak_planks", 1);
        assertFalse(matches(original, pattern("Bob", "minecraft:birch_planks", 1)));
        var named = original.copy();
        named.setHoverName(Component.literal("Keep this separate"));
        assertFalse(matches(original, named));
        var differentQuantity = original.copy();
        differentQuantity.getOrCreateTag().putLong("recipe_output_amount", 4_294_967_297L);
        assertFalse(matches(original, differentQuantity));
        var payload = original.copy();
        payload.getOrCreateTag().putByteArray("addon_payload", new byte[] {1, 0, 3});
        assertFalse(matches(original, payload));
    }

    @Test void onlyTheTopLevelEncoderKeyIsIgnoredAndNestingIsPreserved() {
        var first = pattern("Alice", "minecraft:oak_planks", 1);
        var nested = new CompoundTag();
        nested.putString("encodePlayer", "recipe data, not the terminal encoder");
        first.getOrCreateTag().put("recipe_details", nested);
        var second = first.copy();
        second.getTag().getCompound("recipe_details").putString("encodePlayer", "another recipe detail");
        assertFalse(matches(first, second));
        var normalized = PatternUploadIndex.withoutEncoder(first);
        assertFalse(normalized.getTag().contains("encodePlayer"));
        assertEquals("minecraft:oak_planks", normalized.getTag().getString("recipe"));
        assertEquals(4_294_967_296L, normalized.getTag().getLong("recipe_output_amount"));
        assertEquals(nested, normalized.getTag().getCompound("recipe_details"));
        assertTrue(first.getTag().contains("encodePlayer"));
    }

    @Test void emptyAndDifferentItemsAreNotDuplicates() {
        var stack = new ItemStack(Items.MAP);
        assertFalse(matches(ItemStack.EMPTY, stack));
        assertFalse(matches(stack, ItemStack.EMPTY));
        assertFalse(matches(ItemStack.EMPTY, ItemStack.EMPTY));
        assertFalse(matches(stack, new ItemStack(Items.PAPER)));
    }

    @Test void activeSlotBoundaryExcludesInactivePagesAndAllQueriesAreReadOnly() {
        var inventory = new AppEngInternalInventory(3);
        var index = new PatternUploadIndex();
        var stored = pattern("Alice", "minecraft:oak_planks", 1);
        var incoming = pattern("Bob", "minecraft:oak_planks", 1);
        inventory.setItemDirect(0, pattern("Alice", "minecraft:birch_planks", 1));
        inventory.setItemDirect(2, stored.copy());
        assertFalse(index.contains(inventory, 0, incoming));
        assertFalse(index.contains(inventory, 2, incoming));
        assertTrue(index.contains(inventory, 3, incoming));
        assertEquals(-1, index.firstEmptySlot(inventory, -1));
        assertEquals(1, index.firstEmptySlot(inventory, Integer.MAX_VALUE));
        assertTrue(ItemStack.matches(stored, inventory.getStackInSlot(2)));
        assertTrue(inventory.getStackInSlot(1).isEmpty());
        assertEquals("Bob", incoming.getTag().getString("encodePlayer"));
    }

    @Test void preflightUsesRealAe2SimulationWithoutStagingOrSpending() {
        var inventory = new AppEngInternalInventory(2);
        inventory.setMaxStackSize(0, 1);
        inventory.setMaxStackSize(1, 1);
        var occupied = new ItemStack(Items.PAPER);
        var incoming = pattern("Alice", "minecraft:oak_planks", 1);
        var before = incoming.copy();
        inventory.setItemDirect(0, occupied.copy());
        var index = new PatternUploadIndex();
        assertEquals(-1, index.firstEmptySlot(inventory, 1));
        int slot = index.firstEmptySlot(inventory, 2);
        assertEquals(1, slot);
        assertTrue(inventory.insertItem(slot, incoming.copy(), true).isEmpty());
        assertFalse(inventory.insertItem(slot, incoming.copyWithCount(2), true).isEmpty());
        assertTrue(ItemStack.matches(occupied, inventory.getStackInSlot(0)));
        assertTrue(inventory.getStackInSlot(1).isEmpty());
        assertTrue(ItemStack.matches(before, incoming));
    }

    @Test void repeatedQueriesAndSingleSlotChangesNeverRescanTheLibrary() {
        var inventory = new CountingInventory(10_800);
        var index = new PatternUploadIndex();
        var stack = pattern("Alice", "minecraft:oak_planks", 1);
        inventory.setItemDirect(0, stack.copy());
        inventory.setItemDirect(1, stack.copy());
        assertTrue(index.contains(inventory, 10_800, stack));
        assertEquals(10_800, inventory.reads);
        inventory.reads = 0;
        for (int i = 0; i < 1_000; i++) {
            assertTrue(index.contains(inventory, 10_800, stack));
            assertEquals(2, index.firstEmptySlot(inventory, 10_800));
        }
        assertEquals(0, inventory.reads);
        inventory.setItemDirect(0, ItemStack.EMPTY);
        index.slotChanged(inventory, 0);
        assertTrue(index.contains(inventory, 10_800, stack));
        assertEquals(0, index.firstEmptySlot(inventory, 10_800));
        inventory.setItemDirect(1, ItemStack.EMPTY);
        index.slotChanged(inventory, 1);
        assertFalse(index.contains(inventory, 10_800, stack));
        assertEquals(2, inventory.reads);
        index.invalidate();
        assertFalse(index.contains(inventory, 10_800, stack));
        assertEquals(10_802, inventory.reads);
    }

    @Test void capacityChangesReplacedInventoriesAndRollbackDoNotUseStaleKeys() {
        var inventory = new CountingInventory(3);
        var index = new PatternUploadIndex();
        var stack = pattern("Alice", "minecraft:oak_planks", 1);
        inventory.setItemDirect(2, stack.copy());
        assertTrue(index.contains(inventory, 3, stack));
        assertFalse(index.contains(inventory, 2, stack));
        assertTrue(index.contains(inventory, 3, stack));
        inventory.setItemDirect(2, ItemStack.EMPTY);
        index.slotChanged(inventory, 2);
        assertFalse(index.contains(inventory, 3, stack));
        inventory.setItemDirect(2, stack.copy());
        index.slotChanged(inventory, 2);
        assertTrue(index.contains(inventory, 3, stack));
        assertFalse(index.contains(new AppEngInternalInventory(3), 3, stack));
    }

    @Test void failedLazyReadCannotLeaveAPartiallyBuiltIndex() {
        var failRead = new boolean[] {true};
        var inventory = new AppEngInternalInventory(2) {
            @Override public ItemStack getStackInSlot(int slot) {
                if (failRead[0] && slot == 1) throw new IllegalStateException("Temporary inventory read failure");
                return super.getStackInSlot(slot);
            }
        };
        var stack = pattern("Alice", "minecraft:oak_planks", 1);
        inventory.setItemDirect(0, stack.copy());
        inventory.setItemDirect(1, stack.copy());
        var index = new PatternUploadIndex();
        assertThrows(IllegalStateException.class, () -> index.contains(inventory, 2, stack));
        failRead[0] = false;
        assertTrue(index.contains(inventory, 2, stack));
        inventory.setItemDirect(0, ItemStack.EMPTY);
        index.slotChanged(inventory, 0);
        assertTrue(index.contains(inventory, 2, stack), "The second duplicate must be present after the retried rebuild");
        assertEquals(0, index.firstEmptySlot(inventory, 2));
    }

    private static ItemStack pattern(String encoder, String recipe, int count) {
        var stack = new ItemStack(Items.MAP, count);
        var tag = new CompoundTag();
        tag.putString("encodePlayer", encoder);
        tag.putString("recipe", recipe);
        tag.putLong("recipe_output_amount", 4_294_967_296L);
        stack.setTag(tag);
        return stack;
    }

    private static boolean matches(ItemStack stored, ItemStack incoming) {
        var key = PatternUploadIndex.keyOf(stored);
        return key != null && key.equals(PatternUploadIndex.keyOf(incoming));
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
