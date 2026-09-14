package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;

/** Recent JEI versions draw from container events, which receive logical mouse coordinates. */
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiResponsiveRenderMixin {
    // JEI 15 removes the container origin itself, so retain that origin around its call.
    @WrapMethod(method = "onDrawForeground", require = 0)
    private void molecularmanipulator$legacyForeground(AbstractContainerScreen<?> screen, GuiGraphics graphics,
            int mouseX, int mouseY, Operation<Void> original) {
        if (screen instanceof ResponsiveContainerScreen<?> responsive && responsive.isRenderingScaledContent()) {
            responsive.renderExternalOverlay(graphics, mouseX, mouseY, (gui, x, y, tick) -> {
                gui.pose().translate(screen.getGuiLeft(), screen.getGuiTop(), 0);
                original.call(screen, gui, x, y);
            });
        } else {
            original.call(screen, graphics, mouseX, mouseY);
        }
    }

    // Newer backports use screen-space foreground/background entry points instead.
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
