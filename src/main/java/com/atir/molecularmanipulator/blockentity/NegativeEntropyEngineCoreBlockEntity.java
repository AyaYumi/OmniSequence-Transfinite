package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.atir.molecularmanipulator.ae2.NegativeEntropyInfoKey;
import com.atir.molecularmanipulator.client.render.NegativeEntropyEngineModelData;
import com.atir.molecularmanipulator.entropy.NegativeEntropyBenefits;
import com.atir.molecularmanipulator.entropy.NegativeEntropyNetwork;
import com.atir.molecularmanipulator.entropy.NegativeEntropyRecipes;
import com.atir.molecularmanipulator.entropy.NegativeEntropyResearchData;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.menu.NegativeEntropyEngineMenu;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import com.atir.molecularmanipulator.block.EntropyAnchorBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyEngineCasingBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyEngineCoreBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyObservationPortBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side 3x3x3 structure owner for the Negative Entropy Engine.
 *
 * <p>The formed flag switches the core to its oversized baked model and hides
 * the 26 shell members beneath it. Removing any part restores the loose models.</p>
 */
public final class NegativeEntropyEngineCoreBlockEntity extends AENetworkedBlockEntity {
    private static final int CHECK_INTERVAL = 10;

    private boolean formed;
    private boolean needsStateSync = true;
    private int tickCounter;
    private boolean overclocked;
    private long paidTick = Long.MIN_VALUE;
    private long pendingInformationRefund;
    private UUID boundTeam;
    private int legacyUnlockMask;
    private final Map<Direction, long[]> anchorCounts = new java.util.EnumMap<>(Direction.class);
    private record AnchorMember(Direction facing, int index) {
    }

    private final Map<BlockPos, AnchorMember> managedAnchors = new HashMap<>();
    /** Server-validated visual snapshot sent through AE2's normal BE update stream. */
    private int visualTier;
    private int visualConnections;

