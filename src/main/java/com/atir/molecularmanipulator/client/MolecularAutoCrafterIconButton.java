package com.atir.molecularmanipulator.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Uses AE2's native icons with optional compact, centered rendering. */
final class MolecularAutoCrafterIconButton extends IconButton {
    private Icon icon;
    private final int compactIconSize;
    private boolean selected;

    MolecularAutoCrafterIconButton(int x, int y, Icon icon, Component message, Runnable action) {
        this(x, y, -1, -1, icon, message, action);
    }

    MolecularAutoCrafterIconButton(int x, int y, int buttonSize, int iconSize,
            Icon icon, Component message, Runnable action) {
        super(button -> action.run());
        this.icon = icon;
        compactIconSize = iconSize;
        setPosition(x, y);
        if (buttonSize > 0) {
            setWidth(buttonSize);
            setHeight(buttonSize);
        }
        setMessage(message);
        if (!message.getString().isBlank()) setTooltip(Tooltip.create(message));
    }

    void setIcon(Icon icon) {
        this.icon = icon;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        // Keep AE2's 16px toolbar layout; the 18x20 texture includes an overhang
        // and a bottom shadow, neither of which belongs in the icon's content area.
        boolean compact = compactIconSize > 0;
        int frameX = compact ? getX() : getX() - 1;
        int frameWidth = compact ? getWidth() : 18;
        int frameHeight = compact ? getHeight() : 20;
        int faceHeight = compact ? getHeight() - 2 : 18;
        boolean hovered = active && isHovered();
        boolean keyboardFocus = isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
        int frameY = getY() + (hovered ? 1 : 0);
        Icon background = Icon.TOOLBAR_BUTTON_BACKGROUND;
        if (selected || hovered || keyboardFocus) {
            graphics.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight,
                    OmniUiTheme.BUTTON_HOVER_SHADE);
        }
        background.getBlitter()
                .dest(frameX, frameY, frameWidth, frameHeight)
                .blit(graphics);

        int limit = compactIconSize > 0 ? compactIconSize : 16;
        // Preserve native glyph pixels and their transparent gutter inside the larger controls.
        int iconWidth = Math.min(icon.width, limit);
        int iconHeight = Math.min(icon.height, limit);
        int iconX = frameX + (frameWidth - iconWidth) / 2;
        int iconY = frameY + (faceHeight - iconHeight) / 2;
        var iconBlitter = icon.getBlitter();
        if (!active) iconBlitter.opacity(0.5F);
        iconBlitter.dest(iconX, iconY, iconWidth, iconHeight).blit(graphics);
    }

    @Override
    protected Icon getIcon() {
        return icon;
    }
}
