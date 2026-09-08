package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Native Minecraft controls; AE2 retains ownership of slots and menu actions. */
final class MolecularCenterControls {
    private final MolecularCenterScreen screen;
    private final MolecularCenterMenu menu;
    private final List<OmniButton> matter = new ArrayList<>();
    private final List<OmniButton> colors = new ArrayList<>();
    private final List<OmniButton> reserves = new ArrayList<>();
    private final OmniButton[] tabs = new OmniButton[4];
    private final StructureUpdateConfirmation updateConfirmation = new StructureUpdateConfirmation();
    private final OmniButton preview, build, previous, next, dismantle;
    private final OmniButton deconstruct, rewrite, output, applyLimit, resetColors, update, keepLegacy;

    MolecularCenterControls(MolecularCenterScreen screen, MolecularCenterMenu menu) {
        this.screen = screen;
        this.menu = menu;
        preview = button(8, 4, 46, 18, screen.previewLabel(), text("preview"), () -> {
            if (MolecularCenterGhostPreview.toggle(menu.getCenter())) menu.requestPreview();
        });
        build = button(58, 4, 46, 18, text("build"), text("build"), menu::requestBuild);
        previous = button(108, 4, 20, 18, Component.literal("<"), text("page_previous_tooltip"),
                () -> screen.changePatternPage(-1));
        next = button(130, 4, 20, 18, Component.literal(">"), text("page_next_tooltip"),
                () -> screen.changePatternPage(1));
        dismantle = button(154, 4, 58, 18, screen.dismantleLabel(), screen.dismantleTooltip(), screen::dismantleClicked);
        String[] tabKeys = {"tab_matter", "tab_auto_craft", "tab_quantum", "tab_colors"};
        for (int i = 0; i < tabs.length; i++) {
            int tab = i;
            tabs[i] = button(216 + i * 50, 4, i == 3 ? 54 : 48, 18, text(tabKeys[i]), text(tabKeys[i]), () -> {
                updateConfirmation.cancel();
                screen.selectTab(tab);
            });
        }

        deconstruct = matterButton(207, 95, 76, 18, screen.deconstructLabel(), text("sequence_deconstruct_tooltip"), menu::requestDeconstructMatter);
        output = matterButton(287, 95, 52, 18, screen.rewriteOutputLabel(), text("rewrite_output_tooltip"), menu::requestCycleRewriteOutput);
        rewrite = matterButton(343, 95, 76, 18, screen.rewriteLabel(), text("sequence_rewrite_tooltip"), menu::requestRewriteMatter);
        matterButton(265, 145, 38, 18, text("matter_target_apply"), text("matter_target_tooltip"),
                () -> menu.requestSetDeconstructTarget(screen.deconstructTargetInput()));
        matterButton(381, 145, 38, 18, text("matter_target_apply"), text("matter_target_tooltip"),
                () -> menu.requestSetRewriteTarget(screen.rewriteTargetInput()));
        applyLimit = button(371, 90, 43, 16, text("auto_craft_apply"), text("auto_craft_output_limit_tooltip"),
                () -> menu.requestSetAutoCraftOutputLimit(screen.autoCraftOutputLimitInput()));
        for (int i = 0; i < MolecularAutoCrafter.MAX_INPUTS; i++) {
            int input = i;
            reserves.add(button(i % 2 == 0 ? 277 : 383, 123 + i / 2 * 22, 32, 16,
                    text("auto_craft_apply"), text("auto_craft_input_reserve_tooltip"),
                    () -> menu.requestSetAutoCraftInputReserve(input, screen.autoCraftInputReserveInput(input))));
        }
        String[] channels = {"R", "G", "B"};
        for (int target = 0; target < 5; target++) {
            for (int channel = 0; channel < 3; channel++) {
                int selectedTarget = target, selectedChannel = channel;
                colors.add(button(323 + channel * 30, 43 + target * 32, 27, 18,
                        Component.literal(channels[channel]), text("visual_color_adjust_tooltip"), () -> {
                            int amount = Screen.hasControlDown() ? 1 : 17;
                            menu.requestAdjustVisualColor(selectedTarget, selectedChannel,
                                    Screen.hasShiftDown() ? -amount : amount);
                        }));
            }
        }
        resetColors = button(286, 204, 128, 18, text("visual_color_reset"), text("visual_color_reset"), menu::requestResetVisualColors);
        update = button(210, 233, 100, 18, text("structure_update_confirm"), text("structure_update_confirm_tooltip"), () -> {
            if (updateConfirmation.click()) menu.requestStructureUpdate();
        });
        keepLegacy = button(314, 233, 100, 18, text("structure_update_keep_legacy"), text("structure_update_keep_legacy_tooltip"), () -> {
            updateConfirmation.cancel();
            menu.requestKeepLegacyStructure();
        });
        applyLimit.setProminent(true);
        refresh();
    }

