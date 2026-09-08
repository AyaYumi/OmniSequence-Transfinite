package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ConsoleTextRenderingTest {
    private final UiRenderRecorder.MetricsFont font = new UiRenderRecorder.MetricsFont();

    @Test void buttonsDrawOneShadowlessLabelAtEveryScale() {
        for (float scale : new float[] {1, 2, 3, 0.83F}) {
            for (String label : new String[] {"<", ">", "输出ME", "设置", "开始分解"}) {
                var graphics = new UiRenderRecorder();
                graphics.pose().scale(scale, scale, 1);
                OmniButton.renderButtonText(graphics, font, Component.literal(label), 2, 0, 98, 20, 0, OmniUiTheme.PRIMARY_TEXT);
                assertEquals(1, graphics.text.size(), label);
                assertFalse(graphics.text.get(0).shadow(), label);
                assertEquals(label, graphics.text.get(0).value());
                assertEquals(0, graphics.clipDepth);
            }
        }
    }

    @Test void longButtonLabelsRestoreTheirClipAndNeverRequestShadow() {
        var graphics = new UiRenderRecorder();
        OmniButton.renderButtonText(graphics, font, Component.literal("Very long translated button caption"), 3, 0, 37, 20, 0, OmniUiTheme.PRIMARY_TEXT);
        assertEquals(1, graphics.clips);
        assertEquals(0, graphics.clipDepth);
        assertEquals(1, graphics.text.size());
        assertFalse(graphics.text.get(0).shadow());
    }

    @Test void longEnglishTabUsesTheVisibleClipAfterResponsiveScaling() {
        var graphics = new UiRenderRecorder();
        float scale = 232F / 286F;
        graphics.pose().translate((567 - 430 * scale) / 2F, 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-68, 23, 0);
        var before = new org.joml.Matrix4f(graphics.pose().last().pose());
        OmniButton.renderButtonText(graphics, font, Component.literal("Auto Craft"),
                337, -19, 379, -1, 0, OmniUiTheme.PRIMARY_TEXT);
        assertEquals(1, graphics.text.size());
        assertEquals("Auto Craft", graphics.text.get(0).value());
        assertEquals(1, graphics.scissors.size());
        var clip = graphics.scissors.get(0);
        assertEquals(327, clip.getX());
        assertEquals(7, clip.getY());
        assertEquals(35, clip.getWidth());
        assertEquals(15, clip.getHeight());
        assertEquals(0, graphics.clipDepth);
        assertEquals(before, graphics.pose().last().pose());
    }

    @Test void inputAndHintHaveNoShadowOrDuplicateGlyphPass() {
        var field = OmniUiTheme.tallTextField(null, font, 10, 10, 70, 16, Component.literal("搜索样板"), null);
        var graphics = new UiRenderRecorder();
        field.renderWidget(graphics, -1, -1, 0);
        assertEquals(1, graphics.text.size());
        assertEquals("搜索样板", graphics.text.get(0).value());
        assertFalse(graphics.text.get(0).shadow());
        graphics.text.clear();
        field.setValue("64");
        field.renderWidget(graphics, -1, -1, 0);
        assertEquals(1, graphics.text.size());
        assertEquals("64", graphics.text.get(0).value());
        assertFalse(graphics.text.get(0).shadow());
    }

    @Test void inputCaretSplitPreservesVanillaAdvanceWithoutOverlappingCharacters() {
        var field = OmniUiTheme.tallTextField(null, font, 10, 10, 90, 16, Component.empty(), null);
        field.setValue("6482");
        field.setCursorPosition(2);
        field.setHighlightPos(2);
        var graphics = new UiRenderRecorder();
        field.renderWidget(graphics, -1, -1, 0);
        assertEquals(2, graphics.text.size());
        var first = graphics.text.get(0); var second = graphics.text.get(1);
        assertEquals("64", first.value()); assertEquals("82", second.value());
        assertEquals(first.x() + font.width(first.value()), second.x());
        assertFalse(first.shadow()); assertFalse(second.shadow());
    }

    @Test void focusSelectionAndSuggestionsKeepEditingBehaviorAndSinglePassText() {
        var field = OmniUiTheme.tallTextField(null, font, 10, 10, 90, 16, Component.empty(), null);
        field.setFocused(true);
        field.setValue("6482");
        field.setCursorPosition(2);
        field.setHighlightPos(4);
        var graphics = new UiRenderRecorder();
        field.renderWidget(graphics, -1, -1, 0);
        assertTrue(graphics.rects.stream().anyMatch(r -> r.selection() && r.color() == OmniUiTheme.FIELD_TEXT),
                "The in-text caret must remain visible on the dark field");
        assertTrue(graphics.rects.stream().anyMatch(r -> r.selection() && r.color() == 0xFF0000FF),
                "Vanilla selected-text overlay must still be forwarded");
        assertTrue(graphics.text.stream().noneMatch(UiRenderRecorder.Text::shadow));

        field.insertText("金");
        assertEquals("64金", field.getValue(), "Editing still replaces the native selection");
        field.setSuggestion("样板");
        graphics.text.clear();
        field.renderWidget(graphics, -1, -1, 0);
        assertTrue(graphics.text.stream().anyMatch(t -> t.value().equals("样板")));
        assertTrue(graphics.text.stream().anyMatch(t -> t.value().equals("_")));
        assertTrue(graphics.text.stream().noneMatch(UiRenderRecorder.Text::shadow));
    }

    @Test void longInputScrollsInsideTheFieldAtDifferentScales() {
        for (float scale : new float[] {0.83F, 1, 2, 3}) {
            var field = OmniUiTheme.tallTextField(null, font, 10, 10, 70, 16, Component.empty(), null);
            field.setFocused(true);
            for (char character : "12345678901234567890".toCharArray()) field.charTyped(character, 0);
            field.setFocused(false);
            var graphics = new UiRenderRecorder();
            graphics.pose().scale(scale, scale, 1);
            field.renderWidget(graphics, -1, -1, 0);
            assertEquals(1, graphics.text.size());
            var run = graphics.text.get(0);
            assertFalse(run.shadow());
            assertTrue(run.value().endsWith("7890"), run.value());
            assertTrue(run.x() >= 13 && run.x() + font.width(run.value()) <= 77);
        }
    }
}
