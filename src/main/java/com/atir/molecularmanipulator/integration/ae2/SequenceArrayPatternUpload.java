package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import net.minecraft.network.chat.Component;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Uploads newly encoded patterns to an enabled Sequence Array. */
public final class SequenceArrayPatternUpload {
    private SequenceArrayPatternUpload() {
    }

    /** A successful encode has already consumed its blank. Transfer, or recycle a duplicate. */
    public static boolean uploadFromEncodingMenu(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu.isClientSide() || player.containerMenu != menu || menu.getPlayer() != player
                || !menu.isValidMenu() || !menu.stillValid(player) || player.isSpectator()
                || !(menu.getTarget() instanceof IActionHost host)
                || !(menu.getTarget() instanceof IPatternTerminalMenuHost terminal)) return false;
        var node = host.getActionableNode();
        var grid = node == null ? null : node.getGrid();
        if (grid == null) return false;
        var patterns = terminal.getLogic().getEncodedPatternInv();
        var pattern = patterns.getStackInSlot(0);
        if (!MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        if (containsPatternOnSequenceArray(pattern, grid)) {
            sendDuplicateMessage(player);
            patterns.extractItem(0, pattern.getCount(), false);
            var remainder = terminal.getLogic().getBlankPatternInv()
                    .insertItem(0, AEItems.BLANK_PATTERN.stack(pattern.getCount()), false);
            if (!remainder.isEmpty()) player.getInventory().placeItemBackInInventory(remainder, false);
        } else {
            if (!uploadToSequenceArray(player, pattern, grid)) return false;
            patterns.extractItem(0, pattern.getCount(), false);
        }
        terminal.getLogic().saveChanges();
        menu.broadcastChanges();
        return true;
    }

    /**
     * Accepts one encoded pattern into the first operational Sequence Array
     * whose automatic upload button is enabled. Returns true once the pattern is inserted.
     */
    public static boolean uploadToSequenceArray(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (player == null || pattern == null || pattern.isEmpty() || pattern.getCount() != 1 || grid == null
                || !MolecularCenterLogic.isSupportedPattern(pattern)) {
            return false;
        }

        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (center == null || !center.isAutomaticPatternUploadEnabled() || !center.canAcceptExternalPatternUpload()) {
                continue;
            }
            var logic = center.getLogic();
            if (logic.containsUploadPattern(pattern)) return false;
            int slot = logic.firstUploadSlot();
            if (slot < 0) continue;
            var inventory = logic.getFullPatternInventory();
            if (inventory.insertItem(slot, pattern.copy(), true).isEmpty()
                    && inventory.insertItem(slot, pattern.copy(), false).isEmpty()) return true;
        }
        return false;
    }

    /** Returns whether an available, opted-in Sequence Array already owns this pattern. */
    public static boolean containsPatternOnSequenceArray(ItemStack pattern, IGrid grid) {
        if (pattern == null || pattern.isEmpty() || grid == null
                || !MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (center == null || !center.isAutomaticPatternUploadEnabled() || !center.canAcceptExternalPatternUpload()) continue;
            if (center.getLogic().containsUploadPattern(pattern)) return true;
        }
        return false;
    }

    public static void sendDuplicateMessage(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.molecularmanipulator.pattern_upload.duplicate"));
        }
    }

}
