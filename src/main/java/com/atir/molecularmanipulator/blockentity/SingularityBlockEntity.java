package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.Locatables;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.ModFluids;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;

import java.util.*;

/** Construction terminal and tag-driven resource collection controller. */
public final class SingularityBlockEntity extends AENetworkInvBlockEntity {
    public enum Operation { IDLE, BUILD, DISMANTLE }
    public enum Status { IDLE, STARTING, COLLECTING, BUILDING, DISMANTLING, PAUSED, MATERIAL, CONFLICT, UNLOADED,
        OWNER_OFFLINE, PROTECTED, STORAGE_FULL, OUT_OF_BOUNDS, COMPLETE, VERSION_CHANGED }
    private final AppEngInternalInventory recovery = new AppEngInternalInventory(this, 1);
    private static final String QUANTUM_INVENTORY_TAG = "singularity_quantum_inventory";
    private static final String DUPLICATION_INVENTORY_TAG = "singularity_duplication_inventory";
    private static final double QUANTUM_LINK_POWER = 512.0;
    private final AppEngInternalInventory quantumInventory = new AppEngInternalInventory(this, 1);
    private final AppEngInternalInventory duplicationInventory = new AppEngInternalInventory(this, 2);
    private final SingularityDuplicationProcessor duplication = new SingularityDuplicationProcessor(this::saveChanges);
    private final SingularityMotionState motion = new SingularityMotionState(this);
    private Operation operation = Operation.IDLE;
    private Status status = Status.IDLE;
    private UUID owner;
    private Direction operationFacing = Direction.NORTH;
    private boolean paused, formed, scanRequested, collectionActive;
    private long collectionStartTick = Long.MIN_VALUE;
    private long nextCollectionTick = Long.MIN_VALUE;
    private long nextDuplicationGenerationTick = Long.MIN_VALUE;
    private int duplicationGenerationInterval;
    private long lastDuplicationFeConsumed;
    private double lastDuplicationAeConsumed;
    private int cursor;
    private int structureVersion = SingularityStructure.VERSION;
    private boolean embedRequested;
    private long nextInspection;
    private DismantlePlan dismantle;
    private BlockPos problem;
    private SingularityStructure.Type neededMaterial;
    private SingularityStructure.Inspection inspection = new SingularityStructure.Inspection(
            0, 0, 0, SingularityStructure.parts().size(), Map.of(), null);
    private IGridConnection quantumConnection;
    private IGridNode quantumRemoteNode;
    private long quantumConnectionFrequency;
    private long claimedQuantumFrequency;
    private QuantumLinkState quantumLinkState = QuantumLinkState.EMPTY;

