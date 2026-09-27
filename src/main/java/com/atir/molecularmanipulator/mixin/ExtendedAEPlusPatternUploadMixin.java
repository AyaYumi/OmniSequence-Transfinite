package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.atir.molecularmanipulator.integration.extendedaeplus.MatrixUploadCoreIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes EAEP's automatic matrix upload to an equipped Sequence Array first. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil",
        remap = false)
public abstract class ExtendedAEPlusPatternUploadMixin {
    @Inject(method = "uploadFromEncodingMenuToMatrix", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadEncodedPatternToSequenceArray(
            ServerPlayer player, PatternEncodingTermMenu menu,
            CallbackInfoReturnable<Boolean> callback) {
        AEBaseMenu baseMenu = menu;
        var target = baseMenu.getTarget();
        if (!(target instanceof IActionHost host)
                || !(target instanceof IPatternTerminalMenuHost terminalHost)) return;
        var node = host.getActionableNode();
        var grid = node == null ? null : node.getGrid();
        if (grid == null) return;

        var patternInventory = terminalHost.getLogic().getEncodedPatternInv();
        var pattern = patternInventory.getStackInSlot(0);
        if (MatrixUploadCoreIntegration.containsPatternOnSequenceArray(pattern, grid)) {
            MatrixUploadCoreIntegration.sendDuplicateMessage(player);
            var blanks = terminalHost.getLogic().getBlankPatternInv()
                    .insertItem(0, AEItems.BLANK_PATTERN.stack(pattern.getCount()), false);
            patternInventory.extractItem(0, pattern.getCount(), false);
            if (!blanks.isEmpty()) player.getInventory().placeItemBackInInventory(blanks, false);
            callback.setReturnValue(true);
            return;
        }
        if (!MatrixUploadCoreIntegration.uploadToSequenceArray(player, pattern, grid)) return;

        // EAEP normally consumes this slot after its own matrix insertion. Keep
        // the same source-slot behavior when the Sequence Array accepts it.
        patternInventory.extractItem(0, pattern.getCount(), false);
        callback.setReturnValue(true);
    }

    @Inject(method = "uploadPatternToMatrix", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadToSequenceArray(
            ServerPlayer player, ItemStack pattern, IGrid grid,
            CallbackInfoReturnable<Boolean> callback) {
        if (MatrixUploadCoreIntegration.containsPatternOnSequenceArray(pattern, grid)) {
            MatrixUploadCoreIntegration.sendDuplicateMessage(player);
            callback.setReturnValue(false);
            return;
        }
        if (MatrixUploadCoreIntegration.uploadToSequenceArray(player, pattern, grid)) {
            callback.setReturnValue(true);
        }
    }
}
