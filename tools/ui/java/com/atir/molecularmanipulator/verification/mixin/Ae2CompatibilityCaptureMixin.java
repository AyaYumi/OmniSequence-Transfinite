package com.atir.molecularmanipulator.verification.mixin;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import com.atir.molecularmanipulator.verification.Ae2CompatibilityProbe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CPUSelectionList.class, remap = false)
abstract class Ae2CompatibilityCaptureMixin {
    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int captureName(GuiGraphics graphics, Font font, Component name, int x, int y,
            int color, boolean shadow, Operation<Integer> original) {
        Ae2CompatibilityProbe.names.add(name);
        return original.call(graphics, font, name, x, y, color, shadow);
    }
}

@Mixin(value = InfoBar.class, remap = false)
abstract class Ae2CompatibilityInfoBarMixin {
    @Inject(method = "add(Ljava/lang/String;IFII)V", at = @At("HEAD"))
    private void captureText(String text, int color, float scale, int x, int y, CallbackInfo ci) {
        if (Ae2CompatibilityProbe.capturing) Ae2CompatibilityProbe.amounts.add(text);
    }
}
