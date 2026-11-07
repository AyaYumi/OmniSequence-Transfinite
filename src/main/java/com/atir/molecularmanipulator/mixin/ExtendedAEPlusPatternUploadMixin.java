package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.atir.molecularmanipulator.integration.ae2.SequenceArrayPatternUpload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes EAEP's automatic matrix upload to an enabled Sequence Array first. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil",
        remap = false)
public abstract class ExtendedAEPlusPatternUploadMixin {
    @Inject(method = "uploadFromEncodingMenuToMatrix", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadEncodedPatternToSequenceArray(
            ServerPlayer player, PatternEncodingTermMenu menu,
            CallbackInfoReturnable<Boolean> callback) {
        // Native and EAEP menu paths share validation, physical transfer, duplicate
        // recycling and notifications instead of consuming the source twice.
        if (SequenceArrayPatternUpload.uploadFromEncodingMenu(player, menu)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "uploadPatternToMatrix", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadToSequenceArray(
            ServerPlayer player, ItemStack pattern, IGrid grid,
            CallbackInfoReturnable<Boolean> callback) {
        if (player == null || player.isSpectator() || !player.serverLevel().getServer().isSameThread()) return;
        if (SequenceArrayPatternUpload.containsPatternOnSequenceArray(pattern, grid)) {
            SequenceArrayPatternUpload.sendDuplicateMessage(player);
            callback.setReturnValue(false);
            return;
        }
        if (SequenceArrayPatternUpload.uploadToSequenceArray(player, pattern, grid)) {
            callback.setReturnValue(true);
        }
    }
}
