package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class MatterFabricationScreen extends ResponsiveContainerScreen<MatterFabricationMenu> {
    private static final int PURPLE = 0xFFB66CFF;
    private static final int CYAN = 0xFF6DE6FF;

    public MatterFabricationScreen(MatterFabricationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.molecularmanipulator.fabrication.build"),
                        button -> menu.requestBuild())
                .bounds(leftPos + 66, topPos + 101, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.molecularmanipulator.fabrication.preview"),
                        button -> {
                            MatterFabricationGhostPreview.toggle(menu.getMachine());
                            onClose();
                        })
                .bounds(leftPos + 118, topPos + 101, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.molecularmanipulator.fabrication.refresh"),
                        button -> menu.requestRefresh())
                .bounds(leftPos + 170, topPos + 101, 48, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF171024, 0xFF08131C);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, PURPLE);
        panel(graphics, x + 12, y + 28, x + 94, y + 100, 0xFF533276);
        panel(graphics, x + 105, y + 28, x + 218, y + 100, 0xFF24566A);
        panel(graphics, x + 27, y + 124, x + 203, y + 216, 0xFF344052);

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                slotFrame(graphics, x + MatterFabricationMenu.INPUT_X + column * 18,
                        y + MatterFabricationMenu.INPUT_Y + row * 18, 0xFF8050AC);
            }
        }
        for (int column = 0; column < 2; column++) {
            slotFrame(graphics, x + MatterFabricationMenu.OUTPUT_X + column * 18,
                    y + MatterFabricationMenu.OUTPUT_Y, 0xFF4FAFC9);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, x + MatterFabricationMenu.PLAYER_X + column * 18,
                        y + MatterFabricationMenu.PLAYER_Y + row * 18, 0xFF526079);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, x + MatterFabricationMenu.PLAYER_X + column * 18,
                    y + MatterFabricationMenu.HOTBAR_Y, 0xFF526079);
        }

        int barLeft = x + 105;
        int barTop = y + 82;
        int width = menu.processingTime <= 0 ? 0
                : Math.min(103, Math.round(menu.progress * 103.0F / menu.processingTime));
        graphics.fill(barLeft, barTop, barLeft + 103, barTop + 7, 0xFF111824);
        graphics.fill(barLeft, barTop, barLeft + width, barTop + 7,
                menu.state == MatterFabricationBlockEntity.ProcessingState.RUNNING ? CYAN : PURPLE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0xFFE7D9FF, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.fabrication.inputs"),
                18, 34, 0xFFD9C8F5, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.fabrication.outputs"),
                113, 34, 0xFFC1F2FF, false);
        graphics.drawString(font, stateLabel(), 113, 48, stateColor(), false);
        if (menu.aePerTick > 0) {
            graphics.drawString(font, Component.translatable("gui.molecularmanipulator.fabrication.power",
                    String.format(Locale.ROOT, "%.1f", menu.aePerTick)), 113, 60, 0xFFB9C7D5, false);
        }
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.fabrication.structure",
                menu.correctParts, menu.totalParts), 12, 105,
                menu.formed ? 0xFF70F2A2 : 0xFFFFB75E, false);
        graphics.drawString(font, playerInventoryTitle, MatterFabricationMenu.PLAYER_X, 121,
                0xFFB7C3D7, false);
    }

    private Component stateLabel() {
        return Component.translatable("gui.molecularmanipulator.fabrication.state."
                + menu.state.name().toLowerCase(Locale.ROOT));
    }

    private int stateColor() {
        return switch (menu.state) {
            case RUNNING -> 0xFF70F2A2;
            case STRUCTURE_INCOMPLETE, NETWORK_OFFLINE, OUTPUT_BLOCKED, WAITING_POWER, BUILDING -> 0xFFFFB75E;
            default -> 0xFFB7C3D7;
        };
    }

    private static void panel(GuiGraphics graphics, int left, int top, int right, int bottom, int border) {
        graphics.fill(left, top, right, bottom, 0xD9141928);
        graphics.fill(left, top, right, top + 1, border);
        graphics.fill(left, bottom - 1, right, bottom, border);
        graphics.fill(left, top, left + 1, bottom, border);
        graphics.fill(right - 1, top, right, bottom, border);
    }

    private static void slotFrame(GuiGraphics graphics, int left, int top, int border) {
        graphics.fill(left - 1, top - 1, left + 17, top + 17, border);
        graphics.fill(left, top, left + 16, top + 16, 0xFF090D16);
    }
}