    void tick() {
        updateConfirmation.tick(menu.legacyStructure && !menu.building && !menu.dismantling);
        refresh();
    }

    void close() { updateConfirmation.cancel(); }

    private void refresh() {
        int tab = screen.detailTab();
        boolean auto = tab == MolecularCenterScreen.TAB_AUTO_CRAFT;
        boolean busy = menu.building || menu.dismantling;
        for (var button : matter) button.visible = tab == MolecularCenterScreen.TAB_MATTER;
        for (var button : colors) button.visible = tab == MolecularCenterScreen.TAB_COLORS;
        for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(tab == i);
        resetColors.visible = tab == MolecularCenterScreen.TAB_COLORS && !menu.legacyStructure;
        update.visible = menu.legacyStructure && !auto;
        keepLegacy.visible = update.visible && !menu.legacyStructureUpdateDismissed;
        update.active = keepLegacy.active = !busy;
        build.active = !busy;
        previous.active = !screen.patternSearchWaiting() && menu.getPage() > 0;
        next.active = !screen.patternSearchWaiting() && menu.getPage() + 1 < menu.getPageCount();
        boolean valid = menu.autoCraftSelectedSlot >= 0 && menu.autoCraftState != MolecularAutoCrafter.AutoCraftState.EMPTY;
        applyLimit.visible = auto && valid;
        applyLimit.active = valid;
        for (int i = 0; i < reserves.size(); i++) {
            reserves.get(i).visible = auto && i < menu.autoCraftInputCount;
            reserves.get(i).active = valid && i < menu.autoCraftInputCount;
        }
        update.setMessage(text(updateConfirmation.isArmed() ? "structure_update_second_confirm" : "structure_update_confirm"));
        preview.setMessage(screen.previewLabel());
        dismantle.setMessage(screen.dismantleLabel());
        dismantle.setTooltip(Tooltip.create(screen.dismantleTooltip()));
        dismantle.setDanger(screen.dismantleConfirming());
        update.setDanger(updateConfirmation.isArmed());
        deconstruct.setMessage(screen.deconstructLabel());
        rewrite.setMessage(screen.rewriteLabel());
        deconstruct.setSelected(menu.deconstructEnabled);
        rewrite.setSelected(menu.rewriteEnabled);
        output.setMessage(screen.rewriteOutputLabel());
    }

    void drawBackground(GuiGraphics graphics, int x, int y) {
        if (screen.detailTab() != MolecularCenterScreen.TAB_MATTER) return;
        OmniUiTheme.progress(graphics, x + 207, y + 136, 96, 5,
                ratio(menu.deconstructJobProgress, 1000), OmniUiTheme.ACCENT);
        OmniUiTheme.progress(graphics, x + 323, y + 136, 96, 5,
                ratio(menu.rewriteJobProgress, 1000), OmniUiTheme.CYAN);
        if (!menu.legacyStructure) OmniUiTheme.progress(graphics, x + 207, y + 264, 212, 5,
                ratio(menu.entropy, menu.entropyCapacity), OmniUiTheme.WARNING);
    }

    private OmniButton matterButton(int x, int y, int width, int height, Component label, Component tooltip, Runnable action) {
        var button = button(x, y, width, height, label, tooltip, action);
        matter.add(button);
        return button;
    }

    private OmniButton button(int x, int y, int width, int height, Component label, Component tooltip, Runnable action) {
        var button = OmniUiTheme.button(screen.getGuiLeft() + x, screen.getGuiTop() + y, width, height, label, pressed -> {
            action.run();
            refresh();
        });
        button.setTooltip(Tooltip.create(tooltip));
        return screen.addScreenWidget(button);
    }

    private static float ratio(long value, long maximum) {
        return maximum <= 0 ? 0 : Math.max(0, Math.min(1, value / (float) maximum));
    }

    private static Component text(String key) { return Component.translatable("gui.molecularmanipulator." + key); }
}
