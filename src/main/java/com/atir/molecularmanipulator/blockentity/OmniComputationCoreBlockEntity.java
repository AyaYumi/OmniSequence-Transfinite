package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingServiceBridge;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.atir.molecularmanipulator.mixin.CraftingCPUClusterAccessor;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.MolecularCenterSpawnProtection;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class OmniComputationCoreBlockEntity extends CraftingBlockEntity implements InternalInventoryHost {
    public static final long INFINITE_STORAGE = Long.MAX_VALUE;
    public static final long INFINITE_PARALLELISM = Long.MAX_VALUE;
    public static final int AE2_MAX_THREADS_PER_BLOCK = 16;
    public static final int AE2_PARALLELISM_SENTINEL = Integer.MAX_VALUE;
    public static final double IDLE_POWER = 8_192.0;
    public static final double QUANTUM_LINK_POWER = 512.0;
    private static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final int BUILD_BLOCKS_PER_TICK = 128;
    private static final long COMPAT_DISPATCH_TARGET_TICK_NANOS = 45_000_000L;
    private static final long COMPAT_DISPATCH_MIN_GLOBAL_NANOS = 250_000L;
    private static final String VIRTUAL_CPUS_TAG = "omni_virtual_cpus";
    private static final String SUSPENDED_CPUS_TAG = "omni_suspended_cpus";
    private static final String VIRTUAL_CPU_LANE_ID_TAG = "omni_lane_id";
    private static final String VIRTUAL_CPU_STATE_TAG = "omni_cpu_state";
    private static final String NEXT_VIRTUAL_CPU_LANE_ID_TAG = "omni_next_lane_id";
    private static final long PRIMARY_CPU_LANE_ID = 0L;
    private static final String QUANTUM_INVENTORY_TAG = "omni_quantum_inventory";
    private static final String LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG =
            "omni_legacy_structure_update_dismissed";
    private static final String STRUCTURE_FORMED_TAG = "omni_structure_formed";
    private static final String KNOWN_LAYOUT_TAG = "omni_known_structure_layout";
    private static final String CURRENT_BLUEPRINT_VERSION_TAG = "omni_current_blueprint_version";
    private static final String UPGRADE_ACTIVE_TAG = "omni_upgrade_active";
    private static final String UPGRADE_SOURCE_TAG = "omni_upgrade_source_layout";
    private static final String UPGRADE_QUEUE_TAG = "omni_upgrade_queue";
    private static final String UPGRADE_CURSOR_TAG = "omni_upgrade_cursor";
    private static final String UPGRADE_OWNER_TAG = "omni_upgrade_owner";
    private static final String DISMANTLE_PLAN_TAG = "omni_dismantle_plan";
    private static final String DISMANTLE_ACTIVE_TAG = "omni_dismantle_active";
    private static final String DISMANTLE_OWNER_TAG = "omni_dismantle_owner";
    private static final Map<CraftingCPUCluster, OmniComputationCoreBlockEntity> CPU_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, SharedCompatDispatchState>
            SHARED_COMPAT_DISPATCH_STATES =
                    Collections.synchronizedMap(new WeakHashMap<>());

    private final List<CraftingCPUCluster> virtualCpus = new ArrayList<>();
    private final Map<CraftingCPUCluster, Long> cpuLaneIds = new IdentityHashMap<>();
    private final List<PersistedCpuState> pendingVirtualCpuStates = new ArrayList<>();
    private boolean retiringStoredCpus;
    private final List<PersistedCpuState> suspendedCpuStates = new ArrayList<>();
    private final AtomicInteger activeMaterialCalculations = new AtomicInteger();
    private final AtomicLong completedMaterialCalculations = new AtomicLong();
    private final AtomicLong lastMaterialCalculationNanos = new AtomicLong();
    private final AppEngInternalInventory quantumInventory = new AppEngInternalInventory(this, 1);
    private OmniComputationStructure.Inspection inspection =
            new OmniComputationStructure.Inspection(OmniComputationStructure.parts().size(), 0,
                    OmniComputationStructure.parts().size(), 0, false,
                    OmniComputationStructure.StructureLayout.INCOMPLETE);
    private boolean structureFormed;
    private boolean legacyStructureUpdateDismissed;
    private boolean restoredCpuState;
    private long nextVirtualCpuLaneId = 1L;
    private long nextStructureCheck;
    private long dispatchBudgetTick = Long.MIN_VALUE;
    private long dispatchWorkUnitsRemaining;
    private long dispatchReservedWorkUnits;
    private long dispatchAdaptiveWorkUnits;
    private int dispatchLaneRotation;
    private final Map<CraftingCPUCluster, Long> dispatchLaneAllowances = new IdentityHashMap<>();
    private int dispatchUnscaledAttemptsRemaining;
    private int dispatchUnscaledReservedAttempts;
    private int dispatchUnscaledTotalAttempts;
    private int dispatchUnscaledLaneRotation;
    private final Map<CraftingCPUCluster, Integer> dispatchUnscaledLaneAllowances =
            new IdentityHashMap<>();
    private final Set<CraftingCPUCluster> dispatchUnscaledDemandLanes =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<CraftingCPUCluster> dispatchUnscaledDemandThisTick =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean building;
    private boolean dismantling;
    private boolean rebuildingLegacyStructure;
    private OmniComputationStructure.StructureLayout upgradeSourceLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
    private Map<BlockPos, Block> upgradeSourceBlocks = Map.of();
    private boolean upgradeBlockedNotified;
    private OmniComputationStructure.StructureLayout knownLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
    private DismantlePlan dismantlePlan;
    private boolean dismantleBlockedNotified;
    private int dismantlableBlocks;
    private List<OmniComputationStructure.Part> buildQueue = List.of();
    private int buildCursor;
    private int buildTotal;
    private UUID buildOwner;
    private IGridConnection quantumConnection;
    private IGridNode quantumRemoteNode;
    private long quantumConnectionFrequency;
    private long claimedQuantumFrequency;
    private MolecularCenterBlockEntity.QuantumLinkState quantumLinkState =
            MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    private int syncedVisualActivity;
    private int clientVisualActivity;
    private OmniComputationStructure.StructureLayout visualLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
    private boolean visualFormed;
    private float clientVisualAngle;
    private double clientOrreryAngle;
    private float clientCompletionPulse;
    private double clientVisualSample = Double.NaN;

    public OmniComputationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.OMNI_COMPUTATION_CONTROLLER_BE.get(), pos, state);
        quantumInventory.setMaxStackSize(0, 1);
        getMainNode()
                .setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(IDLE_POWER);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModContent.OMNI_COMPUTATION_CONTROLLER_ITEM.get();
    }

    @Override
    public long getStorageBytes() {
        return structureFormed ? INFINITE_STORAGE : 0;
    }

    @Override
    public int getAcceleratorThreads() {
        return structureFormed ? AE2_MAX_THREADS_PER_BLOCK : 0;
    }

    public long getParallelismLimit() {
        return structureFormed ? INFINITE_PARALLELISM : 0;
    }

    @Override
    public boolean isFormed() {
        return structureFormed;
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void onReady() {
        super.onReady();
        scheduleStructureCheck();
        updateQuantumLink();
        if (level != null && !level.isClientSide()) {
            refreshStructureNow();
            markForClientUpdate();
        }
    }

    @Override
    public void onChunkUnloaded() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
        retireStoredCpus(true);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
        retireStoredCpus(true);
        super.setRemoved();
    }

    public boolean hasRemovalRecovery() {
        return !quantumInventory.isEmpty() || hasStoredCpuContents();
    }

    private boolean hasStoredCpuContents() {
        if (!pendingVirtualCpuStates.isEmpty() || !suspendedCpuStates.isEmpty() || getPreviousState() != null) return true;
        return allCpus().stream().anyMatch(cpu -> cpu.isBusy() || !cpu.craftingLogic.getInventory().list.isEmpty());
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (!hasRemovalRecovery()) return;
        var contents = new CompoundTag();
        quantumInventory.writeToNBT(contents, QUANTUM_INVENTORY_TAG, level.registryAccess());
        if (hasStoredCpuContents()) {
            var cpus = new ListTag();
            for (var state : snapshotStoredCpus()) cpus.add(wrapCpuState(state.laneId(), state.state()));
            contents.put(SUSPENDED_CPUS_TAG, cpus);
            contents.putLong(NEXT_VIRTUAL_CPU_LANE_ID_TAG, nextVirtualCpuLaneId);
        }
        drops.add(RetainedBlockContents.createDrop(this, contents));
    }

    private List<PersistedCpuState> snapshotStoredCpus() {
        if (!suspendedCpuStates.isEmpty()) return List.copyOf(suspendedCpuStates);
        var result = new ArrayList<PersistedCpuState>();
        var primary = getCluster();
        var primaryTag = new CompoundTag();
        if (primary != null && !primary.isDestroyed()) primary.writeToNBT(primaryTag, level.registryAccess());
        else if (getPreviousState() != null) primaryTag = getPreviousState().copy();
        // The suspended format's first record always represents the physical CPU.
        result.add(new PersistedCpuState(PRIMARY_CPU_LANE_ID, primaryTag));
        if (!virtualCpus.isEmpty()) {
            for (var cpu : virtualCpus) {
                if (cpu.isDestroyed()) continue;
                var tag = new CompoundTag();
                cpu.writeToNBT(tag, level.registryAccess());
                result.add(new PersistedCpuState(laneId(cpu), tag));
            }
        } else result.addAll(pendingVirtualCpuStates);
        return result;
    }

    private void retireStoredCpus(boolean retain) {
        if (level == null || level.isClientSide() || retiringStoredCpus) return;
        var snapshots = retain && hasStoredCpuContents() ? snapshotStoredCpus() : List.<PersistedCpuState>of();
        var cpus = allCpus();
        var grid = getMainNode().getGrid();
        var bridge = grid != null && grid.getCraftingService() instanceof OmniCraftingServiceBridge service ? service : null;
        retiringStoredCpus = true;
        try {
            for (var cpu : cpus) {
                if (bridge != null) bridge.molecularmanipulator$unregisterOmniCpu(cpu);
                CPU_OWNERS.remove(cpu, this);
                cpu.destroy();
                cpu.craftingLogic.getInventory().clear();
            }
        } finally {
            retiringStoredCpus = false;
        }
        virtualCpus.clear();
        cpuLaneIds.clear();
        pendingVirtualCpuStates.clear();
        suspendedCpuStates.clear();
        suspendedCpuStates.addAll(snapshots);
        setPreviousState(null);
        setCoreBlock(false);
        restoredCpuState = false;
    }

    @Override
    public void breakCluster() {
        // destroy() calls updateStatus(null), which would normally cancel and drop
        // the CPU inventory. Those assets are already in the portable snapshot.
        if (!retiringStoredCpus) super.breakCluster();
    }

    @Override
    public void clearContent() {
        super.clearContent();
        quantumInventory.clear();
        retireStoredCpus(false);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        if (inventory == quantumInventory && level != null && !level.isClientSide()) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
            updateQuantumLink();
        }
    }

    @Override
    public void updateSubType(boolean updateFormed) {
        syncVisualStructure();
        if (level == null || notLoaded() || isRemoved()) {
            return;
        }
        var current = level.getBlockState(worldPosition);
        if (!current.is(ModContent.OMNI_COMPUTATION_CONTROLLER.get())) {
            return;
        }
        boolean powered = structureFormed && getMainNode().isActive();
        if (current.getValue(BlockStateProperties.POWERED) != powered) {
            level.setBlock(worldPosition, current.setValue(BlockStateProperties.POWERED, powered), 3);
        }
        if (updateFormed) {
            onGridConnectableSidesChanged();
        }
    }

    public Set<ChunkPos> getChunkLoadingChunks() {
        var result = new HashSet<ChunkPos>();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (structureFormed) result.addAll(OmniComputationStructure.chunkFootprint(worldPosition, facing, inspection.layout()));
        if (building || dismantling || rebuildingLegacyStructure) {
            result.addAll(OmniComputationStructure.chunkFootprint(worldPosition, facing, knownLayout));
            if (building || rebuildingLegacyStructure) result.addAll(OmniComputationStructure.chunkFootprint(
                    worldPosition, facing, OmniComputationStructure.StructureLayout.CURRENT));
            if (rebuildingLegacyStructure) result.addAll(OmniComputationStructure.chunkFootprint(worldPosition, facing,
                    upgradeSourceLayout, OmniComputationStructure.StructureLayout.CURRENT));
            if (dismantlePlan != null) result.addAll(dismantlePlan.remainingChunks());
        }
        return result;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (building || dismantling || rebuildingLegacyStructure) MultiblockChunkLoading.maintain(this);
        long gameTime = level.getGameTime();
        if (building) {
            processBuild();
        } else if (dismantling) {
            processDismantle();
        }
        if (gameTime >= nextStructureCheck) {
            refreshStructureNow();
            nextStructureCheck = gameTime + STRUCTURE_CHECK_INTERVAL;
        }
        if (!structureFormed) {
            updateSubType(false);
            syncVisualActivity(0);
            return;
        }

        // Keep the controller model in sync even while AE2 is still rebuilding its
        // crafting cluster after a world or chunk load. Cluster restoration may
        // legitimately take longer than grid activation and must not leave a formed,
        // online controller displaying its inactive texture.
        updateSubType(false);

        if (getCluster() == null) {
            updateMultiBlock(worldPosition);
        }
        var primary = getCluster();
        if (primary == null || primary.isDestroyed()) {
            syncVisualActivity(activeMaterialCalculations.get());
            return;
        }
        cpuLaneIds.putIfAbsent(primary, PRIMARY_CPU_LANE_ID);
        CPU_OWNERS.put(primary, this);
        restoreAdditionalCpus(primary);
        ensureOneIdleCpu();
        registerCpusWithGrid();
        syncVisualActivity(getMainNode().isActive()
                ? getActiveJobCount() + activeMaterialCalculations.get()
                : 0);
    }

    private void syncVisualActivity(int activity) {
        int nextActivity = Math.max(0, Math.min(255, activity));
        if (syncedVisualActivity != nextActivity) {
            syncedVisualActivity = nextActivity;
            markForClientUpdate();
        }
    }

    private void syncVisualStructure() {
        if (level == null || level.isClientSide() || isRemoved()) return;
        boolean nextFormed = structureFormed && inspection.formed();
        var nextLayout = nextFormed ? inspection.layout() : OmniComputationStructure.StructureLayout.INCOMPLETE;
        if (visualFormed != nextFormed || visualLayout != nextLayout) {
            visualFormed = nextFormed;
            visualLayout = nextLayout;
            markForClientUpdate();
        }
    }

    public void scheduleStructureCheck() {
        if (level != null) {
            nextStructureCheck = Math.min(nextStructureCheck, level.getGameTime() + 1);
        } else {
            nextStructureCheck = 0;
        }
    }

    public void refreshStructureNow() {
        if (level == null || level.isClientSide()) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var nextInspection = OmniComputationStructure.inspect(level, worldPosition, facing);
        boolean wasFormed = structureFormed;
        inspection = nextInspection;
        structureFormed = nextInspection.formed();
        if (nextInspection.formed()) {
            knownLayout = nextInspection.layout();
        }
        dismantlableBlocks = dismantlePlan != null ? dismantlePlan.remaining()
                : OmniComputationStructure.countDismantlableBlocks(level, worldPosition, facing, knownLayout);
        if (nextInspection.layout() == OmniComputationStructure.StructureLayout.CURRENT) {
            legacyStructureUpdateDismissed = false;
        }

        if (structureFormed && !wasFormed) {
            restoredCpuState = false;
            updateMultiBlock(worldPosition);
            var primary = getCluster();
            if (primary != null) {
                CPU_OWNERS.put(primary, this);
            }
        } else if (!structureFormed && wasFormed) {
            // Keep every CPU object and its inventory alive while the fixed structure is
            // incomplete. OmniCraftingCpuClusterMixin makes these lanes inactive until
            // the structure is repaired, so jobs resume instead of being cancelled.
            for (var cpu : allCpus()) {
                CPU_OWNERS.put(cpu, this);
            }
        }

        updateSubType(true);
        updateQuantumLink();
        updateSpawnProtection();
        setChanged();
        MultiblockChunkLoading.maintain(this);
    }

    private void updateSpawnProtection() {
        if (level instanceof ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.updateOmni(serverLevel, worldPosition, structureFormed,
                    inspection.layout());
        }
    }

    private void unregisterSpawnProtection() {
        if (level instanceof ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.unregister(serverLevel, worldPosition);
        }
    }

    public AppEngInternalInventory getQuantumInventory() {
        return quantumInventory;
    }

    public long getQuantumFrequency() {
        var stack = quantumInventory.getStackInSlot(0);
        if (!MolecularCenterBlockEntity.isValidQuantumSingularity(stack)) {
            return 0;
        }
        return stack.getOrDefault(AEComponents.ENTANGLED_SINGULARITY_ID, 0L);
    }

    public MolecularCenterBlockEntity.QuantumLinkState getQuantumLinkState() {
        return quantumLinkState;
    }

    private void updateQuantumLink() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long frequency = getQuantumFrequency();
        if (frequency == 0) {
            releaseQuantumFrequency();
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.EMPTY);
            return;
        }
        if (!claimQuantumFrequency(serverLevel, frequency)) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.FREQUENCY_OCCUPIED);
            return;
        }
        if (!getMainNode().isReady()) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
            return;
        }
        var localNode = getMainNode().getNode();
        if (localNode == null) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
            return;
        }
        if (!localNode.getInWorldConnections().isEmpty()) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.WIRED_CONFLICT);
            return;
        }

        var positiveEndpoint = Locatables.quantumNetworkBridges().get(serverLevel, frequency);
        var negativeEndpoint = Locatables.quantumNetworkBridges().get(serverLevel, -frequency);
        if (positiveEndpoint != null && negativeEndpoint != null && positiveEndpoint != negativeEndpoint) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.FREQUENCY_OCCUPIED);
            return;
        }
        var remoteHost = positiveEndpoint != null ? positiveEndpoint : negativeEndpoint;
        if (remoteHost == null) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.REMOTE_MISSING);
            return;
        }
        var remoteNode = remoteHost.getActionableNode();
        if (remoteNode == null || remoteNode == localNode) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.REMOTE_MISSING);
            return;
        }
        if (!remoteNode.isOnline()) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.REMOTE_OFFLINE);
            return;
        }
        if (isQuantumConnectionCurrent(localNode, remoteNode, frequency)) {
            quantumLinkState = localNode.isOnline()
                    ? connectedQuantumLinkState()
                    : MolecularCenterBlockEntity.QuantumLinkState.REMOTE_OFFLINE;
            return;
        }

        disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
        try {
            quantumConnection = GridHelper.createConnection(localNode, remoteNode);
            quantumRemoteNode = remoteNode;
            quantumConnectionFrequency = frequency;
            getMainNode().setIdlePowerUsage(IDLE_POWER + QUANTUM_LINK_POWER);
            quantumLinkState = connectedQuantumLinkState();
            saveChanges();
        } catch (IllegalStateException exception) {
            disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState.CONNECTION_ERROR);
        }
    }

    private boolean isQuantumConnectionCurrent(IGridNode localNode, IGridNode remoteNode, long frequency) {
        return quantumConnection != null
                && quantumRemoteNode == remoteNode
                && quantumConnectionFrequency == frequency
                && localNode.getConnections().contains(quantumConnection)
                && remoteNode.getConnections().contains(quantumConnection);
    }

    private MolecularCenterBlockEntity.QuantumLinkState connectedQuantumLinkState() {
        return structureFormed
                ? MolecularCenterBlockEntity.QuantumLinkState.CONNECTED
                : MolecularCenterBlockEntity.QuantumLinkState.CONNECTED_BUILD_ONLY;
    }

    private void disconnectQuantumLink(MolecularCenterBlockEntity.QuantumLinkState nextState) {
        var connection = quantumConnection;
        quantumConnection = null;
        quantumRemoteNode = null;
        quantumConnectionFrequency = 0;
        quantumLinkState = nextState;
        var mainNode = getMainNode();
        if (mainNode.getNode() != null) {
            mainNode.setIdlePowerUsage(IDLE_POWER);
        }
        if (connection != null) {
            try {
                connection.destroy();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void clearQuantumLinkForRemoval(MolecularCenterBlockEntity.QuantumLinkState nextState) {
        quantumConnection = null;
        quantumRemoteNode = null;
        quantumConnectionFrequency = 0;
        quantumLinkState = nextState;
    }

    private boolean claimQuantumFrequency(ServerLevel serverLevel, long frequency) {
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
        if (claimedQuantumFrequency == 0 || !(level instanceof ServerLevel serverLevel)) {
            claimedQuantumFrequency = 0;
            return;
        }
        EntangledQuantumFrequencyRegistry.release(serverLevel, worldPosition, claimedQuantumFrequency);
        claimedQuantumFrequency = 0;
    }

    private void restoreAdditionalCpus(CraftingCPUCluster primary) {
        if (restoredCpuState || level == null) {
            return;
        }
        restoredCpuState = true;

        if (!suspendedCpuStates.isEmpty()) {
            var primaryState = suspendedCpuStates.getFirst();
            primary.readFromNBT(primaryState.state(), level.registryAccess());
            cpuLaneIds.put(primary, PRIMARY_CPU_LANE_ID);
            for (int index = 1; index < suspendedCpuStates.size(); index++) {
                var state = suspendedCpuStates.get(index);
                var cpu = createVirtualCpu(state.laneId());
                cpu.readFromNBT(state.state(), level.registryAccess());
            }
            suspendedCpuStates.clear();
            pendingVirtualCpuStates.clear();
            return;
        }

        for (var state : pendingVirtualCpuStates) {
            var cpu = createVirtualCpu(state.laneId());
            cpu.readFromNBT(state.state(), level.registryAccess());
        }
        pendingVirtualCpuStates.clear();
    }

    private CraftingCPUCluster createVirtualCpu() {
        return createVirtualCpu(allocateVirtualCpuLaneId());
    }

    private CraftingCPUCluster createVirtualCpu(long requestedLaneId) {
        long laneId = claimVirtualCpuLaneId(requestedLaneId);
        var cpu = new CraftingCPUCluster(worldPosition, worldPosition);
        var accessor = (CraftingCPUClusterAccessor) (Object) cpu;
        accessor.molecularmanipulator$addBlockEntity(this);
        accessor.molecularmanipulator$finishCluster();
        virtualCpus.add(cpu);
        cpuLaneIds.put(cpu, laneId);
        CPU_OWNERS.put(cpu, this);
        setChanged();
        return cpu;
    }

    private long allocateVirtualCpuLaneId() {
        long candidate = Math.max(1L, nextVirtualCpuLaneId);
        while (cpuLaneIds.containsValue(candidate) && candidate < Long.MAX_VALUE) {
            candidate++;
        }
        if (cpuLaneIds.containsValue(candidate)) {
            throw new IllegalStateException("OmniSequence virtual CPU lane id space exhausted");
        }
        nextVirtualCpuLaneId = candidate == Long.MAX_VALUE ? Long.MAX_VALUE : candidate + 1L;
        return candidate;
    }

    private long claimVirtualCpuLaneId(long requestedLaneId) {
        if (requestedLaneId <= PRIMARY_CPU_LANE_ID
                || cpuLaneIds.containsValue(requestedLaneId)) {
            return allocateVirtualCpuLaneId();
        }
        if (requestedLaneId >= nextVirtualCpuLaneId) {
            nextVirtualCpuLaneId = requestedLaneId == Long.MAX_VALUE
                    ? Long.MAX_VALUE : requestedLaneId + 1L;
        }
        return requestedLaneId;
    }

    public void ensureOneIdleCpu() {
        if (!structureFormed || getCluster() == null) {
            return;
        }
        int idleCount = getCluster().isBusy() ? 0 : 1;
        for (var cpu : virtualCpus) {
            if (!cpu.isBusy() && !cpu.isDestroyed()) {
                idleCount++;
            }
        }
        if (idleCount == 0) {
            createVirtualCpu();
            idleCount = 1;
        }

        if (idleCount > 1) {
            var grid = getMainNode().getGrid();
            var bridge = grid != null && grid.getCraftingService() instanceof OmniCraftingServiceBridge service
                    ? service : null;
            Iterator<CraftingCPUCluster> iterator = virtualCpus.iterator();
            while (iterator.hasNext() && idleCount > 1) {
                var cpu = iterator.next();
                if (!cpu.isBusy()) {
                    iterator.remove();
                    cpuLaneIds.remove(cpu);
                    CPU_OWNERS.remove(cpu, this);
                    if (bridge != null) {
                        bridge.molecularmanipulator$unregisterOmniCpu(cpu);
                    }
                    idleCount--;
                    setChanged();
                }
            }
        }
    }

    public CraftingCPUCluster getOrCreateIdleCpu(OmniCraftingServiceBridge bridge) {
        if (!structureFormed || getCluster() == null) {
            return null;
        }
        for (var cpu : allCpus()) {
            if (!cpu.isBusy() && !cpu.isDestroyed()) {
                CPU_OWNERS.put(cpu, this);
                bridge.molecularmanipulator$registerOmniCpu(cpu);
                return cpu;
            }
        }
        var cpu = createVirtualCpu();
        bridge.molecularmanipulator$registerOmniCpu(cpu);
        return cpu;
    }

    public void ensureSpareAndRegister(OmniCraftingServiceBridge bridge) {
        if (!structureFormed || getCluster() == null) {
            return;
        }
        ensureOneIdleCpu();
        for (var cpu : allCpus()) {
            CPU_OWNERS.put(cpu, this);
            bridge.molecularmanipulator$registerOmniCpu(cpu);
        }
    }

    private void registerCpusWithGrid() {
        var grid = getMainNode().isActive() ? getMainNode().getGrid() : null;
        if (grid != null && grid.getCraftingService() instanceof OmniCraftingServiceBridge bridge) {
            ensureSpareAndRegister(bridge);
        }
    }

    public List<CraftingCPUCluster> allCpus() {
        var result = new ArrayList<CraftingCPUCluster>(virtualCpus.size() + 1);
        var primary = getCluster();
        if (primary != null && !primary.isDestroyed()) {
            cpuLaneIds.putIfAbsent(primary, PRIMARY_CPU_LANE_ID);
            result.add(primary);
        }
        boolean changed = false;
        Iterator<CraftingCPUCluster> iterator = virtualCpus.iterator();
        while (iterator.hasNext()) {
            var cpu = iterator.next();
            if (cpu.isDestroyed()) {
                iterator.remove();
                cpuLaneIds.remove(cpu);
                CPU_OWNERS.remove(cpu, this);
                changed = true;
                continue;
            }
            if (!cpuLaneIds.containsKey(cpu)) {
                cpuLaneIds.put(cpu, allocateVirtualCpuLaneId());
                changed = true;
            }
            result.add(cpu);
        }
        if (changed) {
            setChanged();
        }
        return result;
    }

    public int getActiveJobCount() {
        int active = 0;
        for (var cpu : allCpus()) {
            if (cpu.isBusy()) {
                active++;
            }
        }
        return active;
    }

    public int getCpuLaneCount() {
        return allCpus().size();
    }

    public synchronized DispatchAllowance claimDispatchAllowance(CraftingCPUCluster cpu, long tick) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return new DispatchAllowance(0, 0, 0, false);
        }

        if (dispatchBudgetTick != tick) {
            dispatchBudgetTick = tick;
            dispatchAdaptiveWorkUnits = ModConfig.OMNI_DISPATCH_MAX_WORK_UNITS.get();
            dispatchWorkUnitsRemaining = dispatchAdaptiveWorkUnits;
            prepareDispatchAllowances();
        }

        var reservedValue = dispatchLaneAllowances.remove(cpu);
        var unscaledValue = dispatchUnscaledLaneAllowances.remove(cpu);
        long reserved = reservedValue == null ? 0L : reservedValue;
        int unscaledReserved = unscaledValue == null ? 0 : unscaledValue;
        var compatWindow = claimSharedCompatDispatchWindow(
                serverLevel.getServer(), cpu, tick);
        dispatchReservedWorkUnits -= reserved;
        dispatchWorkUnitsRemaining -= reserved;
        long returnedWork = Math.max(0L, dispatchWorkUnitsRemaining - dispatchReservedWorkUnits);
        long allowance = reserved + Math.min(reserved, returnedWork);
        dispatchWorkUnitsRemaining -= allowance - reserved;

        dispatchUnscaledReservedAttempts -= unscaledReserved;
        dispatchUnscaledAttemptsRemaining -= unscaledReserved;
        int returnedAttempts = Math.max(
                0,
                dispatchUnscaledAttemptsRemaining
                        - dispatchUnscaledReservedAttempts);
        int unscaledAttempts = unscaledReserved + returnedAttempts;
        dispatchUnscaledAttemptsRemaining -= returnedAttempts;
        if (allowance <= 0) {
            return new DispatchAllowance(
                    0, unscaledAttempts, compatWindow.deadlineNanos(),
                    compatWindow.progressLane());
        }
        return new DispatchAllowance(
                allowance, unscaledAttempts, compatWindow.deadlineNanos(),
                compatWindow.progressLane());
    }

    private void prepareDispatchAllowances() {
        dispatchLaneAllowances.clear();
        dispatchUnscaledLaneAllowances.clear();
        dispatchUnscaledDemandLanes.clear();
        dispatchUnscaledDemandLanes.addAll(
                dispatchUnscaledDemandThisTick);
        dispatchUnscaledDemandThisTick.clear();
        dispatchUnscaledTotalAttempts =
                ModConfig.OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK.get();
        dispatchUnscaledAttemptsRemaining = dispatchUnscaledTotalAttempts;
        var activeCpus = allCpus().stream().filter(CraftingCPUCluster::isBusy).toList();
        int activeLanes = activeCpus.size();
        if (activeLanes == 0) {
            dispatchReservedWorkUnits = 0;
            dispatchUnscaledReservedAttempts = 0;
            return;
        }

        int start = Math.floorMod(dispatchLaneRotation, activeLanes);
        prepareUnscaledDispatchAllowances(activeCpus);
        if ((long) dispatchAdaptiveWorkUnits >= (long) activeLanes * 2L) {
            long base = dispatchAdaptiveWorkUnits / activeLanes;
            int extra = (int) (dispatchAdaptiveWorkUnits % activeLanes);
            for (int offset = 0; offset < activeLanes; offset++) {
                int index = (start + offset) % activeLanes;
                dispatchLaneAllowances.put(activeCpus.get(index), base + (offset < extra ? 1L : 0L));
            }
            dispatchLaneRotation = (start + Math.max(1, extra)) % activeLanes;
        } else {
            int runnableLanes = (int) (dispatchAdaptiveWorkUnits / 2L);
            for (int offset = 0; offset < runnableLanes; offset++) {
                int index = (start + offset) % activeLanes;
                dispatchLaneAllowances.put(activeCpus.get(index), 2L);
            }
            if ((dispatchAdaptiveWorkUnits & 1L) != 0L && runnableLanes > 0) {
                var first = activeCpus.get(start);
                dispatchLaneAllowances.put(first, dispatchLaneAllowances.get(first) + 1L);
            }
            dispatchLaneRotation = (start + Math.max(1, runnableLanes)) % activeLanes;
        }
        dispatchReservedWorkUnits = dispatchLaneAllowances.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private void prepareUnscaledDispatchAllowances(
            List<CraftingCPUCluster> activeCpus) {
        var demandCpus = activeCpus.stream()
                .filter(dispatchUnscaledDemandLanes::contains)
                .toList();
        var targetCpus = demandCpus.isEmpty() ? activeCpus : demandCpus;
        int targetLanes = targetCpus.size();
        int start = Math.floorMod(dispatchUnscaledLaneRotation, targetLanes);
        int base = dispatchUnscaledTotalAttempts / targetLanes;
        int extra = dispatchUnscaledTotalAttempts % targetLanes;
        for (int offset = 0; offset < targetLanes; offset++) {
            int allowance = base + (offset < extra ? 1 : 0);
            int index = (start + offset) % targetLanes;
            if (allowance > 0) {
                dispatchUnscaledLaneAllowances.put(
                        targetCpus.get(index), allowance);
            }
        }
        dispatchUnscaledLaneRotation =
                (start + Math.max(1, extra)) % targetLanes;
        dispatchUnscaledReservedAttempts =
                dispatchUnscaledLaneAllowances.values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
    }

    public synchronized void recordDispatchWork(
            CraftingCPUCluster cpu, long tick, long allowance, long used,
            int unscaledAllowance, int unscaledUsed,
            boolean unscaledNeedsMore) {
        if (tick != dispatchBudgetTick) {
            return;
        }

        long charged = Math.max(0L, Math.min(allowance, used));
        long unused = Math.max(0L, allowance - charged);
        dispatchWorkUnitsRemaining = Math.min(dispatchAdaptiveWorkUnits,
                dispatchWorkUnitsRemaining + unused);

        int unscaledCharged = Math.max(
                0, Math.min(unscaledAllowance, unscaledUsed));
        int unscaledUnused = Math.max(
                0, unscaledAllowance - unscaledCharged);
        dispatchUnscaledAttemptsRemaining = Math.min(
                dispatchUnscaledTotalAttempts,
                dispatchUnscaledAttemptsRemaining + unscaledUnused);
        if (unscaledNeedsMore && cpu != null && cpu.isBusy()) {
            dispatchUnscaledDemandThisTick.add(cpu);
        }
    }

    private static CompatDispatchWindow claimSharedCompatDispatchWindow(
            MinecraftServer server, CraftingCPUCluster cpu, long tick) {
        synchronized (SHARED_COMPAT_DISPATCH_STATES) {
            var state = SHARED_COMPAT_DISPATCH_STATES.computeIfAbsent(
                    server, ignored -> new SharedCompatDispatchState());
            if (state.tick != tick) {
                state.tick = tick;
                state.deadlineNanos = System.nanoTime()
                        + getAdaptiveCompatTimeBudgetNanos(server);
                var activeLanes = getActiveCompatDispatchLanes(server);
                if (activeLanes.isEmpty()) {
                    state.progressLane = null;
                } else {
                    int index = Math.floorMod(
                            state.progressRotation, activeLanes.size());
                    state.progressLane = activeLanes.get(index);
                    state.progressRotation =
                            (index + 1) % activeLanes.size();
                }
            }
            return new CompatDispatchWindow(
                    state.deadlineNanos, state.progressLane == cpu);
        }
    }

    private static long getAdaptiveCompatTimeBudgetNanos(
            MinecraftServer server) {
        long configuredMax = Math.multiplyExact(
                (long) ModConfig.OMNI_COMPAT_DISPATCH_MAX_TIME_US.get(),
                1_000L);
        long averageTickNanos = Math.max(
                0L, server.getAverageTickTimeNanos());
        float smoothedTickMillis = server.getCurrentSmoothedTickTime();
        if (Float.isFinite(smoothedTickMillis)
                && smoothedTickMillis > 0.0F) {
            averageTickNanos = Math.max(
                    averageTickNanos,
                    (long) (smoothedTickMillis * 1_000_000.0F));
        }
        long headroom = Math.max(
                0L, COMPAT_DISPATCH_TARGET_TICK_NANOS - averageTickNanos);
        return Math.min(
                configuredMax,
                Math.max(COMPAT_DISPATCH_MIN_GLOBAL_NANOS, headroom));
    }

    private static List<CraftingCPUCluster> getActiveCompatDispatchLanes(
            MinecraftServer server) {
        var activeLanes = new ArrayList<CraftingCPUCluster>();
        var demandLanes = new ArrayList<CraftingCPUCluster>();
        synchronized (CPU_OWNERS) {
            for (var entry : CPU_OWNERS.entrySet()) {
                var cpu = entry.getKey();
                var owner = entry.getValue();
                if (cpu != null
                        && owner != null
                        && owner.level instanceof ServerLevel ownerLevel
                        && ownerLevel.getServer() == server
                        && !cpu.isDestroyed()
                        && cpu.isActive()
                        && cpu.isBusy()) {
                    activeLanes.add(cpu);
                    if (owner.hasCompatDispatchDemand(cpu)) {
                        demandLanes.add(cpu);
                    }
                }
            }
        }
        var candidates = demandLanes.isEmpty()
                ? activeLanes
                : demandLanes;
        candidates.sort((left, right) -> Integer.compare(
                System.identityHashCode(left),
                System.identityHashCode(right)));
        return candidates;
    }

    private synchronized boolean hasCompatDispatchDemand(
            CraftingCPUCluster cpu) {
        return dispatchUnscaledDemandLanes.contains(cpu)
                || dispatchUnscaledDemandThisTick.contains(cpu);
    }

    public record DispatchAllowance(
            long workUnits, int unscaledAttempts,
            long compatDeadlineNanos, boolean compatProgressLane) {
    }

    private record CompatDispatchWindow(
            long deadlineNanos, boolean progressLane) {
    }

    private static final class SharedCompatDispatchState {
        private long tick = Long.MIN_VALUE;
        private long deadlineNanos;
        private int progressRotation;
        private CraftingCPUCluster progressLane;
    }

    public int getClientVisualActivity() {
        return clientVisualActivity;
    }

    public float sampleClientVisualAngle(float partialTick) {
        if (level == null) {
            return clientVisualAngle;
        }
        double sample = level.getGameTime() + (double) partialTick;
        if (Double.isNaN(clientVisualSample)) {
            clientVisualSample = sample;
            return clientVisualAngle;
        }
        double elapsed = sample - clientVisualSample;
        if (elapsed >= 0.0 && elapsed <= 5.0) {
            float activityScale = (float) (Math.log1p(clientVisualActivity) / Math.log(17.0));
            double advance = elapsed * (0.55F + Math.min(1.8F, activityScale) * 2.65F);
            clientVisualAngle += (float) advance;
            clientOrreryAngle += advance;
            clientCompletionPulse = Math.max(0.0F,
                    clientCompletionPulse - (float) elapsed * 0.045F);
            if (clientVisualAngle >= 3600.0F) {
                clientVisualAngle %= 360.0F;
            }
        }
        clientVisualSample = sample;
        return clientVisualAngle;
    }

    public float getClientCompletionPulse() {
        return clientCompletionPulse;
    }

    /** New orbital projections use an unwrapped phase; older renderers retain their legacy angle. */
    public double getClientOrreryAngle() {
        return clientOrreryAngle;
    }

    /** Render state comes from the server's validated layout, never a saved recovery hint. */
    public OmniComputationStructure.StructureLayout getVisualLayout() {
        return visualFormed ? visualLayout : OmniComputationStructure.StructureLayout.INCOMPLETE;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeVarInt(syncedVisualActivity);
        data.writeBoolean(visualFormed);
        data.writeVarInt(getVisualLayout().ordinal());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int nextActivity = Math.max(0, data.readVarInt());
        if (nextActivity < clientVisualActivity && clientVisualActivity > 0) {
            clientCompletionPulse = 1.0F;
        }
        changed |= clientVisualActivity != nextActivity;
        clientVisualActivity = nextActivity;
        boolean nextFormed = data.readBoolean();
        int layoutId = data.readVarInt();
        var layouts = OmniComputationStructure.StructureLayout.values();
        var nextLayout = layoutId >= 0 && layoutId < layouts.length ? layouts[layoutId]
                : OmniComputationStructure.StructureLayout.INCOMPLETE;
        nextFormed &= nextLayout != OmniComputationStructure.StructureLayout.INCOMPLETE;
        if (!nextFormed) nextLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
        changed |= structureFormed != nextFormed || visualFormed != nextFormed || visualLayout != nextLayout;
        visualFormed = nextFormed;
        visualLayout = nextLayout;
        structureFormed = nextFormed;
        return changed;
    }

    public boolean isMaterialCalculationEnabled() {
        return structureFormed && !isRemoved() && getMainNode().isActive();
    }

    public void beginMaterialCalculation() {
        activeMaterialCalculations.incrementAndGet();
    }

    public void finishMaterialCalculation(long elapsedNanos) {
        activeMaterialCalculations.updateAndGet(value -> Math.max(0, value - 1));
        completedMaterialCalculations.incrementAndGet();
        lastMaterialCalculationNanos.set(Math.max(0, elapsedNanos));
    }

    public int getActiveMaterialCalculations() {
        return activeMaterialCalculations.get();
    }

    public int getCompletedMaterialCalculations() {
        return saturatedInt(completedMaterialCalculations.get());
    }

    public int getLastMaterialCalculationMillis() {
        return saturatedInt(lastMaterialCalculationNanos.get() / 1_000_000L);
    }

    public boolean isStructureFormed() {
        return structureFormed;
    }

    public boolean isNetworkOnline() {
        return getMainNode().isActive();
    }

    public OmniComputationStructure.Inspection getInspection() {
        return inspection;
    }

    public boolean hasLegacyStructure() {
        return structureFormed
                && inspection.layout().requiresUpdate();
    }

    public boolean isLegacyStructureUpdateDismissed() {
        return legacyStructureUpdateDismissed;
    }

    public boolean isBuilding() {
        return building;
    }

    public boolean isDismantling() {
        return dismantling;
    }

    public int getDismantlableBlocks() {
        return dismantlableBlocks;
    }

    public int getBuildProgress() {
        return buildCursor;
    }

    public int getBuildTotal() {
        return buildTotal;
    }

    public void startBuild(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        if (rebuildingLegacyStructure) {
            startStructureUpdate(player);
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!OmniComputationStructure.isWithinBuildHeight(level, worldPosition)) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.invalid_height"), false);
            return;
        }
        if (!OmniComputationStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.chunks_unloaded"), false);
            return;
        }
        var currentInspection = OmniComputationStructure.inspect(level, worldPosition, facing);
        if (currentInspection.formed()) {
            player.displayClientMessage(Component.translatable(
                    currentInspection.layout().requiresUpdate()
                            ? "message.molecularmanipulator.structure_update_requires_confirmation"
                            : "message.molecularmanipulator.omni.already_formed"), false);
            return;
        }
        if (currentInspection.conflicts() > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.omni.conflicts", currentInspection.conflicts()), false);
            return;
        }
        buildQueue = new ArrayList<>(OmniComputationStructure.buildParts(currentInspection.layout()));
        dismantlePlan = null;
        knownLayout = OmniComputationStructure.StructureLayout.CURRENT;
        buildCursor = 0;
        buildTotal = buildQueue.size();
        buildOwner = player.getUUID();
        building = true;
        setChanged();
    }

    public void startStructureUpdate(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        if (rebuildingLegacyStructure && !buildQueue.isEmpty()) {
            buildOwner = player.getUUID();
            building = true;
            upgradeBlockedNotified = false;
            setChanged();
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var detected = OmniComputationStructure.inspect(level, worldPosition, facing);
        if (!detected.formed()
                || !detected.layout().requiresUpdate()) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_unavailable"), false);
            return;
        }

        relocateControllerAndStartUpdate(player, facing, detected.layout());
    }

    private void relocateControllerAndStartUpdate(ServerPlayer player,
            Direction facing, OmniComputationStructure.StructureLayout sourceLayout) {
        var currentController = new OmniComputationStructure.Part(
                OmniComputationStructure.CURRENT_CONTROLLER_X,
                OmniComputationStructure.CURRENT_CONTROLLER_Y,
                OmniComputationStructure.CURRENT_CONTROLLER_Z,
                OmniComputationStructure.PartType.CONTROLLER);
        BlockPos targetPos = OmniComputationStructure.worldPos(
                worldPosition, facing, currentController, sourceLayout);
        if (!OmniComputationStructure.isWithinBuildHeight(
                level, targetPos, OmniComputationStructure.StructureLayout.CURRENT)) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.omni.invalid_height"), false);
            return;
        }
        if (!OmniComputationStructure.areRequiredChunksLoaded(
                level, targetPos, facing,
                OmniComputationStructure.StructureLayout.CURRENT)) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.omni.chunks_unloaded"), false);
            return;
        }
        if (!player.mayUseItemAt(worldPosition, Direction.UP, ItemStack.EMPTY)
                || !player.mayUseItemAt(targetPos, Direction.UP, ItemStack.EMPTY)) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_unavailable"), false);
            return;
        }
        if (targetPos.equals(worldPosition)) {
            dismantlePlan = null;
            knownLayout = OmniComputationStructure.StructureLayout.CURRENT;
            legacyStructureUpdateDismissed = false;
            buildQueue = new ArrayList<>(
                    OmniComputationStructure.migrationParts(sourceLayout));
            buildCursor = 0;
            buildTotal = buildQueue.size();
            buildOwner = player.getUUID();
            building = true;
            configureUpgradeSource(sourceLayout);
            structureFormed = false;
            unregisterSpawnProtection();
            updateSubType(true);
            updateQuantumLink();
            setChanged();
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_started"), false);
            return;
        }

        BlockState displacedState = level.getBlockState(targetPos);
        ItemStack displaced = ItemStack.EMPTY;
        if (!displacedState.isAir() && !displacedState.canBeReplaced()) {
            if (!OmniComputationStructure.isStructurePart(displacedState)
                    || displacedState.is(ModContent.OMNI_COMPUTATION_CONTROLLER.get())) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.structure_update_unavailable"), false);
                return;
            }
            displaced = new ItemStack(displacedState.getBlock());
            if (!canStoreDismantledBlock(player, displaced)) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.omni.dismantle_storage_full"), false);
                return;
            }
        }

        var registries = level.registryAccess();
        var payload = new CompoundTag();
        saveAdditional(payload, registries);
        BlockState sourceState = getBlockState();
        BlockState centeredState = sourceState.hasProperty(BlockStateProperties.POWERED)
                ? sourceState.setValue(BlockStateProperties.POWERED, false)
                : sourceState;
        if (!level.setBlock(targetPos, centeredState, 3)
                || !(level.getBlockEntity(targetPos)
                        instanceof OmniComputationCoreBlockEntity centeredCore)) {
            level.setBlock(targetPos, displacedState, 3);
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_unavailable"), false);
            return;
        }
        centeredCore.loadTag(payload, registries);
        if (!level.removeBlock(worldPosition, false)) {
            level.setBlock(targetPos, displacedState, 3);
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.structure_update_unavailable"), false);
            return;
        }
        if (!displaced.isEmpty()) {
            centeredCore.storeDismantledBlock(player, displaced);
        }

        centeredCore.legacyStructureUpdateDismissed = false;
        centeredCore.dismantlePlan = null;
        centeredCore.knownLayout = OmniComputationStructure.StructureLayout.CURRENT;
        centeredCore.buildQueue = new ArrayList<>(
                OmniComputationStructure.migrationParts(sourceLayout));
        centeredCore.buildCursor = 0;
        centeredCore.buildTotal = centeredCore.buildQueue.size();
        centeredCore.buildOwner = player.getUUID();
        centeredCore.building = true;
        centeredCore.configureUpgradeSource(sourceLayout);
        centeredCore.structureFormed = false;
        centeredCore.unregisterSpawnProtection();
        centeredCore.updateSubType(true);
        centeredCore.updateQuantumLink();
        centeredCore.setChanged();
        player.closeContainer();
        player.displayClientMessage(Component.translatable(
                "message.molecularmanipulator.structure_update_started"), false);
    }

    public void keepLegacyStructure(ServerPlayer player) {
        if (level == null || level.isClientSide() || !player.mayBuild() || building || dismantling) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var detected = OmniComputationStructure.inspect(level, worldPosition, facing);
        if (!detected.formed()
                || !detected.layout().requiresUpdate()) {
            return;
        }
        inspection = detected;
        structureFormed = true;
        legacyStructureUpdateDismissed = true;
        setChanged();
        player.displayClientMessage(Component.translatable(
                "message.molecularmanipulator.legacy_structure_retained"), false);
    }

    public void startDismantle(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || dismantling || !player.mayBuild()) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (dismantlePlan == null || dismantlePlan.isComplete()) {
            refreshStructureNow();
            if (knownLayout == OmniComputationStructure.StructureLayout.INCOMPLETE) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.dismantle_layout_unknown"), false);
                return;
            }
            if (!OmniComputationStructure.areRequiredChunksLoaded(level, worldPosition, facing, knownLayout)) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.omni.chunks_unloaded"), false);
                return;
            }
            dismantlePlan = DismantlePlan.create(OmniComputationStructure.dismantleEntries(
                    level, worldPosition, facing, knownLayout));
        }
        if (!dismantlePlan.remainingChunksLoaded(level)) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.chunks_unloaded"), false);
            return;
        }
        if (dismantlePlan.isComplete()) {
            dismantlePlan = null;
            knownLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
            dismantlableBlocks = 0;
            setChanged();
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.nothing_to_dismantle"), false);
            return;
        }
        buildQueue = List.of();
        buildCursor = dismantlePlan.completed();
        buildTotal = dismantlePlan.total();
        buildOwner = player.getUUID();
        dismantling = true;
        dismantleBlockedNotified = false;
        structureFormed = false;
        unregisterSpawnProtection();
        updateSubType(true);
        updateQuantumLink();
        setChanged();
    }

    private void processBuild() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || buildOwner == null) {
            pauseUpgradeOrStopBuild();
            return;
        }
        var player = serverLevel.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null) {
            pauseUpgradeOrStopBuild();
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (rebuildingLegacyStructure && !OmniComputationStructure.areRequiredChunksLoaded(level, worldPosition, facing)) return;
        int placed = 0;
        while (buildCursor < buildQueue.size() && placed < BUILD_BLOCKS_PER_TICK) {
            var part = buildQueue.get(buildCursor);
            if (part.type() == OmniComputationStructure.PartType.CONTROLLER) {
                advanceBuildCursor();
                continue;
            }
            var targetPos = OmniComputationStructure.worldPos(worldPosition, facing, part);
            if (!player.mayUseItemAt(targetPos, Direction.UP, ItemStack.EMPTY)) {
                reportBuildBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.build_conflict",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                pauseUpgradeOrStopBuild();
                return;
            }
            var current = level.getBlockState(targetPos);
            if (part.type() == OmniComputationStructure.PartType.AIR) {
                if (current.isAir()) {
                    advanceBuildCursor();
                    continue;
                }
                if (!canReplaceUpgradeSource(player, part, targetPos, current)
                        || !recoverBuildReplacement(player, targetPos, current)) {
                    pauseUpgradeOrStopBuild();
                    return;
                }
                advanceBuildCursor();
                placed++;
                continue;
            }
            var requiredBlock = OmniComputationStructure.block(part.type());
            if (current.is(requiredBlock)) {
                advanceBuildCursor();
                continue;
            }
            if (OmniComputationStructure.isStructurePart(current)) {
                if (!canReplaceUpgradeSource(player, part, targetPos, current)
                        || !recoverBuildReplacement(player, targetPos, current)) {
                    pauseUpgradeOrStopBuild();
                    return;
                }
                current = level.getBlockState(targetPos);
            }
            if (!current.isAir() && !current.canBeReplaced()) {
                reportBuildBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.build_conflict",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                pauseUpgradeOrStopBuild();
                return;
            }
            if (!takeBuildItem(player, requiredBlock.asItem())) {
                reportBuildBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.missing_material",
                        requiredBlock.getName()));
                pauseUpgradeOrStopBuild();
                return;
            }
            var placedState = requiredBlock.defaultBlockState();
            if (placedState.hasProperty(HorizontalDirectionalBlock.FACING)) {
                placedState = placedState.setValue(HorizontalDirectionalBlock.FACING, facing);
            }
            boolean placementSucceeded = level.setBlock(targetPos, placedState, 3);
            if (!placementSucceeded || !level.getBlockState(targetPos).is(requiredBlock)) {
                refundBuildItem(player, requiredBlock.asItem());
                reportBuildBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.build_conflict",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                pauseUpgradeOrStopBuild();
                return;
            }
            advanceBuildCursor();
            placed++;
        }
        setChanged();
        if (buildCursor >= buildQueue.size()) {
            boolean updatedLegacyStructure = rebuildingLegacyStructure;
            stopBuild();
            refreshStructureNow();
            player.displayClientMessage(
                    Component.translatable(updatedLegacyStructure
                            ? "message.molecularmanipulator.structure_update_complete"
                            : "message.molecularmanipulator.omni.build_complete"), false);
        }
    }

    private void configureUpgradeSource(OmniComputationStructure.StructureLayout source) {
        upgradeSourceLayout = source;
        var blocks = new java.util.HashMap<BlockPos, Block>();
        for (var part : OmniComputationStructure.parts(source)) {
            if (part.type() != OmniComputationStructure.PartType.AIR
                    && part.type() != OmniComputationStructure.PartType.CONTROLLER) {
                blocks.put(new BlockPos(part.x(), part.y(), part.z()), OmniComputationStructure.block(part.type()));
            }
        }
        upgradeSourceBlocks = Map.copyOf(blocks);
        rebuildingLegacyStructure = true;
        upgradeBlockedNotified = false;
    }

    private boolean canReplaceUpgradeSource(ServerPlayer player, OmniComputationStructure.Part part,
            BlockPos target, BlockState state) {
        if (!rebuildingLegacyStructure) return true;
        var expected = upgradeSourceBlocks.get(new BlockPos(part.x(), part.y(), part.z()));
        if (expected != null && state.is(expected)) return true;
        reportBuildBlocked(player, Component.translatable("message.molecularmanipulator.omni.build_conflict",
                target.getX(), target.getY(), target.getZ()));
        return false;
    }

    private void advanceBuildCursor() {
        buildCursor++;
        upgradeBlockedNotified = false;
    }

    private void pauseUpgradeOrStopBuild() {
        if (rebuildingLegacyStructure) setChanged();
        else stopBuild();
    }

    private void reportBuildBlocked(ServerPlayer player, Component message) {
        if (!rebuildingLegacyStructure || !upgradeBlockedNotified) player.displayClientMessage(message, false);
        upgradeBlockedNotified = rebuildingLegacyStructure;
        setChanged();
    }

    private void processDismantle() {
        if (!(level instanceof ServerLevel serverLevel) || buildOwner == null || dismantlePlan == null) {
            return;
        }
        var player = serverLevel.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null || !dismantlePlan.remainingChunksLoaded(level)) {
            return;
        }
        int removed = 0;
        while (!dismantlePlan.isComplete() && removed < BUILD_BLOCKS_PER_TICK) {
            var entry = dismantlePlan.current();
            var targetPos = entry.pos();
            if (!entry.matches(level.getBlockState(targetPos))) {
                advanceDismantle();
                continue;
            }
            if (!player.mayUseItemAt(targetPos, Direction.UP, ItemStack.EMPTY)) {
                notifyDismantleBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.build_conflict",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                return;
            }
            var recovered = new ItemStack(entry.block());
            if (!canStoreDismantledBlock(player, recovered)) {
                notifyDismantleBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.dismantle_storage_full"));
                return;
            }
            if (!level.removeBlock(targetPos, false) || entry.matches(level.getBlockState(targetPos))) {
                notifyDismantleBlocked(player, Component.translatable(
                        "message.molecularmanipulator.omni.build_conflict",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                return;
            }
            storeDismantledBlock(player, recovered);
            advanceDismantle();
            removed++;
        }
        setChanged();
        if (dismantlePlan.isComplete()) {
            stopDismantle();
            refreshStructureNow();
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.dismantle_complete"), false);
        }
    }

    private void advanceDismantle() {
        dismantlePlan.advance();
        buildCursor = dismantlePlan.completed();
        dismantlableBlocks = dismantlePlan.remaining();
        dismantleBlockedNotified = false;
    }

    private void notifyDismantleBlocked(ServerPlayer player, Component message) {
        if (!dismantleBlockedNotified) {
            player.displayClientMessage(message, false);
            dismantleBlockedNotified = true;
        }
        // Keep the exact current entry, so freeing space or restoring permission
        // resumes this row instead of jumping to a lower layer or rebuilding a queue.
        setChanged();
    }

    private boolean recoverBuildReplacement(ServerPlayer player, BlockPos targetPos, BlockState currentState) {
        if (!OmniComputationStructure.isStructurePart(currentState)
                || currentState.is(ModContent.OMNI_COMPUTATION_CONTROLLER.get())) {
            reportBuildBlocked(player, Component.translatable(
                    "message.molecularmanipulator.omni.build_conflict",
                    targetPos.getX(), targetPos.getY(), targetPos.getZ()));
            return false;
        }
        var recovered = new ItemStack(currentState.getBlock());
        if (recovered.isEmpty() || !canStoreDismantledBlock(player, recovered)) {
            reportBuildBlocked(player, Component.translatable("message.molecularmanipulator.omni.dismantle_storage_full"));
            return false;
        }
        if (!level.removeBlock(targetPos, false)) {
            reportBuildBlocked(player, Component.translatable(
                    "message.molecularmanipulator.omni.build_conflict",
                    targetPos.getX(), targetPos.getY(), targetPos.getZ()));
            return false;
        }
        storeDismantledBlock(player, recovered);
        return true;
    }

    private boolean takeBuildItem(ServerPlayer player, Item required) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(required)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        var key = AEItemKey.of(new ItemStack(required));
        return grid.getStorageService().getInventory().extract(
                key, 1, Actionable.MODULATE, new PlayerSource(player)) == 1;
    }

    private void refundBuildItem(ServerPlayer player, Item required) {
        if (!player.getAbilities().instabuild) {
            storeDismantledBlock(player, new ItemStack(required));
        }
    }

    private boolean canStoreDismantledBlock(ServerPlayer player, ItemStack stack) {
        var key = AEItemKey.of(stack);
        var grid = getMainNode().isActive() ? getMainNode().getGrid() : null;
        if (key != null && grid != null && grid.getStorageService().getInventory().insert(
                key, stack.getCount(), Actionable.SIMULATE, new PlayerSource(player)) == stack.getCount()) {
            return true;
        }
        for (var inventoryStack : player.getInventory().items) {
            if (inventoryStack.isEmpty()
                    || ItemStack.isSameItemSameComponents(inventoryStack, stack)
                    && inventoryStack.getCount() < inventoryStack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void storeDismantledBlock(ServerPlayer player, ItemStack stack) {
        var key = AEItemKey.of(stack);
        var grid = getMainNode().isActive() ? getMainNode().getGrid() : null;
        if (key != null && grid != null) {
            long inserted = grid.getStorageService().getInventory().insert(
                    key, stack.getCount(), Actionable.MODULATE, new PlayerSource(player));
            stack.shrink((int) Math.min(inserted, stack.getCount()));
        }
        if (!stack.isEmpty()) {
            player.getInventory().add(stack);
        }
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private void stopBuild() {
        building = false;
        rebuildingLegacyStructure = false;
        upgradeSourceLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
        upgradeSourceBlocks = Map.of();
        upgradeBlockedNotified = false;
        buildQueue = List.of();
        buildOwner = null;
        setChanged();
    }

    private void stopDismantle() {
        dismantling = false;
        dismantlePlan = null;
        knownLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
        dismantlableBlocks = 0;
        buildQueue = List.of();
        buildOwner = null;
        setChanged();
    }

    public void openMenu(Player player) {
        MenuOpener.open(OmniComputationMenu.TYPE, player, MenuLocators.forBlockEntity(this));
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        quantumInventory.writeToNBT(tag, QUANTUM_INVENTORY_TAG, registries);
        tag.putBoolean(LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG, legacyStructureUpdateDismissed);
        tag.putBoolean(STRUCTURE_FORMED_TAG, structureFormed);
        tag.putString(KNOWN_LAYOUT_TAG, knownLayout.name());
        tag.putInt(CURRENT_BLUEPRINT_VERSION_TAG, 2);
        tag.putBoolean(UPGRADE_ACTIVE_TAG, rebuildingLegacyStructure);
        if (rebuildingLegacyStructure) {
            tag.putString(UPGRADE_SOURCE_TAG, upgradeSourceLayout.name());
            tag.putInt(UPGRADE_CURSOR_TAG, buildCursor);
            if (buildOwner != null) tag.putUUID(UPGRADE_OWNER_TAG, buildOwner);
            var queue = new ListTag();
            for (var part : buildQueue) {
                var entry = new CompoundTag();
                entry.putInt("x", part.x());
                entry.putInt("y", part.y());
                entry.putInt("z", part.z());
                entry.putString("type", part.type().name());
                queue.add(entry);
            }
            tag.put(UPGRADE_QUEUE_TAG, queue);
        }
        if (dismantlePlan != null) {
            tag.put(DISMANTLE_PLAN_TAG, dismantlePlan.save());
            tag.putBoolean(DISMANTLE_ACTIVE_TAG, dismantling);
            if (buildOwner != null) tag.putUUID(DISMANTLE_OWNER_TAG, buildOwner);
            else tag.remove(DISMANTLE_OWNER_TAG);
        } else {
            tag.remove(DISMANTLE_PLAN_TAG);
            tag.remove(DISMANTLE_ACTIVE_TAG);
            tag.remove(DISMANTLE_OWNER_TAG);
        }
        tag.putLong(NEXT_VIRTUAL_CPU_LANE_ID_TAG, nextVirtualCpuLaneId);
        boolean wroteVirtualCpu = false;
        if (!virtualCpus.isEmpty()) {
            var list = new ListTag();
            for (var cpu : virtualCpus) {
                if (cpu.isDestroyed()) {
                    continue;
                }
                var cpuTag = new CompoundTag();
                cpu.writeToNBT(cpuTag, registries);
                list.add(wrapCpuState(laneId(cpu), cpuTag));
            }
            if (!list.isEmpty()) {
                tag.put(VIRTUAL_CPUS_TAG, list);
                wroteVirtualCpu = true;
            }
        }
        if (!wroteVirtualCpu && !pendingVirtualCpuStates.isEmpty()) {
            var list = new ListTag();
            for (var state : pendingVirtualCpuStates) {
                list.add(wrapCpuState(state.laneId(), state.state()));
            }
            tag.put(VIRTUAL_CPUS_TAG, list);
        }
        if (!suspendedCpuStates.isEmpty()) {
            var list = new ListTag();
            for (var state : suspendedCpuStates) {
                list.add(wrapCpuState(state.laneId(), state.state()));
            }
            tag.put(SUSPENDED_CPUS_TAG, list);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag, registries);
        quantumInventory.readFromNBT(tag, QUANTUM_INVENTORY_TAG, registries);
        legacyStructureUpdateDismissed = tag.getBoolean(LEGACY_STRUCTURE_UPDATE_DISMISSED_TAG);
        try {
            knownLayout = OmniComputationStructure.StructureLayout.fromSavedName(tag.getString(KNOWN_LAYOUT_TAG));
        } catch (IllegalArgumentException ignored) {
            // Old saves never stored a dismantle queue or a reliable partial-layout
            // anchor. Inspect a complete structure again; never reuse an old index.
            knownLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
        }
        if (tag.getInt(CURRENT_BLUEPRINT_VERSION_TAG) < 2
                && knownLayout == OmniComputationStructure.StructureLayout.CURRENT) {
            knownLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
        }
        dismantlePlan = tag.contains(DISMANTLE_PLAN_TAG, CompoundTag.TAG_COMPOUND)
                ? DismantlePlan.load(tag.getCompound(DISMANTLE_PLAN_TAG)) : null;
        dismantling = dismantlePlan != null && !dismantlePlan.isComplete()
                && tag.getBoolean(DISMANTLE_ACTIVE_TAG) && tag.hasUUID(DISMANTLE_OWNER_TAG);
        dismantleBlockedNotified = false;
        if (dismantlePlan != null) {
            building = false;
            rebuildingLegacyStructure = false;
            buildQueue = List.of();
            buildCursor = dismantlePlan.completed();
            buildTotal = dismantlePlan.total();
            dismantlableBlocks = dismantlePlan.remaining();
            buildOwner = tag.hasUUID(DISMANTLE_OWNER_TAG) ? tag.getUUID(DISMANTLE_OWNER_TAG) : null;
        }
        if (!dismantling) restoreUpgradeQueue(tag);
        pendingVirtualCpuStates.clear();
        suspendedCpuStates.clear();
        cpuLaneIds.clear();
        long persistedNextLaneId = tag.contains(NEXT_VIRTUAL_CPU_LANE_ID_TAG, CompoundTag.TAG_LONG)
                ? tag.getLong(NEXT_VIRTUAL_CPU_LANE_ID_TAG) : 1L;
        nextVirtualCpuLaneId = Math.max(1L, persistedNextLaneId);
        var usedLaneIds = new HashSet<Long>();
        long nextFallbackLaneId = 1L;
        nextFallbackLaneId = readCpuTags(
                tag.getList(VIRTUAL_CPUS_TAG, CompoundTag.TAG_COMPOUND),
                pendingVirtualCpuStates, usedLaneIds, nextFallbackLaneId, false);
        nextFallbackLaneId = readCpuTags(tag.getList(SUSPENDED_CPUS_TAG, CompoundTag.TAG_COMPOUND),
                suspendedCpuStates, usedLaneIds, nextFallbackLaneId, true);
        for (long candidate = nextVirtualCpuLaneId;
                usedLaneIds.contains(candidate) && candidate < Long.MAX_VALUE; candidate++) {
            nextVirtualCpuLaneId = candidate + 1L;
        }
        nextVirtualCpuLaneId = Math.max(nextVirtualCpuLaneId, nextFallbackLaneId);
        restoredCpuState = false;
        // Restore the last validated structure state optimistically so AE2's early
        // subtype callbacks do not turn off a persisted active model before the first
        // scheduled inspection. The inspection still runs immediately and corrects
        // stale state if the structure changed while this chunk was unloaded.
        structureFormed = tag.contains(STRUCTURE_FORMED_TAG)
                ? tag.getBoolean(STRUCTURE_FORMED_TAG)
                : getBlockState().getValue(BlockStateProperties.POWERED);
        if (dismantling || rebuildingLegacyStructure) structureFormed = false;
        visualLayout = OmniComputationStructure.StructureLayout.INCOMPLETE;
        visualFormed = false;
        nextStructureCheck = 0;
        claimedQuantumFrequency = 0;
        quantumLinkState = MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    }

    private void restoreUpgradeQueue(CompoundTag tag) {
        if (!tag.getBoolean(UPGRADE_ACTIVE_TAG)) return;
        // Removed experimental layouts must never resume against the current blueprint.
        building = false;
        rebuildingLegacyStructure = false;
        buildQueue = List.of();
        final OmniComputationStructure.StructureLayout source;
        var queue = new ArrayList<OmniComputationStructure.Part>();
        try {
            source = OmniComputationStructure.StructureLayout.fromSavedName(tag.getString(UPGRADE_SOURCE_TAG));
            if (!source.requiresUpdate()) return;
            var saved = tag.getList(UPGRADE_QUEUE_TAG, CompoundTag.TAG_COMPOUND);
            for (int index = 0; index < saved.size(); index++) {
                var entry = saved.getCompound(index);
                var type = OmniComputationStructure.PartType.valueOf(entry.getString("type"));
                queue.add(new OmniComputationStructure.Part(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"), type));
            }
        } catch (IllegalArgumentException invalidSavedQueue) {
            return;
        }
        if (queue.isEmpty()) return;
        configureUpgradeSource(source);
        dismantlePlan = null;
        knownLayout = OmniComputationStructure.StructureLayout.CURRENT;
        buildQueue = List.copyOf(queue);
        buildCursor = Math.max(0, Math.min(tag.getInt(UPGRADE_CURSOR_TAG), queue.size()));
        buildTotal = queue.size();
        buildOwner = tag.hasUUID(UPGRADE_OWNER_TAG) ? tag.getUUID(UPGRADE_OWNER_TAG) : null;
        building = buildOwner != null;
    }

    private static CompoundTag wrapCpuState(long laneId, CompoundTag state) {
        var entry = new CompoundTag();
        entry.putLong(VIRTUAL_CPU_LANE_ID_TAG, laneId);
        entry.put(VIRTUAL_CPU_STATE_TAG, state.copy());
        return entry;
    }

    private static long readCpuTags(ListTag list, List<PersistedCpuState> output,
            Set<Long> usedLaneIds, long nextFallbackLaneId, boolean firstEntryIsPrimary) {
        for (int index = 0; index < list.size(); index++) {
            var entry = list.getCompound(index);
            boolean wrapped = entry.contains(VIRTUAL_CPU_STATE_TAG, CompoundTag.TAG_COMPOUND);
            var state = wrapped ? entry.getCompound(VIRTUAL_CPU_STATE_TAG).copy() : entry.copy();
            long laneId;
            if (firstEntryIsPrimary && index == 0) {
                // The suspended list's first entry is the physical controller CPU.
                // Keep the primary lane invariant even if an older/corrupt tag
                // contains a non-zero lane id for that entry.
                laneId = PRIMARY_CPU_LANE_ID;
            } else if (wrapped && entry.contains(VIRTUAL_CPU_LANE_ID_TAG, CompoundTag.TAG_LONG)) {
                laneId = entry.getLong(VIRTUAL_CPU_LANE_ID_TAG);
            } else {
                laneId = nextUnusedLaneId(usedLaneIds, nextFallbackLaneId);
            }

            boolean primarySlot = firstEntryIsPrimary && index == 0;
            if (laneId < PRIMARY_CPU_LANE_ID || (!primarySlot && laneId == PRIMARY_CPU_LANE_ID)
                    || usedLaneIds.contains(laneId)) {
                laneId = nextUnusedLaneId(usedLaneIds, nextFallbackLaneId);
            }
            usedLaneIds.add(laneId);
            output.add(new PersistedCpuState(laneId, state));
            if (laneId >= nextFallbackLaneId && laneId < Long.MAX_VALUE) {
                nextFallbackLaneId = laneId + 1L;
            }
        }
        return nextFallbackLaneId;
    }

    private static long nextUnusedLaneId(Set<Long> usedLaneIds, long candidate) {
        long next = Math.max(1L, candidate);
        while (usedLaneIds.contains(next) && next < Long.MAX_VALUE) {
            next++;
        }
        return next;
    }

    public static OmniComputationCoreBlockEntity ownerOf(CraftingCPUCluster cpu) {
        return CPU_OWNERS.get(cpu);
    }

    /**
     * Returns the controller-local persistent lane number. The physical CPU is
     * always lane 0; virtual lanes use monotonically allocated positive IDs.
     */
    public long laneId(CraftingCPUCluster cpu) {
        if (cpu == null) {
            return -1L;
        }
        var known = cpuLaneIds.get(cpu);
        if (known != null) {
            return known;
        }
        if (cpu == getCluster()) {
            cpuLaneIds.put(cpu, PRIMARY_CPU_LANE_ID);
            return PRIMARY_CPU_LANE_ID;
        }
        int index = virtualCpus.indexOf(cpu);
        if (index < 0) {
            return -1L;
        }
        long assigned = allocateVirtualCpuLaneId();
        cpuLaneIds.put(cpu, assigned);
        setChanged();
        return assigned;
    }

    /**
     * Stable, local-only identity for diagnostics and future integrations.
     * This mod does not register a Data Energistics integration.
     */
    public String laneStableId(CraftingCPUCluster cpu) {
        long id = laneId(cpu);
        if (id < 0L) {
            return "omnisequence:unknown";
        }
        String dimension = level == null ? "unknown" : level.dimension().location().toString();
        return "omnisequence:" + dimension + ":" + worldPosition.asLong() + ":" + id;
    }

    public String laneName(CraftingCPUCluster cpu) {
        long id = laneId(cpu);
        if (id < 0L) {
            return "?";
        }
        return Long.toString(id == Long.MAX_VALUE ? Long.MAX_VALUE : id + 1L);
    }

    private record PersistedCpuState(long laneId, CompoundTag state) {
    }

    private static int saturatedInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, value);
    }
}
