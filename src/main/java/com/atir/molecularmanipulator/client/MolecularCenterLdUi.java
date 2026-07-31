package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
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
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.math.interpolate.Eases;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * LDLib2 interaction layer for the Molecular Center controller.
 *
 * <p>AE2 remains responsible for the menu, slots, fake-slot packets and synced
 * state. The transparent modular UI owns tabs, actions and animated meters,
 * while vanilla edit boxes retain text input and search semantics.</p>
 */
final class MolecularCenterLdUi {
    private static final int PURPLE = 0xFFB77BFF;
    private static final int CYAN = 0xFF63D8FF;
    private static final int BLUE = 0xFF8EAEFF;
    private static final int PINK = 0xFFFF83D1;
    private static final int RED = 0xFFFF6D78;
    private static final int[] TAB_COLORS = {PURPLE, CYAN, BLUE, PINK};
    private static final int[] TAB_OFFSETS = {0, 50, 100, 150};

    private final MolecularCenterScreen screen;
    private final MolecularCenterMenu menu;
    private final ModularUI modularUI;
    private final List<UIElement> matterControls;
    private final List<UIElement> pipelineControls;
    private final List<UIElement> colorsControls;
    private final List<UIElement> legacyControls;
    private final UIElement tabIndicator;
    private final ColorRectTexture tabIndicatorTexture = new ColorRectTexture(PURPLE);
    private final Button previewButton;
    private final Button previousPageButton;
    private final Button nextPageButton;
    private final Button dismantleButton;
    private final Button matterTabButton;
    private final Button pipelineTabButton;
    private final Button quantumTabButton;
    private final Button colorsTabButton;
    private final Button deconstructButton;
    private final Button rewriteButton;
    private final Button rewriteOutputButton;
    private final Button primaryRouteButton;
    private final Button byproductRouteButton;
    private final Button outputPortButton;
    private final Button resetColorsButton;
    private final Button structureUpdateButton;
    private final Button keepLegacyStructureButton;
    private final ProgressBar deconstructProgress;
    private final ProgressBar rewriteProgress;
    private final ProgressBar entropyProgress;
    private int previousTab = -1;
    private MolecularCenterBlockEntity.MatterJobState previousDeconstructState;
    private MolecularCenterBlockEntity.MatterJobState previousRewriteState;

