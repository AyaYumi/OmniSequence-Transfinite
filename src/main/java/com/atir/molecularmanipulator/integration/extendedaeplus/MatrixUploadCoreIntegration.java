package com.atir.molecularmanipulator.integration.extendedaeplus;

import appeng.api.networking.IGrid;
import appeng.api.inventories.InternalInventory;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Optional ExtendedAE Plus bridge for the Sequence Array upload-core slot. */
public final class MatrixUploadCoreIntegration {
    private static final String MOD_ID = "extendedae_plus";
    private static final String UPLOAD_CORE_ID = MOD_ID + ":assembler_matrix_upload_core";

    private MatrixUploadCoreIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isUploadCore(ItemStack stack) {
        return isLoaded() && stack != null && !stack.isEmpty()
                && UPLOAD_CORE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** Handles the source slot once, for both legacy and current EAEP menu hooks. */
    public static boolean uploadFromEncodingMenu(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null || !isLoaded()
                || !(menu.getTarget() instanceof IPatternTerminalMenuHost terminalHost)) return false;
        var node = menu.getNetworkNode();
        var grid = node == null ? null : node.getGrid();
        if (grid == null) return false;
        var inventory = terminalHost.getLogic().getEncodedPatternInv();
        var pattern = inventory.getStackInSlot(0);
        if (pattern.isEmpty()) return false;
        int count = pattern.getCount();
        if (containsPatternOnSequenceArray(pattern, grid)) {
            sendDuplicateMessage(player);
            var blanks = terminalHost.getLogic().getBlankPatternInv()
                    .insertItem(0, AEItems.BLANK_PATTERN.stack(count), false);
            inventory.extractItem(0, count, false);
            if (!blanks.isEmpty()) player.getInventory().placeItemBackInInventory(blanks, false);
            return true;
        }
        if (!uploadToSequenceArray(player, pattern, grid)) return false;
        inventory.extractItem(0, count, false);
        return true;
    }

    /**
     * Accepts one encoded pattern into the first operational Sequence Array
     * whose upload core is installed. Returning true means the caller must
     * skip ExtendedAE Plus's own matrix scan.
     */
    public static boolean uploadToSequenceArray(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (!isLoaded() || player == null || pattern == null || pattern.isEmpty() || grid == null) {
            return false;
        }

        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (center == null || !center.canAcceptMatrixUpload() || !center.hasMatrixUploadCore()) {
                continue;
            }
            var inventory = center.getLogic().getFullPatternInventory();
            int slots = Math.min(ModConfig.activePatternSlots(), inventory.size());
            if (containsPattern(inventory, pattern, slots)) {
                // The menu hook handles the duplicate message and blank return.
                return false;
            }
            if (!canInsert(inventory, pattern, slots)) {
                continue;
            }
            var remainder = pattern.copy();
            for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
                remainder = inventory.insertItem(slot, remainder, false);
            }
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether an active upload-core Sequence Array already owns this pattern. */
    public static boolean containsPatternOnSequenceArray(ItemStack pattern, IGrid grid) {
        if (!isLoaded() || pattern == null || pattern.isEmpty() || grid == null) return false;
        for (var center : grid.getMachines(MolecularCenterBlockEntity.class)) {
            if (center == null || !center.hasMatrixUploadCore()) continue;
            var inventory = center.getLogic().getFullPatternInventory();
            int slots = Math.min(ModConfig.activePatternSlots(), inventory.size());
            if (containsPattern(inventory, pattern, slots)) return true;
        }
        return false;
    }

    public static void sendDuplicateMessage(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.molecularmanipulator.matrix_upload_duplicate"));
        }
    }

    private static boolean canInsert(InternalInventory inventory, ItemStack pattern, int slots) {
        var remainder = pattern.copy();
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            remainder = inventory.insertItem(slot, remainder, true);
        }
        return remainder.isEmpty();
    }

    private static boolean containsPattern(InternalInventory inventory, ItemStack pattern, int slots) {
        var incoming = withoutEncoder(pattern);
        for (int slot = 0; slot < slots; slot++) {
            var stored = inventory.getStackInSlot(slot);
            if (!stored.isEmpty() && ItemStack.matches(withoutEncoder(stored), incoming)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack withoutEncoder(ItemStack stack) {
        var copy = stack.copy();
        var customData = copy.getTag();
        if (customData != null) {
            CompoundTag tag = customData.copy();
            tag.remove("encodePlayer");
            copy.setTag(tag);
        }
        return copy;
    }
}
