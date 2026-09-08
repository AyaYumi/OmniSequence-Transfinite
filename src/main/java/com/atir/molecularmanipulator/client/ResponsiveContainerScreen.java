package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Keeps oversized controller screens usable at high GUI scales without moving
 * their slots away from the matching server-side menu coordinates.
 */
abstract class ResponsiveContainerScreen<T extends AEBaseMenu>
        extends RestorableContainerScreen<T> {
    private static final int SCREEN_MARGIN = 4;
    private boolean renderingScaledContent;

    protected ResponsiveContainerScreen(T menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected appeng.client.guidebook.PageAnchor getHelpTopic() {
        // AE2 15 has no shouldAddToolbar hook. Suppress its automatic guide tab
        // through the supported hook to match the 1.21.1 screen layout.
        return null;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        // AE2 still supplies slot/widget events; the console owns the complete visual surface.
        OmniUiTheme.console(graphics, x, y, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float scale = responsiveScale();
        if (scale >= 1.0F) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int logicalMouseX = (int) Math.floor(toLogicalX(mouseX, scale));
        int logicalMouseY = (int) Math.floor(toLogicalY(mouseY, scale));
        super.renderBackground(graphics);
        renderingScaledContent = true;
        graphics.pose().pushPose();
        try {
            applyResponsiveTransform(graphics, scale);
            super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
        } finally {
            graphics.pose().popPose();
            renderingScaledContent = false;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        if (!renderingScaledContent) super.renderBackground(graphics);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(logicalMouseX(mouseX), logicalMouseY(mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        float scale = responsiveScale();
        return super.mouseDragged(
                logicalMouseX(mouseX),
                logicalMouseY(mouseY),
                button,
                dragX / scale,
                dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return super.mouseScrolled(
                logicalMouseX(mouseX),
                logicalMouseY(mouseY),
                scrollY);
    }

    @Override
    public void setTooltipForNextRenderPass(List<FormattedCharSequence> tooltip,
            ClientTooltipPositioner positioner, boolean override) {
        super.setTooltipForNextRenderPass(
                tooltip,
                responsiveScale() < 1.0F ? DefaultTooltipPositioner.INSTANCE : positioner,
                override);
    }

    private float responsiveScale() {
        float availableWidth = Math.max(1, width - SCREEN_MARGIN * 2);
        float availableHeight = Math.max(1, height - SCREEN_MARGIN * 2);
        return Math.min(1.0F, Math.min(availableWidth / imageWidth, availableHeight / imageHeight));
    }

    private float visualLeft(float scale) {
        return (width - imageWidth * scale) * 0.5F;
    }

    private float visualTop(float scale) {
        return (height - imageHeight * scale) * 0.5F;
    }

    private double toLogicalX(double mouseX, float scale) {
        return leftPos + (mouseX - visualLeft(scale)) / scale;
    }

    private double toLogicalY(double mouseY, float scale) {
        return topPos + (mouseY - visualTop(scale)) / scale;
    }

    protected final double logicalMouseX(double mouseX) {
        float scale = responsiveScale();
        return scale >= 1.0F ? mouseX : toLogicalX(mouseX, scale);
    }

    protected final double logicalMouseY(double mouseY) {
        float scale = responsiveScale();
        return scale >= 1.0F ? mouseY : toLogicalY(mouseY, scale);
    }

    public final int responsiveScreenX(int logicalX) {
        float scale = responsiveScale();
        return scale >= 1.0F
                ? logicalX
                : Math.round(visualLeft(scale) + (logicalX - leftPos) * scale);
    }

    public final int responsiveScreenY(int logicalY) {
        float scale = responsiveScale();
        return scale >= 1.0F
                ? logicalY
                : Math.round(visualTop(scale) + (logicalY - topPos) * scale);
    }

    public final int responsiveScreenLength(int logicalLength) {
        return Math.max(1, Math.round(logicalLength * responsiveScale()));
    }

    protected final void drawFittedString(GuiGraphics graphics, Component text,
            int x, int y, int maxWidth, int color) {
        drawScaledString(graphics, text, x, y, maxWidth, color, TextAlignment.LEFT);
    }

    protected final void drawCenteredFittedString(GuiGraphics graphics, Component text,
            int centerX, int y, int maxWidth, int color) {
        drawScaledString(graphics, text, centerX, y, maxWidth, color, TextAlignment.CENTER);
    }

    protected final void drawRightAlignedFittedString(GuiGraphics graphics, Component text,
            int right, int y, int maxWidth, int color) {
        drawScaledString(graphics, text, right, y, maxWidth, color, TextAlignment.RIGHT);
    }

    protected final void drawKeyValueRow(GuiGraphics graphics, Component key, Component value,
            int left, int right, int y, int gap, int keyColor, int valueColor) {
        int available = Math.max(1, right - left);
        int contentWidth = Math.max(1, available - gap);
        int keyWidth = font.width(key);
        int valueWidth = font.width(value);
        if (keyWidth + valueWidth <= contentWidth) {
            graphics.drawString(font, key, left, y, keyColor, false);
            graphics.drawString(font, value, right - valueWidth, y, valueColor, false);
            return;
        }

        int valueArea;
        if (valueWidth <= contentWidth / 2) {
            valueArea = Math.max(1, valueWidth);
        } else {
            int combinedWidth = Math.max(1, keyWidth + valueWidth);
            valueArea = Math.max(1,
                    Math.round(contentWidth * valueWidth / (float) combinedWidth));
        }
        valueArea = Math.min(contentWidth - 1, valueArea);
        int keyArea = Math.max(1, contentWidth - valueArea);
        drawFittedString(graphics, key, left, y, keyArea, keyColor);
        drawRightAlignedFittedString(graphics, value, right, y, valueArea, valueColor);
    }

    protected final int drawWrappedString(GuiGraphics graphics, Component text,
            int x, int y, int maxWidth, int maxLines, int lineHeight, int color) {
        if (maxWidth <= 0 || maxLines <= 0) {
            return 0;
        }
        var lines = font.split(text, maxWidth);
        int renderedLines = Math.min(maxLines, lines.size());
        for (int line = 0; line < renderedLines; line++) {
            graphics.drawString(font, lines.get(line), x, y + line * lineHeight, color, false);
        }
        return renderedLines;
    }

    private void drawScaledString(GuiGraphics graphics, Component text,
            int anchorX, int y, int maxWidth, int color, TextAlignment alignment) {
        if (maxWidth <= 0) {
            return;
        }
        int textWidth = font.width(text);
        if (textWidth <= 0) {
            return;
        }
        float scale = Math.min(1.0F, maxWidth / (float) textWidth);
        float renderedWidth = textWidth * scale;
        float x = switch (alignment) {
            case LEFT -> anchorX;
            case CENTER -> anchorX - renderedWidth * 0.5F;
            case RIGHT -> anchorX - renderedWidth;
        };
        if (scale >= 1.0F) {
            graphics.drawString(font, text, Math.round(x), y, color, false);
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, 0, 0, color, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void applyResponsiveTransform(GuiGraphics graphics, float scale) {
        graphics.pose().translate(visualLeft(scale), visualTop(scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-leftPos, -topPos, 0.0F);
    }

    private enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT
    }
}
