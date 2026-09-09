package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.math.interpolate.Eases;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * LDLib2 presentation layer for the Omni Computation controller.
 *
 * <p>The existing AE2 menu remains authoritative for slots, synchronization,
 * and client actions. This view is deliberately transparent so vanilla
 * container slots retain their full interaction behavior.</p>
 */
final class OmniComputationLdUi {
    private static final int PURPLE = AeUiTheme.ACCENT;
    private static final int CYAN = AeUiTheme.CYAN;
    private static final int GREEN = AeUiTheme.SUCCESS;
    private static final int ORANGE = AeUiTheme.WARNING;
    private static final int RED = AeUiTheme.ERROR;
    private static final int PRIMARY_TEXT = AeUiTheme.PRIMARY_TEXT;
    private static final int SECONDARY_TEXT = AeUiTheme.MUTED_TEXT;

    private final OmniComputationMenu menu;
    private final ModularUI modularUI;
    private final UIElement normalButtonRow;
    private final UIElement structureUpdateButtonRow;
    private final Label structureState;
    private final Label structureSize;
    private final Label structureParts;
    private final Label structureDetail;
    private final Label networkValue;
    private final Label activeJobsValue;
    private final Label cpuLanesValue;
    private final Label calculationValue;
    private final Label calculationStats;
    private final Label quantumStateValue;
    private final Label quantumFrequency;
    private final ProgressBar structureProgress;
    private final ColorRectTexture structureProgressTexture =
            new ColorRectTexture(PURPLE);
    private final Button buildButton;
    private final Button dismantleButton;
    private final Button projectionButton;
    private final Button legacyProjectionButton;
    private final Button confirmStructureUpdateButton;
    private final StructureUpdateConfirmation updateConfirmation = new StructureUpdateConfirmation();
    private final Label fixedModel;
    private final Button keepLegacyStructureButton;
    private boolean confirmingLegacyUpdate;
    private boolean showingStructureUpdateChoice;
    private Boolean previousNetworkOnline;
    private Boolean previousFormed;

    OmniComputationLdUi(OmniComputationMenu menu) {
        this.menu = menu;

        var ui = LdUiXml.load("ui/omni_computation.xml");
        structureState = label(ui, "structure-state");
        structureSize = label(ui, "structure-size");
        structureParts = label(ui, "structure-parts");
        structureProgress = progress(ui, "structure-progress", structureProgressTexture, false);
        structureDetail = label(ui, "structure-detail");
        networkValue = label(ui, "network-value");
        activeJobsValue = label(ui, "active-jobs-value");
        cpuLanesValue = label(ui, "cpu-lanes-value");
        calculationValue = label(ui, "calculation-value");
        calculationStats = label(ui, "calculation-stats");
        quantumStateValue = label(ui, "quantum-state-value");
        quantumFrequency = label(ui, "quantum-frequency");
        fixedModel = label(ui, "fixed-model");

        normalButtonRow = LdUiXml.require(ui, "normal-button-row", UIElement.class);
        structureUpdateButtonRow = LdUiXml.require(
                ui, "structure-update-button-row", UIElement.class);
        buildButton = button(ui, "build",
                Component.translatable("gui.molecularmanipulator.omni.build"),
                Component.translatable("gui.molecularmanipulator.omni.build_tooltip"),
                event -> {
                    if (menu.legacyStructure) {
                        confirmingLegacyUpdate = true;
                    } else {
                        menu.requestBuild();
                    }
                });
        dismantleButton = button(ui, "dismantle",
                Component.translatable("gui.molecularmanipulator.omni.dismantle"),
                Component.translatable("gui.molecularmanipulator.omni.dismantle_tooltip"),
                event -> menu.requestDismantle());
        projectionButton = button(ui, "projection", projectionLabel(),
                Component.translatable("gui.molecularmanipulator.omni.projection_tooltip"),
                event -> { updateConfirmation.cancel(); OmniComputationGhostPreview.toggle(menu.getCore()); });
        legacyProjectionButton = button(ui, "structure-preview", projectionLabel(),
                Component.translatable("gui.molecularmanipulator.structure_update_projection_warning"),
                event -> { updateConfirmation.cancel(); OmniComputationGhostPreview.toggle(menu.getCore()); });
        button(ui, "refresh",
                Component.translatable("gui.molecularmanipulator.omni.refresh"),
                Component.translatable("gui.molecularmanipulator.omni.refresh_tooltip"),
                event -> menu.requestRefresh());
        confirmStructureUpdateButton = button(ui, "structure-update",
                Component.translatable("gui.molecularmanipulator.structure_update_confirm"),
                Component.translatable("gui.molecularmanipulator.structure_update_confirm_tooltip"),
                event -> {
                    if (updateConfirmation.click()) {
                        confirmingLegacyUpdate = false;
                        menu.requestStructureUpdate();
                    }
                    refresh();
                });
        keepLegacyStructureButton = button(ui, "structure-keep-legacy",
                Component.translatable("gui.molecularmanipulator.structure_update_keep_legacy"),
                Component.translatable(
                        "gui.molecularmanipulator.structure_update_keep_legacy_tooltip"),
                event -> {
                    updateConfirmation.cancel();
                    confirmingLegacyUpdate = false;
                    menu.requestKeepLegacyStructure();
                });

        modularUI = new ResponsiveModularUI(ui);
        refresh();
    }

