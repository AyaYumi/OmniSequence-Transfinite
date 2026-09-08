package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/** 1.21.1's structure/telemetry/quantum layout, implemented with Forge-native controls. */
public final class OmniComputationScreen extends ResponsiveContainerScreen<OmniComputationMenu> {
    private final StructureUpdateConfirmation updateConfirmation = new StructureUpdateConfirmation();
    private OmniButton build, dismantle, projection, refresh, update, keepLegacy, legacyProjection;
    private boolean confirmingLegacyUpdate;

    public OmniComputationScreen(OmniComputationMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        super.init();
        updateConfirmation.cancel();
        build = button(14, 234, 73, 18, text("omni.build"), text("omni.build_tooltip"), () -> {
            if (menu.legacyStructure) confirmingLegacyUpdate = true;
            else menu.requestBuild();
        });
        dismantle = button(91, 234, 73, 18, text("omni.dismantle"), text("omni.dismantle_tooltip"), menu::requestDismantle);
        projection = button(168, 234, 73, 18, projectionLabel(), text("omni.projection_tooltip"), this::toggleProjection);
        refresh = button(245, 234, 73, 18, text("omni.refresh"), text("omni.refresh_tooltip"), menu::requestRefresh);
        update = button(14, 234, 98, 18, text("structure_update_confirm"), text("structure_update_confirm_tooltip"), () -> {
            if (updateConfirmation.click()) {
                confirmingLegacyUpdate = false;
                menu.requestStructureUpdate();
            }
        });
        keepLegacy = button(116, 234, 98, 18, text("structure_update_keep_legacy"), text("structure_update_keep_legacy_tooltip"), () -> {
            updateConfirmation.cancel();
            confirmingLegacyUpdate = false;
            menu.requestKeepLegacyStructure();
        });
        legacyProjection = button(218, 234, 98, 18, projectionLabel(), text("structure_update_projection_warning"), this::toggleProjection);
        updateControls();
    }

    private void toggleProjection() {
        updateConfirmation.cancel();
        OmniComputationGhostPreview.toggle(menu.getCore());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateConfirmation.tick(menu.legacyStructure && !menu.building && !menu.dismantling);
        updateControls();
    }

    private void updateControls() {
        if (!menu.legacyStructure) confirmingLegacyUpdate = false;
        boolean busy = menu.building || menu.dismantling;
        boolean choice = menu.legacyStructure && (!menu.legacyStructureUpdateDismissed || confirmingLegacyUpdate);
        build.visible = dismantle.visible = projection.visible = refresh.visible = !choice;
        update.visible = keepLegacy.visible = legacyProjection.visible = choice;
        build.active = !busy && (menu.legacyStructure || !menu.formed && menu.conflictParts == 0);
        dismantle.active = !busy && menu.dismantlableBlocks > 0;
        update.active = keepLegacy.active = !busy;
        build.setProminent(true);
        update.setDanger(updateConfirmation.isArmed());
        build.setMessage(text(menu.building ? "omni.building" : menu.legacyStructure ? "structure_update_short" : "omni.build"));
        build.setTooltip(Tooltip.create(text(menu.legacyStructure ? "structure_update_confirm_tooltip" : "omni.build_tooltip")));
        dismantle.setMessage(text(menu.dismantling ? "omni.dismantling" : "omni.dismantle"));
        projection.setMessage(projectionLabel());
        legacyProjection.setMessage(projectionLabel());
        update.setMessage(text(updateConfirmation.isArmed() ? "structure_update_second_confirm" : "structure_update_confirm"));
    }

