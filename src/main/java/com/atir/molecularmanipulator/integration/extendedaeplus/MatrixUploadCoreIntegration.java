package com.atir.molecularmanipulator.integration.extendedaeplus;

import appeng.api.networking.IGrid;
import appeng.api.inventories.InternalInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
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
            player.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
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
        for (int slot = 0; slot < slots; slot++) {
            if (matchesIgnoringEncoder(inventory.getStackInSlot(slot), pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesIgnoringEncoder(ItemStack stored, ItemStack incoming) {
        if (stored.isEmpty() || incoming.isEmpty()) return false;
        var storedCopy = withoutEncoder(stored);
        var incomingCopy = withoutEncoder(incoming);
        return ItemStack.matches(storedCopy, incomingCopy);
    }

    private static ItemStack withoutEncoder(ItemStack stack) {
        var copy = stack.copy();
        var customData = copy.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            tag.remove("encodePlayer");
            copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return copy;
    }
}
