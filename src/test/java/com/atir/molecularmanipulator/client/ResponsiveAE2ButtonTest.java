package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.client.gui.widgets.AE2Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ResponsiveAE2ButtonTest {
    private final UiRenderRecorder.MetricsFont font = new UiRenderRecorder.MetricsFont();

    private static UiRenderRecorder scaledGraphics() {
        var graphics = new UiRenderRecorder();
        float scale = 232F / 286F;
        graphics.pose().translate((567 - 430 * scale) / 2F, 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-68, 23, 0);
        return graphics;
    }

    @Test
    void nativeLongCaptionReproducesTheOffscreenClipAndFixedButtonKeepsItVisible() {
        var old = scaledGraphics();
        AE2Button.renderButtonText(old, font, Component.literal("Auto Craft"), 337, -19, 379, -1, 0, 0xFFFFFFFF);
        assertEquals(-19, old.scissors.getFirst().getY());
        var graphics = scaledGraphics();
        var before = new Matrix4f(graphics.pose().last().pose());
        ResponsiveAE2Button.renderButtonText(graphics, font, Component.literal("Auto Craft"),
                337, -19, 379, -1, 0, 0xFFFFFFFF);
        assertEquals(1, graphics.text.size());
        assertEquals("Auto Craft", graphics.text.getFirst().value());
        var clip = graphics.scissors.getFirst();
        assertEquals(327, clip.getX());
        assertEquals(7, clip.getY());
        assertEquals(35, clip.getWidth());
        assertEquals(15, clip.getHeight());
        assertEquals(0, graphics.clipDepth);
        assertEquals(before, graphics.pose().last().pose());
    }

    @Test
    void shortCaptionsAndUnscaledLongCaptionsKeepNativeTextBehavior() {
        for (String label : new String[] {"Build", "自动合成", "A long translated output direction"}) {
            var expected = new UiRenderRecorder();
            var actual = new UiRenderRecorder();
            AE2Button.renderButtonText(expected, font, Component.literal(label), 3, 0, 45, 18, 1, 0xFFFFFFFF);
            ResponsiveAE2Button.renderButtonText(actual, font, Component.literal(label), 3, 0, 45, 18, 1, 0xFFFFFFFF);
            assertEquals(expected.clips, actual.clips);
            assertEquals(1, actual.text.size());
            assertEquals(label, actual.text.getFirst().value());
            assertEquals(expected.text.getFirst().y(), actual.text.getFirst().y());
            assertEquals(expected.text.getFirst().shadow(), actual.text.getFirst().shadow());
            assertEquals(0, actual.clipDepth);
        }
    }

    @Test
    void ldlibScissorsAlreadyFollowThePoseAndMustNotBeTransformedTwice() {
        var graphics = scaledGraphics();
        var context = new GUIContext();
        context.graphics = graphics;
        context.enableScissor(337, -19, 42, 18);
        var clip = graphics.scissors.getFirst();
        assertEquals(327, clip.getX());
        assertEquals(7, clip.getY());
        assertEquals(35, clip.getWidth());
        assertEquals(15, clip.getHeight());
        context.disableScissor();
        assertEquals(0, graphics.clipDepth);
    }

    @Test
    void ldlibRawMouseIsInvertedOnceIntoMachineCoordinates() {
        var graphics = scaledGraphics();
        var context = GUIContext.of(null, graphics, 327, 12, 0);
        assertEquals(336.625F, context.localMouseX, 0.1F);
        assertEquals(-13.138F, context.localMouseY, 0.1F);
    }
}
