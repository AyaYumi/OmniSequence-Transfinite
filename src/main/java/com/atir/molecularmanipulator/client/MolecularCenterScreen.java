package com.atir.molecularmanipulator.client;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.FakeSlot;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class MolecularCenterScreen extends ResponsiveContainerScreen<MolecularCenterMenu> {
    static final int TAB_MATTER = 0;
    static final int TAB_PIPELINE = 1;
    static final int TAB_QUANTUM = 2;
    static final int TAB_COLORS = 3;
    private static final int PANEL_LEFT = 198;
    private static final int PANEL_RIGHT = 426;
    private static final int LEFT_CONTENT_LEFT = 8;
    private static final int LEFT_CONTENT_RIGHT = 188;
    private static final int DETAIL_CONTENT_LEFT = PANEL_LEFT + 9;
    private static final int DETAIL_CONTENT_RIGHT = PANEL_RIGHT - 7;
    private static final int ACCENT = 0xFFB77BFF;
    private static final int CYAN = 0xFF63D8FF;
    private static final int DISMANTLE_CONFIRM_TICKS = 60;
    private static final int DISMANTLE_CONFIRM_DELAY_TICKS = 6;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;

    private EditBox deconstructTarget;
    private EditBox rewriteTarget;
    private EditBox patternSearch;
    private final PatternSearchIndexState patternSearchIndex = new PatternSearchIndexState();
    private List<Integer> filteredPatternSlots = List.of();
    private String patternSearchQuery = "";
    private int patternSearchDebounce;
    private boolean patternSearchIndexPending;
    private int requestedPatternRevision = -1;
    private int indexedPatternRevision = -1;
    private int detailTab = TAB_MATTER;
    private int dismantleConfirmTicks;
    private MolecularCenterLdUi modularView;

    public MolecularCenterScreen(MolecularCenterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 430;
        imageHeight = 286;
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        cancelDismantleConfirmation();
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

        patternSearch = new EditBox(font, leftPos + 17, topPos + 29, 112, 15,
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
        var targetTooltip = Tooltip.create(
                Component.translatable("gui.molecularmanipulator.matter_target_tooltip"));
        deconstructTarget = new EditBox(font, leftPos + 207, topPos + 153, 56, 18,
                Component.translatable("gui.molecularmanipulator.matter_deconstruct_target"));
        configureTargetField(deconstructTarget, menu.deconstructTarget, targetTooltip);
        addRenderableWidget(deconstructTarget);
        rewriteTarget = new EditBox(font, leftPos + 323, topPos + 153, 56, 18,
                Component.translatable("gui.molecularmanipulator.matter_rewrite_target"));
        configureTargetField(rewriteTarget, menu.rewriteTarget, targetTooltip);
        addRenderableWidget(rewriteTarget);
        selectTab(TAB_MATTER);
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
        detailTab = Math.max(TAB_MATTER, Math.min(TAB_COLORS, tab));
        boolean matterVisible = detailTab == TAB_MATTER;
        deconstructTarget.visible = matterVisible;
        rewriteTarget.visible = matterVisible;
        for (var slot : menu.getSequenceSlots()) {
            slot.setActive(matterVisible);
        }
        for (var slot : menu.getSpeedSlots()) {
            slot.setActive(matterVisible);
        }
        boolean quantumVisible = detailTab == TAB_QUANTUM;
        menu.getQuantumSlot().setActive(quantumVisible);
    }

    @Override
    protected void containerTick() {
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
        syncTargetField(deconstructTarget, menu.deconstructTarget);
        syncTargetField(rewriteTarget, menu.rewriteTarget);
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

    long deconstructTargetInput() {
        return parseTarget(deconstructTarget);
    }

    long rewriteTargetInput() {
        return parseTarget(rewriteTarget);
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
        var pageText = patternSearchIndexPending && !patternSearchQuery.isBlank()
                ? Component.translatable("gui.molecularmanipulator.pattern_search_indexing")
                : !patternSearchQuery.isBlank() && menu.patternSearchActive
                        && menu.patternSearchResultCount == 0
                                ? Component.translatable("gui.molecularmanipulator.pattern_search_no_results")
                                : Component.translatable("gui.molecularmanipulator.page", menu.getPage() + 1,
                                        menu.getPageCount());
        drawRightAlignedFittedString(graphics, pageText, LEFT_CONTENT_RIGHT, 32, 55,
                0xFFB7C3D7);
        var state = menu.formed ? Component.translatable("gui.molecularmanipulator.formed")
                : Component.translatable("gui.molecularmanipulator.incomplete");
        graphics.drawString(font, state, 8, 125, menu.formed ? 0xFF70F2A2 : 0xFFFFB75E, false);
        if (!menu.formed && menu.buildTotal > 0 && menu.buildProgress < menu.buildTotal) {
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
        drawFittedString(graphics,
                Component.translatable("gui.molecularmanipulator.matter_speed",
                        menu.speedCards, menu.matterParallelOperations, menu.matterCycleTicks),
                DETAIL_CONTENT_LEFT, 185,
                MolecularCenterMenu.SPEED_SLOT_X - DETAIL_CONTENT_LEFT - 6,
                0xFFD9C8F5);

        int columnGap = 10;
        int columnWidth = (DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT - columnGap) / 2;
        int rightColumnX = DETAIL_CONTENT_LEFT + columnWidth + columnGap;
        drawSequenceAmount(graphics, "metal", menu.metalSequence,
                DETAIL_CONTENT_LEFT, 205, columnWidth, 0xFFB9C7D5);
        drawSequenceAmount(graphics, "crystal", menu.crystalSequence,
                rightColumnX, 205, columnWidth, 0xFF73CFFF);
        drawSequenceAmount(graphics, "mineral", menu.mineralSequence,
                DETAIL_CONTENT_LEFT, 220, columnWidth, 0xFFB58A62);
        drawSequenceAmount(graphics, "organic", menu.organicSequence,
                rightColumnX, 220, columnWidth, 0xFF73D590);
        if (!menu.legacyStructure) {
            drawFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.matter_entropy_per_item",
                            formatOptionalAmount(menu.deconstructEntropyPerItem),
                            formatOptionalAmount(menu.rewriteEntropyPerItem)),
                    DETAIL_CONTENT_LEFT, 237,
                    DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT, 0xFFFFC4E9);
            drawFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.matter_cooling_estimate",
                            formatCoolingTime(menu.deconstructCoolingSeconds),
                            formatCoolingTime(menu.rewriteCoolingSeconds),
                            formatAmount(menu.entropyCoolingPerSecond)),
                    DETAIL_CONTENT_LEFT, 250,
                    DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT, 0xFFB7C3D7);
            drawKeyValueRow(graphics,
                    Component.translatable("gui.molecularmanipulator.sequence_entropy"),
                    Component.literal(formatAmount(menu.entropy) + " / "
                            + formatAmount(menu.entropyCapacity)),
                    DETAIL_CONTENT_LEFT, DETAIL_CONTENT_RIGHT, 267, 6,
                    0xFFFFA4D8, 0xFFC8B8D8);
        }
    }

    private void drawSequenceAmount(GuiGraphics graphics, String type, long amount,
            int x, int y, int width, int color) {
        drawKeyValueRow(graphics,
                Component.translatable("gui.molecularmanipulator.sequence_" + type),
                Component.literal(formatAmount(amount)),
                x, x + width, y, 4, color, 0xFFE4E7EF);
    }

    private void renderPipelineTab(GuiGraphics graphics) {
        int contentWidth = DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT;
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline"),
                DETAIL_CONTENT_LEFT, 34, contentWidth, CYAN);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline_cache_types",
                menu.pipelineCacheTypes), DETAIL_CONTENT_LEFT, 133, contentWidth, 0xFFD5DBE8);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline_cache_amount",
                formatAmount(menu.pipelineCacheAmount)), DETAIL_CONTENT_LEFT, 147,
                contentWidth, 0xFFD5DBE8);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline_pending",
                formatAmount(menu.pendingOutputAmount)), DETAIL_CONTENT_LEFT, 161,
                contentWidth, 0xFFD5DBE8);
        var pipelineState = menu.pipelineBlocked
                ? Component.translatable("gui.molecularmanipulator.pipeline_blocked")
                : Component.translatable("gui.molecularmanipulator.pipeline_clear");
        drawFittedString(graphics, pipelineState, DETAIL_CONTENT_LEFT, 179, contentWidth,
                menu.pipelineBlocked ? 0xFFFF6D78 : 0xFF70F2A2);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline_recipes",
                menu.activePipelineRecipes), DETAIL_CONTENT_LEFT, 197, contentWidth, 0xFFB7C3D7);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline_crafts",
                formatAmount(menu.activePipelineCrafts)), DETAIL_CONTENT_LEFT, 211,
                contentWidth, 0xFFB7C3D7);
        if (!menu.legacyStructure) {
            drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.pipeline_transfer",
                    formatAmount(menu.lastPipelineTransfer)), DETAIL_CONTENT_LEFT, 225,
                    contentWidth, 0xFFB7C3D7);
        }
    }

    private void renderQuantumTab(GuiGraphics graphics) {
        int contentWidth = DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_LEFT;
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_title"),
                DETAIL_CONTENT_LEFT, 35, contentWidth, 0xFF8EAEFF);
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
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_status",
                Component.translatable("gui.molecularmanipulator.quantum_state."
                        + state.name().toLowerCase(Locale.ROOT))),
                DETAIL_CONTENT_LEFT, 107, contentWidth, stateColor);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_frequency",
                menu.quantumFrequency == 0 ? "—" : formatFrequency(menu.quantumFrequency)),
                DETAIL_CONTENT_LEFT, 125, contentWidth, 0xFFD5DBE8);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_power",
                formatAmount((long) MolecularCenterBlockEntity.QUANTUM_LINK_POWER)),
                DETAIL_CONTENT_LEFT, 143, contentWidth, 0xFFD5DBE8);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_channel"),
                DETAIL_CONTENT_LEFT, 161, contentWidth, 0xFFB7C3D7);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.quantum_remote_ring"),
                DETAIL_CONTENT_LEFT, 179, contentWidth, 0xFFB7C3D7);
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.spawn_protection"),
                DETAIL_CONTENT_LEFT, 205, contentWidth, 0xFF70F2A2);
        if (!menu.legacyStructure) {
            drawFittedString(graphics,
                    Component.translatable("gui.molecularmanipulator.spawn_protection_area"),
                    DETAIL_CONTENT_LEFT, 220, contentWidth, 0xFFB7C3D7);
        }
    }

    private void renderColorsTab(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.visual_colors"),
                210, 34, 0xFFFF83D1, false);
        for (int target = 0; target < 5; target++) {
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

    Component deconstructLabel() {
        return Component.translatable(menu.deconstructEnabled
                ? "gui.molecularmanipulator.sequence_deconstruct_stop"
                : "gui.molecularmanipulator.sequence_deconstruct_start");
    }

    Component rewriteLabel() {
        return Component.translatable(menu.rewriteEnabled
                ? "gui.molecularmanipulator.sequence_rewrite_stop"
                : "gui.molecularmanipulator.sequence_rewrite_start");
    }

    Component rewriteOutputLabel() {
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

    Component primaryRouteLabel() {
        return Component.translatable("gui.molecularmanipulator.route_primary",
                routeName(menu.primaryRoute));
    }

    Component byproductRouteLabel() {
        return Component.translatable("gui.molecularmanipulator.route_byproduct",
                routeName(menu.byproductRoute));
    }

    Component outputPortLabel() {
        return Component.translatable("gui.molecularmanipulator.output_port",
                Component.translatable("gui.molecularmanipulator.output_port."
                        + menu.outputPort.getSerializedName()));
    }

    Component previewLabel() {
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

    private static String formatOptionalAmount(long amount) {
        return amount <= 0 ? "\u2014" : formatAmount(amount);
    }

    private static Component formatCoolingTime(long seconds) {
        if (seconds < 0) {
            return Component.literal("\u2014");
        }
        if (seconds == Long.MAX_VALUE) {
            return Component.translatable("gui.molecularmanipulator.matter_cooling_impossible");
        }
        if (seconds < 60) {
            return Component.translatable(
                    "gui.molecularmanipulator.matter_cooling_seconds", seconds);
        }
        if (seconds < 3600) {
            return Component.literal(String.format(Locale.ROOT, "%d:%02d",
                    seconds / 60, seconds % 60));
        }
        return Component.literal(String.format(Locale.ROOT, "%d:%02d:%02d",
                seconds / 3600, seconds / 60 % 60, seconds % 60));
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
}
