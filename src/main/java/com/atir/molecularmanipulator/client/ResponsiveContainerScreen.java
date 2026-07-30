package com.atir.molecularmanipulator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Keeps oversized controller screens usable at high GUI scales without moving
 * their slots away from the matching server-side menu coordinates.
 */
abstract class ResponsiveContainerScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {
    private static final int SCREEN_MARGIN = 4;

    protected ResponsiveContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        float scale = responsiveScale();
        if (scale >= 1.0F) {
            super.render(graphics, mouseX, mouseY, partialTick);
            renderTooltip(graphics, mouseX, mouseY);
            return;
        }

        int logicalMouseX = (int) Math.floor(toLogicalX(mouseX, scale));
        int logicalMouseY = (int) Math.floor(toLogicalY(mouseY, scale));
        graphics.pose().pushPose();
        try {
            applyResponsiveTransform(graphics, scale);
            super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
        renderTooltip(graphics, mouseX, mouseY);
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

    private void applyResponsiveTransform(GuiGraphics graphics, float scale) {
        graphics.pose().translate(visualLeft(scale), visualTop(scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-leftPos, -topPos, 0.0F);
    }
}
