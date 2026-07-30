package com.atir.molecularmanipulator.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.network.PatternSearchIndexBuilder;
import com.atir.molecularmanipulator.network.PatternSearchIndexChunk;
import com.atir.molecularmanipulator.network.PatternSearchIndexReceiver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class MolecularManipulatorMenu extends AEBaseMenu implements PatternSearchIndexReceiver {
    private static final String ACTION_SET_PAGE = "set_page";
    private static final String ACTION_REQUEST_PATTERN_SEARCH_INDEX = "request_pattern_search_index";
    private static final String ACTION_SET_PATTERN_SEARCH_PAGE = "set_pattern_search_page";

    public static final MenuType<MolecularManipulatorMenu> TYPE = MenuTypeBuilder
            .create(MolecularManipulatorMenu::new, PatternProviderLogicHost.class)
            .buildUnregistered(MolecularManipulator.id("molecular_manipulator"));

    @GuiSync(0)
    public int patternRevision;
    @GuiSync(1)
    public int page;
    @GuiSync(2)
    public int patternSearchResultCount;
    @GuiSync(3)
    public boolean patternSearchActive;

    private final MolecularManipulatorBlockEntity machine;
    private final InternalInventory patternInventory;
    private final List<AppEngSlot> patternSlots;
    private int[] visiblePatternSlotIndices = new int[0];
    private long patternSearchIndexGeneration;
    private Consumer<PatternSearchIndexChunk> patternSearchIndexListener;

    private MolecularManipulatorMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.machine = (MolecularManipulatorBlockEntity) host.getBlockEntity();
        createPlayerInventorySlots(playerInventory);

        var logic = host.getLogic();
        this.patternInventory = logic.getPatternInv();
        for (int index = 0; index < patternInventory.size(); index++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN,
                    patternInventory, index), SlotSemantics.ENCODED_PATTERN);
        }

        var returnInventory = logic.getReturnInv().createMenuWrapper();
        for (int index = 0; index < PatternProviderReturnInventory.NUMBER_OF_SLOTS; index++) {
            if (index < returnInventory.size()) {
                addSlot(new AppEngSlot(returnInventory, index), SlotSemantics.STORAGE);
            }
        }

        this.patternSlots = getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(AppEngSlot.class::cast)
                .toList();
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::applyPage);
        registerClientAction(ACTION_REQUEST_PATTERN_SEARCH_INDEX, this::sendPatternSearchIndex);
        registerClientAction(ACTION_SET_PATTERN_SEARCH_PAGE, PatternSearchPageRequest.class,
                this::applyPatternSearchPage);
        applyPage(0);
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        int slots = patternSearchActive ? patternSearchResultCount : patternSlots.size();
        return Math.max(1, (slots + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
    }

    public List<AppEngSlot> getPatternSlots() {
        return patternSlots;
    }

    public int[] getVisiblePatternSlotIndices() {
        return visiblePatternSlotIndices;
    }

    public void setPatternSearchIndexListener(Consumer<PatternSearchIndexChunk> listener) {
        this.patternSearchIndexListener = listener;
    }

    @Override
    public void acceptPatternSearchIndexChunk(PatternSearchIndexChunk chunk) {
        if (isClientSide() && patternSearchIndexListener != null) {
            patternSearchIndexListener.accept(chunk);
        }
    }

    public void requestPage(int requestedPage) {
        if (patternSearchActive) {
            return;
        }
        int newPage = clampPage(requestedPage);
        if (newPage == page) {
            return;
        }
        applyPage(newPage);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, newPage);
        }
    }

    public void requestPatternSearchIndex() {
        if (isClientSide()) {
            sendClientAction(ACTION_REQUEST_PATTERN_SEARCH_INDEX);
        }
    }

    public void requestPatternSearchPage(int requestedPage, int resultCount, int[] sourceSlots) {
        var request = new PatternSearchPageRequest(requestedPage, resultCount,
                sourceSlots == null ? new int[0] : sourceSlots.clone());
        applyPatternSearchPage(request);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PATTERN_SEARCH_PAGE, request);
        }
    }

    public void clearPatternSearch() {
        applyPage(0);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, 0);
        }
    }

    private void applyPage(int requestedPage) {
        patternSearchActive = false;
        patternSearchResultCount = 0;
        page = clampPage(requestedPage);
        int firstSlot = page * MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE;
        int lastSlot = Math.min(patternSlots.size(), firstSlot + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
        visiblePatternSlotIndices = new int[Math.max(0, lastSlot - firstSlot)];
        for (int index = 0; index < patternSlots.size(); index++) {
            boolean visible = index >= firstSlot && index < lastSlot;
            patternSlots.get(index).setSlotEnabled(visible);
            if (visible) {
                visiblePatternSlotIndices[index - firstSlot] = index;
            }
        }
    }

    private void applyPatternSearchPage(PatternSearchPageRequest request) {
        if (request == null || request.sourceSlots() == null
                || request.sourceSlots().length > MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE) {
            return;
        }

        int resultCount = Math.max(0, Math.min(patternSlots.size(), request.resultCount()));
        int pageCount = Math.max(1, (resultCount + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
        int requestedPage = Math.max(0, Math.min(pageCount - 1, request.page()));
        int expectedFirstResult = requestedPage * MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE;
        int expectedSlots = Math.max(0, Math.min(
                MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE,
                resultCount - expectedFirstResult));
        if (request.sourceSlots().length != expectedSlots) {
            return;
        }

        int[] sourceSlots = request.sourceSlots().clone();
        boolean[] seen = new boolean[patternSlots.size()];
        for (int sourceSlot : sourceSlots) {
            if (sourceSlot < 0 || sourceSlot >= patternSlots.size() || seen[sourceSlot]) {
                return;
            }
            if (!isClientSide()) {
                ItemStack stack = patternInventory.getStackInSlot(sourceSlot);
                if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                    return;
                }
            }
            seen[sourceSlot] = true;
        }

        patternSearchActive = true;
        patternSearchResultCount = resultCount;
        page = requestedPage;
        visiblePatternSlotIndices = sourceSlots;
        for (int index = 0; index < patternSlots.size(); index++) {
            patternSlots.get(index).setSlotEnabled(seen[index]);
        }
        if (!isClientSide()) {
            sendAllDataToRemote();
        }
    }

    private void sendPatternSearchIndex() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player
                && machine.getLevel() != null) {
            var entries = PatternSearchIndexBuilder.build(
                    patternInventory,
                    patternInventory.size(),
                    machine.getLevel(),
                    PatternDetailsHelper::isEncodedPattern);
            long generation = ++patternSearchIndexGeneration;
            for (var payload : PatternSearchIndexBuilder.chunks(containerId, generation, entries)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            patternRevision = machine.getPatternRevision();
        }
        super.broadcastChanges();
    }

    private int clampPage(int requestedPage) {
        return Math.max(0, Math.min(getPageCount() - 1, requestedPage));
    }

    public record PatternSearchPageRequest(int page, int resultCount, int[] sourceSlots) {
        public PatternSearchPageRequest {
            sourceSlots = sourceSlots == null ? new int[0] : Arrays.copyOf(sourceSlots, sourceSlots.length);
        }
    }
}
