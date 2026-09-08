package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import appeng.api.stacks.AEFluidKey;
import appeng.api.client.AEKeyRendering;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Renameable 36-slot pattern assembly terminal. */
public final class MatterFabricationPatternAssemblyScreen
        extends ResponsiveContainerScreen<MatterFabricationPatternAssemblyMenu> {
    private EditBox nameField;
    private boolean initializedName;
    private final Button[] tabs = new Button[3];
    private Button previous, next, refund;

    public MatterFabricationPatternAssemblyScreen(MatterFabricationPatternAssemblyMenu menu,
            Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void init() {
        String draft = nameField == null ? null : nameField.getValue();
        super.init();
        nameField = OmniUiTheme.tallTextField(style, font, leftPos + 20, topPos + 48, 154, 20,
                Component.translatable("gui.molecularmanipulator.fabrication.pattern.name_hint"),
                Component.translatable("gui.molecularmanipulator.fabrication.pattern.name"));
        nameField.setMaxLength(64);
        if (draft != null) nameField.setValue(draft);
        addScreenWidget(nameField);
        addScreenWidget(OmniUiTheme.button(leftPos + 182, topPos + 48, 46, 20,
                Component.translatable("gui.molecularmanipulator.fabrication.pattern.rename"),
                button -> saveName()));
        for (int i = 0; i < tabs.length; i++) {
            final int view = i;
            tabs[i] = addScreenWidget(OmniUiTheme.button(leftPos + 18 + i * 72, topPos + 90, 68, 16,
                    text(i == 0 ? "patterns" : i == 1 ? "input" : "output"), button -> menu.requestView(view)));
            tabs[i].setTooltip(Tooltip.create(text("capacity")));
        }
        previous = addScreenWidget(OmniUiTheme.button(leftPos + 18, topPos + 198, 24, 16, Component.literal("<"),
                button -> menu.requestPage(menu.bufferState.page() - 1)));
        next = addScreenWidget(OmniUiTheme.button(leftPos + 102, topPos + 198, 24, 16, Component.literal(">"),
                button -> menu.requestPage(menu.bufferState.page() + 1)));
        refund = addScreenWidget(OmniUiTheme.button(leftPos + 134, topPos + 198, 96, 16, text("refund"), button -> menu.requestRefund()));
        refund.setTooltip(Tooltip.create(text("refund_hint")));
        updateControls();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateControls();
        if (!initializedName && nameField != null && !menu.assemblyName.isBlank()) {
            nameField.setValue(menu.assemblyName);
            initializedName = true;
        }
    }

    private void updateControls() {
        for (int i = 0; i < tabs.length; i++) if (tabs[i] != null) {
            tabs[i].active = menu.view != i;
            ((OmniButton) tabs[i]).setSelected(menu.view == i);
        }
        if (previous != null) {
            previous.visible = next.visible = menu.view != 0;
            previous.active = menu.bufferState.page() > 0;
            next.active = menu.bufferState.page() + 1 < menu.bufferState.pages();
            refund.active = menu.canRefund;
        }
        menu.updatePatternSlots();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        if (menu.view != 0 && x >= 12 && x < 236 && y >= 110 && y < 218 && scrollY != 0) {
            menu.requestPage(menu.bufferState.page() + (scrollY < 0 ? 1 : -1)); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public appeng.client.gui.StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        if (menu.view != 0 && x >= 18 && x < 230 && y >= 112 && y < 192) {
            int row = (int) (y - 112) / 20;
            if (row < menu.bufferState.contents().size()) return new appeng.client.gui.StackWithBounds(
                    menu.bufferState.contents().get(row), new net.minecraft.client.renderer.Rect2i(
                            responsiveScreenX(leftPos + 18), responsiveScreenY(topPos + 112 + row * 20),
                            responsiveScreenLength(212), responsiveScreenLength(20)));
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        if (menu.view != 0 && x >= 18 && x < 230 && y >= 112 && y < 192) {
            int row = (int) (y - 112) / 20;
            if (row < menu.bufferState.contents().size()) {
                var stack = menu.bufferState.contents().get(row);
                graphics.renderComponentTooltip(font, List.of(stack.what().getDisplayName(),
                        text("amount", DisplayNumbers.exact(stack.amount()) + (stack.what() instanceof AEFluidKey ? " mB" : ""))), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.isFocused() && (keyCode == 257 || keyCode == 335)) {
            saveName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveName() {
        if (nameField != null) {
            menu.requestRename(nameField.getValue());
            nameField.setFocused(false);
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
            float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        graphics.fill(x + 12, y + 36, x + 236, y + 37, OmniUiTheme.SHADOW);
        MachineUiLayout.PATTERN_ASSEMBLY.draw(graphics, x, y);
        if (menu.view == 0) OmniUiTheme.slotGrid(graphics, x + 43, y + 110, 9, 4);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var displayTitle = title.getString().isBlank() ? Component.translatable("block.molecularmanipulator.matter_fabrication_pattern_assembly") : title;
        OmniUiTheme.label(graphics, font, displayTitle, 14, 8, 220, OmniUiTheme.PRIMARY_TEXT);
        graphics.fill(14, 24, 18, 28, menu.networkOnline ? OmniUiTheme.SUCCESS : OmniUiTheme.WARNING);
        OmniUiTheme.label(graphics, font, text(menu.networkOnline ? "network_online" : "network_offline"), 23, 22, 128, OmniUiTheme.MUTED_TEXT);
        OmniUiTheme.label(graphics, font, Component.translatable("gui.molecularmanipulator.fabrication.pattern.used", menu.occupied),
                164, 22, 70, OmniUiTheme.PRIMARY_TEXT);
        if (menu.view == 0) {
            var status = menu.bufferState.unavailable() ? text("unavailable")
                    : !menu.controllerReady && (menu.bufferState.processing() || menu.bufferState.queuedPatterns() > 0) ? text("waiting") : text("capacity");
            drawFittedString(graphics, status, 20, 186, 208, OmniUiTheme.MUTED_TEXT);
            drawFittedString(graphics, text("queued", menu.bufferState.queuedPatterns()), 20, 202, 108, OmniUiTheme.MUTED_TEXT);
        } else {
            if (menu.bufferState.unavailable()) drawFittedString(graphics, text("unavailable"), 20, 118, 208, OmniUiTheme.WARNING);
            else if (menu.bufferState.contents().isEmpty()) drawFittedString(graphics, text("empty"), 20, 118, 208, OmniUiTheme.MUTED_TEXT);
            else for (int row = 0; row < menu.bufferState.contents().size(); row++) {
                var stack = menu.bufferState.contents().get(row); int y = 112 + row * 20;
                AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(), graphics, 20, y, stack.what());
                drawFittedString(graphics, stack.what().getDisplayName(), 42, y, 184, OmniUiTheme.PRIMARY_TEXT);
                drawFittedString(graphics, Component.literal(DisplayNumbers.compact(stack.amount()) + (stack.what() instanceof AEFluidKey ? " mB" : "")),
                        42, y + 10, 184, OmniUiTheme.MUTED_TEXT);
            }
            drawCenteredFittedString(graphics, Component.literal((menu.bufferState.page() + 1) + "/" + menu.bufferState.pages()), 72, 202, 54, OmniUiTheme.MUTED_TEXT);
        }
        OmniUiTheme.label(graphics, font, Component.translatable("container.inventory"), 44, 224, 162, OmniUiTheme.PRIMARY_TEXT);
    }

    private static Component text(String key, Object... args) { return Component.translatable("gui.molecularmanipulator.fabrication.buffer." + key, args); }
}
