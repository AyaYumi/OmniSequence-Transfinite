package com.atir.molecularmanipulator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Single-pass console text. Vanilla text helpers normally add a dark shadow. */
public final class ConsoleTextRenderer {
    private ConsoleTextRenderer() { }

    static void scrolling(GuiGraphics graphics, Font font, Component text,
            int left, int top, int right, int bottom, int color) {
        int available = right - left;
        if (available <= 0) return;
        int width = font.width(text);
        int y = (top + bottom - font.lineHeight) / 2 + 1;
        if (width <= available) {
            graphics.drawString(font, text, (left + right - width) / 2, y, color, false);
            return;
        }
        int overflow = width - available;
        long period = Math.max(3000L, overflow * 500L);
        double phase = (Util.getMillis() % period) / (double) period;
        int offset = (int) Math.round((0.5 - 0.5 * Math.cos(phase * Math.PI * 2)) * overflow);
        graphics.enableScissor(left, top, right, bottom);
        try {
            graphics.drawString(font, text, left - offset, y, color, false);
        } finally {
            graphics.disableScissor();
        }
    }

    static void centered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /**
     * A reused drawing adapter for EditBox, preserving its editing and selection code.
     * Only EditBox's drawing operations are forwarded; it never owns a buffer or pose.
     */
    static final class FieldGraphics extends GuiGraphics {
        private GuiGraphics target;

        FieldGraphics() { super(null, null); }
        void bind(GuiGraphics target) { this.target = target; }
        void clear() { target = null; }

        @Override public PoseStack pose() { return target.pose(); }
        @Override public MultiBufferSource.BufferSource bufferSource() { return target.bufferSource(); }

        @Override public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
            int end = target.drawString(font, text, x, y, color == -8355712 ? OmniUiTheme.FIELD_HINT : color, false);
            return end + (shadow && text != null ? 1 : 0);
        }

        @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            // EditBox subtracts the shadow's extra pixel when rendering after the
            // caret. Keep that advance, while emitting no shadow glyphs at all.
            return target.drawString(font, text, x, y, color, false) + (shadow ? 1 : 0);
        }

        @Override public void fill(int left, int top, int right, int bottom, int color) {
            target.fill(left, top, right, bottom, color);
        }

        @Override public void fill(RenderType type, int left, int top, int right, int bottom, int color) {
            target.fill(type, left, top, right, bottom,
                    type == RenderType.guiOverlay() ? OmniUiTheme.FIELD_TEXT : color);
        }
    }
}
