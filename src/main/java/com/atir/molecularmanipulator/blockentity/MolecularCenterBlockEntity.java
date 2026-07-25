package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
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
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.block.crafting.PatternProviderBlock;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry.MatterValue;
import com.atir.molecularmanipulator.world.MolecularCenterSpawnProtection;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class MolecularCenterBlockEntity extends PatternProviderBlockEntity implements InternalInventoryHost {
    public static final int PATTERNS_PER_PAGE = 36;
    public static final int MAX_PATTERN_PAGES = 1000;
    public static final int MAX_PATTERN_SLOTS = PATTERNS_PER_PAGE * MAX_PATTERN_PAGES;
    public static final long VIRTUAL_PARALLEL_LIMIT = Long.MAX_VALUE;
    private static final int MAX_BUFFERED_TYPES = 256;
    private static final int PIPELINE_STORAGE_PRIORITY = 1_000;
    private static final int MAX_PORT_ITEMS_PER_TICK = 4_096;
    private static final String LEGACY_OUTPUT_BUFFER_TAG = "molecular_center_output_buffer";
    private static final String PENDING_PRIMARY_TAG = "pipeline_pending_primary";
    private static final String PENDING_BYPRODUCT_TAG = "pipeline_pending_byproduct";
    private static final String CACHED_PRIMARY_TAG = "pipeline_cached_primary";
    private static final String CACHED_BYPRODUCT_TAG = "pipeline_cached_byproduct";
    private static final String PRIMARY_ROUTE_TAG = "pipeline_primary_route";
    private static final String BYPRODUCT_ROUTE_TAG = "pipeline_byproduct_route";
    private static final String OUTPUT_PORT_TAG = "pipeline_output_port";
    private static final String OUTPUT_READY_TICK_TAG = "molecular_center_output_ready_tick";
    private static final String REPAIR_ONLY_BUILD_TAG = "molecular_center_repair_only_build";
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
    public static final double QUANTUM_LINK_POWER = 512.0;
    public static final long MAX_SEQUENCE_CAPACITY = 1_000_000_000_000L;
    public static final long MAX_ENTROPY = 100_000L;
    public static final long MAX_JOB_TARGET = 1_000_000_000_000L;
    public static final int MAX_SPEED_CARDS = 4;
    public static final int DEFAULT_FIELD_COLOR = 0xC98CFF;
    public static final int DEFAULT_CORE_COLOR = 0xFFE4FF;
    public static final int DEFAULT_PRIMARY_RING_COLOR = 0xE0B0FF;
    public static final int DEFAULT_SECONDARY_RING_COLOR = 0xBC88FF;
    public static final int DEFAULT_LATTICE_COLOR = 0x8C60FF;

    private final MachineSource actionSource = new MachineSource(this);
    private final AppEngInternalInventory matterInventory = new AppEngInternalInventory(this, 4);
    private final IUpgradeInventory matterUpgrades = UpgradeInventories.forMachine(
            ModContent.MOLECULAR_CENTER_CONTROLLER.get(), MAX_SPEED_CARDS, this::onMatterUpgradesChanged);
    private final MolecularCraftingBatcher craftingBatcher = new MolecularCraftingBatcher();
    private final Object2LongOpenHashMap<AEKey> pendingPrimaryOutputs = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> pendingByproducts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> cachedPrimaryOutputs = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> cachedByproducts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> flushScratch = new Object2LongOpenHashMap<>();
    private final ReferenceOpenHashSet<IPatternDetails> craftingEventsThisTick = new ReferenceOpenHashSet<>();
    private final PipelineStorageProvider pipelineStorageProvider = new PipelineStorageProvider();
    private PipelineRoute primaryRoute = PipelineRoute.NETWORK;
    private PipelineRoute byproductRoute = PipelineRoute.NETWORK;
    private PipelinePort outputPort = PipelinePort.FRONT;
    private boolean assembling;
    private boolean formed;
    private boolean pipelineBlocked;
    private long craftingEventTick = Long.MIN_VALUE;
    private long bufferDirtyTick = Long.MIN_VALUE;
    private long outputReadyTick = Long.MIN_VALUE;
    private long pipelineActivityTick = Long.MIN_VALUE;
    private long pipelineCrafts;
    private long lastPipelineTransfer;
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
    private boolean building;
    private boolean dismantling;
    private boolean repairOnlyBuild;
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
                .setIdlePowerUsage(ModConfig.IDLE_POWER.get())
                .addService(IStorageProvider.class, pipelineStorageProvider);
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
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModContent.MOLECULAR_CENTER_CONTROLLER_ITEM.get());
    }

    public AppEngInternalInventory getMatterInventory() {
        return matterInventory;
    }

    public IUpgradeInventory getMatterUpgrades() {
        return matterUpgrades;
    }

    private void onMatterUpgradesChanged() {
        deconstructJobProgress = Math.min(deconstructJobProgress, getMatterCycleTicks());
        rewriteJobProgress = Math.min(rewriteJobProgress, getMatterCycleTicks());
        saveChanges();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
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
        restoreBuildQueue();
        syncShellConnections(formed);
        IStorageProvider.requestUpdate(getMainNode());
        invalidatePipelineStorageCache();
        updateQuantumLink();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        disconnectQuantumLink(QuantumLinkState.SEARCHING);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        disconnectQuantumLink(QuantumLinkState.SEARCHING);
        super.setRemoved();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("molecular_center_formed", formed);
        tag.putBoolean("molecular_center_building", building);
        tag.putBoolean("molecular_center_dismantling", dismantling);
        tag.putBoolean(REPAIR_ONLY_BUILD_TAG, repairOnlyBuild);
        tag.putInt("molecular_center_work_cursor", workCursor);
        tag.putInt("molecular_center_work_total", workTotal);
        tag.putInt("molecular_center_work_conflicts", workConflicts);
        if (workOwner != null) {
            tag.putUUID("molecular_center_work_owner", workOwner);
        }
        writeStacks(tag, PENDING_PRIMARY_TAG, pendingPrimaryOutputs, registries);
        writeStacks(tag, PENDING_BYPRODUCT_TAG, pendingByproducts, registries);
        writeStacks(tag, CACHED_PRIMARY_TAG, cachedPrimaryOutputs, registries);
        writeStacks(tag, CACHED_BYPRODUCT_TAG, cachedByproducts, registries);
        tag.putString(PRIMARY_ROUTE_TAG, primaryRoute.getSerializedName());
        tag.putString(BYPRODUCT_ROUTE_TAG, byproductRoute.getSerializedName());
        tag.putString(OUTPUT_PORT_TAG, outputPort.getSerializedName());
        tag.putLong(OUTPUT_READY_TICK_TAG, outputReadyTick);
        matterInventory.writeToNBT(tag, MATTER_INVENTORY_TAG, registries);
        matterUpgrades.writeToNBT(tag, MATTER_UPGRADES_TAG, registries);
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
            tag.put(LEGACY_DECONSTRUCT_REFUND_TAG, legacyDeconstructRefund.save(registries));
        }
        if (!deconstructTemplate.isEmpty()) {
            tag.put(DECONSTRUCT_TEMPLATE_TAG, deconstructTemplate.save(registries));
        }
        if (!rewriteTemplate.isEmpty()) {
            tag.put(REWRITE_TEMPLATE_TAG, rewriteTemplate.save(registries));
        }
        writeVisualColors(tag);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        formed = tag.getBoolean("molecular_center_formed");
        building = tag.getBoolean("molecular_center_building");
        dismantling = tag.getBoolean("molecular_center_dismantling");
        repairOnlyBuild = building && tag.getBoolean(REPAIR_ONLY_BUILD_TAG);
        buildQueueInitialized = false;
        buildWorkParts = List.of();
        workCursor = tag.getInt("molecular_center_work_cursor");
        workTotal = repairOnlyBuild
                ? Math.max(0, tag.getInt("molecular_center_work_total"))
                : MolecularCenterStructure.workParts().size();
        if (building) {
            dismantling = false;
            workCursor = Math.max(0, Math.min(workCursor, workTotal));
        } else if (dismantling) {
            workCursor = Math.min(workCursor, workTotal - 1);
        }
        workConflicts = tag.getInt("molecular_center_work_conflicts");
        workOwner = tag.hasUUID("molecular_center_work_owner") ? tag.getUUID("molecular_center_work_owner") : null;
        pendingPrimaryOutputs.clear();
        pendingByproducts.clear();
        cachedPrimaryOutputs.clear();
        cachedByproducts.clear();
        if (tag.contains(PENDING_PRIMARY_TAG, Tag.TAG_LIST)) {
            readStacks(tag, PENDING_PRIMARY_TAG, pendingPrimaryOutputs, registries);
        } else {
            readStacks(tag, LEGACY_OUTPUT_BUFFER_TAG, pendingPrimaryOutputs, registries);
        }
        readStacks(tag, PENDING_BYPRODUCT_TAG, pendingByproducts, registries);
        readStacks(tag, CACHED_PRIMARY_TAG, cachedPrimaryOutputs, registries);
        readStacks(tag, CACHED_BYPRODUCT_TAG, cachedByproducts, registries);
        primaryRoute = PipelineRoute.fromSerializedName(tag.getString(PRIMARY_ROUTE_TAG));
        byproductRoute = PipelineRoute.fromSerializedName(tag.getString(BYPRODUCT_ROUTE_TAG));
        outputPort = PipelinePort.fromSerializedName(tag.getString(OUTPUT_PORT_TAG));
        outputReadyTick = tag.contains(OUTPUT_READY_TICK_TAG, Tag.TAG_LONG)
                ? tag.getLong(OUTPUT_READY_TICK_TAG)
                : Long.MIN_VALUE;
        matterInventory.readFromNBT(tag, MATTER_INVENTORY_TAG, registries);
        legacyDeconstructRefund = tag.contains(LEGACY_DECONSTRUCT_REFUND_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(LEGACY_DECONSTRUCT_REFUND_TAG))
                : ItemStack.EMPTY;
        if (!tag.getBoolean(DECONSTRUCT_MARKER_FORMAT_TAG)
                && legacyDeconstructRefund.isEmpty()
                && !matterInventory.getStackInSlot(0).isEmpty()) {
            legacyDeconstructRefund = matterInventory.getStackInSlot(0).copy();
            matterInventory.setItemDirect(0, legacyDeconstructRefund.copyWithCount(1));
        }
        matterUpgrades.readFromNBT(tag, MATTER_UPGRADES_TAG, registries);
        metalSequence = readStoredAmount(tag, METAL_SEQUENCE_TAG, MAX_SEQUENCE_CAPACITY);
        mineralSequence = readStoredAmount(tag, MINERAL_SEQUENCE_TAG, MAX_SEQUENCE_CAPACITY);
        crystalSequence = readStoredAmount(tag, CRYSTAL_SEQUENCE_TAG, MAX_SEQUENCE_CAPACITY);
        organicSequence = readStoredAmount(tag, ORGANIC_SEQUENCE_TAG, MAX_SEQUENCE_CAPACITY);
        entropy = readStoredAmount(tag, ENTROPY_TAG, MAX_ENTROPY);
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
                ? ItemStack.parseOptional(registries, tag.getCompound(DECONSTRUCT_TEMPLATE_TAG))
                : ItemStack.EMPTY;
        rewriteTemplate = tag.contains(REWRITE_TEMPLATE_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(REWRITE_TEMPLATE_TAG))
                : ItemStack.EMPTY;
        fieldColor = readColor(tag, FIELD_COLOR_TAG, DEFAULT_FIELD_COLOR);
        coreColor = readColor(tag, CORE_COLOR_TAG, DEFAULT_CORE_COLOR);
        primaryRingColor = readColor(tag, PRIMARY_RING_COLOR_TAG, DEFAULT_PRIMARY_RING_COLOR);
        secondaryRingColor = readColor(tag, SECONDARY_RING_COLOR_TAG, DEFAULT_SECONDARY_RING_COLOR);
        latticeColor = readColor(tag, LATTICE_COLOR_TAG, DEFAULT_LATTICE_COLOR);
        invalidatePipelineStorageCache();
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
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeInt(fieldColor);
        data.writeInt(coreColor);
        data.writeInt(primaryRingColor);
        data.writeInt(secondaryRingColor);
        data.writeInt(latticeColor);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int newFieldColor = data.readInt() & 0xFFFFFF;
        int newCoreColor = data.readInt() & 0xFFFFFF;
        int newPrimaryRingColor = data.readInt() & 0xFFFFFF;
        int newSecondaryRingColor = data.readInt() & 0xFFFFFF;
        int newLatticeColor = data.readInt() & 0xFFFFFF;
        changed |= fieldColor != newFieldColor
                || coreColor != newCoreColor
                || primaryRingColor != newPrimaryRingColor
                || secondaryRingColor != newSecondaryRingColor
                || latticeColor != newLatticeColor;
        fieldColor = newFieldColor;
        coreColor = newCoreColor;
        primaryRingColor = newPrimaryRingColor;
        secondaryRingColor = newSecondaryRingColor;
        latticeColor = newLatticeColor;
        return changed;
    }

    private static void writeStacks(CompoundTag tag, String key, Object2LongOpenHashMap<AEKey> stacks,
            HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : stacks.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                list.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(key, list);
    }

    private static void readStacks(CompoundTag tag, String key, Object2LongOpenHashMap<AEKey> stacks,
            HolderLookup.Provider registries) {
        var list = tag.getList(key, Tag.TAG_COMPOUND);
        for (var entryTag : list) {
            var stack = GenericStack.readTag(registries, (CompoundTag) entryTag);
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

    public PipelineRoute getPrimaryRoute() {
        return primaryRoute;
    }

    public PipelineRoute getByproductRoute() {
        return byproductRoute;
    }

    public PipelinePort getOutputPort() {
        return outputPort;
    }

    public void cyclePrimaryRoute() {
        setPrimaryRoute(primaryRoute.next());
    }

    public void cycleByproductRoute() {
        setByproductRoute(byproductRoute.next());
    }

    public void cycleOutputPort() {
        outputPort = outputPort.next();
        onPipelineConfigurationChanged();
    }

    public void setPrimaryRoute(PipelineRoute route) {
        if (route != null && primaryRoute != route) {
            primaryRoute = route;
            onPipelineConfigurationChanged();
        }
    }

    public void setByproductRoute(PipelineRoute route) {
        if (route != null && byproductRoute != route) {
            byproductRoute = route;
            onPipelineConfigurationChanged();
        }
    }

    public long getPipelineCacheAmount() {
        return saturatedSum(cachedPrimaryOutputs, cachedByproducts);
    }

    public int getPipelineCacheTypes() {
        var types = new java.util.HashSet<AEKey>();
        types.addAll(cachedPrimaryOutputs.keySet());
        types.addAll(cachedByproducts.keySet());
        return types.size();
    }

    public long getPendingOutputAmount() {
        return saturatedSum(pendingPrimaryOutputs, pendingByproducts);
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

    public long getQuantumFrequency() {
        var stack = matterInventory.getStackInSlot(3);
        if (!isValidQuantumSingularity(stack)) {
            return 0;
        }
        return stack.getOrDefault(AEComponents.ENTANGLED_SINGULARITY_ID, 0L);
    }

    public QuantumLinkState getQuantumLinkState() {
        return quantumLinkState;
    }

    public static boolean isValidQuantumSingularity(ItemStack stack) {
        return !stack.isEmpty()
                && stack.has(AEComponents.ENTANGLED_SINGULARITY_ID)
                && stack.getOrDefault(AEComponents.ENTANGLED_SINGULARITY_ID, 0L) > 0;
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
        getMainNode().setIdlePowerUsage(ModConfig.IDLE_POWER.get());
        quantumLinkState = nextState;
        if (connection != null) {
            try {
                connection.destroy();
            } catch (RuntimeException ignored) {
                // AE2 may already have invalidated the connection while one endpoint was unloading.
            }
            getLogic().updatePatterns();
        }
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
        return switch (getInstalledSpeedCards()) {
            case 1 -> 10;
            case 2 -> 5;
            case 3 -> 2;
            case 4 -> 1;
            default -> 20;
        };
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
        if (!ItemStack.isSameItemSameComponents(marker, deconstructTemplate)) {
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

        var result = processOneDeconstruction();
        if (result == MatterJobState.RUNNING) {
            setDeconstructJobState(MatterJobState.RUNNING);
            deconstructJobProcessed = Math.min(MAX_JOB_TARGET, deconstructJobProcessed + 1);
            if (deconstructTarget > 0 && deconstructJobProcessed >= deconstructTarget) {
                stopDeconstruction(MatterJobState.TARGET_REACHED);
            } else {
                saveChanges();
            }
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

        var result = processOneRewrite();
        if (result == MatterJobState.RUNNING) {
            setRewriteJobState(MatterJobState.RUNNING);
            rewriteJobProcessed = Math.min(MAX_JOB_TARGET, rewriteJobProcessed + 1);
            if (rewriteTarget > 0 && rewriteJobProcessed >= rewriteTarget) {
                stopRewrite(MatterJobState.TARGET_REACHED);
            } else if (rewriteOutputMode == RewriteOutputMode.OUTPUT_SLOT
                    && !canAcceptMatterOutput(rewriteTemplate)) {
                stopRewrite(MatterJobState.OUTPUT_FULL);
            } else {
                saveChanges();
            }
        } else if (isRetryableMatterState(result, true)) {
            setRewriteJobState(result);
        } else {
            stopRewrite(result);
        }
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
        long entropyPerItem = Math.max(1, value.total() / 64);
        if (entropy + entropyPerItem > MAX_ENTROPY) {
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
        finishMatterOperation();
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
                || !ItemStack.isSameItemSameComponents(blueprint, rewriteTemplate)) {
            return MatterJobState.BLUEPRINT_CHANGED;
        }
        var value = MatterSequenceRegistry.rewriteCostOf(rewriteTemplate);
        if (value == null) {
            return MatterJobState.UNSUPPORTED;
        }
        if (affordableOperations(value) < 1) {
            return MatterJobState.INSUFFICIENT_SEQUENCE;
        }
        long entropyPerItem = Math.max(1, value.total() / 16);
        if (entropy + entropyPerItem > MAX_ENTROPY) {
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
        finishMatterOperation();
        return MatterJobState.RUNNING;
    }

    private boolean canAcceptMatterOutput(ItemStack stack) {
        var output = matterInventory.getStackInSlot(2);
        return output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, stack)
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

    private static long capacityFor(long stored, long perItem) {
        return perItem <= 0 ? Long.MAX_VALUE : (MAX_SEQUENCE_CAPACITY - stored) / perItem;
    }

    private void finishMatterOperation() {
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
        if (id == VISUAL_ACTIVITY_EVENT) {
            clientVisualMode = Math.max(0, Math.min(4, type));
            return true;
        }
        return super.triggerEvent(id, type);
    }

    public int getClientVisualMode() {
        return clientVisualMode;
    }

    private void onPipelineConfigurationChanged() {
        pipelineBlocked = false;
        outputReadyTick = level == null ? outputReadyTick : Math.min(outputReadyTick, level.getGameTime());
        invalidatePipelineStorageCache();
        saveChanges();
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
        boolean newFormed = MolecularCenterStructure.matches(level, worldPosition, facing);
        if (newFormed != formed) {
            formed = newFormed;
            level.setBlock(worldPosition, getBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, formed), 3);
            onGridConnectableSidesChanged();
            syncShellConnections(newFormed);
            invalidatePipelineStorageCache();
            IStorageProvider.requestUpdate(getMainNode());
            getLogic().updatePatterns();
            saveChanges();
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.update(serverLevel, worldPosition, formed);
        }
    }

    private void unregisterSpawnProtection() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.unregister(serverLevel, worldPosition);
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        processWorkQueue();
        flushBufferedOutputs(gameTime);
        flushLegacyDeconstructRefund();
        processMatterJobs();
        syncVisualActivity(gameTime, false);
        if (structureCheckTick != gameTime && gameTime % 20 == 0) {
            structureCheckTick = gameTime;
            refreshStructure();
            updateQuantumLink();
            if (entropy > 0) {
                entropy = Math.max(0, entropy - 25);
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

    public void startBuild(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!MolecularCenterStructure.isWithinBuildHeight(level, worldPosition)
                || !MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.build_area_unavailable"), false);
            return;
        }
        var missingParts = findMissingOnlyParts(facing);
        if (missingParts != null && missingParts.isEmpty()) {
            building = false;
            dismantling = false;
            repairOnlyBuild = false;
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

    public void startDismantle(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        dismantling = true;
        building = false;
        repairOnlyBuild = false;
        buildQueueInitialized = false;
        buildWorkParts = List.of();
        formed = false;
        unregisterSpawnProtection();
        level.setBlock(worldPosition, getBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3);
        onGridConnectableSidesChanged();
        syncShellConnections(false);
        IStorageProvider.requestUpdate(getMainNode());
        updateQuantumLink();
        workCursor = MolecularCenterStructure.workParts().size() - 1;
        workTotal = MolecularCenterStructure.workParts().size();
        workConflicts = 0;
        workOwner = player.getUUID();
        getLogic().updatePatterns();
        saveChanges();
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
                if (MolecularCenterStructure.isStructurePart(state)) {
                    return null;
                }
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
        if (!MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            return;
        }
        if (repairOnlyBuild) {
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
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        player.displayClientMessage(Component.translatable("message.molecularmanipulator.preview_legend"), false);
        if (!MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.preview_cross_chunk"),
                    false);
        }
        if (!MolecularCenterStructure.isWithinBuildHeight(level, worldPosition)) {
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.preview_build_height"),
                    false);
        }
    }

    private void processWorkQueue() {
        if (level == null || level.isClientSide() || (!building && !dismantling) || workOwner == null) {
            return;
        }
        var player = level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(workOwner);
        if (player == null || !player.mayBuild()) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!MolecularCenterStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            return;
        }
        restoreBuildQueue();
        if (building && !buildQueueInitialized) {
            return;
        }
        int budget = ModConfig.BUILD_BLOCKS_PER_TICK.get();
        var workParts = building ? buildWorkParts : MolecularCenterStructure.workParts();
        int partCount = workParts.size();
        var materialSources = building ? findBuildMaterialSources(player) : List.<NetworkMaterialSource>of();
        workTotal = partCount;
        while (budget-- > 0 && (building || dismantling)) {
            if (building && workCursor >= partCount) {
                building = false;
                repairOnlyBuild = false;
                buildQueueInitialized = false;
                buildWorkParts = List.of();
                refreshStructure();
                break;
            }
            if (dismantling && workCursor < 0) {
                dismantling = false;
                refreshStructure();
                break;
            }
            if (workCursor < 0 || workCursor >= partCount) {
                building = false;
                dismantling = false;
                repairOnlyBuild = false;
                buildQueueInitialized = false;
                buildWorkParts = List.of();
                refreshStructure();
                break;
            }
            var part = workParts.get(workCursor);
            var pos = MolecularCenterStructure.worldPos(worldPosition, facing, part);
            if (MolecularCenterStructure.isController(part)) {
                advanceWork();
                continue;
            }
            if (building) {
                processBuildPart(player, part, pos, materialSources);
            } else {
                processDismantlePart(player, part, pos);
            }
            if (building && workCursor >= workTotal) {
                building = false;
                repairOnlyBuild = false;
                buildQueueInitialized = false;
                buildWorkParts = List.of();
                refreshStructure();
                break;
            } else if (dismantling && workCursor < 0) {
                dismantling = false;
                refreshStructure();
                break;
            }
        }
        setChanged();
    }

    private void syncShellConnections(boolean connected) {
        if (level == null) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (var part : MolecularCenterStructure.parts()) {
            if (part.partType() != MolecularCenterStructure.PartType.CASING
                    || MolecularCenterStructure.isController(part)) {
                continue;
            }
            var partPos = MolecularCenterStructure.worldPos(worldPosition, facing, part);
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
            if (removeLegacyStructurePart(player, pos)) {
                advanceWork();
            }
            return;
        }
        var current = level.getBlockState(pos);
        var expected = MolecularCenterStructure.partState(part.partType());
        if (current.is(expected.getBlock())) {
            advanceWork();
            return;
        }
        if (MolecularCenterStructure.isStructurePart(current)
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

    private void processDismantlePart(ServerPlayer player, MolecularCenterStructure.Part part, BlockPos pos) {
        var state = level.getBlockState(pos);
        boolean matches = part.partType() != MolecularCenterStructure.PartType.AIR
                && state.is(MolecularCenterStructure.partState(part.partType()).getBlock());
        if (!matches && !MolecularCenterStructure.isStructurePart(state)) {
            advanceWork();
            return;
        }
        var item = new ItemStack(state.getBlock().asItem());
        if (item.isEmpty() || !canStoreDismantled(player, item)) {
            return;
        }
        if (!level.destroyBlock(pos, false, player)) {
            workConflicts++;
            advanceWork();
            return;
        }
        storeDismantled(player, item);
        advanceWork();
    }

    private boolean extractBuildMaterial(ServerPlayer player, ItemStack template,
            List<NetworkMaterialSource> materialSources) {
        for (var stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, template) && !stack.isEmpty()) {
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
            if (slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, stack)
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
        workCursor += building ? 1 : -1;
    }

    boolean acceptPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (assembling || !isOperational() || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }
        var grid = getMainNode().getGrid();
        if (level == null || grid == null || !getLogic().getReturnInv().isEmpty()) {
            return false;
        }
        assembling = true;
        try {
            if (!craftingBatcher.prepare(patternDetails, inputs, level, VIRTUAL_PARALLEL_LIMIT)) {
                return false;
            }
            var primaryOutputs = craftingBatcher.getPrimaryOutputAmounts();
            var remainderOutputs = craftingBatcher.getRemainderOutputAmounts();
            if (!canQueueOutputs(primaryOutputs, remainderOutputs)) {
                return false;
            }
            addOutputs(pendingPrimaryOutputs, primaryOutputs);
            addOutputs(pendingByproducts, remainderOutputs);
            craftingBatcher.consumeInputs(inputs);
            fireCraftingEventOncePerTick(level, patternDetails, pattern);
            recordPipelineActivity(level.getGameTime(), craftingBatcher.getCraftCount());
            outputReadyTick = Math.max(outputReadyTick, level.getGameTime() + 1);
            markOutputBufferChanged(level.getGameTime());
            return true;
        } finally {
            assembling = false;
        }
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
            mergeChecked(totals, cachedPrimaryOutputs);
            mergeChecked(totals, cachedByproducts);
            mergeChecked(totals, primaryOutputs);
            mergeChecked(totals, remainderOutputs);
            return totals.size() <= MAX_BUFFERED_TYPES;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static void mergeChecked(Object2LongOpenHashMap<AEKey> totals,
            Object2LongOpenHashMap<AEKey> source) {
        for (var entry : source.object2LongEntrySet()) {
            totals.put(entry.getKey(), Math.addExact(totals.getLong(entry.getKey()), entry.getLongValue()));
        }
    }

    private void markOutputBufferChanged(long gameTime) {
        invalidatePipelineStorageCache();
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
            pipelineBlocked = hasNetworkRoutedOutputs();
            return;
        }

        assembling = true;
        try {
            var storage = grid.getStorageService().getInventory();
            var craftingService = grid.getCraftingService();
            boolean changed = false;
            boolean blocked = false;
            long transferred = 0;

            var primaryCacheResult = flushCached(cachedPrimaryOutputs, primaryRoute, storage);
            changed |= primaryCacheResult.changed();
            blocked |= primaryCacheResult.blocked();
            transferred = saturatedAdd(transferred, primaryCacheResult.transferred());

            var byproductCacheResult = flushCached(cachedByproducts, byproductRoute, storage);
            changed |= byproductCacheResult.changed();
            blocked |= byproductCacheResult.blocked();
            transferred = saturatedAdd(transferred, byproductCacheResult.transferred());

            if (gameTime >= outputReadyTick) {
                var primaryResult = flushPending(pendingPrimaryOutputs, cachedPrimaryOutputs,
                        primaryRoute, storage, craftingService);
                changed |= primaryResult.changed();
                blocked |= primaryResult.blocked();
                transferred = saturatedAdd(transferred, primaryResult.transferred());

                var byproductResult = flushPending(pendingByproducts, cachedByproducts,
                        byproductRoute, storage, craftingService);
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
                invalidatePipelineStorageCache();
                saveChanges();
            }
        } finally {
            assembling = false;
        }
    }

    private FlushResult flushCached(Object2LongOpenHashMap<AEKey> cached,
            PipelineRoute route, MEStorage storage) {
        if (route == PipelineRoute.INTERNAL || cached.isEmpty()) {
            return FlushResult.EMPTY;
        }
        return route == PipelineRoute.NETWORK
                ? flushMapToNetwork(cached, storage)
                : flushMapToPort(cached);
    }

    private FlushResult flushPending(Object2LongOpenHashMap<AEKey> pending,
            Object2LongOpenHashMap<AEKey> cache, PipelineRoute route, MEStorage storage,
            appeng.api.networking.crafting.ICraftingService craftingService) {
        if (pending.isEmpty()) {
            return FlushResult.EMPTY;
        }

        boolean changed = false;
        boolean blocked = false;
        long transferred = 0;
        flushScratch.clear();
        for (var entry : pending.object2LongEntrySet()) {
            var key = entry.getKey();
            long remaining = entry.getLongValue();

            long requested = Math.min(remaining, craftingService.getRequestedAmount(key));
            if (requested > 0) {
                long delivered = storage.insert(key, requested, Actionable.MODULATE, actionSource);
                remaining -= delivered;
                transferred = saturatedAdd(transferred, delivered);
                changed |= delivered > 0;
            }

            if (remaining > 0 && route == PipelineRoute.NETWORK) {
                long inserted = storage.insert(key, remaining, Actionable.MODULATE, actionSource);
                remaining -= inserted;
                transferred = saturatedAdd(transferred, inserted);
                changed |= inserted > 0;
                blocked |= remaining > 0;
            } else if (remaining > 0 && route == PipelineRoute.PORT) {
                long inserted = insertIntoOutputPort(key, remaining);
                remaining -= inserted;
                transferred = saturatedAdd(transferred, inserted);
                changed |= inserted > 0;
                blocked |= remaining > 0;
            } else if (remaining > 0) {
                cache.put(key, Math.addExact(cache.getLong(key), remaining));
                transferred = saturatedAdd(transferred, remaining);
                remaining = 0;
                changed = true;
            }

            if (remaining > 0) {
                flushScratch.put(key, remaining);
            }
        }
        pending.clear();
        pending.putAll(flushScratch);
        flushScratch.clear();
        return new FlushResult(changed, blocked, transferred);
    }

    private FlushResult flushMapToNetwork(Object2LongOpenHashMap<AEKey> source, MEStorage storage) {
        boolean changed = false;
        long transferred = 0;
        flushScratch.clear();
        for (var entry : source.object2LongEntrySet()) {
            long inserted = storage.insert(entry.getKey(), entry.getLongValue(),
                    Actionable.MODULATE, actionSource);
            long remaining = entry.getLongValue() - inserted;
            if (remaining > 0) {
                flushScratch.put(entry.getKey(), remaining);
            }
            changed |= inserted > 0;
            transferred = saturatedAdd(transferred, inserted);
        }
        source.clear();
        source.putAll(flushScratch);
        flushScratch.clear();
        return new FlushResult(changed, !source.isEmpty(), transferred);
    }

    private FlushResult flushMapToPort(Object2LongOpenHashMap<AEKey> source) {
        boolean changed = false;
        long transferred = 0;
        int budget = MAX_PORT_ITEMS_PER_TICK;
        flushScratch.clear();
        for (var entry : source.object2LongEntrySet()) {
            long inserted = budget > 0
                    ? insertIntoOutputPort(entry.getKey(), Math.min(entry.getLongValue(), budget))
                    : 0;
            budget -= (int) inserted;
            long remaining = entry.getLongValue() - inserted;
            if (remaining > 0) {
                flushScratch.put(entry.getKey(), remaining);
            }
            changed |= inserted > 0;
            transferred = saturatedAdd(transferred, inserted);
        }
        source.clear();
        source.putAll(flushScratch);
        flushScratch.clear();
        return new FlushResult(changed, !source.isEmpty() && !changed, transferred);
    }

    private long insertIntoOutputPort(AEKey key, long amount) {
        if (level == null || amount <= 0 || !(key instanceof AEItemKey itemKey)) {
            return 0;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var target = outputPort.resolve(worldPosition, facing);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                target.pos(), target.accessSide());
        if (handler == null) {
            return 0;
        }

        int requested = (int) Math.min(amount, MAX_PORT_ITEMS_PER_TICK);
        var stack = itemKey.toStack(requested);
        var remainder = ItemHandlerHelper.insertItemStacked(handler, stack, false);
        return requested - remainder.getCount();
    }

    private boolean hasNetworkRoutedOutputs() {
        return primaryRoute != PipelineRoute.INTERNAL
                && (!pendingPrimaryOutputs.isEmpty() || !cachedPrimaryOutputs.isEmpty())
                || byproductRoute != PipelineRoute.INTERNAL
                && (!pendingByproducts.isEmpty() || !cachedByproducts.isEmpty());
    }

    private void invalidatePipelineStorageCache() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getStorageService().invalidateCache();
        }
    }

    private static long saturatedSum(Object2LongOpenHashMap<AEKey> first,
            Object2LongOpenHashMap<AEKey> second) {
        long total = 0;
        for (var entry : first.object2LongEntrySet()) {
            total = saturatedAdd(total, entry.getLongValue());
        }
        for (var entry : second.object2LongEntrySet()) {
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

    public boolean schedulePatternRebuild(Runnable rebuild) {
        if (level != null && !level.isClientSide() && level.getServer() != null) {
            level.getServer().execute(rebuild);
            return true;
        }
        return false;
    }

    public void openMenu(Player player, appeng.menu.locator.MenuHostLocator locator) {
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
        TARGET_REACHED,
        INPUT_EMPTY,
        BLUEPRINT_EMPTY,
        BLUEPRINT_CHANGED,
        UNSUPPORTED,
        SEQUENCE_STORAGE_FULL,
        INSUFFICIENT_SEQUENCE,
        OUTPUT_FULL
    }

    public enum PipelineRoute {
        NETWORK("network"),
        INTERNAL("internal"),
        PORT("port");

        private final String serializedName;

        PipelineRoute(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }

        public PipelineRoute next() {
            return switch (this) {
                case NETWORK -> INTERNAL;
                case INTERNAL -> PORT;
                case PORT -> NETWORK;
            };
        }

        public static PipelineRoute fromSerializedName(String name) {
            for (var route : values()) {
                if (route.serializedName.equals(name)) {
                    return route;
                }
            }
            return NETWORK;
        }
    }

    public enum PipelinePort {
        FRONT("front"),
        BACK("back"),
        LEFT("left"),
        RIGHT("right"),
        UP("up"),
        DOWN("down");

        private final String serializedName;

        PipelinePort(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }

        public PipelinePort next() {
            var values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private OutputTarget resolve(BlockPos controller, Direction facing) {
            var portPart = switch (this) {
                case FRONT -> new MolecularCenterStructure.Part(0, 13, MolecularCenterStructure.MIN_Z,
                        MolecularCenterStructure.PartType.CASING);
                case BACK -> new MolecularCenterStructure.Part(0, 13, MolecularCenterStructure.MAX_Z,
                        MolecularCenterStructure.PartType.CASING);
                case LEFT -> new MolecularCenterStructure.Part(MolecularCenterStructure.MIN_X, 13,
                        MolecularCenterStructure.CENTER_Z, MolecularCenterStructure.PartType.CASING);
                case RIGHT -> new MolecularCenterStructure.Part(MolecularCenterStructure.MAX_X, 13,
                        MolecularCenterStructure.CENTER_Z, MolecularCenterStructure.PartType.CASING);
                case UP -> new MolecularCenterStructure.Part(0, MolecularCenterStructure.HEIGHT - 1,
                        MolecularCenterStructure.CENTER_Z, MolecularCenterStructure.PartType.STABILIZER);
                case DOWN -> new MolecularCenterStructure.Part(0, 0, MolecularCenterStructure.CENTER_Z,
                        MolecularCenterStructure.PartType.STABILIZER);
            };
            var outward = switch (this) {
                case FRONT -> facing;
                case BACK -> facing.getOpposite();
                case LEFT -> facing.getCounterClockWise();
                case RIGHT -> facing.getClockWise();
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            var shellPos = MolecularCenterStructure.worldPos(controller, facing, portPart);
            return new OutputTarget(shellPos.relative(outward), outward.getOpposite());
        }

        public static PipelinePort fromSerializedName(String name) {
            for (var port : values()) {
                if (port.serializedName.equals(name)) {
                    return port;
                }
            }
            return FRONT;
        }
    }

    private final class PipelineStorageProvider implements IStorageProvider {
        private final MEStorage storage = new MEStorage() {
            @Override
            public boolean isPreferredStorageFor(AEKey what,
                    appeng.api.networking.security.IActionSource source) {
                return cachedPrimaryOutputs.containsKey(what) || cachedByproducts.containsKey(what);
            }

            @Override
            public long extract(AEKey what, long amount, Actionable mode,
                    appeng.api.networking.security.IActionSource source) {
                if (amount <= 0) {
                    return 0;
                }
                long available = saturatedAdd(cachedPrimaryOutputs.getLong(what),
                        cachedByproducts.getLong(what));
                long extracted = Math.min(amount, available);
                if (mode == Actionable.MODULATE && extracted > 0) {
                    long remaining = extractFrom(cachedPrimaryOutputs, what, extracted);
                    extractFrom(cachedByproducts, what, remaining);
                    long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
                    markOutputBufferChanged(gameTime);
                }
                return extracted;
            }

            @Override
            public void getAvailableStacks(KeyCounter out) {
                for (var entry : cachedPrimaryOutputs.object2LongEntrySet()) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                for (var entry : cachedByproducts.object2LongEntrySet()) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }

            @Override
            public Component getDescription() {
                return Component.translatable("gui.molecularmanipulator.pipeline_storage");
            }
        };

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            if (formed) {
                storageMounts.mount(storage, PIPELINE_STORAGE_PRIORITY);
            }
        }
    }

    private static long extractFrom(Object2LongOpenHashMap<AEKey> source, AEKey key, long amount) {
        long available = source.getLong(key);
        long extracted = Math.min(available, amount);
        if (extracted >= available) {
            source.removeLong(key);
        } else if (extracted > 0) {
            source.put(key, available - extracted);
        }
        return amount - extracted;
    }

    private record FlushResult(boolean changed, boolean blocked, long transferred) {
        private static final FlushResult EMPTY = new FlushResult(false, false, 0);
    }

    private record OutputTarget(BlockPos pos, Direction accessSide) {
    }
}
