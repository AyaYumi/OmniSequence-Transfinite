package com.atir.molecularmanipulator.client;

import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.FakeSlot;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public final class MolecularCenterScreen extends ResponsiveContainerScreen<MolecularCenterMenu> {
    private static final int TAB_MATTER = 0;
    private static final int TAB_PIPELINE = 1;
    private static final int TAB_QUANTUM = 2;
    private static final int TAB_COLORS = 3;
    private static final int PANEL_LEFT = 198;
    private static final int PANEL_RIGHT = 426;
    private static final int ACCENT = 0xFFB77BFF;
    private static final int CYAN = 0xFF63D8FF;
    private static final int DISMANTLE_CONFIRM_TICKS = 60;
    private static final int DISMANTLE_CONFIRM_DELAY_TICKS = 6;
    private static final int PATTERN_SEARCH_DEBOUNCE_TICKS = 5;
    private static final int PATTERN_SEARCH_MAX_LENGTH = 64;

    private Button preview;
    private Button dismantle;
    private Button previousPage;
    private Button nextPage;
    private Button matterTab;
    private Button pipelineTab;
    private Button quantumTab;
    private Button colorsTab;
    private Button deconstruct;
    private Button rewrite;
    private Button rewriteOutput;
    private EditBox patternSearch;
    private EditBox deconstructTarget;
    private EditBox rewriteTarget;
    private Button applyDeconstructTarget;
    private Button applyRewriteTarget;
    private Button primaryRoute;
    private Button byproductRoute;
    private Button outputPort;
    private Button resetColors;
    private Button structureUpdate;
    private Button keepLegacyStructure;
    private final Button[][] colorButtons = new Button[5][3];
    private int detailTab = TAB_MATTER;
    private int dismantleConfirmTicks;
    private int patternSearchDebounceTicks = -1;
    private int observedPatternSearchGeneration = -1;
    private int observedPatternInventoryRevision = Integer.MIN_VALUE;
    private int patternIndexRefreshDebounceTicks = -1;
    private final ArrayList<Integer> filteredPatternSources = new ArrayList<>();

    public MolecularCenterScreen(MolecularCenterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 430;
        imageHeight = 262;
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        cancelDismantleConfirmation();
        if (slot instanceof FakeSlot) {
            var action = mouseButton == 1
                    ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                    : InventoryAction.PICKUP_OR_SET_DOWN;
            NetworkHandler.instance().sendToServer(new InventoryActionPacket(action, slotId, 0));
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean clickedDismantle = button == 0 && dismantle != null
                && dismantle.isMouseOver(logicalMouseX(mouseX), logicalMouseY(mouseY));
        if (!clickedDismantle) {
            cancelDismantleConfirmation();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void init() {
        super.init();
        dismantleConfirmTicks = 0;
        preview = addRenderableWidget(Button.builder(previewLabel(),
                        button -> {
                            if (MolecularCenterGhostPreview.toggle(menu.getCenter())) {
                                menu.requestPreview();
                            } else {
                                button.setMessage(previewLabel());
                            }
                        })
                .bounds(leftPos + 8, topPos + 4, 46, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.molecularmanipulator.build"),
                        button -> menu.requestBuild())
                .bounds(leftPos + 58, topPos + 4, 46, 18).build());
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> changePatternPage(menu.getPage() - 1))
                .bounds(leftPos + 108, topPos + 4, 20, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.page_previous_tooltip")))
                .build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> changePatternPage(menu.getPage() + 1))
                .bounds(leftPos + 130, topPos + 4, 20, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.page_next_tooltip")))
                .build());
        dismantle = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.dismantle"),
                        button -> dismantleClicked())
                .bounds(leftPos + 154, topPos + 4, 58, 18)
                .build());
        updateDismantleButton();

        matterTab = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.tab_matter"),
                        button -> selectTab(TAB_MATTER))
                .bounds(leftPos + 216, topPos + 4, 48, 18).build());
        pipelineTab = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.tab_pipeline"),
                        button -> selectTab(TAB_PIPELINE))
                .bounds(leftPos + 266, topPos + 4, 48, 18).build());
        quantumTab = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.tab_quantum"),
                        button -> selectTab(TAB_QUANTUM))
                .bounds(leftPos + 316, topPos + 4, 48, 18).build());
        colorsTab = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.tab_colors"),
                        button -> selectTab(TAB_COLORS))
                .bounds(leftPos + 366, topPos + 4, 54, 18).build());

        patternSearch = new EditBox(
                font,
                leftPos + 17,
                topPos + 29,
                112,
                15,
                Component.translatable("gui.molecularmanipulator.pattern_search"));
        patternSearch.setMaxLength(PATTERN_SEARCH_MAX_LENGTH);
        patternSearch.setHint(Component.translatable(
                "gui.molecularmanipulator.pattern_search_hint"));
        patternSearch.setValue(menu.getClientPatternSearchQuery());
        patternSearch.setResponder(value -> patternSearchDebounceTicks =
                PATTERN_SEARCH_DEBOUNCE_TICKS);
        addRenderableWidget(patternSearch);

        deconstruct = addRenderableWidget(Button.builder(
                        deconstructLabel(),
                        button -> menu.requestDeconstructMatter())
                .bounds(leftPos + 207, topPos + 107, 76, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.sequence_deconstruct_tooltip")))
                .build());
        rewrite = addRenderableWidget(Button.builder(
                        rewriteLabel(),
                        button -> menu.requestRewriteMatter())
                .bounds(leftPos + 343, topPos + 107, 76, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.sequence_rewrite_tooltip")))
                .build());
        rewriteOutput = addRenderableWidget(Button.builder(
                        rewriteOutputLabel(),
                        button -> menu.requestCycleRewriteOutput())
                .bounds(leftPos + 287, topPos + 107, 52, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.rewrite_output_tooltip")))
                .build());
        var targetTooltip = Tooltip.create(
                Component.translatable("gui.molecularmanipulator.matter_target_tooltip"));
        deconstructTarget = new EditBox(font, leftPos + 207, topPos + 153, 56, 18,
                Component.translatable("gui.molecularmanipulator.matter_deconstruct_target"));
        configureTargetField(deconstructTarget, menu.deconstructTarget, targetTooltip);
        addRenderableWidget(deconstructTarget);
        applyDeconstructTarget = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.matter_target_apply"),
                        button -> menu.requestSetDeconstructTarget(parseTarget(deconstructTarget)))
                .bounds(leftPos + 265, topPos + 153, 38, 18)
                .tooltip(targetTooltip)
                .build());
        rewriteTarget = new EditBox(font, leftPos + 323, topPos + 153, 56, 18,
                Component.translatable("gui.molecularmanipulator.matter_rewrite_target"));
        configureTargetField(rewriteTarget, menu.rewriteTarget, targetTooltip);
        addRenderableWidget(rewriteTarget);
        applyRewriteTarget = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.matter_target_apply"),
                        button -> menu.requestSetRewriteTarget(parseTarget(rewriteTarget)))
                .bounds(leftPos + 381, topPos + 153, 38, 18)
                .tooltip(targetTooltip)
                .build());

        primaryRoute = addRenderableWidget(Button.builder(primaryRouteLabel(),
                        button -> menu.requestCyclePrimaryRoute())
                .bounds(leftPos + 210, topPos + 50, 204, 20).build());
        byproductRoute = addRenderableWidget(Button.builder(byproductRouteLabel(),
                        button -> menu.requestCycleByproductRoute())
                .bounds(leftPos + 210, topPos + 76, 204, 20).build());
        outputPort = addRenderableWidget(Button.builder(outputPortLabel(),
                        button -> menu.requestCycleOutputPort())
                .bounds(leftPos + 210, topPos + 102, 204, 20).build());

        var colorTooltip = Tooltip.create(
                Component.translatable("gui.molecularmanipulator.visual_color_adjust_tooltip"));
        var channelLabels = new Component[] {
                Component.literal("R").withStyle(ChatFormatting.RED),
                Component.literal("G").withStyle(ChatFormatting.GREEN),
                Component.literal("B").withStyle(ChatFormatting.BLUE)
        };
        for (int target = 0; target < colorButtons.length; target++) {
            for (int channel = 0; channel < colorButtons[target].length; channel++) {
                int selectedTarget = target;
                int selectedChannel = channel;
                colorButtons[target][channel] = addRenderableWidget(Button.builder(channelLabels[channel],
                                button -> {
                                    int amount = Screen.hasControlDown() ? 1 : 17;
                                    menu.requestAdjustVisualColor(selectedTarget, selectedChannel,
                                            Screen.hasShiftDown() ? -amount : amount);
                                })
                        .bounds(leftPos + 323 + channel * 30, topPos + 48 + target * 32, 27, 18)
                        .tooltip(colorTooltip)
                        .build());
            }
        }
        resetColors = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.visual_color_reset"),
                        button -> menu.requestResetVisualColors())
                .bounds(leftPos + 286, topPos + 211, 128, 18).build());
        structureUpdate = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.structure_update_confirm"),
                        button -> menu.requestStructureUpdate())
                .bounds(leftPos + 210, topPos + 239, 100, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.structure_update_confirm_tooltip")))
                .build());
        keepLegacyStructure = addRenderableWidget(Button.builder(
                        Component.translatable("gui.molecularmanipulator.structure_update_keep_legacy"),
                        button -> menu.requestKeepLegacyStructure())
                .bounds(leftPos + 314, topPos + 239, 100, 18)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.molecularmanipulator.structure_update_keep_legacy_tooltip")))
                .build());
        updateStructureUpdateControls();
        selectTab(TAB_MATTER);
        observedPatternInventoryRevision = menu.patternInventoryRevision;
        boolean hasPatternSearchQuery = !menu.getClientPatternSearchQuery().isBlank();
        if (hasPatternSearchQuery
                && menu.getClientPatternSearchRevision() != observedPatternInventoryRevision) {
            menu.invalidateClientPatternSearchIndex(observedPatternInventoryRevision);
            patternIndexRefreshDebounceTicks = PATTERN_SEARCH_DEBOUNCE_TICKS;
        } else if (hasPatternSearchQuery && !menu.isClientPatternSearchComplete()) {
            patternIndexRefreshDebounceTicks = PATTERN_SEARCH_DEBOUNCE_TICKS;
        }
        observedPatternSearchGeneration = menu.getClientPatternSearchGeneration();
        if (menu.isClientPatternSearchComplete()
                && !menu.getClientPatternSearchQuery().isBlank()) {
            rebuildPatternSearch(menu.getPage());
        }
    }

    private void selectTab(int tab) {
        detailTab = Math.max(TAB_MATTER, Math.min(TAB_COLORS, tab));
        boolean matterVisible = detailTab == TAB_MATTER;
        deconstruct.visible = matterVisible;
        rewrite.visible = matterVisible;
        rewriteOutput.visible = matterVisible;
        deconstructTarget.visible = matterVisible;
        rewriteTarget.visible = matterVisible;
        applyDeconstructTarget.visible = matterVisible;
        applyRewriteTarget.visible = matterVisible;
        for (var slot : menu.getSequenceSlots()) {
            slot.setActive(matterVisible);
        }
        for (var slot : menu.getSpeedSlots()) {
            slot.setActive(matterVisible);
        }
        boolean quantumVisible = detailTab == TAB_QUANTUM;
        menu.getQuantumSlot().setActive(quantumVisible);

        boolean pipelineVisible = detailTab == TAB_PIPELINE;
        primaryRoute.visible = pipelineVisible;
        byproductRoute.visible = pipelineVisible;
        outputPort.visible = pipelineVisible;

        boolean colorsVisible = detailTab == TAB_COLORS;
        for (var row : colorButtons) {
            for (var button : row) {
                button.visible = colorsVisible;
            }
        }
        resetColors.visible = colorsVisible && !menu.legacyStructure;
        matterTab.active = !matterVisible;
        pipelineTab.active = !pipelineVisible;
        quantumTab.active = !quantumVisible;
        colorsTab.active = !colorsVisible;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        patternSearch.tick();
        if (patternSearchDebounceTicks > 0 && --patternSearchDebounceTicks == 0) {
            commitPatternSearch();
        }
        if (menu.patternInventoryRevision != observedPatternInventoryRevision) {
            observedPatternInventoryRevision = menu.patternInventoryRevision;
            menu.invalidateClientPatternSearchIndex(observedPatternInventoryRevision);
            patternIndexRefreshDebounceTicks =
                    menu.getClientPatternSearchQuery().isBlank()
                            ? -1
                            : PATTERN_SEARCH_DEBOUNCE_TICKS;
        }
        if (patternIndexRefreshDebounceTicks > 0
                && --patternIndexRefreshDebounceTicks == 0) {
            if (!menu.getClientPatternSearchQuery().isBlank()) {
                menu.requestPatternSearchIndex();
            }
        }
        int searchGeneration = menu.getClientPatternSearchGeneration();
        if (searchGeneration != observedPatternSearchGeneration) {
            observedPatternSearchGeneration = searchGeneration;
            if (menu.isClientPatternSearchComplete()
                    && !menu.getClientPatternSearchQuery().isBlank()) {
                rebuildPatternSearch(menu.getPage());
            } else if (!menu.isClientPatternSearchComplete()) {
                filteredPatternSources.clear();
            }
        }
        if (dismantleConfirmTicks > 0 && --dismantleConfirmTicks == 0) {
            updateDismantleButton();
        }
        previousPage.active = menu.getPage() > 0;
        nextPage.active = menu.getPage() + 1 < menu.getPageCount();
        primaryRoute.setMessage(primaryRouteLabel());
        byproductRoute.setMessage(byproductRouteLabel());
        outputPort.setMessage(outputPortLabel());
        preview.setMessage(previewLabel());
        deconstruct.setMessage(deconstructLabel());
        rewrite.setMessage(rewriteLabel());
        rewriteOutput.setMessage(rewriteOutputLabel());
        syncTargetField(deconstructTarget, menu.deconstructTarget);
        syncTargetField(rewriteTarget, menu.rewriteTarget);
        updateStructureUpdateControls();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (patternSearch != null && patternSearch.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                patternSearchDebounceTicks = -1;
                commitPatternSearch();
                return true;
            }
            if (patternSearch.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitPatternSearch() {
        String query = patternSearch.getValue().strip();
        menu.setClientPatternSearchQuery(query);
        filteredPatternSources.clear();
        if (query.isEmpty()) {
            patternIndexRefreshDebounceTicks = -1;
            menu.requestPage(0);
            return;
        }
        if (menu.isClientPatternSearchComplete()) {
            rebuildPatternSearch(0);
        } else if (patternIndexRefreshDebounceTicks <= 0) {
            menu.requestPatternSearchIndex();
        }
    }

    private void rebuildPatternSearch(int preferredPage) {
        String query = menu.getClientPatternSearchQuery().strip();
        filteredPatternSources.clear();
        if (query.isEmpty()) {
            menu.requestPage(0);
            return;
        }
        menu.forEachClientPatternSearchEntry((sourceSlot, keys) -> {
            for (var key : keys) {
                try {
                    if (PatternSearchMatcher.contains(key.getDisplayName().getString(), query)
                            || PatternSearchMatcher.contains(key.getId().toString(), query)) {
                        filteredPatternSources.add(sourceSlot);
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // An optional/custom AE key must not make the controller screen unusable.
                }
            }
        });
        showFilteredPatternPage(preferredPage);
    }

    private void changePatternPage(int requestedPage) {
        if (menu.getClientPatternSearchQuery().isBlank()) {
            menu.requestPage(requestedPage);
        } else {
            showFilteredPatternPage(requestedPage);
        }
    }

    private void showFilteredPatternPage(int requestedPage) {
        int pageCount = Math.max(1, (filteredPatternSources.size()
                + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        int selectedPage = Math.max(0, Math.min(pageCount - 1, requestedPage));
        int[] projection = new int[MolecularCenterBlockEntity.PATTERNS_PER_PAGE];
        Arrays.fill(projection, -1);
        int firstResult = selectedPage * MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
        for (int slot = 0;
                slot < projection.length && firstResult + slot < filteredPatternSources.size();
                slot++) {
            projection[slot] = filteredPatternSources.get(firstResult + slot);
        }
        menu.requestFilteredPatternPage(
                menu.getClientPatternSearchRevision(),
                selectedPage,
                filteredPatternSources.size(),
                projection);
    }

    private void updateStructureUpdateControls() {
        if (structureUpdate == null || keepLegacyStructure == null) {
            return;
        }
        structureUpdate.visible = menu.legacyStructure;
        keepLegacyStructure.visible = menu.legacyStructure
                && !menu.legacyStructureUpdateDismissed;
        boolean idle = !menu.building && !menu.dismantling;
        structureUpdate.active = menu.legacyStructure && idle;
        keepLegacyStructure.active = menu.legacyStructure && idle;
        resetColors.visible = detailTab == TAB_COLORS && !menu.legacyStructure;
    }

    private void dismantleClicked() {
        if (dismantleConfirmTicks == 0) {
            dismantleConfirmTicks = DISMANTLE_CONFIRM_TICKS;
            updateDismantleButton();
            return;
        }
        if (dismantleConfirmTicks <= DISMANTLE_CONFIRM_TICKS - DISMANTLE_CONFIRM_DELAY_TICKS) {
            cancelDismantleConfirmation();
            menu.requestDismantle();
        }
    }

    private void cancelDismantleConfirmation() {
        if (dismantleConfirmTicks == 0) {
            return;
        }
        dismantleConfirmTicks = 0;
        updateDismantleButton();
    }

    private void updateDismantleButton() {
        if (dismantle == null) {
            return;
        }
        boolean confirming = dismantleConfirmTicks > 0;
        dismantle.setMessage(confirming
                ? Component.translatable("gui.molecularmanipulator.dismantle_confirm")
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("gui.molecularmanipulator.dismantle"));
        dismantle.setTooltip(Tooltip.create(Component.translatable(confirming
                ? "gui.molecularmanipulator.dismantle_confirm_tooltip"
                : "gui.molecularmanipulator.dismantle_tooltip")));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF171424, 0xFF0C1320);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, ACCENT);
        graphics.fill(x + 4, y + 28, x + 192, y + 122, 0xE6192233);
        graphics.fill(x + 4, y + 136, x + 192, y + imageHeight - 4, 0xE6151C2B);
        drawPanelBorder(graphics, x + 4, y + 28, x + 192, y + 122, 0xFF393353);
        drawPanelBorder(graphics, x + 4, y + 136, x + 192, y + imageHeight - 4, 0xFF393353);
        graphics.fill(x + PANEL_LEFT, y + 28, x + PANEL_RIGHT, y + imageHeight - 4, 0xE6131A2A);
        drawPanelBorder(graphics, x + PANEL_LEFT, y + 28, x + PANEL_RIGHT, y + imageHeight - 4,
                detailTab == TAB_MATTER ? ACCENT
                        : detailTab == TAB_PIPELINE ? CYAN
                        : detailTab == TAB_QUANTUM ? 0xFF8EAEFF
                        : 0xFFFF83D1);
        if (menu.legacyStructure) {
            graphics.fill(x + 204, y + 225, x + 422, y + 259, 0xF0201820);
            drawPanelBorder(graphics, x + 204, y + 225, x + 422, y + 259, 0xFFFFB75E);
        }
        drawSlotGrid(graphics, MolecularCenterMenu.PATTERN_X, MolecularCenterMenu.PATTERN_Y, 9, 4);
        drawSlotGrid(graphics, MolecularCenterMenu.PLAYER_X, MolecularCenterMenu.PLAYER_MAIN_Y, 9, 3);
        drawSlotGrid(graphics, MolecularCenterMenu.PLAYER_X, MolecularCenterMenu.PLAYER_HOTBAR_Y, 9, 1);
        if (detailTab == TAB_MATTER) {
            drawSlotFrame(graphics, MolecularCenterMenu.SEQUENCE_INPUT_X, MolecularCenterMenu.SEQUENCE_SLOT_Y,
                    0xFF9B68E8);
            drawSlotFrame(graphics, MolecularCenterMenu.SEQUENCE_SAMPLE_X, MolecularCenterMenu.SEQUENCE_SLOT_Y,
                    0xFF66C8FF);
            drawSlotFrame(graphics, MolecularCenterMenu.SEQUENCE_OUTPUT_X, MolecularCenterMenu.SEQUENCE_SLOT_Y,
                    0xFFFF82D8);
            for (int slot = 0; slot < menu.getSpeedSlots().size(); slot++) {
                drawSlotFrame(graphics, MolecularCenterMenu.SPEED_SLOT_X + slot * 18,
                        MolecularCenterMenu.SPEED_SLOT_Y, 0xFFB77BFF);
            }
            graphics.fill(x + 249, y + 85, x + 272, y + 87, 0xFF755A9A);
            graphics.fill(x + 321, y + 85, x + 344, y + 87, 0xFF755A9A);
            graphics.fill(x + 269, y + 83, x + 272, y + 89, 0xFFB985FF);
            graphics.fill(x + 341, y + 83, x + 344, y + 89, 0xFFB985FF);
        } else if (detailTab == TAB_QUANTUM) {
            drawSlotFrame(graphics, MolecularCenterMenu.QUANTUM_SLOT_X, MolecularCenterMenu.QUANTUM_SLOT_Y,
                    0xFF8EAEFF);
            graphics.fill(x + 302, y + 91, x + 304, y + 98, 0xFF536B9F);
            graphics.fill(x + 302, y + 98, x + 304, y + 101, 0xFF8EAEFF);
        }
    }

    private void drawSlotGrid(GuiGraphics graphics, int startX, int startY, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                drawSlotFrame(graphics, startX + column * 18, startY + row * 18, 0xFF4B4961);
            }
        }
    }

    private void drawSlotFrame(GuiGraphics graphics, int slotX, int slotY, int borderColor) {
        int x = leftPos + slotX;
        int y = topPos + slotY;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, borderColor);
        graphics.fill(x, y, x + 16, y + 16, 0xFF090D16);
    }

    private static void drawPanelBorder(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var pageLabel = Component.translatable(
                "gui.molecularmanipulator.page",
                menu.getPage() + 1,
                menu.getPageCount());
        int pageLabelWidth = font.width(pageLabel);
        float pageLabelScale = Math.min(1.0F, 54.0F / Math.max(1, pageLabelWidth));
        drawScaledString(
                graphics,
                pageLabel,
                188 - Math.round(pageLabelWidth * pageLabelScale),
                33,
                pageLabelScale,
                0xFFB7C3D7);
        var state = menu.formed ? Component.translatable("gui.molecularmanipulator.formed")
                : Component.translatable("gui.molecularmanipulator.incomplete");
        graphics.drawString(font, state, 8, 125, menu.formed ? 0xFF70F2A2 : 0xFFFFB75E, false);
        if (menu.buildTotal > 0 && menu.buildProgress < menu.buildTotal) {
            graphics.drawString(font, Component.translatable("gui.molecularmanipulator.progress",
                    menu.buildProgress, menu.buildTotal), 90, 125, 0xFFD8DDE8, false);
        }
        graphics.drawString(font, playerInventoryTitle, 17, 141, 0xFFB7C3D7, false);

        switch (detailTab) {
            case TAB_MATTER -> renderMatterTab(graphics);
            case TAB_PIPELINE -> renderPipelineTab(graphics);
            case TAB_QUANTUM -> renderQuantumTab(graphics);
            case TAB_COLORS -> renderColorsTab(graphics);
            default -> {
            }
        }
        if (menu.legacyStructure) {
            graphics.drawString(font, Component.translatable(
                    menu.legacyStructureUpdateDismissed
                            ? "gui.molecularmanipulator.legacy_structure_retained"
                            : "gui.molecularmanipulator.structure_update_available"),
                    210, 228, 0xFFFFB75E, false);
        }
    }

    private void renderMatterTab(GuiGraphics graphics) {
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.sequence_input"),
                MolecularCenterMenu.SEQUENCE_INPUT_X + 8, 53, 0xFFD9C8F5);
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.sequence_blueprint"),
                MolecularCenterMenu.SEQUENCE_SAMPLE_X + 8, 53, 0xFFC1E9FF);
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.sequence_output"),
                MolecularCenterMenu.SEQUENCE_OUTPUT_X + 8, 53, 0xFFFFC4E9);
        var deconstructState = matterStateLabel(menu.deconstructJobState);
        var rewriteState = matterStateLabel(menu.rewriteJobState);
        drawCenteredFittedString(graphics, deconstructState, 255, 128, 96,
                matterStateColor(menu.deconstructJobState));
        drawCenteredFittedString(graphics, rewriteState, 371, 128, 96,
                matterStateColor(menu.rewriteJobState));
        var deconstructCount = Component.translatable("gui.molecularmanipulator.matter_job_count",
                formatAmount(menu.deconstructJobProcessed),
                menu.deconstructTarget == 0 ? "\u221e" : formatAmount(menu.deconstructTarget));
        var rewriteCount = Component.translatable("gui.molecularmanipulator.matter_job_count",
                formatAmount(menu.rewriteJobProcessed),
                menu.rewriteTarget == 0 ? "\u221e" : formatAmount(menu.rewriteTarget));
        drawCenteredFittedString(graphics, deconstructCount, 255, 138, 96, 0xFFC8B8D8);
        drawCenteredFittedString(graphics, rewriteCount, 371, 138, 96, 0xFFC8B8D8);
        drawMeter(graphics, 207, 149, 96, 3, menu.deconstructJobProgress, 1000, 0xFF9B68E8);
        drawMeter(graphics, 323, 149, 96, 3, menu.rewriteJobProgress, 1000, 0xFFFF82D8);
        int contentLeft = 207;
        int contentRight = 419;
        int columnMiddle = (contentLeft + contentRight) / 2;
        int columnGap = 6;
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.matter_speed",
                        menu.speedCards, menu.matterCycleTicks),
                contentLeft, 185, MolecularCenterMenu.SPEED_SLOT_X - 8 - contentLeft,
                0xFFD9C8F5);

        drawSequenceAmount(graphics, "metal", menu.metalSequence, 205,
                contentLeft, columnMiddle - columnGap / 2, 0xFFB9C7D5);
        drawSequenceAmount(graphics, "crystal", menu.crystalSequence, 205,
                columnMiddle + columnGap / 2, contentRight, 0xFF73CFFF);
        drawSequenceAmount(graphics, "mineral", menu.mineralSequence, 220,
                contentLeft, columnMiddle - columnGap / 2, 0xFFB58A62);
        drawSequenceAmount(graphics, "organic", menu.organicSequence, 220,
                columnMiddle + columnGap / 2, contentRight, 0xFF73D590);
        if (!menu.legacyStructure) {
            drawLabelValueRow(graphics,
                    Component.translatable("gui.molecularmanipulator.sequence_entropy"),
                    Component.literal(formatAmount(menu.entropy) + " / "
                            + formatAmount(MolecularCenterBlockEntity.MAX_ENTROPY)),
                    contentLeft, contentRight, 239, 0xFFFFA4D8, 0xFFC8B8D8);
            drawMeter(graphics, contentLeft, 251, contentRight - contentLeft, 3,
                    menu.entropy, MolecularCenterBlockEntity.MAX_ENTROPY, 0xFFE75AAE);
        }
    }

    private void drawSequenceAmount(GuiGraphics graphics, String type, long amount, int y,
                                    int left, int right, int color) {
        drawLabelValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.sequence_" + type),
                Component.literal(formatAmount(amount)),
                left, right, y, color, 0xFFE4E7EF);
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

    private void drawFittedString(GuiGraphics graphics, Component text, int x, int y,
                                  int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= Math.max(1, maxWidth)) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        drawScaledString(graphics, text, x, y,
                Math.max(1, maxWidth) / (float) textWidth, color);
    }

    private void drawCenteredFittedString(GuiGraphics graphics, Component text, int centerX, int y,
                                          int maxWidth, int color) {
        int availableWidth = Math.max(1, maxWidth);
        int textWidth = font.width(text);
        if (textWidth <= availableWidth) {
            graphics.drawCenteredString(font, text, centerX, y, color);
            return;
        }
        float scale = availableWidth / (float) textWidth;
        drawScaledString(graphics, text,
                centerX - Math.round(textWidth * scale * 0.5F), y, scale, color);
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

    private static void drawMeter(GuiGraphics graphics, int x, int y, int width, int height,
            long value, long maximum, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF25283A);
        int filled = maximum <= 0 ? 0 : (int) Math.min(width,
                Math.ceil(width * Math.max(0, value) / (double) maximum));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, color);
        }
    }

    private void renderPipelineTab(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline"),
                210, 34, CYAN, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline_cache_types",
                menu.pipelineCacheTypes), 210, 133, 0xFFD5DBE8, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline_cache_amount",
                formatAmount(menu.pipelineCacheAmount)), 210, 147, 0xFFD5DBE8, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline_pending",
                formatAmount(menu.pendingOutputAmount)), 210, 161, 0xFFD5DBE8, false);
        var pipelineState = menu.pipelineBlocked
                ? Component.translatable("gui.molecularmanipulator.pipeline_blocked")
                : Component.translatable("gui.molecularmanipulator.pipeline_clear");
        graphics.drawString(font, pipelineState, 210, 179,
                menu.pipelineBlocked ? 0xFFFF6D78 : 0xFF70F2A2, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline_recipes",
                menu.activePipelineRecipes), 210, 197, 0xFFB7C3D7, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline_crafts",
                formatAmount(menu.activePipelineCrafts)), 210, 211, 0xFFB7C3D7, false);
        if (!menu.legacyStructure) {
            graphics.drawString(font, Component.translatable("gui.molecularmanipulator.pipeline_transfer",
                    formatAmount(menu.lastPipelineTransfer)), 210, 225, 0xFFB7C3D7, false);
        }
    }

    private void renderQuantumTab(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.quantum_title"),
                210, 35, 0xFF8EAEFF, false);
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.quantum_singularity"),
                MolecularCenterMenu.QUANTUM_SLOT_X + 8, 52, 0xFFD9E2FF);

        var state = menu.quantumLinkState;
        int stateColor = switch (state) {
            case CONNECTED -> 0xFF70F2A2;
            case CONNECTED_BUILD_ONLY -> 0xFF78C8FF;
            case EMPTY, SEARCHING -> 0xFFB7C3D7;
            case REMOTE_MISSING, REMOTE_OFFLINE, STRUCTURE_INCOMPLETE -> 0xFFFFB75E;
            default -> 0xFFFF6D78;
        };
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.quantum_status",
                Component.translatable("gui.molecularmanipulator.quantum_state."
                        + state.name().toLowerCase(Locale.ROOT))), 210, 107, stateColor, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.quantum_frequency",
                menu.quantumFrequency == 0 ? "—" : formatFrequency(menu.quantumFrequency)),
                210, 125, 0xFFD5DBE8, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.quantum_power",
                formatAmount((long) MolecularCenterBlockEntity.QUANTUM_LINK_POWER)),
                210, 143, 0xFFD5DBE8, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.quantum_channel"),
                210, 161, 0xFFB7C3D7, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.quantum_remote_ring"),
                210, 179, 0xFFB7C3D7, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.spawn_protection"),
                210, 205, 0xFF70F2A2, false);
        if (!menu.legacyStructure) {
            graphics.drawString(font, Component.translatable("gui.molecularmanipulator.spawn_protection_area"),
                    210, 220, 0xFFB7C3D7, false);
        }
    }

    private void renderColorsTab(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.visual_colors"),
                210, 34, 0xFFFF83D1, false);
        for (int target = 0; target < colorButtons.length; target++) {
            int color = visualColor(target);
            int y = 48 + target * 32;
            graphics.drawString(font,
                    Component.translatable("gui.molecularmanipulator.visual_color." + target),
                    210, y, 0xFFD5DBE8, false);
            graphics.drawString(font, String.format(Locale.ROOT, "#%06X", color),
                    266, y, color, false);
            graphics.fill(308, y + 2, 316, y + 10, 0xFF000000 | color);
        }
    }

    private void configureTargetField(EditBox field, long target, Tooltip tooltip) {
        field.setMaxLength(12);
        field.setFilter(MolecularCenterScreen::isTargetText);
        field.setValue(Long.toString(target));
        field.setTooltip(tooltip);
    }

    private static boolean isTargetText(String value) {
        if (value.isEmpty()) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static long parseTarget(EditBox field) {
        if (field.getValue().isBlank()) {
            return 0;
        }
        try {
            return Math.min(MolecularCenterBlockEntity.MAX_JOB_TARGET,
                    Math.max(0, Long.parseLong(field.getValue())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void syncTargetField(EditBox field, long target) {
        if (!field.isFocused() && parseTarget(field) != target) {
            field.setValue(Long.toString(target));
        }
    }

    private Component deconstructLabel() {
        return Component.translatable(menu.deconstructEnabled
                ? "gui.molecularmanipulator.sequence_deconstruct_stop"
                : "gui.molecularmanipulator.sequence_deconstruct_start");
    }

    private Component rewriteLabel() {
        return Component.translatable(menu.rewriteEnabled
                ? "gui.molecularmanipulator.sequence_rewrite_stop"
                : "gui.molecularmanipulator.sequence_rewrite_start");
    }

    private Component rewriteOutputLabel() {
        return Component.translatable("gui.molecularmanipulator.rewrite_output."
                + menu.rewriteOutputMode.name().toLowerCase(Locale.ROOT));
    }

    private static Component matterStateLabel(MolecularCenterBlockEntity.MatterJobState state) {
        return Component.translatable("gui.molecularmanipulator.matter_job_state."
                + state.name().toLowerCase(Locale.ROOT));
    }

    private static int matterStateColor(MolecularCenterBlockEntity.MatterJobState state) {
        return switch (state) {
            case RUNNING -> 0xFF70F2A2;
            case WAITING_NETWORK, WAITING_POWER, COOLING, INPUT_EMPTY, INSUFFICIENT_SEQUENCE -> 0xFFFFB75E;
            case IDLE, STOPPED, TARGET_REACHED -> 0xFFB7C3D7;
            default -> 0xFFFF6D78;
        };
    }

    private Component primaryRouteLabel() {
        return Component.translatable("gui.molecularmanipulator.route_primary",
                routeName(menu.primaryRoute));
    }

    private Component byproductRouteLabel() {
        return Component.translatable("gui.molecularmanipulator.route_byproduct",
                routeName(menu.byproductRoute));
    }

    private Component outputPortLabel() {
        return Component.translatable("gui.molecularmanipulator.output_port",
                Component.translatable("gui.molecularmanipulator.output_port."
                        + menu.outputPort.getSerializedName()));
    }

    private Component previewLabel() {
        return Component.translatable(MolecularCenterGhostPreview.isShowing(menu.getCenter())
                ? "gui.molecularmanipulator.preview_hide"
                : "gui.molecularmanipulator.preview");
    }

    private static Component routeName(MolecularCenterBlockEntity.PipelineRoute route) {
        return Component.translatable(switch (route) {
            case INTERNAL -> "gui.molecularmanipulator.route_internal";
            case PORT -> "gui.molecularmanipulator.route_port";
            case NETWORK -> "gui.molecularmanipulator.route_network";
        });
    }

    private static String formatAmount(long amount) {
        if (amount >= 1_000_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fT", amount / 1_000_000_000_000.0);
        }
        if (amount >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", amount / 1_000.0);
        }
        return Long.toString(amount);
    }

    private static String formatFrequency(long frequency) {
        return Long.toUnsignedString(frequency, 16).toUpperCase(Locale.ROOT);
    }

    private int visualColor(int target) {
        return switch (target) {
            case 0 -> menu.fieldColor;
            case 1 -> menu.coreColor;
            case 2 -> menu.primaryRingColor;
            case 3 -> menu.secondaryRingColor;
            case 4 -> menu.latticeColor;
            default -> 0xFFFFFF;
        };
    }
}