    @Override
    public void removed() {
        updateConfirmation.cancel();
        super.removed();
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        MachineUiLayout.OMNI_COMPUTATION.draw(graphics, x, y);
        int pulse = (int) ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) % 20);
        OmniUiTheme.computationCore(graphics, x + 82, y + 82,
                29 + Math.min(pulse, 20 - pulse) / 5, menu.formed, menu.networkOnline);
        float ratio = menu.totalParts <= 0 ? 0 : menu.correctParts / (float) menu.totalParts;
        if (!menu.formed) OmniUiTheme.progress(graphics, x + 18, y + 160, 128, 8, ratio, OmniUiTheme.ACCENT);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        drawCenteredFittedString(graphics, title.getString().isBlank()
                ? Component.translatable("block.molecularmanipulator.omni_computation_controller") : title,
                166, 9, 312, OmniUiTheme.PRIMARY_TEXT);
        centered(graphics, text(menu.formed ? "omni.formed" : "omni.incomplete"), 119,
                menu.formed ? OmniUiTheme.SUCCESS : OmniUiTheme.WARNING);
        centered(graphics, text("omni.size", menu.legacyStructure ? 31 : OmniComputationStructure.WIDTH,
                menu.legacyStructure ? 31 : OmniComputationStructure.WIDTH,
                menu.legacyStructure ? 39 : OmniComputationStructure.HEIGHT), 132, OmniUiTheme.MUTED_TEXT);
        centered(graphics, text("omni.parts", menu.correctParts, menu.totalParts), 145, OmniUiTheme.PRIMARY_TEXT);
        centered(graphics, structureDetail(), 174, menu.conflictParts > 0 ? OmniUiTheme.ERROR : OmniUiTheme.MUTED_TEXT);

        row(graphics, "omni.network", text(menu.networkOnline ? "omni.online" : "omni.offline"), 42,
                menu.networkOnline ? OmniUiTheme.SUCCESS : OmniUiTheme.WARNING);
        row(graphics, "omni.storage", Component.literal("∞"), 59, OmniUiTheme.ACCENT);
        row(graphics, "omni.parallel", Component.literal("∞"), 75, OmniUiTheme.CYAN);
        row(graphics, "omni.active_jobs", Component.literal(Integer.toString(menu.activeJobs)), 95, OmniUiTheme.PRIMARY_TEXT);
        row(graphics, "omni.virtual_lanes", Component.literal(Integer.toString(menu.cpuLanes)), 110, OmniUiTheme.PRIMARY_TEXT);
        row(graphics, "omni.material_calculation", menu.activeMaterialCalculations > 0
                ? text("omni.calculating", menu.activeMaterialCalculations) : text("omni.calculation_idle"), 125, OmniUiTheme.PRIMARY_TEXT);
        drawFittedString(graphics, text("omni.calculation_stats", menu.completedMaterialCalculations,
                menu.lastMaterialCalculationMillis), 174, 139, 140, OmniUiTheme.MUTED_TEXT);
        drawFittedString(graphics, text("omni.tick_budget"), 174, 151, 140, OmniUiTheme.MUTED_TEXT);
        drawWrappedString(graphics, text(menu.legacyStructure ? "structure_update_projection_warning" : "omni.fixed"),
                174, 163, 140, 2, 9, menu.legacyStructure ? OmniUiTheme.WARNING : OmniUiTheme.MUTED_TEXT);

        drawKeyValueRow(graphics, text("quantum_title"),
                text("quantum_state." + menu.quantumLinkState.name().toLowerCase(Locale.ROOT)),
                18, 286, 202, 8, OmniUiTheme.ACCENT, quantumColor());
        drawFittedString(graphics, text("quantum_frequency", frequency()), 18, 214, 268, OmniUiTheme.MUTED_TEXT);
        graphics.drawString(font, playerInventoryTitle, 85, 268, OmniUiTheme.PRIMARY_TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        if (x >= 10 && x < 154 && y >= 28 && y < 190) {
            graphics.renderComponentTooltip(font, List.of(structureDetail(),
                    text(menu.legacyStructure ? "structure_update_projection_warning" : "omni.fixed")), mouseX, mouseY);
        } else if (x >= 18 && x < 286 && y >= 198 && y < 224) {
            graphics.renderComponentTooltip(font, List.of(text("quantum_state." + menu.quantumLinkState.name().toLowerCase(Locale.ROOT)),
                    text("quantum_frequency", frequency())), mouseX, mouseY);
        } else if (x >= 174 && x < 314 && y >= 139 && y < 182) {
            graphics.renderComponentTooltip(font, List.of(text("omni.calculation_stats", menu.completedMaterialCalculations,
                    menu.lastMaterialCalculationMillis), text("omni.tick_budget"),
                    text(menu.legacyStructure ? "structure_update_projection_warning" : "omni.fixed")), mouseX, mouseY);
        }
    }

    private Component structureDetail() {
        if (menu.legacyStructure) return text(menu.legacyStructureUpdateDismissed && !confirmingLegacyUpdate
                ? "legacy_structure_retained" : "structure_update_available");
        if ((menu.building || menu.dismantling) && menu.buildTotal > 0) return text(menu.dismantling
                ? "omni.dismantle_progress" : "omni.build_progress", menu.buildProgress, menu.buildTotal);
        return text(menu.conflictParts > 0 ? "omni.conflicts" : "omni.missing",
                menu.conflictParts > 0 ? menu.conflictParts : menu.missingParts);
    }

    private int quantumColor() {
        return switch (menu.quantumLinkState) {
            case CONNECTED, CONNECTED_BUILD_ONLY -> OmniUiTheme.SUCCESS;
            case EMPTY, SEARCHING -> OmniUiTheme.PRIMARY_TEXT;
            case STRUCTURE_INCOMPLETE, REMOTE_MISSING, REMOTE_OFFLINE -> OmniUiTheme.WARNING;
            case FREQUENCY_OCCUPIED, WIRED_CONFLICT, CONNECTION_ERROR -> OmniUiTheme.ERROR;
        };
    }

    private String frequency() {
        return menu.quantumFrequency == 0 ? "—" : Long.toUnsignedString(menu.quantumFrequency, 16).toUpperCase(Locale.ROOT);
    }

    private Component projectionLabel() {
        return text(OmniComputationGhostPreview.isShowing(menu.getCore()) ? "omni.projection_hide" : "omni.projection");
    }

    private void centered(GuiGraphics graphics, Component label, int y, int color) {
        drawCenteredFittedString(graphics, label, 82, y, 136, color);
    }

    private void row(GuiGraphics graphics, String key, Component value, int y, int color) {
        drawKeyValueRow(graphics, text(key), value, 174, 314, y, 6, OmniUiTheme.PRIMARY_TEXT, color);
    }

    private OmniButton button(int x, int y, int width, int height, Component label, Component tooltip, Runnable action) {
        var button = addScreenWidget(OmniUiTheme.button(leftPos + x, topPos + y, width, height, label, pressed -> {
            action.run();
            updateControls();
        }));
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    private static Component text(String key, Object... args) { return Component.translatable("gui.molecularmanipulator." + key, args); }
}
