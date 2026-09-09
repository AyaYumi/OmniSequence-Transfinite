package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

/** JEI overlays and hit areas live outside the machine's local render transform. */
record ResponsiveScreenJeiProperties(Class<? extends Screen> screenClass, Rect2i bounds,
        int screenWidth, int screenHeight) implements IGuiProperties {
    static IGuiProperties of(ResponsiveContainerScreen<?> screen) {
        return new ResponsiveScreenJeiProperties(screen.getClass(), screen.responsiveBounds(), screen.width, screen.height);
    }

    static <T extends ResponsiveContainerScreen<?>> IGuiContainerHandler<T> containerHandler() {
        return new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    IClickableIngredientFactory factory, T screen, double mouseX, double mouseY) {
                var slot = screen.getSlotUnderMouse();
                if (slot == null || !slot.isActive() || slot.getItem().isEmpty()) return Optional.empty();
                var area = screen.responsiveSlotBounds(slot);
                if (!area.contains((int) mouseX, (int) mouseY)) return Optional.empty();
                return factory.createBuilder(slot.getItem())
                        .buildWithArea(area.getX(), area.getY(), area.getWidth(), area.getHeight());
            }
        };
    }

    @Override public int guiLeft() { return bounds.getX(); }
    @Override public int guiTop() { return bounds.getY(); }
    @Override public int guiXSize() { return bounds.getWidth(); }
    @Override public int guiYSize() { return bounds.getHeight(); }
}
