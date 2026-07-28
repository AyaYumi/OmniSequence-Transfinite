package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import com.atir.molecularmanipulator.integration.ae2.LongNumberEntryWidgetBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = NumberEntryWidget.class, remap = false)
public abstract class NumberEntryWidgetAccessor
        implements LongNumberEntryWidgetBridge {
    @Shadow
    @Final
    private ConfirmableTextField textField;

    @Override
    public void molecularmanipulator$setInputMaxLength(int maxLength) {
        textField.setMaxLength(maxLength);
    }
}
