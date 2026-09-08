package com.atir.molecularmanipulator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Square AE-style radio indicator for each independent crafting lane. */
final class OmniToggle extends Button {
    private boolean selected;

    OmniToggle(int x, int y, int width, int height, Component label, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
    }

    void setSelected(boolean value) { selected = value; }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = isHoveredOrFocused() ? OmniUiTheme.SOFT_ACCENT : OmniUiTheme.HIGHLIGHT;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, OmniUiTheme.BUTTON_SHADE);
        if (selected) graphics.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3,
                active ? OmniUiTheme.SOFT_ACCENT : OmniUiTheme.BORDER);
    }
}
