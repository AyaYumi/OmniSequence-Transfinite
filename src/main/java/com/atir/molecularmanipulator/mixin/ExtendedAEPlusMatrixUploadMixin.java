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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EAEP 1.6's split upload API: a void menu entry and two boolean direct entries. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.MatrixUploadUtil", remap = false)
public abstract class ExtendedAEPlusMatrixUploadMixin {
    @Inject(method = "uploadFromEncodingMenuToMatrix(Lnet/minecraft/server/level/ServerPlayer;Lappeng/menu/me/items/PatternEncodingTermMenu;)V",
            at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadEncodedPattern(ServerPlayer player,
            PatternEncodingTermMenu menu, CallbackInfo callback) {
        if (SequenceArrayPatternUpload.uploadFromEncodingMenu(player, menu)) callback.cancel();
    }

    @Inject(method = "uploadPatternToMatrix(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lappeng/api/networking/IGrid;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadPattern(ServerPlayer player, ItemStack pattern,
            IGrid grid, CallbackInfoReturnable<Boolean> callback) {
        molecularmanipulator$upload(player, pattern, grid, false, callback);
    }

    @Inject(method = "uploadPatternToMatrix(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lappeng/api/networking/IGrid;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadPatternSilently(ServerPlayer player, ItemStack pattern,
            IGrid grid, boolean silent, CallbackInfoReturnable<Boolean> callback) {
        molecularmanipulator$upload(player, pattern, grid, silent, callback);
    }

    private static void molecularmanipulator$upload(ServerPlayer player, ItemStack pattern,
            IGrid grid, boolean silent, CallbackInfoReturnable<Boolean> callback) {
        if (SequenceArrayPatternUpload.containsPatternOnSequenceArray(pattern, grid)) {
            if (!silent) SequenceArrayPatternUpload.sendDuplicateMessage(player);
            callback.setReturnValue(false);
        } else if (SequenceArrayPatternUpload.uploadToSequenceArray(player, pattern, grid)) {
            callback.setReturnValue(true);
        }
    }
}
