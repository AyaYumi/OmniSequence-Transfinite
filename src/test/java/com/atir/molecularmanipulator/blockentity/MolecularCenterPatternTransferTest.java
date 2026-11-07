package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.ForgeTestBootstrap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Real AE2 physical slots and Forge NBT; crafting-detail definitions are never used as items. */
class MolecularCenterPatternTransferTest {
    static {
        try { ForgeTestBootstrap.bootstrapMinecraft(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
        ForgeTestBootstrap.bootstrapAeKeyTypes();
    }

    @Test void movesExactlyOnePhysicalItemWithAllNbtAndCount() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(3));
        assertTrue(move(source, target));
        assertTrue(ItemStack.matches(pattern(2), source.getStackInSlot(0)));
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(0)));
        assertTrue(ItemStack.matches(pattern(1), ItemStack.of(target.getStackInSlot(0).save(new CompoundTag()))));
        assertEquals(1, source.extractions);
        assertEquals(1, target.insertions);
        assertEquals(3, source.getStackInSlot(0).getCount() + target.getStackInSlot(0).getCount());
    }

    @Test void lastPatternAndRepeatedCallsCannotDuplicateIt() {
        var source = new Inventory(1);
        var target = new Inventory(2);
        source.setItemDirect(0, pattern(1));
        assertTrue(move(source, target));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 1, () -> true));
        assertTrue(source.isEmpty());
        assertTrue(target.getStackInSlot(1).isEmpty());
        assertEquals(1, source.extractions);
    }

    @Test void duplicatePhysicalItemsRemainSeparateAndPartialProgressConservesEverything() {
        var source = new Inventory(3);
        var target = new Inventory(2);
        for (int slot = 0; slot < 3; slot++) source.setItemDirect(slot, pattern(1));
        int moved = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (MolecularCenterPatternTransfer.fromInventory(source, slot, target, slot, () -> true)) moved++;
        }
        assertEquals(2, moved);
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertTrue(source.getStackInSlot(1).isEmpty());
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(2)));
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(0)));
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(1)));
    }

    @Test void fullOrInvalidTargetAndSameInventoryNeverExtract() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        target.setItemDirect(0, new ItemStack(Items.DIAMOND));
        assertFalse(move(source, target));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, source, 0, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, -1, target, 0, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 1, target, 0, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, -1, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 1, () -> true));
        assertEquals(0, source.extractions);
        assertEquals(0, target.insertions);
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.getStackInSlot(0).is(Items.DIAMOND));
    }

    @Test void readOnlyRecipeRowsCannotBeCopiedOrCleared() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        source.rejectExtractionSimulation = true;
        assertUnchanged(source, target);
        assertEquals(0, source.extractions);
        assertEquals(0, target.insertions);
    }

    @Test void targetSimulationAndCommitRejectionsDoNotSpendTheSource() {
        for (boolean simulation : List.of(false, true)) {
            var source = new Inventory(1);
            var target = new Inventory(1);
            source.setItemDirect(0, pattern(1));
            target.rejectInsertSimulation = simulation;
            target.rejectInsertCommit = !simulation;
            assertUnchanged(source, target);
            assertEquals(0, source.extractions);
        }
    }

    @Test void failedSourceCommitRollsBackTheStagedTarget() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        source.rejectExtractionCommit = true;
        assertUnchanged(source, target);
        assertEquals(1, target.insertions, "Stage before spending the source item");
    }

    @Test void targetExceptionAfterWritingIsUndoneBeforeExtraction() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        target.throwAfterInsert = true;
        assertUnchanged(source, target);
        assertEquals(0, source.extractions);
    }

    @Test void sourceExceptionBeforeOrAfterRemovalRestoresBothInventories() {
        for (boolean after : List.of(false, true)) {
            var source = new Inventory(1);
            var target = new Inventory(1);
            source.setItemDirect(0, pattern(1));
            source.throwBeforeExtract = !after;
            source.throwAfterExtract = after;
            assertUnchanged(source, target);
        }
    }

    @Test void wrongReturnedItemOrOverExtractionRestoresTheOriginalStack() {
        for (boolean wrongItem : List.of(false, true)) {
            var source = new Inventory(1);
            var target = new Inventory(1);
            source.setItemDirect(0, pattern(3));
            source.wrongExtractionItem = wrongItem;
            source.overExtract = !wrongItem;
            assertFalse(move(source, target));
            assertTrue(ItemStack.matches(pattern(3), source.getStackInSlot(0)));
            assertTrue(target.isEmpty());
        }
    }

    @Test void ownershipOrGridGuardChangingAfterStagingRollsBackWithoutExtracting() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        var writable = new AtomicBoolean(true);
        source.setItemDirect(0, pattern(1));
        target.afterInsert = () -> writable.set(false);
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, writable::get));
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.isEmpty());
        assertEquals(0, source.extractions);
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, () -> false));
        assertEquals(1, target.insertions);
    }

    @Test void addonCellUsesOnlyExactKeyQueriesAndPreservesNbtAboveIntCounts() {
        var cell = new AddonCell();
        cell.count = 4_294_967_299L;
        var target = new Inventory(3);
        for (int slot = 0; slot < 3; slot++) {
            assertTrue(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                    cell.source, target, slot, () -> true));
            assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(slot)));
        }
        assertEquals(4_294_967_296L, cell.count);
        assertEquals(3, cell.extractions);
    }

    @Test void addonCellCallbackThrowingAfterExtractionStillCommitsOnePhysicalDecrement() {
        var cell = new AddonCell();
        cell.throwAfterExtract = true;
        var target = new Inventory(1);
        assertTrue(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                cell.source, target, 0, () -> true));
        assertEquals(2, cell.count);
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(0)));
        assertEquals(1, cell.extractions);
    }

    @Test void creativeOrRejectedCellCannotInventAPhysicalPattern() {
        for (int mode = 0; mode < 3; mode++) {
            var cell = new AddonCell();
            cell.creative = mode == 0;
            cell.rejectCommit = mode == 1;
            cell.throwBeforeExtract = mode == 2;
            var target = new Inventory(1);
            assertFalse(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                    cell.source, target, 0, () -> true));
            assertEquals(3, cell.count);
            assertTrue(target.isEmpty());
        }
    }

    @Test void cellIsNotExtractedWhenStagingFailsOrOwnershipChanges() {
        for (boolean ownership : List.of(false, true)) {
            var cell = new AddonCell();
            var target = new Inventory(1);
            var writable = new AtomicBoolean(true);
            target.throwAfterInsert = !ownership;
            if (ownership) target.afterInsert = () -> writable.set(false);
            assertFalse(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                    cell.source, target, 0, writable::get));
            assertEquals(3, cell.count);
            assertEquals(0, cell.extractions);
            assertTrue(target.isEmpty());
        }
    }

    private static boolean move(Inventory source, Inventory target) {
        return MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, () -> true);
    }

    private static void assertUnchanged(Inventory source, Inventory target) {
        assertFalse(move(source, target));
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.isEmpty());
    }

    private static ItemStack pattern(int count) {
        var stack = new ItemStack(Items.MAP, count);
        stack.setHoverName(Component.literal("Exact physical pattern NBT"));
        var tag = stack.getOrCreateTag();
        tag.putString("encodePlayer", "Keep even the encoder during physical migration");
        tag.putLong("recipe_output_amount", 4_294_967_297L);
        tag.putIntArray("recipe_input_slots", new int[] {0, 4, 8});
        return stack;
    }

    private static final class AddonCell implements StorageCell {
        final AEItemKey key = AEItemKey.of(pattern(1));
        final IActionSource source = IActionSource.empty();
        long count = 3;
        int extractions;
        boolean creative, rejectCommit, throwBeforeExtract, throwAfterExtract;
        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            assertSame(this.source, source);
            if (!key.equals(what)) return 0;
            long extracted = Math.min(count, amount);
            if (mode == Actionable.MODULATE) {
                extractions++;
                if (throwBeforeExtract) throw new IllegalStateException("Before removal");
                if (rejectCommit) return 0;
                if (!creative) count -= extracted;
                if (throwAfterExtract) throw new IllegalStateException("Addon save callback after removal");
            }
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) {
            throw new AssertionError("Per-item transfers query one key, never enumerate a whole disk");
        }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            throw new AssertionError("A rejected transfer must not trust a voiding/filtered refund path");
        }
        @Override public CellState getStatus() { return count == 0 ? CellState.EMPTY : CellState.NOT_EMPTY; }
        @Override public double getIdleDrain() { return 0; }
        @Override public void persist() {}
        @Override public Component getDescription() { return Component.literal("Unknown addon StorageCell"); }
    }

    private static final class Inventory extends AppEngInternalInventory {
        int insertions, extractions;
        boolean rejectInsertSimulation, rejectInsertCommit, rejectExtractionSimulation, rejectExtractionCommit;
        boolean throwAfterInsert, throwBeforeExtract, throwAfterExtract, wrongExtractionItem, overExtract;
        Runnable afterInsert = () -> {};
        Inventory(int size) { super(null, size); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate ? rejectInsertSimulation : rejectInsertCommit) return stack;
            var result = super.insertItem(slot, stack, simulate);
            if (!simulate) {
                insertions++;
                afterInsert.run();
                if (throwAfterInsert) throw new IllegalStateException("Target callback failed after writing");
            }
            return result;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (simulate ? rejectExtractionSimulation : rejectExtractionCommit) return ItemStack.EMPTY;
            if (!simulate) {
                extractions++;
                if (throwBeforeExtract) throw new IllegalStateException("Source rejected before removal");
            }
            var result = super.extractItem(slot, !simulate && overExtract ? amount + 1 : amount, simulate);
            if (!simulate && throwAfterExtract) throw new IllegalStateException("Source callback failed after removal");
            return !simulate && wrongExtractionItem ? new ItemStack(Items.DIRT) : result;
        }
    }
}
