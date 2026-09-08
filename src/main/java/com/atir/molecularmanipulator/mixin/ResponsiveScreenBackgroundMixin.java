package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ResponsiveScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$skipScaledBackground(GuiGraphics graphics, CallbackInfo callback) {
        if ((Object) this instanceof ResponsiveContainerScreen<?> screen && screen.isRenderingScaledContent()) {
            callback.cancel();
        }
    }
}
