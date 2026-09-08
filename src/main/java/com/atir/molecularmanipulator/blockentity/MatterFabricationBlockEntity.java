package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.util.SettingsFrom;
import appeng.api.features.Locatables;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchProgress;
import com.atir.molecularmanipulator.research.ResearchVisualState;
import com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MatterFabricationBlockEntity extends AENetworkInvBlockEntity {
    public static final int INPUT_SLOTS = 4;
    public static final int OUTPUT_SLOTS = 2;
    public static final double IDLE_POWER = 16.0;
    public static final double QUANTUM_LINK_POWER = 512.0;
    private static final String QUANTUM_INVENTORY_TAG = "fabrication_quantum_inventory";
    private static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final String PROGRESS_TAG = "fabrication_progress";
    private static final String ACTIVE_RECIPE_TAG = "fabrication_recipe";
    private static final String PATTERN_RECIPE_TAG = "fabrication_pattern_recipe";
    private static final String BUILDING_TAG = "fabrication_building";
    private static final String DISMANTLING_TAG = "fabrication_dismantling";
    private static final String BUILD_CURSOR_TAG = "fabrication_build_cursor";
    private static final String BUILD_OWNER_TAG = "fabrication_build_owner";
    private static final String STRUCTURE_UPDATE_TAG = "fabrication_structure_update";
    private static final String UPDATE_SOURCE_LAYOUT_TAG = "fabrication_update_source_layout";
    private static final String UPDATE_BUILDING_PHASE_TAG = "fabrication_update_building_phase";
    private static final String PENDING_SERVICE_BLOCKS_TAG = "fabrication_pending_service_blocks";
    private static final String DISMANTLE_PLAN_TAG = "fabrication_dismantle_plan";
    private static final String DISMANTLE_RECOVERY_TAG = "fabrication_dismantle_recovery";
    private static final String KNOWN_LAYOUT_TAG = "fabrication_known_layout";
    private static final String BLUEPRINT_VERSION_TAG = "fabrication_blueprint_version";
    private static final String BUILD_TARGET_LAYOUT_TAG = "fabrication_build_target_layout";
    private static final int BUILD_BLOCKS_PER_TICK = 64;
    private static final int MIGRATION_SCAN_PER_TICK = 512;

    private final AppEngInternalInventory inventory = new AppEngInternalInventory(this,
            INPUT_SLOTS + OUTPUT_SLOTS);
    private final MachineSource actionSource = new MachineSource(this);
    private static final String RESEARCH_TAG = "fabrication_research";
    private final MatterResearchProgress research = new MatterResearchProgress();
    private static final String BATCH_TAG = "fabrication_owned_batch";
    private final MatterFabricationBatch batch = new MatterFabricationBatch();
    private long manualCrafts = 1;
    private final AppEngInternalInventory quantumInventory = new AppEngInternalInventory(this, 1);
    private IGridConnection quantumConnection;
    private IGridNode quantumRemoteNode;
    private long quantumConnectionFrequency;
    private long claimedQuantumFrequency;
    private long nextQuantumCheck;
    private QuantumLinkState quantumLinkState = QuantumLinkState.EMPTY;
    private MatterFabricationStructure.Inspection inspection =
            new MatterFabricationStructure.Inspection(MatterFabricationStructure.parts().size(), 0,
                    MatterFabricationStructure.parts().size(), 0);
    private boolean structureFormed;
    private boolean clientStructureFormed;
    private boolean clientRunning;
    private int clientRecipeProgress;
    private int clientRecipeDuration;
    private double clientProgressSample;
    private long completionTick = Long.MIN_VALUE;
    private long clientCompletionTick = Long.MIN_VALUE;
    private boolean clientVisualInitialized;
    private double clientPulseStarted = Double.NaN;
    private ResearchVisualState researchVisualState = ResearchVisualState.EMPTY;
    private ResearchVisualState clientResearchVisualState = ResearchVisualState.EMPTY;
    private double clientResearchPulseStarted = Double.NaN;
    private double clientResearchSample;
    private int progress;
    private ResourceLocation activeRecipeId;
    private ResourceLocation patternRecipeId;
    private BlockPos processingAssembly;
    private int nextAssembly;
    private ProcessingState processingState = ProcessingState.STRUCTURE_INCOMPLETE;
    private int currentProcessingTime;
    private double currentAePerTick;
    private long nextStructureCheck;
    private boolean building;
    private boolean dismantling;
    private int buildCursor;
    private UUID buildOwner;
    private MatterFabricationStructure.StructureLayout structureLayout =
            MatterFabricationStructure.StructureLayout.NONE;
    private MatterFabricationStructure.StructureLayout updateSourceLayout =
            MatterFabricationStructure.StructureLayout.NONE;
    private boolean updatingStructure;
    private boolean updateBuildingPhase;
    private MatterFabricationStructure.StructureLayout buildTargetLayout = MatterFabricationStructure.StructureLayout.CURRENT;
    private final List<ItemStack> pendingMigrationServiceBlocks = new ArrayList<>();
    private DismantlePlan dismantlePlan;
    private ItemStack pendingDismantleRecovery = ItemStack.EMPTY;
    private MatterFabricationStructure.StructureLayout lastKnownLayout =
            MatterFabricationStructure.StructureLayout.NONE;

    public MatterFabricationBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(), pos, state);
        quantumInventory.setMaxStackSize(0, 1);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(IDLE_POWER);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get();
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return inventory;
    }

    public InternalInventory getInputInventory() {
        return inventory.getSubInventory(0, INPUT_SLOTS);
    }

    public InternalInventory getOutputInventory() {
        return inventory.getSubInventory(INPUT_SLOTS, INPUT_SLOTS + OUTPUT_SLOTS);
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return InternalInventory.empty();
    }

    @Override
    public void onReady() {
        super.onReady();
        scheduleStructureCheck();
        updateQuantumLink();
    }

    @Override
    public void onChunkUnloaded() {
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        releaseQuantumFrequency();
        clearQuantumLinkForRemoval();
        super.setRemoved();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (hasRemovalRecovery()) drops.add(createRemovalRecovery());
    }

    public boolean hasRemovalRecovery() {
        return !inventory.isEmpty() || !quantumInventory.isEmpty() || batch.hasWork() || research.hasStoredMaterials()
                || !pendingMigrationServiceBlocks.isEmpty() || !pendingDismantleRecovery.isEmpty();
    }

    private ItemStack createRemovalRecovery() {
        var payload = new CompoundTag();
        super.saveAdditional(payload);
        // Only the inherited inventory belongs in the portable payload, not the AE grid node.
        var contents = new CompoundTag();
        contents.put("inv", payload.getCompound("inv"));
        quantumInventory.writeToNBT(contents, QUANTUM_INVENTORY_TAG);
        if (research.hasProgress()) contents.put(RESEARCH_TAG, research.save());
        if (batch.hasWork()) contents.put(BATCH_TAG, batch.save());
        if (!pendingDismantleRecovery.isEmpty()) contents.put(DISMANTLE_RECOVERY_TAG, pendingDismantleRecovery.save(new CompoundTag()));
        var services = new ListTag();
        for (var stack : pendingMigrationServiceBlocks) if (!stack.isEmpty()) services.add(stack.save(new CompoundTag()));
        contents.put(PENDING_SERVICE_BLOCKS_TAG, services);
        return RetainedBlockContents.createDrop(this, contents);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        quantumInventory.clear();
        batch.clear();
        research.clearStoredMaterials();
        pendingMigrationServiceBlocks.clear();
        pendingDismantleRecovery = ItemStack.EMPTY;
    }

    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory changedInventory, int slot) {
        if (changedInventory == quantumInventory && level != null && !level.isClientSide()) {
            disconnectQuantumLink(QuantumLinkState.SEARCHING);
            updateQuantumLink();
        }
        // Kept only so pre-redesign controller inventories deserialize safely.
        // The hidden legacy slots are evacuated to the service ports or network.
    }

    public AppEngInternalInventory getQuantumInventory() {
        return quantumInventory;
    }

    public long getQuantumFrequency() {
        var stack = quantumInventory.getStackInSlot(0);
        if (!MolecularCenterBlockEntity.isValidQuantumSingularity(stack)) {
            return 0;
        }
        return (stack.hasTag() ? stack.getTag().getLong(appeng.blockentity.qnb.QuantumBridgeBlockEntity.TAG_FREQUENCY) : 0L);
    }

    public QuantumLinkState getQuantumLinkState() {
        return quantumLinkState;
    }

    private void updateQuantumLink() {
        if (!(level instanceof ServerLevel serverLevel)) {
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
            getMainNode().setIdlePowerUsage(IDLE_POWER + QUANTUM_LINK_POWER);
            quantumLinkState = connectedQuantumLinkState();
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
        return structureFormed
                ? QuantumLinkState.CONNECTED
                : QuantumLinkState.CONNECTED_BUILD_ONLY;
    }

    private void disconnectQuantumLink(QuantumLinkState nextState) {
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

    private void clearQuantumLinkForRemoval() {
        quantumConnection = null;
        quantumRemoteNode = null;
        quantumConnectionFrequency = 0;
        quantumLinkState = QuantumLinkState.SEARCHING;
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

    public void openMenu(Player player) {
        MenuOpener.open(ModContent.MATTER_FABRICATION_MENU.get(), player,
                MenuLocators.forBlockEntity(this));
    }

    public MatterResearchProgress getResearch() { return research; }

    public boolean consumeResearchPower(double amount) { return consumePower(amount); }

    double simulateBatchPower(double amount) {
        var grid = getMainNode().getGrid();
        return grid == null ? 0 : grid.getEnergyService().extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag builder, Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM && (research.hasProgress() || batch.hasWork())) {
            var tag = new CompoundTag();
            if (research.hasProgress()) tag.put(RESEARCH_TAG, research.save());
            if (batch.hasWork()) tag.put(BATCH_TAG, batch.save());
            builder.merge(tag);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, CompoundTag input, Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM && level != null && !level.isClientSide()) {
            var tag = input;
            if (tag.contains(BATCH_TAG)) batch.load(tag.getCompound(BATCH_TAG));
            if (tag.contains(RESEARCH_TAG)) {
                research.load(tag.getCompound(RESEARCH_TAG));
                saveChanges();
            }
        }
    }

    public Set<ChunkPos> getChunkLoadingChunks() {
        var result = new HashSet<ChunkPos>();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (structureLayout.isFormed()) result.addAll(MatterFabricationStructure.chunkFootprint(worldPosition, facing, structureLayout));
        if (building || dismantling || updatingStructure) {
            if (building || updatingStructure) result.addAll(MatterFabricationStructure.chunkFootprint(worldPosition, facing, buildTargetLayout));
            if (updatingStructure) result.addAll(MatterFabricationStructure.chunkFootprint(worldPosition, facing, updateSourceLayout));
            if (dismantling) result.addAll(MatterFabricationStructure.chunkFootprint(worldPosition, facing, lastKnownLayout));
            if (dismantlePlan != null) result.addAll(dismantlePlan.remainingChunks());
        }
        return result;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (building || dismantling || updatingStructure) MultiblockChunkLoading.maintain(this);
        long gameTime = level.getGameTime();
        if (gameTime >= nextQuantumCheck) {
            updateQuantumLink();
            nextQuantumCheck = gameTime + STRUCTURE_CHECK_INTERVAL;
        }
        evacuateLegacyControllerInventory();
        if (building) {
            processBuild();
            tickResearch();
            if (building) {
                setState(ProcessingState.BUILDING);
            }
            updatePoweredState(false);
            return;
        }
        if (dismantling) {
            processDismantle();
            tickResearch();
            if (dismantling) {
                setState(ProcessingState.DISMANTLING);
            }
            updatePoweredState(false);
            return;
        }
        if (gameTime >= nextStructureCheck) {
            refreshStructure();
            nextStructureCheck = gameTime + STRUCTURE_CHECK_INTERVAL;
        }
        tickResearch();
        if (batch.hasWork()) processBatch();
        else if (!processPatternBuffers()) processRecipe();
    }

    public void scheduleStructureCheck() {
        nextStructureCheck = level == null ? 0 : Math.min(nextStructureCheck, level.getGameTime() + 1);
    }

    private void tickResearch() {
        research.tick(this);
        if (level.getGameTime() % 5 != 0) return;
        var next = researchVisualSnapshot();
        if (!next.equals(researchVisualState)) {
            researchVisualState = next;
            markForUpdate();
        }
    }

    private ResearchVisualState researchVisualSnapshot() {
        return research.visualState(worldPosition.asLong() ^ (level == null ? 0 : level.dimension().location().hashCode()));
    }

    public void refreshStructure() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var nextInspection = MatterFabricationStructure.inspect(level, worldPosition, facing);
        var nextLayout = nextInspection.formed()
                ? MatterFabricationStructure.StructureLayout.CURRENT
                : MatterFabricationStructure.detectLayout(level, worldPosition, facing);
        boolean changed = structureFormed != nextInspection.formed()
                || structureLayout != nextLayout;
        inspection = nextInspection;
        structureFormed = nextInspection.formed();
        structureLayout = nextLayout;
        if (nextLayout.isFormed()) {
            lastKnownLayout = nextLayout;
        }
        updateOptionalLinks();
        updateQuantumLink();
        if (!structureFormed) {
            resetProgress();
        }
        if (changed) {
            onGridConnectableSidesChanged();
            markForUpdate();
        }
        saveChanges();
        MultiblockChunkLoading.maintain(this);
    }

    public void startBuild(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || dismantling
                || updatingStructure || !player.mayBuild()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (!isCurrentBuildHeightValid(player)) return;
        var currentInspection = MatterFabricationStructure.inspect(level, worldPosition, facing);
        var detectedLayout = currentInspection.formed()
                ? MatterFabricationStructure.StructureLayout.CURRENT
                : MatterFabricationStructure.detectLayout(level, worldPosition, facing);
        structureLayout = detectedLayout;
        if (detectedLayout.requiresUpdate()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.structure_update_requires_confirmation"), false);
            saveChanges();
            return;
        }
        if (currentInspection.formed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.already_formed"), false);
            return;
        }
        if (currentInspection.conflicts() > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.conflicts", currentInspection.conflicts()), false);
            return;
        }
        building = true;
        buildTargetLayout = MatterFabricationStructure.StructureLayout.CURRENT;
        lastKnownLayout = MatterFabricationStructure.StructureLayout.CURRENT;
        dismantlePlan = null;
        buildCursor = 0;
        buildOwner = player.getUUID();
        setState(ProcessingState.BUILDING);
        saveChanges();
    }

    private boolean isCurrentBuildHeightValid(ServerPlayer player) {
        int minimum = MatterFabricationStructure.parts().stream().mapToInt(MatterFabricationStructure.Part::y).min().orElse(0);
        int maximum = MatterFabricationStructure.parts().stream().mapToInt(MatterFabricationStructure.Part::y).max().orElse(0);
        int below = MatterFabricationStructure.CONTROLLER_Y - minimum;
        int above = maximum - MatterFabricationStructure.CONTROLLER_Y;
        int controllerY = worldPosition.getY();
        if (controllerY - below >= level.getMinBuildHeight() && controllerY + above < level.getMaxBuildHeight()) return true;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.molecularmanipulator.preview_build_height", controllerY, below, above,
                controllerY - below, controllerY + above, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1,
                level.getMinBuildHeight() + below, level.getMaxBuildHeight() - 1 - above), false);
        return false;
    }

    public void startStructureUpdate(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || dismantling || !player.mayBuild()) {
            return;
        }
        if (updatingStructure && updateSourceLayout.requiresUpdate()) {
            buildOwner = player.getUUID();
            if (updateBuildingPhase) {
                building = true;
                setState(ProcessingState.BUILDING);
            } else {
                dismantling = true;
                setState(ProcessingState.DISMANTLING);
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.structure_update_started"), false);
            saveChanges();
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var detected = MatterFabricationStructure.detectLayout(level, worldPosition, facing);
        if (!detected.requiresUpdate() || !canMigrateLayout(player, facing, detected)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.structure_update_unavailable"), false);
            return;
        }

        structureLayout = detected;
        lastKnownLayout = detected;
        updateSourceLayout = detected;
        buildTargetLayout = MatterFabricationStructure.StructureLayout.CURRENT;
        updatingStructure = true;
        updateBuildingPhase = false;
        dismantling = true;
        building = false;
        buildCursor = MatterFabricationStructure.parts(detected).size();
        buildOwner = player.getUUID();
        structureFormed = false;
        resetProgress();
        updateOptionalLinks();
        setState(ProcessingState.DISMANTLING);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.molecularmanipulator.structure_update_started"), false);
        saveChanges();
    }

    private boolean canMigrateLayout(ServerPlayer player, Direction facing,
            MatterFabricationStructure.StructureLayout sourceLayout) {
        var removable = new HashSet<BlockPos>();
        for (var part : MatterFabricationStructure.parts(sourceLayout)) {
            if (MatterFabricationStructure.isController(part)) {
                continue;
            }
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, part);
            if (!level.hasChunkAt(pos)
                    || pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()
                    || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
                return false;
            }
            removable.add(pos);
        }
        for (var part : MatterFabricationStructure.parts()) {
            if (MatterFabricationStructure.isController(part)) {
                continue;
            }
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, part);
            if (!level.hasChunkAt(pos)
                    || pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()
                    || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
                return false;
            }
            var state = level.getBlockState(pos);
            if (!MatterFabricationStructure.matches(state, part)
                    && !state.canBeReplaced() && !removable.contains(pos)) {
                return false;
            }
        }
        return true;
    }

    private void processBuild() {
        if (level == null || buildOwner == null || level.getServer() == null) {
            return;
        }
        var player = level.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null || !player.mayBuild()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var parts = MatterFabricationStructure.parts(buildTargetLayout);
        int budget = BUILD_BLOCKS_PER_TICK;
        while (budget-- > 0 && buildCursor < parts.size()) {
            var part = parts.get(buildCursor++);
            if (MatterFabricationStructure.isController(part)) {
                continue;
            }
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, part);
            var expected = MatterFabricationStructure.partState(part.type());
            var current = level.getBlockState(pos);
            if (MatterFabricationStructure.matchesLayoutPart(current, part, buildTargetLayout)) {
                continue;
            }
            if (!current.canBeReplaced() || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
                abortStructureBuild(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.molecularmanipulator.fabrication.build_conflict",
                        pos.getX(), pos.getY(), pos.getZ()), false);
                saveChanges();
                return;
            }
            var material = new ItemStack(expected.getBlock().asItem());
            if (!takeBuildMaterial(player, material)) {
                abortStructureBuild(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.molecularmanipulator.fabrication.missing_material",
                        material.getHoverName()), false);
                saveChanges();
                return;
            }
            if (!level.setBlock(pos, expected, 3)) {
                refundBuildMaterial(player, material);
                abortStructureBuild(player);
                saveChanges();
                return;
            }
        }
        if (buildCursor >= parts.size()) {
            boolean completedUpdate = updatingStructure;
            building = false;
            buildCursor = parts.size();
            if (completedUpdate) {
                if (!restoreMigrationServiceBlocks(player, facing)) {
                    building = true;
                    saveChanges();
                    return;
                }
                updatingStructure = false;
                updateBuildingPhase = false;
                updateSourceLayout = MatterFabricationStructure.StructureLayout.NONE;
            }
            refreshStructure();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    completedUpdate
                            ? "message.molecularmanipulator.structure_update_complete"
                            : "message.molecularmanipulator.fabrication.build_complete"), false);
        }
        saveChanges();
    }

    private void abortStructureBuild(ServerPlayer player) {
        building = false;
        if (updatingStructure) {
            buildCursor = Math.max(0, buildCursor - 1);
            setState(ProcessingState.STRUCTURE_INCOMPLETE);
            return;
        }
        buildOwner = null;
    }

    private boolean isMigrationServiceBlock(BlockState state) {
        return state.is(ModContent.MATTER_FABRICATION_ITEM_INPUT.get())
                || state.is(ModContent.MATTER_FABRICATION_ITEM_OUTPUT.get())
                || state.is(ModContent.MATTER_FABRICATION_FLUID_INPUT.get())
                || state.is(ModContent.MATTER_FABRICATION_FLUID_OUTPUT.get())
                || state.is(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get());
    }

    private ItemStack captureMigrationServiceBlock(BlockPos pos, BlockState state) {
        var recovered = new ItemStack(state.getBlock());
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.saveToItem(recovered);
        }
        return recovered;
    }

    private boolean restoreMigrationServiceBlocks(ServerPlayer player, Direction facing) {
        if (!pendingDismantleRecovery.isEmpty()) {
            pendingDismantleRecovery = storeDismantleRecovery(player, pendingDismantleRecovery);
            if (!pendingDismantleRecovery.isEmpty()) return false;
        }
        if (pendingMigrationServiceBlocks.isEmpty()) {
            return true;
        }
        var remaining = new ArrayList<>(pendingMigrationServiceBlocks);
        pendingMigrationServiceBlocks.clear();

        // Fixed I/O ports are restored first so pattern assemblies can occupy
        // only genuinely unused optional bays.
        remaining.removeIf(stack -> fixedServiceTarget(stack) != null
                && placeMigrationServiceBlockAnywhere(player, facing, stack));
        remaining.removeIf(stack -> stack.is(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM.get())
                && placeMigrationPatternAssembly(player, facing, stack));
        for (var stack : remaining) {
            var remainder = storeDismantleRecovery(player, stack);
            if (!remainder.isEmpty()) pendingMigrationServiceBlocks.add(remainder);
        }
        updateOptionalLinks();
        return pendingMigrationServiceBlocks.isEmpty() && pendingDismantleRecovery.isEmpty();
    }

    private MatterFabricationStructure.OptionalPart fixedServiceTarget(ItemStack stack) {
        if (stack.is(ModContent.MATTER_FABRICATION_ITEM_INPUT_ITEM.get())) {
            return MatterFabricationStructure.ITEM_INPUT;
        }
        if (stack.is(ModContent.MATTER_FABRICATION_ITEM_OUTPUT_ITEM.get())) {
            return MatterFabricationStructure.ITEM_OUTPUT;
        }
        if (stack.is(ModContent.MATTER_FABRICATION_FLUID_INPUT_ITEM.get())) {
            return MatterFabricationStructure.FLUID_INPUT;
        }
        if (stack.is(ModContent.MATTER_FABRICATION_FLUID_OUTPUT_ITEM.get())) {
            return MatterFabricationStructure.FLUID_OUTPUT;
        }
        return null;
    }

    private boolean placeMigrationPatternAssembly(ServerPlayer player, Direction facing,
            ItemStack stack) {
        return placeMigrationServiceBlockAnywhere(player, facing, stack);
    }

    private boolean placeMigrationServiceBlockAnywhere(ServerPlayer player, Direction facing,
            ItemStack stack) {
        for (var target : MatterFabricationStructure.patternAssemblyBays()) {
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, target);
            var expected = MatterFabricationStructure.partState(target.type());
            if (level.getBlockState(pos).is(expected.getBlock())
                    && placeMigrationServiceBlockAt(player, pos, stack, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean placeMigrationServiceBlockAt(ServerPlayer player, BlockPos pos,
            ItemStack stack, MatterFabricationStructure.Part target) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        var current = level.getBlockState(pos);
        if (!current.is(MatterFabricationStructure.partState(target.type()).getBlock())
                || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
            return false;
        }
        var recoveredCasing = new ItemStack(current.getBlock());
        if (!pendingDismantleRecovery.isEmpty()
                || !player.getAbilities().instabuild && !canStoreDismantleRecovery(player, recoveredCasing)) {
            return false;
        }
        if (!level.setBlock(pos, blockItem.getBlock().defaultBlockState(), 3)) {
            return false;
        }
        boolean loadedData = BlockItem.updateCustomBlockEntityTag(level, player, pos, stack);
        var restoredEntity = level.getBlockEntity(pos);
        if (!loadedData || restoredEntity == null) {
            level.setBlock(pos, current, 3);
            return false;
        }
        if (restoredEntity instanceof appeng.blockentity.AEBaseBlockEntity aeBlockEntity && stack.hasTag()) {
            aeBlockEntity.importSettings(SettingsFrom.DISMANTLE_ITEM, stack.getTag(), player);
        }
        restoredEntity.setChanged();
        if (!player.getAbilities().instabuild) pendingDismantleRecovery = storeDismantleRecovery(player, recoveredCasing);
        return true;
    }

    private void releasePendingServiceBlocks(ServerPlayer player) {
        for (var stack : pendingMigrationServiceBlocks) {
            returnPreservedServiceBlock(player, stack);
        }
        pendingMigrationServiceBlocks.clear();
    }

    private void returnPreservedServiceBlock(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        var remainder = insertIntoNetwork(stack);
        if (remainder.isEmpty()) {
            return;
        }
        var playerRemainder = remainder.copy();
        player.getInventory().add(playerRemainder);
        if (!playerRemainder.isEmpty()) {
            player.drop(playerRemainder, false);
        }
    }

    public void startDismantle(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || dismantling
                || updatingStructure || !player.mayBuild()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        dismantlePlan = captureActualDismantlePlan(facing);
        if (dismantlePlan == null || dismantlePlan.isComplete()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.nothing_to_dismantle"), false);
            return;
        }
        dismantling = true;
        building = false;
        buildCursor = 0;
        buildOwner = player.getUUID();
        structureFormed = false;
        resetProgress();
        updateOptionalLinks();
        setState(ProcessingState.DISMANTLING);
        saveChanges();
    }

    private void processDismantle() {
        if (updatingStructure) {
            processStructureUpdateDismantle();
            return;
        }
        if (level == null || buildOwner == null || level.getServer() == null) {
            return;
        }
        var player = level.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null || !player.mayBuild()) {
            return;
        }
        if (!pendingDismantleRecovery.isEmpty() && (dismantlePlan == null || dismantlePlan.isComplete())) {
            pendingDismantleRecovery = storeDismantleRecovery(player, pendingDismantleRecovery);
            if (!pendingDismantleRecovery.isEmpty()) {
                saveChanges();
                return;
            }
        }
        if (dismantlePlan == null) {
            // Old saves used a reverse cursor into a historical union; that cursor has no spatial meaning.
            dismantlePlan = captureActualDismantlePlan(getBlockState().getValue(HorizontalDirectionalBlock.FACING));
            buildCursor = 0;
            if (dismantlePlan == null) {
                return;
            }
        }
        if (!dismantlePlan.remainingChunksLoaded(level)) {
            return;
        }
        int budget = BUILD_BLOCKS_PER_TICK;
        while (!dismantlePlan.isComplete() && budget > 0) {
            if (!pendingDismantleRecovery.isEmpty()) {
                pendingDismantleRecovery = storeDismantleRecovery(player, pendingDismantleRecovery);
                if (!pendingDismantleRecovery.isEmpty()) {
                    saveChanges();
                    return;
                }
                dismantlePlan.advance();
                buildCursor = dismantlePlan.completed();
                continue;
            }
            var entry = dismantlePlan.current();
            var current = level.getBlockState(entry.pos());
            if (entry.pos().equals(worldPosition) || !entry.matches(current)) {
                dismantlePlan.advance();
                buildCursor = dismantlePlan.completed();
                continue;
            }
            if (!player.mayUseItemAt(entry.pos(), Direction.UP, ItemStack.EMPTY)) {
                saveChanges();
                return;
            }
            boolean service = isMigrationServiceBlock(current);
            var recovered = service ? captureMigrationServiceBlock(entry.pos(), current)
                    : new ItemStack(current.getBlock());
            boolean returnMaterial = service || !player.getAbilities().instabuild;
            if (returnMaterial && !canStoreDismantleRecovery(player, recovered)) {
                saveChanges();
                return;
            }
            if (!removeDismantledBlock(entry.pos(), service)) {
                saveChanges();
                return;
            }
            budget--;
            if (returnMaterial) {
                // Keep any unexpected post-removal storage remainder in NBT, never drop or lose it.
                pendingDismantleRecovery = storeDismantleRecovery(player, recovered);
                if (!pendingDismantleRecovery.isEmpty()) {
                    saveChanges();
                    return;
                }
            }
            dismantlePlan.advance();
            buildCursor = dismantlePlan.completed();
        }
        if (dismantlePlan.isComplete()) {
            stopDismantle();
            refreshStructure();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.dismantle_complete"), false);
        }
        saveChanges();
    }

    private DismantlePlan captureActualDismantlePlan(Direction facing) {
        var detected = MatterFabricationStructure.detectLayout(level, worldPosition, facing);
        var source = detected.isFormed() ? detected : lastKnownLayout;
        if (!source.isFormed()) {
            source = MatterFabricationStructure.inferDismantleLayout(level, worldPosition, facing);
        }
        if (source.isFormed()) {
            lastKnownLayout = source;
        }
        return MatterFabricationStructure.captureDismantlePlan(level, worldPosition, facing, source);
    }

    private boolean canStoreDismantleRecovery(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        long remaining = stack.getCount();
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(stack);
        if (grid != null && key != null) {
            remaining -= grid.getStorageService().getInventory().insert(
                    key, remaining, Actionable.SIMULATE, actionSource);
        }
        for (var slot : player.getInventory().items) {
            if (slot.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(slot, stack)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private ItemStack storeDismantleRecovery(ServerPlayer player, ItemStack stack) {
        var remainder = insertIntoNetwork(stack);
        if (!remainder.isEmpty()) {
            player.getInventory().add(remainder);
        }
        return remainder;
    }

    private boolean removeDismantledBlock(BlockPos pos, boolean preserveServiceData) {
        var blockEntity = level.getBlockEntity(pos);
        CompoundTag snapshot = preserveServiceData && blockEntity != null
                ? blockEntity.saveWithFullMetadata() : null;
        if (snapshot != null && blockEntity instanceof AEBaseBlockEntity aeBlockEntity) {
            // AEBaseEntityBlock.onRemove emits additional contents; they are already inside the saved item.
            aeBlockEntity.clearContent();
        }
        if (level.removeBlock(pos, false)) {
            return true;
        }
        if (snapshot != null) {
            blockEntity.load(snapshot);
            blockEntity.setChanged();
        }
        return false;
    }

    /** Upgrade cleanup retains its historical source list and preserved-service rebuild phase. */
    private void processStructureUpdateDismantle() {
        if (level == null || buildOwner == null || level.getServer() == null) {
            return;
        }
        var player = level.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null) {
            return;
        }
        if (!player.mayBuild()) {
            stopDismantle();
            return;
        }
        if (!pendingDismantleRecovery.isEmpty()) {
            pendingDismantleRecovery = storeDismantleRecovery(player, pendingDismantleRecovery);
            if (!pendingDismantleRecovery.isEmpty()) {
                saveChanges();
                return;
            }
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var parts = MatterFabricationStructure.parts(updateSourceLayout);
        int scanBudget = MIGRATION_SCAN_PER_TICK;
        int removalBudget = BUILD_BLOCKS_PER_TICK;
        while (scanBudget-- > 0 && removalBudget > 0 && buildCursor > 0) {
            var part = parts.get(--buildCursor);
            if (MatterFabricationStructure.isController(part)) {
                continue;
            }
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, part);
            var current = level.getBlockState(pos);
            if (isValidBuildTargetBlockAt(current, part)) {
                continue;
            }
            boolean matches = MatterFabricationStructure.isMigrationMatch(current, part, updateSourceLayout);
            if (!matches
                    || current.is(ModContent.MATTER_FABRICATION_CONTROLLER.get())) {
                continue;
            }
            if (!player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.molecularmanipulator.fabrication.build_conflict",
                        pos.getX(), pos.getY(), pos.getZ()), false);
                buildCursor++;
                stopDismantle();
                return;
            }
            boolean preserveServiceData = isMigrationServiceBlock(current);
            var recovered = new ItemStack(current.getBlock());
            var preservedService = preserveServiceData
                    ? captureMigrationServiceBlock(pos, current) : ItemStack.EMPTY;
            if (!preserveServiceData && !player.getAbilities().instabuild
                    && !canStoreDismantleRecovery(player, recovered)) {
                buildCursor++;
                stopDismantle();
                return;
            }
            if (!removeDismantledBlock(pos, preserveServiceData)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.molecularmanipulator.fabrication.build_conflict",
                        pos.getX(), pos.getY(), pos.getZ()), false);
                buildCursor++;
                stopDismantle();
                return;
            }
            if (preserveServiceData) {
                pendingMigrationServiceBlocks.add(preservedService);
            } else if (!player.getAbilities().instabuild) {
                pendingDismantleRecovery = storeDismantleRecovery(player, recovered);
                if (!pendingDismantleRecovery.isEmpty()) {
                    saveChanges();
                    return;
                }
            }
            removalBudget--;
        }
        if (buildCursor <= 0) {
            dismantling = false;
            building = true;
            updateBuildingPhase = true;
            buildCursor = 0;
            structureLayout = MatterFabricationStructure.StructureLayout.NONE;
            setState(ProcessingState.BUILDING);
        }
        saveChanges();
    }

    private boolean isValidBuildTargetBlockAt(BlockState state, MatterFabricationStructure.Part sourcePart) {
        if (buildTargetLayout == MatterFabricationStructure.StructureLayout.CURRENT) {
            return MatterFabricationStructure.isValidCurrentBlockAt(state, sourcePart);
        }
        for (var target : MatterFabricationStructure.parts(buildTargetLayout)) {
            if (target.x() == sourcePart.x() && target.y() == sourcePart.y() && target.z() == sourcePart.z()) {
                return MatterFabricationStructure.matchesLayoutPart(state, target, buildTargetLayout);
            }
        }
        return false;
    }

    private void stopDismantle() {
        if (updatingStructure) {
            dismantling = false;
            setState(ProcessingState.STRUCTURE_INCOMPLETE);
            scheduleStructureCheck();
            saveChanges();
            return;
        }
        dismantling = false;
        updateBuildingPhase = false;
        buildCursor = 0;
        buildOwner = null;
        scheduleStructureCheck();
        saveChanges();
    }

    private void storeRecoveredMaterial(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        var remainder = insertIntoNetwork(stack);
        if (!remainder.isEmpty()) {
            var playerRemainder = remainder.copy();
            player.getInventory().add(playerRemainder);
            if (!playerRemainder.isEmpty()) {
                player.drop(playerRemainder, false);
            }
        }
    }

    private boolean takeBuildMaterial(ServerPlayer player, ItemStack template) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (var stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) {
                stack.shrink(1);
                return true;
            }
        }
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(template);
        return grid != null && key != null
                && grid.getStorageService().getInventory().extract(
                        key, 1, Actionable.MODULATE, new PlayerSource(player)) == 1;
    }

    private void refundBuildMaterial(ServerPlayer player, ItemStack template) {
        if (player.getAbilities().instabuild || player.getInventory().add(template.copy())) {
            return;
        }
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(template);
        if (grid == null || key == null || grid.getStorageService().getInventory().insert(
                key, 1, Actionable.MODULATE, new PlayerSource(player)) != 1) {
            player.drop(template.copy(), false);
        }
    }

    private ItemStack insertIntoNetwork(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(stack);
        if (grid == null || key == null) {
            return stack.copy();
        }
        long inserted = grid.getStorageService().getInventory().insert(
                key, stack.getCount(), Actionable.MODULATE, actionSource);
        if (inserted <= 0) {
            return stack.copy();
        }
        var remainder = stack.copy();
        remainder.shrink((int) Math.min(inserted, remainder.getCount()));
        return remainder;
    }

    private void evacuateLegacyControllerInventory() {
        if (!building && !dismantling && !updatingStructure) {
            evacuatePortableRecovery();
        }
        if (inventory.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            var remainder = slot < INPUT_SLOTS
                    ? insertItemInput(stored)
                    : insertItemOutput(stored);
            remainder = insertIntoNetwork(remainder);
            if (remainder.getCount() != stored.getCount()) {
                inventory.setItemDirect(slot, remainder);
                changed = true;
            }
        }
        if (changed) {
            saveChanges();
        }
    }

    private void evacuatePortableRecovery() {
        boolean changed = false;
        if (!pendingDismantleRecovery.isEmpty()) {
            var remainder = insertIntoNetwork(insertItemOutput(pendingDismantleRecovery));
            changed = remainder.getCount() != pendingDismantleRecovery.getCount();
            pendingDismantleRecovery = remainder;
        }
        for (var iterator = pendingMigrationServiceBlocks.listIterator(); iterator.hasNext();) {
            var stored = iterator.next();
            var remainder = insertIntoNetwork(insertItemOutput(stored));
            if (remainder.getCount() != stored.getCount()) {
                changed = true;
                if (remainder.isEmpty()) iterator.remove();
                else iterator.set(remainder);
            }
        }
        if (changed) saveChanges();
    }

    private void processRecipe() {
        if (!structureFormed) {
            setState(ProcessingState.STRUCTURE_INCOMPLETE);
            updatePoweredState(false);
            return;
        }
        if (!getMainNode().isActive()) {
            setState(ProcessingState.NETWORK_OFFLINE);
            updatePoweredState(false);
            return;
        }

        var selection = findRecipe();
        if (selection == null) {
            resetProgress();
            setState(ProcessingState.NO_RECIPE);
            updatePoweredState(false);
            return;
        }

        var input = selection.input();
        var holder = selection.holder();
        var recipe = holder.value();
        if (!holder.id().equals(activeRecipeId)) {
            progress = 0;
            activeRecipeId = holder.id();
        }
        var profile = MatterResearchApi.productionProfile(this, holder);
        currentProcessingTime = profile.ticks();
        if (progress == 0) manualCrafts = manualBatchLimit(recipe, input, profile.parallel());
        if (manualCrafts <= 0) {
            setState(outputsFit(recipe, 1) ? ProcessingState.WAITING_POWER : ProcessingState.OUTPUT_BLOCKED);
            updatePoweredState(false); return;
        }
        if (recipe.consumptionPlan(input, manualCrafts) == null) { resetProgress(); return; }
        currentAePerTick = recipe.aePerTick() * manualCrafts;

        if (!outputsFit(recipe, manualCrafts)) {
            setState(ProcessingState.OUTPUT_BLOCKED);
            updatePoweredState(false);
            return;
        }
        if (!Double.isFinite(currentAePerTick) || !consumePower(currentAePerTick)) {
            setState(ProcessingState.WAITING_POWER);
            updatePoweredState(false);
            return;
        }

        progress++;
        setState(ProcessingState.RUNNING);
        updatePoweredState(true);
        if (progress >= currentProcessingTime) {
            completeRecipe(recipe, input);
        }
        if (progress % 5 == 0) markForUpdate();
        saveChanges();
    }

    private RecipeSelection findRecipe() {
        if (patternRecipeId != null && inputsAreEmpty()) patternRecipeId = null;
        for (var input : snapshotInputs()) {
            var match = level.getRecipeManager().getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()).stream()
                    .filter(holder -> patternRecipeId == null || holder.id().equals(patternRecipeId))
                    .filter(holder -> holder.value().matches(input, level) && MatterResearchApi.canUseRecipe(this, holder)).findFirst();
            if (match.isPresent()) {
                return new RecipeSelection(input, match.get());
            }
        }
        return null;
    }

    private List<MatterFabricationRecipeInput> snapshotInputs() {
        var stacks = new ArrayList<ItemStack>(MatterFabricationPortBlockEntity.ITEM_SLOTS);
        var itemPort = getPort(MatterFabricationStructure.ITEM_INPUT);
        if (itemPort != null) {
            for (int slot = 0; slot < itemPort.getInventory().getSlots(); slot++) {
                stacks.add(itemPort.getInventory().getStackInSlot(slot).copy());
            }
        }
        var result = new ArrayList<MatterFabricationRecipeInput>();
        result.add(new MatterFabricationRecipeInput(stacks, FluidStack.EMPTY));
        var fluidPort = getPort(MatterFabricationStructure.FLUID_INPUT);
        if (fluidPort == null) {
            return result;
        }
        var fluids = new ArrayList<FluidStack>();
        for (int tank = 0; tank < MatterFabricationPortBlockEntity.FLUID_TANKS; tank++) {
            var fluid = fluidPort.getTank(tank).getFluid();
            if (fluid.isEmpty()) {
                continue;
            }
            FluidStack aggregate = null;
            for (var existing : fluids) {
                if (existing.isFluidEqual(fluid)) {
                    aggregate = existing;
                    break;
                }
            }
            if (aggregate == null) {
                fluids.add(fluid.copy());
            } else {
                long total = (long) aggregate.getAmount() + fluid.getAmount();
                aggregate.setAmount((int) Math.min(Integer.MAX_VALUE, total));
            }
        }
        for (var fluid : fluids) {
            result.add(new MatterFabricationRecipeInput(stacks, fluid));
        }
        return result;
    }

    private boolean consumePower(double amount) {
        if (amount <= 0.0) {
            return true;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        var energy = grid.getEnergyService();
        double simulated = energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated < amount - 0.01) {
            return false;
        }
        return energy.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - 0.01;
    }

    private long manualBatchLimit(MatterFabricationRecipe recipe, MatterFabricationRecipeInput input, long limit) {
        long upper = limit;
        for (var ingredient : recipe.ingredients()) {
            long available = 0;
            for (int slot = 0; slot < input.size(); slot++) if (ingredient.ingredient().test(input.getItem(slot))) available += input.getItem(slot).getCount();
            upper = Math.min(upper, available / ingredient.count());
        }
        if (!recipe.fluidInput().isEmpty()) upper = Math.min(upper, input.fluid().getAmount() / recipe.fluidInput().getAmount());
        for (var output : recipe.results()) upper = Math.min(upper, Integer.MAX_VALUE / output.getCount());
        if (!recipe.fluidResult().isEmpty()) upper = Math.min(upper, Integer.MAX_VALUE / recipe.fluidResult().getAmount());
        upper = MatterFabricationBatch.powerCapacity(this, recipe.aePerTick(), upper);
        long low = 0, high = upper;
        while (low < high) {
            long middle = low + (high - low) / 2 + 1;
            if (recipe.consumptionPlan(input, middle) != null && outputsFit(recipe, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private boolean outputsFit(MatterFabricationRecipe recipe, long crafts) {
        var additions = new ArrayList<ItemStack>();
        for (var stack : recipe.results()) {
            if (crafts > Integer.MAX_VALUE / stack.getCount()) return false;
            additions.add(stack.copyWithCount((int) (stack.getCount() * crafts)));
        }
        if (!insertIntoCopies(outputSlotCopies(), additions)) return false;
        if (recipe.fluidResult().isEmpty()) return true;
        if (crafts > Integer.MAX_VALUE / recipe.fluidResult().getAmount()) return false;
        var fluid = recipe.fluidResult().copy(); fluid.setAmount((int) (fluid.getAmount() * crafts));
        var port = getPort(MatterFabricationStructure.FLUID_OUTPUT);
        return port != null && port.getInternalFluidHandler().fill(fluid, IFluidHandler.FluidAction.SIMULATE) == fluid.getAmount();
    }

    private void completeRecipe(MatterFabricationRecipe recipe, MatterFabricationRecipeInput input) {
        int[] consumption = recipe.consumptionPlan(input, manualCrafts);
        if (consumption == null || !outputsFit(recipe, manualCrafts)) {
            resetProgress();
            return;
        }
        progress = 0;
        activeRecipeId = null;
        patternRecipeId = null;
        var inputPort = getPort(MatterFabricationStructure.ITEM_INPUT);
        if (inputPort != null) {
            for (int slot = 0; slot < inputPort.getInventory().getSlots(); slot++) {
                if (slot < consumption.length && consumption[slot] > 0) {
                    inputPort.getInventory().extractItem(slot, consumption[slot], false);
                }
            }
        }
        if (!recipe.fluidInput().isEmpty()) {
            var fluidInput = getPort(MatterFabricationStructure.FLUID_INPUT);
            if (fluidInput != null) {
                fluidInput.getInternalFluidHandler().drain(
                        com.atir.molecularmanipulator.util.ForgeFluids.copyWithAmount(recipe.fluidInput(), (int) (recipe.fluidInput().getAmount() * manualCrafts)), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        for (var result : recipe.resultCopies()) {
            result.setCount((int) (result.getCount() * manualCrafts));
            var remainder = insertItemOutput(result);
            if (!remainder.isEmpty()) {
                // Output space was simulated immediately before insertion, so this is
                // only a defensive fallback for concurrent capability access.
                net.minecraft.world.Containers.dropItemStack(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5, remainder);
            }
        }
        if (!recipe.fluidResult().isEmpty()) {
            var fluidOutput = getPort(MatterFabricationStructure.FLUID_OUTPUT);
            if (fluidOutput != null) {
                fluidOutput.getInternalFluidHandler().fill(
                        com.atir.molecularmanipulator.util.ForgeFluids.copyWithAmount(recipe.fluidResult(), (int) (recipe.fluidResult().getAmount() * manualCrafts)), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        completionTick = level.getGameTime();
        markForUpdate();
    }

    private boolean canAdmitBatch() {
        return level != null && !level.isClientSide() && structureFormed && getMainNode().isActive()
                && !building && !dismantling && !updatingStructure && inputsAreEmpty()
                && (batch.hasWork() || progress == 0);
    }

    boolean hasBatchWork() { return batch.hasWork(); }

    long batchCapacity(IPatternDetails pattern, Map<appeng.api.stacks.AEKey, Long> oneCraft) {
        if (!canAdmitBatch()) return 0;
        try {
            var holder = MatterFabricationBatch.match(this, pattern, oneCraft, 1);
            return holder == null ? 0 : batch.capacity(this, holder, oneCraft);
        } catch (ArithmeticException error) { return 0; }
    }

    boolean acceptBatch(IPatternDetails pattern, Map<appeng.api.stacks.AEKey, Long> input, long count,
            Map<appeng.api.stacks.AEKey, Long> expectedOutputs) {
        return canAdmitBatch() && batch.accept(this, pattern, input, count, expectedOutputs);
    }

    boolean acceptPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        var supplied = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var counter : inputs) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return false;
                supplied.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        } catch (ArithmeticException error) { return false; }
        if (!acceptBatch(patternDetails, supplied, 1, null)) return false;
        for (var counter : inputs) counter.clear();
        return true;
    }

    private void processBatch() {
        var result = batch.tick(this);
        progress = result.progress(); currentProcessingTime = result.duration(); currentAePerTick = result.power();
        setState(result.state()); updatePoweredState(result.state() == ProcessingState.RUNNING);
        if (result.completed()) { completionTick = level.getGameTime(); markForUpdate(); }
        else if (progress % 5 == 0) markForUpdate();
    }

    private boolean processPatternBuffers() {
        if (!structureFormed || !getMainNode().isActive()) { processingAssembly = null; return false; }
        // Do not interrupt a manual recipe that is already consuming processing time.
        if (processingAssembly == null && activeRecipeId != null && progress > 0) return false;
        var assemblies = new ArrayList<MatterFabricationPatternAssemblyBlockEntity>();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (var bay : MatterFabricationStructure.patternAssemblyBays()) {
            if (level.getBlockEntity(MatterFabricationStructure.worldPos(worldPosition, facing, bay)) instanceof MatterFabricationPatternAssemblyBlockEntity assembly
                    && assembly.getController() == this) assemblies.add(assembly);
        }
        if (assemblies.isEmpty()) { processingAssembly = null; return false; }
        int start = Math.floorMod(nextAssembly, assemblies.size());
        for (int i = 0; i < assemblies.size(); i++) if (assemblies.get(i).getBlockPos().equals(processingAssembly)) { start = i; break; }
        MatterFabricationBatch.Update waiting = null;
        for (int offset = 0; offset < assemblies.size(); offset++) {
            int index = (start + offset) % assemblies.size(); var assembly = assemblies.get(index);
            var update = assembly.getBuffer().process(this);
            if (update == null) continue;
            if (update.state() != ProcessingState.RUNNING && !update.completed()) { if (waiting == null) waiting = update; continue; }
            processingAssembly = update.completed() ? null : assembly.getBlockPos();
            nextAssembly = update.completed() ? index + 1 : index;
            activeRecipeId = null;
            progress = update.progress(); currentProcessingTime = update.duration(); currentAePerTick = update.power();
            setState(update.state()); updatePoweredState(update.state() == ProcessingState.RUNNING);
            if (update.completed()) { completionTick = level.getGameTime(); markForUpdate(); }
            else if (progress % 5 == 0) markForUpdate();
            return true;
        }
        processingAssembly = null;
        if (waiting != null) {
            activeRecipeId = null; progress = waiting.progress(); currentProcessingTime = waiting.duration(); currentAePerTick = waiting.power();
            setState(waiting.state()); updatePoweredState(false); return true;
        }
        return false;
    }

    long offerBatchOutputToPort(appeng.api.stacks.AEKey key, long amount) {
        if (key instanceof AEItemKey item) {
            int requested = (int) Math.min(amount, MatterFabricationPortBlockEntity.ITEM_SLOTS * 64L);
            return requested - insertItemOutput(item.toStack(requested)).getCount();
        }
        if (key instanceof AEFluidKey fluid) {
            var port = getPort(MatterFabricationStructure.FLUID_OUTPUT);
            return port == null ? 0 : port.getInternalFluidHandler().fill(fluid.toStack((int) Math.min(amount, Integer.MAX_VALUE)), IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private boolean inputsAreEmpty() {
        var itemPort = getPort(MatterFabricationStructure.ITEM_INPUT);
        if (itemPort != null) {
            for (int slot = 0; slot < itemPort.getInventory().getSlots(); slot++) {
                if (!itemPort.getInventory().getStackInSlot(slot).isEmpty()) {
                    return false;
                }
            }
        }
        var fluidPort = getPort(MatterFabricationStructure.FLUID_INPUT);
        if (fluidPort != null) {
            for (int tank = 0; tank < MatterFabricationPortBlockEntity.FLUID_TANKS; tank++) {
                if (!fluidPort.getTank(tank).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private ArrayList<ItemStack> outputSlotCopies() {
        var copies = new ArrayList<ItemStack>();
        var port = getPort(MatterFabricationStructure.ITEM_OUTPUT);
        if (port != null) {
            for (int slot = 0; slot < port.getInventory().getSlots(); slot++) {
                copies.add(port.getInventory().getStackInSlot(slot).copy());
            }
        }
        return copies;
    }

    private static boolean insertIntoCopies(List<ItemStack> slots, List<ItemStack> additions) {
        for (var addition : additions) {
            int remaining = addition.getCount();
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                var existing = slots.get(slot);
                if (existing.isEmpty()) {
                    int inserted = Math.min(remaining, addition.getMaxStackSize());
                    slots.set(slot, addition.copyWithCount(inserted));
                    remaining -= inserted;
                } else if (ItemStack.isSameItemSameTags(existing, addition)) {
                    int inserted = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                    existing.grow(inserted);
                    remaining -= inserted;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private ItemStack insertItemInput(ItemStack stack) {
        var port = getPort(MatterFabricationStructure.ITEM_INPUT);
        if (port == null || stack.isEmpty()) {
            return stack.copy();
        }
        var remainder = stack.copy();
        for (int slot = 0; slot < port.getInventory().getSlots() && !remainder.isEmpty(); slot++) {
            remainder = port.getInventory().insertItem(slot, remainder, false);
        }
        return remainder;
    }

    private ItemStack insertItemOutput(ItemStack stack) {
        var port = getPort(MatterFabricationStructure.ITEM_OUTPUT);
        if (port == null) {
            return stack.copy();
        }
        var remainder = stack.copy();
        for (int slot = 0; slot < port.getInventory().getSlots() && !remainder.isEmpty(); slot++) {
            remainder = port.getInventory().insertItem(slot, remainder, false);
        }
        return remainder;
    }

    private MatterFabricationPortBlockEntity getPort(MatterFabricationStructure.OptionalPart part) {
        if (level == null) {
            return null;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (var host : MatterFabricationStructure.patternAssemblyBays()) {
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, host);
            if (!level.getBlockState(pos).is(part.block())) {
                continue;
            }
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MatterFabricationPortBlockEntity port
                    && port.isLinkedTo(worldPosition)) {
                return port;
            }
        }
        return null;
    }

    private void updateOptionalLinks() {
        if (level == null) {
            return;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (var part : MatterFabricationStructure.patternAssemblyBays()) {
            var blockEntity = level.getBlockEntity(
                    MatterFabricationStructure.worldPos(worldPosition, facing, part));
            if (blockEntity instanceof MatterFabricationPortBlockEntity port) {
                port.setControllerPos(structureFormed ? worldPosition : null);
            } else if (blockEntity instanceof MatterFabricationPatternAssemblyBlockEntity assembly) {
                assembly.setControllerPos(structureFormed ? worldPosition : null);
            }
        }
    }

    private void resetProgress() {
        progress = 0;
        activeRecipeId = null;
        currentProcessingTime = 0;
        currentAePerTick = 0.0;
    }

    private void setState(ProcessingState next) {
        if (processingState != next) {
            processingState = next;
            markForUpdate();
        }
    }

    private void updatePoweredState(boolean powered) {
        var state = getBlockState();
        if (state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.POWERED, powered), 3);
            markForUpdate();
        }
    }

    public boolean isStructureFormed() {
        return structureFormed;
    }

    public boolean hasLegacyStructure() {
        return structureLayout.requiresUpdate() || updatingStructure;
    }

    public MatterFabricationStructure.StructureLayout getStructureLayout() {
        return structureLayout;
    }

    public boolean isUpdatingStructure() {
        return updatingStructure;
    }

    public boolean isNetworkOnline() {
        return structureFormed && getMainNode().isActive();
    }

    public boolean isClientStructureFormed() {
        return clientStructureFormed;
    }

    public boolean isClientRunning() {
        return clientRunning;
    }

    public ResearchVisualState getClientResearchVisualState() {
        return clientResearchVisualState;
    }

    public double sampleClientResearchElapsed(float partialTick) {
        return level == null ? 0 : com.atir.molecularmanipulator.util.MathCompat.clamp(level.getGameTime() + partialTick - clientResearchSample, 0, 5);
    }

    public float sampleClientResearchCompletionPulse(float partialTick) {
        if (Double.isNaN(clientResearchPulseStarted) || level == null) return 0;
        return (float) com.atir.molecularmanipulator.util.MathCompat.clamp(1 - (level.getGameTime() + partialTick - clientResearchPulseStarted) / 32.0, 0, 1);
    }

    public float sampleClientCompletionPulse(float partialTick) {
        if (Double.isNaN(clientPulseStarted)) return 0;
        double sample = level == null ? clientProgressSample : level.getGameTime() + partialTick;
        return (float)com.atir.molecularmanipulator.util.MathCompat.clamp(1 - (sample - clientPulseStarted) / 20.0, 0, 1);
    }

    public float sampleClientRecipeProgress(float partialTick) {
        if (clientRecipeDuration <= 0) return 0;
        double elapsed = clientRunning && level != null
                ? com.atir.molecularmanipulator.util.MathCompat.clamp(level.getGameTime() + partialTick - clientProgressSample, 0, 5) : 0;
        return (float)com.atir.molecularmanipulator.util.MathCompat.clamp((clientRecipeProgress + elapsed) / clientRecipeDuration, 0, 1);
    }

    public MatterFabricationStructure.Inspection getInspection() {
        return inspection;
    }

    public int getProgress() {
        return progress;
    }

    public int getCurrentProcessingTime() {
        return currentProcessingTime;
    }

    public double getCurrentAePerTick() {
        return currentAePerTick;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    public boolean isBuilding() {
        return building;
    }

    public boolean isDismantling() {
        return dismantling;
    }

    public int getBuildCursor() {
        return buildCursor;
    }

    public int getOperationProgress() {
        if (updatingStructure) {
            int total = updateBuildingPhase
                    ? MatterFabricationStructure.parts(buildTargetLayout).size()
                    : MatterFabricationStructure.parts(updateSourceLayout).size();
            return updateBuildingPhase ? buildCursor : Math.max(0, total - buildCursor);
        }
        if (!dismantling) {
            return buildCursor;
        }
        return dismantlePlan == null ? 0 : dismantlePlan.completed();
    }

    public int getOperationTotal() {
        if (updatingStructure) {
            return updateBuildingPhase
                    ? MatterFabricationStructure.parts(buildTargetLayout).size()
                    : MatterFabricationStructure.parts(updateSourceLayout).size();
        }
        if (!dismantling) {
            return MatterFabricationStructure.parts(building ? buildTargetLayout
                    : MatterFabricationStructure.StructureLayout.CURRENT).size();
        }
        return dismantlePlan == null ? 0 : dismantlePlan.total();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        quantumInventory.writeToNBT(tag, QUANTUM_INVENTORY_TAG);
        if (research.hasProgress()) tag.put(RESEARCH_TAG, research.save());
        else tag.remove(RESEARCH_TAG);
        if (batch.hasWork()) tag.put(BATCH_TAG, batch.save()); else tag.remove(BATCH_TAG);
        tag.putLong("fabrication_manual_crafts", manualCrafts);
        tag.putInt(PROGRESS_TAG, progress);
        if (activeRecipeId != null) {
            tag.putString(ACTIVE_RECIPE_TAG, activeRecipeId.toString());
        }
        if (patternRecipeId != null) tag.putString(PATTERN_RECIPE_TAG, patternRecipeId.toString());
        else tag.remove(PATTERN_RECIPE_TAG);
        tag.putBoolean(BUILDING_TAG, building);
        tag.putBoolean(DISMANTLING_TAG, dismantling);
        tag.putBoolean(STRUCTURE_UPDATE_TAG, updatingStructure);
        tag.putBoolean(UPDATE_BUILDING_PHASE_TAG, updateBuildingPhase);
        tag.putString(UPDATE_SOURCE_LAYOUT_TAG, updateSourceLayout.name());
        tag.putString(KNOWN_LAYOUT_TAG, lastKnownLayout.name());
        tag.putInt(BLUEPRINT_VERSION_TAG, 4);
        tag.putString(BUILD_TARGET_LAYOUT_TAG, buildTargetLayout.name());
        if (dismantlePlan != null) {
            tag.put(DISMANTLE_PLAN_TAG, dismantlePlan.save());
        }
        if (!pendingDismantleRecovery.isEmpty()) {
            tag.put(DISMANTLE_RECOVERY_TAG, pendingDismantleRecovery.save(new CompoundTag()));
        } else tag.remove(DISMANTLE_RECOVERY_TAG);
        tag.putInt(BUILD_CURSOR_TAG, buildCursor);
        if (buildOwner != null) {
            tag.putUUID(BUILD_OWNER_TAG, buildOwner);
        }
        if (!pendingMigrationServiceBlocks.isEmpty()) {
            var list = new ListTag();
            for (var stack : pendingMigrationServiceBlocks) {
                if (!stack.isEmpty()) {
                    list.add(stack.save(new CompoundTag()));
                }
            }
            tag.put(PENDING_SERVICE_BLOCKS_TAG, list);
        } else tag.remove(PENDING_SERVICE_BLOCKS_TAG);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag);
        quantumInventory.readFromNBT(tag, QUANTUM_INVENTORY_TAG);
        research.load(tag.getCompound(RESEARCH_TAG));
        batch.load(tag.getCompound(BATCH_TAG));
        manualCrafts = Math.max(1, tag.getLong("fabrication_manual_crafts"));
        progress = Math.max(0, tag.getInt(PROGRESS_TAG));
        activeRecipeId = ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE_TAG));
        patternRecipeId = ResourceLocation.tryParse(tag.getString(PATTERN_RECIPE_TAG));
        building = tag.getBoolean(BUILDING_TAG);
        dismantling = tag.getBoolean(DISMANTLING_TAG);
        updatingStructure = tag.getBoolean(STRUCTURE_UPDATE_TAG);
        updateBuildingPhase = tag.getBoolean(UPDATE_BUILDING_PHASE_TAG);
        try {
            updateSourceLayout = MatterFabricationStructure.StructureLayout.valueOf(
                    tag.getString(UPDATE_SOURCE_LAYOUT_TAG));
        } catch (IllegalArgumentException exception) {
            updateSourceLayout = MatterFabricationStructure.StructureLayout.NONE;
        }
        updateSourceLayout = remapSavedCurrentLayout(updateSourceLayout, tag.getInt(BLUEPRINT_VERSION_TAG));
        if (!updateSourceLayout.requiresUpdate()) {
            updatingStructure = false;
            updateBuildingPhase = false;
        }
        if (building && dismantling) {
            dismantling = false;
        }
        buildTargetLayout = tag.contains(BUILD_TARGET_LAYOUT_TAG)
                ? MatterFabricationStructure.StructureLayout.fromSavedName(tag.getString(BUILD_TARGET_LAYOUT_TAG))
                : MatterFabricationStructure.StructureLayout.CURRENT;
        buildTargetLayout = remapSavedCurrentLayout(buildTargetLayout, tag.getInt(BLUEPRINT_VERSION_TAG));
        if (!buildTargetLayout.isFormed()) { building = false; buildCursor = 0; }
        try {
            lastKnownLayout = MatterFabricationStructure.StructureLayout.valueOf(tag.getString(KNOWN_LAYOUT_TAG));
        } catch (IllegalArgumentException exception) {
            lastKnownLayout = updatingStructure ? updateSourceLayout : building
                    ? buildTargetLayout
                    : MatterFabricationStructure.StructureLayout.NONE;
        }
        lastKnownLayout = remapSavedCurrentLayout(lastKnownLayout, tag.getInt(BLUEPRINT_VERSION_TAG));
        dismantlePlan = !updatingStructure && tag.contains(DISMANTLE_PLAN_TAG, Tag.TAG_COMPOUND)
                ? DismantlePlan.load(tag.getCompound(DISMANTLE_PLAN_TAG)) : null;
        pendingDismantleRecovery = tag.contains(DISMANTLE_RECOVERY_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(DISMANTLE_RECOVERY_TAG)) : ItemStack.EMPTY;
        int operationSize = updatingStructure
                ? updateBuildingPhase ? MatterFabricationStructure.parts(buildTargetLayout).size()
                        : MatterFabricationStructure.parts(updateSourceLayout).size()
                : MatterFabricationStructure.parts(buildTargetLayout).size();
        buildCursor = dismantling && !updatingStructure
                ? dismantlePlan == null ? 0 : dismantlePlan.completed()
                : Math.max(0, Math.min(operationSize, tag.getInt(BUILD_CURSOR_TAG)));
        buildOwner = tag.hasUUID(BUILD_OWNER_TAG) ? tag.getUUID(BUILD_OWNER_TAG) : null;
        pendingMigrationServiceBlocks.clear();
        for (var entry : tag.getList(PENDING_SERVICE_BLOCKS_TAG, Tag.TAG_COMPOUND)) {
            var stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) {
                pendingMigrationServiceBlocks.add(stack);
            }
        }
    }

    private static MatterFabricationStructure.StructureLayout remapSavedCurrentLayout(
            MatterFabricationStructure.StructureLayout saved, int version) {
        return saved == MatterFabricationStructure.StructureLayout.CURRENT && version < 4
                ? MatterFabricationStructure.StructureLayout.NONE : saved;
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(structureFormed);
        data.writeBoolean(processingState == ProcessingState.RUNNING);
        data.writeVarInt(Math.max(0, progress));
        data.writeVarInt(Math.max(0, currentProcessingTime));
        data.writeLong(completionTick);
        researchVisualSnapshot().write(data);
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean nextFormed = data.readBoolean();
        boolean nextRunning = data.readBoolean();
        int nextProgress = Math.max(0, data.readVarInt());
        int nextDuration = Math.max(0, data.readVarInt());
        long nextCompletion = data.readLong();
        var nextResearch = ResearchVisualState.read(data);
        double now = level == null ? 0 : level.getGameTime();
        if (clientVisualInitialized && nextFormed
                && nextResearch.completionSerial() > clientResearchVisualState.completionSerial()
                && (Double.isNaN(clientResearchPulseStarted) || now - clientResearchPulseStarted >= 32)) {
            clientResearchPulseStarted = now;
        }
        changed |= !nextResearch.equals(clientResearchVisualState);
        if (!nextResearch.equals(clientResearchVisualState)) clientResearchSample = now;
        clientResearchVisualState = nextResearch;
        if (clientVisualInitialized && nextFormed && nextCompletion > clientCompletionTick
                && (Double.isNaN(clientPulseStarted) || now - clientPulseStarted >= 20)) clientPulseStarted = now;
        changed |= clientRecipeProgress != nextProgress || clientRecipeDuration != nextDuration
                || clientCompletionTick != nextCompletion;
        clientRecipeProgress = nextProgress;
        clientRecipeDuration = nextDuration;
        clientProgressSample = now;
        clientCompletionTick = nextCompletion;
        clientVisualInitialized = true;
        changed |= clientStructureFormed != nextFormed || clientRunning != nextRunning;
        clientStructureFormed = nextFormed;
        clientRunning = nextRunning;
        return changed;
    }

    private record RecipeSelection(MatterFabricationRecipeInput input,
            MatterFabricationRecipe holder) {
    }

    public enum ProcessingState {
        IDLE,
        STRUCTURE_INCOMPLETE,
        NETWORK_OFFLINE,
        NO_RECIPE,
        OUTPUT_BLOCKED,
        WAITING_POWER,
        BUILDING,
        DISMANTLING,
        RUNNING
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        var machine = this;
        net.minecraft.core.Direction facing = machine.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        net.minecraft.core.BlockPos center = MatterFabricationStructure.worldPos(machine.getBlockPos(), facing,
                new MatterFabricationStructure.Part(0, MatterFabricationStructure.EFFECT_CENTER_Y, 0, MatterFabricationStructure.PartType.CORE));
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.5D;
        double z = center.getZ() + 0.5D;
        double radius = MatterPearlGeometry.RADIUS + 2.0D;
        return new net.minecraft.world.phys.AABB(x - radius, y + MatterPearlGeometry.MIN_Y - MatterPearlGeometry.CENTER_Y - 1.0D, z - radius,
                x + radius, y + MatterPearlGeometry.MAX_Y - MatterPearlGeometry.CENTER_Y + 1.0D, z + radius);
    }

}
