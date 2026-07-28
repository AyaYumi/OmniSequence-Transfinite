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
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
    private static final String QUANTUM_INVENTORY_TAG = "omni_quantum_inventory";
    private static final Map<CraftingCPUCluster, OmniComputationCoreBlockEntity> CPU_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, SharedCompatDispatchState>
            SHARED_COMPAT_DISPATCH_STATES =
                    Collections.synchronizedMap(new WeakHashMap<>());

    private final List<CraftingCPUCluster> virtualCpus = new ArrayList<>();
    private final List<CompoundTag> pendingVirtualCpuStates = new ArrayList<>();
    private final List<CompoundTag> suspendedCpuStates = new ArrayList<>();
    private final AtomicInteger activeMaterialCalculations = new AtomicInteger();
    private final AtomicLong completedMaterialCalculations = new AtomicLong();
    private final AtomicLong lastMaterialCalculationNanos = new AtomicLong();
    private final AppEngInternalInventory quantumInventory = new AppEngInternalInventory(this, 1);
    private OmniComputationStructure.Inspection inspection =
            new OmniComputationStructure.Inspection(OmniComputationStructure.parts().size(), 0,
                    OmniComputationStructure.parts().size(), 0, false);
    private boolean structureFormed;
    private boolean restoredCpuState;
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
    private float clientVisualAngle;
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
    }

    @Override
    public void onChunkUnloaded() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterSpawnProtection();
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval(MolecularCenterBlockEntity.QuantumLinkState.SEARCHING);
        super.setRemoved();
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

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
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
            syncVisualActivity(0);
            return;
        }

        if (getCluster() == null) {
            updateMultiBlock(worldPosition);
        }
        var primary = getCluster();
        if (primary == null || primary.isDestroyed()) {
            syncVisualActivity(activeMaterialCalculations.get());
            return;
        }
        CPU_OWNERS.put(primary, this);
        restoreAdditionalCpus(primary);
        ensureOneIdleCpu();
        registerCpusWithGrid();
        updateSubType(false);
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
    }

    private void updateSpawnProtection() {
        if (level instanceof ServerLevel serverLevel) {
            MolecularCenterSpawnProtection.updateOmni(serverLevel, worldPosition, structureFormed);
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
            primary.readFromNBT(suspendedCpuStates.getFirst(), level.registryAccess());
            for (int index = 1; index < suspendedCpuStates.size(); index++) {
                var cpu = createVirtualCpu();
                cpu.readFromNBT(suspendedCpuStates.get(index), level.registryAccess());
            }
            suspendedCpuStates.clear();
            pendingVirtualCpuStates.clear();
            return;
        }

        for (var state : pendingVirtualCpuStates) {
            var cpu = createVirtualCpu();
            cpu.readFromNBT(state, level.registryAccess());
        }
        pendingVirtualCpuStates.clear();
    }

    private CraftingCPUCluster createVirtualCpu() {
        var cpu = new CraftingCPUCluster(worldPosition, worldPosition);
        var accessor = (CraftingCPUClusterAccessor) (Object) cpu;
        accessor.molecularmanipulator$addBlockEntity(this);
        accessor.molecularmanipulator$finishCluster();
        virtualCpus.add(cpu);
        CPU_OWNERS.put(cpu, this);
        return cpu;
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
                    if (bridge != null) {
                        bridge.molecularmanipulator$unregisterOmniCpu(cpu);
                    }
                    idleCount--;
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
            result.add(primary);
        }
        for (var cpu : virtualCpus) {
            if (!cpu.isDestroyed()) {
                result.add(cpu);
            }
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
        double sample = level.getGameTime() + partialTick;
        if (Double.isNaN(clientVisualSample)) {
            clientVisualSample = sample;
            return clientVisualAngle;
        }
        double elapsed = sample - clientVisualSample;
        if (elapsed >= 0.0 && elapsed <= 5.0) {
            float activityScale = (float) (Math.log1p(clientVisualActivity) / Math.log(17.0));
            clientVisualAngle += (float) elapsed * (0.55F + Math.min(1.8F, activityScale) * 2.65F);
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

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeVarInt(syncedVisualActivity);
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

    public boolean isBuilding() {
        return building;
    }

    public boolean isDismantling() {
        return dismantling;
    }

    public int getBuildProgress() {
        return buildCursor;
    }

    public int getBuildTotal() {
        return buildTotal;
    }

    public void startBuild(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || dismantling) {
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
        if (currentInspection.conflicts() > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.molecularmanipulator.omni.conflicts", currentInspection.conflicts()), false);
            return;
        }
        if (currentInspection.formed()) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.already_formed"), false);
            return;
        }
        buildQueue = new ArrayList<>(OmniComputationStructure.parts());
        buildCursor = 0;
        buildTotal = buildQueue.size();
        buildOwner = player.getUUID();
        building = true;
        setChanged();
    }

    public void startDismantle(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || dismantling || !player.mayBuild()) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!OmniComputationStructure.areRequiredChunksLoaded(level, worldPosition, facing)) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.chunks_unloaded"), false);
            return;
        }
        refreshStructureNow();
        if (inspection.correct() <= 1) {
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.nothing_to_dismantle"), false);
            return;
        }
        buildQueue = new ArrayList<>(OmniComputationStructure.parts());
        Collections.reverse(buildQueue);
        buildCursor = 0;
        buildTotal = buildQueue.size();
        buildOwner = player.getUUID();
        dismantling = true;
        structureFormed = false;
        unregisterSpawnProtection();
        updateSubType(true);
        updateQuantumLink();
        setChanged();
    }

    private void processBuild() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || buildOwner == null) {
            stopBuild();
            return;
        }
        var player = serverLevel.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null) {
            stopBuild();
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        int placed = 0;
        while (buildCursor < buildQueue.size() && placed < BUILD_BLOCKS_PER_TICK) {
            var part = buildQueue.get(buildCursor);
            buildCursor++;
            if (part.type() == OmniComputationStructure.PartType.CONTROLLER) {
                continue;
            }
            var targetPos = OmniComputationStructure.worldPos(worldPosition, facing, part);
            var requiredBlock = OmniComputationStructure.block(part.type());
            var current = level.getBlockState(targetPos);
            if (current.is(requiredBlock)) {
                continue;
            }
            if (!current.isAir() && !current.canBeReplaced()) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.omni.build_conflict",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ()), false);
                stopBuild();
                return;
            }
            if (!takeBuildItem(player, requiredBlock.asItem())) {
                player.displayClientMessage(Component.translatable(
                        "message.molecularmanipulator.omni.missing_material",
                        requiredBlock.getName()), false);
                stopBuild();
                return;
            }
            var placedState = requiredBlock.defaultBlockState();
            if (placedState.hasProperty(HorizontalDirectionalBlock.FACING)) {
                placedState = placedState.setValue(HorizontalDirectionalBlock.FACING, facing);
            }
            level.setBlock(targetPos, placedState, 3);
            placed++;
        }
        setChanged();
        if (buildCursor >= buildQueue.size()) {
            stopBuild();
            refreshStructureNow();
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.build_complete"), false);
        }
    }

    private void processDismantle() {
        if (!(level instanceof ServerLevel serverLevel) || buildOwner == null) {
            stopDismantle();
            return;
        }
        var player = serverLevel.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null) {
            stopDismantle();
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        int removed = 0;
        while (buildCursor < buildQueue.size() && removed < BUILD_BLOCKS_PER_TICK) {
            var part = buildQueue.get(buildCursor);
            if (part.type() == OmniComputationStructure.PartType.CONTROLLER) {
                buildCursor++;
                continue;
            }
            var targetPos = OmniComputationStructure.worldPos(worldPosition, facing, part);
            var requiredBlock = OmniComputationStructure.block(part.type());
            var currentState = level.getBlockState(targetPos);
            if (!currentState.is(requiredBlock)) {
                buildCursor++;
                continue;
            }
            var recovered = new ItemStack(requiredBlock);
            if (!canStoreDismantledBlock(player, recovered)) {
                player.displayClientMessage(
                        Component.translatable("message.molecularmanipulator.omni.dismantle_storage_full"), false);
                stopDismantle();
                return;
            }
            level.removeBlock(targetPos, false);
            storeDismantledBlock(player, recovered);
            buildCursor++;
            removed++;
        }
        setChanged();
        if (buildCursor >= buildQueue.size()) {
            stopDismantle();
            refreshStructureNow();
            player.displayClientMessage(
                    Component.translatable("message.molecularmanipulator.omni.dismantle_complete"), false);
        }
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
        buildQueue = List.of();
        buildOwner = null;
        setChanged();
    }

    private void stopDismantle() {
        dismantling = false;
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
        if (!virtualCpus.isEmpty()) {
            var list = new ListTag();
            for (var cpu : virtualCpus) {
                var cpuTag = new CompoundTag();
                cpu.writeToNBT(cpuTag, registries);
                list.add(cpuTag);
            }
            tag.put(VIRTUAL_CPUS_TAG, list);
        }
        if (!suspendedCpuStates.isEmpty()) {
            var list = new ListTag();
            for (var cpuTag : suspendedCpuStates) {
                list.add(cpuTag.copy());
            }
            tag.put(SUSPENDED_CPUS_TAG, list);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        quantumInventory.readFromNBT(tag, QUANTUM_INVENTORY_TAG, registries);
        pendingVirtualCpuStates.clear();
        suspendedCpuStates.clear();
        readCpuTags(tag.getList(VIRTUAL_CPUS_TAG, CompoundTag.TAG_COMPOUND), pendingVirtualCpuStates);
        readCpuTags(tag.getList(SUSPENDED_CPUS_TAG, CompoundTag.TAG_COMPOUND), suspendedCpuStates);
        restoredCpuState = false;
        structureFormed = false;
        nextStructureCheck = 0;
        claimedQuantumFrequency = 0;
        quantumLinkState = MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    }

    private static void readCpuTags(ListTag list, List<CompoundTag> output) {
        for (int index = 0; index < list.size(); index++) {
            output.add(list.getCompound(index).copy());
        }
    }

    public static OmniComputationCoreBlockEntity ownerOf(CraftingCPUCluster cpu) {
        return CPU_OWNERS.get(cpu);
    }

    public String laneName(CraftingCPUCluster cpu) {
        var all = allCpus();
        int index = all.indexOf(cpu);
        return index < 0 ? "?" : Integer.toString(index + 1);
    }

    private static int saturatedInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, value);
    }
}
