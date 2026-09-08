package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.Locatables;
import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.crafting.CraftingEvent;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.block.crafting.PatternProviderBlock;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainerHost;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry.MatterValue;
import com.atir.molecularmanipulator.world.MolecularCenterSpawnProtection;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class MolecularCenterBlockEntity extends PatternProviderBlockEntity
        implements InternalInventoryHost, SegmentedPatternContainerHost {
    public static final int PATTERNS_PER_PAGE = 36;
    public static final int MAX_PATTERN_PAGES = 1000;
    public static final int MAX_PATTERN_SLOTS = PATTERNS_PER_PAGE * MAX_PATTERN_PAGES;
    public static final long VIRTUAL_PARALLEL_LIMIT = Long.MAX_VALUE;
    private static final int MAX_BUFFERED_TYPES = 256;
    private static final String LEGACY_OUTPUT_BUFFER_TAG = "molecular_center_output_buffer";
    private static final String PENDING_PRIMARY_TAG = "pipeline_pending_primary";
    private static final String PENDING_BYPRODUCT_TAG = "pipeline_pending_byproduct";
    private static final String CACHED_PRIMARY_TAG = "pipeline_cached_primary";
    private static final String CACHED_BYPRODUCT_TAG = "pipeline_cached_byproduct";
    private static final String AUTO_CRAFT_PRIMARY_ESCROW_TAG = "auto_craft_primary_escrow";
    private static final String AUTO_CRAFT_REMAINDER_ESCROW_TAG = "auto_craft_remainder_escrow";
    private static final String OUTPUT_READY_TICK_TAG = "molecular_center_output_ready_tick";
    private static final String ACTIVE_REUSABLE_BATCH_TAG = "active_reusable_batch";
    private static final String REUSABLE_BATCH_REFUNDS_TAG = "reusable_batch_refunds";
    private static final String REPAIR_ONLY_BUILD_TAG = "molecular_center_repair_only_build";
    private static final String STRUCTURE_UPDATING_TAG = "molecular_center_structure_updating";
    private static final String STRUCTURE_UPDATE_SOURCE_TAG = "molecular_center_structure_update_source";
    private static final String CENTERED_CONTROLLER_TAG = "molecular_center_centered_controller";
    private static final String CONTROLLER_ANCHOR_TAG = "molecular_center_controller_anchor";
    private static final String DISMANTLE_LAYOUT_TAG = "molecular_center_dismantle_layout";
    private static final String DISMANTLE_PLAN_TAG = "molecular_center_dismantle_plan";
    private static final String DISMANTLE_UPGRADE_TAG = "molecular_center_dismantle_upgrade";
    private static final String LAST_KNOWN_LAYOUT_TAG = "molecular_center_last_known_layout";
    private static final String CONTROLLER_MOVE_RECOVERY_TAG = "molecular_center_controller_move_recovery";
    private static final String LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG =
            "molecular_center_legacy_structure_update_dismissed";
    private static final String MATTER_INVENTORY_TAG = "matter_sequence_inventory";
    private static final String METAL_SEQUENCE_TAG = "matter_sequence_metal";
    private static final String MINERAL_SEQUENCE_TAG = "matter_sequence_mineral";
    private static final String CRYSTAL_SEQUENCE_TAG = "matter_sequence_crystal";
    private static final String ORGANIC_SEQUENCE_TAG = "matter_sequence_organic";
    private static final String ENTROPY_TAG = "matter_sequence_entropy";
    private static final String MATTER_UPGRADES_TAG = "matter_sequence_upgrades";
    private static final String MATTER_JOB_MODE_TAG = "matter_job_mode";
    private static final String MATTER_JOB_DISPLAY_MODE_TAG = "matter_job_display_mode";
    private static final String MATTER_JOB_STATE_TAG = "matter_job_state";
    private static final String MATTER_JOB_PROGRESS_TAG = "matter_job_progress";
    private static final String MATTER_JOB_PROCESSED_TAG = "matter_job_processed";
    private static final String DECONSTRUCT_ENABLED_TAG = "matter_deconstruct_enabled";
    private static final String DECONSTRUCT_STATE_TAG = "matter_deconstruct_state";
    private static final String DECONSTRUCT_PROGRESS_TAG = "matter_deconstruct_progress";
    private static final String DECONSTRUCT_PROCESSED_TAG = "matter_deconstruct_processed";
    private static final String REWRITE_ENABLED_TAG = "matter_rewrite_enabled";
    private static final String REWRITE_STATE_TAG = "matter_rewrite_state";
    private static final String REWRITE_PROGRESS_TAG = "matter_rewrite_progress";
    private static final String REWRITE_PROCESSED_TAG = "matter_rewrite_processed";
    private static final String DECONSTRUCT_TARGET_TAG = "matter_deconstruct_target";
    private static final String REWRITE_TARGET_TAG = "matter_rewrite_target";
    private static final String DECONSTRUCT_TEMPLATE_TAG = "matter_deconstruct_template";
    private static final String REWRITE_TEMPLATE_TAG = "matter_rewrite_template";
    private static final String REWRITE_OUTPUT_MODE_TAG = "matter_rewrite_output_mode";
    private static final String DECONSTRUCT_MARKER_FORMAT_TAG = "matter_deconstruct_marker_format";
    private static final String LEGACY_DECONSTRUCT_REFUND_TAG = "matter_deconstruct_legacy_refund";
    private static final String FIELD_COLOR_TAG = "visual_field_color";
    private static final String CORE_COLOR_TAG = "visual_core_color";
    private static final String PRIMARY_RING_COLOR_TAG = "visual_primary_ring_color";
    private static final String SECONDARY_RING_COLOR_TAG = "visual_secondary_ring_color";
    private static final String LATTICE_COLOR_TAG = "visual_lattice_color";
    private static final int VISUAL_ACTIVITY_EVENT = 91;
    private static final int CROWN_ACTIVITY_EVENT = 92;
    public static final double QUANTUM_LINK_POWER = 512.0;
    public static final long MAX_JOB_TARGET = 1_000_000_000_000L;
    public static final int MAX_SPEED_CARDS = 4;
    public static final int DEFAULT_FIELD_COLOR = 0xB6AEFF;
    public static final int DEFAULT_CORE_COLOR = 0xDFFFFF;
    public static final int DEFAULT_PRIMARY_RING_COLOR = 0x90EAFF;
    public static final int DEFAULT_SECONDARY_RING_COLOR = 0xC0ABFF;
    public static final int DEFAULT_LATTICE_COLOR = 0xBA8DFF;

    private final MachineSource actionSource = new MachineSource(this);
    private final SegmentedPatternContainers terminalPatternContainers =
            new SegmentedPatternContainers(this);
    private final AppEngInternalInventory matterInventory = new AppEngInternalInventory(this, 4);
    private final IUpgradeInventory matterUpgrades = UpgradeInventories.forMachine(
            ModContent.MOLECULAR_CENTER_CONTROLLER.get(), MAX_SPEED_CARDS, this::onMatterUpgradesChanged);
    private final MolecularCraftingBatcher craftingBatcher = new MolecularCraftingBatcher();
    private final Object2LongOpenHashMap<AEKey> pendingPrimaryOutputs = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> pendingByproducts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> reusableBatchRefunds = new Object2LongOpenHashMap<>();
    private final AEKeyTransferScheduler pendingPrimaryTransferScheduler = new AEKeyTransferScheduler();
    private final AEKeyTransferScheduler pendingByproductTransferScheduler = new AEKeyTransferScheduler();
    private final AEKeyTransferScheduler reusableBatchRefundTransferScheduler = new AEKeyTransferScheduler();
    private final ReferenceOpenHashSet<IPatternDetails> craftingEventsThisTick = new ReferenceOpenHashSet<>();
    private final MolecularAutoCrafter autoCrafter = new MolecularAutoCrafter(this);
    private boolean assembling;
    private boolean formed;
    private MolecularCenterStructure.StructureLayout structureLayout =
            MolecularCenterStructure.StructureLayout.INCOMPLETE;
    private boolean legacyStructureUpdateDismissed;
    private boolean pipelineBlocked;
    private long craftingEventTick = Long.MIN_VALUE;
    private long bufferDirtyTick = Long.MIN_VALUE;
    private long outputReadyTick = Long.MIN_VALUE;
    private long pipelineActivityTick = Long.MIN_VALUE;
    private long pipelineCrafts;
    private long lastPipelineTransfer;
    private MolecularReusableBatchJob activeReusableBatch;
    private CompoundTag quarantinedReusableBatchTag;
    private long structureCheckTick = Long.MIN_VALUE;
    private int fieldColor = DEFAULT_FIELD_COLOR;
    private int coreColor = DEFAULT_CORE_COLOR;
    private int primaryRingColor = DEFAULT_PRIMARY_RING_COLOR;
    private int secondaryRingColor = DEFAULT_SECONDARY_RING_COLOR;
    private int latticeColor = DEFAULT_LATTICE_COLOR;
    private long metalSequence;
    private long mineralSequence;
    private long crystalSequence;
    private long organicSequence;
    private long entropy;
    private boolean deconstructEnabled;
    private MatterJobState deconstructJobState = MatterJobState.IDLE;
    private int deconstructJobProgress;
    private long deconstructJobProcessed;
    private boolean rewriteEnabled;
    private MatterJobState rewriteJobState = MatterJobState.IDLE;
    private int rewriteJobProgress;
    private long rewriteJobProcessed;
    private long deconstructTarget;
    private long rewriteTarget;
    private ItemStack deconstructTemplate = ItemStack.EMPTY;
    private ItemStack rewriteTemplate = ItemStack.EMPTY;
    private ItemStack legacyDeconstructRefund = ItemStack.EMPTY;
    private RewriteOutputMode rewriteOutputMode = RewriteOutputMode.NETWORK;
    private IGridConnection quantumConnection;
    private IGridNode quantumRemoteNode;
    private long quantumConnectionFrequency;
    private long claimedQuantumFrequency;
    private QuantumLinkState quantumLinkState = QuantumLinkState.EMPTY;
    private int visualModeAnnounced = -1;
    private int clientVisualMode;
    private float clientVisualAngle;
    private double clientVisualSample = Double.NaN;
    private final SequenceCrownAnimation crownAnimation = new SequenceCrownAnimation();
    private int crownWorkingAnnounced = -1;
    private long visualSuccessTick = Long.MIN_VALUE;
    private long visualPulseTick = Long.MIN_VALUE;
    private boolean building;
    private boolean dismantling;
    private boolean repairOnlyBuild;
    private boolean structureUpdating;
    private ControllerAnchor controllerAnchor = ControllerAnchor.LOWERED;
    private MolecularCenterStructure.StructureLayout dismantleLayout =
            MolecularCenterStructure.StructureLayout.CURRENT;
    private MolecularCenterStructure.StructureLayout lastKnownStructureLayout =
            MolecularCenterStructure.StructureLayout.INCOMPLETE;
    private DismantlePlan dismantlePlan;
    private boolean dismantleIncludesUpgrade;
    private MolecularCenterStructure.StructureLayout structureUpdateSourceLayout =
            MolecularCenterStructure.StructureLayout.INCOMPLETE;
    private boolean buildQueueInitialized;
    private List<MolecularCenterStructure.Part> buildWorkParts = List.of();
    private int workCursor;
    private int workTotal;
    private int workConflicts;
    private UUID workOwner;

    public MolecularCenterBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOLECULAR_CENTER_CONTROLLER_BE.get(), pos, state);
        matterInventory.setMaxStackSize(0, 1);
        matterInventory.setMaxStackSize(1, 1);
        matterInventory.setMaxStackSize(3, 1);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(ModConfig.IDLE_POWER.get());
    }

    @Override
    protected MolecularCenterLogic createLogic() {
        return new MolecularCenterLogic(this);
    }

    @Override
    public MolecularCenterLogic getLogic() {
        return (MolecularCenterLogic) super.getLogic();
    }

    @Override
    public appeng.api.inventories.InternalInventory getTerminalPatternInventory() {
        int activeSlots = Math.min(ModConfig.activePatternSlots(), getLogic().getFullPatternInventory().size());
        return getLogic().getFullPatternInventory().getSubInventory(0, activeSlots);
    }

    @Override
    public List<PatternContainer> molecularmanipulator$getTerminalPatternContainers() {
        return terminalPatternContainers.getContainers();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModContent.MOLECULAR_CENTER_CONTROLLER_ITEM.get());
    }

    public AppEngInternalInventory getMatterInventory() {
        return matterInventory;
    }

    public IUpgradeInventory getMatterUpgrades() {
        return matterUpgrades;
    }

    public MolecularAutoCrafter getAutoCrafter() {
        return autoCrafter;
    }

    private void onMatterUpgradesChanged() {
        deconstructJobProgress = Math.min(deconstructJobProgress, getMatterCycleTicks());
        rewriteJobProgress = Math.min(rewriteJobProgress, getMatterCycleTicks());
        saveChanges();
    }


    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory inventory, int slot) {
        if (inventory == matterInventory && slot == 3 && level != null && !level.isClientSide()) {
            disconnectQuantumLink(QuantumLinkState.SEARCHING);
            updateQuantumLink();
        }
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void onReady() {
        super.onReady();
        refreshStructure();
        normalizeIsolatedControllerAnchor();
        restoreBuildQueue();
        syncShellConnections(formed);
        updateQuantumLink();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval(QuantumLinkState.SEARCHING);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval(QuantumLinkState.SEARCHING);
        super.setRemoved();
    }

    public boolean hasRemovalRecovery() {
        return activeReusableBatch != null
                || quarantinedReusableBatchTag != null
                || !pendingPrimaryOutputs.isEmpty()
                || !pendingByproducts.isEmpty()
                || !reusableBatchRefunds.isEmpty()
                || RetainedBlockContents.hasPatternContents(this)
                || !matterInventory.isEmpty() || !matterUpgrades.isEmpty()
                || !autoCrafter.getPatternInventory().isEmpty()
                || !legacyDeconstructRefund.isEmpty()
                || metalSequence > 0 || mineralSequence > 0 || crystalSequence > 0 || organicSequence > 0;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos,
            List<ItemStack> drops) {
        if (hasRemovalRecovery()) {
            drops.add(createRemovalRecovery());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        matterInventory.clear();
        matterUpgrades.clear();
        metalSequence = mineralSequence = crystalSequence = organicSequence = entropy = 0;
        legacyDeconstructRefund = ItemStack.EMPTY;
        pendingPrimaryOutputs.clear();
        pendingByproducts.clear();
        reusableBatchRefunds.clear();
        autoCrafter.clear();
        activeReusableBatch = null;
        quarantinedReusableBatchTag = null;
        outputReadyTick = Long.MIN_VALUE;
    }

    private ItemStack createRemovalRecovery() {
        var payload = new CompoundTag();
        getLogic().writeToNBT(payload);
        saveStoredContents(payload);
        payload.putString(CONTROLLER_ANCHOR_TAG, controllerAnchor.name());
        return RetainedBlockContents.createDrop(this, payload);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("molecular_center_work_revision", 1);
        tag.putBoolean("molecular_center_formed", formed);
        tag.putBoolean("molecular_center_building", building);
        tag.putBoolean("molecular_center_dismantling", dismantling);
        tag.putBoolean(REPAIR_ONLY_BUILD_TAG, repairOnlyBuild);
        tag.putBoolean(STRUCTURE_UPDATING_TAG, structureUpdating);
        tag.putBoolean(CENTERED_CONTROLLER_TAG, controllerAnchor != ControllerAnchor.RIM);
        tag.putString(CONTROLLER_ANCHOR_TAG, controllerAnchor.name());
        if (dismantling) {
            tag.putString(DISMANTLE_LAYOUT_TAG, dismantleLayout.name());
            tag.putBoolean(DISMANTLE_UPGRADE_TAG, dismantleIncludesUpgrade);
            if (dismantlePlan != null) {
                tag.put(DISMANTLE_PLAN_TAG, dismantlePlan.save());
            }
        }
        if (lastKnownStructureLayout.isFormed()) {
            tag.putString(LAST_KNOWN_LAYOUT_TAG, lastKnownStructureLayout.name());
        }
        if (structureUpdating) {
            tag.putString(STRUCTURE_UPDATE_SOURCE_TAG, structureUpdateSourceLayout.name());
        }
        tag.putBoolean(LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG, legacyStructureUpdateDismissed);
        tag.putInt("molecular_center_work_cursor", workCursor);
        tag.putInt("molecular_center_work_total", workTotal);
        tag.putInt("molecular_center_work_conflicts", workConflicts);
        if (workOwner != null) {
            tag.putUUID("molecular_center_work_owner", workOwner);
        }
        saveStoredContents(tag);
    }

    private void saveStoredContents(CompoundTag tag) {
        writeStacks(tag, AUTO_CRAFT_PRIMARY_ESCROW_TAG, pendingPrimaryOutputs);
        writeStacks(tag, AUTO_CRAFT_REMAINDER_ESCROW_TAG, pendingByproducts);
        writeStacks(tag, REUSABLE_BATCH_REFUNDS_TAG, reusableBatchRefunds);
        if (activeReusableBatch != null) {
            tag.put(ACTIVE_REUSABLE_BATCH_TAG, activeReusableBatch.writeToTag());
        } else if (quarantinedReusableBatchTag != null) {
            tag.put(ACTIVE_REUSABLE_BATCH_TAG, quarantinedReusableBatchTag.copy());
        }
        tag.putLong(OUTPUT_READY_TICK_TAG, outputReadyTick);
        autoCrafter.save(tag);
        matterInventory.writeToNBT(tag, MATTER_INVENTORY_TAG);
        matterUpgrades.writeToNBT(tag, MATTER_UPGRADES_TAG);
        tag.putLong(METAL_SEQUENCE_TAG, metalSequence);
        tag.putLong(MINERAL_SEQUENCE_TAG, mineralSequence);
        tag.putLong(CRYSTAL_SEQUENCE_TAG, crystalSequence);
        tag.putLong(ORGANIC_SEQUENCE_TAG, organicSequence);
        tag.putLong(ENTROPY_TAG, entropy);
        tag.putBoolean(DECONSTRUCT_ENABLED_TAG, deconstructEnabled);
        tag.putString(DECONSTRUCT_STATE_TAG, deconstructJobState.name());
        tag.putInt(DECONSTRUCT_PROGRESS_TAG, deconstructJobProgress);
        tag.putLong(DECONSTRUCT_PROCESSED_TAG, deconstructJobProcessed);
        tag.putBoolean(REWRITE_ENABLED_TAG, rewriteEnabled);
        tag.putString(REWRITE_STATE_TAG, rewriteJobState.name());
        tag.putInt(REWRITE_PROGRESS_TAG, rewriteJobProgress);
        tag.putLong(REWRITE_PROCESSED_TAG, rewriteJobProcessed);
        tag.putLong(DECONSTRUCT_TARGET_TAG, deconstructTarget);
        tag.putLong(REWRITE_TARGET_TAG, rewriteTarget);
        tag.putString(REWRITE_OUTPUT_MODE_TAG, rewriteOutputMode.name());
        tag.putBoolean(DECONSTRUCT_MARKER_FORMAT_TAG, true);
        if (!legacyDeconstructRefund.isEmpty()) {
            tag.put(LEGACY_DECONSTRUCT_REFUND_TAG, legacyDeconstructRefund.save(new CompoundTag()));
        }
        if (!deconstructTemplate.isEmpty()) {
            tag.put(DECONSTRUCT_TEMPLATE_TAG, deconstructTemplate.save(new CompoundTag()));
        }
        if (!rewriteTemplate.isEmpty()) {
            tag.put(REWRITE_TEMPLATE_TAG, rewriteTemplate.save(new CompoundTag()));
        }
        writeVisualColors(tag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag);
        formed = tag.getBoolean("molecular_center_formed");
        building = tag.getBoolean("molecular_center_building");
        dismantling = tag.getBoolean("molecular_center_dismantling");
        lastKnownStructureLayout = MolecularCenterStructure.StructureLayout.fromSavedName(tag.getString(LAST_KNOWN_LAYOUT_TAG));
        dismantleIncludesUpgrade = tag.getBoolean(DISMANTLE_UPGRADE_TAG);
        dismantlePlan = dismantling && tag.contains(DISMANTLE_PLAN_TAG, Tag.TAG_COMPOUND)
                ? DismantlePlan.load(tag.getCompound(DISMANTLE_PLAN_TAG)) : null;
        // A failed final relocation pauses while retaining its source blueprint.
        structureUpdating = !dismantling && tag.getBoolean(STRUCTURE_UPDATING_TAG);
        controllerAnchor = readEnum(tag.getString(CONTROLLER_ANCHOR_TAG), ControllerAnchor.RIM);
        dismantleLayout = MolecularCenterStructure.StructureLayout.fromSavedName(tag.getString(DISMANTLE_LAYOUT_TAG));
        structureUpdateSourceLayout = structureUpdating
                ? MolecularCenterStructure.StructureLayout.fromSavedName(tag.getString(STRUCTURE_UPDATE_SOURCE_TAG))
                : MolecularCenterStructure.StructureLayout.INCOMPLETE;
        if (structureUpdateSourceLayout == MolecularCenterStructure.StructureLayout.CURRENT
                || structureUpdateSourceLayout == MolecularCenterStructure.StructureLayout.INCOMPLETE) {
            if (structureUpdating) building = false;
            structureUpdating = false;
            structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
        }
        repairOnlyBuild = building && !structureUpdating && tag.getBoolean(REPAIR_ONLY_BUILD_TAG);
        if (building && !structureUpdating && controllerAnchor != ControllerAnchor.LOWERED) {
            // Pre-anchor-change saves may contain an unfinished generic build.
            // Its numeric cursor must not resume against a different blueprint.
            building = false;
            repairOnlyBuild = false;
        }
        legacyStructureUpdateDismissed = tag.getBoolean(LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG);
        buildQueueInitialized = false;
        buildWorkParts = List.of();
        workCursor = tag.getInt("molecular_center_work_cursor");
        // Earlier generic build cursors indexed a union of experimental blueprints.
        // Re-check completed placements safely instead of applying that index to the smaller work list.
        if (building && !structureUpdating && tag.getInt("molecular_center_work_revision") < 1) workCursor = 0;
        workTotal = repairOnlyBuild
                ? Math.max(0, tag.getInt("molecular_center_work_total"))
                : structureUpdating
                        ? MolecularCenterStructure.updateWorkParts(structureUpdateSourceLayout).size()
                        : MolecularCenterStructure.workParts().size();
        if (building) {
            dismantling = false;
            workCursor = Math.max(0, Math.min(workCursor, workTotal));
        } else if (dismantling) {
            // The former counter walked a historical AIR union backwards. It
            // cannot address the new actual-block snapshot and is never reused.
            workCursor = dismantlePlan == null ? 0 : dismantlePlan.completed();
            workTotal = dismantlePlan == null ? 0 : dismantlePlan.total();
        }
        workConflicts = tag.getInt("molecular_center_work_conflicts");
        workOwner = tag.hasUUID("molecular_center_work_owner") ? tag.getUUID("molecular_center_work_owner") : null;
        pendingPrimaryOutputs.clear();
        pendingByproducts.clear();
        reusableBatchRefunds.clear();
        var legacyCachedPrimary = new Object2LongOpenHashMap<AEKey>();
        var legacyCachedRemainders = new Object2LongOpenHashMap<AEKey>();
        boolean hasAutoCraftEscrow = tag.contains(AUTO_CRAFT_PRIMARY_ESCROW_TAG, Tag.TAG_LIST)
                || tag.contains(AUTO_CRAFT_REMAINDER_ESCROW_TAG, Tag.TAG_LIST);
        if (hasAutoCraftEscrow) {
            readStacks(tag, AUTO_CRAFT_PRIMARY_ESCROW_TAG, pendingPrimaryOutputs);
            readStacks(tag, AUTO_CRAFT_REMAINDER_ESCROW_TAG, pendingByproducts);
        } else if (tag.contains(PENDING_PRIMARY_TAG, Tag.TAG_LIST)) {
            readStacks(tag, PENDING_PRIMARY_TAG, pendingPrimaryOutputs);
            readStacks(tag, PENDING_BYPRODUCT_TAG, pendingByproducts);
        } else {
            readStacks(tag, LEGACY_OUTPUT_BUFFER_TAG, pendingPrimaryOutputs);
            readStacks(tag, PENDING_BYPRODUCT_TAG, pendingByproducts);
        }
        if (!hasAutoCraftEscrow) {
            readStacks(tag, CACHED_PRIMARY_TAG, legacyCachedPrimary);
            readStacks(tag, CACHED_BYPRODUCT_TAG, legacyCachedRemainders);
        }
        MolecularAutoCraftMath.mergeSaturated(pendingPrimaryOutputs, legacyCachedPrimary);
        MolecularAutoCraftMath.mergeSaturated(pendingByproducts, legacyCachedRemainders);
        readStacks(tag, REUSABLE_BATCH_REFUNDS_TAG, reusableBatchRefunds);
        activeReusableBatch = null;
        quarantinedReusableBatchTag = null;
        if (tag.contains(ACTIVE_REUSABLE_BATCH_TAG, Tag.TAG_COMPOUND)) {
            var jobTag = tag.getCompound(ACTIVE_REUSABLE_BATCH_TAG);
            activeReusableBatch = MolecularReusableBatchJob.readFromTag(jobTag);
            if (activeReusableBatch == null) {
                quarantinedReusableBatchTag = jobTag.copy();
                MolecularManipulator.LOGGER.error(
                        "Invalid sequence-array reusable batch at {}; preserving its NBT and locking the controller",
                        getBlockPos());
            }
        }
        // Legacy pipeline routes are intentionally not restored. Every output now
        // returns to the controller's ME network.
        outputReadyTick = tag.contains(OUTPUT_READY_TICK_TAG, Tag.TAG_LONG)
                ? tag.getLong(OUTPUT_READY_TICK_TAG)
                : Long.MIN_VALUE;
        matterInventory.readFromNBT(tag, MATTER_INVENTORY_TAG);
        legacyDeconstructRefund = tag.contains(LEGACY_DECONSTRUCT_REFUND_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(LEGACY_DECONSTRUCT_REFUND_TAG))
                : ItemStack.EMPTY;
        if (!tag.getBoolean(DECONSTRUCT_MARKER_FORMAT_TAG)
                && legacyDeconstructRefund.isEmpty()
                && !matterInventory.getStackInSlot(0).isEmpty()) {
            legacyDeconstructRefund = matterInventory.getStackInSlot(0).copy();
            matterInventory.setItemDirect(0, legacyDeconstructRefund.copyWithCount(1));
        }
        matterUpgrades.readFromNBT(tag, MATTER_UPGRADES_TAG);
        long sequenceCapacity = getMatterSequenceCapacity();
        metalSequence = readStoredAmount(tag, METAL_SEQUENCE_TAG, sequenceCapacity);
        mineralSequence = readStoredAmount(tag, MINERAL_SEQUENCE_TAG, sequenceCapacity);
        crystalSequence = readStoredAmount(tag, CRYSTAL_SEQUENCE_TAG, sequenceCapacity);
        organicSequence = readStoredAmount(tag, ORGANIC_SEQUENCE_TAG, sequenceCapacity);
        entropy = readStoredAmount(tag, ENTROPY_TAG, getMatterEntropyCapacity());
        if (tag.contains(DECONSTRUCT_ENABLED_TAG, Tag.TAG_BYTE)) {
            deconstructEnabled = tag.getBoolean(DECONSTRUCT_ENABLED_TAG);
            deconstructJobState = readEnum(tag.getString(DECONSTRUCT_STATE_TAG), MatterJobState.IDLE);
            deconstructJobProgress = readJobProgress(tag, DECONSTRUCT_PROGRESS_TAG);
            deconstructJobProcessed = readStoredAmount(tag, DECONSTRUCT_PROCESSED_TAG, MAX_JOB_TARGET);
            rewriteEnabled = tag.getBoolean(REWRITE_ENABLED_TAG);
            rewriteJobState = readEnum(tag.getString(REWRITE_STATE_TAG), MatterJobState.IDLE);
            rewriteJobProgress = readJobProgress(tag, REWRITE_PROGRESS_TAG);
            rewriteJobProcessed = readStoredAmount(tag, REWRITE_PROCESSED_TAG, MAX_JOB_TARGET);
        } else {
            var legacyMode = readEnum(tag.getString(MATTER_JOB_MODE_TAG), MatterJobMode.IDLE);
            var legacyDisplayMode = readEnum(tag.getString(MATTER_JOB_DISPLAY_MODE_TAG),
                    legacyMode == MatterJobMode.REWRITE ? MatterJobMode.REWRITE : MatterJobMode.DECONSTRUCT);
            var legacyState = readEnum(tag.getString(MATTER_JOB_STATE_TAG), MatterJobState.IDLE);
            int legacyProgress = readJobProgress(tag, MATTER_JOB_PROGRESS_TAG);
            long legacyProcessed = readStoredAmount(tag, MATTER_JOB_PROCESSED_TAG, MAX_JOB_TARGET);
            deconstructEnabled = legacyMode == MatterJobMode.DECONSTRUCT;
            rewriteEnabled = legacyMode == MatterJobMode.REWRITE;
            boolean deconstructWasLast = legacyMode == MatterJobMode.DECONSTRUCT
                    || legacyMode == MatterJobMode.IDLE && legacyDisplayMode == MatterJobMode.DECONSTRUCT;
            deconstructJobState = deconstructWasLast ? legacyState : MatterJobState.IDLE;
            rewriteJobState = deconstructWasLast ? MatterJobState.IDLE : legacyState;
            deconstructJobProgress = deconstructEnabled ? legacyProgress : 0;
            rewriteJobProgress = rewriteEnabled ? legacyProgress : 0;
            deconstructJobProcessed = deconstructWasLast ? legacyProcessed : 0;
            rewriteJobProcessed = deconstructWasLast ? 0 : legacyProcessed;
        }
        deconstructTarget = readStoredAmount(tag, DECONSTRUCT_TARGET_TAG, MAX_JOB_TARGET);
        rewriteTarget = readStoredAmount(tag, REWRITE_TARGET_TAG, MAX_JOB_TARGET);
        rewriteOutputMode = readEnum(tag.getString(REWRITE_OUTPUT_MODE_TAG), RewriteOutputMode.NETWORK);
        deconstructTemplate = tag.contains(DECONSTRUCT_TEMPLATE_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(DECONSTRUCT_TEMPLATE_TAG))
                : ItemStack.EMPTY;
        rewriteTemplate = tag.contains(REWRITE_TEMPLATE_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(REWRITE_TEMPLATE_TAG))
                : ItemStack.EMPTY;
        fieldColor = readColor(tag, FIELD_COLOR_TAG, DEFAULT_FIELD_COLOR);
        coreColor = readColor(tag, CORE_COLOR_TAG, DEFAULT_CORE_COLOR);
        primaryRingColor = readColor(tag, PRIMARY_RING_COLOR_TAG, DEFAULT_PRIMARY_RING_COLOR);
        secondaryRingColor = readColor(tag, SECONDARY_RING_COLOR_TAG, DEFAULT_SECONDARY_RING_COLOR);
        latticeColor = readColor(tag, LATTICE_COLOR_TAG, DEFAULT_LATTICE_COLOR);
        autoCrafter.load(tag);
    }

    private void writeVisualColors(CompoundTag tag) {
        tag.putInt(FIELD_COLOR_TAG, fieldColor);
        tag.putInt(CORE_COLOR_TAG, coreColor);
        tag.putInt(PRIMARY_RING_COLOR_TAG, primaryRingColor);
        tag.putInt(SECONDARY_RING_COLOR_TAG, secondaryRingColor);
        tag.putInt(LATTICE_COLOR_TAG, latticeColor);
    }

    private static int readColor(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) & 0xFFFFFF : fallback;
    }

    private static long readStoredAmount(CompoundTag tag, String key, long maximum) {
        return Math.max(0, Math.min(maximum, tag.getLong(key)));
    }

    private int readJobProgress(CompoundTag tag, String key) {
        return Math.max(0, Math.min(getMatterCycleTicks(), tag.getInt(key)));
    }

    private static <E extends Enum<E>> E readEnum(String name, E fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            @SuppressWarnings("unchecked")
            var type = (Class<E>) fallback.getDeclaringClass();
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeInt(fieldColor);
        data.writeInt(coreColor);
        data.writeInt(primaryRingColor);
        data.writeInt(secondaryRingColor);
        data.writeInt(latticeColor);
        data.writeEnum(structureLayout);
        data.writeEnum(controllerAnchor);
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int newFieldColor = data.readInt() & 0xFFFFFF;
        int newCoreColor = data.readInt() & 0xFFFFFF;
        int newPrimaryRingColor = data.readInt() & 0xFFFFFF;
        int newSecondaryRingColor = data.readInt() & 0xFFFFFF;
        int newLatticeColor = data.readInt() & 0xFFFFFF;
        var newLayout = data.readEnum(MolecularCenterStructure.StructureLayout.class);
        var newControllerAnchor = data.readEnum(ControllerAnchor.class);
        changed |= fieldColor != newFieldColor
                || coreColor != newCoreColor
                || primaryRingColor != newPrimaryRingColor
                || secondaryRingColor != newSecondaryRingColor
                || latticeColor != newLatticeColor
                || structureLayout != newLayout
                || controllerAnchor != newControllerAnchor;
        fieldColor = newFieldColor;
        coreColor = newCoreColor;
        primaryRingColor = newPrimaryRingColor;
        secondaryRingColor = newSecondaryRingColor;
        latticeColor = newLatticeColor;
        structureLayout = newLayout;
        controllerAnchor = newControllerAnchor;
        return changed;
    }

    private static void writeStacks(CompoundTag tag, String key, Object2LongOpenHashMap<AEKey> stacks) {
        var list = new ListTag();
        for (var entry : stacks.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                list.add(GenericStack.writeTag(new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(key, list);
    }

    private static void readStacks(CompoundTag tag, String key, Object2LongOpenHashMap<AEKey> stacks) {
        var list = tag.getList(key, Tag.TAG_COMPOUND);
        for (var entryTag : list) {
            var stack = GenericStack.readTag((CompoundTag) entryTag);
            if (stack != null && stack.amount() > 0) {
                try {
                    stacks.put(stack.what(), Math.addExact(stacks.getLong(stack.what()), stack.amount()));
                } catch (ArithmeticException ignored) {
                    stacks.put(stack.what(), Long.MAX_VALUE);
                }
            }
        }
    }

    public boolean isFormed() {
        return formed;
    }

    public boolean isOperational() {
        return formed && getMainNode().isActive();
    }

    boolean hasActiveReusableBatch() {
        return activeReusableBatch != null
                || quarantinedReusableBatchTag != null;
    }

    public long getPendingOutputAmount() {
        return saturatedAdd(saturatedSum(pendingPrimaryOutputs, pendingByproducts),
                sumAmounts(reusableBatchRefunds));
    }

    public boolean isPipelineBlocked() {
        return pipelineBlocked;
    }

    public int getActivePipelineRecipes() {
        return isRecentPipelineActivity() ? craftingEventsThisTick.size() : 0;
    }

    public long getActivePipelineCrafts() {
        return isRecentPipelineActivity() ? pipelineCrafts : 0;
    }

    public long getLastPipelineTransfer() {
        return lastPipelineTransfer;
    }

    boolean canRunAutoCrafting() {
        return !assembling && !pipelineBlocked && !hasActiveReusableBatch() && isOperational()
                && getLogic().getReturnInv().isEmpty();
    }

    MachineSource autoCraftActionSource() {
        return actionSource;
    }

    boolean canQueueAutoCraftOutputs(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders) {
        return canQueueOutputs(primary, remainders);
    }

    void queueAutoCraftOutputs(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders, long gameTime,
            long craftCount) {
        addOutputs(pendingPrimaryOutputs, primary);
        addOutputs(pendingByproducts, remainders);
        recordPipelineActivity(gameTime, craftCount);
        outputReadyTick = Math.max(outputReadyTick, gameTime + 1);
        markOutputBufferChanged(gameTime);
        returnAutoCraftRemaindersImmediately(remainders, gameTime);
    }

    private void returnAutoCraftRemaindersImmediately(
            Object2LongOpenHashMap<AEKey> remainders, long gameTime) {
        if (remainders.isEmpty()) {
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        var storage = grid.getStorageService().getInventory();
        boolean changed = false;
        for (var entry : remainders.object2LongEntrySet()) {
            long pending = pendingByproducts.getLong(entry.getKey());
            long requested = Math.min(pending, entry.getLongValue());
            if (requested <= 0) {
                continue;
            }
            long inserted = storage.insert(entry.getKey(), requested,
                    Actionable.MODULATE, actionSource);
            if (inserted <= 0) {
                continue;
            }
            long remaining = pending - inserted;
            if (remaining == 0) {
                pendingByproducts.removeLong(entry.getKey());
            } else {
                pendingByproducts.put(entry.getKey(), remaining);
            }
            changed = true;
        }
        if (changed) {
            markOutputBufferChanged(gameTime);
        }
    }

    void queueAutoCraftRefund(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        reusableBatchRefunds.put(key, saturatedAdd(
                reusableBatchRefunds.getLong(key), amount));
        if (level != null) {
            markOutputBufferChanged(level.getGameTime());
        } else {
            saveChanges();
        }
    }

    long getBufferedAutoCraftAmount(AEKey key) {
        if (key == null) {
            return 0;
        }
        long amount = saturatedAdd(pendingPrimaryOutputs.getLong(key),
                pendingByproducts.getLong(key));
        return saturatedAdd(amount, reusableBatchRefunds.getLong(key));
    }

    public long getMetalSequence() {
        return metalSequence;
    }

    public long getMineralSequence() {
        return mineralSequence;
    }

    public long getCrystalSequence() {
        return crystalSequence;
    }

    public long getOrganicSequence() {
        return organicSequence;
    }

    public long getEntropy() {
        return entropy;
    }

    public long getMatterSequenceCapacity() {
        return ModConfig.MATTER_SEQUENCE_CAPACITY.get();
    }

    public long getMatterEntropyCapacity() {
        return ModConfig.MATTER_ENTROPY_CAPACITY.get();
    }

    public long getMatterEntropyCoolingPerSecond() {
        return saturatedMultiply(
                ModConfig.MATTER_ENTROPY_COOLING_PER_SECOND.get(),
                getMatterEntropyCoolingMultiplier());
    }

    public long getMatterEntropyCoolingMultiplier() {
        return ModConfig.matterEntropyCoolingMultiplier(getInstalledSpeedCards());
    }

    public long getQuantumFrequency() {
        var stack = matterInventory.getStackInSlot(3);
        if (!isValidQuantumSingularity(stack)) {
            return 0;
        }
        return (stack.hasTag() ? stack.getTag().getLong(appeng.blockentity.qnb.QuantumBridgeBlockEntity.TAG_FREQUENCY) : 0L);
    }

    public QuantumLinkState getQuantumLinkState() {
        return quantumLinkState;
    }

    public static boolean isValidQuantumSingularity(ItemStack stack) {
        return !stack.isEmpty()
                && stack.hasTag() && stack.getTag().contains(appeng.blockentity.qnb.QuantumBridgeBlockEntity.TAG_FREQUENCY)
                && (stack.hasTag() ? stack.getTag().getLong(appeng.blockentity.qnb.QuantumBridgeBlockEntity.TAG_FREQUENCY) : 0L) > 0;
    }

    private void updateQuantumLink() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        long frequency = getQuantumFrequency();
        if (frequency == 0) {
            releaseQuantumFrequency();
            disconnectQuantumLink(QuantumLinkState.EMPTY);
            return;
        }
        if (!claimQuantumFrequency(serverLevel, frequency)) {
            disconnectQuantumLink(QuantumLinkState.FREQUENCY_OCCUPIED);
            return;
        }
        if (!getMainNode().isReady()) {
            disconnectQuantumLink(QuantumLinkState.SEARCHING);
            return;
        }
        var localNode = getMainNode().getNode();
        if (localNode == null) {
            disconnectQuantumLink(QuantumLinkState.SEARCHING);
            return;
        }
        if (!localNode.getInWorldConnections().isEmpty()) {
            disconnectQuantumLink(QuantumLinkState.WIRED_CONFLICT);
            return;
        }

        var positiveEndpoint = Locatables.quantumNetworkBridges().get(serverLevel, frequency);
        var negativeEndpoint = Locatables.quantumNetworkBridges().get(serverLevel, -frequency);
        if (positiveEndpoint != null && negativeEndpoint != null && positiveEndpoint != negativeEndpoint) {
            disconnectQuantumLink(QuantumLinkState.FREQUENCY_OCCUPIED);
            return;
        }
        var remoteHost = positiveEndpoint != null ? positiveEndpoint : negativeEndpoint;
        if (remoteHost == null) {
            disconnectQuantumLink(QuantumLinkState.REMOTE_MISSING);
            return;
        }
        var remoteNode = remoteHost.getActionableNode();
        if (remoteNode == null || remoteNode == localNode) {
            disconnectQuantumLink(QuantumLinkState.REMOTE_MISSING);
            return;
        }
        if (!remoteNode.isOnline()) {
            disconnectQuantumLink(QuantumLinkState.REMOTE_OFFLINE);
            return;
        }
        if (isQuantumConnectionCurrent(localNode, remoteNode, frequency)) {
            quantumLinkState = localNode.isOnline()
                    ? connectedQuantumLinkState()
                    : QuantumLinkState.REMOTE_OFFLINE;
            return;
        }

        disconnectQuantumLink(QuantumLinkState.SEARCHING);
        try {
            quantumConnection = GridHelper.createConnection(localNode, remoteNode);
            quantumRemoteNode = remoteNode;
            quantumConnectionFrequency = frequency;
            getMainNode().setIdlePowerUsage(ModConfig.IDLE_POWER.get() + QUANTUM_LINK_POWER);
            quantumLinkState = connectedQuantumLinkState();
            getLogic().updatePatterns();
            saveChanges();
        } catch (IllegalStateException exception) {
            disconnectQuantumLink(QuantumLinkState.CONNECTION_ERROR);
        }
    }

    private boolean isQuantumConnectionCurrent(IGridNode localNode, IGridNode remoteNode, long frequency) {
        return quantumConnection != null
                && quantumRemoteNode == remoteNode
                && quantumConnectionFrequency == frequency
                && localNode.getConnections().contains(quantumConnection)
                && remoteNode.getConnections().contains(quantumConnection);
    }

    private QuantumLinkState connectedQuantumLinkState() {
        return formed ? QuantumLinkState.CONNECTED : QuantumLinkState.CONNECTED_BUILD_ONLY;
    }

    private void disconnectQuantumLink(QuantumLinkState nextState) {
        var connection = quantumConnection;
        quantumConnection = null;
        quantumRemoteNode = null;
        quantumConnectionFrequency = 0;
        quantumLinkState = nextState;
        var mainNode = getMainNode();
        if (mainNode.getNode() != null) {
            mainNode.setIdlePowerUsage(ModConfig.IDLE_POWER.get());
        }
        if (connection != null) {
            try {
                connection.destroy();
            } catch (RuntimeException ignored) {
                // AE2 may already have invalidated the connection while one endpoint was unloading.
            }
            getLogic().updatePatterns();
        }
    }

    private void clearQuantumLinkForRemoval(QuantumLinkState nextState) {
        quantumConnection = null;
        quantumRemoteNode = null;
        quantumConnectionFrequency = 0;
        quantumLinkState = nextState;
    }

    private boolean claimQuantumFrequency(net.minecraft.server.level.ServerLevel serverLevel, long frequency) {
        if (claimedQuantumFrequency != 0 && claimedQuantumFrequency != frequency) {
            releaseQuantumFrequency();
        }
        if (!EntangledQuantumFrequencyRegistry.claim(serverLevel, worldPosition, frequency)) {
            return false;
        }
        claimedQuantumFrequency = frequency;
        return true;
    }

    private void releaseQuantumFrequency() {
        if (claimedQuantumFrequency == 0 || level == null || level.getServer() == null) {
            claimedQuantumFrequency = 0;
            return;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            EntangledQuantumFrequencyRegistry.release(serverLevel, worldPosition, claimedQuantumFrequency);
        }
        claimedQuantumFrequency = 0;
    }

    public boolean isDeconstructEnabled() {
        return deconstructEnabled;
    }

    public MatterJobState getDeconstructJobState() {
        return deconstructJobState;
    }

    public int getDeconstructJobProgress() {
        return scaledJobProgress(deconstructJobProgress);
    }

    public long getDeconstructJobProcessed() {
        return deconstructJobProcessed;
    }

    public boolean isRewriteEnabled() {
        return rewriteEnabled;
    }

    public MatterJobState getRewriteJobState() {
        return rewriteJobState;
    }

    public int getRewriteJobProgress() {
        return scaledJobProgress(rewriteJobProgress);
    }

    public long getRewriteJobProcessed() {
        return rewriteJobProcessed;
    }

    private int scaledJobProgress(int progress) {
        int cycleTicks = getMatterCycleTicks();
        return cycleTicks <= 0 ? 0 : Math.min(1000, progress * 1000 / cycleTicks);
    }

    public long getDeconstructTarget() {
        return deconstructTarget;
    }

    public long getRewriteTarget() {
        return rewriteTarget;
    }

    public RewriteOutputMode getRewriteOutputMode() {
        return rewriteOutputMode;
    }

    public void cycleRewriteOutputMode() {
        rewriteOutputMode = rewriteOutputMode.next();
        saveChanges();
    }

    public int getInstalledSpeedCards() {
        return matterUpgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
    }

    public int getMatterCycleTicks() {
        return ModConfig.matterCycleTicks(getInstalledSpeedCards());
    }

    public int getMatterParallelOperations() {
        return ModConfig.matterParallelOperations(getInstalledSpeedCards());
    }

    public long getDeconstructionEntropyPerItem() {
        var value = MatterSequenceRegistry.deconstructionOf(matterInventory.getStackInSlot(0));
        return value == null ? 0 : entropyPerItem(value, 64);
    }

    public long getRewriteEntropyPerItem() {
        var value = MatterSequenceRegistry.rewriteCostOf(matterInventory.getStackInSlot(1));
        return value == null ? 0 : entropyPerItem(value, 16);
    }

    public long getDeconstructionCoolingSeconds() {
        return estimatedCoolingSeconds(getDeconstructionEntropyPerItem());
    }

    public long getRewriteCoolingSeconds() {
        return estimatedCoolingSeconds(getRewriteEntropyPerItem());
    }

    private long estimatedCoolingSeconds(long entropyPerItem) {
        if (entropyPerItem <= 0) {
            return -1;
        }
        long capacity = getMatterEntropyCapacity();
        if (entropyPerItem > capacity) {
            return Long.MAX_VALUE;
        }
        long availableCapacity = Math.max(0, capacity - Math.min(entropy, capacity));
        if (entropyPerItem <= availableCapacity) {
            return 0;
        }
        long requiredCooling = entropyPerItem - availableCapacity;
        long coolingPerSecond = getMatterEntropyCoolingPerSecond();
        return requiredCooling <= coolingPerSecond
                ? 1
                : 1 + (requiredCooling - 1) / coolingPerSecond;
    }

    public void setDeconstructTarget(long target) {
        deconstructTarget = sanitizeJobTarget(target);
        if (deconstructEnabled && deconstructTarget > 0
                && deconstructJobProcessed >= deconstructTarget) {
            stopDeconstruction(MatterJobState.TARGET_REACHED);
        }
        saveChanges();
    }

    public void setRewriteTarget(long target) {
        rewriteTarget = sanitizeJobTarget(target);
        if (rewriteEnabled && rewriteTarget > 0 && rewriteJobProcessed >= rewriteTarget) {
            stopRewrite(MatterJobState.TARGET_REACHED);
        }
        saveChanges();
    }

    private static long sanitizeJobTarget(long target) {
        return Math.max(0, Math.min(MAX_JOB_TARGET, target));
    }

    public void toggleDeconstruction(ServerPlayer player) {
        if (deconstructEnabled) {
            stopDeconstruction(MatterJobState.STOPPED);
            return;
        }
        var input = matterInventory.getStackInSlot(0);
        if (input.isEmpty()) {
            deconstructJobState = MatterJobState.INPUT_EMPTY;
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.sequence_input_empty"), false);
            saveChanges();
            return;
        }
        if (MatterSequenceRegistry.deconstructionOf(input) == null) {
            deconstructJobState = MatterJobState.UNSUPPORTED;
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.sequence_unsupported"), false);
            saveChanges();
            return;
        }
        deconstructTemplate = input.copyWithCount(1);
        deconstructEnabled = true;
        deconstructJobState = isOperational() ? MatterJobState.RUNNING : MatterJobState.WAITING_NETWORK;
        deconstructJobProgress = 0;
        deconstructJobProcessed = 0;
        onMatterJobToggleChanged();
    }

    public void toggleRewrite(ServerPlayer player) {
        if (rewriteEnabled) {
            stopRewrite(MatterJobState.STOPPED);
            return;
        }
        var blueprint = matterInventory.getStackInSlot(1);
        if (blueprint.isEmpty()) {
            rewriteJobState = MatterJobState.BLUEPRINT_EMPTY;
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.sequence_blueprint_empty"), false);
            saveChanges();
            return;
        }
        if (MatterSequenceRegistry.rewriteCostOf(blueprint) == null) {
            rewriteJobState = MatterJobState.UNSUPPORTED;
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.sequence_unsupported"), false);
            saveChanges();
            return;
        }
        rewriteTemplate = blueprint.copyWithCount(1);
        rewriteEnabled = true;
        rewriteJobState = isOperational() ? MatterJobState.RUNNING : MatterJobState.WAITING_NETWORK;
        rewriteJobProgress = 0;
        rewriteJobProcessed = 0;
        onMatterJobToggleChanged();
    }

    private void stopDeconstruction(MatterJobState state) {
        deconstructEnabled = false;
        deconstructJobState = state;
        deconstructJobProgress = 0;
        onMatterJobToggleChanged();
    }

    private void stopRewrite(MatterJobState state) {
        rewriteEnabled = false;
        rewriteJobState = state;
        rewriteJobProgress = 0;
        onMatterJobToggleChanged();
    }

    private void onMatterJobToggleChanged() {
        saveChanges();
        if (level != null && !level.isClientSide()) {
            syncVisualActivity(level.getGameTime(), true);
        }
    }

    private void processMatterJobs() {
        processDeconstructionJob();
        processRewriteJob();
    }

    private void processDeconstructionJob() {
        if (!deconstructEnabled) {
            return;
        }
        var marker = matterInventory.getStackInSlot(0);
        if (marker.isEmpty()) {
            stopDeconstruction(MatterJobState.INPUT_EMPTY);
            return;
        }
        if (!ItemStack.isSameItemSameTags(marker, deconstructTemplate)) {
            if (MatterSequenceRegistry.deconstructionOf(marker) == null) {
                stopDeconstruction(MatterJobState.UNSUPPORTED);
                return;
            }
            deconstructTemplate = marker.copyWithCount(1);
            deconstructJobProgress = 0;
            deconstructJobProcessed = 0;
            setDeconstructJobState(isOperational()
                    ? MatterJobState.RUNNING
                    : MatterJobState.WAITING_NETWORK);
        }
        if (deconstructTarget > 0 && deconstructJobProcessed >= deconstructTarget) {
            stopDeconstruction(MatterJobState.TARGET_REACHED);
            return;
        }
        if (!isOperational()) {
            setDeconstructJobState(MatterJobState.WAITING_NETWORK);
            return;
        }
        if (++deconstructJobProgress < getMatterCycleTicks()) {
            return;
        }
        deconstructJobProgress = 0;

        int completed = 0;
        int parallelOperations = batchLimit(deconstructTarget, deconstructJobProcessed);
        MatterJobState result = MatterJobState.RUNNING;
        while (completed < parallelOperations) {
            result = processOneDeconstruction();
            if (result != MatterJobState.RUNNING) {
                break;
            }
            completed++;
        }
        if (completed > 0) {
            deconstructJobProcessed = Math.min(
                    MAX_JOB_TARGET, deconstructJobProcessed + completed);
            finishMatterOperations();
        }
        if (deconstructTarget > 0 && deconstructJobProcessed >= deconstructTarget) {
            stopDeconstruction(MatterJobState.TARGET_REACHED);
        } else if (result == MatterJobState.RUNNING) {
            setDeconstructJobState(MatterJobState.RUNNING);
        } else if (isRetryableMatterState(result, false)) {
            setDeconstructJobState(result);
        } else {
            stopDeconstruction(result);
        }
    }

    private void processRewriteJob() {
        if (!rewriteEnabled) {
            return;
        }
        if (rewriteTarget > 0 && rewriteJobProcessed >= rewriteTarget) {
            stopRewrite(MatterJobState.TARGET_REACHED);
            return;
        }
        if (!isOperational()) {
            setRewriteJobState(MatterJobState.WAITING_NETWORK);
            return;
        }
        if (++rewriteJobProgress < getMatterCycleTicks()) {
            return;
        }
        rewriteJobProgress = 0;

        int completed = 0;
        int parallelOperations = batchLimit(rewriteTarget, rewriteJobProcessed);
        MatterJobState result = MatterJobState.RUNNING;
        while (completed < parallelOperations) {
            result = processOneRewrite();
            if (result != MatterJobState.RUNNING) {
                break;
            }
            completed++;
        }
        if (completed > 0) {
            rewriteJobProcessed = Math.min(MAX_JOB_TARGET, rewriteJobProcessed + completed);
            finishMatterOperations();
        }
        if (rewriteTarget > 0 && rewriteJobProcessed >= rewriteTarget) {
            stopRewrite(MatterJobState.TARGET_REACHED);
        } else if (result == MatterJobState.RUNNING
                && rewriteOutputMode == RewriteOutputMode.OUTPUT_SLOT
                && !canAcceptMatterOutput(rewriteTemplate)) {
            stopRewrite(MatterJobState.OUTPUT_FULL);
        } else if (result == MatterJobState.RUNNING) {
            setRewriteJobState(MatterJobState.RUNNING);
        } else if (isRetryableMatterState(result, true)) {
            setRewriteJobState(result);
        } else {
            stopRewrite(result);
        }
    }

    private int batchLimit(long target, long processed) {
        int parallelOperations = getMatterParallelOperations();
        if (target <= 0) {
            return parallelOperations;
        }
        return (int) Math.min(parallelOperations, Math.max(0, target - processed));
    }

    private static boolean isRetryableMatterState(MatterJobState state, boolean rewrite) {
        return state == MatterJobState.WAITING_NETWORK
                || state == MatterJobState.WAITING_POWER
                || state == MatterJobState.COOLING
                || state == MatterJobState.INPUT_EMPTY
                || rewrite && state == MatterJobState.INSUFFICIENT_SEQUENCE;
    }

    private MatterJobState processOneDeconstruction() {
        var value = MatterSequenceRegistry.deconstructionOf(deconstructTemplate);
        if (value == null) {
            return MatterJobState.UNSUPPORTED;
        }
        if (capacityFor(metalSequence, value.metal()) < 1
                || capacityFor(mineralSequence, value.mineral()) < 1
                || capacityFor(crystalSequence, value.crystal()) < 1
                || capacityFor(organicSequence, value.organic()) < 1) {
            return MatterJobState.SEQUENCE_STORAGE_FULL;
        }
        long entropyPerItem = entropyPerItem(value, 64);
        long entropyCapacity = getMatterEntropyCapacity();
        if (entropyPerItem > entropyCapacity) {
            return MatterJobState.ENTROPY_COST_TOO_HIGH;
        }
        if (entropy > entropyCapacity - entropyPerItem) {
            return MatterJobState.COOLING;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return MatterJobState.WAITING_NETWORK;
        }
        var storage = grid.getStorageService().getInventory();
        var key = AEItemKey.of(deconstructTemplate);
        if (key == null) {
            return MatterJobState.UNSUPPORTED;
        }
        if (storage.extract(key, 1, Actionable.SIMULATE, actionSource) < 1) {
            return MatterJobState.INPUT_EMPTY;
        }
        double powerPerItem = Math.max(256, value.total() * 2.0);
        if (availablePowerOperations(powerPerItem) < 1) {
            return MatterJobState.WAITING_POWER;
        }

        if (storage.extract(key, 1, Actionable.MODULATE, actionSource) < 1) {
            return MatterJobState.INPUT_EMPTY;
        }
        if (!consumePower(powerPerItem)) {
            refundDeconstructionInput(storage);
            return MatterJobState.WAITING_POWER;
        }

        metalSequence += value.metal();
        mineralSequence += value.mineral();
        crystalSequence += value.crystal();
        organicSequence += value.organic();
        entropy += entropyPerItem;
        return MatterJobState.RUNNING;
    }

    private void refundDeconstructionInput(MEStorage storage) {
        var key = AEItemKey.of(deconstructTemplate);
        if (key != null) {
            storage.insert(key, 1, Actionable.MODULATE, actionSource);
        }
    }

    private MatterJobState processOneRewrite() {
        var blueprint = matterInventory.getStackInSlot(1);
        if (blueprint.isEmpty()
                || !ItemStack.isSameItemSameTags(blueprint, rewriteTemplate)) {
            return MatterJobState.BLUEPRINT_CHANGED;
        }
        var value = MatterSequenceRegistry.rewriteCostOf(rewriteTemplate);
        if (value == null) {
            return MatterJobState.UNSUPPORTED;
        }
        if (affordableOperations(value) < 1) {
            return MatterJobState.INSUFFICIENT_SEQUENCE;
        }
        long entropyPerItem = entropyPerItem(value, 16);
        long entropyCapacity = getMatterEntropyCapacity();
        if (entropyPerItem > entropyCapacity) {
            return MatterJobState.ENTROPY_COST_TOO_HIGH;
        }
        if (entropy > entropyCapacity - entropyPerItem) {
            return MatterJobState.COOLING;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return MatterJobState.WAITING_NETWORK;
        }
        var storage = grid.getStorageService().getInventory();
        var key = AEItemKey.of(rewriteTemplate);
        if (key == null) {
            return MatterJobState.UNSUPPORTED;
        }
        boolean outputAvailable = rewriteOutputMode == RewriteOutputMode.NETWORK
                ? storage.insert(key, 1, Actionable.SIMULATE, actionSource) >= 1
                : canAcceptMatterOutput(rewriteTemplate);
        if (!outputAvailable) {
            return MatterJobState.OUTPUT_FULL;
        }
        double powerPerItem = Math.max(1024, value.total() * 8.0);
        if (availablePowerOperations(powerPerItem) < 1 || !consumePower(powerPerItem)) {
            return MatterJobState.WAITING_POWER;
        }

        metalSequence -= value.metal();
        mineralSequence -= value.mineral();
        crystalSequence -= value.crystal();
        organicSequence -= value.organic();
        entropy += entropyPerItem;

        boolean stored = rewriteOutputMode == RewriteOutputMode.NETWORK
                ? storage.insert(key, 1, Actionable.MODULATE, actionSource) >= 1
                : insertMatterOutput(rewriteTemplate);
        if (!stored) {
            metalSequence += value.metal();
            mineralSequence += value.mineral();
            crystalSequence += value.crystal();
            organicSequence += value.organic();
            entropy -= entropyPerItem;
            return MatterJobState.OUTPUT_FULL;
        }
        return MatterJobState.RUNNING;
    }

    private boolean canAcceptMatterOutput(ItemStack stack) {
        var output = matterInventory.getStackInSlot(2);
        return output.isEmpty()
                || ItemStack.isSameItemSameTags(output, stack)
                && output.getCount() < output.getMaxStackSize();
    }

    private boolean insertMatterOutput(ItemStack stack) {
        if (!canAcceptMatterOutput(stack)) {
            return false;
        }
        var output = matterInventory.getStackInSlot(2);
        if (output.isEmpty()) {
            matterInventory.setItemDirect(2, stack.copyWithCount(1));
        } else {
            output.grow(1);
        }
        return true;
    }

    private void setDeconstructJobState(MatterJobState state) {
        if (deconstructJobState != state) {
            deconstructJobState = state;
            saveChanges();
        }
    }

    private void setRewriteJobState(MatterJobState state) {
        if (rewriteJobState != state) {
            rewriteJobState = state;
            saveChanges();
        }
    }

    private long availablePowerOperations(double powerPerItem) {
        var grid = getMainNode().getGrid();
        if (grid == null || powerPerItem <= 0) {
            return 0;
        }
        double available = grid.getEnergyService().extractAEPower(
                powerPerItem * 64.0, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return Math.max(0, (long) Math.floor((available + 0.001) / powerPerItem));
    }

    private boolean consumePower(double amount) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        var energy = grid.getEnergyService();
        if (energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG) < amount - 0.01) {
            return false;
        }
        return energy.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - 0.01;
    }

    private long affordableOperations(MatterValue value) {
        long amount = Long.MAX_VALUE;
        if (value.metal() > 0) amount = Math.min(amount, metalSequence / value.metal());
        if (value.mineral() > 0) amount = Math.min(amount, mineralSequence / value.mineral());
        if (value.crystal() > 0) amount = Math.min(amount, crystalSequence / value.crystal());
        if (value.organic() > 0) amount = Math.min(amount, organicSequence / value.organic());
        return amount == Long.MAX_VALUE ? 0 : amount;
    }

    private long capacityFor(long stored, long perItem) {
        if (perItem <= 0) {
            return Long.MAX_VALUE;
        }
        long capacity = getMatterSequenceCapacity();
        return stored >= capacity ? 0 : (capacity - stored) / perItem;
    }

    private static long entropyPerItem(MatterValue value, long divisor) {
        return Math.max(1, value.total() / divisor);
    }

    private void finishMatterOperations() {
        visualSuccessTick = level.getGameTime();
        saveChanges();
        markForUpdate();
    }

    public int getFieldColor() {
        return fieldColor;
    }

    public int getCoreColor() {
        return coreColor;
    }

    public int getPrimaryRingColor() {
        return primaryRingColor;
    }

    public int getSecondaryRingColor() {
        return secondaryRingColor;
    }

    public int getLatticeColor() {
        return latticeColor;
    }

    public int getVisualColor(int target) {
        return switch (target) {
            case 0 -> fieldColor;
            case 1 -> coreColor;
            case 2 -> primaryRingColor;
            case 3 -> secondaryRingColor;
            case 4 -> latticeColor;
            default -> 0xFFFFFF;
        };
    }

    public void adjustVisualColor(int target, int channel, int delta) {
        if (target < 0 || target > 4 || channel < 0 || channel > 2 || delta == 0) {
            return;
        }
        int color = getVisualColor(target);
        int shift = (2 - channel) * 8;
        int component = Math.max(0, Math.min(255, (color >> shift & 0xFF) + delta));
        setVisualColor(target, color & ~(0xFF << shift) | component << shift);
    }

    public void resetVisualColors() {
        fieldColor = DEFAULT_FIELD_COLOR;
        coreColor = DEFAULT_CORE_COLOR;
        primaryRingColor = DEFAULT_PRIMARY_RING_COLOR;
        secondaryRingColor = DEFAULT_SECONDARY_RING_COLOR;
        latticeColor = DEFAULT_LATTICE_COLOR;
        onVisualColorsChanged();
    }

    private void setVisualColor(int target, int color) {
        color &= 0xFFFFFF;
        switch (target) {
            case 0 -> fieldColor = color;
            case 1 -> coreColor = color;
            case 2 -> primaryRingColor = color;
            case 3 -> secondaryRingColor = color;
            case 4 -> latticeColor = color;
            default -> {
                return;
            }
        }
        onVisualColorsChanged();
    }

    private void onVisualColorsChanged() {
        saveChanges();
        markForUpdate();
    }

    private boolean isRecentPipelineActivity() {
        return level != null && pipelineActivityTick != Long.MIN_VALUE
                && level.getGameTime() - pipelineActivityTick <= 1;
    }

    public float sampleClientVisualAngle(float partialTick) {
        if (level == null) {
            return clientVisualAngle;
        }
        double sample = level.getGameTime() + partialTick;
        if (Double.isNaN(clientVisualSample)) {
            clientVisualSample = sample;
        } else {
            double elapsed = sample - clientVisualSample;
            if (elapsed >= 0.0 && elapsed <= 5.0) {
                float speed = switch (clientVisualMode) {
                    case 1 -> 2.6F;
                    case 2 -> 4.6F;
                    case 3 -> 7.2F;
                    case 4 -> 4.5F;
                    default -> 0.8F;
                };
                clientVisualAngle += (float) elapsed * speed;
                if (clientVisualAngle >= 3600.0F) {
                    clientVisualAngle %= 360.0F;
                }
            }
            clientVisualSample = sample;
        }
        return clientVisualAngle;
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == CROWN_ACTIVITY_EVENT) {
            if (level != null && level.isClientSide()) {
                crownAnimation.receive((type & 1) != 0, (type & 2) != 0, level.getGameTime());
            }
            return true;
        }
        if (id == VISUAL_ACTIVITY_EVENT) {
            clientVisualMode = Math.max(0, Math.min(4, type));
            return true;
        }
        return super.triggerEvent(id, type);
    }

    public int getClientVisualMode() {
        return clientVisualMode;
    }

    public SequenceCrownAnimation sampleCrownAnimation(float partialTick) {
        if (level != null) crownAnimation.sample(level.getGameTime() + (double) partialTick);
        return crownAnimation;
    }

    @Override
    public boolean isVisibleInTerminal() {
        return formed && super.isVisibleInTerminal();
    }

    public void refreshStructure() {
        if (level == null || level.isClientSide()) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var newLayout = MolecularCenterStructure.detectLayout(level, worldPosition, facing);
        boolean newFormed = newLayout.isFormed();
        boolean knownLayoutChanged = newFormed && lastKnownStructureLayout != newLayout;
        if (newFormed) {
            lastKnownStructureLayout = newLayout;
        }
        boolean anchorChanged = false;
        if (newFormed) {
            var newAnchor = controllerAnchorFor(newLayout);
            anchorChanged = controllerAnchor != newAnchor;
            controllerAnchor = newAnchor;
        }
        boolean layoutChanged = structureLayout != newLayout;
        if (layoutChanged && formed) {
            syncShellConnections(false);
        }
        structureLayout = newLayout;
        boolean stateChanged = anchorChanged || knownLayoutChanged;
        if (newLayout == MolecularCenterStructure.StructureLayout.CURRENT
                && legacyStructureUpdateDismissed) {
            legacyStructureUpdateDismissed = false;
            stateChanged = true;
        }
        if (newFormed != formed) {
            formed = newFormed;
            level.setBlock(worldPosition, getBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, formed), 3);
            onGridConnectableSidesChanged();
            syncShellConnections(newFormed);
            getLogic().updatePatterns();
            stateChanged = true;
        }
        if (stateChanged) {
            saveChanges();
        }
        if (layoutChanged || anchorChanged) {
            if (newFormed) {
                syncShellConnections(true);
            }
            markForUpdate();
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.update(serverLevel, worldPosition, formed);
        }
        MultiblockChunkLoading.maintain(this);
    }

    private void unregisterSpawnProtection() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.unregister(serverLevel, worldPosition);
        }
    }

    public Set<ChunkPos> getChunkLoadingChunks() {
        var result = new HashSet<ChunkPos>();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (formed) result.addAll(MolecularCenterStructure.chunkFootprint(worldPosition, facing, structureLayout, structureLayout));
        if (building || dismantling || structureUpdating) {
            var origin = getConstructionOriginLayout();
            result.addAll(MolecularCenterStructure.chunkFootprint(worldPosition, facing, origin, origin));
            if (building || structureUpdating || dismantleIncludesUpgrade) {
                result.addAll(MolecularCenterStructure.chunkFootprint(worldPosition, facing,
                        MolecularCenterStructure.StructureLayout.CURRENT, origin));
            }
            if (dismantlePlan != null) result.addAll(dismantlePlan.remainingChunks());
        }
        return result;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (building || dismantling || structureUpdating) MultiblockChunkLoading.maintain(this);
        long gameTime = level.getGameTime();
        processWorkQueue();
        if (isRemoved()) {
            return;
        }
        processReusableBatch(gameTime);
        flushBufferedOutputs(gameTime);
        autoCrafter.tick(gameTime);
        flushLegacyDeconstructRefund();
        processMatterJobs();
        syncVisualActivity(gameTime, false);
        if (structureCheckTick != gameTime && gameTime % 20 == 0) {
            structureCheckTick = gameTime;
            refreshStructure();
            updateQuantumLink();
            if (entropy > 0) {
                long coolingPerSecond = getMatterEntropyCoolingPerSecond();
                entropy = entropy <= coolingPerSecond ? 0 : entropy - coolingPerSecond;
                saveChanges();
            }
        }
    }

    private void flushLegacyDeconstructRefund() {
        if (legacyDeconstructRefund.isEmpty()) {
            return;
        }
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(legacyDeconstructRefund);
        if (grid == null || key == null) {
            return;
        }
        long inserted = grid.getStorageService().getInventory().insert(
                key, legacyDeconstructRefund.getCount(), Actionable.MODULATE, actionSource);
        if (inserted > 0) {
            legacyDeconstructRefund.shrink((int) Math.min(inserted, legacyDeconstructRefund.getCount()));
            if (legacyDeconstructRefund.isEmpty()) {
                legacyDeconstructRefund = ItemStack.EMPTY;
            }
            saveChanges();
        }
    }

    private void syncVisualActivity(long gameTime, boolean force) {
        boolean working = (deconstructEnabled && deconstructJobState == MatterJobState.RUNNING)
                || (rewriteEnabled && rewriteJobState == MatterJobState.RUNNING) || isRecentPipelineActivity();
        boolean completed = visualSuccessTick != Long.MIN_VALUE && gameTime - visualSuccessTick <= 1
                && (visualPulseTick == Long.MIN_VALUE || gameTime - visualPulseTick >= 40);
        int work = working ? 1 : 0;
        if (force || work != crownWorkingAnnounced || completed || working && gameTime % 20 == 0) {
            crownWorkingAnnounced = work;
            if (completed) visualPulseTick = gameTime;
            level.blockEvent(worldPosition, getBlockState().getBlock(),
                    CROWN_ACTIVITY_EVENT, work | (completed ? 2 : 0));
        }
        int mode = (deconstructEnabled ? 1 : 0) | (rewriteEnabled ? 2 : 0);
        if (mode == 0 && isRecentPipelineActivity()) {
            mode = 4;
        }
        if (force || mode != visualModeAnnounced || mode != 0 && gameTime % 20 == 0) {
            visualModeAnnounced = mode;
            level.blockEvent(worldPosition, getBlockState().getBlock(),
                    VISUAL_ACTIVITY_EVENT, mode);
        }
    }

    public int getBuildProgress() {
        return workCursor;
    }

    public int getBuildTotal() {
        return workTotal;
    }

    public boolean isBuilding() {
        return building;
    }

    public boolean isDismantling() {
        return dismantling;
    }

    public boolean hasLegacyStructure() {
        return structureUpdating
                || formed && structureLayout != MolecularCenterStructure.StructureLayout.CURRENT;
    }

    public MolecularCenterStructure.StructureLayout getStructureLayout() {
        return structureLayout;
    }

    public MolecularCenterStructure.StructureLayout getControllerAnchorLayout() {
        return controllerAnchor == ControllerAnchor.LOWERED ? MolecularCenterStructure.StructureLayout.CURRENT
                : MolecularCenterStructure.StructureLayout.LEGACY_1_3_9;
    }

    private static ControllerAnchor controllerAnchorFor(MolecularCenterStructure.StructureLayout layout) {
        return layout == MolecularCenterStructure.StructureLayout.LEGACY_1_3_9 ? ControllerAnchor.RIM : ControllerAnchor.LOWERED;
    }

    private enum ControllerAnchor {
        RIM,
        LOWERED
    }

    public MolecularCenterStructure.StructureLayout getConstructionOriginLayout() {
        if (structureUpdating) {
            return structureUpdateSourceLayout;
        }
        if (dismantling) {
            return dismantleLayout;
        }
        return structureLayout.isFormed() ? structureLayout : getControllerAnchorLayout();
    }

    /** Reinterpret an unused pre-migration controller in place; never move world blocks. */
    public boolean normalizeIsolatedControllerAnchor() {
        if (level == null || level.isClientSide() || controllerAnchor == ControllerAnchor.LOWERED || formed
                || building || dismantling || structureUpdating) {
            return false;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (MolecularCenterStructure.detectLayout(level, worldPosition, facing).isFormed()
                || !MolecularCenterStructure.isHistoricalAreaEmpty(level, worldPosition, facing,
                        getControllerAnchorLayout())) {
            return false;
        }
        controllerAnchor = ControllerAnchor.LOWERED;
        structureLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
        lastKnownStructureLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
        saveChanges();
        markForUpdate();
        return true;
    }

    public boolean isLegacyStructureUpdateDismissed() {
        return legacyStructureUpdateDismissed;
    }

    public void startBuild(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        normalizeIsolatedControllerAnchor();
        if (structureUpdating) {
            startStructureUpdate(player);
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        structureLayout = MolecularCenterStructure.detectLayout(level, worldPosition, facing);
        if (structureLayout.isFormed()
                && structureLayout != MolecularCenterStructure.StructureLayout.CURRENT) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_requires_confirmation"), false);
            return;
        }
        if (controllerAnchor != ControllerAnchor.LOWERED && !structureLayout.isFormed()) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.controller_anchor_unknown"), false);
            return;
        }
        if (!MolecularCenterStructure.isWithinBuildHeight(level, worldPosition)) {
            showBuildHeightFailure(player);
            return;
        }
        if (!MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.build_area_unavailable"), false);
            return;
        }
        var missingParts = findMissingOnlyParts(facing);
        if (missingParts != null && missingParts.isEmpty()) {
            building = false;
            dismantling = false;
            repairOnlyBuild = false;
            structureUpdating = false;
            structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
            buildQueueInitialized = false;
            buildWorkParts = List.of();
            workCursor = 0;
            workTotal = 0;
            workConflicts = 0;
            workOwner = player.getUUID();
            refreshStructure();
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.build_already_complete"), false);
            saveChanges();
            return;
        }
        building = true;
        dismantling = false;
        repairOnlyBuild = missingParts != null;
        lastKnownStructureLayout = MolecularCenterStructure.StructureLayout.CURRENT;
        structureUpdating = false;
        structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
        buildQueueInitialized = true;
        buildWorkParts = repairOnlyBuild ? missingParts : MolecularCenterStructure.workParts();
        workCursor = 0;
        workTotal = buildWorkParts.size();
        workConflicts = 0;
        workOwner = player.getUUID();
        player.displayClientMessage(Component.translatable(
                repairOnlyBuild
                        ? "message.molecularmanipulator.build_repair_missing"
                        : "message.molecularmanipulator.build_full_calibration",
                workTotal), false);
        saveChanges();
    }

    public void startStructureUpdate(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        normalizeIsolatedControllerAnchor();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var detected = structureUpdating ? structureUpdateSourceLayout
                : MolecularCenterStructure.detectLayout(level, worldPosition, facing);
        if (!structureUpdating) {
            structureLayout = detected;
        }
        if (!detected.isFormed()
                || detected == MolecularCenterStructure.StructureLayout.CURRENT
                || !MolecularCenterStructure.areUpdateChunksLoaded(level, worldPosition, facing, detected)
                || !MolecularCenterStructure.isWithinUpdateHeight(level, worldPosition, detected)) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_unavailable"), false);
            return;
        }

        if (!canRelocateController(player, facing, detected)) {
            return;
        }
        controllerAnchor = controllerAnchorFor(detected);
        building = true;
        dismantling = false;
        repairOnlyBuild = false;
        structureUpdating = true;
        structureUpdateSourceLayout = detected;
        buildQueueInitialized = true;
        buildWorkParts = MolecularCenterStructure.updateWorkParts(detected);
        workCursor = 0;
        workTotal = buildWorkParts.size();
        workConflicts = 0;
        workOwner = player.getUUID();
        formed = false;
        legacyStructureUpdateDismissed = false;
        deactivateStructureForWork();
        player.displayClientMessage(Component.translatable(
                "message.molecularmanipulator.structure_update_started"), false);
        saveChanges();
    }

    private void rollbackStructureUpdate(BlockPos visualCenterPos, BlockState visualCenterState,
            BlockPos upperCorePos, BlockState upperCoreState) {
        try {
            level.setBlock(upperCorePos, upperCoreState, 3);
            if (!level.getBlockState(upperCorePos).equals(upperCoreState)) {
                com.atir.molecularmanipulator.MolecularManipulator.LOGGER.error(
                        "Failed to restore the molecular center upper stabilizer at {}", upperCorePos);
            }
        } catch (RuntimeException exception) {
            com.atir.molecularmanipulator.MolecularManipulator.LOGGER.error(
                    "Exception while restoring the molecular center upper stabilizer at {}",
                    upperCorePos, exception);
        }
        try {
            level.setBlock(visualCenterPos, visualCenterState, 3);
            if (!level.getBlockState(visualCenterPos).equals(visualCenterState)) {
                com.atir.molecularmanipulator.MolecularManipulator.LOGGER.error(
                        "Failed to restore the molecular center visual core at {}", visualCenterPos);
            }
        } catch (RuntimeException exception) {
            com.atir.molecularmanipulator.MolecularManipulator.LOGGER.error(
                    "Exception while restoring the molecular center visual core at {}",
                    visualCenterPos, exception);
        }
        refreshStructure();
    }

    public void keepLegacyStructure(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        structureLayout = MolecularCenterStructure.detectLayout(level, worldPosition, facing);
        if (!structureLayout.isFormed()
                || structureLayout == MolecularCenterStructure.StructureLayout.CURRENT) {
            return;
        }
        legacyStructureUpdateDismissed = true;
        player.displayClientMessage(Component.translatable(
                "message.molecularmanipulator.legacy_structure_retained"), false);
        saveChanges();
    }

    public void startDismantle(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var detected = MolecularCenterStructure.detectLayout(level, worldPosition, facing);
        var source = structureUpdating ? structureUpdateSourceLayout
                : detected.isFormed() ? detected : lastKnownStructureLayout;
        if (!source.isFormed()) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.dismantle_layout_unknown"), false);
            return;
        }
        var plan = MolecularCenterStructure.createDismantlePlan(level, worldPosition, facing, source, structureUpdating);
        if (plan == null) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.build_area_unavailable"), false);
            return;
        }
        if (detected.isFormed()) {
            structureLayout = detected;
            lastKnownStructureLayout = detected;
        }
        dismantleLayout = source;
        dismantleIncludesUpgrade = structureUpdating;
        dismantlePlan = plan;
        dismantling = !plan.isComplete();
        building = false;
        repairOnlyBuild = false;
        structureUpdating = false;
        structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
        buildQueueInitialized = false;
        buildWorkParts = List.of();
        formed = false;
        unregisterSpawnProtection();
        level.setBlock(worldPosition, getBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3);
        onGridConnectableSidesChanged();
        syncShellConnections(false);
        updateQuantumLink();
        workCursor = plan.completed();
        workTotal = plan.total();
        workConflicts = 0;
        workOwner = player.getUUID();
        getLogic().updatePatterns();
        saveChanges();
    }

    private void deactivateStructureForWork() {
        formed = false;
        unregisterSpawnProtection();
        level.setBlock(worldPosition, getBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3);
        onGridConnectableSidesChanged();
        syncShellConnections(false);
        updateQuantumLink();
        getLogic().updatePatterns();
    }

    private List<MolecularCenterStructure.Part> findMissingOnlyParts(Direction facing) {
        if (level == null) {
            return null;
        }
        var missing = new ArrayList<MolecularCenterStructure.Part>();
        for (var part : MolecularCenterStructure.workParts()) {
            if (MolecularCenterStructure.isController(part)) {
                continue;
            }
            var state = level.getBlockState(MolecularCenterStructure.worldPos(worldPosition, facing, part));
            if (part.partType() == MolecularCenterStructure.PartType.AIR) {
                continue;
            }
            if (state.is(MolecularCenterStructure.partState(part.partType()).getBlock())) {
                continue;
            }
            if (state.isAir() || state.canBeReplaced()) {
                missing.add(part);
                continue;
            }
            return null;
        }
        return List.copyOf(missing);
    }

    private void restoreBuildQueue() {
        if (!building || level == null || level.isClientSide() || buildQueueInitialized) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!(structureUpdating
                ? MolecularCenterStructure.areUpdateChunksLoaded(level, worldPosition, facing,
                        structureUpdateSourceLayout)
                : MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing,
                        getConstructionOriginLayout()))) {
            return;
        }
        if (structureUpdating) {
            buildWorkParts = MolecularCenterStructure.updateWorkParts(structureUpdateSourceLayout);
            if (buildWorkParts.isEmpty()) {
                building = false;
                structureUpdating = false;
                structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
                workCursor = 0;
                workTotal = 0;
                saveChanges();
                return;
            }
            workCursor = Math.max(0, Math.min(workCursor, buildWorkParts.size()));
        } else if (repairOnlyBuild) {
            var remaining = findMissingOnlyParts(facing);
            if (remaining != null) {
                buildWorkParts = remaining;
                workCursor = 0;
            } else {
                repairOnlyBuild = false;
                buildWorkParts = MolecularCenterStructure.workParts();
                workCursor = 0;
            }
        } else {
            buildWorkParts = MolecularCenterStructure.workParts();
            workCursor = Math.max(0, Math.min(workCursor, buildWorkParts.size()));
        }
        workTotal = buildWorkParts.size();
        buildQueueInitialized = true;
    }

    public void showPreview(ServerPlayer player) {
        if (level == null) {
            return;
        }
        normalizeIsolatedControllerAnchor();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        player.displayClientMessage(Component.translatable("message.molecularmanipulator.preview_legend"), false);
        var originLayout = getConstructionOriginLayout();
        if (!MolecularCenterStructure.areUpdateChunksLoaded(level, worldPosition, facing, originLayout)) {
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.preview_cross_chunk"),
                    false);
        }
        if (!MolecularCenterStructure.isWithinUpdateHeight(level, worldPosition, originLayout)) {
            showBuildHeightFailure(player);
        }
    }

    private void showBuildHeightFailure(ServerPlayer player) {
        var originLayout = getConstructionOriginLayout();
        var anchor = MolecularCenterStructure.controllerPart(originLayout);
        var blueprint = MolecularCenterStructure.parts(originLayout);
        int below = anchor.y() - blueprint.stream().mapToInt(MolecularCenterStructure.Part::y)
                .min().orElse(MolecularCenterStructure.CURRENT_MIN_Y);
        int above = blueprint.stream().mapToInt(MolecularCenterStructure.Part::y)
                .max().orElse(MolecularCenterStructure.CURRENT_MAX_Y) - anchor.y();
        int controllerY = worldPosition.getY();
        player.displayClientMessage(Component.translatable("message.molecularmanipulator.preview_build_height",
                controllerY, below, above, controllerY - below, controllerY + above,
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1,
                MolecularCenterStructure.minimumControllerY(level.getMinBuildHeight(), originLayout),
                MolecularCenterStructure.maximumControllerY(level.getMaxBuildHeight(), originLayout)), false);
    }

    private void processWorkQueue() {
        if (level == null || level.isClientSide() || (!building && !dismantling) || workOwner == null) {
            return;
        }
        var player = level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(workOwner);
        if (player == null || !player.mayBuild()) {
            return;
        }
        if (dismantling) {
            processDismantleQueue(player);
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!(structureUpdating
                ? MolecularCenterStructure.areUpdateChunksLoaded(level, worldPosition, facing,
                        structureUpdateSourceLayout)
                : MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing,
                        getConstructionOriginLayout()))) {
            return;
        }
        restoreBuildQueue();
        if (building && !buildQueueInitialized) {
            return;
        }
        int budget = ModConfig.BUILD_BLOCKS_PER_TICK.get();
        var workParts = buildWorkParts;
        int partCount = workParts.size();
        var materialSources = findBuildMaterialSources(player);
        workTotal = partCount;
        while (budget-- > 0 && building) {
            if (workCursor >= partCount) {
                finishBuilding(player);
                break;
            }
            if (workCursor < 0 || workCursor >= partCount) {
                building = false;
                dismantling = false;
                repairOnlyBuild = false;
                structureUpdating = false;
                structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
                buildQueueInitialized = false;
                buildWorkParts = List.of();
                refreshStructure();
                break;
            }
            var part = workParts.get(workCursor);
            var originLayout = structureUpdating ? structureUpdateSourceLayout
                    : MolecularCenterStructure.StructureLayout.CURRENT;
            var pos = MolecularCenterStructure.worldPos(worldPosition, facing, part, originLayout);
            if (MolecularCenterStructure.isController(part, originLayout)
                    || structureUpdating && MolecularCenterStructure.isController(part)) {
                advanceWork();
                continue;
            }
            processBuildPart(player, part, pos, materialSources);
            if (building && workCursor >= workTotal) {
                finishBuilding(player);
                break;
            }
        }
        if (!isRemoved()) {
            setChanged();
        }
    }

    private void finishBuilding(ServerPlayer player) {
        boolean completedStructureUpdate = structureUpdating;
        if (completedStructureUpdate) {
            var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            building = false;
            repairOnlyBuild = false;
            buildQueueInitialized = false;
            buildWorkParts = List.of();
            if (!MolecularCenterStructure.matchesUpdateTarget(level, worldPosition, facing,
                    structureUpdateSourceLayout)) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.structure_update_incomplete", Math.max(1, workConflicts)), false);
                saveChanges();
                return;
            }
            var destination = MolecularCenterStructure.relocatedControllerPos(worldPosition, facing,
                    structureUpdateSourceLayout);
            if (!destination.equals(worldPosition)) {
                relocateController(player, facing, destination);
                return;
            }
        }
        building = false;
        repairOnlyBuild = false;
        structureUpdating = false;
        structureUpdateSourceLayout = MolecularCenterStructure.StructureLayout.INCOMPLETE;
        buildQueueInitialized = false;
        buildWorkParts = List.of();
        refreshStructure();
        if (completedStructureUpdate) {
            var message = structureLayout == MolecularCenterStructure.StructureLayout.CURRENT
                    && workConflicts == 0
                    ? Component.translatable("message.molecularmanipulator.structure_update_complete")
                    : Component.translatable("message.molecularmanipulator.structure_update_incomplete",
                            workConflicts);
            player.displayClientMessage(message, false);
        }
    }

    private boolean canRelocateController(ServerPlayer player, Direction facing,
            MolecularCenterStructure.StructureLayout sourceLayout) {
        var destination = MolecularCenterStructure.relocatedControllerPos(worldPosition, facing, sourceLayout);
        if (destination.equals(worldPosition)) {
            return true;
        }
        var target = MolecularCenterStructure.controllerPart(MolecularCenterStructure.StructureLayout.CURRENT);
        var state = level.getBlockState(destination);
        boolean sourceMatches = MolecularCenterStructure.matchesSourcePart(sourceLayout, target, state);
        var existing = level.getBlockEntity(destination);
        boolean knownShell = existing instanceof MolecularCenterShellBlockEntity && sourceMatches;
        boolean unobstructed = level.isUnobstructed(null,
                net.minecraft.world.phys.shapes.Shapes.block().move(
                        destination.getX(), destination.getY(), destination.getZ()));
        if (!level.hasChunkAt(destination) || !sourceMatches && !state.canBeReplaced()
                || existing != null && !knownShell || !unobstructed
                || !player.mayUseItemAt(destination, Direction.UP, ItemStack.EMPTY)
                || !player.mayUseItemAt(worldPosition, Direction.UP, ItemStack.EMPTY)) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.controller_move_blocked",
                    destination.getX(), destination.getY(), destination.getZ()), false);
            return false;
        }
        return true;
    }

    private void relocateController(ServerPlayer player, Direction facing, BlockPos destination) {
        if (!canRelocateController(player, facing, structureUpdateSourceLayout)) {
            saveChanges();
            return;
        }
        var sourceAnchor = MolecularCenterStructure.controllerPart(structureUpdateSourceLayout);
        var replacement = MolecularCenterStructure.partAt(sourceAnchor.x(), sourceAnchor.y(), sourceAnchor.z());
        if (replacement == null || replacement.partType() == MolecularCenterStructure.PartType.AIR) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.controller_move_failed"), false);
            saveChanges();
            return;
        }
        var replacementState = MolecularCenterStructure.partState(replacement.partType());
        var material = new ItemStack(replacementState.getBlock());
        var destinationState = level.getBlockState(destination);
        var recovered = MolecularCenterStructure.matchesSourcePart(structureUpdateSourceLayout,
                MolecularCenterStructure.controllerPart(MolecularCenterStructure.StructureLayout.CURRENT),
                destinationState) ? new ItemStack(destinationState.getBlock()) : ItemStack.EMPTY;
        // Moving down into the existing coil reuses that exact block to decorate
        // the old socket; no extra coil is charged or returned to the player.
        boolean reuseRecovered = !recovered.isEmpty()
                && ItemStack.isSameItemSameTags(recovered, material);
        if (!reuseRecovered && !recovered.isEmpty() && !canReceiveRelocationItem(player, recovered)) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.controller_move_materials"), false);
            saveChanges();
            return;
        }
        var registries = level.registryAccess();
        var originalState = level.getBlockState(worldPosition);
        var originalData = saveWithFullMetadata();
        var destinationEntity = level.getBlockEntity(destination);
        var destinationData = destinationEntity == null ? null : destinationEntity.saveWithFullMetadata();
        var transferData = originalData.copy();
        transferData.putInt("x", destination.getX());
        transferData.putInt("y", destination.getY());
        transferData.putInt("z", destination.getZ());
        transferData.putBoolean(CENTERED_CONTROLLER_TAG, true);
        transferData.putString(CONTROLLER_ANCHOR_TAG, ControllerAnchor.LOWERED.name());
        transferData.putBoolean("molecular_center_formed", false);
        transferData.putBoolean("molecular_center_building", false);
        transferData.putBoolean("molecular_center_dismantling", false);
        transferData.putBoolean(STRUCTURE_UPDATING_TAG, false);
        transferData.remove(STRUCTURE_UPDATE_SOURCE_TAG);
        transferData.putBoolean(LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG, false);
        var controllerState = originalState.setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false);

        // Decode before touching either world position. The prepared controller's
        // managed AE node is still uncreated and cannot duplicate an active node.
        final MolecularCenterBlockEntity relocated;
        try {
            relocated = new MolecularCenterBlockEntity(destination, controllerState);
            relocated.load(transferData);
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.error("Unable to prepare molecular controller relocation at {}", worldPosition, exception);
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.controller_move_failed"), false);
            saveChanges();
            return;
        }
        if (!reuseRecovered && !extractBuildMaterial(player, material, findBuildMaterialSources(player))) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.controller_move_materials"), false);
            saveChanges();
            return;
        }
        var journal = player.getPersistentData().getCompound(CONTROLLER_MOVE_RECOVERY_TAG);
        String transaction = UUID.randomUUID().toString();
        var backup = new CompoundTag();
        backup.putString("dimension", level.dimension().location().toString());
        backup.putLong("source_pos", worldPosition.asLong());
        backup.putLong("target_pos", destination.asLong());
        backup.put("source_state", net.minecraft.nbt.NbtUtils.writeBlockState(originalState));
        backup.put("target_state", net.minecraft.nbt.NbtUtils.writeBlockState(destinationState));
        backup.put("source_data", originalData.copy());
        if (destinationData != null) {
            backup.put("target_data", destinationData.copy());
        }
        journal.put(transaction, backup);
        player.getPersistentData().put(CONTROLLER_MOVE_RECOVERY_TAG, journal);
        try {
            // AEBaseEntityBlock.onRemove drops every internal inventory even for
            // setBlock. Remove the old BE first so that hook cannot see its data.
            level.removeBlockEntity(worldPosition);
            if (destinationEntity != null) {
                level.removeBlockEntity(destination);
            }
            level.setBlock(destination, controllerState, 3);
            if (!level.getBlockState(destination).equals(controllerState)) {
                throw new IllegalStateException("Destination refused the controller block");
            }
            level.removeBlockEntity(destination); // discard the empty auto-created BE
            level.setBlockEntity(relocated);
            level.setBlock(worldPosition, replacementState, 3);
            if (!level.getBlockState(worldPosition).equals(replacementState)
                    || level.getBlockEntity(destination) != relocated
                    || MolecularCenterStructure.detectLayout(level, destination, facing)
                            != MolecularCenterStructure.StructureLayout.CURRENT) {
                throw new IllegalStateException("Relocated controller failed final structure validation");
            }
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.error("Molecular controller relocation {} failed; restoring both positions",
                    transaction, exception);
            boolean targetRestored = restoreRelocationBlock(destination, destinationState, destinationData);
            boolean detached = detachRelocatedController(destination, relocated);
            boolean restored = detached && restoreRelocationBlock(worldPosition, originalState, originalData)
                    && targetRestored;
            if (!reuseRecovered && !player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(material.copy());
            }
            if (restored) {
                journal.remove(transaction);
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.controller_move_failed"), false);
            } else {
                // Keep the full, non-itemized payload in persistent player data;
                // never spawn a second usable controller during failed recovery.
                MolecularManipulator.LOGGER.error("Controller recovery retained in player {} tag {} transaction {}",
                        player.getUUID(), CONTROLLER_MOVE_RECOVERY_TAG, transaction);
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.controller_move_recovery_failed"), false);
            }
            return;
        }
        journal.remove(transaction);
        structureUpdating = false;
        relocated.structureLayout = MolecularCenterStructure.StructureLayout.CURRENT;
        relocated.lastKnownStructureLayout = MolecularCenterStructure.StructureLayout.CURRENT;
        relocated.saveChanges();
        relocated.markForUpdate();
        if (!reuseRecovered && !recovered.isEmpty()) {
            player.getInventory().placeItemBackInInventory(recovered);
        }
        player.displayClientMessage(Component.translatable(
                workConflicts == 0 ? "message.molecularmanipulator.structure_update_complete"
                        : "message.molecularmanipulator.structure_update_incomplete", workConflicts), false);
        player.closeContainer();
    }

    private static boolean canReceiveRelocationItem(ServerPlayer player, ItemStack stack) {
        return player.getInventory().items.stream().anyMatch(slot -> slot.isEmpty()
                || ItemStack.isSameItemSameTags(slot, stack)
                        && slot.getCount() + stack.getCount() <= slot.getMaxStackSize());
    }

    private boolean detachRelocatedController(BlockPos pos, MolecularCenterBlockEntity relocated) {
        try {
            if (level.getBlockEntity(pos) == relocated) {
                level.removeBlockEntity(pos);
            }
            if (!relocated.isRemoved()) {
                relocated.setRemoved();
            }
            return relocated.isRemoved();
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.error("Cannot detach the failed relocated controller at {}", pos, exception);
            // Do not create another controller from the same payload while this
            // node might still be alive. The persistent recovery journal remains.
            return false;
        }
    }

    private boolean restoreRelocationBlock(BlockPos pos, BlockState state, CompoundTag data) {
        try {
            var present = level.getBlockEntity(pos);
            if (present != null && !(present instanceof MolecularCenterBlockEntity)
                    && !(present instanceof MolecularCenterShellBlockEntity)) {
                return false;
            }
            level.removeBlockEntity(pos);
            level.setBlock(pos, state, 3);
            if (!level.getBlockState(pos).equals(state)) {
                return false;
            }
            if (data != null) {
                var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        pos, state, data.copy());
                if (restored == null) {
                    return false;
                }
                level.removeBlockEntity(pos);
                level.setBlockEntity(restored);
                restored.setChanged();
                if (restored instanceof MolecularCenterBlockEntity center) {
                    center.markForUpdate();
                }
            }
            return true;
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.error("Unable to restore controller relocation position {}", pos, exception);
            return false;
        }
    }

    private void syncShellConnections(boolean connected) {
        if (level == null) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (var part : MolecularCenterStructure.parts(structureLayout)) {
            if (part.partType() != MolecularCenterStructure.PartType.CASING
                    || MolecularCenterStructure.isController(part, structureLayout)) {
                continue;
            }
            var partPos = MolecularCenterStructure.worldPos(worldPosition, facing, part, structureLayout);
            if (!level.hasChunkAt(partPos)) {
                continue;
            }
            var shell = level.getBlockEntity(partPos);
            if (shell instanceof MolecularCenterShellBlockEntity shellBlockEntity) {
                shellBlockEntity.setControllerPos(connected ? worldPosition : null);
            }
        }
    }

    private void processBuildPart(ServerPlayer player, MolecularCenterStructure.Part part, BlockPos pos,
            List<NetworkMaterialSource> materialSources) {
        if (part.partType() == MolecularCenterStructure.PartType.AIR) {
            if (MolecularCenterStructure.isVisualCenter(part) && !structureUpdating) {
                advanceWork();
                return;
            }
            var current = level.getBlockState(pos);
            if (current.isAir()) {
                advanceWork();
                return;
            }
            if (structureUpdating) {
                if (MolecularCenterStructure.matchesSourcePart(
                        structureUpdateSourceLayout, part, current)) {
                    if (removeLegacyStructurePart(player, pos)) {
                        advanceWork();
                    }
                } else {
                    // The exact old block changed after preflight. Never sweep
                    // an unrelated player block merely because it is an AE or
                    // multiblock decoration block.
                    workConflicts++;
                    advanceWork();
                }
                return;
            }
            if (MolecularCenterStructure.isStructurePart(current)) {
                if (removeLegacyStructurePart(player, pos)) {
                    advanceWork();
                }
                return;
            }
            if (current.canBeReplaced()
                    && player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)
                    && level.removeBlock(pos, false)) {
                advanceWork();
                return;
            }
            workConflicts++;
            advanceWork();
            return;
        }
        var current = level.getBlockState(pos);
        var expected = MolecularCenterStructure.partState(part.partType());
        if (current.is(expected.getBlock())) {
            advanceWork();
            return;
        }
        if (structureUpdating) {
            if (MolecularCenterStructure.matchesSourcePart(
                    structureUpdateSourceLayout, part, current)) {
                if (!removeLegacyStructurePart(player, pos)) {
                    return;
                }
            } else if (!current.canBeReplaced()) {
                workConflicts++;
                advanceWork();
                return;
            }
        } else if (MolecularCenterStructure.isStructurePart(current)
                    && !removeLegacyStructurePart(player, pos)) {
                return;
        }
        current = level.getBlockState(pos);
        if (!current.canBeReplaced() || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
            workConflicts++;
            advanceWork();
            return;
        }
        var material = new ItemStack(expected.getBlock().asItem());
        if (material.isEmpty() || !extractBuildMaterial(player, material, materialSources)) {
            return;
        }
        if (!placeBlock(player, pos, material, expected)) {
            refundBuildMaterial(player, material, materialSources);
            workConflicts++;
        }
        advanceWork();
    }

    private boolean removeLegacyStructurePart(ServerPlayer player, BlockPos pos) {
        var current = level.getBlockState(pos);
        if (!MolecularCenterStructure.isStructurePart(current)
                || pos.equals(worldPosition)) {
            return true;
        }
        if (!player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
            return false;
        }
        var recovered = new ItemStack(current.getBlock().asItem());
        if (recovered.isEmpty()) {
            return level.destroyBlock(pos, true, player);
        }
        if (!canStoreDismantled(player, recovered)
                || !level.destroyBlock(pos, false, player)) {
            return false;
        }
        storeDismantled(player, recovered);
        return true;
    }

    private void processDismantleQueue(ServerPlayer player) {
        if (dismantlePlan == null) {
            var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            dismantlePlan = MolecularCenterStructure.createDismantlePlan(level, worldPosition, facing,
                    dismantleLayout, dismantleIncludesUpgrade);
            if (dismantlePlan == null) {
                return;
            }
            workCursor = 0;
            workTotal = dismantlePlan.total();
        }
        if (!dismantlePlan.remainingChunksLoaded(level)) {
            return;
        }
        int budget = ModConfig.BUILD_BLOCKS_PER_TICK.get();
        while (budget > 0 && !dismantlePlan.isComplete()) {
            var entry = dismantlePlan.current();
            var state = level.getBlockState(entry.pos());
            var blockEntity = level.getBlockEntity(entry.pos());
            if (!entry.matches(state) || entry.pos().equals(worldPosition)
                    || blockEntity != null && !(blockEntity instanceof MolecularCenterShellBlockEntity)) {
                dismantlePlan.advance();
                continue;
            }
            var item = new ItemStack(entry.block());
            if (!player.mayUseItemAt(entry.pos(), Direction.UP, ItemStack.EMPTY)
                    || item.isEmpty() || !canStoreDismantled(player, item)) {
                break;
            }
            if (!level.destroyBlock(entry.pos(), false, player)) {
                break;
            }
            storeDismantled(player, item);
            dismantlePlan.advance();
            budget--;
        }
        workCursor = dismantlePlan.completed();
        workTotal = dismantlePlan.total();
        if (dismantlePlan.isComplete()) {
            dismantling = false;
            refreshStructure();
        }
        setChanged();
    }

    private boolean extractBuildMaterial(ServerPlayer player, ItemStack template,
            List<NetworkMaterialSource> materialSources) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (var stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(stack, template) && !stack.isEmpty()) {
                stack.shrink(1);
                return true;
            }
        }
        var key = AEItemKey.of(template);
        if (key == null) {
            return false;
        }
        for (var source : materialSources) {
            if (source.storage().extract(key, 1, Actionable.MODULATE, source.actionSource()) == 1) {
                return true;
            }
        }
        return false;
    }

    private void refundBuildMaterial(ServerPlayer player, ItemStack template,
            List<NetworkMaterialSource> materialSources) {
        if (player.getAbilities().instabuild) {
            return;
        }
        if (player.getInventory().add(template.copy())) {
            return;
        }
        var key = AEItemKey.of(template);
        if (key == null) {
            return;
        }
        for (var source : materialSources) {
            if (source.storage().insert(key, 1, Actionable.MODULATE, source.actionSource()) == 1) {
                return;
            }
        }
    }

    private List<NetworkMaterialSource> findBuildMaterialSources(ServerPlayer player) {
        var sources = new ArrayList<NetworkMaterialSource>();
        var seenStorages = new ReferenceOpenHashSet<MEStorage>();
        var playerSource = new PlayerSource(player);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) {
                continue;
            }
            var linkedGrid = terminal.getLinkedGrid(stack, player.level(), null);
            if (linkedGrid == null) {
                continue;
            }
            var storage = linkedGrid.getStorageService().getInventory();
            if (seenStorages.add(storage)) {
                sources.add(new NetworkMaterialSource(storage, playerSource));
            }
        }
        var controllerGrid = getMainNode().isActive() ? getMainNode().getGrid() : null;
        if (controllerGrid != null) {
            var storage = controllerGrid.getStorageService().getInventory();
            if (seenStorages.add(storage)) {
                sources.add(new NetworkMaterialSource(storage, playerSource));
            }
        }
        return sources;
    }

    private record NetworkMaterialSource(MEStorage storage,
            appeng.api.networking.security.IActionSource actionSource) {
    }

    private boolean placeBlock(ServerPlayer player, BlockPos pos, ItemStack stack, BlockState expected) {
        if (!(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var context = new BlockPlaceContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
        var result = ((BlockItem) stack.getItem()).place(context);
        return result.consumesAction() && level.getBlockState(pos).is(expected.getBlock());
    }

    private boolean canStoreDismantled(ServerPlayer player, ItemStack stack) {
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(stack);
        if (grid != null && key != null
                && grid.getStorageService().getInventory().insert(
                        key, stack.getCount(), Actionable.SIMULATE, actionSource) >= stack.getCount()) {
            return true;
        }
        for (var slot : player.getInventory().items) {
            if (slot.isEmpty() || ItemStack.isSameItemSameTags(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void storeDismantled(ServerPlayer player, ItemStack stack) {
        long remaining = stack.getCount();
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(stack);
        if (grid != null && key != null) {
            remaining -= grid.getStorageService().getInventory().insert(
                    key, remaining, Actionable.MODULATE, actionSource);
        }
        if (remaining <= 0) {
            return;
        }
        var remainder = stack.copyWithCount((int) remaining);
        if (!player.getInventory().add(remainder)) {
            player.drop(remainder, false);
        }
    }

    private void advanceWork() {
        workCursor++;
    }

    boolean acceptPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (assembling || hasActiveReusableBatch() || !isOperational()
                || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }
        var grid = getMainNode().getGrid();
        if (level == null || grid == null || !getLogic().getReturnInv().isEmpty()) {
            return false;
        }
        assembling = true;
        try {
            var batchContext = MolecularBatchDispatchContext.current(
                    patternDetails, inputs);
            if (batchContext != null
                    && batchContext.reusablePlan() != null) {
                return acceptReusablePattern(
                        patternDetails, pattern, inputs, batchContext);
            }
            boolean prepared = batchContext != null
                    ? craftingBatcher.prepareSelected(
                            patternDetails, inputs, level,
                            VIRTUAL_PARALLEL_LIMIT,
                            batchContext.firstInputs(),
                            batchContext.craftCount())
                    : craftingBatcher.prepare(
                            patternDetails, inputs, level,
                            VIRTUAL_PARALLEL_LIMIT);
            if (!prepared) {
                return false;
            }
            var primaryOutputs = craftingBatcher.getPrimaryOutputAmounts();
            var remainderOutputs = craftingBatcher.getRemainderOutputAmounts();
            if (!canQueueOutputs(primaryOutputs, remainderOutputs)) {
                return false;
            }
            addOutputs(pendingPrimaryOutputs, primaryOutputs);
            addOutputs(pendingByproducts, remainderOutputs);
            // The output buffers now own the complete batch. No post-commit
            // hook may escape and make AE2 schedule the same work again.
            try {
                craftingBatcher.consumeInputs(inputs);
            } catch (RuntimeException exception) {
                logPostCommitFailure("input holder clear", exception);
            }
            try {
                fireCraftingEventOncePerTick(level, patternDetails, pattern);
            } catch (RuntimeException exception) {
                logPostCommitFailure("crafting event", exception);
            }
            recordPipelineActivity(level.getGameTime(), craftingBatcher.getCraftCount());
            outputReadyTick = Math.max(outputReadyTick, level.getGameTime() + 1);
            try {
                markOutputBufferChanged(level.getGameTime());
            } catch (RuntimeException exception) {
                logPostCommitFailure("dirty-state update", exception);
            }
            return true;
        } finally {
            assembling = false;
        }
    }

    private boolean acceptReusablePattern(IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputs,
            MolecularBatchDispatchContext.Context context) {
        if (level == null || activeReusableBatch != null) {
            return false;
        }

        var job = craftingBatcher.prepareReusable(patternDetails, inputs, level,
                context.craftingId(), context.reusablePlan());
        if (job == null
                || !canQueueOutputs(job.projectedPrimaryOutputs(),
                        job.projectedFinalRemainders())
                || !canQueueRefunds(job.cancellationRefunds())) {
            return false;
        }

        // Commit point: once the holders are cleared, this persisted job owns
        // every extracted input until completion or cancellation refund.
        activeReusableBatch = job;
        try {
            craftingBatcher.consumeInputs(inputs);
        } catch (RuntimeException exception) {
            logPostCommitFailure("reusable input holder clear", exception);
        }
        try {
            saveChanges();
        } catch (RuntimeException exception) {
            logPostCommitFailure("reusable dirty-state update", exception);
        }
        try {
            fireCraftingEventOncePerTick(level, patternDetails, pattern);
        } catch (RuntimeException exception) {
            logPostCommitFailure("reusable crafting event", exception);
        }
        return true;
    }

    private void logPostCommitFailure(String stage,
            RuntimeException exception) {
        MolecularManipulator.LOGGER.warn(
                "Molecular sequence array {} failed after batch ownership committed at {}",
                stage, getBlockPos(), exception);
    }

    private void fireCraftingEventOncePerTick(Level level, IPatternDetails details,
            IMolecularAssemblerSupportedPattern pattern) {
        long gameTime = level.getGameTime();
        if (craftingEventTick != gameTime) {
            craftingEventTick = gameTime;
            craftingEventsThisTick.clear();
        }
        if (craftingEventsThisTick.add(details)) {
            CraftingEvent.fireAutoCraftingEvent(level, pattern, craftingBatcher.getCraftedOutput().copy(),
                    craftingBatcher.getCraftingGrid());
        }
    }

    private void recordPipelineActivity(long gameTime, long craftCount) {
        if (craftCount > 0) visualSuccessTick = gameTime;
        if (pipelineActivityTick != gameTime) {
            pipelineActivityTick = gameTime;
            pipelineCrafts = 0;
        }
        try {
            pipelineCrafts = Math.addExact(pipelineCrafts, craftCount);
        } catch (ArithmeticException exception) {
            pipelineCrafts = Long.MAX_VALUE;
        }
    }

    private static void addOutputs(Object2LongOpenHashMap<AEKey> destination,
            Object2LongOpenHashMap<AEKey> outputs) {
        for (var entry : outputs.object2LongEntrySet()) {
            destination.put(entry.getKey(),
                    Math.addExact(destination.getLong(entry.getKey()), entry.getLongValue()));
        }
    }

    private boolean canQueueOutputs(Object2LongOpenHashMap<AEKey> primaryOutputs,
            Object2LongOpenHashMap<AEKey> remainderOutputs) {
        var totals = new Object2LongOpenHashMap<AEKey>();
        try {
            mergeChecked(totals, pendingPrimaryOutputs);
            mergeChecked(totals, pendingByproducts);
            mergeChecked(totals, primaryOutputs);
            mergeChecked(totals, remainderOutputs);
            return totals.size() <= MAX_BUFFERED_TYPES;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private boolean canQueueRefunds(Object2LongOpenHashMap<AEKey> refunds) {
        var totals = new Object2LongOpenHashMap<AEKey>();
        try {
            mergeChecked(totals, reusableBatchRefunds);
            mergeChecked(totals, refunds);
            return totals.size() <= MAX_BUFFERED_TYPES;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private void processReusableBatch(long gameTime) {
        var job = activeReusableBatch;
        if (level == null || job == null || assembling) {
            return;
        }

        if (MolecularBatchCancellationData.isCanceled(level, job.craftingId())) {
            var refunds = job.cancellationRefunds();
            if (!canQueueRefunds(refunds)) {
                return;
            }
            addOutputs(reusableBatchRefunds, refunds);
            activeReusableBatch = null;
            markOutputBufferChanged(gameTime);
            return;
        }

        if (!isOperational()) {
            return;
        }

        // The complete accepted batch is a constant-size aggregate operation.
        long step = job.nextStep(Long.MAX_VALUE);
        if (step <= 0) {
            return;
        }
        var primary = job.primaryOutputsFor(step);
        boolean completes = step == job.totalCrafts() - job.completedCrafts();
        var remainders = completes
                ? job.projectedFinalRemainders()
                : new Object2LongOpenHashMap<AEKey>();
        if (!canQueueOutputs(primary, remainders)) {
            return;
        }

        job.advance(step);
        addOutputs(pendingPrimaryOutputs, primary);
        if (job.isComplete()) {
            addOutputs(pendingByproducts, job.completedRemainders());
            activeReusableBatch = null;
        }
        recordPipelineActivity(gameTime, step);
        outputReadyTick = Math.max(outputReadyTick, gameTime + 1);
        markOutputBufferChanged(gameTime);
    }

    private static void mergeChecked(Object2LongOpenHashMap<AEKey> totals,
            Object2LongOpenHashMap<AEKey> source) {
        for (var entry : source.object2LongEntrySet()) {
            totals.put(entry.getKey(), Math.addExact(totals.getLong(entry.getKey()), entry.getLongValue()));
        }
    }

    private void markOutputBufferChanged(long gameTime) {
        if (bufferDirtyTick != gameTime) {
            bufferDirtyTick = gameTime;
            saveChanges();
        }
    }

    private void flushBufferedOutputs(long gameTime) {
        if (assembling) {
            return;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            pipelineBlocked = hasBufferedOutputs();
            return;
        }

        assembling = true;
        try {
            var storage = grid.getStorageService().getInventory();
            var craftingService = grid.getCraftingService();
            boolean changed = false;
            boolean blocked = false;
            long transferred = 0;
            var transferBudget = AEKeyTransferScheduler.defaultBudget();

            var refundResult = flushMapToNetwork(reusableBatchRefunds, storage,
                    reusableBatchRefundTransferScheduler, transferBudget);
            changed |= refundResult.changed();
            blocked |= refundResult.blocked();
            transferred = saturatedAdd(transferred, refundResult.transferred());

            if (gameTime >= outputReadyTick) {
                var primaryResult = flushPending(pendingPrimaryOutputs,
                        storage, craftingService, pendingPrimaryTransferScheduler, transferBudget);
                changed |= primaryResult.changed();
                blocked |= primaryResult.blocked();
                transferred = saturatedAdd(transferred, primaryResult.transferred());

                var byproductResult = flushPending(pendingByproducts,
                        storage, craftingService, pendingByproductTransferScheduler, transferBudget);
                changed |= byproductResult.changed();
                blocked |= byproductResult.blocked();
                transferred = saturatedAdd(transferred, byproductResult.transferred());
            }

            pipelineBlocked = blocked;
            if (transferred > 0) {
                lastPipelineTransfer = transferred;
            }
            outputReadyTick = pendingPrimaryOutputs.isEmpty() && pendingByproducts.isEmpty()
                    ? Long.MIN_VALUE
                    : gameTime + 1;
            if (changed) {
                saveChanges();
            }
        } finally {
            assembling = false;
        }
    }

    private FlushResult flushPending(Object2LongOpenHashMap<AEKey> pending,
            MEStorage storage,
            appeng.api.networking.crafting.ICraftingService craftingService,
            AEKeyTransferScheduler scheduler, AEKeyTransferScheduler.TransferBudget transferBudget) {
        if (pending.isEmpty()) {
            return FlushResult.EMPTY;
        }

        var result = scheduler.flush(pending, transferBudget, (key, amount) -> {
            long remaining = amount;
            long requested = Math.min(remaining, craftingService.getRequestedAmount(key));
            if (requested > 0) {
                long delivered = storage.insert(key, requested, Actionable.MODULATE, actionSource);
                remaining -= delivered;
            }

            if (remaining > 0) {
                long inserted = storage.insert(key, remaining, Actionable.MODULATE, actionSource);
                remaining -= inserted;
            }
            return amount - remaining;
        });
        return new FlushResult(result.changed(), result.blocked(), result.transferred());
    }

    private FlushResult flushMapToNetwork(Object2LongOpenHashMap<AEKey> source, MEStorage storage,
            AEKeyTransferScheduler scheduler, AEKeyTransferScheduler.TransferBudget transferBudget) {
        var result = scheduler.flush(source, transferBudget,
                (key, amount) -> storage.insert(key, amount, Actionable.MODULATE, actionSource));
        return new FlushResult(result.changed(), result.blocked(), result.transferred());
    }

    private boolean hasBufferedOutputs() {
        return !reusableBatchRefunds.isEmpty()
                || !pendingPrimaryOutputs.isEmpty()
                || !pendingByproducts.isEmpty();
    }

    private static long saturatedSum(Object2LongOpenHashMap<AEKey> first,
            Object2LongOpenHashMap<AEKey> second) {
        return saturatedAdd(sumAmounts(first), sumAmounts(second));
    }

    private static long sumAmounts(Object2LongOpenHashMap<AEKey> stacks) {
        long total = 0;
        for (var entry : stacks.object2LongEntrySet()) {
            total = saturatedAdd(total, entry.getLongValue());
        }
        return total;
    }

    private static long saturatedAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedMultiply(long first, long second) {
        if (first <= 0 || second <= 0) {
            return 0;
        }
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    public boolean schedulePatternRebuild(Runnable rebuild) {
        if (level != null && !level.isClientSide() && level.getServer() != null) {
            level.getServer().execute(rebuild);
            return true;
        }
        return false;
    }

    public void openMenu(Player player, appeng.menu.locator.MenuLocator locator) {
        appeng.menu.MenuOpener.open(MolecularCenterMenu.TYPE, player, locator);
    }

    public void returnToMainMenu(Player player, appeng.menu.ISubMenu subMenu) {
        appeng.menu.MenuOpener.returnTo(MolecularCenterMenu.TYPE, player, subMenu.getLocator());
    }

    public static <T extends MolecularCenterBlockEntity> BlockEntityTicker<T> ticker() {
        return (level, pos, state, be) -> be.serverTick();
    }

    public enum QuantumLinkState {
        EMPTY,
        STRUCTURE_INCOMPLETE,
        SEARCHING,
        REMOTE_MISSING,
        REMOTE_OFFLINE,
        FREQUENCY_OCCUPIED,
        WIRED_CONFLICT,
        CONNECTED,
        CONNECTION_ERROR,
        CONNECTED_BUILD_ONLY
    }

    public enum MatterJobMode {
        IDLE,
        DECONSTRUCT,
        REWRITE
    }

    public enum RewriteOutputMode {
        NETWORK,
        OUTPUT_SLOT;

        public RewriteOutputMode next() {
            return this == NETWORK ? OUTPUT_SLOT : NETWORK;
        }
    }

    public enum MatterJobState {
        IDLE,
        RUNNING,
        STOPPED,
        WAITING_NETWORK,
        WAITING_POWER,
        COOLING,
        ENTROPY_COST_TOO_HIGH,
        TARGET_REACHED,
        INPUT_EMPTY,
        BLUEPRINT_EMPTY,
        BLUEPRINT_CHANGED,
        UNSUPPORTED,
        SEQUENCE_STORAGE_FULL,
        INSUFFICIENT_SEQUENCE,
        OUTPUT_FULL
    }

    private record FlushResult(boolean changed, boolean blocked, long transferred) {
        private static final FlushResult EMPTY = new FlushResult(false, false, 0);
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        var center = this;
        var facing = center.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        var anchor = center.getControllerAnchorLayout();
        var minimum = MolecularCenterStructure.worldPos(center.getBlockPos(), facing,
                new MolecularCenterStructure.Part(MolecularCenterStructure.MIN_X,
                        0, MolecularCenterStructure.MIN_Z,
                        MolecularCenterStructure.PartType.AIR), anchor);
        var maximum = MolecularCenterStructure.worldPos(center.getBlockPos(), facing,
                new MolecularCenterStructure.Part(MolecularCenterStructure.MAX_X,
                        Math.max(MolecularCenterStructure.CURRENT_MAX_Y, 45),
                        MolecularCenterStructure.MAX_Z,
                        MolecularCenterStructure.PartType.AIR), anchor);
        return new net.minecraft.world.phys.AABB(minimum).minmax(new net.minecraft.world.phys.AABB(maximum)).inflate(2.0);
    }

}
