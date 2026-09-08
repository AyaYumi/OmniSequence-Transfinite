package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.runtime.IClickableIngredient;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

/** JEI lays out its overlays in screen coordinates, outside the console's pose. */
record ResponsiveScreenJeiProperties(Class<? extends Screen> screenClass, Rect2i bounds,
        int screenWidth, int screenHeight) implements IGuiProperties {
    static IGuiProperties of(ResponsiveContainerScreen<?> screen) {
        return new ResponsiveScreenJeiProperties(screen.getClass(), screen.responsiveBounds(), screen.width, screen.height);
    }

    static <T extends ResponsiveContainerScreen<?>> IScreenHandler<T> handler() {
        return new IScreenHandler<>() {
            @Override public IGuiProperties apply(T screen) { return of(screen); }

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

    @Override public Class<? extends Screen> getScreenClass() { return screenClass; }
    @Override public int getGuiLeft() { return bounds.getX(); }
    @Override public int getGuiTop() { return bounds.getY(); }
    @Override public int getGuiXSize() { return bounds.getWidth(); }
    @Override public int getGuiYSize() { return bounds.getHeight(); }
    @Override public int getScreenWidth() { return screenWidth; }
    @Override public int getScreenHeight() { return screenHeight; }
}
