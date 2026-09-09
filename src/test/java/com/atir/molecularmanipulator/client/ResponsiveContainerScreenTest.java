package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ResponsiveContainerScreenTest {
    @Test
    void screenshotViewportReportsTheFittedPanelAndKeepsLogicalMenuCoordinates() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var bounds = screen.responsiveBounds();
        assertEquals(109, bounds.getX());
        assertEquals(4, bounds.getY());
        assertEquals(349, bounds.getWidth());
        assertEquals(232, bounds.getHeight());
        assertEquals(68, screen.getGuiLeft());
        assertEquals(-23, screen.getGuiTop());
        for (String style : new String[] {"molecular_center", "omni_computation", "matter_fabrication",
                "matter_fabrication_port", "matter_fabrication_pattern_assembly"}) {
            try (var stream = getClass().getResourceAsStream("/assets/ae2/screens/" + style + ".json")) {
                assertNotNull(stream);
                var json = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var background = json.getAsJsonObject("generatedBackground");
                int w = background.get("width").getAsInt(), h = background.get("height").getAsInt();
                for (int[] viewport : new int[][] {{320, 240}, {567, 240}, {680, 288}, {854, 480}}) {
                    screen.viewport(viewport[0], viewport[1], w, h);
                    var area = screen.responsiveBounds();
                    assertTrue(area.getX() >= 4 && area.getY() >= 4);
                    assertTrue(area.getX() + area.getWidth() <= screen.width - 4);
                    assertTrue(area.getY() + area.getHeight() <= screen.height - 4);
                    for (int[] point : new int[][] {{10, 20}, {w / 2, h / 2}, {w - 9, h - 9}}) {
                        int x = screen.getGuiLeft() + point[0], y = screen.getGuiTop() + point[1];
                        assertEquals(x, screen.logicalMouseX(screen.responsiveScreenX(x)), 0.9);
                        assertEquals(y, screen.logicalMouseY(screen.responsiveScreenY(y)), 0.9);
                    }
                }
            }
        }
    }

    @Test
    void injectedControlsRenderOnceInScreenSpaceAndRestoreWidgetOrder() {
        var content = new ResponsiveScreenWidgets();
        var machine = new ProbeControl(80, 20);
        var sidebar = new ProbeControl(1, 1);
        var darkMode = new ProbeControl(8, 136);
        content.own(machine);
        List<Renderable> renderables = new ArrayList<>(List.of(sidebar, machine, darkMode));
        var before = List.copyOf(renderables);
        var graphics = new UiRenderRecorder();
        var outerPose = new Matrix4f(graphics.pose().last().pose());
        content.render(graphics, renderables, 8, 9, 0, () -> {
            assertEquals(List.of(machine), renderables);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(54, 22, 0);
                graphics.pose().scale(0.81F, 0.81F, 1);
                for (var renderer : renderables) renderer.render(graphics, -56, -16, 0);
            } finally { graphics.pose().popPose(); }
        });
        assertEquals(before, renderables);
        assertEquals(1, machine.renders);
        assertEquals(1, sidebar.renders);
        assertEquals(1, darkMode.renders);
        assertNotEquals(outerPose, machine.pose);
        assertEquals(outerPose, sidebar.pose);
        assertEquals(outerPose, darkMode.pose);
        assertEquals(8, sidebar.mouseX);
        assertEquals(9, sidebar.mouseY);
        assertEquals(outerPose, graphics.pose().last().pose());
        assertThrows(IllegalStateException.class, () -> content.render(graphics, renderables, 0, 0, 0, () -> {
            throw new IllegalStateException("render failure");
        }));
        assertEquals(before, renderables);
    }

    @Test
    void thirdPartyClickHoverDragScrollReleaseAndKeyboardKeepScreenCoordinates() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var sidebar = new ProbeControl(1, 1);
        screen.inject(sidebar);
        screen.mouseMoved(8, 9);
        assertEquals(8, sidebar.mouseX);
        assertEquals(9, sidebar.mouseY);
        assertTrue(screen.mouseClicked(8, 9, 0));
        assertSame(sidebar, screen.getFocused());
        assertTrue(screen.mouseDragged(12, 11, 0, 4, 2));
        assertEquals(12, sidebar.mouseX);
        assertEquals(11, sidebar.mouseY);
        assertEquals(4, sidebar.dragX);
        assertEquals(2, sidebar.dragY);
        assertTrue(screen.mouseScrolled(8, 9, 0, -1));
        assertEquals(-1, sidebar.scroll);
        assertTrue(screen.charTyped('a', 0));
        assertEquals('a', sidebar.typed);
        assertTrue(screen.mouseReleased(200, 150, 0));
        assertEquals(200, sidebar.mouseX);
        assertFalse(screen.isDragging());
    }

    @Test
    void machineButtonReceivesLogicalCoordinatesWithoutRedispatchingToExternalControls() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var machine = new ProbeControl(screen.getGuiLeft() + 20, screen.getGuiTop() + 30);
        var sidebar = new ProbeControl(1, 1);
        screen.inject(sidebar);
        screen.own(machine);
        int x = screen.responsiveScreenX(machine.x + 8);
        int y = screen.responsiveScreenY(machine.y + 8);
        assertTrue(screen.mouseClicked(x, y, 0));
        assertSame(machine, screen.getFocused());
        assertEquals(machine.x + 8, machine.mouseX, 0.7);
        assertEquals(machine.y + 8, machine.mouseY, 0.7);
        assertEquals(1, sidebar.clickAttempts);
        assertEquals(List.of(sidebar, machine), screen.children());
    }

    @Test
    void ingredientBoundsFollowTheRenderedSlot() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var inventory = new SimpleContainer(new ItemStack(Items.STONE));
        var slot = new Slot(inventory, 0, 20, 30);
        ResponsiveScreenTestFixture.set(screen, "hoveredSlot", slot);
        var bounds = screen.responsiveSlotBounds(slot);
        assertEquals(screen.responsiveScreenX(screen.getGuiLeft() + slot.x), bounds.getX());
        assertEquals(screen.responsiveScreenY(screen.getGuiTop() + slot.y), bounds.getY());
        assertTrue(bounds.getWidth() < 16);
        assertTrue(bounds.getHeight() < 16);
    }

    @Test
    void nativeExclusionAreasAreConvertedOnlyForExternalConsumers() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(567, 240, 430, 286);
        var logicalArea = new net.minecraft.client.renderer.Rect2i(48, -13, 18, 18);
        ResponsiveScreenTestFixture.set(screen, "widgets", new appeng.client.gui.WidgetContainer(new appeng.client.gui.style.ScreenStyle()) {
            @Override
            public void addExclusionZones(List<net.minecraft.client.renderer.Rect2i> areas, net.minecraft.client.renderer.Rect2i bounds) {
                areas.add(logicalArea);
            }
        });
        var visualArea = screen.getExclusionZones().get(0);
        assertEquals(screen.responsiveScreenX(48), visualArea.getX());
        assertEquals(screen.responsiveScreenY(-13), visualArea.getY());
        assertTrue(visualArea.getWidth() < 18);
        ResponsiveScreenTestFixture.set(screen, "renderingScaledContent", true);
        assertSame(logicalArea, screen.getExclusionZones().get(0));
    }

    private static final class ProbeControl implements GuiEventListener, Renderable, NarratableEntry {
        final int x, y;
        int renders, clickAttempts;
        double mouseX, mouseY, dragX, dragY, scroll;
        boolean focused;
        char typed;
        Matrix4f pose;
        ProbeControl(int x, int y) { this.x = x; this.y = y; }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            renders++; mouseX = x; mouseY = y; pose = new Matrix4f(graphics.pose().last().pose());
        }
        @Override public boolean isMouseOver(double x, double y) { return x >= this.x && y >= this.y && x < this.x + 16 && y < this.y + 16; }
        @Override public void mouseMoved(double x, double y) { mouseX = x; mouseY = y; }
        @Override public boolean mouseClicked(double x, double y, int button) { clickAttempts++; mouseMoved(x, y); return isMouseOver(x, y); }
        @Override public boolean mouseReleased(double x, double y, int button) { mouseMoved(x, y); return true; }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { mouseMoved(x, y); dragX = dx; dragY = dy; return true; }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) { mouseMoved(x, y); scroll = delta; return true; }
        @Override public boolean charTyped(char codePoint, int modifiers) { typed = codePoint; return true; }
        @Override public void setFocused(boolean focused) { this.focused = focused; }
        @Override public boolean isFocused() { return focused; }
        @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
        @Override public void updateNarration(NarrationElementOutput output) { }
    }
}
