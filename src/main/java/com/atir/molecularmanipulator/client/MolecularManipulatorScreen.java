package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class MolecularManipulatorScreen extends RestorableContainerScreen<MolecularManipulatorMenu> {
    private static final int HIDDEN_SLOT_POSITION = -10_000;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;

    private EditBox patternSearch;
    private MolecularManipulatorLdUi modularView;
    private final PatternSearchIndexState patternSearchIndex = new PatternSearchIndexState();
    private List<Integer> filteredPatternSlots = List.of();
    private String patternSearchQuery = "";
    private int patternSearchDebounce;
    private boolean patternSearchIndexPending;
    private int requestedPatternRevision = -1;
    private int indexedPatternRevision = -1;

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
        if (modularView != null) {
            modularView.close();
        }
        super.init();
        menu.setPatternSearchIndexListener(this::acceptPatternSearchIndexChunk);
        patternSearch = AeUiTheme.textField(style, font, leftPos + 62, topPos + 36, 116, 12,
                Component.translatable("gui.molecularmanipulator.pattern_search"),
                Component.translatable("gui.molecularmanipulator.pattern_search_tooltip"));
        patternSearch.setMaxLength(64);
        patternSearch.setValue(patternSearchQuery);
        patternSearch.setResponder(this::patternSearchChanged);
        addScreenWidget(patternSearch);
        if (!patternSearchQuery.isBlank()) {
            patternSearchDebounce = 1;
        }
        layoutPatternPage();
        modularView = new MolecularManipulatorLdUi(this, menu);
        modularView.attach(this);
        addScreenWidget(modularView.widget());
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

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        AeUiTheme.panel(guiGraphics, offsetX + 8, offsetY + 35, offsetX + 186, offsetY + 130);
        AeUiTheme.panel(guiGraphics, offsetX + 8, offsetY + 134, offsetX + 186, offsetY + 170);
        AeUiTheme.panel(guiGraphics, offsetX + 8, offsetY + 176, offsetX + 186, offsetY + 267);
        AeUiTheme.slotGrid(guiGraphics, offsetX + 15, offsetY + 51, 9, 4);
        AeUiTheme.slotGrid(guiGraphics, offsetX + 15, offsetY + 144, 9, 1);
        AeUiTheme.slotGrid(guiGraphics, offsetX + 15, offsetY + 189, 9, 3);
        AeUiTheme.slotGrid(guiGraphics, offsetX + 15, offsetY + 247, 9, 1);
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
                slot.x = 16 + pageIndex % 9 * 18;
                slot.y = 52 + pageIndex / 9 * 18;
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
