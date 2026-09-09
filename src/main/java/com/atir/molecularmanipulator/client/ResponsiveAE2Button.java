package com.atir.molecularmanipulator.client;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;

/** Retains AE2's button rendering while moving its text clip into screen coordinates. */
class ResponsiveAE2Button extends AE2Button {
    ResponsiveAE2Button(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress);
    }

    @Override
    protected void renderButtonText(GuiGraphics graphics, Font font, int padding, int color, int yOffset) {
        renderButtonText(graphics, font, getMessage(), getX() + padding, getY(),
                getX() + getWidth() - padding, getY() + getHeight(), yOffset, color);
    }

    public static void renderButtonText(GuiGraphics graphics, Font font, Component text,
            int left, int top, int right, int bottom, int yOffset, int color) {
        AE2Button.renderButtonText(new TextClipGraphics(graphics), font, text, left, top, right, bottom, yOffset, color);
    }

    private static final class TextClipGraphics extends GuiGraphics {
        private final GuiGraphics target;

        private TextClipGraphics(GuiGraphics target) { super(Minecraft.getInstance(), target.bufferSource()); this.target = target; }

        @Override public void enableScissor(int left, int top, int right, int bottom) {
            var matrix = target.pose().last().pose();
            var start = matrix.transformPosition(new Vector3f(left, top, 0));
            var end = matrix.transformPosition(new Vector3f(right, bottom, 0));
            target.enableScissor((int) Math.floor(start.x), (int) Math.floor(start.y),
                    (int) Math.ceil(end.x), (int) Math.ceil(end.y));
        }

        @Override public void disableScissor() { target.disableScissor(); }
        @Override public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
            return target.drawString(font, text, x, y, color, shadow);
        }
        @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            return target.drawString(font, text, x, y, color, shadow);
        }
    }
}