    MolecularCenterLdUi(MolecularCenterScreen screen, MolecularCenterMenu menu) {
        this.screen = screen;
        this.menu = menu;

        var ui = LdUiXml.load("ui/molecular_center.xml");
        matterControls = ui.select(".matter-control").toList();
        pipelineControls = ui.select(".pipeline-control").toList();
        colorsControls = ui.select(".color-control").toList();
        legacyControls = ui.select(".legacy-control").toList();

        previewButton = button(ui, "preview", screen.previewLabel(),
                Component.translatable("gui.molecularmanipulator.preview"), event -> {
                    if (MolecularCenterGhostPreview.toggle(menu.getCenter())) {
                        menu.requestPreview();
                    }
                    refresh();
                });
        button(ui, "build", Component.translatable("gui.molecularmanipulator.build"),
                Component.translatable("gui.molecularmanipulator.build"),
                event -> menu.requestBuild());
        previousPageButton = button(ui, "page-previous", Component.literal("<"),
                Component.translatable("gui.molecularmanipulator.page_previous_tooltip"),
                event -> screen.changePatternPage(-1));
        nextPageButton = button(ui, "page-next", Component.literal(">"),
                Component.translatable("gui.molecularmanipulator.page_next_tooltip"),
                event -> screen.changePatternPage(1));
        dismantleButton = button(ui, "dismantle", screen.dismantleLabel(),
                screen.dismantleTooltip(), event -> {
                    screen.dismantleClicked();
                    refresh();
                });

        matterTabButton = tab(ui, "tab-matter",
                "gui.molecularmanipulator.tab_matter", MolecularCenterScreen.TAB_MATTER);
        pipelineTabButton = tab(ui, "tab-pipeline",
                "gui.molecularmanipulator.tab_pipeline", MolecularCenterScreen.TAB_PIPELINE);
        quantumTabButton = tab(ui, "tab-quantum",
                "gui.molecularmanipulator.tab_quantum", MolecularCenterScreen.TAB_QUANTUM);
        colorsTabButton = tab(ui, "tab-colors",
                "gui.molecularmanipulator.tab_colors", MolecularCenterScreen.TAB_COLORS);
        tabIndicator = LdUiXml.require(ui, "tab-indicator", UIElement.class);
        tabIndicator.style(style -> style.backgroundTexture(tabIndicatorTexture));

        deconstructButton = button(ui, "matter-deconstruct", screen.deconstructLabel(),
                Component.translatable("gui.molecularmanipulator.sequence_deconstruct_tooltip"),
                event -> menu.requestDeconstructMatter());
        rewriteOutputButton = button(ui, "matter-rewrite-output", screen.rewriteOutputLabel(),
                Component.translatable("gui.molecularmanipulator.rewrite_output_tooltip"),
                event -> menu.requestCycleRewriteOutput());
        rewriteButton = button(ui, "matter-rewrite", screen.rewriteLabel(),
                Component.translatable("gui.molecularmanipulator.sequence_rewrite_tooltip"),
                event -> menu.requestRewriteMatter());
        button(ui, "matter-apply-deconstruct",
                Component.translatable("gui.molecularmanipulator.matter_target_apply"),
                Component.translatable("gui.molecularmanipulator.matter_target_tooltip"),
                event -> menu.requestSetDeconstructTarget(screen.deconstructTargetInput()));
        button(ui, "matter-apply-rewrite",
                Component.translatable("gui.molecularmanipulator.matter_target_apply"),
                Component.translatable("gui.molecularmanipulator.matter_target_tooltip"),
                event -> menu.requestSetRewriteTarget(screen.rewriteTargetInput()));
        deconstructProgress = progress(ui, "matter-deconstruct-progress", 0xFF9B68E8);
        rewriteProgress = progress(ui, "matter-rewrite-progress", 0xFFFF82D8);
        entropyProgress = progress(ui, "matter-entropy-progress", 0xFFE75AAE);

        primaryRouteButton = button(ui, "pipeline-primary-route", screen.primaryRouteLabel(),
                Component.translatable("gui.molecularmanipulator.route_primary"),
                event -> menu.requestCyclePrimaryRoute());
        byproductRouteButton = button(ui, "pipeline-byproduct-route", screen.byproductRouteLabel(),
                Component.translatable("gui.molecularmanipulator.route_byproduct"),
                event -> menu.requestCycleByproductRoute());
        outputPortButton = button(ui, "pipeline-output-port", screen.outputPortLabel(),
                Component.translatable("gui.molecularmanipulator.output_port"),
                event -> menu.requestCycleOutputPort());

        int[] channelColors = {0xFFFF6D78, 0xFF70F2A2, 0xFF69DBFF};
        String[] channelLabels = {"R", "G", "B"};
        for (int target = 0; target < 5; target++) {
            for (int channel = 0; channel < 3; channel++) {
                int selectedTarget = target;
                int selectedChannel = channel;
                int channelColor = channelColors[channel];
                var colorButton = button(ui, "color-" + target + "-" + channel,
                        Component.literal(channelLabels[channel]),
                        Component.translatable(
                                "gui.molecularmanipulator.visual_color_adjust_tooltip"),
                        event -> {
                            int amount = Screen.hasControlDown() ? 1 : 17;
                            menu.requestAdjustVisualColor(selectedTarget, selectedChannel,
                                    Screen.hasShiftDown() ? -amount : amount);
                        });
                colorButton.textStyle(style -> style.textColor(channelColor));
            }
        }
        resetColorsButton = button(ui, "color-reset",
                Component.translatable("gui.molecularmanipulator.visual_color_reset"),
                Component.translatable("gui.molecularmanipulator.visual_color_reset"),
                event -> menu.requestResetVisualColors());

        structureUpdateButton = button(ui, "legacy-update",
                Component.translatable("gui.molecularmanipulator.structure_update_confirm"),
                Component.translatable(
                        "gui.molecularmanipulator.structure_update_confirm_tooltip"),
                event -> menu.requestStructureUpdate());
        keepLegacyStructureButton = button(ui, "legacy-keep",
                Component.translatable(
                        "gui.molecularmanipulator.structure_update_keep_legacy"),
                Component.translatable(
                        "gui.molecularmanipulator.structure_update_keep_legacy_tooltip"),
                event -> menu.requestKeepLegacyStructure());

        modularUI = ModularUI.of(ui);
        refresh();
    }

    void attach(Screen screen) {
        modularUI.setScreenAndInit(screen);
    }

    ModularUI.ModularUIWidget widget() {
        return modularUI.getWidget();
    }

    void tick() {
        refresh();
        modularUI.tick();
    }

    void close() {
        modularUI.onRemoved();
    }

    private void selectTab(int tab) {
        screen.selectTab(tab);
        refresh();
    }

