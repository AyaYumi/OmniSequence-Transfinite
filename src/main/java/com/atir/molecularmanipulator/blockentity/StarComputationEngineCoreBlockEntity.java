package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.client.render.StarComputationEngineModelData;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryFoundation;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import com.atir.molecularmanipulator.starcomputation.StarComputationNetwork;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipes;
import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import com.atir.molecularmanipulator.block.EntropyAnchorBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side 3x3x3 structure owner for the Star Computation Engine.
 *
 * <p>The formed flag switches the core to its oversized baked model and hides
 * the 26 shell members beneath it. Removing any part restores the loose models.</p>
 */
public final class StarComputationEngineCoreBlockEntity extends AENetworkedBlockEntity {
    private static final int CHECK_INTERVAL = 10;
    private static final int ARMILLARY_LAYOUT_VERSION = 2;
    private static final int[] ARMILLARY_NODE_LEVELS = {1, 3, 6, 7, 9};

    private boolean formed;
    private boolean needsStateSync = true;
    private int tickCounter;
    private boolean overclocked;
    private long paidTick = Long.MIN_VALUE;
    private long pendingInformationRefund;
    private UUID boundTeam;
    private int legacyUnlockMask;
    private final Map<Direction, long[]> anchorCounts = new java.util.EnumMap<>(Direction.class);
    private record AnchorMember(Direction facing, int index, boolean armillary, int slot) {
        private AnchorMember(Direction facing, int index) {
            this(facing, index, false, -1);
        }
    }

    private final Map<BlockPos, AnchorMember> managedAnchors = new HashMap<>();
    /** Server-validated visual snapshot sent through AE2's normal BE update stream. */
    private int visualTier;
    private int visualConnections;
    /** Number of complete outer-prefix rings, zero-based ring count 0..9. */
    private int litRings;
    /** Saved rings that still need runtime revalidation after a reload. */
    private int pendingLitRings;
    private boolean armillaryValidated = true;
    @Nullable private EntropyArmillaryFoundation.Scan foundationScan;
    private boolean foundationRecovery;
    private boolean foundationVisualsDirty;
    private int syncedLitRings;
    /** Runtime-only visual load: idle, selected online engine, or paid overclock. */
    private int visualActivity;

