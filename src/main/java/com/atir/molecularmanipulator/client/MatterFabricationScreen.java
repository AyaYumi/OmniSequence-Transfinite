package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;
import java.util.List;

/** Fabrication telemetry, quantum construction link and structure-management controls. */
public final class MatterFabricationScreen extends ResponsiveContainerScreen<MatterFabricationMenu> {
    private static final int PURPLE = AeUiTheme.ACCENT;
    private static final int CYAN = AeUiTheme.CYAN;
    private static final long DISMANTLE_CONFIRM_MILLIS = 3_000L;
    private static final long DISMANTLE_DEBOUNCE_MILLIS = 250L;

    private Button buildButton;
    private Button updateButton;
    private Button dismantleButton;
    private Button previewButton;
    private Button refreshButton;
    private Button statusTab;
    private Button researchTab;
    private MatterResearchPanel researchPanel;
    private boolean researchPage;
    private long dismantleArmedAt;

    public MatterFabricationScreen(MatterFabricationMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        var selection = researchPanel == null ? null : researchPanel.selection();
        super.init();
        buildButton = addScreenWidget(AeUiTheme.button(leftPos + 12, topPos + 246, 70, 18,
                Component.translatable("gui.molecularmanipulator.fabrication.build"),
                button -> menu.requestBuild()));
        updateButton = addScreenWidget(AeUiTheme.button(leftPos + 12, topPos + 246, 70, 18,
                Component.translatable("gui.molecularmanipulator.fabrication.update"),
                button -> menu.requestStructureUpdate()));
        updateButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.molecularmanipulator.structure_update_confirm_tooltip")));
        previewButton = addScreenWidget(AeUiTheme.button(leftPos + 90, topPos + 246, 70, 18,
                Component.translatable("gui.molecularmanipulator.fabrication.preview"), button -> {
                    MatterFabricationGhostPreview.toggle(menu.getMachine());
                    onClose();
                }));
        refreshButton = addScreenWidget(AeUiTheme.button(leftPos + 168, topPos + 246, 70, 18,
                Component.translatable("gui.molecularmanipulator.fabrication.refresh"),
                button -> menu.requestRefresh()));
        dismantleButton = addScreenWidget(AeUiTheme.button(leftPos + 246, topPos + 246, 74, 18,
                Component.translatable("gui.molecularmanipulator.fabrication.dismantle"),
                button -> handleDismantleClick()));
        dismantleButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.molecularmanipulator.fabrication.dismantle_tooltip")));
        statusTab = addScreenWidget(AeUiTheme.button(leftPos + 12, topPos + 22, 70, 20,
                Component.translatable("gui.molecularmanipulator.research.status_tab"), button -> setResearchPage(false)));
        researchTab = addScreenWidget(AeUiTheme.button(leftPos + 86, topPos + 22, 70, 20,
                Component.translatable("gui.molecularmanipulator.research.research_tab"), button -> setResearchPage(true)));
        researchPanel = new MatterResearchPanel(menu, font, leftPos, topPos, button -> addScreenWidget(button));
        researchPanel.restoreSelection(selection);
        setResearchPage(researchPage);
    }

    private void setResearchPage(boolean visible) {
        researchPage = visible;
        menu.showResearchPage(visible);
        updatePageWidgets();
    }