    void attach(Screen screen) {
        modularUI.setScreenAndInit(screen);
    }

    ModularUI.ModularUIWidget widget() {
        return modularUI.getWidget();
    }

    void tick() {
        updateConfirmation.tick(menu.legacyStructure && !menu.building && !menu.dismantling);
        refresh();
        modularUI.tick();
    }

    void close() {
        updateConfirmation.cancel();
        modularUI.onRemoved();
    }

    private void refresh() {
        if (!menu.legacyStructure) {
            confirmingLegacyUpdate = false;
        }

        boolean busy = menu.building || menu.dismantling;
        legacyProjectionButton.setText(projectionLabel());
        fixedModel.setValue(Component.translatable(menu.legacyStructure
                ? "gui.molecularmanipulator.structure_update_projection_warning"
                : "gui.molecularmanipulator.omni.fixed"));
        fixedModel.getTextStyle().textColor(menu.legacyStructure ? ORANGE : SECONDARY_TEXT);
        confirmStructureUpdateButton.setText(Component.translatable(updateConfirmation.isArmed()
                ? "gui.molecularmanipulator.structure_update_second_confirm"
                : "gui.molecularmanipulator.structure_update_confirm"));
        float structureRatio = menu.totalParts <= 0
                ? 0
                : Math.max(0, Math.min(1, menu.correctParts / (float) menu.totalParts));
        structureProgressTexture.color = menu.formed ? GREEN : PURPLE;
        structureProgress.setDisplay(!menu.formed);
        structureProgress.setProgress(structureRatio);

        boolean showUpdateChoice = menu.legacyStructure
                && (!menu.legacyStructureUpdateDismissed || confirmingLegacyUpdate);
        if (showUpdateChoice != showingStructureUpdateChoice) {
            showingStructureUpdateChoice = showUpdateChoice;
            modularUI.clearFocus();
        }
        normalButtonRow.setDisplay(!showUpdateChoice);
        structureUpdateButtonRow.setDisplay(showUpdateChoice);

        buildButton.setActive(menu.legacyStructure
                ? !busy
                : !menu.formed && !busy && menu.conflictParts == 0);
        // An intact pre-redesign structure can score only the controller against
        // the current blueprint, so use the layout-independent removable count.
        dismantleButton.setActive(!busy && menu.dismantlableBlocks > 0);
        confirmStructureUpdateButton.setActive(!busy);
        keepLegacyStructureButton.setActive(!busy);

        String buildLabel = menu.building
                ? "gui.molecularmanipulator.omni.building"
                : menu.legacyStructure
                        ? "gui.molecularmanipulator.structure_update_short"
                        : "gui.molecularmanipulator.omni.build";
        buildButton.setText(Component.translatable(buildLabel));
        buildButton.style(style -> style.tooltips(Component.translatable(menu.legacyStructure
                ? "gui.molecularmanipulator.structure_update_confirm_tooltip"
                : "gui.molecularmanipulator.omni.build_tooltip")));
        dismantleButton.setText(Component.translatable(menu.dismantling
                ? "gui.molecularmanipulator.omni.dismantling"
                : "gui.molecularmanipulator.omni.dismantle"));
        projectionButton.setText(projectionLabel());

        structureSize.setValue(Component.translatable("gui.molecularmanipulator.omni.size",
                menu.legacyStructure ? 31 : OmniComputationStructure.WIDTH,
                menu.legacyStructure ? 31 : OmniComputationStructure.WIDTH,
                menu.legacyStructure ? 39 : OmniComputationStructure.HEIGHT));
        setLabel(structureState,
                Component.translatable(menu.formed
                        ? "gui.molecularmanipulator.omni.formed"
                        : "gui.molecularmanipulator.omni.incomplete"),
                menu.formed ? GREEN : ORANGE);
        structureParts.setValue(Component.translatable(
                "gui.molecularmanipulator.omni.parts",
                menu.correctParts,
                menu.totalParts));

        if (menu.legacyStructure) {
            setLabel(structureDetail,
                    Component.translatable(menu.legacyStructureUpdateDismissed
                            && !confirmingLegacyUpdate
                                    ? "gui.molecularmanipulator.legacy_structure_retained"
                                    : "gui.molecularmanipulator.structure_update_available"),
                    ORANGE);
        } else if (busy && menu.buildTotal > 0) {
            setLabel(structureDetail,
                    Component.translatable(menu.dismantling
                                    ? "gui.molecularmanipulator.omni.dismantle_progress"
                                    : "gui.molecularmanipulator.omni.build_progress",
                            menu.buildProgress,
                            menu.buildTotal),
                    PRIMARY_TEXT);
        } else if (menu.conflictParts > 0) {
            setLabel(structureDetail,
                    Component.translatable("gui.molecularmanipulator.omni.conflicts",
                            menu.conflictParts),
                    RED);
        } else {
            setLabel(structureDetail,
                    Component.translatable("gui.molecularmanipulator.omni.missing",
                            menu.missingParts),
                    SECONDARY_TEXT);
        }

        setLabel(networkValue,
                Component.translatable(menu.networkOnline
                        ? "gui.molecularmanipulator.omni.online"
                        : "gui.molecularmanipulator.omni.offline"),
                menu.networkOnline ? GREEN : ORANGE);
        setLabel(activeJobsValue,
                Component.literal(Integer.toString(menu.activeJobs)),
                menu.activeJobs > 0 ? GREEN : PRIMARY_TEXT);
        cpuLanesValue.setValue(Component.literal(Integer.toString(menu.cpuLanes)));
        setLabel(calculationValue,
                menu.activeMaterialCalculations > 0
                        ? Component.translatable("gui.molecularmanipulator.omni.calculating",
                                menu.activeMaterialCalculations)
                        : Component.translatable(
                                "gui.molecularmanipulator.omni.calculation_idle"),
                menu.activeMaterialCalculations > 0 ? CYAN : PRIMARY_TEXT);
        calculationStats.setValue(Component.translatable(
                "gui.molecularmanipulator.omni.calculation_stats",
                menu.completedMaterialCalculations,
                menu.lastMaterialCalculationMillis));

        var quantumState = menu.quantumLinkState;
        int quantumColor = switch (quantumState) {
            case CONNECTED -> GREEN;
            case CONNECTED_BUILD_ONLY -> 0xFF78C8FF;
            case EMPTY, SEARCHING -> PRIMARY_TEXT;
            case STRUCTURE_INCOMPLETE, REMOTE_MISSING, REMOTE_OFFLINE -> ORANGE;
            case FREQUENCY_OCCUPIED, WIRED_CONFLICT, CONNECTION_ERROR -> RED;
        };
        setLabel(quantumStateValue,
                Component.translatable("gui.molecularmanipulator.quantum_state."
                        + quantumState.name().toLowerCase(Locale.ROOT)),
                quantumColor);
        quantumFrequency.setValue(Component.translatable(
                "gui.molecularmanipulator.quantum_frequency",
                menu.quantumFrequency == 0
                        ? "\u2014"
                        : Long.toUnsignedString(menu.quantumFrequency, 16)
                                .toUpperCase(Locale.ROOT)));

        if (previousNetworkOnline != null
                && previousNetworkOnline != menu.networkOnline) {
            pulse(networkValue);
        }
        if (previousFormed != null && previousFormed != menu.formed) {
            pulse(structureState);
            pulse(structureProgress);
        }
        previousNetworkOnline = menu.networkOnline;
        previousFormed = menu.formed;
    }

