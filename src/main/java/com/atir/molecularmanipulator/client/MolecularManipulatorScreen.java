package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class MolecularManipulatorScreen extends ResponsiveContainerScreen<MolecularManipulatorMenu> {
    private static final int HIDDEN_SLOT_POSITION = -10_000;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;

    private EditBox patternSearch;
    private OmniButton previousPage, nextPage;
    private int usedPatterns;
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
    protected void init() {
        super.init();
        menu.setPatternSearchIndexListener(this::acceptPatternSearchIndexChunk);
        patternSearch = OmniUiTheme.textField(style, font, leftPos + 62, topPos + 36, 116, 12,
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
        previousPage = addScreenWidget(OmniUiTheme.button(leftPos + 51, topPos + 18, 16, 16,
                Component.literal("<"), button -> changePage(-1)));
        nextPage = addScreenWidget(OmniUiTheme.button(leftPos + 127, topPos + 18, 16, 16,
                Component.literal(">"), button -> changePage(1)));
        previousPage.setTooltip(Tooltip.create(Component.translatable("gui.molecularmanipulator.page_previous_tooltip")));
        nextPage.setTooltip(Tooltip.create(Component.translatable("gui.molecularmanipulator.page_next_tooltip")));
        updateNavigation();
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
        updateNavigation();
    }

    @Override
    public void removed() {
        menu.setPatternSearchIndexListener(null);
        super.removed();
    }

    private void updateNavigation() {
        previousPage.active = !patternSearchWaiting() && menu.getPage() > 0;
        nextPage.active = !patternSearchWaiting() && menu.getPage() + 1 < menu.getPageCount();
        usedPatterns = menu.occupiedPatternSlots;
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        MachineUiLayout.MOLECULAR_ARRAY.draw(guiGraphics, offsetX, offsetY);
        OmniUiTheme.progress(guiGraphics, offsetX + 15, offsetY + 126, 162, 3,
                usedPatterns / (float) Math.max(1, menu.getPatternSlots().size()), OmniUiTheme.CYAN);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.molecular_manipulator"),
                8, 7, 178, OmniUiTheme.PRIMARY_TEXT);
        drawCenteredFittedString(graphics, patternPageLabel(), 97, 22, 58,
                patternSearchWaiting() ? OmniUiTheme.WARNING : patternSearchHasNoResults() ? OmniUiTheme.ERROR : OmniUiTheme.PRIMARY_TEXT);
        drawRightAlignedFittedString(graphics, Component.literal(usedPatterns + "/" + menu.getPatternSlots().size()),
                178, 22, 32, OmniUiTheme.MUTED_TEXT);
        graphics.drawString(font, Component.translatable("gui.ae2.Patterns"), 12, 39, OmniUiTheme.PRIMARY_TEXT, false);
        graphics.drawString(font, Component.translatable("gui.ae2.ReturnInventory"), 12, 136, OmniUiTheme.PRIMARY_TEXT, false);
        graphics.drawString(font, playerInventoryTitle, 12, 178, OmniUiTheme.PRIMARY_TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        if (x >= 146 && x < 180 && y >= 20 && y < 32 || x >= 15 && x < 177 && y >= 126 && y < 130) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable("gui.molecularmanipulator.pattern_capacity_tooltip")), mouseX, mouseY);
        }
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
