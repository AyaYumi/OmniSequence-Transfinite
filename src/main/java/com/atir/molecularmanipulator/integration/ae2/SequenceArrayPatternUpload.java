package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.atir.molecularmanipulator.blockentity.MolecularCenterPatternTransfer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Uploads newly encoded patterns to an enabled Sequence Array, independently of optional EAEP. */
public final class SequenceArrayPatternUpload {
    private SequenceArrayPatternUpload() {}

    /** A successful encode has already consumed its blank. Transfer, or recycle a duplicate. */
    public static boolean uploadFromEncodingMenu(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null || menu.isClientSide() || player.containerMenu != menu
                || menu.getPlayer() != player || !menu.isValidMenu() || !menu.stillValid(player)
                || player.isSpectator() || !(menu.getTarget() instanceof IPatternTerminalMenuHost terminal)) return false;
        // AE2 15 exposes this publicly on MEStorageMenu, including wireless menu hosts.
        var node = menu.getNetworkNode();
        var grid = node == null ? null : node.getGrid();
        if (grid == null) return false;
        var patterns = terminal.getLogic().getEncodedPatternInv();
        var pattern = patterns.getStackInSlot(0).copy();
        if (pattern.getCount() != 1 || !MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        if (containsPatternOnSequenceArray(pattern, grid)) {
            if (!removeEncodedPattern(patterns, pattern)) return false;
            var remainder = terminal.getLogic().getBlankPatternInv()
                    .insertItem(0, AEItems.BLANK_PATTERN.stack(1), false);
            if (!remainder.isEmpty()) player.getInventory().placeItemBackInInventory(remainder, false);
            sendDuplicateMessage(player);
        } else {
            boolean transferred = false;
            for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
                if (!accepts(center, grid)) continue;
                var logic = center.getLogic();
                int slot = logic.firstUploadSlot();
                if (slot < 0) continue;
                if (!MolecularCenterPatternTransfer.fromInventory(patterns, 0, logic.getFullPatternInventory(), slot,
                        () -> accepts(center, grid) && player.containerMenu == menu && menu.isValidMenu())) return false;
                transferred = true;
                break;
            }
            if (!transferred) return false;
        }
        terminal.getLogic().saveChanges();
        menu.broadcastChanges();
        return true;
    }

    /** Direct EAEP entry: the caller owns/consumes the source only after this returns true. */
    public static boolean uploadToSequenceArray(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (player == null || player.isSpectator() || pattern == null || pattern.isEmpty()
                || pattern.getCount() != 1 || grid == null || !MolecularCenterLogic.isSupportedPattern(pattern)
                || containsPatternOnSequenceArray(pattern, grid)) return false;
        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (!accepts(center, grid)) continue;
            var logic = center.getLogic();
            int slot = logic.firstUploadSlot();
            if (slot < 0) continue;
            var inventory = logic.getFullPatternInventory();
            if (!inventory.getStackInSlot(slot).isEmpty()
                    || !inventory.insertItem(slot, pattern.copy(), true).isEmpty() || !accepts(center, grid)) continue;
            boolean inserted = false;
            try {
                inserted = inventory.insertItem(slot, pattern.copy(), false).isEmpty()
                        && ItemStack.matches(pattern, inventory.getStackInSlot(slot)) && accepts(center, grid);
                if (inserted) return true;
            } catch (RuntimeException failure) {
                logic.invalidateUploadIndex();
                return false;
            } finally {
                if (!inserted && ItemStack.matches(pattern, inventory.getStackInSlot(slot))) {
                    restoreSlot(inventory, slot, ItemStack.EMPTY);
                }
            }
        }
        return false;
    }

    /** Only available, opted-in arrays participate; inactive pages are excluded by the controller index. */
    public static boolean containsPatternOnSequenceArray(ItemStack pattern, IGrid grid) {
        if (pattern == null || pattern.isEmpty() || grid == null
                || !MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (accepts(center, grid) && center.getLogic().containsUploadPattern(pattern)) return true;
        }
        return false;
    }

    private static boolean accepts(MolecularCenterBlockEntity center, IGrid grid) {
        return center != null && !center.isRemoved() && center.getMainNode().getGrid() == grid
                && center.isAutomaticPatternUploadEnabled() && center.canAcceptExternalPatternUpload();
    }

    private static boolean removeEncodedPattern(InternalInventory patterns, ItemStack pattern) {
        if (!ItemStack.matches(pattern, patterns.extractItem(0, 1, true))) return false;
        boolean removed = false;
        try {
            removed = ItemStack.matches(pattern, patterns.extractItem(0, 1, false))
                    && patterns.getStackInSlot(0).isEmpty();
            return removed;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            if (!removed) restoreSlot(patterns, 0, pattern);
        }
    }

    private static void restoreSlot(InternalInventory inventory, int slot, ItemStack snapshot) {
        try {
            inventory.setItemDirect(slot, snapshot.copy());
        } catch (RuntimeException failure) {
            if (!ItemStack.matches(snapshot, inventory.getStackInSlot(slot))) throw failure;
        }
    }

    public static void sendDuplicateMessage(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.molecularmanipulator.pattern_upload.duplicate"));
        }
    }
}
