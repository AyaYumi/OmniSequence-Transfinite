package com.atir.molecularmanipulator.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.atir.molecularmanipulator.integration.ae2.SequenceArrayPatternUpload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 15's final return follows encoding/clearPattern; early rejected encodes cannot upload. */
@Mixin(PatternEncodingTermMenu.class)
public abstract class SequenceArrayPatternUploadMixin {
    @Inject(method = "encode", at = @At("TAIL"), remap = false)
    private void molecularmanipulator$uploadToEnabledSequenceArray(CallbackInfo callback) {
        var menu = (PatternEncodingTermMenu) (Object) this;
        if (!menu.isClientSide() && menu.getPlayer() instanceof ServerPlayer player) {
            SequenceArrayPatternUpload.uploadFromEncodingMenu(player, menu);
        }
    }
}
