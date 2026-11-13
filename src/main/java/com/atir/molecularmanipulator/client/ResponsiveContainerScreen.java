package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.AEBaseMenu;
import com.atir.molecularmanipulator.menu.BuildingMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
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
    private static final int TERMINAL_BUTTON_SPACE = 24;
    private static final int BLOCKED_MOUSE = -10000;
    private boolean renderingScaledContent;
    private boolean renderingWirelessPanel;
    private boolean dispatchingLogicalInput;
    private int rawMouseX;
    private int rawMouseY;
    private TabButton terminalButton;
    private net.minecraft.client.gui.components.Button wirelessButton;
    private SequenceWirelessPanel wirelessPanel;

    protected ResponsiveContainerScreen(T menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        addTerminalButton();
        addWirelessButton();
        positionWirelessPanel();
        if (wirelessPanel != null) {
            addWidget(wirelessPanel);
            setFocused(wirelessPanel);
        }
    }

    private void addWirelessButton() {
        wirelessButton = null;
        if (!(menu instanceof BuildingMenu building)) return;
        wirelessButton = SequenceWirelessPanel.sideButton(leftPos + imageWidth + SequenceWirelessPanel.EDGE_GAP,
                topPos + 72, this::wirelessPanelOpen, () -> openWirelessPanel(building));
        wirelessButton.visible = hasWirelessHost(building);
        addScreenWidget(wirelessButton);
    }

    private boolean hasWirelessHost(BuildingMenu building) {
        return building.wirelessAvailable
                || com.atir.molecularmanipulator.integration.ae2.SequenceWirelessAccess.host(menu.getBlockEntity()) != null;
    }

    private void openWirelessPanel(BuildingMenu building) {
        wirelessPanel = new SequenceWirelessPanel(building, font, this::closeWirelessPanel);
        positionWirelessPanel();
        addWidget(wirelessPanel);
        setFocused(wirelessPanel);
        setDragging(false);
        hoveredSlot = null;
        building.requestWirelessOpen(true);
    }

    private void closeWirelessPanel() {
        if (wirelessPanel != null) removeWidget(wirelessPanel);
        wirelessPanel = null;
        setFocused(null);
        setDragging(false);
        if (menu instanceof BuildingMenu building) building.requestWirelessOpen(false);
    }

    private void positionWirelessPanel() {
        if (wirelessPanel != null) wirelessPanel.position(leftPos + (imageWidth - SequenceWirelessPanel.WIDTH) / 2,
                topPos + (imageHeight - SequenceWirelessPanel.HEIGHT) / 2);
    }

    protected final boolean wirelessPanelOpen() { return wirelessPanel != null; }

    private void renderWirelessPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (wirelessPanel == null) return;
        graphics.pose().pushPose();
        renderingWirelessPanel = true;
        try {
            graphics.pose().translate(0, 0, 600);
            graphics.fill(0, 0, width, height, 0x66211E2E);
            float scale = responsiveScale();
            if (scale < 1) applyResponsiveTransform(graphics, scale);
            wirelessPanel.render(graphics, (int) logicalMouseX(mouseX), (int) logicalMouseY(mouseY), partialTick);
        } finally {
            renderingWirelessPanel = false;
            graphics.pose().popPose();
        }
    }

    private void addTerminalButton() {
        terminalButton = null;
        if (menu instanceof BuildingMenu building) {
            // Native AE navigation, docked to the panel's upper-left edge without covering its title.
            terminalButton = new TabButton(Icon.BACK,
                    Component.translatable("gui.molecularmanipulator.building_terminal.return"),
                    button -> building.requestTerminal());
            terminalButton.setPosition(leftPos - TERMINAL_BUTTON_SPACE, topPos);
            terminalButton.visible = building.terminalAccess;
            addScreenWidget(terminalButton);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        restoreScreenWidgets();
        if (terminalButton != null && menu instanceof BuildingMenu building) {
            terminalButton.visible = building.terminalAccess;
        }
        if (wirelessButton != null && menu instanceof BuildingMenu building) wirelessButton.visible = hasWirelessHost(building);
        // Hover is recomputed by widget rendering, not just mouseMoved. The modal
        // alone receives the real pointer, including LDLib's raw-coordinate renderer.
        rawMouseX = wirelessPanel == null ? mouseX : BLOCKED_MOUSE;
        rawMouseY = wirelessPanel == null ? mouseY : BLOCKED_MOUSE;
        if (wirelessPanel != null) {
            // The opening button's click dispatcher may restore its focus after onPress.
            if (getFocused() != wirelessPanel) setFocused(wirelessPanel);
            setDragging(false);
            hoveredSlot = null;
        }
        float scale = responsiveScale();
        if (scale >= 1.0F) {
            super.render(graphics, rawMouseX, rawMouseY, partialTick);
            renderWirelessPanel(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int logicalMouseX = (int) Math.floor(toLogicalX(rawMouseX, scale));
        int logicalMouseY = (int) Math.floor(toLogicalY(rawMouseY, scale));
        renderTransparentBackground(graphics);
        screenContent.render(graphics, renderables, rawMouseX, rawMouseY, partialTick, () -> {
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
        renderWirelessPanel(graphics, mouseX, mouseY, partialTick);
    }

    /** Native AE2 slot silhouettes, without the opaque tile behind them. */
    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!(slot instanceof appeng.menu.slot.AppEngSlot nativeSlot) || !slot.getItem().isEmpty()
                || !slot.isActive() || !nativeSlot.isSlotEnabled()) {
            super.renderSlot(graphics, slot);
            return;
        }
        var icon=nativeSlot.getIcon();
        if (!com.atir.molecularmanipulator.client.gui.theme.OsqSlotGhosts.supports(icon)) {
            super.renderSlot(graphics, slot); return;
        }
        nativeSlot.setIcon(null);
        try { super.renderSlot(graphics, slot); }
        finally { nativeSlot.setIcon(icon); }
        com.atir.molecularmanipulator.client.gui.theme.OsqSlotGhosts.draw(graphics,icon,slot.x,slot.y);
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
        if (wirelessPanel != null) {
            wirelessPanel.mouseMoved(logicalMouseX(mouseX), logicalMouseY(mouseY));
            return;
        }
        if (responsiveScale() < 1.0F) {
            for (var child : externalChildren()) child.mouseMoved(mouseX, mouseY);
        }
        withLogicalInput(() -> {
            super.mouseMoved(logicalMouseX(mouseX), logicalMouseY(mouseY));
            return true;
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (wirelessPanel != null) return wirelessPanel.mouseClicked(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
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
        if (wirelessPanel != null) {
            wirelessPanel.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
            setDragging(false);
            return true;
        }
        if (hasExternalFocus()) {
            setDragging(false);
            return getFocused().mouseReleased(mouseX, mouseY, button);
        }
        return withLogicalInput(() -> super.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (wirelessPanel != null) return true;
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
        if (wirelessPanel != null) return wirelessPanel.mouseScrolled(logicalMouseX(mouseX), logicalMouseY(mouseY), scrollX, scrollY);
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
    public boolean keyPressed(int key, int scan, int modifiers) {
        return wirelessPanel != null ? wirelessPanel.keyPressed(key, scan, modifiers) : super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        return wirelessPanel != null || super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return wirelessPanel != null || super.charTyped(character, modifiers);
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

    /** Container render events run inside the fitted panel, but JEI draws in screen space. */
    public final void renderExternalOverlay(GuiGraphics graphics, int mouseX, int mouseY, Renderable overlay) {
        if (wirelessPanel != null) return;
        if (!renderingScaledContent) {
            overlay.render(graphics, mouseX, mouseY, 0);
            return;
        }
        float depth = graphics.pose().last().pose().m32();
        graphics.pose().pushPose();
        renderingScaledContent = false;
        try {
            graphics.pose().setIdentity();
            graphics.pose().translate(0, 0, depth);
            overlay.render(graphics, rawMouseX, rawMouseY, 0);
        } finally {
            graphics.pose().popPose();
            renderingScaledContent = true;
        }
    }

    public final Rect2i responsiveSlotBounds(Slot slot) {
        return responsiveArea(new Rect2i(leftPos + slot.x, topPos + slot.y, 16, 16));
    }

    /** Finds a menu slot from raw screen coordinates, accounting for the fitted panel scale. */
    public final Slot responsiveSlotAt(double mouseX, double mouseY) {
        if (wirelessPanel != null) return null;
        if (menu == null) return getSlotUnderMouse();
        int x = (int) mouseX;
        int y = (int) mouseY;
        for (var slot : menu.slots) {
            if (!slot.isActive() || slot.getItem().isEmpty()) continue;
            var area = responsiveSlotBounds(slot);
            if (area.contains(x, y)) return slot;
        }
        return null;
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
        if (terminalButton != null && terminalButton.visible) {
            areas.add(new Rect2i(terminalButton.getX(), terminalButton.getY(),
                    terminalButton.getWidth(), terminalButton.getHeight()));
        }
        if (wirelessButton != null && wirelessButton.visible) {
            areas.add(new Rect2i(wirelessButton.getX(), wirelessButton.getY(), wirelessButton.getWidth(), wirelessButton.getHeight()));
        }
        if (wirelessPanel != null) areas.add(new Rect2i(leftPos + (imageWidth - SequenceWirelessPanel.WIDTH) / 2,
                topPos + (imageHeight - SequenceWirelessPanel.HEIGHT) / 2, SequenceWirelessPanel.WIDTH, SequenceWirelessPanel.HEIGHT));
        return renderingScaledContent || responsiveScale() >= 1.0F
                ? areas : areas.stream().map(this::responsiveArea).toList();
    }

    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        if (wirelessPanel != null) return null;
        var stack = super.getStackUnderMouse(mouseX, mouseY);
        return stack == null || responsiveScale() >= 1.0F
                ? stack : new StackWithBounds(stack.stack(), responsiveArea(stack.bounds()));
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (wirelessPanel == null) super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void setTooltipForNextRenderPass(List<FormattedCharSequence> tooltip,
            ClientTooltipPositioner positioner, boolean override) {
        if (wirelessPanel != null && !renderingWirelessPanel) return;
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
        // Keep the panel centered while fitting its attached return button inside the viewport.
        int sideSpace = terminalButton != null && terminalButton.visible ? TERMINAL_BUTTON_SPACE : 0;
        if (wirelessButton != null && wirelessButton.visible) sideSpace = Math.max(sideSpace,
                SequenceWirelessPanel.EDGE_WIDTH + SequenceWirelessPanel.EDGE_GAP);
        int contentWidth = imageWidth + sideSpace * 2;
        int contentHeight = imageHeight;
        if (wirelessPanel != null) {
            contentWidth = Math.max(contentWidth, SequenceWirelessPanel.WIDTH + 6);
            contentHeight = Math.max(contentHeight, SequenceWirelessPanel.HEIGHT + 6);
        }
        return Math.min(1.0F, Math.min(availableWidth / contentWidth, availableHeight / contentHeight));
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
