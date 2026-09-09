package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIJEIHandlers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ResponsiveModularUITest {
    @Test
    void ldlibJeiHandlerReceivesVisualAreasWithoutChangingCachedLogicalRectangles() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var ui = ResponsiveScreenTestFixture.allocate(ResponsiveModularUI.class);
        var logical = new Rect2i(68, -23, 430, 286);
        ResponsiveScreenTestFixture.set(ui, "screen", screen);
        ResponsiveScreenTestFixture.set(ui, "extraAreas", new ArrayList<>(List.of(logical)));
        screen.addResponsiveModularWidget(ui.new ModularUIWidget());
        for (int frame = 0; frame < 2; frame++) {
            var area = ModularUIJEIHandlers.GUI_CONTAINER_HANDLER.getGuiExtraAreas(screen).getFirst();
            assertEquals(109, area.getX());
            assertEquals(4, area.getY());
            assertEquals(349, area.getWidth());
            assertEquals(232, area.getHeight());
        }
        assertEquals(68, logical.getX());
        assertEquals(-23, logical.getY());
        assertEquals(430, logical.getWidth());
    }

    @Test
    void modularWidgetRemainsOwnedAndGetsRawMouseInsideTheMachinePose() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var ui = ResponsiveScreenTestFixture.allocate(ResponsiveModularUI.class);
        int[] mouse = new int[2];
        Matrix4f[] renderedPose = new Matrix4f[1];
        var widget = ui.new ModularUIWidget() {
            @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
                mouse[0] = x; mouse[1] = y;
                renderedPose[0] = new Matrix4f(graphics.pose().last().pose());
            }
        };
        screen.addResponsiveModularWidget(widget);
        ResponsiveScreenTestFixture.set(screen, "rawMouseX", 327);
        ResponsiveScreenTestFixture.set(screen, "rawMouseY", 12);
        assertTrue(screen.screenContent.owns(widget));
        var graphics = new UiRenderRecorder();
        screen.screenContent.render(graphics, screen.renderables, 327, 12, 0, () -> {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(54, 22, 0);
                graphics.pose().scale(0.81F, 0.81F, 1);
                for (var renderer : screen.renderables) renderer.render(graphics, -999, -999, 0);
            } finally { graphics.pose().popPose(); }
        });
        assertArrayEquals(new int[] {327, 12}, mouse);
        assertNotEquals(graphics.pose().last().pose(), renderedPose[0]);
        assertEquals(1, screen.renderables.size());
        assertEquals(List.of(widget), screen.children());
    }
}