    public StarComputationEngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.STAR_COMPUTATION_ENGINE_CORE_BE.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(16);
    }

    @Override
    protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) { return EnumSet.allOf(Direction.class); }

    public void bindOwner(ServerPlayer player) {
        if (boundTeam == null) {
            boundTeam = FtbTeamOwnership.forPlayer(player);
            if (boundTeam == null) boundTeam = player.getUUID();
            saveChanges();
        }
    }

    @Nullable public UUID getBoundTeam() { return boundTeam; }

    public boolean isArmillary() {
        var state = getBlockState();
        return state.hasProperty(StarComputationEngineCoreBlock.ARMILLARY)
                && state.getValue(StarComputationEngineCoreBlock.ARMILLARY);
    }

    public int getLitRings() {
        return isArmillary() && isFormed() && armillaryValidated
                ? Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS) : 0;
    }

    public int getVisualTier() {
        return visualTier;
    }

    public int getVisualActivity() {
        return getLitRings() > 0 ? visualActivity : 0;
    }

    /**
     * Research budget for a node. Legacy nodes are untouched; the opt-in armillary
     * reuses the shared ring thresholds (L1/L3/L6/L7/L9).
     */
    public int getResearchEntropyThreshold(int node) {
        if (node < 0 || node >= StarComputationRecipes.NODES.size()) {
            return 0;
        }
        if (isArmillary()) {
            return EntropyArmillaryLayout.threshold(ARMILLARY_NODE_LEVELS[node]);
        }
        return (int) Math.min(Integer.MAX_VALUE, StarComputationRecipes.NODES.get(node).entropy());
    }

    public long getEffectiveEntropy() {
        if (isArmillary()) {
            // Pending reload restoration is not a benefit until this server has
            // verified the rings again; the fixed layout defines the entropy.
            if (!isFormed() || !armillaryValidated) {
                return 0L;
            }
            return EntropyArmillaryLayout.entropyForRings(Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS));
        }
        long[] counts = countAnchors();
        return isFormed() ? StarComputationBenefits.entropy(counts[0], counts[1], counts[2]) : 0;
    }

    public int getAnchorCount() {
        if (isArmillary()) {
            if (!isFormed() || !armillaryValidated) {
                return 0;
            }
            int total = 0;
            for (int ring = 0; ring < Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS); ring++) {
                total += EntropyArmillaryRings.slotCount(ring);
            }
            return total;
        }
        long[] counts = countAnchors();
        return isFormed() ? (int) Math.min(Integer.MAX_VALUE, counts[0] + counts[1] + counts[2]) : 0;
    }

    /** Formed anchor count of one tier: 0 = base, 1 = quantum, 2 = terminal. */
    public int getAnchorCount(int tier) {
        if (tier < 0 || tier > 2) return 0;
        if (isArmillary()) {
            // Saved or unloaded rings are not anchors until this server has verified them again.
            if (!isFormed() || !armillaryValidated) return 0;
            return armillaryTierCount(litRings, tier);
        }
        return isFormed() ? (int) Math.min(Integer.MAX_VALUE, countAnchors()[tier]) : 0;
    }

    /** Slots of one ring tier inside the verified outer prefix; never derived from a level. */
    static int armillaryTierCount(int litRings, int tier) {
        if (tier < 0 || tier > 2) return 0;
        int total = 0;
        for (int ring = 0; ring < Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS); ring++) {
            if (EntropyArmillaryLayout.tierOf(ring) == tier) {
                total += EntropyArmillaryLayout.slotCount(ring);
            }
        }
        return total;
    }

    private long[] countAnchors() {
        long[] total = new long[3];
        anchorCounts.values().forEach(count -> { for (int i = 0; i < 3; i++) total[i] += count[i]; });
        return total;
    }

    public StarComputationBenefits getBenefits() {
        if (isArmillary()) {
            int level = isFormed() && armillaryValidated
                    ? Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS) : 0;
            return new StarComputationBenefits(level, overclocked);
        }
        return new StarComputationBenefits(StarComputationBenefits.levelFor(getEffectiveEntropy()), overclocked);
    }

    /** Bounded footprint of the core and the actual slots of the verified rings. */
    public Set<ChunkPos> getChunkLoadingChunks() {
        if (!isArmillary() || !isFormed() && !foundationRecovery) {
            return Set.of();
        }
        int rings = Math.min(EntropyArmillaryRings.RINGS, 1 + Math.max(litRings, pendingLitRings));
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return EntropyArmillaryRings.chunksFor(worldPosition, facing, rings);
    }

    public boolean isArmillaryMember(BlockPos pos) {
        return managedAnchors.containsKey(pos);
    }

    /** True when this controller currently considers the whole ring part of its footprint. */
    public boolean mayOwnArmillarySlot(int ring, int slot) {
        if (!isArmillary()) {
            return false;
        }
        int rings = Math.max(Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS),
                Math.clamp(pendingLitRings, 0, EntropyArmillaryRings.RINGS));
        return ring >= 0 && ring < rings && slot >= 0 && slot < EntropyArmillaryRings.slotCount(ring);
    }

    /** Physical engine members, including installed anchor assemblies and the armillary foundation. */
    public Set<BlockPos> foldingMembers() {
        var members = new java.util.LinkedHashSet<BlockPos>();
        if (level == null) return Set.of();
        for (var pos : managedAnchors.keySet())
            if (level.getBlockState(pos).getBlock() instanceof EntropyAnchorBlock) members.add(pos);
        members.add(worldPosition);
        if (!isArmillary()) {
            for (var pos : BlockPos.betweenClosed(worldPosition.offset(-1, -1, -1), worldPosition.offset(1, 1, 1))) {
                var state = level.getBlockState(pos);
                if (state.is(ModContent.STAR_COMPUTATION_ENGINE_CASING.get()) || state.is(ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get()))
                    members.add(pos.immutable());
            }
        } else {
            var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            for (var member : EntropyArmillaryFoundation.members()) {
                var pos = EntropyArmillaryFoundation.position(worldPosition, facing, member);
                var expected = member.material() == EntropyArmillaryFoundation.Material.PLINTH
                        ? ModContent.ENTROPY_ARMILLARY_PLINTH.get() : ModContent.ENTROPY_ARMILLARY_TRIM.get();
                if (level.getBlockState(pos).is(expected)) members.add(pos);
            }
        }
        return Set.copyOf(members);
    }

    public boolean isOverclocked() { return overclocked; }

    @Override
    public ModelData getModelData() {
        var inherited = super.getModelData();
        return inherited.derive()
                .with(StarComputationEngineModelData.PROPERTY,
                        new StarComputationEngineModelData.Snapshot(visualTier, visualConnections))
                .build();
    }

    public long getInfoConsumption() {
        return getMainNode().isActive() && StarComputationNetwork.best(getMainNode().getGrid()) == this
                ? getBenefits().infoPerTick() : 0;
    }

    public long getInfoBalance() {
        var grid = getMainNode().getGrid();
        return grid == null ? 0 : grid.getStorageService().getInventory()
                .extract(StarSignalKey.INSTANCE, Long.MAX_VALUE, Actionable.SIMULATE, new MachineSource(this));
    }

    public void toggleOverclock() {
        if (level == null || level.isClientSide()) return;
        if (overclocked) overclocked = false;
        else if (isFormed() && getMainNode().isActive() && getBenefits().level() > 0
                && getInfoBalance() >= new StarComputationBenefits(getBenefits().level(), true).infoPerTick()) overclocked = true;
        saveChanges();
        if (StarComputationNetwork.best(getMainNode().getGrid()) == this) payOverclockForCurrentTick();
    }

    /** Called both by consumers and the ticker, so tick order cannot grant unpaid overclock. */
    public void payOverclockForCurrentTick() {
        if (level == null || level.isClientSide() || !overclocked || paidTick == level.getGameTime()) return;
        if (!isFormed() || !getMainNode().isActive() || getBenefits().level() == 0
                || !extractInformation(getBenefits().infoPerTick(), new MachineSource(this))) {
            overclocked = false;
            saveChanges();
            return;
        }
        paidTick = level.getGameTime();
    }

    private boolean extractInformation(long amount, appeng.api.networking.security.IActionSource source) {
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var storage = grid.getStorageService().getInventory();
        if (storage.extract(StarSignalKey.INSTANCE, amount, Actionable.SIMULATE, source) < amount) return false;
        long extracted = storage.extract(StarSignalKey.INSTANCE, amount, Actionable.MODULATE, source);
        if (extracted == amount) return true;
        // A nonconforming storage may return a partial extraction after simulation. Retain its refund debt.
        pendingInformationRefund += extracted;
        refundInformation();
        saveChanges();
        return false;
    }

    private void refundInformation() {
        var grid = getMainNode().getGrid();
        if (pendingInformationRefund > 0 && grid != null) {
            pendingInformationRefund -= grid.getStorageService().getInventory().insert(StarSignalKey.INSTANCE,
                    pendingInformationRefund, Actionable.MODULATE, new MachineSource(this));
            saveChanges();
        }
    }

    public int getUnlockMask() {
        if (!(level instanceof ServerLevel server)) return legacyUnlockMask;
        var data = StarComputationResearchData.get(server);
        data.mergeUnlockMask(boundTeam, legacyUnlockMask);
        return data.getUnlockMask(boundTeam) | legacyUnlockMask;
    }

    public boolean unlockRecipe(int index, ServerPlayer player) {
        if (level == null || level.isClientSide() || player.level() != level || !player.mayBuild() || player.isSpectator()
                || player.containerMenu == null || !(player.containerMenu instanceof StarComputationEngineMenu)
                || !isFormed() || !getMainNode().isActive() || !canInteract(player)) return false;
        if (index < 0 || index >= StarComputationRecipes.NODES.size()) return false;
        bindOwner(player);
        var node = StarComputationRecipes.NODES.get(index);
        int bit = 1 << index;
        int mask = getUnlockMask();
        if ((mask & bit) != 0 || getEffectiveEntropy() < getResearchEntropyThreshold(index)
                || (mask & node.prerequisiteMask()) != node.prerequisiteMask()) return false;
        if (!extractInformation(node.informationCost(), new PlayerSource(player))) return false;
        StarComputationResearchData.get((ServerLevel) level).addUnlock(boundTeam, index);
        legacyUnlockMask |= bit;
        saveChanges();
        return true;
    }

    public boolean canInteract(Player player) {
        if (player.level() != level || isRemoved()) return false;
        if (player.distanceToSqr(worldPosition.getCenter()) <= 64
                || com.atir.molecularmanipulator.terminal.BuildingTerminal.canAccess(player, this)) return true;
        // Attached anchors may be arbitrarily long: use the actual valid member, never an enlarged radius.
        var pos = player.blockPosition();
        for (var member : managedAnchors.keySet()) {
            if (member.distSqr(pos) <= 64 && level.hasChunkAt(member)
                    && level.getBlockState(member).getBlock() instanceof EntropyAnchorBlock
                    && level.getBlockState(member).getValue(EntropyAnchorBlock.FORMED)) return true;
        }
        return false;
    }

    public void openMenu(Player player) {
        if (player instanceof ServerPlayer server && isFormed() && canInteract(player)) {
            bindOwner(server);
            MenuOpener.open(ModContent.STAR_COMPUTATION_ENGINE_MENU.get(), player, MenuLocators.forBlockEntity(this));
        }
    }

    public void refreshStructureNow() { refreshStructure(); }

    /** Transient diagnostics; never treated as saved formation or research credit. */
    @Nullable public EntropyArmillaryFoundation.Scan getFoundationScan() { return foundationScan; }

    @Nullable
    public static StarComputationEngineCoreBlockEntity findCore(Level level, BlockPos part) {
        if (!level.hasChunkAt(part)) return null;
        var state = level.getBlockState(part);
        if (state.getBlock() instanceof EntropyAnchorBlock && state.getValue(EntropyAnchorBlock.FORMED)) {
            if (state.getValue(EntropyAnchorBlock.ARMILLARY)) {
                return findArmillaryCore(level, part, state);
            }
            var facing = state.getValue(HorizontalDirectionalBlock.FACING);
            var center = StarComputationAnchorStructure.centerOf(part, facing, state.getValue(EntropyAnchorBlock.ANCHOR_POS));
            var inward = facing.getOpposite();
            while (level.hasChunkAt(center) && level.isInWorldBounds(center)) {
                var next = level.getBlockState(center);
                if (!(next.getBlock() instanceof EntropyAnchorBlock)) {
                    var candidate = center.relative(inward);
                    return level.hasChunkAt(candidate) && level.getBlockEntity(candidate) instanceof StarComputationEngineCoreBlockEntity core
                            && core.isFormed() && core.managedAnchors.containsKey(part) ? core : null;
                }
                if (!next.getValue(EntropyAnchorBlock.FORMED) || next.getValue(HorizontalDirectionalBlock.FACING) != facing) return null;
                center = center.relative(inward);
            }
            return null;
        }
        for (var candidate : BlockPos.betweenClosed(part.offset(-1, -1, -1), part.offset(1, 1, 1))) {
            if (level.hasChunkAt(candidate) && level.getBlockEntity(candidate) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isFormed()) return core;
        }
        return null;
    }

    /**
     * Armillary lookup first trusts the per-level ownership map, then verifies
     * candidate slots without loading any chunk. Facing and ring alone are never
     * enough to claim another engine's member.
     */
    @Nullable
    private static StarComputationEngineCoreBlockEntity findArmillaryCore(Level level, BlockPos part, BlockState state) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int ring = state.getValue(EntropyAnchorBlock.ANCHOR_POS);
        if (level instanceof ServerLevel server) {
            var owner = EntropyArmillaryOwnership.owner(server, part);
            if (owner != null && server.hasChunkAt(owner)
                    && server.getBlockEntity(owner) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isFormed() && core.isArmillary() && core.isArmillaryMember(part)) {
                return core;
            }
        }
        for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
            var corePos = part.subtract(EntropyArmillaryLayout.offset(facing, ring, slot));
            if (!level.hasChunkAt(corePos)) continue;
            if (level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isFormed() && core.isArmillary()
                    && core.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing
                    && (core.isArmillaryMember(part) || core.mayOwnArmillarySlot(ring, slot))) {
                return core;
            }
        }
        return null;
    }

    /**
     * @return whether the blockstate currently shows a formed engine. Reads the
     *         blockstate rather than the saved flag so callers always see the
     *         same state as the shell models.
     */
    public boolean isFormed() {
        return getBlockState().getValue(StarComputationEngineCoreBlock.FORMED);
    }

    public void serverTick() {
        refundInformation();
        if (overclocked && StarComputationNetwork.best(getMainNode().getGrid()) == this) payOverclockForCurrentTick();
        if (overclocked && (!isFormed() || !getMainNode().isActive())) {
            overclocked = false;
            saveChanges();
        }
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        refreshStructure();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            // Existing worlds may still have the pre-FORMED blockstate defaults
            // (formed=false) while the block entity NBT says the engine is formed.
            // Do not rewrite blockstates while the chunk is still loading; run
            // the forced pass on the next server tick instead.
            needsStateSync = true;
            tickCounter = CHECK_INTERVAL - 1;
        }
    }

    private void refreshStructure() {
        var level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        // Opt-out must restore loaded base meshes even if the legacy shell
        // cannot currently be scanned across an unloaded chunk boundary.
        if (!isArmillary() && level instanceof ServerLevel server) {
            EntropyArmillaryFoundationVisuals.setHidden(server, getBlockPos(), false);
        }
        // Preserve the legacy contract. Armillary mode must still find known
        // shell/base holes when another shell chunk is unavailable.
        if (!isArmillary() && !level.hasChunksAt(getBlockPos().offset(-1, -1, -1), getBlockPos().offset(1, 1, 1))) {
            if (level instanceof ServerLevel server) MultiblockChunkLoading.maintain(this);
            return;
        }

        boolean shellUnloaded = false;
        boolean complete = true;
        for (int dx = -1; dx <= 1 && complete; dx++) {
            for (int dy = -1; dy <= 1 && complete; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    var shellPos = getBlockPos().offset(dx, dy, dz);
                    if (!level.hasChunkAt(shellPos)) {
                        shellUnloaded = true;
                        continue;
                    }
                    var state = level.getBlockState(shellPos);
                    boolean sideWindow = dy == 0 && Math.abs(dx) + Math.abs(dz) == 1;
                    boolean valid = sideWindow
                            ? state.is(ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get())
                            : state.is(ModContent.STAR_COMPUTATION_ENGINE_CASING.get());
                    if (!valid) {
                        complete = false;
                        break;
                    }
                }
            }
        }

        foundationVisualsDirty = false;
        if (isArmillary() && complete) {
            var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            foundationScan = EntropyArmillaryFoundation.scan(getBlockPos(), facing, (pos, material) -> {
                if (!level.hasChunkAt(pos)) return EntropyArmillaryFoundation.Probe.UNLOADED;
                var expected = material == EntropyArmillaryFoundation.Material.PLINTH
                        ? ModContent.ENTROPY_ARMILLARY_PLINTH.get() : ModContent.ENTROPY_ARMILLARY_TRIM.get();
                var state = level.getBlockState(pos);
                if (!state.is(expected)) return EntropyArmillaryFoundation.Probe.OTHER;
                if (!state.getValue(com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock.FORMED)) {
                    foundationVisualsDirty = true;
                }
                return EntropyArmillaryFoundation.Probe.MATCH;
            });
            if (!foundationScan.resolved() || foundationScan.complete() && shellUnloaded) {
                // Unknown is not air. Keep the recovery footprint and shell state,
                // but never grant ring benefits before the physical base is verified.
                if (litRings > 0 || armillaryValidated) setChanged();
                foundationRecovery = true;
                pendingLitRings = Math.max(pendingLitRings, litRings);
                litRings = 0;
                armillaryValidated = false;
                syncVisualState();
                if (level instanceof ServerLevel server) MultiblockChunkLoading.maintain(this);
                return;
            }
            complete = foundationScan.complete();
        } else {
            foundationScan = null;
        }

        foundationRecovery = false;
        boolean changed = complete != formed;
        if (changed || needsStateSync || isFormed() != complete || complete && foundationVisualsDirty) {
            // A missing known block can unform loaded members now. Reconcile
            // the remaining shell states once unavailable chunks return.
            needsStateSync = shellUnloaded;
            formed = complete;
            updateAllBlockStates();
            if (changed) {
                setChanged();
            }
        }
        // Columns can be extended or broken without changing the 3x3x3 shell.
        refreshAnchors(level);
        if (level instanceof ServerLevel server) MultiblockChunkLoading.maintain(this);
    }

    private void refreshAnchors(Level level) {
        if (!formed) {
            anchorCounts.clear();
            resetManagedAnchors(level);
            if (level instanceof ServerLevel server) {
                EntropyArmillaryOwnership.release(server, getBlockPos());
            }
            litRings = 0;
            pendingLitRings = 0;
            armillaryValidated = true;
            syncVisualState();
            return;
        }
        if (isArmillary()) {
            if (level instanceof ServerLevel server) {
                refreshArmillaryAnchors(server);
            }
            return;
        }
        // Leaving the opt-in mode hides only armillary members. Legacy columns
        // (including the four old directions) keep their exact old behaviour.
        if (level instanceof ServerLevel server) {
            resetArmillaryManaged(server);
        }
        litRings = 0;
        pendingLitRings = 0;
        armillaryValidated = true;
        refreshLegacyAnchors(level);
    }

    private void refreshLegacyAnchors(Level level) {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var column = StarComputationAnchorStructure.scan(getBlockPos(), facing,
                    (pos, index) -> anchorProbe(EntropyAnchorBlock.loadedState(level, pos), facing, index));
            if (!column.resolved()) {
                // Do not invent a new end cap or dissolve a column at an unloaded boundary.
                continue;
            }
            var desired = new HashSet<BlockPos>();
            long[] counts = new long[3];
            for (int layer = 0; layer < column.layers(); layer++) {
                for (int index = 0; index < StarComputationAnchorStructure.PARTS_PER_LAYER; index++) {
                    var pos = StarComputationAnchorStructure.position(getBlockPos(), facing, layer, index);
                    var state = level.getBlockState(pos);
                    counts[state.is(ModContent.TERMINAL_ENTROPY_ANCHOR.get()) ? 2
                            : state.is(ModContent.QUANTUM_ENTROPY_ANCHOR.get()) ? 1 : 0]++;
                    var updated = EntropyAnchorBlock.formedState(state, facing, index, layer == column.layers() - 1);
                    if (updated != state) {
                        level.setBlock(pos, updated, EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
                    }
                    EntropyAnchorBlock.scheduleIntegrityCheck(level, pos, updated);
                    desired.add(pos);
                    managedAnchors.put(pos, new AnchorMember(facing, index));
                    if (updated != state) updateNetworkNeighbors(level, pos);
                }
            }
            anchorCounts.put(facing, counts);
            var previous = managedAnchors.entrySet().iterator();
            while (previous.hasNext()) {
                var entry = previous.next();
                if (entry.getValue().facing() == facing && !desired.contains(entry.getKey())
                        && resetAnchor(level, entry.getKey(), entry.getValue())) {
                    previous.remove();
                }
            }
        }
        syncVisualState();
    }

    /**
     * Strict ring scan plus ownership claim. Only the verified outer prefix is
     * ever formed; an unresolved tail is dropped to the last actually verified
     * ring once the controller has validated at least once since loading.
     */
    private void refreshArmillaryAnchors(ServerLevel level) {
        // A same-block /setblock mode switch must not retain a legacy column's
        // ownership. Keep unloaded members for their scheduled local repair.
        var oldMembers = managedAnchors.entrySet().iterator();
        while (oldMembers.hasNext()) {
            var entry = oldMembers.next();
            if (!entry.getValue().armillary() && resetAnchor(level, entry.getKey(), entry.getValue())) oldMembers.remove();
        }
        anchorCounts.clear();
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var corePos = getBlockPos();
        var scan = EntropyArmillaryRings.scan(corePos, facing, (pos, ring) -> {
            var owner = EntropyArmillaryOwnership.owner(level, pos);
            boolean ownedByOther = owner != null && !owner.equals(corePos) && level.hasChunkAt(owner);
            return armillaryProbe(EntropyAnchorBlock.loadedState(level, pos), armillaryAnchorFor(ring),
                    facing, ring, ownedByOther);
        });
        int pending = Math.clamp(pendingLitRings, 0, EntropyArmillaryRings.RINGS);
        if (!armillaryValidated && pending > 0 && !scan.resolved() && scan.lit() < pending) {
            // A saved build is still restoring its tickets/chunks. Keep the saved
            // footprint, but do not grant any unverified benefit or dissolve it.
            litRings = 0;
            syncVisualState();
            return;
        }

        int lit = scan.lit();
        claimRings:
        for (int ring = 0; ring < lit; ring++) {
            for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                if (!EntropyArmillaryOwnership.claim(level, corePos,
                        EntropyArmillaryRings.slot(corePos, facing, ring, slot))) {
                    lit = ring;
                    break claimRings;
                }
            }
        }

        var desired = new HashSet<BlockPos>();
        for (int ring = 0; ring < lit; ring++) {
            boolean end = ring == lit - 1;
            for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                var pos = EntropyArmillaryRings.slot(corePos, facing, ring, slot);
                var state = level.getBlockState(pos);
                var updated = EntropyAnchorBlock.armillaryState(state, facing, ring, end);
                if (updated != state) {
                    level.setBlock(pos, updated, EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
                    updateNetworkNeighbors(level, pos);
                }
                EntropyAnchorBlock.scheduleIntegrityCheck(level, pos, updated);
                desired.add(pos);
                managedAnchors.put(pos, new AnchorMember(facing, ring, true, slot));
            }
        }

        var previous = managedAnchors.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            if (entry.getValue().armillary() && !desired.contains(entry.getKey())
                    && releaseManagedArmillary(level, entry.getKey(), entry.getValue())) {
                previous.remove();
            }
        }
        EntropyArmillaryOwnership.retain(level, corePos, desired);
        if (litRings != lit || pendingLitRings != 0 || !armillaryValidated) setChanged();
        litRings = lit;
        pendingLitRings = 0;
        armillaryValidated = true;
        syncVisualState();
    }

    /** Expected anchor tier for a ring, independent of the block's tier display. */
    static EntropyArmillaryRings.Probe armillaryProbe(@Nullable BlockState state, Block expected,
            Direction facing, int ring, boolean ownedByOther) {
        if (state == null) {
            return EntropyArmillaryRings.Probe.UNLOADED;
        }
        if (!state.is(expected)) {
            return EntropyArmillaryRings.Probe.OTHER;
        }
        if (state.getValue(EntropyAnchorBlock.FORMED)) {
            if (!state.getValue(EntropyAnchorBlock.ARMILLARY)
                    || state.getValue(HorizontalDirectionalBlock.FACING) != facing
                    || state.getValue(EntropyAnchorBlock.ANCHOR_POS) != ring
                    || ownedByOther) {
                return EntropyArmillaryRings.Probe.OTHER;
            }
        } else if (ownedByOther) {
            return EntropyArmillaryRings.Probe.OTHER;
        }
        return EntropyArmillaryRings.Probe.MATCH;
    }

    private static Block armillaryAnchorFor(int ring) {
        return switch (EntropyArmillaryRings.tierOf(ring)) {
            case 0 -> ModContent.BASE_ENTROPY_ANCHOR.get();
            case 1 -> ModContent.QUANTUM_ENTROPY_ANCHOR.get();
            default -> ModContent.TERMINAL_ENTROPY_ANCHOR.get();
        };
    }

    private void resetArmillaryManaged(ServerLevel level) {
        var previous = managedAnchors.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            if (entry.getValue().armillary()
                    && releaseManagedArmillary(level, entry.getKey(), entry.getValue())) {
                previous.remove();
            }
        }
        EntropyArmillaryOwnership.release(level, getBlockPos());
    }

    /**
     * Releases one armillary slot. When its chunk is unloaded the membership is
     * still dropped (unverified state is not kept), and the anchor's own local
     * integrity check will unform the stale block when that chunk returns.
     */
    private boolean releaseManagedArmillary(ServerLevel level, BlockPos pos, AnchorMember member) {
        var owner = EntropyArmillaryOwnership.owner(level, pos);
        if (owner != null && !owner.equals(getBlockPos())) {
            return true;
        }
        var state = EntropyAnchorBlock.loadedState(level, pos);
        if (state != null && state.getBlock() instanceof EntropyAnchorBlock
                && state.getValue(EntropyAnchorBlock.FORMED)
                && state.getValue(EntropyAnchorBlock.ARMILLARY)
                && state.getValue(HorizontalDirectionalBlock.FACING) == member.facing()
                && state.getValue(EntropyAnchorBlock.ANCHOR_POS) == member.index()) {
            level.setBlock(pos, EntropyAnchorBlock.unformedState(state), EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
            updateNetworkNeighbors(level, pos);
        }
        EntropyArmillaryOwnership.releaseIfOwner(level, pos, getBlockPos());
        return true;
    }

    private void syncVisualState() {
        int nextTier = 0;
        int nextConnections = 0;
        int nextLit = 0;
        if (formed) {
            if (isArmillary()) {
                if (armillaryValidated) {
                    nextLit = Math.clamp(litRings, 0, EntropyArmillaryRings.RINGS);
                    nextTier = nextLit == 0 ? 0 : EntropyArmillaryRings.tierOf(nextLit - 1) + 1;
                }
            } else {
                for (var entry : anchorCounts.entrySet()) {
                    var counts = entry.getValue();
                    if (counts[0] > 0) nextTier = Math.max(nextTier, 1);
                    if (counts[1] > 0) nextTier = Math.max(nextTier, 2);
                    if (counts[2] > 0) nextTier = Math.max(nextTier, 3);
                    if (counts[0] + counts[1] + counts[2] > 0) {
                        nextConnections |= connectionBit(entry.getKey());
                    }
                }
            }
        }
        int nextActivity = 0;
        if (nextLit > 0 && getMainNode().isActive()
                && StarComputationNetwork.best(getMainNode().getGrid()) == this) {
            nextActivity = overclocked && level != null && paidTick == level.getGameTime() ? 2 : 1;
        }
        if (visualTier != nextTier || visualConnections != nextConnections
                || syncedLitRings != nextLit || visualActivity != nextActivity) {
            visualTier = nextTier;
            visualConnections = nextConnections;
            syncedLitRings = nextLit;
            visualActivity = nextActivity;
            markForClientUpdate();
        }
    }

    private static int connectionBit(Direction direction) {
        return switch (direction) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 8;
            default -> 0;
        };
    }

    static StarComputationAnchorStructure.Probe anchorProbe(@Nullable BlockState state, Direction facing, int index) {
        if (state == null) {
            return StarComputationAnchorStructure.Probe.UNLOADED;
        }
        if (state.getBlock() instanceof EntropyAnchorBlock
                && !state.getValue(EntropyAnchorBlock.ARMILLARY)
                && (!state.getValue(EntropyAnchorBlock.FORMED)
                        || state.getValue(HorizontalDirectionalBlock.FACING) == facing
                                && state.getValue(EntropyAnchorBlock.ANCHOR_POS) == index)) {
            return StarComputationAnchorStructure.Probe.ANCHOR;
        }
        // Facing plus the local 3x3 position identifies the column's center line.
        // Do not steal a column with another direction OR another cross-section center.
        return StarComputationAnchorStructure.Probe.OTHER;
    }

    private void resetManagedAnchors(Level level) {
        var previous = managedAnchors.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            if (entry.getValue().armillary() && level instanceof ServerLevel server) {
                if (releaseManagedArmillary(server, entry.getKey(), entry.getValue())) {
                    previous.remove();
                }
            } else if (resetAnchor(level, entry.getKey(), entry.getValue())) {
                previous.remove();
            }
        }
    }

    private static boolean resetAnchor(Level level, BlockPos pos, AnchorMember member) {
        var state = EntropyAnchorBlock.loadedState(level, pos);
        if (state == null) {
            return false;
        }
        if (member.armillary()) {
            if (state.getBlock() instanceof EntropyAnchorBlock
                    && state.getValue(EntropyAnchorBlock.FORMED)
                    && state.getValue(EntropyAnchorBlock.ARMILLARY)
                    && state.getValue(HorizontalDirectionalBlock.FACING) == member.facing()
                    && state.getValue(EntropyAnchorBlock.ANCHOR_POS) == member.index()) {
                level.setBlock(pos, EntropyAnchorBlock.unformedState(state), EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
                updateNetworkNeighbors(level, pos);
            }
            return true;
        }
        if (state.getBlock() instanceof EntropyAnchorBlock
                && state.getValue(EntropyAnchorBlock.FORMED)
                && !state.getValue(EntropyAnchorBlock.ARMILLARY)
                && state.getValue(HorizontalDirectionalBlock.FACING) == member.facing()
                && state.getValue(EntropyAnchorBlock.ANCHOR_POS) == member.index()) {
            level.setBlock(pos, EntropyAnchorBlock.unformedState(state), EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
        }
        return true;
    }

    /**
     * Updates FORMED on the shell and the physical foundation's visual-only state.
     */
    private void updateAllBlockStates() {
        var level = getLevel();
        if (level == null) {
            return;
        }

        if (level instanceof ServerLevel server) {
            EntropyArmillaryFoundationVisuals.setHidden(server, getBlockPos(), isArmillary() && formed);
        }
        applyFormed(level, getBlockPos(), formed);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    applyFormed(level, getBlockPos().offset(dx, dy, dz), formed);
                }
            }
        }
    }

    /**
     * Drops the shell back to its unformed look when the core disappears, so no
     * shell block keeps a stale FORMED state after the engine is taken apart.
     */
    public static void resetShell(Level level, BlockPos corePos) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core) {
            core.resetManagedAnchors(level);
        }
        if (level instanceof ServerLevel server) {
            EntropyArmillaryOwnership.release(server, corePos);
            EntropyArmillaryFoundationVisuals.setHidden(server, corePos, false);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    var pos = corePos.offset(dx, dy, dz);
                    if (level.hasChunkAt(pos)) {
                        applyFormed(level, pos, false);
                    }
                }
            }
        }
    }

    private static void applyFormed(Level level, BlockPos pos, boolean formed) {
        if (!level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        BlockState updated = withFormed(state, formed);
        if (updated != null) {
            level.setBlock(pos, updated, EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
            updateNetworkNeighbors(level, pos);
        }
    }

    private static void updateNetworkNeighbors(Level level, BlockPos pos) {
        level.invalidateCapabilities(pos);
        for (var direction : Direction.values()) {
            var adjacent = pos.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            var block = level.getBlockState(adjacent).getBlock();
            if (!(block instanceof EntropyAnchorBlock || block instanceof StarComputationEngineCasingBlock
                    || block instanceof StarComputationObservationPortBlock || block instanceof StarComputationEngineCoreBlock)) {
                level.neighborChanged(adjacent, level.getBlockState(pos).getBlock(), pos);
            }
        }
    }

    @Nullable
    static BlockState withFormed(BlockState state, boolean formed) {
        if (!(state.getBlock() instanceof StarComputationEngineCoreBlock
                || state.getBlock() instanceof StarComputationEngineCasingBlock
                || state.getBlock() instanceof StarComputationObservationPortBlock)) {
            return null;
        }
        // All engine parts share the same named FORMED property. Do not return
        // early when only INVISIBLE is stale (e.g. a pre-migration saved state).
        BlockState updated = state.setValue(StarComputationEngineCoreBlock.FORMED, formed);
        if (updated.hasProperty(StarComputationEngineCasingBlock.INVISIBLE)) {
            updated = updated.setValue(StarComputationEngineCasingBlock.INVISIBLE, formed);
        }
        return updated == state ? null : updated;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
        tag.putBoolean("entropy_overclocked", overclocked);
        tag.putInt("entropy_unlocks", legacyUnlockMask);
        tag.putLong("entropy_refund", pendingInformationRefund);
        tag.putInt("entropy_visual_tier", visualTier);
        tag.putInt("entropy_visual_connections", visualConnections);
        tag.putInt("entropy_armillary_lit_rings", litRings);
        tag.putInt("entropy_armillary_pending_rings", pendingLitRings);
        tag.putInt("entropy_armillary_layout_version", ARMILLARY_LAYOUT_VERSION);
        if (boundTeam != null) tag.putUUID("entropy_team", boundTeam);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(visualTier);
        data.writeByte(visualConnections);
        data.writeByte(syncedLitRings);
        data.writeByte(visualActivity);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int nextTier = Math.clamp(data.readUnsignedByte(), 0, 3);
        int nextConnections = data.readUnsignedByte() & 0xF;
        int nextLit = Math.clamp(data.readUnsignedByte(), 0, EntropyArmillaryRings.RINGS);
        int nextActivity = Math.clamp(data.readUnsignedByte(), 0, 2);
        changed |= visualTier != nextTier || visualConnections != nextConnections
                || litRings != nextLit || visualActivity != nextActivity;
        visualTier = nextTier;
        visualConnections = nextConnections;
        litRings = nextLit;
        syncedLitRings = nextLit;
        visualActivity = nextActivity;
        armillaryValidated = true;
        return changed;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        formed = tag.getBoolean("formed");
        overclocked = tag.getBoolean("entropy_overclocked");
        legacyUnlockMask = tag.getInt("entropy_unlocks") & StarComputationRecipes.ALL_UNLOCKS;
        pendingInformationRefund = Math.max(0, tag.getLong("entropy_refund"));
        visualTier = Math.clamp(tag.getInt("entropy_visual_tier"), 0, 3);
        visualConnections = tag.getInt("entropy_visual_connections") & 0xF;
        boolean currentLayout = tag.getInt("entropy_armillary_layout_version") == ARMILLARY_LAYOUT_VERSION;
        int savedLit = currentLayout
                ? Math.clamp(tag.getInt("entropy_armillary_lit_rings"), 0, EntropyArmillaryRings.RINGS) : 0;
        int savedPending = currentLayout
                ? Math.clamp(tag.getInt("entropy_armillary_pending_rings"), 0, EntropyArmillaryRings.RINGS) : 0;
        // Saved rings are only a ticket-recovery hint. They never become a
        // benefit until this server has scanned them again.
        litRings = 0;
        pendingLitRings = Math.max(savedLit, savedPending);
        armillaryValidated = !isArmillary();
        foundationScan = null;
        foundationRecovery = false;
        syncedLitRings = 0;
        visualActivity = 0;
        if (isArmillary() && (!currentLayout || pendingLitRings > 0)) {
            visualTier = 0;
            visualConnections = 0;
        }
        boundTeam = tag.hasUUID("entropy_team") ? tag.getUUID("entropy_team") : null;
    }
}