    public NegativeEntropyEngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.NEGATIVE_ENTROPY_ENGINE_CORE_BE.get(), pos, state);
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

    public long getEffectiveEntropy() {
        long[] counts = countAnchors();
        return isFormed() ? NegativeEntropyBenefits.entropy(counts[0], counts[1], counts[2]) : 0;
    }

    public int getAnchorCount() {
        long[] counts = countAnchors();
        return isFormed() ? (int) Math.min(Integer.MAX_VALUE, counts[0] + counts[1] + counts[2]) : 0;
    }

    private long[] countAnchors() {
        long[] total = new long[3];
        anchorCounts.values().forEach(count -> { for (int i = 0; i < 3; i++) total[i] += count[i]; });
        return total;
    }

    public NegativeEntropyBenefits getBenefits() {
        return new NegativeEntropyBenefits(NegativeEntropyBenefits.levelFor(getEffectiveEntropy()), overclocked);
    }

    public boolean isOverclocked() { return overclocked; }

    @Override
    public ModelData getModelData() {
        var inherited = super.getModelData();
        return inherited.derive()
                .with(NegativeEntropyEngineModelData.PROPERTY,
                        new NegativeEntropyEngineModelData.Snapshot(visualTier, visualConnections))
                .build();
    }

    public long getInfoConsumption() {
        return getMainNode().isActive() && NegativeEntropyNetwork.best(getMainNode().getGrid()) == this
                ? getBenefits().infoPerTick() : 0;
    }

    public long getInfoBalance() {
        var grid = getMainNode().getGrid();
        return grid == null ? 0 : grid.getStorageService().getInventory()
                .extract(NegativeEntropyInfoKey.INSTANCE, Long.MAX_VALUE, Actionable.SIMULATE, new MachineSource(this));
    }

    public void toggleOverclock() {
        if (level == null || level.isClientSide()) return;
        if (overclocked) overclocked = false;
        else if (isFormed() && getMainNode().isActive() && getBenefits().level() > 0
                && getInfoBalance() >= new NegativeEntropyBenefits(getBenefits().level(), true).infoPerTick()) overclocked = true;
        saveChanges();
        if (NegativeEntropyNetwork.best(getMainNode().getGrid()) == this) payOverclockForCurrentTick();
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
        if (storage.extract(NegativeEntropyInfoKey.INSTANCE, amount, Actionable.SIMULATE, source) < amount) return false;
        long extracted = storage.extract(NegativeEntropyInfoKey.INSTANCE, amount, Actionable.MODULATE, source);
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
            pendingInformationRefund -= grid.getStorageService().getInventory().insert(NegativeEntropyInfoKey.INSTANCE,
                    pendingInformationRefund, Actionable.MODULATE, new MachineSource(this));
            saveChanges();
        }
    }

    public int getUnlockMask() {
        if (!(level instanceof ServerLevel server)) return legacyUnlockMask;
        var data = NegativeEntropyResearchData.get(server);
        data.mergeUnlockMask(boundTeam, legacyUnlockMask);
        return data.getUnlockMask(boundTeam) | legacyUnlockMask;
    }

    public boolean unlockRecipe(int index, ServerPlayer player) {
        if (level == null || level.isClientSide() || player.level() != level || !player.mayBuild() || player.isSpectator()
                || player.containerMenu == null || !(player.containerMenu instanceof NegativeEntropyEngineMenu)
                || !isFormed() || !getMainNode().isActive() || !canInteract(player)) return false;
        if (index < 0 || index >= NegativeEntropyRecipes.NODES.size()) return false;
        bindOwner(player);
        var node = NegativeEntropyRecipes.NODES.get(index);
        int bit = 1 << index;
        int mask = getUnlockMask();
        if ((mask & bit) != 0 || getEffectiveEntropy() < node.entropy()
                || (mask & node.prerequisiteMask()) != node.prerequisiteMask()) return false;
        if (!extractInformation(node.informationCost(), new PlayerSource(player))) return false;
        NegativeEntropyResearchData.get((ServerLevel) level).addUnlock(boundTeam, index);
        legacyUnlockMask |= bit;
        saveChanges();
        return true;
    }

    public boolean canInteract(Player player) {
        if (player.level() != level || isRemoved()) return false;
        if (player.distanceToSqr(worldPosition.getCenter()) <= 64) return true;
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
            MenuOpener.open(ModContent.NEGATIVE_ENTROPY_ENGINE_MENU.get(), player, MenuLocators.forBlockEntity(this));
        }
    }

    public void refreshStructureNow() { refreshStructure(); }

    @Nullable
    public static NegativeEntropyEngineCoreBlockEntity findCore(Level level, BlockPos part) {
        if (!level.hasChunkAt(part)) return null;
        var state = level.getBlockState(part);
        if (state.getBlock() instanceof EntropyAnchorBlock && state.getValue(EntropyAnchorBlock.FORMED)) {
            var facing = state.getValue(HorizontalDirectionalBlock.FACING);
            var center = NegativeEntropyAnchorStructure.centerOf(part, facing, state.getValue(EntropyAnchorBlock.ANCHOR_POS));
            var inward = facing.getOpposite();
            while (level.hasChunkAt(center) && level.isInWorldBounds(center)) {
                var next = level.getBlockState(center);
                if (!(next.getBlock() instanceof EntropyAnchorBlock)) {
                    var candidate = center.relative(inward);
                    return level.hasChunkAt(candidate) && level.getBlockEntity(candidate) instanceof NegativeEntropyEngineCoreBlockEntity core
                            && core.isFormed() && core.managedAnchors.containsKey(part) ? core : null;
                }
                if (!next.getValue(EntropyAnchorBlock.FORMED) || next.getValue(HorizontalDirectionalBlock.FACING) != facing) return null;
                center = center.relative(inward);
            }
            return null;
        }
        for (var candidate : BlockPos.betweenClosed(part.offset(-1, -1, -1), part.offset(1, 1, 1))) {
            if (level.hasChunkAt(candidate) && level.getBlockEntity(candidate) instanceof NegativeEntropyEngineCoreBlockEntity core
                    && core.isFormed()) return core;
        }
        return null;
    }

    /**
     * @return whether the blockstate currently shows a formed engine. Reads the
     *         blockstate rather than the saved flag so callers always see the
     *         same state as the shell models.
     */
    public boolean isFormed() {
        return getBlockState().getValue(NegativeEntropyEngineCoreBlock.FORMED);
    }

    public void serverTick() {
        refundInformation();
        if (overclocked && NegativeEntropyNetwork.best(getMainNode().getGrid()) == this) payOverclockForCurrentTick();
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

        // Preserve the last known formation while a neighboring chunk is unloaded.
        if (!level.hasChunksAt(getBlockPos().offset(-1, -1, -1), getBlockPos().offset(1, 1, 1))) {
            return;
        }

        boolean complete = true;
        for (int dx = -1; dx <= 1 && complete; dx++) {
            for (int dy = -1; dy <= 1 && complete; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    var state = level.getBlockState(getBlockPos().offset(dx, dy, dz));
                    boolean sideWindow = dy == 0 && Math.abs(dx) + Math.abs(dz) == 1;
                    boolean valid = sideWindow
                            ? state.is(ModContent.NEGATIVE_ENTROPY_OBSERVATION_PORT.get())
                            : state.is(ModContent.NEGATIVE_ENTROPY_ENGINE_CASING.get());
                    if (!valid) {
                        complete = false;
                        break;
                    }
                }
            }
        }

        boolean changed = complete != formed;
        if (changed || needsStateSync) {
            needsStateSync = false;
            formed = complete;
            updateAllBlockStates();
            if (changed) {
                setChanged();
            }
        }
        // Columns can be extended or broken without changing the 3x3x3 shell.
        refreshAnchors(level);
    }

    private void refreshAnchors(Level level) {
        if (!formed) {
            anchorCounts.clear();
            resetManagedAnchors(level);
            syncVisualState();
            return;
        }
        for (var facing : Direction.Plane.HORIZONTAL) {
            var column = NegativeEntropyAnchorStructure.scan(getBlockPos(), facing,
                    (pos, index) -> anchorProbe(EntropyAnchorBlock.loadedState(level, pos), facing, index));
            if (!column.resolved()) {
                // Do not invent a new end cap or dissolve a column at an unloaded boundary.
                continue;
            }
            var desired = new HashSet<BlockPos>();
            long[] counts = new long[3];
            for (int layer = 0; layer < column.layers(); layer++) {
                for (int index = 0; index < NegativeEntropyAnchorStructure.PARTS_PER_LAYER; index++) {
                    var pos = NegativeEntropyAnchorStructure.position(getBlockPos(), facing, layer, index);
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

    private void syncVisualState() {
        int nextTier = 0;
        int nextConnections = 0;
        if (formed) {
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
        if (visualTier != nextTier || visualConnections != nextConnections) {
            visualTier = nextTier;
            visualConnections = nextConnections;
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

    static NegativeEntropyAnchorStructure.Probe anchorProbe(@Nullable BlockState state, Direction facing, int index) {
        if (state == null) {
            return NegativeEntropyAnchorStructure.Probe.UNLOADED;
        }
        if (state.getBlock() instanceof EntropyAnchorBlock
                && (!state.getValue(EntropyAnchorBlock.FORMED)
                        || state.getValue(HorizontalDirectionalBlock.FACING) == facing
                                && state.getValue(EntropyAnchorBlock.ANCHOR_POS) == index)) {
            return NegativeEntropyAnchorStructure.Probe.ANCHOR;
        }
        // Facing plus the local 3x3 position identifies the column's center line.
        // Do not steal a column with another direction OR another cross-section center.
        return NegativeEntropyAnchorStructure.Probe.OTHER;
    }

    private void resetManagedAnchors(Level level) {
        var previous = managedAnchors.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            if (resetAnchor(level, entry.getKey(), entry.getValue())) {
                previous.remove();
            }
        }
    }

    private static boolean resetAnchor(Level level, BlockPos pos, AnchorMember member) {
        var state = EntropyAnchorBlock.loadedState(level, pos);
        if (state == null) {
            return false;
        }
        if (state.getBlock() instanceof EntropyAnchorBlock && state.getValue(EntropyAnchorBlock.FORMED)
                && state.getValue(HorizontalDirectionalBlock.FACING) == member.facing()
                && state.getValue(EntropyAnchorBlock.ANCHOR_POS) == member.index()) {
            level.setBlock(pos, EntropyAnchorBlock.unformedState(state), EntropyAnchorBlock.VISUAL_UPDATE_FLAGS);
        }
        return true;
    }

    /**
     * Updates FORMED on all 27 blocks and INVISIBLE on the shell members.
     */
    private void updateAllBlockStates() {
        var level = getLevel();
        if (level == null) {
            return;
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
        if (level.getBlockEntity(corePos) instanceof NegativeEntropyEngineCoreBlockEntity core) {
            core.resetManagedAnchors(level);
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
            if (!(block instanceof EntropyAnchorBlock || block instanceof NegativeEntropyEngineCasingBlock
                    || block instanceof NegativeEntropyObservationPortBlock || block instanceof NegativeEntropyEngineCoreBlock)) {
                level.neighborChanged(adjacent, level.getBlockState(pos).getBlock(), pos);
            }
        }
    }

    @Nullable
    static BlockState withFormed(BlockState state, boolean formed) {
        if (!(state.getBlock() instanceof NegativeEntropyEngineCoreBlock
                || state.getBlock() instanceof NegativeEntropyEngineCasingBlock
                || state.getBlock() instanceof NegativeEntropyObservationPortBlock)) {
            return null;
        }
        // All engine parts share the same named FORMED property. Do not return
        // early when only INVISIBLE is stale (e.g. a pre-migration saved state).
        BlockState updated = state.setValue(NegativeEntropyEngineCoreBlock.FORMED, formed);
        if (updated.hasProperty(NegativeEntropyEngineCasingBlock.INVISIBLE)) {
            updated = updated.setValue(NegativeEntropyEngineCasingBlock.INVISIBLE, formed);
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
        if (boundTeam != null) tag.putUUID("entropy_team", boundTeam);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(visualTier);
        data.writeByte(visualConnections);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int nextTier = Math.clamp(data.readUnsignedByte(), 0, 3);
        int nextConnections = data.readUnsignedByte() & 0xF;
        changed |= visualTier != nextTier || visualConnections != nextConnections;
        visualTier = nextTier;
        visualConnections = nextConnections;
        return changed;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        formed = tag.getBoolean("formed");
        overclocked = tag.getBoolean("entropy_overclocked");
        legacyUnlockMask = tag.getInt("entropy_unlocks") & NegativeEntropyRecipes.ALL_UNLOCKS;
        pendingInformationRefund = Math.max(0, tag.getLong("entropy_refund"));
        visualTier = Math.clamp(tag.getInt("entropy_visual_tier"), 0, 3);
        visualConnections = tag.getInt("entropy_visual_connections") & 0xF;
        boundTeam = tag.hasUUID("entropy_team") ? tag.getUUID("entropy_team") : null;
    }
}
