package com.atir.molecularmanipulator.client;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AECheckbox;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.FakeSlot;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public final class MolecularCenterScreen extends ResponsiveContainerScreen<MolecularCenterMenu> {
    static final int TAB_OVERVIEW = 0;
    static final int TAB_AUTO_CRAFT = 1;
    static final int TAB_COLORS = 2;
    private static final int PANEL_LEFT = 198;
    private static final int PANEL_RIGHT = 426;
    private static final int LEFT_CONTENT_LEFT = 8;
    private static final int LEFT_CONTENT_RIGHT = 188;
    private static final int DETAIL_CONTENT_LEFT = PANEL_LEFT + 9;
    private static final int DETAIL_CONTENT_RIGHT = PANEL_RIGHT - 7;
    private static final int DISMANTLE_CONFIRM_TICKS = 60;
    private static final int DISMANTLE_CONFIRM_DELAY_TICKS = 6;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;

    private EditBox patternSearch;
    private EditBox autoCraftOutputLimit;
    private final EditBox[] autoCraftInputReserves = new EditBox[MolecularAutoCrafter.MAX_INPUTS];
    private final List<CompactCogButton> autoCraftConfigButtons = new java.util.ArrayList<>();
    private final List<AECheckbox> autoCraftToggleButtons = new java.util.ArrayList<>();
    private final PatternSearchIndexState patternSearchIndex = new PatternSearchIndexState();
    private List<Integer> filteredPatternSlots = List.of();
    private String patternSearchQuery = "";
    private int patternSearchDebounce;
    private boolean patternSearchIndexPending;
    private int requestedPatternRevision = -1;
    private int indexedPatternRevision = -1;
    private int detailTab = TAB_OVERVIEW;
    private int dismantleConfirmTicks;
    private int displayedAutoCraftSlot = Integer.MIN_VALUE;
    private MolecularCenterLdUi modularView;

    public MolecularCenterScreen(MolecularCenterMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        cancelDismantleConfirmation();
        int autoCraftPatternSlot = menu.getAutoCraftPatternSlots().indexOf(slot);
        if (detailTab == TAB_AUTO_CRAFT && autoCraftPatternSlot >= 0 && mouseButton == 1) {
            menu.requestSelectAutoCraftSlot(autoCraftPatternSlot);
            return;
        }
        if (slot instanceof FakeSlot) {
            var action = mouseButton == 1
                    ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                    : InventoryAction.PICKUP_OR_SET_DOWN;
            PacketDistributor.sendToServer(new InventoryActionPacket(action, slotId, 0));
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double logicalX = logicalMouseX(mouseX);
        double logicalY = logicalMouseY(mouseY);
        boolean clickedDismantle = button == 0
                && logicalX >= leftPos + 154 && logicalX < leftPos + 212
                && logicalY >= topPos + 4 && logicalY < topPos + 22;
        if (!clickedDismantle) {
            cancelDismantleConfirmation();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void init() {
        if (modularView != null) {
            modularView.close();
        }
        super.init();
        menu.setPatternSearchIndexListener(this::acceptPatternSearchIndexChunk);
        dismantleConfirmTicks = 0;

        patternSearch = AeUiTheme.tallTextField(style, font, leftPos + 17, topPos + 33, 112, 16,
                Component.translatable("gui.molecularmanipulator.pattern_search"),
                Component.translatable("gui.molecularmanipulator.pattern_search_tooltip"));
        patternSearch.setMaxLength(64);
        patternSearch.setValue(patternSearchQuery);
        patternSearch.setResponder(this::patternSearchChanged);
        addScreenWidget(patternSearch);
        if (!patternSearchQuery.isBlank()) {
            patternSearchDebounce = 1;
        }
        autoCraftOutputLimit = AeUiTheme.tallTextField(style, font,
                leftPos + 226, topPos + 90, 141, 16,
                Component.translatable("gui.molecularmanipulator.auto_craft_output_limit"),
                Component.translatable("gui.molecularmanipulator.auto_craft_output_limit_tooltip"));
        configureLongField(autoCraftOutputLimit, menu.autoCraftOutputLimit,
                "gui.molecularmanipulator.auto_craft_output_limit_tooltip");
        addScreenWidget(autoCraftOutputLimit);
        for (int input = 0; input < autoCraftInputReserves.length; input++) {
            int columnX = input % 2 == 0 ? 207 : 313;
            int rowY = 123 + input / 2 * 22;
            var reserve = AeUiTheme.tallTextField(style, font,
                    leftPos + columnX + 18, topPos + rowY, 48, 16,
                    Component.translatable("gui.molecularmanipulator.auto_craft_input_reserve_index",
                            input + 1),
                    Component.translatable("gui.molecularmanipulator.auto_craft_input_reserve_tooltip"));
            configureLongField(reserve, menu.getAutoCraftInputReserve(input),
                    "gui.molecularmanipulator.auto_craft_input_reserve_tooltip");
            autoCraftInputReserves[input] = reserve;
            addScreenWidget(reserve);
        }
        autoCraftConfigButtons.clear();
        autoCraftToggleButtons.clear();
        for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
            int selectedSlot = slot;
            var configButton = new CompactCogButton(
                    leftPos + MolecularCenterMenu.AUTO_CRAFT_PATTERN_X + slot * 18 + 2,
                    topPos + 32,
                    () -> menu.requestSelectAutoCraftSlot(selectedSlot));
            configButton.setTooltip(Tooltip.create(Component.translatable(
                    "gui.molecularmanipulator.auto_craft_select_slot", slot + 1)));
            autoCraftConfigButtons.add(configButton);
            addScreenWidget(configButton);

            var toggleButton = new AECheckbox(
                    leftPos + MolecularCenterMenu.AUTO_CRAFT_PATTERN_X + slot * 18 + 1,
                    topPos + 70,
                    AECheckbox.SIZE,
                    AECheckbox.SIZE,
                    style,
                    Component.empty());
            toggleButton.setRadio(true);
            toggleButton.setChangeListener(
                    () -> menu.requestToggleAutoCraft(selectedSlot));
            autoCraftToggleButtons.add(toggleButton);
            addScreenWidget(toggleButton);
        }
        selectTab(detailTab);
        modularView = new MolecularCenterLdUi(this, menu);
        modularView.attach(this);
        addResponsiveModularWidget(modularView.widget());
    }

    void changePatternPage(int offset) {
        if (patternSearchQuery.isBlank()) {
            menu.requestPage(menu.getPage() + offset);
        } else {
            showPatternSearchPage(menu.getPage() + offset);
        }
    }

    private void patternSearchChanged(String query) {
        patternSearchQuery = query == null ? "" : query.strip();
        patternSearchDebounce = SEARCH_DEBOUNCE_TICKS;
        if (patternSearchQuery.isBlank()) {
            filteredPatternSlots = List.of();
            patternSearchIndexPending = false;
            menu.clearPatternSearch();
        }
    }

    private void applyPatternSearchQuery() {
        if (patternSearchQuery.isBlank()) {
            return;
        }
        if (patternSearchIndex.complete() && indexedPatternRevision == menu.patternRevision) {
            rebuildPatternSearchResults();
        } else {
            requestPatternSearchIndex();
        }
    }

    private void requestPatternSearchIndex() {
        if (patternSearchIndexPending) {
            return;
        }
        patternSearchIndexPending = true;
        requestedPatternRevision = menu.patternRevision;
        menu.requestPatternSearchIndex();
    }

    private void acceptPatternSearchIndexChunk(
            com.atir.molecularmanipulator.network.PatternSearchIndexChunk chunk) {
        if (!patternSearchIndex.accept(chunk) || !patternSearchIndex.complete()) {
            return;
        }
        patternSearchIndexPending = false;
        indexedPatternRevision = requestedPatternRevision;
        if (patternSearchQuery.isBlank()) {
            return;
        }
        if (indexedPatternRevision != menu.patternRevision) {
            requestPatternSearchIndex();
        } else {
            rebuildPatternSearchResults();
        }
    }

    private void rebuildPatternSearchResults() {
        filteredPatternSlots = patternSearchIndex.entries().stream()
                .filter(entry -> ClientSearchMatcher.matchesPattern(entry, patternSearchQuery))
                .map(entry -> entry.sourceSlot())
                .toList();
        showPatternSearchPage(0);
    }

    private void showPatternSearchPage(int requestedPage) {
        int resultCount = filteredPatternSlots.size();
        int pageCount = Math.max(1, (resultCount + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        int first = page * MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
        int last = Math.min(resultCount, first + MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        int[] sourceSlots = new int[Math.max(0, last - first)];
        for (int index = first; index < last; index++) {
            sourceSlots[index - first] = filteredPatternSlots.get(index);
        }
        menu.requestPatternSearchPage(page, resultCount, sourceSlots);
    }

    void selectTab(int tab) {
        detailTab = Math.max(TAB_OVERVIEW, Math.min(TAB_COLORS, tab));
        boolean autoCraftVisible = detailTab == TAB_AUTO_CRAFT;
        boolean autoCraftSelected = menu.autoCraftSelectedSlot >= 0
                && menu.autoCraftState != MolecularAutoCrafter.AutoCraftState.EMPTY;
        autoCraftOutputLimit.visible = autoCraftVisible && autoCraftSelected;
        for (int input = 0; input < autoCraftInputReserves.length; input++) {
            autoCraftInputReserves[input].visible = autoCraftVisible
                    && input < menu.autoCraftInputCount;
        }
        for (var slot : menu.getAutoCraftPatternSlots()) {
            slot.setActive(autoCraftVisible);
        }
        boolean overviewVisible = detailTab == TAB_OVERVIEW;
        menu.getQuantumSlot().setActive(overviewVisible);
        if (menu.getMatrixUploadCoreSlot() != null) {
            menu.getMatrixUploadCoreSlot().setActive(overviewVisible);
        }
        refreshAutoCraftFields();
        refreshAutoCraftIconButtons();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (patternSearchDebounce > 0 && --patternSearchDebounce == 0) {
            applyPatternSearchQuery();
        }
        if (!patternSearchQuery.isBlank()
                && indexedPatternRevision != menu.patternRevision
                && !patternSearchIndexPending
                && patternSearchDebounce == 0) {
            requestPatternSearchIndex();
        }
        if (dismantleConfirmTicks > 0) {
            --dismantleConfirmTicks;
        }
        refreshAutoCraftFields();
        refreshAutoCraftIconButtons();
        if (modularView != null) {
            modularView.tick();
        }
    }

    @Override
    public void removed() {
        menu.setPatternSearchIndexListener(null);
        if (modularView != null) {
            modularView.close();
            modularView = null;
        }
        super.removed();
    }

    void dismantleClicked() {
        if (dismantleConfirmTicks == 0) {
            dismantleConfirmTicks = DISMANTLE_CONFIRM_TICKS;
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
    }

    Component dismantleLabel() {
        return Component.translatable(dismantleConfirmTicks > 0
                ? "gui.molecularmanipulator.dismantle_confirm"
                : "gui.molecularmanipulator.dismantle");
    }

    Component dismantleTooltip() {
        return Component.translatable(dismantleConfirmTicks > 0
                ? "gui.molecularmanipulator.dismantle_confirm_tooltip"
                : "gui.molecularmanipulator.dismantle_tooltip");
    }

    boolean dismantleConfirming() {
        return dismantleConfirmTicks > 0;
    }

    int detailTab() {
        return detailTab;
    }

    boolean patternSearchWaiting() {
        return !patternSearchQuery.isBlank() && patternSearchIndexPending;
    }

    long autoCraftOutputLimitInput() {
        return parseNonNegativeLong(autoCraftOutputLimit);
    }

    long autoCraftInputReserveInput(int inputIndex) {
        return inputIndex < 0 || inputIndex >= autoCraftInputReserves.length
                ? 0
                : parseNonNegativeLong(autoCraftInputReserves[inputIndex]);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailTab == TAB_AUTO_CRAFT
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (autoCraftOutputLimit.isFocused() && autoCraftOutputLimit.active) {
                menu.requestSetAutoCraftOutputLimit(autoCraftOutputLimitInput());
                autoCraftOutputLimit.setFocused(false);
                return true;
            }
            for (int input = 0; input < autoCraftInputReserves.length; input++) {
                var reserve = autoCraftInputReserves[input];
                if (reserve.isFocused() && reserve.active && reserve.visible) {
                    menu.requestSetAutoCraftInputReserve(input,
                            autoCraftInputReserveInput(input));
                    reserve.setFocused(false);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
            float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        AeUiTheme.area(graphics, x + 4, y + 28, x + 192, y + 153);
        AeUiTheme.area(graphics, x + 4, y + 157, x + 192, y + imageHeight - 4);
        AeUiTheme.area(graphics, x + PANEL_LEFT, y + 28, x + PANEL_RIGHT,
                y + imageHeight - (detailTab == TAB_AUTO_CRAFT ? 2 : 4));
        if (menu.legacyStructure && detailTab == TAB_COLORS) {
            AeUiTheme.insetPanel(graphics, x + 204, y + 219, x + 422, y + 257);
            drawPanelBorder(graphics, x + 204, y + 219, x + 422, y + 257, AeUiTheme.WARNING);
        }
        drawSlotGrid(graphics, MolecularCenterMenu.PATTERN_X, MolecularCenterMenu.PATTERN_Y, 9, 4);
        if (detailTab == TAB_AUTO_CRAFT) {
            drawSlotGrid(graphics, MolecularCenterMenu.AUTO_CRAFT_PATTERN_X,
                    MolecularCenterMenu.AUTO_CRAFT_PATTERN_Y,
                    MolecularAutoCrafter.PATTERN_SLOTS, 1);
            for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
                int slotX = leftPos + MolecularCenterMenu.AUTO_CRAFT_PATTERN_X + slot * 18;
                int slotY = topPos + MolecularCenterMenu.AUTO_CRAFT_PATTERN_Y;
                int markerColor = (menu.autoCraftEnabledMask & (1 << slot)) != 0
                        ? AeUiTheme.SUCCESS
                        : AeUiTheme.MUTED_TEXT;
                graphics.fill(slotX, slotY + 18, slotX + 16, slotY + 20, markerColor);
                if (slot == menu.autoCraftSelectedSlot) {
                    drawPanelBorder(graphics, slotX - 1, slotY - 1,
                            slotX + 17, slotY + 17, AeUiTheme.CYAN);
                }
            }
        }
        drawSlotGrid(graphics, MolecularCenterMenu.PLAYER_X, MolecularCenterMenu.PLAYER_MAIN_Y, 9, 3);
        drawSlotGrid(graphics, MolecularCenterMenu.PLAYER_X, MolecularCenterMenu.PLAYER_HOTBAR_Y, 9, 1);
        if (detailTab == TAB_OVERVIEW) {
            drawSlotFrame(graphics, MolecularCenterMenu.QUANTUM_SLOT_X, MolecularCenterMenu.QUANTUM_SLOT_Y,
                    0xFF55799E);
            if (menu.getMatrixUploadCoreSlot() != null) {
                drawSlotFrame(graphics, MolecularCenterMenu.MATRIX_UPLOAD_CORE_SLOT_X,
                        MolecularCenterMenu.MATRIX_UPLOAD_CORE_SLOT_Y, 0xFF8B70C4);
            }
            graphics.fill(x + 207, y + 148, x + 418, y + 149, AeUiTheme.SHADOW);
        }
    }

    private void drawSlotGrid(GuiGraphics graphics, int startX, int startY, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                drawPlainSlotFrame(graphics, startX + column * 18, startY + row * 18);
            }
        }
        AeUiTheme.slotGridOutline(graphics,
                leftPos + startX - 1, topPos + startY - 1, columns, rows);
    }

    private void drawSlotFrame(GuiGraphics graphics, int slotX, int slotY, int borderColor) {
        int x = leftPos + slotX;
        int y = topPos + slotY;
        AeUiTheme.slot(graphics, x - 1, y - 1);
        graphics.fill(x - 1, y - 1, x + 17, y, borderColor);
    }


    private void drawPlainSlotFrame(GuiGraphics graphics, int slotX, int slotY) {
        AeUiTheme.slot(graphics, leftPos + slotX - 1, topPos + slotY - 1);
    }

    private static void drawPanelBorder(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var pageText = patternSearchIndexPending && !patternSearchQuery.isBlank()
                ? Component.translatable("gui.molecularmanipulator.pattern_search_indexing")
                : !patternSearchQuery.isBlank() && menu.patternSearchActive
                        && menu.patternSearchResultCount == 0
                                ? Component.translatable("gui.molecularmanipulator.pattern_search_no_results")
                                : Component.translatable("gui.molecularmanipulator.page", menu.getPage() + 1,
                                        menu.getPageCount());
        drawRightAlignedFittedString(graphics, pageText, LEFT_CONTENT_RIGHT, 36, 55,
                AeUiTheme.MUTED_TEXT);
        graphics.drawString(font, playerInventoryTitle, 17, 174, AeUiTheme.PRIMARY_TEXT, false);

        switch (detailTab) {
            case TAB_OVERVIEW -> renderOverview(graphics);
            case TAB_AUTO_CRAFT -> renderAutoCraftTab(graphics);
            case TAB_COLORS -> renderColorsTab(graphics);
        }
        if (menu.legacyStructure && detailTab == TAB_COLORS) {
            drawFittedString(graphics, Component.translatable(
                    "gui.molecularmanipulator.structure_update_projection_warning"),
                    210, 222, 204, AeUiTheme.WARNING);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        int width = DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT;
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.overview_title"),
                DETAIL_CONTENT_LEFT, 36, width, AeUiTheme.ACCENT);

        Component structure = menu.legacyStructure
                ? Component.translatable("gui.molecularmanipulator.structure_legacy_139")
                : Component.translatable(menu.formed
                        ? "gui.molecularmanipulator.formed"
                        : "gui.molecularmanipulator.incomplete");
        drawOverviewRow(graphics, "overview_structure", structure, 50,
                menu.formed && !menu.legacyStructure ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        drawOverviewRow(graphics, "overview_network",
                Component.translatable(menu.operational
                        ? "gui.molecularmanipulator.overview_online"
                        : "gui.molecularmanipulator.overview_offline"),
                67, menu.operational ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        drawOverviewRow(graphics, "overview_patterns",
                Component.literal(formatAmount(menu.usedPatternSlots) + " / "
                        + formatAmount(menu.patternCapacity)), 84, AeUiTheme.PRIMARY_TEXT);
        drawOverviewRow(graphics, "overview_auto_craft",
                Component.literal(Integer.bitCount(menu.autoCraftEnabledMask) + " / "
                        + MolecularAutoCrafter.PATTERN_SLOTS), 101, AeUiTheme.PRIMARY_TEXT);
        drawOverviewRow(graphics, "overview_pipeline",
                menu.activePipelineRecipes > 0
                        ? Component.translatable("gui.molecularmanipulator.overview_pipeline_active",
                                menu.activePipelineRecipes, formatAmount(menu.activePipelineCrafts))
                        : Component.translatable("gui.molecularmanipulator.overview_pipeline_idle"),
                118, menu.activePipelineRecipes > 0 ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
        drawOverviewRow(graphics, "overview_pending",
                menu.pipelineBlocked
                        ? Component.translatable("gui.molecularmanipulator.overview_output_blocked",
                                formatAmount(menu.pendingOutputAmount))
                        : Component.literal(formatAmount(menu.pendingOutputAmount)), 135,
                menu.pipelineBlocked ? AeUiTheme.WARNING : AeUiTheme.PRIMARY_TEXT);

        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.quantum_title"),
                DETAIL_CONTENT_LEFT, 155, width, AeUiTheme.CYAN);
        var state = menu.quantumLinkState;
        int stateColor = switch (state) {
            case CONNECTED -> AeUiTheme.SUCCESS;
            case CONNECTED_BUILD_ONLY -> AeUiTheme.CYAN;
            case EMPTY, SEARCHING -> AeUiTheme.MUTED_TEXT;
            case REMOTE_MISSING, REMOTE_OFFLINE, STRUCTURE_INCOMPLETE -> AeUiTheme.WARNING;
            default -> AeUiTheme.ERROR;
        };
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_status",
                Component.translatable("gui.molecularmanipulator.quantum_state."
                        + state.name().toLowerCase(Locale.ROOT))),
                DETAIL_CONTENT_LEFT, 171, width, stateColor);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_frequency",
                menu.quantumFrequency == 0 ? "-" : formatFrequency(menu.quantumFrequency)),
                DETAIL_CONTENT_LEFT, 185, width, AeUiTheme.PRIMARY_TEXT);
        drawCenteredFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.quantum_singularity"),
                MolecularCenterMenu.QUANTUM_SLOT_X + 8, 198, 90, AeUiTheme.PRIMARY_TEXT);
        if (menu.getMatrixUploadCoreSlot() != null) {
            drawCenteredFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.quantum_upload_core"),
                    MolecularCenterMenu.MATRIX_UPLOAD_CORE_SLOT_X + 8, 198, 84, AeUiTheme.ACCENT);
        }
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.overview_quantum_requirements",
                        formatAmount((long) MolecularCenterBlockEntity.QUANTUM_LINK_POWER)),
                DETAIL_CONTENT_LEFT, 230, width, AeUiTheme.MUTED_TEXT);
        if (menu.getMatrixUploadCoreSlot() != null) {
            drawFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.quantum_upload_core_hint"),
                    DETAIL_CONTENT_LEFT, 245, width, AeUiTheme.MUTED_TEXT);
        }
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.spawn_protection"),
                DETAIL_CONTENT_LEFT, menu.getMatrixUploadCoreSlot() == null ? 245 : 260,
                width, AeUiTheme.SUCCESS);
        if (menu.getMatrixUploadCoreSlot() == null && !menu.legacyStructure) {
            drawFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.spawn_protection_area"),
                    DETAIL_CONTENT_LEFT, 260, width, AeUiTheme.MUTED_TEXT);
        }
    }

    private void drawOverviewRow(GuiGraphics graphics, String key, Component value, int y, int valueColor) {
        drawKeyValueRow(graphics,
                Component.translatable("gui.molecularmanipulator." + key), value,
                DETAIL_CONTENT_LEFT, DETAIL_CONTENT_RIGHT, y, 6,
                AeUiTheme.MUTED_TEXT, valueColor);
    }


    private void renderAutoCraftTab(GuiGraphics graphics) {
        int contentWidth = DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT;
        if (menu.autoCraftSelectedSlot < 0) {
            drawFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.auto_craft_select_hint"),
                    DETAIL_CONTENT_LEFT, 92, contentWidth, AeUiTheme.MUTED_TEXT);
            return;
        }

        IPatternDetails details = selectedAutoCraftPattern();
        GenericStack output = details == null ? null : details.getPrimaryOutput();
        Component outputName = output == null
                ? Component.translatable("gui.molecularmanipulator.auto_craft_invalid_pattern")
                : output.what().getDisplayName();
        if (output != null) {
            AEStackIcon.draw(graphics, output, DETAIL_CONTENT_LEFT, 90);
        }
        autoCraftOutputLimit.setTooltip(Tooltip.create(Component.translatable(
                "gui.molecularmanipulator.auto_craft_output_limit_for_tooltip", outputName)));

        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.auto_craft_materials"),
                DETAIL_CONTENT_LEFT, 111, contentWidth, AeUiTheme.ACCENT);

        for (int inputIndex = 0; inputIndex < menu.autoCraftInputCount
                && inputIndex < autoCraftInputReserves.length; inputIndex++) {
            GenericStack input = selectedAutoCraftInput(details, inputIndex);
            if (input == null) {
                continue;
            }
            int columnX = inputIndex % 2 == 0 ? DETAIL_CONTENT_LEFT : 313;
            int rowY = 123 + inputIndex / 2 * 22;
            AEStackIcon.draw(graphics, input, columnX, rowY);
            autoCraftInputReserves[inputIndex].setTooltip(Tooltip.create(Component.translatable(
                    "gui.molecularmanipulator.auto_craft_input_reserve_for_tooltip",
                    inputIndex + 1, input.what().getDisplayName())));
        }

    }

    private void renderColorsTab(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.visual_colors"),
                210, 31, AeUiTheme.ACCENT, false);
        for (int target = 0; target < 5; target++) {
            int color = visualColor(target);
            int y = 43 + target * 32;
            graphics.drawString(font,
                    Component.translatable("gui.molecularmanipulator.visual_color." + target),
                    210, y, AeUiTheme.PRIMARY_TEXT, false);
            graphics.drawString(font, String.format(Locale.ROOT, "#%06X", color),
                    266, y, color, false);
            graphics.fill(308, y + 2, 316, y + 10, 0xFF000000 | color);
        }
    }

    private void configureLongField(EditBox field, long value, String tooltipKey) {
        field.setMaxLength(19);
        field.setFilter(MolecularCenterScreen::isTargetText);
        field.setValue(Long.toString(Math.max(0, value)));
        field.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    private void refreshAutoCraftFields() {
        if (autoCraftOutputLimit == null || autoCraftInputReserves[0] == null) {
            return;
        }
        boolean autoCraftVisible = detailTab == TAB_AUTO_CRAFT;
        boolean selected = menu.autoCraftSelectedSlot >= 0
                && menu.autoCraftState != MolecularAutoCrafter.AutoCraftState.EMPTY;
        autoCraftOutputLimit.visible = autoCraftVisible && selected;
        autoCraftOutputLimit.active = selected;

        boolean selectedSlotChanged = displayedAutoCraftSlot != menu.autoCraftSelectedSlot;
        if (selectedSlotChanged) {
            displayedAutoCraftSlot = menu.autoCraftSelectedSlot;
            autoCraftOutputLimit.setFocused(false);
            autoCraftOutputLimit.setValue(Long.toString(Math.max(0, menu.autoCraftOutputLimit)));
        }
        syncLongField(autoCraftOutputLimit, menu.autoCraftOutputLimit);
        for (int input = 0; input < autoCraftInputReserves.length; input++) {
            var reserve = autoCraftInputReserves[input];
            boolean inputVisible = autoCraftVisible && selected
                    && input < menu.autoCraftInputCount;
            reserve.visible = inputVisible;
            reserve.active = selected && input < menu.autoCraftInputCount;
            long syncedReserve = menu.getAutoCraftInputReserve(input);
            if (selectedSlotChanged) {
                reserve.setFocused(false);
                reserve.setValue(Long.toString(Math.max(0, syncedReserve)));
            } else {
                syncLongField(reserve, syncedReserve);
            }
        }
    }

    private void refreshAutoCraftIconButtons() {
        if (autoCraftConfigButtons.size() != MolecularAutoCrafter.PATTERN_SLOTS
                || autoCraftToggleButtons.size() != MolecularAutoCrafter.PATTERN_SLOTS) {
            return;
        }
        boolean visible = detailTab == TAB_AUTO_CRAFT;
        for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
            var config = autoCraftConfigButtons.get(slot);
            config.visible = visible;
            config.active = visible;
            config.setSelected(visible && slot == menu.autoCraftSelectedSlot);

            boolean occupied = !menu.getAutoCraftPatternSlots().get(slot).getItem().isEmpty();
            boolean enabled = (menu.autoCraftEnabledMask & (1 << slot)) != 0;
            var toggle = autoCraftToggleButtons.get(slot);
            toggle.visible = visible;
            toggle.active = visible && occupied;
            toggle.setSelected(enabled);
            toggle.setTooltip(Tooltip.create(Component.translatable(
                    enabled
                            ? "gui.molecularmanipulator.auto_craft_stop_slot"
                            : "gui.molecularmanipulator.auto_craft_start_slot",
                    slot + 1)));
        }
    }

    private IPatternDetails selectedAutoCraftPattern() {
        ItemStack pattern = menu.getSelectedAutoCraftPatternStack();
        if (pattern.isEmpty() || minecraft == null || minecraft.level == null) {
            return null;
        }
        try {
            return PatternDetailsHelper.decodePattern(pattern, minecraft.level);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static GenericStack selectedAutoCraftInput(IPatternDetails details, int inputIndex) {
        if (details == null || inputIndex < 0 || inputIndex >= details.getInputs().length) {
            return null;
        }
        var input = details.getInputs()[inputIndex];
        if (input == null || input.getPossibleInputs().length == 0) {
            return null;
        }
        return input.getPossibleInputs()[0];
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

    private static long parseNonNegativeLong(EditBox field) {
        if (field.getValue().isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(field.getValue()));
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static void syncLongField(EditBox field, long value) {
        if (!field.isFocused() && parseNonNegativeLong(field) != value) {
            field.setValue(Long.toString(Math.max(0, value)));
        }
    }

    Component previewLabel() {
        return Component.translatable(MolecularCenterGhostPreview.isShowing(menu.getCenter())
                ? "gui.molecularmanipulator.preview_hide"
                : "gui.molecularmanipulator.preview");
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

    int visualColor(int target) {
        return switch (target) {
            case 0 -> menu.fieldColor;
            case 1 -> menu.coreColor;
            case 2 -> menu.primaryRingColor;
            case 3 -> menu.secondaryRingColor;
            case 4 -> menu.latticeColor;
            default -> 0xFFFFFF;
        };
    }

    private static final class CompactCogButton extends AbstractButton {
        private static final int BUTTON_SIZE = 14;
        private static final int ICON_SIZE = 12;

        private final Runnable action;
        private boolean selected;

        private CompactCogButton(int x, int y, Runnable action) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                float partialTick) {
            Icon background = isHovered()
                    ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                    : selected || isFocused()
                            ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                            : Icon.TOOLBAR_BUTTON_BACKGROUND;
            float opacity = active ? 1.0F : 0.5F;
            background.getBlitter()
                    .dest(getX(), getY(), BUTTON_SIZE, BUTTON_SIZE)
                    .opacity(opacity)
                    .zOffset(2)
                    .blit(graphics);
            Icon.COG.getBlitter()
                    .dest(getX() + 1, getY() + 1, ICON_SIZE, ICON_SIZE)
                    .opacity(opacity)
                    .zOffset(3)
                    .blit(graphics);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
