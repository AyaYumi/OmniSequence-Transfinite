package com.atir.molecularmanipulator.integration.jei;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResponsiveScreenJeiPropertiesTest {
    @Test
    void normalItemsUseVisualHitBoundsAndIgnoreStaleHoveredSlots() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var stack = new ItemStack(Items.STONE);
        var slot = new Slot(new SimpleContainer(stack), 0, 20, 30);
        ResponsiveScreenTestFixture.set(screen, "hoveredSlot", slot);
        var area = screen.responsiveSlotBounds(slot);
        Rect2i[] captured = { null };
        var builder = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IClickableIngredientFactory.IBuilder.class }, (proxy, method, args) -> {
                    captured[0] = new Rect2i((int) args[0], (int) args[1], (int) args[2], (int) args[3]);
                    return Optional.empty();
                });
        var factory = (IClickableIngredientFactory) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IClickableIngredientFactory.class }, (proxy, method, args) -> {
                    assertSame(stack, args[0]);
                    return builder;
                });
        var handler = ResponsiveScreenJeiProperties.<ResponsiveContainerScreen<?>>containerHandler();
        handler.getClickableIngredientUnderMouse(factory, screen, area.getX() + 4, area.getY() + 4);
        assertNotNull(captured[0]);
        assertEquals(area.getX(), captured[0].getX());
        assertEquals(area.getY(), captured[0].getY());
        assertEquals(area.getWidth(), captured[0].getWidth());
        assertEquals(area.getHeight(), captured[0].getHeight());
        captured[0] = null;
        handler.getClickableIngredientUnderMouse(factory, screen, 0, 0);
        assertNull(captured[0]);
    }

    @Test
    void pluginRegistersTheFittedScreenBoundsForEveryMachineFamily() throws Exception {
        Map<Class<?>, IScreenHandler<?>> handlers = new HashMap<>();
        var registration = (IGuiHandlerRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IGuiHandlerRegistration.class }, (proxy, method, args) -> {
                    if (method.getName().equals("addGuiScreenHandler")) handlers.put((Class<?>) args[0], (IScreenHandler<?>) args[1]);
                    return null;
                });
        new MolecularCenterJeiPlugin().registerGuiHandlers(registration);
        assertTrue(handlers.containsKey(ResponsiveContainerScreen.class));
        for (var type : new Class<?>[] { MolecularCenterScreen.class,
                OmniComputationScreen.class, MatterFabricationScreen.class, MatterFabricationPortScreen.class,
                MatterFabricationPatternAssemblyScreen.class }) {
            assertTrue(ResponsiveContainerScreen.class.isAssignableFrom(type));
        }
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        @SuppressWarnings("unchecked")
        var handler = (IScreenHandler<ResponsiveContainerScreen<?>>) handlers.get(ResponsiveContainerScreen.class);
        var properties = handler.apply(screen);
        assertEquals(109, properties.guiLeft());
        assertEquals(4, properties.guiTop());
        assertEquals(349, properties.guiXSize());
        assertEquals(232, properties.guiYSize());
        assertEquals(567, properties.screenWidth());
        assertEquals(240, properties.screenHeight());
        screen.viewport(854, 480, 430, 286);
        properties = handler.apply(screen);
        assertEquals(screen.getGuiLeft(), properties.guiLeft());
        assertEquals(screen.getGuiTop(), properties.guiTop());
        assertEquals(430, properties.guiXSize());
        assertEquals(286, properties.guiYSize());
    }
}