    private void updatePageWidgets() {
        buildButton.visible = !researchPage && !menu.legacyStructure;
        updateButton.visible = !researchPage && menu.legacyStructure;
        previewButton.visible = refreshButton.visible = dismantleButton.visible = !researchPage;
        statusTab.active = researchPage;
        researchTab.active = !researchPage;
        researchPanel.update(researchPage);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (researchPanel != null && researchPanel.scroll(logicalMouseX(mouseX) - leftPos,
                logicalMouseY(mouseY) - topPos, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        List<Component> tooltip = researchPage ? researchPanel.tooltipAt(x, y)
                : x >= 20 && x < 152 && y >= 90 && y < 102
                        ? List.of(Component.translatable("gui.molecularmanipulator.fabrication.power", DisplayNumbers.exact(menu.aePerTick)))
                        : List.of();
        if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        boolean operationActive = menu.building || menu.dismantling;
        if (buildButton != null) {
            buildButton.active = !operationActive && !menu.updatingStructure;
        }
        if (updateButton != null) {
            updateButton.active = !operationActive;
        }
        if (dismantleButton != null) {
            dismantleButton.active = !operationActive && !menu.updatingStructure;
        }
        if (dismantleArmedAt > 0
                && Util.getMillis() - dismantleArmedAt > DISMANTLE_CONFIRM_MILLIS) {
            disarmDismantle();
        }
        updatePageWidgets();
    }

    private void handleDismantleClick() {
        long now = Util.getMillis();
        if (dismantleArmedAt == 0
                || now - dismantleArmedAt > DISMANTLE_CONFIRM_MILLIS) {
            dismantleArmedAt = now;
            dismantleButton.setMessage(Component.translatable(
                    "gui.molecularmanipulator.fabrication.dismantle_confirm"));
            return;
        }
        if (now - dismantleArmedAt >= DISMANTLE_DEBOUNCE_MILLIS) {
            menu.requestDismantle();
            disarmDismantle();
        }
    }

    private void disarmDismantle() {
        dismantleArmedAt = 0;
        if (dismantleButton != null) {
            dismantleButton.setMessage(Component.translatable(
                    "gui.molecularmanipulator.fabrication.dismantle"));
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
            float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        AeUiTheme.slotGrid(graphics, x + 84, y + 281, 9, 3);
        AeUiTheme.slotGrid(graphics, x + 84, y + 339, 9, 1);
        if (researchPage) {
            researchPanel.drawBackground(graphics, x, y);
            return;
        }
        AeUiTheme.panel(graphics, x + 12, y + 44, x + 160, y + 142);
        AeUiTheme.panel(graphics, x + 168, y + 44, x + 320, y + 142);
        AeUiTheme.panel(graphics, x + 12, y + 150, x + 320, y + 238);
        AeUiTheme.slot(graphics, x + 21, y + 168);

        float processRatio = menu.processingTime <= 0 ? 0.0F
                : Math.min(1.0F, menu.progress / (float) menu.processingTime);
        AeUiTheme.progress(graphics, x + 21, y + 120, 130, 7,
                processRatio,
                menu.state == MatterFabricationBlockEntity.ProcessingState.RUNNING ? CYAN : PURPLE);
        float operationRatio = menu.operationTotal <= 0 ? 0.0F
                : Math.min(1.0F, menu.operationProgress / (float) menu.operationTotal);
        AeUiTheme.progress(graphics, x + 177, y + 120, 134, 7,
                operationRatio, menu.dismantling ? AeUiTheme.ERROR : CYAN);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var displayTitle = title.getString().isBlank()
                ? Component.translatable("block.molecularmanipulator.matter_fabrication_controller") : title;
        drawFittedText(graphics, displayTitle, 12, 10, 308, AeUiTheme.PRIMARY_TEXT);
        if (researchPage) {
            researchPanel.drawForeground(graphics);
            drawFittedText(graphics, Component.translatable("container.inventory"), 85, 270, 162, AeUiTheme.PRIMARY_TEXT);
            return;
        }
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.telemetry"), 20, 48, 132, AeUiTheme.PRIMARY_TEXT);
        drawFittedText(graphics, stateLabel(), 20, 62, 132, stateColor());
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.network",
                Component.translatable(menu.networkOnline
                        ? "gui.molecularmanipulator.fabrication.online"
                        : "gui.molecularmanipulator.fabrication.offline")),
                20, 76, 132, menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.power",
                DisplayNumbers.compact(menu.aePerTick)),
                20, 90, 132, AeUiTheme.MUTED_TEXT);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.progress", menu.progress, Math.max(menu.processingTime, 0)),
                20, 104, 132, AeUiTheme.MUTED_TEXT);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.service_ports"), 20, 130, 132, AeUiTheme.MUTED_TEXT);

        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.management"), 176, 48, 136, AeUiTheme.PRIMARY_TEXT);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.structure", menu.correctParts, menu.totalParts),
                176, 62, 136, menu.formed ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.missing", menu.missingParts),
                176, 76, 136, menu.missingParts == 0 ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.conflict", menu.conflictParts),
                176, 90, 136, menu.conflictParts == 0 ? AeUiTheme.SUCCESS : AeUiTheme.ERROR);
        drawFittedText(graphics, operationLabel(), 176, 104, 136,
                menu.dismantling ? AeUiTheme.ERROR : AeUiTheme.MUTED_TEXT);
        if (menu.legacyStructure) {
            drawFittedText(graphics, Component.translatable(
                    "gui.molecularmanipulator.fabrication.legacy_detected"), 176, 130, 136, AeUiTheme.WARNING);
        }

        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.quantum_title"), 50, 158, 262, AeUiTheme.PRIMARY_TEXT);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.quantum_frequency", menu.quantumFrequency),
                50, 172, 262, AeUiTheme.MUTED_TEXT);
        boolean connected = menu.quantumLinkState == QuantumLinkState.CONNECTED
                || menu.quantumLinkState == QuantumLinkState.CONNECTED_BUILD_ONLY;
        drawFittedText(graphics, Component.translatable("gui.molecularmanipulator.quantum_state."
                + menu.quantumLinkState.name().toLowerCase(Locale.ROOT)), 50, 186, 262,
                connected ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        graphics.drawWordWrap(font, Component.translatable(
                "gui.molecularmanipulator.fabrication.quantum_build_hint"), 20, 202, 292, AeUiTheme.MUTED_TEXT);
        drawFittedText(graphics, Component.translatable(
                "gui.molecularmanipulator.fabrication.pattern_network_hint"), 20, 225, 292, AeUiTheme.MUTED_TEXT);
        drawFittedText(graphics, Component.translatable("container.inventory"), 85, 270, 162, AeUiTheme.PRIMARY_TEXT);
    }

    private void drawFittedText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        float scale = Math.min(1.0F, maxWidth / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private Component operationLabel() {
        if (menu.updatingStructure) {
            return Component.translatable(
                    "gui.molecularmanipulator.fabrication.operation_update",
                    menu.operationProgress, menu.operationTotal);
        }
        if (menu.building) {
            return Component.translatable("gui.molecularmanipulator.fabrication.operation_build",
                    menu.operationProgress, menu.operationTotal);
        }
        if (menu.dismantling) {
            return Component.translatable("gui.molecularmanipulator.fabrication.operation_dismantle",
                    menu.operationProgress, menu.operationTotal);
        }
        return Component.translatable("gui.molecularmanipulator.fabrication.operation_idle");
    }

    private Component stateLabel() {
        return Component.translatable("gui.molecularmanipulator.fabrication.state."
                + menu.state.name().toLowerCase(Locale.ROOT));
    }

    private int stateColor() {
        return switch (menu.state) {
            case RUNNING -> AeUiTheme.SUCCESS;
            case STRUCTURE_INCOMPLETE, NETWORK_OFFLINE, OUTPUT_BLOCKED,
                    WAITING_POWER, BUILDING, DISMANTLING -> AeUiTheme.WARNING;
            default -> AeUiTheme.MUTED_TEXT;
        };
    }
}
