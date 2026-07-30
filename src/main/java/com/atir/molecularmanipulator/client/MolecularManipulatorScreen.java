package com.atir.molecularmanipulator.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class MolecularManipulatorScreen extends AEBaseScreen<MolecularManipulatorMenu> {
    private static final int HIDDEN_SLOT_POSITION = -10_000;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;

    private Button previousPage;
    private Button nextPage;
    private EditBox patternSearch;
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
        setTextContent(TEXT_ID_DIALOG_TITLE,
                Component.translatable("gui.molecularmanipulator.molecular_manipulator"));
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        menu.setPatternSearchIndexListener(this::acceptPatternSearchIndexChunk);
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(leftPos + 51, topPos + 18, 16, 16)
                .build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(leftPos + 127, topPos + 18, 16, 16)
                .build());
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
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        boolean waiting = !patternSearchQuery.isBlank() && patternSearchIndexPending;
        previousPage.active = !waiting && menu.getPage() > 0;
        nextPage.active = !waiting && menu.getPage() + 1 < menu.getPageCount();
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
    }

    @Override
    public void removed() {
        menu.setPatternSearchIndexListener(null);
        super.removed();
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        var pageText = patternSearchIndexPending && !patternSearchQuery.isBlank()
                ? Component.translatable("gui.molecularmanipulator.pattern_search_indexing")
                : !patternSearchQuery.isBlank() && menu.patternSearchActive
                        && menu.patternSearchResultCount == 0
                                ? Component.translatable("gui.molecularmanipulator.pattern_search_no_results")
                                : Component.translatable("gui.molecularmanipulator.page", menu.getPage() + 1,
                                        menu.getPageCount());
        guiGraphics.drawCenteredString(font, pageText, 97, 22, 0x403748);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        panel(guiGraphics, offsetX + 8, offsetY + 35, offsetX + 186, offsetY + 130,
                0xFFEDE7F2, 0xFF8D6AA8);
        panel(guiGraphics, offsetX + 8, offsetY + 134, offsetX + 186, offsetY + 170,
                0xFFE5EDF4, 0xFF6388A5);
        panel(guiGraphics, offsetX + 8, offsetY + 176, offsetX + 186, offsetY + 267,
                0xFFE9E9EC, 0xFF777782);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 51, 9, 4, 0xFF7B648C);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 144, 9, 1, 0xFF55758D);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 189, 9, 3, 0xFF676771);
        drawSlotGrid(guiGraphics, offsetX + 15, offsetY + 247, 9, 1, 0xFF676771);
    }

    private void changePage(int offset) {
        if (patternSearchQuery.isBlank()) {
            menu.requestPage(menu.getPage() + offset);
        } else {
            showPatternSearchPage(menu.getPage() + offset);
        }
        layoutPatternPage();
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
