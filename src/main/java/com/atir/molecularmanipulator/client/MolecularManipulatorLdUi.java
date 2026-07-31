package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
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

/**
 * LDLib2 presentation and interaction layer for the Molecular Sequence Rewrite Array.
 *
 * <p>AE2 continues to own the container, slots and synchronization. The XML-backed
 * overlay owns navigation, status labels and the animated pattern-capacity meter;
 * the vanilla edit box remains responsible for text input and IME behavior.</p>
 */
final class MolecularManipulatorLdUi {
    private static final int CYAN = 0xFF69DBFF;
    private static final int ORANGE = 0xFFFFB766;
    private static final int RED = 0xFFFF6D78;
    private static final int SECONDARY_TEXT = 0xFFB7C3D7;

    private final MolecularManipulatorScreen screen;
    private final MolecularManipulatorMenu menu;
    private final ModularUI modularUI;
    private final Button previousPageButton;
    private final Button nextPageButton;
    private final Label pageLabel;
    private final Label capacityLabel;
    private final ProgressBar capacityProgress;
    private int previousPage = -1;
    private int previousPatternRevision = -1;
    private int previousUsedPatterns = -1;

    MolecularManipulatorLdUi(MolecularManipulatorScreen screen, MolecularManipulatorMenu menu) {
        this.screen = screen;
        this.menu = menu;

        var ui = LdUiXml.load("ui/molecular_manipulator.xml");
        previousPageButton = button(ui, "page-previous", Component.literal("<"),
                Component.translatable("gui.molecularmanipulator.page_previous_tooltip"),
                event -> screen.changePage(-1));
        nextPageButton = button(ui, "page-next", Component.literal(">"),
                Component.translatable("gui.molecularmanipulator.page_next_tooltip"),
                event -> screen.changePage(1));
        pageLabel = label(ui, "page-label");
        capacityLabel = label(ui, "capacity-label");
        capacityLabel.style(style -> style.tooltips(Component.translatable(
                "gui.molecularmanipulator.pattern_capacity_tooltip")));
        capacityProgress = progress(ui, "capacity-progress");
        capacityProgress.style(style -> style.tooltips(Component.translatable(
                "gui.molecularmanipulator.pattern_capacity_tooltip")));

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

    private void refresh() {
        boolean waiting = screen.patternSearchWaiting();
        previousPageButton.setActive(!waiting && menu.getPage() > 0);
        nextPageButton.setActive(!waiting && menu.getPage() + 1 < menu.getPageCount());

        pageLabel.setValue(screen.patternPageLabel());
        pageLabel.getTextStyle().textColor(waiting
                ? ORANGE
                : screen.patternSearchHasNoResults() ? RED : SECONDARY_TEXT);

        int totalPatterns = menu.getPatternSlots().size();
        int usedPatterns = (int) menu.getPatternSlots().stream()
                .filter(slot -> slot.hasItem())
                .count();
        capacityLabel.setValue(Component.literal(usedPatterns + "/" + totalPatterns));
        capacityLabel.getTextStyle().textColor(menu.patternSearchActive ? CYAN : SECONDARY_TEXT);
        capacityProgress.setProgress(totalPatterns == 0 ? 0 : usedPatterns / (float) totalPatterns);

        if (previousPage >= 0 && previousPage != menu.getPage()) {
            pulse(pageLabel);
        }
        if (previousPatternRevision >= 0
                && (previousPatternRevision != menu.patternRevision
                        || previousUsedPatterns != usedPatterns)) {
            pulse(capacityLabel);
            pulse(capacityProgress);
        }
        previousPage = menu.getPage();
        previousPatternRevision = menu.patternRevision;
        previousUsedPatterns = usedPatterns;
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

    private static Label label(com.lowdragmc.lowdraglib2.gui.ui.UI ui, String id) {
        return LdUiXml.require(ui, id, Label.class);
    }

    private static ProgressBar progress(
            com.lowdragmc.lowdraglib2.gui.ui.UI ui,
            String id) {
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
        progress.bar.style(style ->
                style.backgroundTexture(new ColorRectTexture(CYAN)));
        progress.label.setDisplay(false);
        return progress;
    }

    private static void pulse(UIElement element) {
        element.style(style -> style
                .opacity(0.45f)
                .transform2D(new Transform2D().scale(0.96f)));
        element.animation()
                .duration(0.28f)
                .ease(Eases.QUAD_OUT)
                .style(PropertyRegistry.OPACITY, 1f)
                .style(PropertyRegistry.TRANSFORM_2D, new Transform2D())
                .start();
    }
}
