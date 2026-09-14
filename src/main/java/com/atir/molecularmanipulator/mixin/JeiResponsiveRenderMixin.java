package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

/** Recent JEI versions draw from container events, which receive logical mouse coordinates. */
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiResponsiveRenderMixin {
    // Older JEI versions use onDrawScreenPost instead, outside the fitted panel render.
    @WrapMethod(method = "drawForScreenForeground", require = 0)
    private void molecularmanipulator$screenForeground(Screen screen, GuiGraphics graphics,
            int mouseX, int mouseY, Operation<Void> original) {
        if (screen instanceof ResponsiveContainerScreen<?> responsive) {
            responsive.renderExternalOverlay(graphics, mouseX, mouseY,
                    (gui, x, y, tick) -> original.call(screen, gui, x, y));
        } else {
            original.call(screen, graphics, mouseX, mouseY);
        }
    }

    @WrapMethod(method = "drawForScreenBackground", require = 0)
    private void molecularmanipulator$screenBackground(Screen screen, GuiGraphics graphics,
            Operation<Void> original) {
        if (screen instanceof ResponsiveContainerScreen<?> responsive) {
            responsive.renderExternalOverlay(graphics, 0, 0,
                    (gui, x, y, tick) -> original.call(screen, gui));
        } else {
            original.call(screen, graphics);
        }
    }
}
