package com.atir.molecularmanipulator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Console button with separate focus, selection, primary-action and confirmation states. */
class OmniButton extends Button {
    private boolean selected;
    private boolean prominent;
    private boolean danger;

    OmniButton(int x, int y, int width, int height, Component text, OnPress onPress) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
    }

    void setSelected(boolean value) { selected = value; }
    void setProminent(boolean value) { prominent = value; }
    void setDanger(boolean value) { danger = value; }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean filled = selected || prominent && active;
        OmniUiTheme.buttonSurface(graphics, getX(), getY(), getX() + width, getY() + height,
                isHoveredOrFocused(), active, filled, danger);
        renderButtonText(graphics, Minecraft.getInstance().font, 3,
                OmniUiTheme.buttonText(active, filled, danger), 0);
    }

    protected void renderButtonText(GuiGraphics graphics, Font font, int padding, int color, int yOffset) {
        renderButtonText(graphics, font, getMessage(), getX() + padding, getY(),
                getX() + width - padding, getY() + height, yOffset, color);
    }

    static void renderButtonText(GuiGraphics graphics, Font font, Component text,
            int left, int top, int right, int bottom, int yOffset, int color) {
        ConsoleTextRenderer.scrolling(graphics, font, text, left, top + yOffset, right, bottom + yOffset, color);
    }
}