    private Component projectionLabel() {
        return Component.translatable(OmniComputationGhostPreview.isShowing(menu.getCore())
                ? "gui.molecularmanipulator.omni.projection_hide"
                : "gui.molecularmanipulator.omni.projection");
    }

    private static void pulse(UIElement element) {
        element.style(style -> style
                .opacity(0.35f)
                .transform2D(new Transform2D().scale(0.92f)));
        element.animation()
                .duration(0.32f)
                .ease(Eases.ELASTIC_OUT)
                .style(PropertyRegistry.OPACITY, 1f)
                .style(PropertyRegistry.TRANSFORM_2D, new Transform2D())
                .start();
    }

    private static ProgressBar progress(
            com.lowdragmc.lowdraglib2.gui.ui.UI ui,
            String id,
            ColorRectTexture fillTexture,
            boolean showLabel) {
        var progress = LdUiXml.require(ui, id, ProgressBar.class);
        progress.setRange(0, 1);
        progress.progressBarStyle(style -> style
                .interpolate(true)
                .interpolateStep(0.035f));
        progress.barContainer(container -> {
            container.layout(layout -> layout.paddingAll(1));
            container.style(style -> style.backgroundTexture(new GuiTextureGroup(
                    new ColorRectTexture(AeUiTheme.TRACK),
                    new ColorBorderTexture(-1, AeUiTheme.SHADOW))));
        });
        progress.barBackground.style(style ->
                style.backgroundTexture(new ColorRectTexture(AeUiTheme.PANEL_INSET)));
        progress.bar.style(style -> style.backgroundTexture(fillTexture));
        progress.label.textStyle(style -> style
                .fontSize(7)
                .textColor(0xFFFFFFFF)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL));
        progress.label.setDisplay(showLabel);
        return progress;
    }

    private static Button button(
            com.lowdragmc.lowdraglib2.gui.ui.UI ui,
            String id,
            Component text,
            Component tooltip,
            UIEventListener onClick) {
        var button = LdUiXml.require(ui, id, Button.class);
        button.setText(text);
        button.setOnClick(onClick);
        button.setFocusable(true);
        button.text.layout(layout -> layout.widthPercent(100).heightPercent(100));
        button.textStyle(style -> style
                .adaptiveWidth(false)
                .fontSize(8)
                .textColor(0xFFFFFFFF)
                .textShadow(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        AeUiTheme.styleLdButton(button);
        button.style(style -> style.tooltips(tooltip));
        return button;
    }

    private static Label label(com.lowdragmc.lowdraglib2.gui.ui.UI ui, String id) {
        return LdUiXml.require(ui, id, Label.class);
    }

    private static void setLabel(Label label, Component text, int color) {
        label.setValue(text);
        label.getTextStyle().textColor(color);
    }

}
