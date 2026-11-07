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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Real AE2 slotted inventories and vanilla components; no running server/AE2 item registration needed. */
class MolecularCenterPatternTransferTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void movesExactlyOnePhysicalItemWithAllComponents() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(3));
        assertTrue(move(source, target));
        assertTrue(ItemStack.matches(pattern(2), source.getStackInSlot(0)));
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(0)));
        assertEquals(1, source.extractions);
        assertEquals(1, target.insertions);
        assertEquals(3, source.getStackInSlot(0).getCount() + target.getStackInSlot(0).getCount());
    }

    @Test void lastPatternTransfersAndRepeatedCallsDoNotDuplicateIt() {
        var source = new Inventory(1);
        var target = new Inventory(2);
        source.setItemDirect(0, pattern(1));
        assertTrue(move(source, target));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 1, () -> true));
        assertTrue(source.isEmpty());
        assertTrue(target.getStackInSlot(1).isEmpty());
        assertEquals(1, source.extractions);
    }

    @Test void identicalPhysicalPatternsOccupySeparateSlotsInsteadOfBeingSynthesizedOrCollapsed() {
        var source = new Inventory(2);
        var target = new Inventory(2);
        source.setItemDirect(0, pattern(1)); source.setItemDirect(1, pattern(1));
        assertTrue(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, () -> true));
        assertTrue(MolecularCenterPatternTransfer.fromInventory(source, 1, target, 1, () -> true));
        assertTrue(source.isEmpty());
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(0)));
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(1)));
    }

    @Test void fullTargetIsUntouchedAndDoesNotExtractFromTheProvider() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1)); target.setItemDirect(0, new ItemStack(Items.DIAMOND));
        assertFalse(move(source, target));
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.getStackInSlot(0).is(Items.DIAMOND));
        assertEquals(0, source.extractions);
        assertEquals(0, target.insertions);
    }

    @Test void sameInventoryAndOutOfBoundsSlotsCannotMoveAnything() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, source, 0, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, -1, target, 0, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 1, target, 0, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, -1, () -> true));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 1, () -> true));
        assertEquals(0, source.extractions);
        assertTrue(target.isEmpty());
    }

    @Test void sourceSimulationRejectionCannotCreateAFreeTargetPattern() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1)); source.rejectExtractionSimulation = true;
        assertUnchanged(source, target);
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
        source.setItemDirect(0, pattern(1)); source.rejectExtractionCommit = true;
        assertUnchanged(source, target);
        assertEquals(1, target.insertions, "Target must be staged before spending a source item");
    }

    @Test void targetExceptionAfterWritingIsUndoneBeforeAnySourceExtraction() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1)); target.throwAfterInsert = true;
        assertUnchanged(source, target);
        assertEquals(0, source.extractions);
    }

    @Test void sourceExceptionBeforeOrAfterRemovalRestoresBothInventories() {
        for (boolean after : List.of(false, true)) {
            var source = new Inventory(1);
            var target = new Inventory(1);
            source.setItemDirect(0, pattern(1));
            source.throwBeforeExtract = !after; source.throwAfterExtract = after;
            assertUnchanged(source, target);
        }
    }

    @Test void invalidSourceResultOrOverExtractionRestoresTheOriginalStack() {
        for (boolean wrongItem : List.of(false, true)) {
            var source = new Inventory(1);
            var target = new Inventory(1);
            source.setItemDirect(0, pattern(3));
            source.wrongExtractionItem = wrongItem; source.overExtract = !wrongItem;
            assertFalse(move(source, target));
            assertTrue(ItemStack.matches(pattern(3), source.getStackInSlot(0)));
            assertTrue(target.isEmpty());
        }
    }

    @Test void ownershipOrGridGuardChangingAfterStagingRollsBackWithoutExtracting() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        var writable = new AtomicBoolean(true);
        source.setItemDirect(0, pattern(1)); target.afterInsert = () -> writable.set(false);
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, writable::get));
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.isEmpty());
        assertEquals(0, source.extractions);
    }

    @Test void anAlreadyLockedTargetIsNeverModified() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, () -> false));
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.isEmpty());
        assertEquals(0, source.extractions);
    }

    @Test void partialProgressAtCapacityConservesEveryPattern() {
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

    @Test void readOnlyTerminalRowsCannotBeCopiedOrCleared() {
        var source = new Inventory(1);
        var target = new Inventory(1);
        source.setItemDirect(0, pattern(1));
        source.rejectExtractionSimulation = true;
        assertFalse(MolecularCenterPatternTransfer.fromInventory(source, 0, target, 0, () -> true));
        assertTrue(ItemStack.matches(pattern(1), source.getStackInSlot(0)));
        assertTrue(target.isEmpty());
        assertEquals(0, source.extractions);
        assertEquals(0, target.insertions);
    }

    @Test void addonStorageCellUsesPublicInterfaceAndExactKeyQueriesWithoutEnumerating() {
        var cell = new AddonCell();
        var target = new Inventory(3);
        for (int slot = 0; slot < 3; slot++) {
            assertTrue(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                    IActionSource.empty(), target, slot, () -> true));
            assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(slot)));
        }
        assertEquals(0, cell.count);
        assertEquals(3, cell.extractions);
    }

    @Test void addonCellCallbackThrowingAfterExtractionStillCommitsExactlyOneItem() {
        var cell = new AddonCell();
        cell.throwAfterExtract = true;
        var target = new Inventory(1);
        assertTrue(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                IActionSource.empty(), target, 0, () -> true));
        assertEquals(2, cell.count);
        assertTrue(ItemStack.matches(pattern(1), target.getStackInSlot(0)));
        assertEquals(1, cell.extractions);
    }

    @Test void creativeOrDisplayOnlyCellCannotInventAPhysicalPattern() {
        var cell = new AddonCell();
        cell.creative = true;
        var target = new Inventory(1);
        assertFalse(MolecularCenterPatternTransfer.fromCell(cell, cell, cell.key,
                IActionSource.empty(), target, 0, () -> true));
        assertEquals(3, cell.count);
        assertTrue(target.isEmpty());
    }

    private static final class AddonCell implements StorageCell {
        final AEItemKey key = AEItemKey.of(pattern(1));
        long count = 3;
        int extractions;
        boolean creative, throwAfterExtract;
        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!key.equals(what)) return 0;
            long extracted = Math.min(count, amount);
            if (mode == Actionable.MODULATE) {
                extractions++;
                if (!creative) count -= extracted;
                if (throwAfterExtract) throw new IllegalStateException("Addon save callback after removal");
            }
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) {
            throw new AssertionError("Per-item transactions must query one key, not enumerate a whole disk");
        }
        @Override public CellState getStatus() { return count == 0 ? CellState.EMPTY : CellState.NOT_EMPTY; }
        @Override public double getIdleDrain() { return 0; }
        @Override public void persist() {}
        @Override public Component getDescription() { return Component.literal("Unknown addon StorageCell"); }
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
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Exact pattern components"));
        return stack;
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
