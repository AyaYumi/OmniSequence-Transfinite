package com.atir.molecularmanipulator.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKey;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public final class MolecularCenterMenu extends AEBaseMenu {
    private static final String ACTION_SET_PAGE = "set_page";
    private static final String ACTION_PREVIEW = "preview";
    private static final String ACTION_BUILD = "build";
    private static final String ACTION_DISMANTLE = "dismantle";
    private static final String ACTION_CYCLE_PRIMARY_ROUTE = "cycle_primary_route";
    private static final String ACTION_CYCLE_BYPRODUCT_ROUTE = "cycle_byproduct_route";
    private static final String ACTION_CYCLE_OUTPUT_PORT = "cycle_output_port";
    private static final String ACTION_ADJUST_VISUAL_COLOR = "adjust_visual_color";
    private static final String ACTION_RESET_VISUAL_COLORS = "reset_visual_colors";
    private static final String ACTION_DECONSTRUCT_MATTER = "deconstruct_matter";
    private static final String ACTION_REWRITE_MATTER = "rewrite_matter";
    private static final String ACTION_SET_DECONSTRUCT_TARGET = "set_deconstruct_target";
    private static final String ACTION_SET_REWRITE_TARGET = "set_rewrite_target";
    private static final String ACTION_CYCLE_REWRITE_OUTPUT = "cycle_rewrite_output";
    private static final String ACTION_UPDATE_STRUCTURE = "update_structure";
    private static final String ACTION_KEEP_LEGACY_STRUCTURE = "keep_legacy_structure";
    private static final String ACTION_REQUEST_PATTERN_INDEX = "request_pattern_index";
    private static final String ACTION_SET_PATTERN_PROJECTION = "set_pattern_projection";
    private static final int MAX_SEARCH_KEYS_PER_SIDE = 16;
    public static final int PATTERN_X = 17;
    public static final int PATTERN_Y = 46;
    public static final int PLAYER_X = 17;
    public static final int PLAYER_MAIN_Y = 152;
    public static final int PLAYER_HOTBAR_Y = 210;
    public static final int SEQUENCE_INPUT_X = 222;
    public static final int SEQUENCE_SAMPLE_X = 294;
    public static final int SEQUENCE_OUTPUT_X = 366;
    public static final int SEQUENCE_SLOT_Y = 78;
    public static final int QUANTUM_SLOT_X = 294;
    public static final int QUANTUM_SLOT_Y = 67;
    public static final int SPEED_SLOT_X = 337;
    public static final int SPEED_SLOT_Y = 181;

    public static final MenuType<MolecularCenterMenu> TYPE = ForgeMenuTypeFactory.create(
            MolecularManipulator.id("molecular_center"),
            MolecularCenterMenu::new,
            PatternProviderLogicHost.class);

    @GuiSync(10)
    public boolean formed;
    @GuiSync(11)
    public int buildProgress;
    @GuiSync(12)
    public int buildTotal;
    @GuiSync(13)
    public MolecularCenterBlockEntity.PipelineRoute primaryRoute =
            MolecularCenterBlockEntity.PipelineRoute.NETWORK;
    @GuiSync(14)
    public MolecularCenterBlockEntity.PipelineRoute byproductRoute =
            MolecularCenterBlockEntity.PipelineRoute.NETWORK;
    @GuiSync(15)
    public long pipelineCacheAmount;
    @GuiSync(16)
    public int pipelineCacheTypes;
    @GuiSync(17)
    public long pendingOutputAmount;
    @GuiSync(18)
    public boolean pipelineBlocked;
    @GuiSync(19)
    public int activePipelineRecipes;
    @GuiSync(20)
    public long activePipelineCrafts;
    @GuiSync(21)
    public long lastPipelineTransfer;
    @GuiSync(22)
    public MolecularCenterBlockEntity.PipelinePort outputPort =
            MolecularCenterBlockEntity.PipelinePort.FRONT;
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
    public int patternInventoryRevision;

    private final MolecularCenterBlockEntity center;
    private final PagedInventory pageInventory;
    private final List<AppEngSlot> patternSlots;
    private final List<AppEngSlot> sequenceSlots;
    private final AppEngSlot quantumSlot;
    private final List<AppEngSlot> speedSlots;
    private final TreeMap<Integer, LinkedHashSet<AEKey>> clientPatternSearchIndex = new TreeMap<>();
    private int page;
    private boolean filteredPatternView;
    private int filteredPatternCount;
    private String clientPatternSearchQuery = "";
    private int clientPatternSearchRevision = -1;
    private int clientPatternSearchGeneration;
    private boolean clientPatternSearchComplete;
    private boolean clientPatternSearchRequested;
    private int serverPatternSearchRevision = -1;
    private int serverInvalidatedPatternSearchRevision = -1;
    private List<PatternSearchIndexPayload.Entry> serverPatternSearchIndex = List.of();
    private Set<Integer> serverSearchablePatternSlots = Set.of();

    private MolecularCenterMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.center = (MolecularCenterBlockEntity) host.getBlockEntity();
        createPlayerSlots(playerInventory);
        this.sequenceSlots = new java.util.ArrayList<>(3);
        addSequenceSlots();
        this.quantumSlot = addQuantumSlot();
        this.speedSlots = addSpeedSlots();
        InternalInventory patternSource = isClientSide()
                ? new AppEngInternalInventory(MolecularCenterBlockEntity.PATTERNS_PER_PAGE)
                : center.getLogic().getFullPatternInventory();
        this.pageInventory = new PagedInventory(patternSource, isClientSide());
        this.patternSlots = new java.util.ArrayList<>(MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        for (int slot = 0; slot < MolecularCenterBlockEntity.PATTERNS_PER_PAGE; slot++) {
            var patternSlot = new SupportedPatternSlot(pageInventory, slot);
            addSlot(patternSlot, SlotSemantics.ENCODED_PATTERN);
            patternSlot.x = PATTERN_X + slot % 9 * 18;
            patternSlot.y = PATTERN_Y + slot / 9 * 18;
            patternSlots.add(patternSlot);
        }
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::applyPage);
        registerClientAction(ACTION_REQUEST_PATTERN_INDEX, this::sendPatternSearchIndex);
        registerClientAction(
                ACTION_SET_PATTERN_PROJECTION,
                PatternPageRequest.class,
                this::applyPatternProjection);
        registerClientAction(ACTION_PREVIEW, this::preview);
        registerClientAction(ACTION_BUILD, this::build);
        registerClientAction(ACTION_DISMANTLE, this::dismantle);
        registerClientAction(ACTION_CYCLE_PRIMARY_ROUTE, this::cyclePrimaryRoute);
        registerClientAction(ACTION_CYCLE_BYPRODUCT_ROUTE, this::cycleByproductRoute);
        registerClientAction(ACTION_CYCLE_OUTPUT_PORT, this::cycleOutputPort);
        registerClientAction(ACTION_ADJUST_VISUAL_COLOR, Integer.class, this::adjustVisualColor);
        registerClientAction(ACTION_RESET_VISUAL_COLORS, this::resetVisualColors);
        registerClientAction(ACTION_DECONSTRUCT_MATTER, this::deconstructMatter);
        registerClientAction(ACTION_REWRITE_MATTER, this::rewriteMatter);
        registerClientAction(ACTION_SET_DECONSTRUCT_TARGET, Long.class, this::setDeconstructTarget);
        registerClientAction(ACTION_SET_REWRITE_TARGET, Long.class, this::setRewriteTarget);
        registerClientAction(ACTION_CYCLE_REWRITE_OUTPUT, this::cycleRewriteOutput);
        registerClientAction(ACTION_UPDATE_STRUCTURE, this::updateStructure);
        registerClientAction(ACTION_KEEP_LEGACY_STRUCTURE, this::keepLegacyStructure);
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

    public MolecularCenterBlockEntity getCenter() {
        return center;
    }

    public List<AppEngSlot> getPatternSlots() {
        return patternSlots;
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

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        if (filteredPatternView) {
            return Math.max(1, (filteredPatternCount + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1)
                    / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        }
        return directPageCount();
    }

    public void requestPage(int requestedPage) {
        int newPage = clampDirectPage(requestedPage);
        boolean needsServerUpdate = filteredPatternView || newPage != page;
        if (!needsServerUpdate) {
            return;
        }
        applyPage(newPage);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, newPage);
        }
    }

    public void requestPatternSearchIndex() {
        if (!isClientSide() || clientPatternSearchRequested) {
            return;
        }
        clientPatternSearchRequested = true;
        sendClientAction(ACTION_REQUEST_PATTERN_INDEX);
    }

    public void invalidateClientPatternSearchIndex(int revision) {
        if (!isClientSide() || clientPatternSearchRevision == revision) {
            return;
        }
        clientPatternSearchIndex.clear();
        clientPatternSearchRevision = -1;
        clientPatternSearchComplete = false;
        clientPatternSearchRequested = false;
        if (!clientPatternSearchQuery.isBlank()) {
            filteredPatternView = true;
            filteredPatternCount = 0;
            page = 0;
            pageInventory.clearClientView();
            setPatternSlotActivity(0);
        }
        clientPatternSearchGeneration++;
    }

    public void setClientPatternSearchQuery(String query) {
        clientPatternSearchQuery = query == null ? "" : query;
    }

    public String getClientPatternSearchQuery() {
        return clientPatternSearchQuery;
    }

    public int getClientPatternSearchRevision() {
        return clientPatternSearchRevision;
    }

    public int getClientPatternSearchGeneration() {
        return clientPatternSearchGeneration;
    }

    public boolean isClientPatternSearchComplete() {
        return clientPatternSearchComplete;
    }

    public void forEachClientPatternSearchEntry(
            BiConsumer<Integer, Collection<AEKey>> consumer) {
        clientPatternSearchIndex.forEach(consumer);
    }

    public void acceptClientPatternSearchChunk(int revision, boolean reset, boolean complete,
            List<PatternSearchIndexPayload.Entry> entries) {
        if (!isClientSide()) {
            return;
        }
        if (reset) {
            clientPatternSearchIndex.clear();
            clientPatternSearchRevision = revision;
            clientPatternSearchComplete = false;
            if (!clientPatternSearchQuery.isBlank()) {
                filteredPatternView = true;
                filteredPatternCount = 0;
                page = 0;
                pageInventory.clearClientView();
                setPatternSlotActivity(0);
            }
            clientPatternSearchGeneration++;
        } else if (revision != clientPatternSearchRevision) {
            return;
        }
        for (var entry : entries) {
            if (entry.sourceSlot() < 0 || entry.sourceSlot() >= ModConfig.activePatternSlots()) {
                continue;
            }
            clientPatternSearchIndex
                    .computeIfAbsent(entry.sourceSlot(), ignored -> new LinkedHashSet<>())
                    .addAll(entry.keys());
        }
        if (complete) {
            clientPatternSearchComplete = true;
            clientPatternSearchRequested = false;
            clientPatternSearchGeneration++;
        }
    }

    public void requestFilteredPatternPage(int revision, int requestedPage, int resultCount,
            int[] sourceSlots) {
        if (!isClientSide()
                || !clientPatternSearchComplete
                || revision != clientPatternSearchRevision
                || sourceSlots == null
                || sourceSlots.length != MolecularCenterBlockEntity.PATTERNS_PER_PAGE) {
            return;
        }
        filteredPatternView = true;
        filteredPatternCount = Math.max(0, resultCount);
        page = Math.max(0, Math.min(getPageCount() - 1, requestedPage));
        pageInventory.clearClientView();
        setPatternSlotActivity(countMappedSlots(sourceSlots));
        sendClientAction(
                ACTION_SET_PATTERN_PROJECTION,
                new PatternPageRequest(containerId, revision, page, sourceSlots));
    }

    private int directPageCount() {
        return Math.max(1, (ModConfig.activePatternSlots()
                + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
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

    public void requestCyclePrimaryRoute() {
        if (isClientSide()) sendClientAction(ACTION_CYCLE_PRIMARY_ROUTE);
    }

    public void requestCycleByproductRoute() {
        if (isClientSide()) sendClientAction(ACTION_CYCLE_BYPRODUCT_ROUTE);
    }

    public void requestCycleOutputPort() {
        if (isClientSide()) sendClientAction(ACTION_CYCLE_OUTPUT_PORT);
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
        if (clickedSlot.container != getPlayerInventory()
                || !clickedSlot.mayPickup(player)) {
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
        int firstSlot = filteredPatternView
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
        return PatternDetailsHelper.isEncodedPattern(stack)
                || AEItems.BLANK_PATTERN.isSameAs(stack);
    }

    private void applyPage(int requestedPage) {
        filteredPatternView = false;
        filteredPatternCount = 0;
        page = clampDirectPage(requestedPage);
        pageInventory.setDirectPage(page);
        setPatternSlotActivity(MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        sendAllDataToRemote();
    }

    private int clampDirectPage(int requestedPage) {
        return Math.max(0, Math.min(directPageCount() - 1, requestedPage));
    }

    private void sendPatternSearchIndex() {
        if (!isServerSide() || !(getPlayer() instanceof ServerPlayer player)
                || player.containerMenu != this) {
            return;
        }
        int revision = center.getLogic().getPatternInventoryRevision();
        if (serverPatternSearchRevision == revision) {
            PatternSearchIndexPayload.sendChunked(
                    player,
                    containerId,
                    revision,
                    serverPatternSearchIndex);
        } else {
            rebuildAndSendPatternSearchIndex(player);
        }
    }

    private void rebuildAndSendPatternSearchIndex(ServerPlayer player) {
        int revision = center.getLogic().getPatternInventoryRevision();
        var index = new java.util.ArrayList<PatternSearchIndexPayload.Entry>();
        var searchableSlots = new HashSet<Integer>();
        var patternInventory = center.getLogic().getFullPatternInventory();
        int activeSlots = Math.min(ModConfig.activePatternSlots(), patternInventory.size());
        var level = center.getLevel();
        if (level != null) {
            for (int sourceSlot = 0; sourceSlot < activeSlots; sourceSlot++) {
                ItemStack stack = patternInventory.getStackInSlot(sourceSlot);
                if (!MolecularCenterLogic.isSupportedPattern(stack)) {
                    continue;
                }
                try {
                    var details = PatternDetailsHelper.decodePattern(stack, level);
                    if (!(details instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern)) {
                        continue;
                    }
                    var outputKeys = new LinkedHashSet<AEKey>();
                    for (var output : details.getOutputs()) {
                        if (outputKeys.size() >= MAX_SEARCH_KEYS_PER_SIDE) {
                            break;
                        }
                        if (output != null) {
                            addLightweightSearchKey(outputKeys, output.what());
                        }
                    }
                    var inputKeys = new LinkedHashSet<AEKey>();
                    for (var input : details.getInputs()) {
                        if (input == null || inputKeys.size() >= MAX_SEARCH_KEYS_PER_SIDE) {
                            continue;
                        }
                        for (var possibleInput : input.getPossibleInputs()) {
                            if (inputKeys.size() >= MAX_SEARCH_KEYS_PER_SIDE) {
                                break;
                            }
                            if (possibleInput != null) {
                                addLightweightSearchKey(inputKeys, possibleInput.what());
                            }
                        }
                    }
                    var keys = new LinkedHashSet<AEKey>(outputKeys);
                    keys.addAll(inputKeys);
                    if (!keys.isEmpty()) {
                        index.add(new PatternSearchIndexPayload.Entry(
                                sourceSlot,
                                List.copyOf(keys)));
                        searchableSlots.add(sourceSlot);
                    }
                } catch (RuntimeException exception) {
                    MolecularManipulator.LOGGER.warn(
                            "Unable to add molecular center pattern slot {} to the search index",
                            sourceSlot,
                            exception);
                }
            }
        }

        serverPatternSearchRevision = revision;
        serverInvalidatedPatternSearchRevision = -1;
        serverPatternSearchIndex = List.copyOf(index);
        serverSearchablePatternSlots = Set.copyOf(searchableSlots);
        if (filteredPatternView) {
            page = 0;
            filteredPatternCount = 0;
            pageInventory.clearProjection();
            setPatternSlotActivity(0);
            sendAllDataToRemote();
        }
        PatternSearchIndexPayload.sendChunked(
                player,
                containerId,
                revision,
                serverPatternSearchIndex);
    }

    private static void addLightweightSearchKey(Set<AEKey> keys, AEKey key) {
        if (key == null || keys.size() >= MAX_SEARCH_KEYS_PER_SIDE) {
            return;
        }
        AEKey lightweight = key.dropSecondary();
        if (lightweight != null) {
            keys.add(lightweight);
        }
    }

    private void applyPatternProjection(PatternPageRequest request) {
        if (!isServerSide()
                || !(getPlayer() instanceof ServerPlayer player)
                || player.containerMenu != this) {
            return;
        }
        if (!isValidPatternProjectionRequest(request)) {
            rejectPatternProjection();
            return;
        }
        int[] sourceSlots = request.sourceSlots();
        filteredPatternView = true;
        page = request.page();
        pageInventory.setProjection(sourceSlots);
        setPatternSlotActivity(countMappedSlots(sourceSlots));
        sendAllDataToRemote();
    }

    private void rejectPatternProjection() {
        filteredPatternView = true;
        filteredPatternCount = 0;
        page = 0;
        pageInventory.clearProjection();
        setPatternSlotActivity(0);
        sendAllDataToRemote();
    }

    private boolean isValidPatternProjectionRequest(PatternPageRequest request) {
        if (request == null
                || request.containerId() != containerId
                || request.revision() != serverPatternSearchRevision
                || request.page() < 0
                || request.page() >= MolecularCenterBlockEntity.MAX_PATTERN_PAGES) {
            return false;
        }
        int[] sourceSlots = request.sourceSlots();
        if (sourceSlots == null
                || sourceSlots.length != MolecularCenterBlockEntity.PATTERNS_PER_PAGE) {
            return false;
        }

        int activeSlots = Math.min(
                ModConfig.activePatternSlots(),
                center.getLogic().getFullPatternInventory().size());
        var seen = new HashSet<Integer>();
        boolean reachedEnd = false;
        for (int sourceSlot : sourceSlots) {
            if (sourceSlot == -1) {
                reachedEnd = true;
                continue;
            }
            if (reachedEnd
                    || sourceSlot < 0
                    || sourceSlot >= activeSlots
                    || !seen.add(sourceSlot)
                    || !serverSearchablePatternSlots.contains(sourceSlot)) {
                return false;
            }
        }
        return true;
    }

    private void setPatternSlotActivity(int mappedSlots) {
        for (int slot = 0; slot < patternSlots.size(); slot++) {
            patternSlots.get(slot).setActive(slot < mappedSlots);
        }
    }

    private static int countMappedSlots(int[] sourceSlots) {
        int count = 0;
        while (count < sourceSlots.length && sourceSlots[count] >= 0) {
            count++;
        }
        return count;
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

    private void cyclePrimaryRoute() {
        if (!isClientSide()) {
            center.cyclePrimaryRoute();
        }
    }

    private void cycleByproductRoute() {
        if (!isClientSide()) {
            center.cycleByproductRoute();
        }
    }

    private void cycleOutputPort() {
        if (!isClientSide()) {
            center.cycleOutputPort();
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
            patternInventoryRevision = center.getLogic().getPatternInventoryRevision();
            if (filteredPatternView
                    && serverPatternSearchRevision != patternInventoryRevision
                    && serverInvalidatedPatternSearchRevision != patternInventoryRevision) {
                page = 0;
                filteredPatternCount = 0;
                pageInventory.clearProjection();
                setPatternSlotActivity(0);
                serverInvalidatedPatternSearchRevision = patternInventoryRevision;
            }
            formed = center.isFormed();
            buildProgress = center.getBuildProgress();
            buildTotal = center.getBuildTotal();
            primaryRoute = center.getPrimaryRoute();
            byproductRoute = center.getByproductRoute();
            pipelineCacheAmount = center.getPipelineCacheAmount();
            pipelineCacheTypes = center.getPipelineCacheTypes();
            pendingOutputAmount = center.getPendingOutputAmount();
            pipelineBlocked = center.isPipelineBlocked();
            activePipelineRecipes = center.getActivePipelineRecipes();
            activePipelineCrafts = center.getActivePipelineCrafts();
            lastPipelineTransfer = center.getLastPipelineTransfer();
            outputPort = center.getOutputPort();
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
            rewriteOutputMode = center.getRewriteOutputMode();
            rewriteEnabled = center.isRewriteEnabled();
            rewriteJobState = center.getRewriteJobState();
            rewriteJobProgress = center.getRewriteJobProgress();
            rewriteJobProcessed = center.getRewriteJobProcessed();
            legacyStructure = center.hasLegacyStructure();
            legacyStructureUpdateDismissed = center.isLegacyStructureUpdateDismissed();
            building = center.isBuilding();
            dismantling = center.isDismantling();
        }
        super.broadcastChanges();
    }

    private static final class SupportedPatternSlot extends RestrictedInputSlot {
        private SupportedPatternSlot(InternalInventory inventory, int slot) {
            super(PlacableItemType.ENCODED_PATTERN, inventory, slot);
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
        private final int[] sourceSlots = new int[MolecularCenterBlockEntity.PATTERNS_PER_PAGE];

        private PagedInventory(InternalInventory source, boolean clientView) {
            this.source = source;
            this.clientView = clientView;
            if (clientView) {
                for (int slot = 0; slot < sourceSlots.length; slot++) {
                    sourceSlots[slot] = slot;
                }
            } else {
                setDirectPage(0);
            }
        }

        void setDirectPage(int page) {
            if (clientView) {
                clearClientView();
                return;
            }
            int firstSourceSlot = page * MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
            for (int slot = 0; slot < sourceSlots.length; slot++) {
                sourceSlots[slot] = firstSourceSlot + slot;
            }
        }

        void setProjection(int[] projection) {
            if (clientView) {
                clearClientView();
                return;
            }
            System.arraycopy(projection, 0, sourceSlots, 0, sourceSlots.length);
        }

        void clearProjection() {
            if (clientView) {
                clearClientView();
            } else {
                Arrays.fill(sourceSlots, -1);
            }
        }

        void clearClientView() {
            if (!clientView) {
                return;
            }
            for (int slot = 0; slot < source.size(); slot++) {
                source.setItemDirect(slot, ItemStack.EMPTY);
            }
        }

        private int sourceSlot(int slot) {
            if (slot < 0 || slot >= sourceSlots.length) {
                return -1;
            }
            return sourceSlots[slot];
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
            return sourceSlot >= 0
                    && sourceSlot < source.size()
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

    public static final class PatternPageRequest {
        private int containerId;
        private int revision;
        private int page;
        private int[] sourceSlots = new int[0];

        public PatternPageRequest() {
        }

        public PatternPageRequest(int containerId, int revision, int page, int[] sourceSlots) {
            this.containerId = containerId;
            this.revision = revision;
            this.page = page;
            this.sourceSlots = Arrays.copyOf(sourceSlots, sourceSlots.length);
        }

        public int containerId() {
            return containerId;
        }

        public int revision() {
            return revision;
        }

        public int page() {
            return page;
        }

        public int[] sourceSlots() {
            return sourceSlots;
        }
    }
}
