package com.atir.molecularmanipulator.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MolecularManipulatorScreen extends AEBaseScreen<MolecularManipulatorMenu> {
    private static final int HIDDEN_SLOT_POSITION = -10_000;

    private Button previousPage;
    private Button nextPage;

    public MolecularManipulatorScreen(MolecularManipulatorMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        setTextContent(TEXT_ID_DIALOG_TITLE,
                Component.translatable("gui.molecularmanipulator.molecular_manipulator"));
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(leftPos + 51, topPos + 18, 16, 16)
                .build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(leftPos + 127, topPos + 18, 16, 16)
                .build());
        layoutPatternPage();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        previousPage.active = menu.getPage() > 0;
        nextPage.active = menu.getPage() + 1 < menu.getPageCount();
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        var pageText = Component.translatable("gui.molecularmanipulator.page", menu.getPage() + 1,
                menu.getPageCount());
        guiGraphics.drawCenteredString(font, pageText, 97, 22, 0x403748);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        panel(guiGraphics, offsetX + 8, offsetY + 35, offsetX + 186, offsetY + 130,
                0xFFEDE7F2, 0xFF8D6AA8);
        panel(guiGraphics, offsetX + 8, offsetY + 134, offsetX + 186, offsetY + 170,
                0xFFE5EDF4, 0xFF6388A5);
        panel(guiGraphics, offsetX + 8, offsetY + 176, offsetX + 186, offsetY + 267,
                0xFFE9E9EC, 0xFF777782);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 51, 9, 4, 0xFF7B648C);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 144, 9, 1, 0xFF55758D);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 189, 9, 3, 0xFF676771);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 247, 9, 1, 0xFF676771);
    }

    private void changePage(int offset) {
        menu.requestPage(menu.getPage() + offset);
        layoutPatternPage();
    }

    private void layoutPatternPage() {
        var slots = menu.getPatternSlots();
        int firstSlot = menu.getPage() * MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE;
        int lastSlot = Math.min(slots.size(), firstSlot + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);

        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            if (index >= firstSlot && index < lastSlot) {
                int pageIndex = index - firstSlot;
                slot.x = 16 + pageIndex % 9 * 18;
                slot.y = 52 + pageIndex / 9 * 18;
            } else {
                slot.x = HIDDEN_SLOT_POSITION;
                slot.y = HIDDEN_SLOT_POSITION;
            }
        }
    }

    private static void panel(GuiGraphics guiGraphics, int left, int top, int right, int bottom,
            int fill, int border) {
        guiGraphics.fill(left, top, right, bottom, fill);
        guiGraphics.fill(left, top, right, top + 1, border);
        guiGraphics.fill(left, bottom - 1, right, bottom, border);
        guiGraphics.fill(left, top, left + 1, bottom, border);
        guiGraphics.fill(right - 1, top, right, bottom, border);
    }

    private static void drawSlotGrid(GuiGraphics guiGraphics, int left, int top, int columns, int rows,
            int border) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int x = left + column * 18;
                int y = top + row * 18;
                guiGraphics.fill(x, y, x + 18, y + 18, border);
                guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF15151B);
            }
        }
    }
}
