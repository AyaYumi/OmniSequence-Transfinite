package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.atir.molecularmanipulator.blockentity.MolecularCenterPatternTransfer;
import java.util.function.BooleanSupplier;
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
        if (player == null || menu == null || !player.serverLevel().getServer().isSameThread()
                || menu.isClientSide() || player.containerMenu != menu || menu.getPlayer() != player
                || !menu.isValidMenu() || !menu.stillValid(player) || player.isSpectator()
                || !(menu.getTarget() instanceof IActionHost host)
                || !(menu.getTarget() instanceof IPatternTerminalMenuHost terminal)) return false;
        var node = host.getActionableNode();
        var grid = node == null ? null : node.getGrid();
        if (grid == null) return false;
        var patterns = terminal.getLogic().getEncodedPatternInv();
        var pattern = patterns.getStackInSlot(0).copy();
        if (pattern.getCount() != 1 || !MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        if (containsPatternOnSequenceArray(pattern, grid)) {
            if (!consumeEncodedPattern(patterns, pattern)) return false;
            var remainder = terminal.getLogic().getBlankPatternInv()
                    .insertItem(0, AEItems.BLANK_PATTERN.stack(), false);
            if (!remainder.isEmpty()) player.getInventory().placeItemBackInInventory(remainder, false);
            sendDuplicateMessage(player);
        } else if (!uploadToSequenceArray(player, pattern, grid, patterns,
                () -> player.containerMenu == menu && menu.isValidMenu() && menu.stillValid(player)
                        && host.getActionableNode() != null && host.getActionableNode().getGrid() == grid)) {
            return false;
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
        if (pattern == null || pattern.isEmpty() || pattern.getCount() != 1) return false;
        // EAEP's direct-item API retains responsibility for consuming its source after true.
        var offered = new AppEngInternalInventory(1);
        offered.setItemDirect(0, pattern.copy());
        return uploadToSequenceArray(player, pattern, grid, offered, () -> true);
    }

    private static boolean uploadToSequenceArray(ServerPlayer player, ItemStack pattern, IGrid grid,
            InternalInventory source, BooleanSupplier sourceValid) {
        if (player == null || player.isSpectator() || !player.serverLevel().getServer().isSameThread()
                || grid == null || !MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (center == null || !center.isAutomaticPatternUploadEnabled()
                    || !center.canAcceptExternalPatternUpload(pattern)) continue;
            var logic = center.getLogic();
            if (logic.containsUploadPattern(pattern)) return false;
            int slot = logic.firstUploadSlot();
            if (slot < 0) continue;
            // Share the physical migration transaction: a destination callback failure must
            // roll back its crystal/index write before the terminal can keep the original.
            if (MolecularCenterPatternTransfer.fromInventory(source, 0, logic.getFullPatternInventory(), slot,
                    () -> sourceValid.getAsBoolean() && center.getMainNode().getGrid() == grid
                            && center.isAutomaticPatternUploadEnabled() && center.canAcceptExternalPatternUpload())) return true;
        }
        return false;
    }

    private static boolean consumeEncodedPattern(InternalInventory source, ItemStack original) {
        if (!ItemStack.matches(original, source.getStackInSlot(0))) return false;
        boolean consumed = false;
        try {
            var extracted = source.extractItem(0, 1, false);
            consumed = ItemStack.matches(original, extracted) && source.getStackInSlot(0).isEmpty();
            return consumed;
        } finally {
            if (!consumed) source.setItemDirect(0, original.copy());
        }
    }

    /** Returns whether an available, opted-in Sequence Array already owns this pattern. */
    public static boolean containsPatternOnSequenceArray(ItemStack pattern, IGrid grid) {
        if (pattern == null || pattern.isEmpty() || grid == null
                || !MolecularCenterLogic.isSupportedPattern(pattern)) return false;
        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (center == null || !center.isAutomaticPatternUploadEnabled()
                    || center.getMainNode().getGrid() != grid || !center.canAcceptExternalPatternUpload(pattern)) continue;
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
