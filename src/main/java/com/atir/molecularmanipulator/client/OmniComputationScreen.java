package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class OmniComputationScreen extends ResponsiveContainerScreen<OmniComputationMenu> {
    private static final int PURPLE = 0xFFB56CFF;
    private static final int CYAN = 0xFF69DBFF;
    private static final int GREEN = 0xFF72F2A5;
    private static final int ORANGE = 0xFFFFB766;
    private Button buildButton;
    private Button dismantleButton;
    private Button projectionButton;
    private Button refreshButton;
    private Button confirmStructureUpdateButton;
    private Button keepLegacyStructureButton;
    private boolean confirmingLegacyUpdate;

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
                        button -> {
                            if (menu.legacyStructure) {
                                confirmingLegacyUpdate = true;
                            } else {
                                menu.requestBuild();
                            }
                        })
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
        refreshButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.omni.refresh"),
                        button -> menu.requestRefresh())
                .bounds(leftPos + 245, topPos + 234, 73, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.omni.refresh_tooltip")))
                .build());
        confirmStructureUpdateButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.structure_update_confirm"),
                        button -> {
                            confirmingLegacyUpdate = false;
                            menu.requestStructureUpdate();
                        })
                .bounds(leftPos + 39, topPos + 234, 124, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.structure_update_confirm_tooltip")))
                .build());
        keepLegacyStructureButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.structure_update_keep_legacy"),
                        button -> {
                            confirmingLegacyUpdate = false;
                            menu.requestKeepLegacyStructure();
                        })
                .bounds(leftPos + 169, topPos + 234, 124, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.structure_update_keep_legacy_tooltip")))
                .build());
        updateStructureControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateStructureControls();
    }

    private void updateStructureControls() {
        if (!menu.legacyStructure) {
            confirmingLegacyUpdate = false;
        }
        boolean busy = menu.building || menu.dismantling;
        boolean showUpdateChoice = menu.legacyStructure
                && (!menu.legacyStructureUpdateDismissed || confirmingLegacyUpdate);
        buildButton.visible = !showUpdateChoice;
        dismantleButton.visible = !showUpdateChoice;
        projectionButton.visible = !showUpdateChoice;
        refreshButton.visible = !showUpdateChoice;
        confirmStructureUpdateButton.visible = showUpdateChoice;
        keepLegacyStructureButton.visible = showUpdateChoice;
        confirmStructureUpdateButton.active = !busy;
        keepLegacyStructureButton.active = !busy;

        buildButton.active = menu.legacyStructure
                ? !busy
                : !menu.formed && !busy && menu.conflictParts == 0;
        dismantleButton.active = !menu.building && !menu.dismantling && menu.correctParts > 1;
        String buildLabel = menu.building
                ? "gui.molecularmanipulator.omni.building"
                : menu.legacyStructure
                        ? "gui.molecularmanipulator.structure_update_short"
                        : "gui.molecularmanipulator.omni.build";
        buildButton.setMessage(Component.translatable(buildLabel));
        buildButton.setTooltip(Tooltip.create(Component.translatable(menu.legacyStructure
                ? "gui.molecularmanipulator.structure_update_confirm_tooltip"
                : "gui.molecularmanipulator.omni.build_tooltip")));
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
        panel(graphics, x + 10, y + 194, x + 322, y + 230, 0xFF69408F);
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
        if (menu.legacyStructure) {
            drawFittedString(graphics,
                    Component.translatable(menu.legacyStructureUpdateDismissed
                            && !confirmingLegacyUpdate
                                    ? "gui.molecularmanipulator.legacy_structure_retained"
                                    : "gui.molecularmanipulator.structure_update_available"),
                    12, 174, 140, ORANGE);
        } else if (menu.conflictParts > 0) {
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

        int metricsLeft = 174;
        int metricsRight = 314;
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.omni.network"),
                Component.translatable(menu.networkOnline
                        ? "gui.molecularmanipulator.omni.online"
                        : "gui.molecularmanipulator.omni.offline"),
                metricsLeft, metricsRight, 38, 0xFFBFC9DB,
                menu.networkOnline ? GREEN : ORANGE);
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.omni.storage"),
                Component.literal("\u221e"),
                metricsLeft, metricsRight, 55, 0xFFBFC9DB, PURPLE);
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.omni.parallel"),
                Component.literal("\u221e"),
                metricsLeft, metricsRight, 70, 0xFFBFC9DB, CYAN);
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.omni.active_jobs"),
                Component.literal(Integer.toString(menu.activeJobs)),
                metricsLeft, metricsRight, 88, 0xFFBFC9DB,
                menu.activeJobs > 0 ? GREEN : 0xFFD9D4E3);
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.omni.virtual_lanes"),
                Component.literal(Integer.toString(menu.cpuLanes)),
                metricsLeft, metricsRight, 104, 0xFFBFC9DB, 0xFFD9D4E3);

        var calculationStatus = menu.activeMaterialCalculations > 0
                ? Component.translatable("gui.molecularmanipulator.omni.calculating",
                        menu.activeMaterialCalculations)
                : Component.translatable("gui.molecularmanipulator.omni.calculation_idle");
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.omni.material_calculation"),
                calculationStatus, metricsLeft, metricsRight, 120, 0xFFBFC9DB,
                menu.activeMaterialCalculations > 0 ? CYAN : 0xFFD9D4E3);

        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.omni.calculation_stats",
                        menu.completedMaterialCalculations,
                        menu.lastMaterialCalculationMillis),
                metricsLeft, 132, metricsRight - metricsLeft, 0xFF9EA9BB);

        drawWrappedString(graphics,
                Component.translatable("gui.molecularmanipulator.omni.tick_budget"),
                metricsLeft, 143, metricsRight - metricsLeft, 2, font.lineHeight,
                0xFF9EA9BB);
        drawWrappedString(graphics,
                Component.translatable("gui.molecularmanipulator.omni.fixed"),
                metricsLeft, 162, metricsRight - metricsLeft, 3, font.lineHeight,
                0xFF9EA9BB);

        var quantumState = menu.quantumLinkState;
        int quantumColor = switch (quantumState) {
            case CONNECTED -> GREEN;
            case CONNECTED_BUILD_ONLY -> 0xFF78C8FF;
            case EMPTY, SEARCHING -> 0xFFD9D4E3;
            case STRUCTURE_INCOMPLETE, REMOTE_MISSING, REMOTE_OFFLINE -> ORANGE;
            case FREQUENCY_OCCUPIED, WIRED_CONFLICT, CONNECTION_ERROR -> 0xFFFF6D78;
        };
        int quantumLeft = 18;
        int quantumRight = 286;
        drawTwoSidedRow(graphics,
                Component.translatable("gui.molecularmanipulator.quantum_title"),
                Component.translatable("gui.molecularmanipulator.quantum_frequency",
                        menu.quantumFrequency == 0 ? "\u2014"
                                : formatFrequency(menu.quantumFrequency)),
                quantumLeft, quantumRight, 198, PURPLE, 0xFFD5DBE8);
        drawWrappedString(graphics,
                Component.translatable("gui.molecularmanipulator.quantum_state."
                        + quantumState.name().toLowerCase(Locale.ROOT)),
                quantumLeft, 210, quantumRight - quantumLeft, 2, font.lineHeight,
                quantumColor);

        graphics.drawString(font, Component.translatable("container.inventory"),
                85, 268, 0xFFD9D4E3, false);

        if ((menu.building || menu.dismantling) && menu.buildTotal > 0) {
            graphics.drawCenteredString(font,
                    Component.translatable(menu.dismantling
                                    ? "gui.molecularmanipulator.omni.dismantle_progress"
                                    : "gui.molecularmanipulator.omni.build_progress",
                            menu.buildProgress, menu.buildTotal),
                    imageWidth / 2, 19, 0xFFF1E8FF);
        }
    }

    private void drawLabelValueRow(GuiGraphics graphics, Component label, Component value,
                                   int left, int right, int y, int labelColor, int valueColor) {
        int gap = 4;
        int valueWidth = font.width(value);
        int valueX = right - valueWidth;
        int labelWidth = valueX - gap - left;
        if (labelWidth <= 0) {
            drawTwoSidedRow(graphics, label, value, left, right, y, labelColor, valueColor);
            return;
        }
        drawFittedString(graphics, label, left, y, labelWidth, labelColor);
        graphics.drawString(font, value, valueX, y, valueColor, false);
    }

    private void drawTwoSidedRow(GuiGraphics graphics, Component leftText, Component rightText,
                                 int left, int right, int y, int leftColor, int rightColor) {
        int gap = 6;
        int leftWidth = font.width(leftText);
        int rightWidth = font.width(rightText);
        int availableWidth = Math.max(1, right - left);
        int naturalWidth = leftWidth + gap + rightWidth;
        float scale = naturalWidth <= availableWidth
                ? 1.0F
                : availableWidth / (float) naturalWidth;
        drawScaledString(graphics, leftText, left, y, scale, leftColor);
        int rightX = right - Math.round(rightWidth * scale);
        drawScaledString(graphics, rightText, rightX, y, scale, rightColor);
    }

    private void drawWrappedString(GuiGraphics graphics, Component text, int x, int y,
                                   int maxWidth, int maxLines, int lineHeight, int color) {
        var lines = font.split(text, Math.max(1, maxWidth));
        int lineCount = Math.min(maxLines, lines.size());
        for (int line = 0; line < lineCount; line++) {
            graphics.drawString(font, lines.get(line), x, y + line * lineHeight,
                    color, false);
        }
    }

    private void drawFittedString(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        drawScaledString(graphics, text, x, y,
                Math.max(1, maxWidth) / (float) textWidth, color);
    }

    private void drawScaledString(GuiGraphics graphics, Component text, int x, int y,
                                  float scale, int color) {
        if (scale >= 0.999F) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static String formatFrequency(long frequency) {
        return Long.toUnsignedString(frequency, 16).toUpperCase(Locale.ROOT);
    }
}
