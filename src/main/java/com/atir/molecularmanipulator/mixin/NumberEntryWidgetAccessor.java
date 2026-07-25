package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NumberEntryWidget.class, remap = false)
public interface NumberEntryWidgetAccessor {
    @Accessor("textField")
    ConfirmableTextField molecularmanipulator$getTextField();
}
