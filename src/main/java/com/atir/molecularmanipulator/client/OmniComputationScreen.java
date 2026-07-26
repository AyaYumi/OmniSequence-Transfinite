package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class OmniComputationScreen extends AbstractContainerScreen<OmniComputationMenu> {
    private static final int PURPLE = 0xFFB56CFF;
    private static final int CYAN = 0xFF69DBFF;
    private static final int GREEN = 0xFF72F2A5;
    private static final int ORANGE = 0xFFFFB766;
    private Button buildButton;
    private Button dismantleButton;
    private Button projectionButton;

    public OmniComputationScreen(OmniComputationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 332;
        imageHeight = 364;
    }

    @Override
    protected void init() {
        super.init();
        buildButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.omni.build"),
                        button -> menu.requestBuild())
                .bounds(leftPos + 14, topPos + 234, 73, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.omni.build_tooltip")))
                .build());
        dismantleButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.omni.dismantle"),
                        button -> menu.requestDismantle())
                .bounds(leftPos + 91, topPos + 234, 73, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.omni.dismantle_tooltip")))
                .build());
        projectionButton = addRenderableWidget(Button.builder(projectionLabel(),
                        button -> {
                            OmniComputationGhostPreview.toggle(menu.getCore());
                            button.setMessage(projectionLabel());
                        })
                .bounds(leftPos + 168, topPos + 234, 73, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.omni.projection_tooltip")))
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.omni.refresh"),
                        button -> menu.requestRefresh())
                .bounds(leftPos + 245, topPos + 234, 73, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.omni.refresh_tooltip")))
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        buildButton.active = !menu.formed && !menu.building && !menu.dismantling && menu.conflictParts == 0;
        dismantleButton.active = !menu.building && !menu.dismantling && menu.correctParts > 1;
        buildButton.setMessage(Component.translatable(menu.building
                ? "gui.molecularmanipulator.omni.building"
                : "gui.molecularmanipulator.omni.build"));
        dismantleButton.setMessage(Component.translatable(menu.dismantling
                ? "gui.molecularmanipulator.omni.dismantling"
                : "gui.molecularmanipulator.omni.dismantle"));
        projectionButton.setMessage(projectionLabel());
    }

    private Component projectionLabel() {
        return Component.translatable(OmniComputationGhostPreview.isShowing(menu.getCore())
                ? "gui.molecularmanipulator.omni.projection_hide"
                : "gui.molecularmanipulator.omni.projection");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF171126, 0xFF080D17);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, PURPLE);
        panel(graphics, x + 10, y + 28, x + 154, y + 190, 0xFF392650);
        panel(graphics, x + 162, y + 28, x + 322, y + 190, 0xFF234352);
        panel(graphics, x + 10, y + 194, x + 322, y + 226, 0xFF69408F);
        panel(graphics, x + 75, y + 260, x + 257, y + 360, 0xFF29334A);
        slotFrame(graphics, x + 292, y + 200, 0xFF9B67CC);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, x + 84 + column * 18, y + 281 + row * 18, 0xFF526079);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, x + 84 + column * 18, y + 339, 0xFF526079);
        }

        int centerX = x + 82;
        int centerY = y + 82;
        int pulse = (int) ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) % 20);
        int radius = 29 + Math.min(pulse, 20 - pulse) / 5;
        diamond(graphics, centerX, centerY, radius, 0xFF563378);
        diamond(graphics, centerX, centerY, radius - 7, 0xFF9252D0);
        diamond(graphics, centerX, centerY, radius - 14,
                menu.formed && menu.networkOnline ? CYAN : 0xFF544866);
        graphics.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4,
                menu.formed ? 0xFFE9D6FF : 0xFF6A5E78);

        int structureWidth = 128;
        graphics.fill(x + 18, y + 162, x + 18 + structureWidth, y + 166, 0xFF24283A);
        int completed = menu.totalParts <= 0 ? 0
                : (int) Math.min(structureWidth,
                Math.ceil(structureWidth * menu.correctParts / (double) menu.totalParts));
        if (completed > 0) {
            graphics.fill(x + 18, y + 162, x + 18 + completed, y + 166,
                    menu.formed ? GREEN : PURPLE);
        }

    }

    private static void panel(GuiGraphics graphics, int left, int top, int right, int bottom, int border) {
        graphics.fill(left, top, right, bottom, 0xD9141928);
        graphics.fill(left, top, right, top + 1, border);
        graphics.fill(left, bottom - 1, right, bottom, border);
        graphics.fill(left, top, left + 1, bottom, border);
        graphics.fill(right - 1, top, right, bottom, border);
    }

    private static void slotFrame(GuiGraphics graphics, int left, int top, int border) {
        graphics.fill(left, top, left + 18, top + 18, border);
        graphics.fill(left + 1, top + 1, left + 17, top + 17, 0xFF0C101A);
    }

    private static void diamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            graphics.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font,
                Component.translatable("block.molecularmanipulator.omni_computation_controller"),
                imageWidth / 2, 9, 0xFFF1E8FF);

        graphics.drawCenteredString(font,
                Component.translatable(menu.formed
                        ? "gui.molecularmanipulator.omni.formed"
                        : "gui.molecularmanipulator.omni.incomplete"),
                82, 119, menu.formed ? GREEN : ORANGE);
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.omni.size",
                        OmniComputationStructure.WIDTH,
                        OmniComputationStructure.WIDTH,
                        OmniComputationStructure.HEIGHT),
                82, 132, 0xFFBDB5CC);
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.omni.parts",
                        menu.correctParts, menu.totalParts),
                82, 145, 0xFFD9D4E3);
        if (menu.conflictParts > 0) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.molecularmanipulator.omni.conflicts",
                            menu.conflictParts),
                    82, 174, 0xFFFF6D78);
        } else {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.molecularmanipulator.omni.missing",
                            menu.missingParts),
                    82, 174, 0xFFBDB5CC);
        }

        int valueX = 306;
        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.omni.network"),
                174, 42, 0xFFBFC9DB, false);
        graphics.drawString(font,
                Component.translatable(menu.networkOnline
                        ? "gui.molecularmanipulator.omni.online"
                        : "gui.molecularmanipulator.omni.offline"),
                valueX - 42, 42, menu.networkOnline ? GREEN : ORANGE, false);

        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.omni.storage"),
                174, 66, 0xFFBFC9DB, false);
        graphics.drawString(font, Component.literal("\u221e"),
                valueX, 66, PURPLE, false);

        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.omni.parallel"),
                174, 84, 0xFFBFC9DB, false);
        graphics.drawString(font, Component.literal("\u221e"),
                valueX, 84, CYAN, false);

        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.omni.active_jobs"),
                174, 108, 0xFFBFC9DB, false);
        graphics.drawString(font, Integer.toString(menu.activeJobs),
                valueX, 108, menu.activeJobs > 0 ? GREEN : 0xFFD9D4E3, false);

        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.omni.virtual_lanes"),
                174, 126, 0xFFBFC9DB, false);
        graphics.drawString(font, Integer.toString(menu.cpuLanes),
                valueX, 126, 0xFFD9D4E3, false);

        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.omni.material_calculation"),
                174, 144, 0xFFBFC9DB, false);
        var calculationStatus = menu.activeMaterialCalculations > 0
                ? Component.translatable("gui.molecularmanipulator.omni.calculating",
                        menu.activeMaterialCalculations)
                : Component.translatable("gui.molecularmanipulator.omni.calculation_idle");
        graphics.drawString(font, calculationStatus,
                valueX - (menu.activeMaterialCalculations > 0 ? 30 : 18), 144,
                menu.activeMaterialCalculations > 0 ? CYAN : 0xFFD9D4E3, false);

        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.omni.calculation_stats",
                        menu.completedMaterialCalculations,
                        menu.lastMaterialCalculationMillis),
                174, 158, 140, 0xFF9EA9BB);

        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.omni.tick_budget"),
                174, 170, 140, 0xFF9EA9BB);
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.omni.fixed"),
                174, 181, 140, 0xFF9EA9BB);

        var quantumState = menu.quantumLinkState;
        int quantumColor = switch (quantumState) {
            case CONNECTED -> GREEN;
            case CONNECTED_BUILD_ONLY -> 0xFF78C8FF;
            case EMPTY, SEARCHING -> 0xFFD9D4E3;
            case STRUCTURE_INCOMPLETE, REMOTE_MISSING, REMOTE_OFFLINE -> ORANGE;
            case FREQUENCY_OCCUPIED, WIRED_CONFLICT, CONNECTION_ERROR -> 0xFFFF6D78;
        };
        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.quantum_title"),
                18, 202, PURPLE, false);
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.quantum_state."
                        + quantumState.name().toLowerCase(Locale.ROOT)),
                106, 202, 180, quantumColor);
        graphics.drawString(font,
                Component.translatable("gui.molecularmanipulator.quantum_frequency",
                        menu.quantumFrequency == 0 ? "\u2014" : formatFrequency(menu.quantumFrequency)),
                106, 214, 0xFFD5DBE8, false);

        graphics.drawString(font, Component.translatable("container.inventory"),
                85, 268, 0xFFD9D4E3, false);

        if ((menu.building || menu.dismantling) && menu.buildTotal > 0) {
            graphics.drawCenteredString(font,
                    Component.translatable(menu.dismantling
                                    ? "gui.molecularmanipulator.omni.dismantle_progress"
                                    : "gui.molecularmanipulator.omni.build_progress",
                            menu.buildProgress, menu.buildTotal),
                    imageWidth / 2, 187, 0xFFF1E8FF);
        }
    }

    private void drawFittedString(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = maxWidth / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static String formatFrequency(long frequency) {
        return Long.toUnsignedString(frequency, 16).toUpperCase(Locale.ROOT);
    }
}
