package com.atir.molecularmanipulator.integration.extendedaeplus;

import appeng.api.networking.IGrid;
import appeng.api.inventories.InternalInventory;
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
                // Let EAEP handle its normal duplicate path (including
                // returning the blank pattern and clearing the encoded slot).
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

    private static boolean canInsert(InternalInventory inventory, ItemStack pattern, int slots) {
        var remainder = pattern.copy();
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            remainder = inventory.insertItem(slot, remainder, true);
        }
        return remainder.isEmpty();
    }

    private static boolean containsPattern(InternalInventory inventory, ItemStack pattern, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            if (ItemStack.matches(inventory.getStackInSlot(slot), pattern)) {
                return true;
            }
        }
        return false;
    }
}