    public SingularityBlockEntity(BlockPos pos, BlockState state) {
        super(SingularityContent.CONTROLLER_BE.get(), pos, state);
        quantumInventory.setFilter(new IAEItemFilter() {
            @Override public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                return isQuantumEntangledSingularity(stack);
            }
        });
        duplicationInventory.setFilter(new IAEItemFilter() {
            @Override public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                return !stack.isEmpty() && (slot == 0 || slot == 1 && stack.is(ModContent.BLACK_HOLE.get()));
            }
        });
        quantumInventory.setMaxStackSize(0, 1);
        duplicationInventory.setMaxStackSize(0, 1);
        duplicationInventory.setMaxStackSize(1, 64);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(4);
        if (isSingleBlock()) {
            formed = true;
            inspection = new SingularityStructure.Inspection(1, 0, 0, 0, Map.of(), null);
        }
    }
    public boolean isSingleBlock() { return getBlockState().is(SingularityContent.COMPACT.get()); }
    @Override protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }
    @Override public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) { return EnumSet.allOf(Direction.class); }
    @Override public InternalInventory getInternalInventory() { return recovery; }
    @Override protected InternalInventory getExposedInventoryForSide(Direction side) { return InternalInventory.empty(); }
    @Override public void onChangeInventory(InternalInventory inv, int slot) {
        if (inv == quantumInventory && level != null && !level.isClientSide()) {
            disconnectQuantumLink(QuantumLinkState.SEARCHING);
            updateQuantumLink();
        }
        saveChanges();
    }
    @Override public void onReady() {
        super.onReady();
        scanRequested |= formed;
        nextInspection = 0;
        if (collectionActive && nextCollectionTick == Long.MIN_VALUE) {
            nextCollectionTick = level == null ? Long.MIN_VALUE : level.getGameTime() + collectionIntervalTicks();
        }
        updateQuantumLink();
    }
    @Override public void onChunkUnloaded() {
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval();
        super.onChunkUnloaded();
    }
    @Override public void setRemoved() {
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval();
        super.setRemoved();
    }
    public void openMenu(Player player) {
        if (player instanceof ServerPlayer server && operation == Operation.IDLE && motion.hasPortable()) setRecoveryOwner(server);
        MenuOpener.open(SingularityContent.MENU.get(), player, MenuLocators.forBlockEntity(this));
    }
    public SingularityMotionState motion() { return motion; }
    public int structureVersion() { return structureVersion; }
    public boolean embedRequested() { return embedRequested; }
    public List<SingularityStructure.Part> structureParts() { return isSingleBlock() ? List.of() : SingularityStructure.parts(structureVersion); }
    public BlockPos worldPos(BlockPos local) { return SingularityStructure.worldPos(worldPosition, facing(), local, structureVersion); }
    public BlockPos worldPos(SingularityStructure.Part part) { return worldPos(part.pos()); }
    public void setRecoveryOwner(ServerPlayer player) { owner = player.getUUID(); saveChanges(); }
    public void scheduleInspection() { nextInspection = 0; }
    public Direction facing() { return getBlockState().getValue(HorizontalDirectionalBlock.FACING); }
    public boolean formed() { return formed; }
    public boolean networkOnline() { return getMainNode().isActive(); }
    public AppEngInternalInventory getQuantumInventory() { return quantumInventory; }
    public AppEngInternalInventory getDuplicationInventory() { return duplicationInventory; }
    public int blackHoleCount() { return duplicationInventory.getStackInSlot(1).getCount(); }
    /** Each black hole contributes one base fluid-production rate. */
    public int duplicationSpeedMultiplier() { return blackHoleCount(); }
    public int duplicationMaterialProductionRate(int baseRate) {
        return Math.max(0, baseRate) * duplicationSpeedMultiplier();
    }
    public int duplicationIntervalTicksForMenu() {
        return Math.max(1, ModConfig.SINGULARITY_DUPLICATION_INTERVAL_TICKS.get());
    }
    public int duplicationBatchSizeForMenu() {
        return blackHoleCount() * Math.max(1, ModConfig.SINGULARITY_DUPLICATION_MATTER_PER_BLACK_HOLE.get());
    }
    public int duplicationTargetPerSecond() {
        long value = (long) duplicationBatchSizeForMenu() * 20L / duplicationIntervalTicksForMenu();
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
    public long duplicationFePerCycleForMenu() {
        return (long) duplicationBatchSizeForMenu() * Math.max(1, ModConfig.SINGULARITY_DUPLICATION_FE_PER_UNIT.get());
    }
    public long duplicationAePerCycleForMenu() {
        return (long) duplicationBatchSizeForMenu() * Math.max(1, ModConfig.SINGULARITY_DUPLICATION_AE_PER_UNIT.get());
    }
    public long lastDuplicationFeConsumed() { return lastDuplicationFeConsumed; }
    public double lastDuplicationAeConsumed() { return lastDuplicationAeConsumed; }
    private void clearDuplicationEnergyUsage() {
        lastDuplicationFeConsumed = 0;
        lastDuplicationAeConsumed = 0;
    }
    public String duplicationEnergyPriorityForMenu() {
        return ModConfig.SINGULARITY_DUPLICATION_ENERGY_PRIORITY.get().stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" → "));
    }
    public static boolean isQuantumEntangledSingularity(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem());
    }
    public long getQuantumFrequency() {
        var stack = quantumInventory.getStackInSlot(0);
        return isQuantumEntangledSingularity(stack)
                ? Math.max(0L, (stack.hasTag() ? stack.getTag().getLong(appeng.blockentity.qnb.QuantumBridgeBlockEntity.TAG_FREQUENCY) : 0L)) : 0;
    }
    public QuantumLinkState getQuantumLinkState() { return quantumLinkState; }
    public boolean collectionActive() { return collectionActive; }
    /** Comma-separated registry ids used by the collection page and client UI. */
    public String collectionItemIds() {
        return configuredCollectionItems().stream()
                .map(BuiltInRegistries.ITEM::getKey)
                .map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }
    public int collectionBatchSizeForMenu() { return collectionBatchSize(); }
    public boolean collectionStarting() { return collectionActive && collectionStartTick != Long.MIN_VALUE
            && level != null && level.getGameTime() - collectionStartTick < 40; }
    public Operation operation() { return operation; }
    public Status status() { return status; }
    public boolean paused() { return paused; }
    public BlockPos problem() { return problem; }
    public SingularityStructure.Type neededMaterial() { return neededMaterial; }
    public SingularityStructure.Inspection inspection() { return inspection; }
    public int progress() { return operation == Operation.DISMANTLE && dismantle != null ? dismantle.completed() : cursor; }
    public int operationTotal() { return isSingleBlock() ? 1 : operation == Operation.DISMANTLE && dismantle != null ? dismantle.total() : structureParts().size(); }
    public Set<ChunkPos> getChunkLoadingChunks() {
        if (isSingleBlock()) return Set.of();
        if (motion.hasBodies()) return MultiblockChunkLoading.rectangle(
                worldPos(new BlockPos(-50, 0, -50)), worldPos(new BlockPos(50, 128, 50)));
        if (operation == Operation.DISMANTLE && dismantle != null) return dismantle.remainingChunks();
        return operation != Operation.IDLE || formed || scanRequested ? SingularityStructure.chunks(worldPosition, facing(), structureVersion) : Set.of();
    }

    private void updateQuantumLink() {
        if (!(level instanceof ServerLevel serverLevel)) return;
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
            quantumLinkState = localNode.isOnline() ? connectedQuantumLinkState() : QuantumLinkState.REMOTE_OFFLINE;
            return;
        }
        disconnectQuantumLink(QuantumLinkState.SEARCHING);
        try {
            quantumConnection = GridHelper.createConnection(localNode, remoteNode);
            quantumRemoteNode = remoteNode;
            quantumConnectionFrequency = frequency;
            getMainNode().setIdlePowerUsage(4 + QUANTUM_LINK_POWER);
            quantumLinkState = connectedQuantumLinkState();
            saveChanges();
        } catch (IllegalStateException exception) {
            disconnectQuantumLink(QuantumLinkState.CONNECTION_ERROR);
        }
    }

    private boolean isQuantumConnectionCurrent(IGridNode localNode, IGridNode remoteNode, long frequency) {
        return quantumConnection != null && quantumRemoteNode == remoteNode
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
        getMainNode().setIdlePowerUsage(4);
        if (connection != null) {
            try { connection.destroy(); } catch (RuntimeException ignored) { }
        }
    }

    private void clearQuantumLinkForRemoval() {
        quantumConnection = null;
        quantumRemoteNode = null;
        quantumConnectionFrequency = 0;
        quantumLinkState = QuantumLinkState.SEARCHING;
    }

    private boolean claimQuantumFrequency(ServerLevel serverLevel, long frequency) {
        if (claimedQuantumFrequency != 0 && claimedQuantumFrequency != frequency) releaseQuantumFrequency();
        if (!EntangledQuantumFrequencyRegistry.claim(serverLevel, worldPosition, frequency)) return false;
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

    public void toggleCollection(ServerPlayer player) {
        if (isSingleBlock()) {
            if (!canManage(player) || !collectionActive && !networkOnline()) return;
            collectionActive = !collectionActive;
            nextCollectionTick = collectionActive ? level.getGameTime() + collectionIntervalTicks() : Long.MIN_VALUE;
            status = collectionActive ? Status.COLLECTING : Status.IDLE;
            saveChanges(); markForUpdate();
            return;
        }
        if (!canManage(player) || operation != Operation.IDLE || embedRequested
                || motion.hasPortable() || !formed || !networkOnline() || motion.mode() == SingularityMotionState.DOCKING) return;
        if (collectionActive) {
            collectionActive = false;
            collectionStartTick = Long.MIN_VALUE;
            nextCollectionTick = Long.MIN_VALUE;
            status = Status.IDLE;
            motion.stopForCollection(player);
        } else {
            if (!motion.startForCollection(player)) return;
            collectionActive = true;
            collectionStartTick = level.getGameTime();
            nextCollectionTick = Long.MIN_VALUE;
            status = Status.STARTING;
        }
        saveChanges();
        markForUpdate();
    }
    public boolean canManage(ServerPlayer player) {
        return player != null && level != null && !isRemoved() && player.level() == level && player.mayBuild()
                && player.distanceToSqr(worldPosition.getCenter()) <= 64
                && level.mayInteract(player, worldPosition);
    }
    public void requestInspection(ServerPlayer player) {
        if (isSingleBlock() || !canManage(player)) return;
        scanRequested = true; nextInspection = 0;
    }
    public void startBuild(ServerPlayer player) {
        if (isSingleBlock() || !canManage(player) || operation != Operation.IDLE || collectionActive || motion.hasBodies()
                || motion.hasPortable() || embedRequested) return;
        if (!SingularityStructure.fits(level, worldPosition, facing(), structureVersion)) { status = Status.OUT_OF_BOUNDS; return; }
        operation = Operation.BUILD; owner = player.getUUID(); operationFacing = facing();
        cursor = 0; paused = false; problem = null; status = Status.BUILDING; saveChanges();
    }
    public void togglePause(ServerPlayer player) {
        if (isSingleBlock() || !canManage(player) || operation == Operation.IDLE
                || !player.getUUID().equals(owner) && !player.hasPermissions(2)) return;
        paused = !paused;
        if (!paused) owner = player.getUUID();
        status = paused ? Status.PAUSED : operation == Operation.BUILD ? Status.BUILDING : Status.DISMANTLING;
        saveChanges();
    }
    public void cancel(ServerPlayer player) {
        if (isSingleBlock()) return;
        if (!canManage(player) || !player.getUUID().equals(owner) && !player.hasPermissions(2)) return;
        operation = Operation.IDLE; paused = false; dismantle = null; embedRequested = false;
        if (collectionActive && motion.hasBodies()) motion.stopForCollection(player);
        collectionActive = false; collectionStartTick = Long.MIN_VALUE;
        nextCollectionTick = Long.MIN_VALUE; status = Status.IDLE;
        nextInspection = 0; saveChanges();
    }
    public void startDismantle(ServerPlayer player) {
        if (isSingleBlock() || !canManage(player) || operation != Operation.IDLE || collectionActive || embedRequested) return;
        if (motion.hasBodies()) { motion.dock(player, true); return; }
        beginDismantle(player);
    }
    void beginDismantle(ServerPlayer player) {
        if (player.level() != level || !player.mayBuild()) return;
        refreshInspection();
        if (inspection.unloaded() > 0) { scanRequested = true; status = Status.UNLOADED; return; }
        var entries = new ArrayList<DismantlePlan.Entry>();
        for (var part : structureParts()) {
            if (SingularityStructure.isController(part)) continue;
            var pos = worldPos(part);
            if (SingularityStructure.matches(level.getBlockState(pos), part, facing()))
                entries.add(new DismantlePlan.Entry(pos, SingularityStructure.block(part.type())));
        }
        // Nodes from the previous running layout are fixed compatibility joints.
        // Include them in reclaim so a layout update cannot strand old capture nodes.
        for (var local : SingularityStructure.compatibilityCollectionNodePositions(structureVersion)) {
            var pos = worldPos(local);
            if (level.getBlockState(pos).is(SingularityContent.COLLECTION_NODE.get()))
                entries.add(new DismantlePlan.Entry(pos, SingularityContent.COLLECTION_NODE.get()));
        }
        dismantle = DismantlePlan.create(entries);
        operation = Operation.DISMANTLE; owner = player.getUUID(); operationFacing = facing();
        paused = false; status = Status.DISMANTLING; problem = null; saveChanges();
    }
    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (isSingleBlock()) {
            // Compact hubs never inspect, build, move or force-load a multiblock footprint.
            repairMisplacedBlackHoles();
            if (level.getGameTime() % 20 == 0) updateQuantumLink();
            if (collectionActive && networkOnline()) {
                if (nextCollectionTick == Long.MIN_VALUE) nextCollectionTick = level.getGameTime() + collectionIntervalTicks();
                collectResource();
            } else {
                nextCollectionTick = Long.MIN_VALUE;
                status = Status.IDLE;
            }
            tickDuplication();
            return;
        }
        if (level.getGameTime() % 20 == 0 || nextInspection == 0) MultiblockChunkLoading.maintain(this);
        if (level.getGameTime() >= nextInspection) refreshInspection();
        repairMisplacedBlackHoles();
        if (level.getGameTime() % 20 == 0) updateQuantumLink();
        if (collectionActive) {
            if (!formed || !networkOnline() || operation != Operation.IDLE || embedRequested
                    || motion.mode() != SingularityMotionState.RUNNING) {
                collectionActive = false;
                collectionStartTick = Long.MIN_VALUE;
                nextCollectionTick = Long.MIN_VALUE;
                status = Status.IDLE;
                var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
                if (player != null && canManage(player)) motion.stopForCollection(player);
                saveChanges();
            } else if (collectionStartTick != Long.MIN_VALUE
                    && level.getGameTime() - collectionStartTick >= 40) {
                collectionStartTick = Long.MIN_VALUE;
                nextCollectionTick = level.getGameTime() + collectionIntervalTicks();
                status = Status.COLLECTING;
                saveChanges();
            }
        }
        if (collectionActive && !collectionStarting()) collectResource();
        tickDuplication();
        motion.serverTick();
        // Motion is owned by the collection device. Old saves or a failed
        // restart cannot leave a standalone running carriage behind.
        if (!collectionActive && operation == Operation.IDLE && motion.hasBodies() && motion.mode() != SingularityMotionState.DOCKING) {
            var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
            if (player != null && canManage(player)) motion.stopForCollection(player);
        }
        if (embedRequested && !motion.hasBodies()) {
            var relocatingPlayer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
            if (relocatingPlayer != null && canManage(relocatingPlayer)) {
                embedRequested = false;
                if (structureVersion == SingularityStructure.LEGACY_VERSION) SingularityControllerEmbedding.move(this, relocatingPlayer);
                else SingularitySuspendedUpgrade.begin(this, relocatingPlayer);
                if (isRemoved()) return;
                saveChanges(); markForUpdate();
            }
        }
        if (operation == Operation.IDLE && recovery.isEmpty() && !motion.hasPortable()) return;
        var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level || !player.mayBuild()) {
            if (!paused && operation != Operation.IDLE) status = Status.OWNER_OFFLINE;
            return;
        }
        flushRecovery(player);
        if (operation != Operation.BUILD && recovery.isEmpty() && motion.hasPortable()) { recovery.setItemDirect(0, motion.nextPortableStack()); flushRecovery(player); }
        if (operation == Operation.IDLE || paused) return;
        if (operationFacing != facing()) { paused = true; status = Status.CONFLICT; saveChanges(); return; }
        // Retry environmental/material waits once per second, not every tick.
        if (status != Status.BUILDING && status != Status.DISMANTLING && level.getGameTime() % 20 != 0) return;
        if (!recovery.isEmpty()) { status = Status.STORAGE_FULL; return; }
        if (operation == Operation.BUILD) build(player); else dismantle(player);
    }
    /** Move black holes left in the quantum slot by older menu versions into the dedicated accelerator slot. */
    private void repairMisplacedBlackHoles() {
        ItemStack misplaced = quantumInventory.getStackInSlot(0);
        if (!misplaced.is(ModContent.BLACK_HOLE.get())) return;
        ItemStack rejected = duplicationInventory.insertItem(1, misplaced.copy(), false);
        int moved = misplaced.getCount() - rejected.getCount();
        if (moved > 0) {
            quantumInventory.extractItem(0, moved, false);
            saveChanges();
        }
    }

    private void tickDuplication() {
        if (!networkOnline()) { nextDuplicationGenerationTick = Long.MIN_VALUE; clearDuplicationEnergyUsage(); return; }
        var grid = getMainNode().getGrid();
        if (grid == null) { clearDuplicationEnergyUsage(); return; }
        var storage = grid.getStorageService().getInventory();
        var key = AEFluidKey.of(ModFluids.SEQUENCE_MATTER.get());
        var source = new MachineSource(this);
        boolean canCopy = formed && operation == Operation.IDLE && !embedRequested;
        var sample = canCopy ? duplicationInventory.getStackInSlot(0) : ItemStack.EMPTY;
        // Spend stored fluid and return the copied item to ME. Pending outputs/refunds
        // are also returned when the sample is removed or the structure is dismantled.
        duplication.tick(storage, key, source, sample);
        if (!canCopy || blackHoleCount() <= 0) {
            clearDuplicationEnergyUsage();
            nextDuplicationGenerationTick = Long.MIN_VALUE;
            return;
        }
        int interval = duplicationIntervalTicksForMenu();
        long now = level.getGameTime();
        if (nextDuplicationGenerationTick == Long.MIN_VALUE || duplicationGenerationInterval != interval) {
            duplicationGenerationInterval = interval;
            nextDuplicationGenerationTick = now + interval;
            return;
        }
        if (now < nextDuplicationGenerationTick) return;
        nextDuplicationGenerationTick = now + interval;
        clearDuplicationEnergyUsage();
        int requestedUnits = duplicationBatchSizeForMenu();
        // Check capacity before drawing energy. Generation remains independent of
        // the one-bucket cost and can replenish the fluid spent by this tick's copy.
        int accepted = (int) Math.min(requestedUnits, storage.insert(key, requestedUnits, Actionable.SIMULATE, source));
        if (accepted <= 0) return;
        int produced = consumeDuplicationEnergy(accepted);
        if (produced <= 0) return;
        storage.insert(key, produced, Actionable.MODULATE, source);
    }

    /** Returns -1 when this source is unavailable, allowing priority fallback. */
    private int consumeDuplicationEnergy(int requestedUnits) {
        for (String configured : ModConfig.SINGULARITY_DUPLICATION_ENERGY_PRIORITY.get()) {
            if (configured == null) continue;
            String source = configured.trim().toLowerCase(Locale.ROOT);
            int result = switch (source) {
                case "fe" -> consumeFeEnergy(requestedUnits);
                case "ae" -> consumeAeEnergy(requestedUnits);
                default -> -1;
            };
            if (result >= 0) return result;
        }
        return 0;
    }

    private int consumeFeEnergy(int requestedUnits) {
        int perUnit = Math.max(1, ModConfig.SINGULARITY_DUPLICATION_FE_PER_UNIT.get());
        long wanted = (long) requestedUnits * perUnit;
        var storages = new ArrayList<IEnergyStorage>();
        int available = 0;
        for (Direction side : Direction.values()) {
            var neighborPos = worldPosition.relative(side);
            if (!level.hasChunkAt(neighborPos)) continue;
            var neighbor = level.getBlockEntity(neighborPos);
            var storage = neighbor == null ? null : neighbor.getCapability(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (storage == null || !storage.canExtract()) continue;
            storages.add(storage);
            available = (int) Math.min(Integer.MAX_VALUE, (long) available + storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, wanted), true));
        }
        if (available == 0) return -1;
        int units = Math.min(requestedUnits, available / perUnit);
        if (units <= 0) return 0;
        int remaining = units * perUnit;
        for (var storage : storages) {
            if (remaining <= 0) break;
            int extracted = storage.extractEnergy(remaining, false);
            lastDuplicationFeConsumed += extracted;
            remaining -= extracted;
        }
        return remaining == 0 ? units : Math.max(0, units - (remaining + perUnit - 1) / perUnit);
    }

    private int consumeAeEnergy(int requestedUnits) {
        var grid = getMainNode().getGrid();
        if (grid == null) return -1;
        int perUnit = Math.max(1, ModConfig.SINGULARITY_DUPLICATION_AE_PER_UNIT.get());
        double available = grid.getEnergyService().extractAEPower(
                (double) requestedUnits * perUnit, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (available <= 0.001) return -1;
        int units = Math.min(requestedUnits, (int) Math.floor(available / perUnit + 1.0e-6));
        if (units <= 0) return 0;
        double consumed = grid.getEnergyService().extractAEPower(
                (double) units * perUnit, Actionable.MODULATE, PowerMultiplier.CONFIG);
        lastDuplicationAeConsumed += consumed;
        return consumed + 0.001 >= (double) units * perUnit ? units : 0;
    }

    private void collectResource() {
        long now = level.getGameTime();
        if (now < nextCollectionTick) return;
        nextCollectionTick = now + collectionIntervalTicks();

        var collectionItems = configuredCollectionItems();
        if (collectionItems.isEmpty()) {
            status = Status.MATERIAL;
            saveChanges();
            return;
        }

        // Reclaim any previously buffered output before creating another batch.
        if (!recovery.isEmpty()) {
            flushRecovery(null);
            if (!recovery.isEmpty()) {
                status = Status.STORAGE_FULL;
                saveChanges();
                return;
            }
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            status = Status.STORAGE_FULL;
            saveChanges();
            return;
        }
        var storage = grid.getStorageService().getInventory();
        int batchSize = collectionBatchSize();
        var source = new MachineSource(this);
        // Reserve the whole cycle first. This keeps all configured resources simultaneous
        // and prevents a full network from dropping only the later entries in the batch.
        for (var item : collectionItems) {
            var key = AEItemKey.of(new ItemStack(item, batchSize));
            if (storage.insert(key, batchSize, Actionable.SIMULATE, source) < batchSize) {
                status = Status.STORAGE_FULL;
                saveChanges();
                return;
            }
        }
        for (var item : collectionItems) {
            var key = AEItemKey.of(new ItemStack(item, batchSize));
            long inserted = storage.insert(key, batchSize, Actionable.MODULATE, source);
            if (inserted < batchSize) {
                var remainder = new ItemStack(item, batchSize - (int) inserted);
                recovery.setItemDirect(0, remainder);
                status = Status.STORAGE_FULL;
                saveChanges();
                return;
            }
        }
        status = Status.COLLECTING;
        saveChanges();
    }
    private List<Item> configuredCollectionItems() {
        var items = new LinkedHashSet<Item>();
        addConfiguredItemTags(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get(), items);
        var excluded = new HashSet<ResourceLocation>();
        for (var entry : ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get()) {
            if (entry == null) continue;
            var id = ResourceLocation.tryParse(entry.trim());
            if (id != null) excluded.add(id);
        }
        // Apply exclusions after all tag unions, before menu display and storage reservation.
        items.removeIf(item -> excluded.contains(BuiltInRegistries.ITEM.getKey(item)));
        return List.copyOf(items);
    }
    private void addConfiguredItemTags(List<? extends String> configuredTags, Set<Item> result) {
        for (var configured : configuredTags) {
            if (configured == null) continue;
            var text = configured.trim();
            if (text.startsWith("#")) text = text.substring(1);
            var id = ResourceLocation.tryParse(text);
            if (id == null) continue;
            var tag = TagKey.create(Registries.ITEM, id);
            level.registryAccess().lookupOrThrow(Registries.ITEM).get(tag)
                    .ifPresent(holders -> holders.forEach(holder -> result.add(holder.value())));
        }
    }
    private static int collectionBatchSize() {
        return Math.max(1, ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.get());
    }
    private static long collectionIntervalTicks() {
        return Math.max(1L, ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get());
    }
    private void refreshInspection() {
        inspection = SingularityStructure.inspect(level, worldPosition, facing(), motion::owns, structureVersion);
        boolean value = inspection.formed();
        if (formed != value) { formed = value; markForUpdate(); saveChanges(); }
        if (inspection.unloaded() == 0) scanRequested = false;
        nextInspection = level.getGameTime() + 40;
    }
    boolean allowed(ServerPlayer player, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.mayInteract(player, pos) && player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY);
    }
    private void waitAt(Status reason, BlockPos pos) { status = reason; problem = pos; saveChanges(); }
    private void build(ServerPlayer player) {
        if (!SingularityStructure.fits(level, worldPosition, operationFacing, structureVersion)) { waitAt(Status.OUT_OF_BOUNDS, worldPosition); return; }
        status = Status.BUILDING; neededMaterial = null; problem = null;
        int placed = 0, visited = 0;
        while (cursor < structureParts().size() && placed < 64 && visited++ < 512) {
            var part = structureParts().get(cursor);
            if (SingularityStructure.isController(part)) { cursor++; continue; }
            var pos = SingularityStructure.worldPos(worldPosition, operationFacing, part, structureVersion);
            if (!level.hasChunkAt(pos)) { waitAt(Status.UNLOADED, pos); return; }
            var current = level.getBlockState(pos);
            if (SingularityStructure.matches(current, part, operationFacing)) { cursor++; continue; }
            if (!current.canBeReplaced() || current.hasBlockEntity()) { waitAt(Status.CONFLICT, pos); return; }
            if (!allowed(player, pos)) { waitAt(Status.PROTECTED, pos); return; }
            var material = new ItemStack(SingularityStructure.block(part.type()));
            if (!takeMaterial(player, material, part.type())) { neededMaterial = part.type(); waitAt(Status.MATERIAL, pos); return; }
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            var expected = SingularityStructure.state(part, operationFacing);
            boolean changed = level.setBlock(pos, expected, Block.UPDATE_CLIENTS);
            boolean rejected = changed && MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                    snapshot, current, player));
            if (!changed || rejected) {
                if (changed) snapshot.restore(true, false);
                if (!player.getAbilities().instabuild) recovery.setItemDirect(0, material);
                waitAt(rejected ? Status.PROTECTED : Status.CONFLICT, pos); return;
            }
            level.updateNeighborsAt(pos, expected.getBlock());
            cursor++; placed++;
        }
        if (cursor >= structureParts().size()) {
            refreshInspection();
            if (inspection.unloaded() > 0) { waitAt(Status.UNLOADED, inspection.problem()); return; }
            if (!inspection.formed()) {
                if (inspection.missing() > 0) cursor = 0;
                waitAt(Status.CONFLICT, inspection.problem()); return;
            }
            finish();
        }
        saveChanges();
    }
    private void dismantle(ServerPlayer player) {
        status = Status.DISMANTLING; problem = null;
        for (int budget = 0; budget < 64 && dismantle != null && !dismantle.isComplete(); budget++) {
            var entry = dismantle.current(); var pos = entry.pos();
            if (!level.hasChunkAt(pos)) { waitAt(Status.UNLOADED, pos); return; }
            var current = level.getBlockState(pos);
            if (!entry.matches(current)) { dismantle.advance(); continue; }
            // Revalidate the authored state too: a modified double slab must never be erased for one item.
            // Membership is bound to a saved exact position and block; states are checked against the blueprint below.
            var part = partAt(pos);
            boolean compatibilityNode = part == null
                    && current.is(SingularityContent.COLLECTION_NODE.get())
                    && SingularityStructure.isCompatibilityCollectionNode(worldPosition, operationFacing, pos, structureVersion);
            if (!compatibilityNode && (part == null || !SingularityStructure.matches(current, part, operationFacing))) {
                dismantle.advance(); continue;
            }
            if (!allowed(player, pos) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, current, player))) {
                waitAt(Status.PROTECTED, pos); return;
            }
            if (!recovery.isEmpty()) { waitAt(Status.STORAGE_FULL, pos); return; }
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) { waitAt(Status.CONFLICT, pos); return; }
            if (!player.getAbilities().instabuild) recovery.setItemDirect(0, new ItemStack(entry.block()));
            dismantle.advance(); saveChanges(); flushRecovery(player);
            if (!recovery.isEmpty()) { waitAt(Status.STORAGE_FULL, pos); return; }
        }
        if (dismantle == null || dismantle.isComplete()) finish();
        saveChanges();
    }
    private SingularityStructure.Part partAt(BlockPos pos) {
        return SingularityStructure.partAt(worldPosition, operationFacing, pos, structureVersion);
    }
    private boolean takeMaterial(ServerPlayer player, ItemStack template, SingularityStructure.Type type) {
        if (player.getAbilities().instabuild) return true;
        if (motion.takePortable(type)) return true;
        for (var stack : player.getInventory().items) if (ItemStack.isSameItemSameTags(stack, template)) {
            stack.shrink(1); player.getInventory().setChanged(); return true;
        }
        var grid = getMainNode().getGrid();
        return networkOnline() && grid != null && grid.getStorageService().getInventory().extract(
                AEItemKey.of(template), 1, Actionable.MODULATE, new PlayerSource(player)) == 1;
    }
    private void flushRecovery(ServerPlayer player) {
        var stack = recovery.getStackInSlot(0).copy();
        if (stack.isEmpty()) return;
        var grid = getMainNode().getGrid();
        IActionSource source = player == null ? new MachineSource(this) : new PlayerSource(player);
        if (networkOnline() && grid != null) stack.shrink((int) grid.getStorageService().getInventory().insert(
                AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source));
        if (!stack.isEmpty() && player != null) player.getInventory().add(stack);
        recovery.setItemDirect(0, stack); saveChanges();
    }
    private void finish() {
        operation = Operation.IDLE; paused = false; status = Status.COMPLETE; problem = null;
        nextInspection = 0; saveChanges();
    }
    @Override public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        quantumInventory.writeToNBT(tag, QUANTUM_INVENTORY_TAG);
        duplicationInventory.writeToNBT(tag, DUPLICATION_INVENTORY_TAG);
        tag.put("singularityDuplication", duplication.save());
        if (isSingleBlock()) {
            tag.putBoolean("singularityCollectionActive", collectionActive);
            return;
        }
        tag.put("singularityMotion", motion.save());
        tag.putInt("singularityMaterialVersion", SingularityStructure.MATERIAL_VERSION);
        tag.putInt("singularityVersion", structureVersion); tag.putInt("singularityLayout", structureVersion);
        tag.putBoolean("singularityEmbedRequested", embedRequested); tag.putString("operation", operation.name());
        tag.putInt("cursor", cursor); tag.putBoolean("paused", paused); tag.putBoolean("formed", formed);
        tag.putBoolean("singularityCollectionActive", collectionActive);
        tag.putString("operationFacing", operationFacing.getName());
        if (owner != null) tag.putUUID("owner", owner);
        if (dismantle != null && operation == Operation.DISMANTLE) tag.put("dismantle", dismantle.save());
    }
    @Override public void loadTag(CompoundTag tag) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag);
        quantumInventory.readFromNBT(tag, QUANTUM_INVENTORY_TAG);
        duplicationInventory.readFromNBT(tag, DUPLICATION_INVENTORY_TAG);
        duplication.load(tag.getCompound("singularityDuplication"));
        nextDuplicationGenerationTick = Long.MIN_VALUE;
        duplicationGenerationInterval = 0;
        clearDuplicationEnergyUsage();
        if (isSingleBlock()) {
            // Only production inventories/settings are portable; no construction or motion state.
            formed = true; operation = Operation.IDLE; paused = false; embedRequested = false;
            structureVersion = SingularityStructure.VERSION; motion.clear();
            inspection = new SingularityStructure.Inspection(1, 0, 0, 0, Map.of(), null);
            collectionActive = tag.getBoolean("singularityCollectionActive");
            collectionStartTick = Long.MIN_VALUE; nextCollectionTick = Long.MIN_VALUE;
            status = collectionActive ? Status.COLLECTING : Status.IDLE;
            return;
        }
        int storedLayout = tag.contains("singularityLayout") ? tag.getInt("singularityLayout") : tag.getInt("singularityVersion");
        structureVersion = storedLayout == SingularityStructure.LEGACY_VERSION || storedLayout == SingularityStructure.EMBEDDED_VERSION
                ? storedLayout : SingularityStructure.VERSION;
        embedRequested = structureVersion < SingularityStructure.VERSION && tag.getBoolean("singularityEmbedRequested");
        if (tag.contains("singularityPortableMaterials")) motion.loadPortable(SingularityStructure.readMaterialCounts(tag, "singularityPortableMaterials"));
        else if (tag.contains("singularityMotion")) motion.load(tag.getCompound("singularityMotion"));
        try { operation = Operation.valueOf(tag.getString("operation")); } catch (IllegalArgumentException ignored) { operation = Operation.IDLE; }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        operationFacing = Direction.byName(tag.getString("operationFacing"));
        if (operationFacing == null || operationFacing.getAxis().isVertical()) operationFacing = facing();
        cursor = tag.getInt("singularityMaterialVersion") < SingularityStructure.MATERIAL_VERSION
                && operation == Operation.BUILD ? 0 : com.atir.molecularmanipulator.util.MathCompat.clamp(tag.getInt("cursor"), 0, structureParts().size());
        paused = tag.getBoolean("paused"); formed = tag.getBoolean("formed");
        collectionActive = tag.getBoolean("singularityCollectionActive");
        collectionStartTick = Long.MIN_VALUE;
        nextCollectionTick = Long.MIN_VALUE;
        dismantle = tag.contains("dismantle") ? DismantlePlan.load(tag.getCompound("dismantle")) : null;
        if (operation == Operation.DISMANTLE && dismantle == null) operation = Operation.IDLE;
        if (operation != Operation.IDLE && tag.getInt("singularityVersion") != structureVersion) {
            operation = Operation.IDLE; status = Status.VERSION_CHANGED;
        } else if (collectionActive) status = Status.COLLECTING;
        else status = paused ? Status.PAUSED : Status.IDLE;
        nextInspection = 0;
    }
    public boolean hasRemovalRecovery() {
        return !recovery.isEmpty() || !quantumInventory.isEmpty() || !duplicationInventory.isEmpty()
                || duplication.hasContents() || motion.hasBodies() || motion.hasPortable();
    }
    @Override public void addAdditionalDrops(net.minecraft.world.level.Level level, BlockPos pos, List<ItemStack> drops) {
        if (!hasRemovalRecovery()) return;
        var data = new CompoundTag(); super.saveAdditional(data);
        var portable = new CompoundTag(); portable.put("inv", data.getCompound("inv"));
        quantumInventory.writeToNBT(portable, QUANTUM_INVENTORY_TAG);
        duplicationInventory.writeToNBT(portable, DUPLICATION_INVENTORY_TAG);
        portable.put("singularityDuplication", duplication.save());
        SingularityStructure.writeMaterialCounts(portable, "singularityPortableMaterials", motion.portableCounts());
        drops.add(RetainedBlockContents.createDrop(this, portable));
    }
    @Override public void clearContent() { super.clearContent(); quantumInventory.clear(); duplicationInventory.clear(); duplication.clear(); motion.clear(); }
    @Override protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data); data.writeBoolean(formed); data.writeVarInt(motion.mask());
        data.writeVarInt(structureVersion); data.writeBoolean(embedRequested);
    }
    @Override protected boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data); boolean previous = formed; formed = data.readBoolean();
        int previousMask = motion.mask(); motion.setClientMask(data.readVarInt());
        int previousVersion = structureVersion; structureVersion = data.readVarInt();
        boolean previousRequest = embedRequested; embedRequested = data.readBoolean();
        return changed || previous != formed || previousMask != motion.mask() || previousVersion != structureVersion || previousRequest != embedRequested;
    }

    public void requestEmbedding(ServerPlayer player) {
        if (isSingleBlock() || !canManage(player) || operation != Operation.IDLE || structureVersion != SingularityStructure.LEGACY_VERSION
                || embedRequested || motion.mode() == SingularityMotionState.DOCKING || motion.hasPortable()) return;
        refreshInspection();
        if (!formed) { status = Status.CONFLICT; problem = inspection.problem(); return; }
        owner = player.getUUID(); embedRequested = true;
        if (motion.hasBodies()) motion.dock(player, false);
        saveChanges(); markForUpdate();
    }

    public void requestSuspendedUpgrade(ServerPlayer player) {
        if (isSingleBlock() || !canManage(player) || operation != Operation.IDLE || structureVersion != SingularityStructure.EMBEDDED_VERSION
                || embedRequested || motion.mode() == SingularityMotionState.DOCKING || motion.hasPortable()) return;
        refreshInspection();
        if (!formed) { status = Status.CONFLICT; problem = inspection.problem(); return; }
        owner = player.getUUID(); embedRequested = true;
        if (motion.hasBodies()) motion.dock(player, false);
        saveChanges(); markForUpdate();
    }
    void beginSuspendedConstruction(ServerPlayer player, int[] refunds) {
        structureVersion = SingularityStructure.VERSION; embedRequested = false;
        motion.loadPortable(player.getAbilities().instabuild ? new int[refunds.length] : refunds);
        owner = player.getUUID(); operationFacing = facing(); cursor = 0; paused = false;
        operation = Operation.BUILD; status = Status.BUILDING; problem = null; neededMaterial = null;
        refreshInspection(); saveChanges(); markForUpdate();
    }

    void embeddingFailed(Status reason, BlockPos pos) { waitAt(reason, pos); markForUpdate(); }
    void embeddingComplete() { refreshInspection(); status = Status.COMPLETE; saveChanges(); markForUpdate(); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        var center = worldPos(new net.minecraft.core.BlockPos(0, 64, 0));
        return new net.minecraft.world.phys.AABB(center.getX() - 54, center.getY() - 47, center.getZ() - 54,
                center.getX() + 55, center.getY() + 71, center.getZ() + 55);
    }
}
