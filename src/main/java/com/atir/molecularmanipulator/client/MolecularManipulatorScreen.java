package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class MolecularManipulatorScreen extends ResponsiveContainerScreen<MolecularManipulatorMenu> {
    private static final int HIDDEN_SLOT_POSITION = -10_000;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;
    private static final String LAYOUT = "seq_manipulator";

    private EditBox patternSearch;
    private OsqUi ui;
    private net.minecraft.client.gui.components.Button previousPageButton;
    private net.minecraft.client.gui.components.Button nextPageButton;
    private final PatternSearchIndexState patternSearchIndex = new PatternSearchIndexState();
    private List<Integer> filteredPatternSlots = List.of();
    private String patternSearchQuery = "";
    private int patternSearchDebounce;
    private boolean patternSearchIndexPending;
    private int requestedPatternRevision = -1;
    private int indexedPatternRevision = -1;
    private int autoPageRevision = -1;

    public MolecularManipulatorScreen(MolecularManipulatorMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        imageWidth = 190;
        imageHeight = 272;
        super.init();
        ui = new OsqUi(LAYOUT);
        menu.setPatternSearchIndexListener(this::acceptPatternSearchIndexChunk);
        layoutInventorySlots();
        patternSearch = ui.field("search", leftPos, topPos, font,
                Component.translatable("gui.molecularmanipulator.pattern_search"));
        patternSearch.setMaxLength(64);
        patternSearch.setValue(patternSearchQuery);
        patternSearch.setResponder(this::patternSearchChanged);
        addScreenWidget(patternSearch);
        if (!patternSearchQuery.isBlank()) {
            patternSearchDebounce = 1;
        }
        layoutPatternPage();
        previousPageButton = addScreenWidget(ui.button("prev", leftPos, topPos,
                () -> Component.literal("<"), () -> changePage(-1)));
        nextPageButton = addScreenWidget(ui.button("next", leftPos, topPos,
                () -> Component.literal(">"), () -> changePage(1)));
        ui.text("gui.molecularmanipulator.page", this::patternPageLabel);
        ui.omit("gui.molecularmanipulator.seq.s019");
        ui.number(16, 160, () -> menu.usedPatternSlots);
        ui.number(35.42f, 160, () -> "/ " + menu.getPatternSlots().size());
        refreshPageButtons();
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
        refreshPageButtons();
        if (patternSearchQuery.isBlank() && autoPageRevision != menu.patternRevision) {
            autoPageRevision = menu.patternRevision;
            autoAdvanceFullPage();
        }
    }

    private void autoAdvanceFullPage() {
        if (menu.getPage() + 1 >= menu.getPageCount()) return;
        var visible = menu.getVisiblePatternSlotIndices();
        if (visible.length != MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE) return;
        for (int sourceSlot : visible) {
            if (sourceSlot < 0 || sourceSlot >= menu.getPatternSlots().size()
                    || !menu.getPatternSlots().get(sourceSlot).hasItem()) return;
        }
        menu.requestPage(menu.getPage() + 1);
        layoutPatternPage();
    }

    @Override
    public void removed() {
        menu.setPatternSearchIndexListener(null);
        super.removed();
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        if (ui != null) ui.background(guiGraphics, font, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        // Vanilla has already translated the pose to leftPos/topPos in renderLabels.
        if (ui != null) ui.foreground(graphics, font);
    }

    private void layoutInventorySlots() {
        var inventory = menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int index = 0; index < inventory.size(); index++) {
            var slot = inventory.get(index);
            slot.x = 15 + index % 9 * 18;
            slot.y = 182 + index / 9 * 18;
        }
        var hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int index = 0; index < hotbar.size(); index++) {
            var slot = hotbar.get(index);
            slot.x = 15 + index * 18;
            slot.y = 240;
        }
    }

    private void refreshPageButtons() {
        if (previousPageButton == null || nextPageButton == null) return;
        boolean waiting = patternSearchWaiting();
        previousPageButton.active = !waiting && menu.getPage() > 0;
        nextPageButton.active = !waiting && menu.getPage() + 1 < menu.getPageCount();
    }

    void changePage(int offset) {
        if (patternSearchQuery.isBlank()) {
            menu.requestPage(menu.getPage() + offset);
        } else {
            showPatternSearchPage(menu.getPage() + offset);
        }
        layoutPatternPage();
    }

    boolean patternSearchWaiting() {
        return !patternSearchQuery.isBlank() && patternSearchIndexPending;
    }

    boolean patternSearchHasNoResults() {
        return !patternSearchQuery.isBlank()
                && menu.patternSearchActive
                && menu.patternSearchResultCount == 0;
    }

    Component patternPageLabel() {
        if (patternSearchWaiting()) {
            return Component.translatable("gui.molecularmanipulator.pattern_search_indexing");
        }
        if (patternSearchHasNoResults()) {
            return Component.translatable("gui.molecularmanipulator.pattern_search_no_results");
        }
        return Component.translatable("gui.molecularmanipulator.page",
                menu.getPage() + 1, menu.getPageCount());
    }

    private void layoutPatternPage() {
        var slots = menu.getPatternSlots();
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            slot.x = HIDDEN_SLOT_POSITION;
            slot.y = HIDDEN_SLOT_POSITION;
        }
        int[] visibleSlots = menu.getVisiblePatternSlotIndices();
        for (int pageIndex = 0; pageIndex < visibleSlots.length; pageIndex++) {
            int sourceSlot = visibleSlots[pageIndex];
            if (sourceSlot >= 0 && sourceSlot < slots.size()) {
                var slot = slots.get(sourceSlot);
                slot.x = 15 + pageIndex % 9 * 18;
                slot.y = 56 + pageIndex / 9 * 18;
            }
        }
    }

    private void patternSearchChanged(String query) {
        patternSearchQuery = query == null ? "" : query.strip();
        patternSearchDebounce = SEARCH_DEBOUNCE_TICKS;
        if (patternSearchQuery.isBlank()) {
            filteredPatternSlots = List.of();
            patternSearchIndexPending = false;
            menu.clearPatternSearch();
            layoutPatternPage();
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
        int pageCount = Math.max(1, (resultCount + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        int first = page * MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE;
        int last = Math.min(resultCount, first + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
        int[] sourceSlots = new int[Math.max(0, last - first)];
        for (int index = first; index < last; index++) {
            sourceSlots[index - first] = filteredPatternSlots.get(index);
        }
        menu.requestPatternSearchPage(page, resultCount, sourceSlots);
        layoutPatternPage();
    }

}
