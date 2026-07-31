package com.atir.molecularmanipulator.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class MolecularManipulatorScreen extends AEBaseScreen<MolecularManipulatorMenu> {
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
        patternSearch = new EditBox(font, leftPos + 62, topPos + 36, 116, 14,
                Component.translatable("gui.molecularmanipulator.pattern_search"));
        patternSearch.setMaxLength(64);
        patternSearch.setHint(Component.translatable("gui.molecularmanipulator.pattern_search"));
        patternSearch.setTooltip(Tooltip.create(Component.translatable(
                "gui.molecularmanipulator.pattern_search_tooltip")));
        patternSearch.setValue(patternSearchQuery);
        patternSearch.setResponder(this::patternSearchChanged);
        addRenderableWidget(patternSearch);
        if (!patternSearchQuery.isBlank()) {
            patternSearchDebounce = 1;
        }
        layoutPatternPage();
        modularView = new MolecularManipulatorLdUi(this, menu);
        modularView.attach(this);
        addRenderableWidget(modularView.widget());
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
        guiGraphics.fillGradient(offsetX, offsetY, offsetX + imageWidth, offsetY + imageHeight,
                0xFF171424, 0xFF0C1320);
        guiGraphics.fill(offsetX + 1, offsetY + 1, offsetX + imageWidth - 1, offsetY + 3,
                0xFFB77BFF);
        panel(guiGraphics, offsetX + 8, offsetY + 35, offsetX + 186, offsetY + 130,
                0xE6192233, 0xFF8D6AA8);
        panel(guiGraphics, offsetX + 8, offsetY + 134, offsetX + 186, offsetY + 170,
                0xE6152130, 0xFF6388A5);
        panel(guiGraphics, offsetX + 8, offsetY + 176, offsetX + 186, offsetY + 267,
                0xE6151C2B, 0xFF54536A);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 51, 9, 4, 0xFF72558C);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 144, 9, 1, 0xFF426E87);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 189, 9, 3, 0xFF48485B);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 247, 9, 1, 0xFF48485B);
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

    private static void panel(GuiGraphics guiGraphics, int left, int top, int right, int bottom,
            int fill, int border) {
        guiGraphics.fill(left, top, right, bottom, fill);
        guiGraphics.fill(left, top, right, top + 1, border);
        guiGraphics.fill(left, bottom - 1, right, bottom, border);
        guiGraphics.fill(left, top, left + 1, bottom, border);
        guiGraphics.fill(right - 1, top, right, bottom, border);
    }

    private static void drawSlotGrid(GuiGraphics guiGraphics, int left, int top, int columns, int rows,
            int border) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int x = left + column * 18;
                int y = top + row * 18;
                guiGraphics.fill(x, y, x + 18, y + 18, border);
                guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF15151B);
            }
        }
    }
}
