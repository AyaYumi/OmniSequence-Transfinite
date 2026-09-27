package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
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
    @Inject(method = "uploadPatternToMatrix", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$uploadToSequenceArray(
            ServerPlayer player, ItemStack pattern, IGrid grid,
            CallbackInfoReturnable<Boolean> callback) {
        if (MatrixUploadCoreIntegration.uploadToSequenceArray(player, pattern, grid)) {
            callback.setReturnValue(true);
        }
    }
}
