package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.mixin.JeiResponsiveRenderMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class JeiResponsiveRenderTest {
    @Test
    void foregroundHoverUsesTheSamePositionAsClickEvenAfterJeiResetsThePose() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(854, 300, 332, 368);
        var graphics = new UiRenderRecorder();
        // Clicking JEI uses this screen-space position. The container event receives a different one.
        var ingredient = new Rect2i(696, 90, 16, 16);
        int mouseX = 700, mouseY = 96;
        int logicalX = (int) Math.floor(screen.logicalMouseX(mouseX));
        int logicalY = (int) Math.floor(screen.logicalMouseY(mouseY));
        assertTrue(ingredient.contains(mouseX, mouseY));
        assertFalse(ingredient.contains(logicalX, logicalY));
        ResponsiveScreenTestFixture.set(screen, "rawMouseX", mouseX);
        ResponsiveScreenTestFixture.set(screen, "rawMouseY", mouseY);
        ResponsiveScreenTestFixture.set(screen, "renderingScaledContent", true);
        // JEI 19.54's NeoForge event adapter already sets an identity XY pose.
        graphics.pose().translate(0, 0, 200);
        var before = new Matrix4f(graphics.pose().last().pose());
        int[] calls = {0};
        foreground(screen, graphics, logicalX, logicalY, args -> {
            calls[0]++;
            assertTrue(ingredient.contains((int) args[2], (int) args[3]));
            assertEquals(mouseX, args[2]);
            assertEquals(mouseY, args[3]);
            assertEquals(before, graphics.pose().last().pose());
            // Nested/external passes must not convert the raw coordinates a second time.
            screen.renderExternalOverlay(graphics, mouseX, mouseY, (gui, x, y, tick) -> {
                assertEquals(mouseX, x);
                assertEquals(mouseY, y);
            });
            return null;
        });
        assertEquals(1, calls[0]);
        assertEquals(before, graphics.pose().last().pose());
    }

    @Test
    void bothPassesReadVisualExclusionsAndRestoreTheMachinePoseOnFailure() throws Exception {
        var screen = ResponsiveScreenTestFixture.create(854, 300, 332, 368);
        var logicalArea = new Rect2i(245, -24, 18, 18);
        ResponsiveScreenTestFixture.set(screen, "widgets",
                new appeng.client.gui.WidgetContainer(new appeng.client.gui.style.ScreenStyle()) {
                    @Override public void addExclusionZones(List<Rect2i> areas, Rect2i bounds) {
                        areas.add(logicalArea);
                    }
                });
        var visual = screen.getExclusionZones().getFirst();
        var graphics = new UiRenderRecorder();
        graphics.pose().translate(295, 4, 200);
        graphics.pose().scale(0.793F, 0.793F, 1);
        var machinePose = new Matrix4f(graphics.pose().last().pose());
        ResponsiveScreenTestFixture.set(screen, "renderingScaledContent", true);
        Operation<Void> overlay = args -> {
            assertEquals(new Matrix4f().translation(0, 0, 200), graphics.pose().last().pose());
            var area = screen.getExclusionZones().getFirst();
            assertEquals(visual.getX(), area.getX());
            assertEquals(visual.getY(), area.getY());
            assertEquals(visual.getWidth(), area.getWidth());
            throw new IllegalStateException("overlay failure");
        };
        var background = JeiResponsiveRenderMixin.class.getDeclaredMethod(
                "molecularmanipulator$screenBackground", Screen.class, GuiGraphics.class, Operation.class);
        background.setAccessible(true);
        assertInstanceOf(IllegalStateException.class, assertThrows(InvocationTargetException.class,
                () -> background.invoke(new JeiResponsiveRenderMixin() {}, screen, graphics, overlay)).getCause());
        assertEquals(machinePose, graphics.pose().last().pose());
        assertSame(logicalArea, screen.getExclusionZones().getFirst());
        assertInstanceOf(IllegalStateException.class, assertThrows(InvocationTargetException.class,
                () -> foreground(screen, graphics, 700, 96, overlay)).getCause());
        assertEquals(machinePose, graphics.pose().last().pose());
        assertSame(logicalArea, screen.getExclusionZones().getFirst());
    }

    @Test
    void outsideMachineRenderingKeepsTheEventCoordinatesAndPose() throws Exception {
        for (Screen screen : new Screen[] {null,
                ResponsiveScreenTestFixture.create(854, 480, 332, 368),
                ResponsiveScreenTestFixture.create(854, 300, 332, 368)}) {
            var graphics = new UiRenderRecorder();
            graphics.pose().translate(12, 25, 200);
            var before = new Matrix4f(graphics.pose().last().pose());
            foreground(screen, graphics, 710, 113, args -> {
                assertSame(screen, args[0]);
                assertSame(graphics, args[1]);
                assertEquals(710, args[2]);
                assertEquals(113, args[3]);
                assertEquals(before, graphics.pose().last().pose());
                return null;
            });
            assertEquals(before, graphics.pose().last().pose());
        }
    }

    private static void foreground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
            Operation<Void> original) throws Exception {
        var method = JeiResponsiveRenderMixin.class.getDeclaredMethod(
                "molecularmanipulator$screenForeground", Screen.class, GuiGraphics.class,
                int.class, int.class, Operation.class);
        method.setAccessible(true);
        method.invoke(new JeiResponsiveRenderMixin() {}, screen, graphics, mouseX, mouseY, original);
    }
}