    private void refresh() {
        int tab = screen.detailTab();
        boolean matterVisible = tab == MolecularCenterScreen.TAB_MATTER;
        boolean pipelineVisible = tab == MolecularCenterScreen.TAB_PIPELINE;
        boolean colorsVisible = tab == MolecularCenterScreen.TAB_COLORS;
        boolean busy = menu.building || menu.dismantling;

        setDisplay(matterControls, matterVisible);
        setDisplay(pipelineControls, pipelineVisible);
        setDisplay(colorsControls, colorsVisible);
        setDisplay(legacyControls, menu.legacyStructure);
        entropyProgress.setDisplay(matterVisible && !menu.legacyStructure);
        resetColorsButton.setDisplay(colorsVisible && !menu.legacyStructure);
        keepLegacyStructureButton.setDisplay(
                menu.legacyStructure && !menu.legacyStructureUpdateDismissed);

        matterTabButton.setActive(!matterVisible);
        pipelineTabButton.setActive(!pipelineVisible);
        quantumTabButton.setActive(tab != MolecularCenterScreen.TAB_QUANTUM);
        colorsTabButton.setActive(!colorsVisible);
        previousPageButton.setActive(!screen.patternSearchWaiting() && menu.getPage() > 0);
        nextPageButton.setActive(!screen.patternSearchWaiting()
                && menu.getPage() + 1 < menu.getPageCount());
        structureUpdateButton.setActive(menu.legacyStructure && !busy);
        keepLegacyStructureButton.setActive(menu.legacyStructure && !busy);

        previewButton.setText(screen.previewLabel());
        dismantleButton.setText(screen.dismantleLabel());
        dismantleButton.style(style -> style.tooltips(screen.dismantleTooltip()));
        dismantleButton.textStyle(style -> style.textColor(
                screen.dismantleConfirming() ? RED : 0xFFFFFFFF));
        deconstructButton.setText(screen.deconstructLabel());
        rewriteButton.setText(screen.rewriteLabel());
        rewriteOutputButton.setText(screen.rewriteOutputLabel());
        primaryRouteButton.setText(screen.primaryRouteLabel());
        byproductRouteButton.setText(screen.byproductRouteLabel());
        outputPortButton.setText(screen.outputPortLabel());

        deconstructProgress.setProgress(clampRatio(menu.deconstructJobProgress, 1000));
        rewriteProgress.setProgress(clampRatio(menu.rewriteJobProgress, 1000));
        entropyProgress.setProgress(clampRatio(
                menu.entropy, MolecularCenterBlockEntity.MAX_ENTROPY));

        if (previousTab != tab) {
            animateTab(tab);
            previousTab = tab;
        }
        if (previousDeconstructState != null
                && previousDeconstructState != menu.deconstructJobState) {
            pulse(deconstructProgress);
        }
        if (previousRewriteState != null && previousRewriteState != menu.rewriteJobState) {
            pulse(rewriteProgress);
        }
        previousDeconstructState = menu.deconstructJobState;
        previousRewriteState = menu.rewriteJobState;
    }

    private void animateTab(int tab) {
        int selected = Math.max(0, Math.min(TAB_OFFSETS.length - 1, tab));
        tabIndicatorTexture.color = TAB_COLORS[selected];
        var transform = new Transform2D().translate(TAB_OFFSETS[selected], 0);
        if (previousTab < 0) {
            tabIndicator.style(style -> style.transform2D(transform));
            return;
        }
        tabIndicator.animation()
                .duration(0.2f)
                .ease(Eases.QUAD_OUT)
                .style(PropertyRegistry.TRANSFORM_2D, transform)
                .start();
        pulse(tabIndicator);
    }

    private static float clampRatio(long value, long maximum) {
        return maximum <= 0
                ? 0
                : Math.max(0, Math.min(1, value / (float) maximum));
    }

    private static void setDisplay(List<UIElement> elements, boolean display) {
        for (var element : elements) {
            element.setDisplay(display);
        }
    }

    private static void pulse(UIElement element) {
        element.style(style -> style.opacity(0.4f));
        element.animation()
                .duration(0.28f)
                .ease(Eases.QUAD_OUT)
                .style(PropertyRegistry.OPACITY, 1f)
                .start();
    }

    private Button tab(com.lowdragmc.lowdraglib2.gui.ui.UI ui, String id, String key, int tab) {
        var text = Component.translatable(key);
        return button(ui, id, text, text, event -> selectTab(tab));
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
        button.style(style -> style.tooltips(tooltip));
        return button;
    }

    private static ProgressBar progress(
            com.lowdragmc.lowdraglib2.gui.ui.UI ui,
            String id,
            int color) {
        var progress = LdUiXml.require(ui, id, ProgressBar.class);
        progress.setRange(0, 1);
        progress.progressBarStyle(style -> style
                .interpolate(true)
                .interpolateStep(0.04f));
        progress.barContainer(container -> {
            container.layout(layout -> layout.paddingAll(1));
            container.style(style -> style.backgroundTexture(new GuiTextureGroup(
                    new ColorRectTexture(0xFF080D17),
                    new ColorBorderTexture(-1, 0xFF526079))));
        });
        progress.barBackground.style(style ->
                style.backgroundTexture(new ColorRectTexture(0xFF171D2A)));
        progress.bar.style(style -> style.backgroundTexture(new ColorRectTexture(color)));
        progress.label.setDisplay(false);
        return progress;
    }
}
