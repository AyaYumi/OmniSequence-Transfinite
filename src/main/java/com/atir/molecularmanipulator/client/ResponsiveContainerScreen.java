package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.StackWithBounds;
import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Keeps oversized controller screens usable at high GUI scales without moving
 * their slots away from the matching server-side menu coordinates.
 */
public abstract class ResponsiveContainerScreen<T extends AEBaseMenu>
        extends RestorableContainerScreen<T> {
    private static final int SCREEN_MARGIN = 4;
    private boolean renderingScaledContent;
    private boolean dispatchingLogicalInput;
    private int rawMouseX;
    private int rawMouseY;

    protected ResponsiveContainerScreen(T menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        restoreScreenWidgets();
        rawMouseX = mouseX;
        rawMouseY = mouseY;
        float scale = responsiveScale();
        if (scale >= 1.0F) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int logicalMouseX = (int) Math.floor(toLogicalX(mouseX, scale));
        int logicalMouseY = (int) Math.floor(toLogicalY(mouseY, scale));
        renderTransparentBackground(graphics);
        screenContent.render(graphics, renderables, mouseX, mouseY, partialTick, () -> {
            renderingScaledContent = true;
            graphics.pose().pushPose();
            try {
                applyResponsiveTransform(graphics, scale);
                super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
            } finally {
                graphics.pose().popPose();
                renderingScaledContent = false;
            }
        });
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (renderingScaledContent) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (responsiveScale() < 1.0F) {
            for (var child : externalChildren()) child.mouseMoved(mouseX, mouseY);
        }
        super.mouseMoved(logicalMouseX(mouseX), logicalMouseY(mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        restoreScreenWidgets();
        if (responsiveScale() < 1.0F) {
            for (var child : externalChildren()) {
                if (child.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(child);
                    if (button == 0) setDragging(true);
                    return true;
                }
            }
        }
        return withLogicalInput(() -> super.mouseClicked(logicalMouseX(mouseX), logicalMouseY(mouseY), button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (hasExternalFocus()) {
            setDragging(false);
            return getFocused().mouseReleased(mouseX, mouseY, button);
        }
        return withLogicalInput(() -> super.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        float scale = responsiveScale();
        if (hasExternalFocus()) {
            return isDragging() && button == 0 && getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return withLogicalInput(() -> super.mouseDragged(
                logicalMouseX(mouseX),
                logicalMouseY(mouseY),
                button,
                dragX / scale,
                dragY / scale));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (responsiveScale() < 1.0F) {
            for (var child : externalChildren()) {
                if (child.isMouseOver(mouseX, mouseY) && child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
            }
        }
        return withLogicalInput(() -> super.mouseScrolled(
                logicalMouseX(mouseX),
                logicalMouseY(mouseY),
                scrollX,
                scrollY));
    }

    @Override
    public List<? extends GuiEventListener> children() {
        var children = super.children();
        return dispatchingLogicalInput ? children.stream().filter(screenContent::owns).toList() : children;
    }

    private List<? extends GuiEventListener> externalChildren() {
        return super.children().stream().filter(child -> !screenContent.owns(child)).toList();
    }

    private boolean hasExternalFocus() {
        return responsiveScale() < 1.0F && getFocused() != null && !screenContent.owns(getFocused());
    }

    private boolean withLogicalInput(BooleanSupplier action) {
        dispatchingLogicalInput = responsiveScale() < 1.0F;
        try {
            return action.getAsBoolean();
        } finally {
            dispatchingLogicalInput = false;
        }
    }

    public final Rect2i responsiveBounds() {
        return responsiveArea(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
    }

    public final Rect2i responsiveSlotBounds(Slot slot) {
        return responsiveArea(new Rect2i(leftPos + slot.x, topPos + slot.y, 16, 16));
    }

    final Rect2i responsiveArea(Rect2i area) {
        int left = responsiveScreenX(area.getX());
        int top = responsiveScreenY(area.getY());
        return new Rect2i(left, top,
                Math.max(1, responsiveScreenX(area.getX() + area.getWidth()) - left),
                Math.max(1, responsiveScreenY(area.getY() + area.getHeight()) - top));
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        var areas = super.getExclusionZones();
        return renderingScaledContent || responsiveScale() >= 1.0F
                ? areas : areas.stream().map(this::responsiveArea).toList();
    }

    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var stack = super.getStackUnderMouse(mouseX, mouseY);
        return stack == null || responsiveScale() >= 1.0F
                ? stack : new StackWithBounds(stack.stack(), responsiveArea(stack.bounds()));
    }

    @Override
    public void setTooltipForNextRenderPass(List<FormattedCharSequence> tooltip,
            ClientTooltipPositioner positioner, boolean override) {
        super.setTooltipForNextRenderPass(
                tooltip,
                renderingScaledContent ? DefaultTooltipPositioner.INSTANCE : positioner,
                override);
    }

    /**
     * Registers an LDLib2 widget without passing the already transformed logical
     * mouse coordinates back into LDLib2's pose-aware hit test.
     *
     * <p>Container input is dispatched through {@link #addWidget} and therefore
     * still receives the logical coordinates produced by this screen. Rendering is
     * registered separately so LDLib2 receives the original screen coordinates;
     * its GUI context then applies the inverse responsive pose exactly once.</p>
     */
    protected final void addResponsiveModularWidget(ModularUI.ModularUIWidget widget) {
        addScreenWidget(widget, (graphics, ignoredMouseX, ignoredMouseY, partialTick) ->
                widget.render(graphics, rawMouseX, rawMouseY, partialTick));
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
