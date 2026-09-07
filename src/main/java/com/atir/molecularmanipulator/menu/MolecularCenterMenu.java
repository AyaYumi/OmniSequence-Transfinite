package com.atir.molecularmanipulator.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.atir.molecularmanipulator.network.PatternSearchIndexBuilder;
import com.atir.molecularmanipulator.network.PatternSearchIndexChunk;
import com.atir.molecularmanipulator.network.PatternSearchIndexReceiver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class MolecularCenterMenu extends AEBaseMenu implements PatternSearchIndexReceiver {
    private static final String ACTION_SET_PAGE = "set_page";
    private static final String ACTION_PREVIEW = "preview";
    private static final String ACTION_BUILD = "build";
    private static final String ACTION_DISMANTLE = "dismantle";
    private static final String ACTION_ADJUST_VISUAL_COLOR = "adjust_visual_color";
    private static final String ACTION_RESET_VISUAL_COLORS = "reset_visual_colors";
    private static final String ACTION_DECONSTRUCT_MATTER = "deconstruct_matter";
    private static final String ACTION_REWRITE_MATTER = "rewrite_matter";
    private static final String ACTION_SET_DECONSTRUCT_TARGET = "set_deconstruct_target";
    private static final String ACTION_SET_REWRITE_TARGET = "set_rewrite_target";
    private static final String ACTION_CYCLE_REWRITE_OUTPUT = "cycle_rewrite_output";
    private static final String ACTION_UPDATE_STRUCTURE = "update_structure";
    private static final String ACTION_KEEP_LEGACY_STRUCTURE = "keep_legacy_structure";
    private static final String ACTION_REQUEST_PATTERN_SEARCH_INDEX = "request_pattern_search_index";
    private static final String ACTION_SET_PATTERN_SEARCH_PAGE = "set_pattern_search_page";
    private static final String ACTION_SELECT_AUTO_CRAFT_SLOT = "select_auto_craft_slot";
    private static final String ACTION_TOGGLE_AUTO_CRAFT = "toggle_auto_craft";
    private static final String ACTION_SET_AUTO_CRAFT_RESERVE = "set_auto_craft_reserve";
    private static final String ACTION_SET_AUTO_CRAFT_OUTPUT_LIMIT = "set_auto_craft_output_limit";
    public static final int PATTERN_X = 17;
    public static final int PATTERN_Y = 52;
    public static final int PLAYER_X = 17;
    public static final int PLAYER_MAIN_Y = 188;
    public static final int PLAYER_HOTBAR_Y = 246;
    public static final int SEQUENCE_INPUT_X = 232;
    public static final int SEQUENCE_SAMPLE_X = 304;
    public static final int SEQUENCE_OUTPUT_X = 376;
    public static final int SEQUENCE_SLOT_Y = 66;
    public static final int QUANTUM_SLOT_X = 304;
    public static final int QUANTUM_SLOT_Y = 61;
    public static final int SPEED_SLOT_X = 337;
    public static final int SPEED_SLOT_Y = 169;
    public static final int AUTO_CRAFT_PATTERN_X = 233;
    public static final int AUTO_CRAFT_PATTERN_Y = 50;
    public static final SlotSemantic AUTO_CRAFT_PATTERN_SEMANTIC = SlotSemantics.register(
            "molecularmanipulator:AUTO_CRAFT_PATTERN", false, -100);

    public static final MenuType<MolecularCenterMenu> TYPE = MenuTypeBuilder
            .create(MolecularCenterMenu::new, PatternProviderLogicHost.class)
            .buildUnregistered(MolecularManipulator.id("molecular_center"));

    @GuiSync(10)
    public boolean formed;
    @GuiSync(11)
    public int buildProgress;
    @GuiSync(12)
    public int buildTotal;
    @GuiSync(23)
    public int fieldColor = MolecularCenterBlockEntity.DEFAULT_FIELD_COLOR;
    @GuiSync(24)
    public int coreColor = MolecularCenterBlockEntity.DEFAULT_CORE_COLOR;
    @GuiSync(25)
    public int primaryRingColor = MolecularCenterBlockEntity.DEFAULT_PRIMARY_RING_COLOR;
    @GuiSync(26)
    public int secondaryRingColor = MolecularCenterBlockEntity.DEFAULT_SECONDARY_RING_COLOR;
    @GuiSync(27)
    public int latticeColor = MolecularCenterBlockEntity.DEFAULT_LATTICE_COLOR;
    @GuiSync(28)
    public long metalSequence;
    @GuiSync(29)
    public long mineralSequence;
    @GuiSync(30)
    public long crystalSequence;
    @GuiSync(31)
    public long organicSequence;
    @GuiSync(32)
    public long entropy;
    @GuiSync(33)
    public MolecularCenterBlockEntity.QuantumLinkState quantumLinkState =
            MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    @GuiSync(34)
    public long quantumFrequency;
    @GuiSync(35)
    public boolean deconstructEnabled;
    @GuiSync(36)
    public MolecularCenterBlockEntity.MatterJobState deconstructJobState =
            MolecularCenterBlockEntity.MatterJobState.IDLE;
    @GuiSync(37)
    public int deconstructJobProgress;
    @GuiSync(38)
    public long deconstructJobProcessed;
    @GuiSync(39)
    public long deconstructTarget;
    @GuiSync(40)
    public long rewriteTarget;
    @GuiSync(41)
    public int speedCards;
    @GuiSync(42)
    public int matterCycleTicks = 20;
    @GuiSync(43)
    public MolecularCenterBlockEntity.RewriteOutputMode rewriteOutputMode =
            MolecularCenterBlockEntity.RewriteOutputMode.NETWORK;
    @GuiSync(44)
    public boolean rewriteEnabled;
    @GuiSync(45)
    public MolecularCenterBlockEntity.MatterJobState rewriteJobState =
            MolecularCenterBlockEntity.MatterJobState.IDLE;
    @GuiSync(46)
    public int rewriteJobProgress;
    @GuiSync(47)
    public long rewriteJobProcessed;
    @GuiSync(48)
    public boolean legacyStructure;
    @GuiSync(49)
    public boolean legacyStructureUpdateDismissed;
    @GuiSync(50)
    public boolean building;
    @GuiSync(51)
    public boolean dismantling;
    @GuiSync(52)
    public int patternRevision;
    @GuiSync(53)
    public int page;
    @GuiSync(54)
    public int patternSearchResultCount;
    @GuiSync(55)
    public boolean patternSearchActive;
    @GuiSync(56)
    public int matterParallelOperations = 1;
    @GuiSync(57)
    public long entropyCapacity = 1_000_000L;
    @GuiSync(58)
    public long entropyCoolingPerSecond = 25L;
    @GuiSync(59)
    public long deconstructEntropyPerItem;
    @GuiSync(60)
    public long rewriteEntropyPerItem;
    @GuiSync(61)
    public long deconstructCoolingSeconds = -1;
    @GuiSync(62)
    public long rewriteCoolingSeconds = -1;
    @GuiSync(63)
    public int autoCraftSelectedSlot = -1;
    @GuiSync(64)
    public boolean autoCraftEnabled;
    @GuiSync(65)
    public MolecularAutoCrafter.AutoCraftState autoCraftState =
            MolecularAutoCrafter.AutoCraftState.EMPTY;
    @GuiSync(66)
    public long autoCraftOutputLimit;
    @GuiSync(68)
    public int autoCraftInputCount;
    @GuiSync(69)
    public long autoCraftInputReserve0;
    @GuiSync(70)
    public long autoCraftLastCrafts;
    @GuiSync(71)
    public long autoCraftCumulativeCrafts;
    @GuiSync(72)
    public int autoCraftEnabledMask;
    @GuiSync(73)
    public long autoCraftInputReserve1;
    @GuiSync(74)
    public long autoCraftInputReserve2;
    @GuiSync(75)
    public long autoCraftInputReserve3;
    @GuiSync(76)
    public long autoCraftInputReserve4;
    @GuiSync(77)
    public long autoCraftInputReserve5;
    @GuiSync(78)
    public long autoCraftInputReserve6;
    @GuiSync(79)
    public long autoCraftInputReserve7;
    @GuiSync(80)
    public long autoCraftInputReserve8;

    private final MolecularCenterBlockEntity center;
    private final PagedInventory pageInventory;
    private final List<Slot> patternSlots;
    private final List<AppEngSlot> sequenceSlots;
    private final AppEngSlot quantumSlot;
    private final List<AppEngSlot> speedSlots;
    private final List<AppEngSlot> autoCraftPatternSlots;
    private long patternSearchIndexGeneration;
    private Consumer<PatternSearchIndexChunk> patternSearchIndexListener;

    private MolecularCenterMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.center = (MolecularCenterBlockEntity) host.getBlockEntity();
        createPlayerSlots(playerInventory);
        this.sequenceSlots = new java.util.ArrayList<>(3);
        addSequenceSlots();
        this.quantumSlot = addQuantumSlot();
        this.speedSlots = addSpeedSlots();
        this.autoCraftPatternSlots = addAutoCraftPatternSlots();
        this.pageInventory = isClientSide()
                ? PagedInventory.clientView()
                : PagedInventory.serverView(center.getLogic().getFullPatternInventory());
        this.patternSlots = new java.util.ArrayList<>(MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        for (int slot = 0; slot < MolecularCenterBlockEntity.PATTERNS_PER_PAGE; slot++) {
            var added = addSlot(new SupportedPatternSlot(pageInventory, slot), SlotSemantics.ENCODED_PATTERN);
            added.x = PATTERN_X + slot % 9 * 18;
            added.y = PATTERN_Y + slot / 9 * 18;
            patternSlots.add(added);
        }
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::applyPage);
        registerClientAction(ACTION_PREVIEW, this::preview);
        registerClientAction(ACTION_BUILD, this::build);
        registerClientAction(ACTION_DISMANTLE, this::dismantle);
        registerClientAction(ACTION_ADJUST_VISUAL_COLOR, Integer.class, this::adjustVisualColor);
        registerClientAction(ACTION_RESET_VISUAL_COLORS, this::resetVisualColors);
        registerClientAction(ACTION_DECONSTRUCT_MATTER, this::deconstructMatter);
        registerClientAction(ACTION_REWRITE_MATTER, this::rewriteMatter);
        registerClientAction(ACTION_SET_DECONSTRUCT_TARGET, Long.class, this::setDeconstructTarget);
        registerClientAction(ACTION_SET_REWRITE_TARGET, Long.class, this::setRewriteTarget);
        registerClientAction(ACTION_CYCLE_REWRITE_OUTPUT, this::cycleRewriteOutput);
        registerClientAction(ACTION_UPDATE_STRUCTURE, this::updateStructure);
        registerClientAction(ACTION_KEEP_LEGACY_STRUCTURE, this::keepLegacyStructure);
        registerClientAction(ACTION_REQUEST_PATTERN_SEARCH_INDEX, this::sendPatternSearchIndex);
        registerClientAction(ACTION_SET_PATTERN_SEARCH_PAGE, PatternSearchPageRequest.class,
                this::applyPatternSearchPage);
        registerClientAction(ACTION_SELECT_AUTO_CRAFT_SLOT, Integer.class, this::selectAutoCraftSlot);
        registerClientAction(ACTION_TOGGLE_AUTO_CRAFT, Integer.class, this::toggleAutoCraft);
        registerClientAction(ACTION_SET_AUTO_CRAFT_RESERVE, AutoCraftValueRequest.class,
                this::setAutoCraftReserve);
        registerClientAction(ACTION_SET_AUTO_CRAFT_OUTPUT_LIMIT, AutoCraftValueRequest.class,
                this::setAutoCraftOutputLimit);
        applyPage(0);
    }

    private void createPlayerSlots(Inventory inventory) {
        for (int index = 0; index < 36; index++) {
            int x = PLAYER_X + (index % 9) * 18;
            int y = index < 27 ? PLAYER_MAIN_Y + (index / 9) * 18 : PLAYER_HOTBAR_Y;
            int inventoryIndex = index < 27 ? index + 9 : index - 27;
            addSlot(new Slot(inventory, inventoryIndex, x, y),
                    index < 27 ? SlotSemantics.PLAYER_INVENTORY : SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private void addSequenceSlots() {
        var inventory = center.getMatterInventory();
        var input = new FakeSlot(inventory, 0) {
            @Override
            public void set(ItemStack stack) {
                super.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            }

            @Override
            public boolean canSetFilterTo(ItemStack stack) {
                return (stack.isEmpty()
                        || com.atir.molecularmanipulator.sequence.MatterSequenceRegistry
                                .deconstructionOf(stack) != null)
                        && super.canSetFilterTo(stack);
            }
        };
        addSlot(input, SlotSemantics.CONFIG);
        input.x = SEQUENCE_INPUT_X;
        input.y = SEQUENCE_SLOT_Y;
        sequenceSlots.add(input);

        var sample = new AppEngSlot(inventory, 1);
        addSlot(sample, SlotSemantics.CONFIG);
        sample.x = SEQUENCE_SAMPLE_X;
        sample.y = SEQUENCE_SLOT_Y;
        sequenceSlots.add(sample);

        var output = new OutputSlot(inventory, 2, Icon.BACKGROUND_PRIMARY_OUTPUT);
        addSlot(output, SlotSemantics.MACHINE_OUTPUT);
        output.x = SEQUENCE_OUTPUT_X;
        output.y = SEQUENCE_SLOT_Y;
        sequenceSlots.add(output);
    }

    private AppEngSlot addQuantumSlot() {
        var slot = new AppEngSlot(center.getMatterInventory(), 3) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MolecularCenterBlockEntity.isValidQuantumSingularity(stack)
                        && super.mayPlace(stack);
            }
        };
        addSlot(slot, SlotSemantics.CONFIG);
        slot.x = QUANTUM_SLOT_X;
        slot.y = QUANTUM_SLOT_Y;
        return slot;
    }

    private List<AppEngSlot> addSpeedSlots() {
        var result = new java.util.ArrayList<AppEngSlot>(MolecularCenterBlockEntity.MAX_SPEED_CARDS);
        for (int index = 0; index < MolecularCenterBlockEntity.MAX_SPEED_CARDS; index++) {
            var slot = new AppEngSlot(center.getMatterUpgrades(), index);
            addSlot(slot, SlotSemantics.UPGRADE);
            slot.x = SPEED_SLOT_X + index * 18;
            slot.y = SPEED_SLOT_Y;
            result.add(slot);
        }
        return result;
    }

    private List<AppEngSlot> addAutoCraftPatternSlots() {
        var result = new java.util.ArrayList<AppEngSlot>(MolecularAutoCrafter.PATTERN_SLOTS);
        var inventory = center.getAutoCrafter().getPatternInventory();
        for (int index = 0; index < MolecularAutoCrafter.PATTERN_SLOTS; index++) {
            var slot = new SupportedPatternSlot(inventory, index);
            addSlot(slot, AUTO_CRAFT_PATTERN_SEMANTIC);
            slot.x = AUTO_CRAFT_PATTERN_X + index * 18;
            slot.y = AUTO_CRAFT_PATTERN_Y;
            result.add(slot);
        }
        return result;
    }

    public MolecularCenterBlockEntity getCenter() {
        return center;
    }

    public List<Slot> getPatternSlots() {
        return patternSlots;
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

    public List<AppEngSlot> getSequenceSlots() {
        return sequenceSlots;
    }

    public AppEngSlot getQuantumSlot() {
        return quantumSlot;
    }

    public List<AppEngSlot> getSpeedSlots() {
        return speedSlots;
    }

    public List<AppEngSlot> getAutoCraftPatternSlots() {
        return autoCraftPatternSlots;
    }

    public ItemStack getSelectedAutoCraftPatternStack() {
        return autoCraftSelectedSlot < 0 || autoCraftSelectedSlot >= autoCraftPatternSlots.size()
                ? ItemStack.EMPTY
                : autoCraftPatternSlots.get(autoCraftSelectedSlot).getItem();
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        int slots = patternSearchActive
                ? patternSearchResultCount
                : com.atir.molecularmanipulator.config.ModConfig.activePatternSlots();
        return Math.max(1, (slots
                + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1) / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
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

    public void requestSelectAutoCraftSlot(int slot) {
        if (slot < 0 || slot >= MolecularAutoCrafter.PATTERN_SLOTS) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_SELECT_AUTO_CRAFT_SLOT, slot);
        } else {
            selectAutoCraftSlot(slot);
        }
    }

    public void requestToggleAutoCraft(int slot) {
        if (isClientSide() && slot >= 0 && slot < MolecularAutoCrafter.PATTERN_SLOTS) {
            sendClientAction(ACTION_TOGGLE_AUTO_CRAFT, slot);
        }
    }

    public void requestSetAutoCraftInputReserve(int inputIndex, long amount) {
        if (isClientSide() && autoCraftSelectedSlot >= 0 && inputIndex >= 0
                && inputIndex < autoCraftInputCount) {
            sendClientAction(ACTION_SET_AUTO_CRAFT_RESERVE,
                    new AutoCraftValueRequest(autoCraftSelectedSlot, inputIndex,
                            Math.max(0, amount)));
        }
    }

    public long getAutoCraftInputReserve(int inputIndex) {
        return switch (inputIndex) {
            case 0 -> autoCraftInputReserve0;
            case 1 -> autoCraftInputReserve1;
            case 2 -> autoCraftInputReserve2;
            case 3 -> autoCraftInputReserve3;
            case 4 -> autoCraftInputReserve4;
            case 5 -> autoCraftInputReserve5;
            case 6 -> autoCraftInputReserve6;
            case 7 -> autoCraftInputReserve7;
            case 8 -> autoCraftInputReserve8;
            default -> 0;
        };
    }

    public void requestSetAutoCraftOutputLimit(long amount) {
        if (isClientSide() && autoCraftSelectedSlot >= 0) {
            sendClientAction(ACTION_SET_AUTO_CRAFT_OUTPUT_LIMIT,
                    new AutoCraftValueRequest(autoCraftSelectedSlot, -1, Math.max(0, amount)));
        }
    }

    public void clearPatternSearch() {
        applyPage(0);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, 0);
        }
    }

    public void requestPreview() {
        if (isClientSide()) sendClientAction(ACTION_PREVIEW);
    }

    public void requestBuild() {
        if (isClientSide()) sendClientAction(ACTION_BUILD);
    }

    public void requestDismantle() {
        if (isClientSide()) sendClientAction(ACTION_DISMANTLE);
    }

    public void requestAdjustVisualColor(int target, int channel, int delta) {
        if (!isClientSide() || target < 0 || target > 4 || channel < 0 || channel > 2 || delta == 0) {
            return;
        }
        int packed = target | channel << 3 | (delta & 0xFFFF) << 5;
        sendClientAction(ACTION_ADJUST_VISUAL_COLOR, packed);
    }

    public void requestResetVisualColors() {
        if (isClientSide()) sendClientAction(ACTION_RESET_VISUAL_COLORS);
    }

    public void requestDeconstructMatter() {
        if (isClientSide()) sendClientAction(ACTION_DECONSTRUCT_MATTER);
    }

    public void requestRewriteMatter() {
        if (isClientSide()) sendClientAction(ACTION_REWRITE_MATTER);
    }

    public void requestSetDeconstructTarget(long target) {
        if (isClientSide()) sendClientAction(ACTION_SET_DECONSTRUCT_TARGET, target);
    }

    public void requestSetRewriteTarget(long target) {
        if (isClientSide()) sendClientAction(ACTION_SET_REWRITE_TARGET, target);
    }

    public void requestCycleRewriteOutput() {
        if (isClientSide()) sendClientAction(ACTION_CYCLE_REWRITE_OUTPUT);
    }

    public void requestStructureUpdate() {
        if (isClientSide()) sendClientAction(ACTION_UPDATE_STRUCTURE);
    }

    public void requestKeepLegacyStructure() {
        if (isClientSide()) sendClientAction(ACTION_KEEP_LEGACY_STRUCTURE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (isClientSide() || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        var clickedSlot = slots.get(slotIndex);
        if (!isPlayerSideSlot(clickedSlot) || !clickedSlot.mayPickup(player)) {
            return super.quickMoveStack(player, slotIndex);
        }

        var stack = clickedSlot.getItem();
        if (stack.isEmpty() || !isPatternItem(stack)) {
            return super.quickMoveStack(player, slotIndex);
        }

        // Encoded patterns must never fall through into the blueprint/sample slot.
        if (!MolecularCenterLogic.isSupportedPattern(stack)) {
            return ItemStack.EMPTY;
        }

        int transferred = insertSupportedPatterns(stack);
        if (transferred > 0) {
            clickedSlot.remove(transferred);
            clickedSlot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    private int insertSupportedPatterns(ItemStack stack) {
        InternalInventory patternInventory = center.getTerminalPatternInventory();
        int firstSlot = patternSearchActive
                ? 0
                : page * MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
        if (firstSlot >= patternInventory.size()) {
            return 0;
        }

        var remainder = stack.copy();
        for (int slot = firstSlot; slot < patternInventory.size() && !remainder.isEmpty(); slot++) {
            if (!patternInventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            remainder = patternInventory.insertItem(slot, remainder, false);
        }
        return stack.getCount() - remainder.getCount();
    }

    private static boolean isPatternItem(ItemStack stack) {
        return PatternDetailsHelper.isEncodedPattern(stack) || AEItems.BLANK_PATTERN.is(stack);
    }

    private void applyPage(int requestedPage) {
        patternSearchActive = false;
        patternSearchResultCount = 0;
        page = clampPage(requestedPage);
        pageInventory.setSequentialPage(page);
        setPatternSlotsActive(MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        if (!isClientSide()) {
            sendAllDataToRemote();
        }
    }

    private void applyPatternSearchPage(PatternSearchPageRequest request) {
        if (request == null || request.sourceSlots() == null
                || request.sourceSlots().length > MolecularCenterBlockEntity.PATTERNS_PER_PAGE) {
            rejectPatternSearchProjection();
            return;
        }

        int activeSlots = Math.min(
                com.atir.molecularmanipulator.config.ModConfig.activePatternSlots(),
                center.getLogic().getFullPatternInventory().size());
        int resultCount = Math.max(0, Math.min(activeSlots, request.resultCount()));
        int pageCount = Math.max(1, (resultCount + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        int requestedPage = Math.max(0, Math.min(pageCount - 1, request.page()));
        int expectedFirstResult = requestedPage * MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
        int expectedSlots = Math.max(0, Math.min(
                MolecularCenterBlockEntity.PATTERNS_PER_PAGE,
                resultCount - expectedFirstResult));
        if (request.sourceSlots().length != expectedSlots) {
            rejectPatternSearchProjection();
            return;
        }

        int[] sourceSlots = request.sourceSlots().clone();
        boolean[] seen = new boolean[activeSlots];
        for (int sourceSlot : sourceSlots) {
            if (sourceSlot < 0 || sourceSlot >= activeSlots || seen[sourceSlot]) {
                rejectPatternSearchProjection();
                return;
            }
            seen[sourceSlot] = true;
        }

        patternSearchActive = true;
        patternSearchResultCount = resultCount;
        page = requestedPage;
        pageInventory.setMappedSlots(sourceSlots);
        setPatternSlotsActive(sourceSlots.length);
        if (!isClientSide()) {
            sendAllDataToRemote();
        }
    }

    private void rejectPatternSearchProjection() {
        if (isClientSide()) {
            return;
        }
        patternSearchActive = true;
        patternSearchResultCount = 0;
        page = 0;
        pageInventory.setMappedSlots(new int[0]);
        setPatternSlotsActive(0);
        sendAllDataToRemote();
    }

    private void setPatternSlotsActive(int visibleSlots) {
        for (int index = 0; index < patternSlots.size(); index++) {
            if (patternSlots.get(index) instanceof AppEngSlot slot) {
                slot.setActive(index < visibleSlots);
            }
        }
    }

    private void sendPatternSearchIndex() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player
                && center.getLevel() != null) {
            var inventory = center.getLogic().getFullPatternInventory();
            int activeSlots = Math.min(
                    com.atir.molecularmanipulator.config.ModConfig.activePatternSlots(),
                    inventory.size());
            var entries = PatternSearchIndexBuilder.build(
                    inventory,
                    activeSlots,
                    center.getLevel(),
                    MolecularCenterLogic::isSupportedPattern);
            long generation = ++patternSearchIndexGeneration;
            for (var payload : PatternSearchIndexBuilder.chunks(containerId, generation, entries)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private int clampPage(int requestedPage) {
        return Math.max(0, Math.min(getPageCount() - 1, requestedPage));
    }

    private void preview() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.showPreview(player);
            player.closeContainer();
        }
    }

    private void build() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.startBuild(player);
        }
    }

    private void dismantle() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.startDismantle(player);
        }
    }

    private void selectAutoCraftSlot(int slot) {
        if (isClientSide()) {
            return;
        }
        if (slot < 0 || slot >= MolecularAutoCrafter.PATTERN_SLOTS) {
            autoCraftSelectedSlot = -1;
            resetAutoCraftSnapshot();
            sendAllDataToRemote();
            return;
        }
        autoCraftSelectedSlot = slot;
        refreshAutoCraftView();
        sendAllDataToRemote();
    }

    private void resetAutoCraftSnapshot() {
        autoCraftEnabled = false;
        autoCraftState = MolecularAutoCrafter.AutoCraftState.EMPTY;
        autoCraftOutputLimit = 0;
        autoCraftInputCount = 0;
        for (int input = 0; input < MolecularAutoCrafter.MAX_INPUTS; input++) {
            setAutoCraftInputReserve(input, 0);
        }
        autoCraftLastCrafts = 0;
        autoCraftCumulativeCrafts = 0;
    }

    private void toggleAutoCraft(int slot) {
        if (isClientSide() || slot < 0 || slot >= MolecularAutoCrafter.PATTERN_SLOTS) {
            return;
        }
        var autoCrafter = center.getAutoCrafter();
        var view = autoCrafter.getView(slot);
        if (view.state() == MolecularAutoCrafter.AutoCraftState.EMPTY
                || view.state() == MolecularAutoCrafter.AutoCraftState.INVALID_PATTERN) {
            return;
        }
        autoCraftSelectedSlot = slot;
        autoCrafter.setEnabled(slot, !view.enabled());
        refreshAutoCraftView();
        sendAllDataToRemote();
    }

    private void setAutoCraftReserve(AutoCraftValueRequest request) {
        if (isClientSide() || !validAutoCraftRequest(request)
                || request.inputIndex() < 0
                || request.inputIndex() >= MolecularAutoCrafter.MAX_INPUTS
                || request.inputIndex() >= center.getAutoCrafter().getView(request.slot()).inputCount()) {
            return;
        }
        center.getAutoCrafter().setProtection(request.slot(), request.inputIndex(), request.value());
        refreshAutoCraftView();
    }

    private void setAutoCraftOutputLimit(AutoCraftValueRequest request) {
        if (isClientSide() || !validAutoCraftRequest(request)) {
            return;
        }
        center.getAutoCrafter().setOutputLimit(request.slot(), request.value());
        refreshAutoCraftView();
    }

    private boolean validAutoCraftRequest(AutoCraftValueRequest request) {
        return request != null && request.slot() == autoCraftSelectedSlot
                && autoCraftSelectedSlot >= 0 && request.value() >= 0;
    }

    private void refreshAutoCraftView() {
        if (isClientSide()) {
            return;
        }
        int enabledMask = 0;
        for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
            if (center.getAutoCrafter().getView(slot).enabled()) {
                enabledMask |= 1 << slot;
            }
        }
        autoCraftEnabledMask = enabledMask;
        if (autoCraftSelectedSlot < 0) {
            return;
        }
        var view = center.getAutoCrafter().getView(autoCraftSelectedSlot);
        autoCraftEnabled = view.enabled();
        autoCraftState = view.state();
        autoCraftOutputLimit = view.outputLimit();
        autoCraftInputCount = Math.max(0,
                Math.min(MolecularAutoCrafter.MAX_INPUTS, view.inputCount()));
        long[] protections = view.protections();
        for (int input = 0; input < MolecularAutoCrafter.MAX_INPUTS; input++) {
            setAutoCraftInputReserve(input,
                    input < protections.length ? protections[input] : 0);
        }
        autoCraftLastCrafts = view.lastBatch();
        autoCraftCumulativeCrafts = view.totalCrafts();
    }

    private void setAutoCraftInputReserve(int inputIndex, long value) {
        long reserve = Math.max(0, value);
        switch (inputIndex) {
            case 0 -> autoCraftInputReserve0 = reserve;
            case 1 -> autoCraftInputReserve1 = reserve;
            case 2 -> autoCraftInputReserve2 = reserve;
            case 3 -> autoCraftInputReserve3 = reserve;
            case 4 -> autoCraftInputReserve4 = reserve;
            case 5 -> autoCraftInputReserve5 = reserve;
            case 6 -> autoCraftInputReserve6 = reserve;
            case 7 -> autoCraftInputReserve7 = reserve;
            case 8 -> autoCraftInputReserve8 = reserve;
            default -> {
            }
        }
    }

    private void adjustVisualColor(int packed) {
        if (isClientSide()) {
            return;
        }
        int target = packed & 7;
        int channel = packed >> 3 & 3;
        int delta = (short) (packed >>> 5);
        center.adjustVisualColor(target, channel, delta);
    }

    private void resetVisualColors() {
        if (!isClientSide()) {
            center.resetVisualColors();
        }
    }

    private void deconstructMatter() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.toggleDeconstruction(player);
        }
    }

    private void rewriteMatter() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.toggleRewrite(player);
        }
    }

    private void setDeconstructTarget(long target) {
        if (!isClientSide()) {
            center.setDeconstructTarget(target);
        }
    }

    private void setRewriteTarget(long target) {
        if (!isClientSide()) {
            center.setRewriteTarget(target);
        }
    }

    private void cycleRewriteOutput() {
        if (!isClientSide()) {
            center.cycleRewriteOutputMode();
        }
    }

    private void updateStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.startStructureUpdate(player);
        }
    }

    private void keepLegacyStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            center.keepLegacyStructure(player);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            formed = center.isFormed();
            buildProgress = center.getBuildProgress();
            buildTotal = center.getBuildTotal();
            fieldColor = center.getFieldColor();
            coreColor = center.getCoreColor();
            primaryRingColor = center.getPrimaryRingColor();
            secondaryRingColor = center.getSecondaryRingColor();
            latticeColor = center.getLatticeColor();
            metalSequence = center.getMetalSequence();
            mineralSequence = center.getMineralSequence();
            crystalSequence = center.getCrystalSequence();
            organicSequence = center.getOrganicSequence();
            entropy = center.getEntropy();
            quantumLinkState = center.getQuantumLinkState();
            quantumFrequency = center.getQuantumFrequency();
            deconstructEnabled = center.isDeconstructEnabled();
            deconstructJobState = center.getDeconstructJobState();
            deconstructJobProgress = center.getDeconstructJobProgress();
            deconstructJobProcessed = center.getDeconstructJobProcessed();
            deconstructTarget = center.getDeconstructTarget();
            rewriteTarget = center.getRewriteTarget();
            speedCards = center.getInstalledSpeedCards();
            matterCycleTicks = center.getMatterCycleTicks();
            matterParallelOperations = center.getMatterParallelOperations();
            entropyCapacity = center.getMatterEntropyCapacity();
            entropyCoolingPerSecond = center.getMatterEntropyCoolingPerSecond();
            deconstructEntropyPerItem = center.getDeconstructionEntropyPerItem();
            rewriteEntropyPerItem = center.getRewriteEntropyPerItem();
            deconstructCoolingSeconds = center.getDeconstructionCoolingSeconds();
            rewriteCoolingSeconds = center.getRewriteCoolingSeconds();
            rewriteOutputMode = center.getRewriteOutputMode();
            rewriteEnabled = center.isRewriteEnabled();
            rewriteJobState = center.getRewriteJobState();
            rewriteJobProgress = center.getRewriteJobProgress();
            rewriteJobProcessed = center.getRewriteJobProcessed();
            legacyStructure = center.hasLegacyStructure();
            legacyStructureUpdateDismissed = center.isLegacyStructureUpdateDismissed();
            building = center.isBuilding();
            dismantling = center.isDismantling();
            patternRevision = center.getLogic().getPatternRevision();
            refreshAutoCraftView();
        }
        super.broadcastChanges();
    }

    private static final class SupportedPatternSlot extends RestrictedInputSlot {
        private SupportedPatternSlot(InternalInventory inventory, int slot) {
            super(PlacableItemType.PROVIDER_PATTERN, inventory, slot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return MolecularCenterLogic.isSupportedPattern(stack) && super.mayPlace(stack);
        }

        @Override
        protected boolean getCurrentValidationState() {
            var stack = getItem();
            return stack.isEmpty() || MolecularCenterLogic.isSupportedPattern(stack);
        }
    }

    private static final class PagedInventory implements InternalInventory {
        private final InternalInventory source;
        private final boolean clientView;
        private int page;
        private int[] mappedSlots;

        private PagedInventory(InternalInventory source, boolean clientView) {
            this.source = source;
            this.clientView = clientView;
        }

        static PagedInventory clientView() {
            return new PagedInventory(
                    new AppEngInternalInventory(MolecularCenterBlockEntity.PATTERNS_PER_PAGE),
                    true);
        }

        static PagedInventory serverView(InternalInventory source) {
            return new PagedInventory(source, false);
        }

        void setSequentialPage(int page) {
            this.page = page;
            this.mappedSlots = null;
            if (clientView) {
                clearClientView();
            }
        }

        void setMappedSlots(int[] sourceSlots) {
            this.mappedSlots = sourceSlots.clone();
            if (clientView) {
                clearClientView();
            }
        }

        int absoluteSlot(int visibleSlot) {
            if (visibleSlot < 0 || visibleSlot >= MolecularCenterBlockEntity.PATTERNS_PER_PAGE) {
                return -1;
            }
            if (mappedSlots != null) {
                return visibleSlot < mappedSlots.length ? mappedSlots[visibleSlot] : -1;
            }
            return page * MolecularCenterBlockEntity.PATTERNS_PER_PAGE + visibleSlot;
        }

        private void clearClientView() {
            for (int slot = 0; slot < source.size(); slot++) {
                source.setItemDirect(slot, ItemStack.EMPTY);
            }
        }

        private int sourceSlot(int slot) {
            if (clientView) {
                return slot;
            }
            return absoluteSlot(slot);
        }

        @Override
        public int size() {
            return MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            int sourceSlot = sourceSlot(slotIndex);
            return sourceSlot >= 0 && sourceSlot < source.size()
                    ? source.getStackInSlot(sourceSlot)
                    : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            int sourceSlot = sourceSlot(slotIndex);
            if (sourceSlot >= 0 && sourceSlot < source.size()) {
                source.setItemDirect(sourceSlot, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int sourceSlot = sourceSlot(slot);
            return sourceSlot >= 0 && sourceSlot < source.size()
                    && source.isItemValid(sourceSlot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            int sourceSlot = sourceSlot(slot);
            return sourceSlot >= 0 && sourceSlot < source.size()
                    ? source.getSlotLimit(sourceSlot)
                    : 0;
        }
    }

    public record PatternSearchPageRequest(int page, int resultCount, int[] sourceSlots) {
        public PatternSearchPageRequest {
            sourceSlots = sourceSlots == null ? new int[0] : Arrays.copyOf(sourceSlots, sourceSlots.length);
        }
    }

    public record AutoCraftValueRequest(int slot, int inputIndex, long value) {
        public AutoCraftValueRequest {
            value = Math.max(0, value);
        }
    }
}
