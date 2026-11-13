package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.util.SettingsFrom;
import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation;
import com.atir.molecularmanipulator.starcomputation.StarOrreryCoreStructure;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import com.atir.molecularmanipulator.starcomputation.StarComputationNetwork;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipes;
import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeProgress;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessHost;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessLink;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/** Server-side owner of the central 3x3x3 body, physical foundation and nine anchor rings. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class StarComputationEngineCoreBlockEntity extends AENetworkedBlockEntity implements SequenceWirelessHost {
    private static final int CHECK_INTERVAL = 10;
    private static final int STAR_ORRERY_LAYOUT_VERSION = 2;
    private static final int[] STAR_ORRERY_NODE_LEVELS = {1, 3, 6, 7, 9};
    /** Deferred chunk-local repair also covers orphaned shells whose controller no longer exists. */
    private static final Map<ServerLevel, Set<ChunkPos>> LEGACY_VISUAL_CHUNKS = new WeakHashMap<>();

    private final SequenceWirelessLink wirelessLink = new SequenceWirelessLink(this, () -> 16.0);
    private boolean formed;
    private boolean needsStateSync = true;
    private int tickCounter;
    private boolean overclocked;
    private long paidTick = Long.MIN_VALUE;
    private long pendingInformationRefund;
    private UUID boundTeam;
    private int legacyUnlockMask;
    private final StarComputationUpgradeProgress upgrade = new StarComputationUpgradeProgress();
    private String upgradeStatus = "idle";
    private record AnchorMember(Direction facing, int index, int slot) {}

    private final Map<BlockPos, AnchorMember> managedAnchors = new HashMap<>();
    /** Server-validated visual snapshot sent through AE2's normal BE update stream. */
    private int visualTier;
    /** Number of complete outer-prefix rings, zero-based ring count 0..9. */
    private int litRings;
    /** Saved rings that still need runtime revalidation after a reload. */
    private int pendingLitRings;
    private boolean starOrreryValidated = true;
    @Nullable private StarOrreryFoundation.Scan foundationScan;
    @Nullable private StarOrreryCoreStructure.Scan coreScan;
    private boolean structureValidated;
    private boolean foundationRecovery;
    private boolean structureVisualsDirty;
    private int syncedLitRings;
    /** Runtime-only visual load: idle, selected online engine, or paid overclock. */
    private int visualActivity;

    public StarComputationEngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.STAR_COMPUTATION_ENGINE_CORE_BE.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(16);
    }

    @Override public SequenceWirelessLink getWirelessLink() { return wirelessLink; }
    @Override public void refreshRemoteConnection() {
        wirelessLink.update(false, isFormed() ? MolecularCenterBlockEntity.QuantumLinkState.CONNECTED
                : MolecularCenterBlockEntity.QuantumLinkState.STRUCTURE_INCOMPLETE);
    }
    @Override public void onReady() {
        if (BuildingTransfer.restoring(this)) return;
        super.onReady();
        refreshRemoteConnection();
    }
    @Override public void onChunkUnloaded() { wirelessLink.disconnect(); super.onChunkUnloaded(); }
    @Override public void setRemoved() { wirelessLink.disconnect(); super.setRemoved(); }
    @Override public void clearContent() { wirelessLink.clear(); super.clearContent(); }

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

    public boolean isStarOrrery() {
        var state = getBlockState();
        return state.hasProperty(StarComputationEngineCoreBlock.STAR_ORRERY)
                && state.getValue(StarComputationEngineCoreBlock.STAR_ORRERY);
    }

    public int getLitRings() {
        return isStarOrrery() && isFormed() && starOrreryValidated
                ? Math.clamp(litRings, 0, StarOrreryRings.RINGS) : 0;
    }

    public int getVisualTier() {
        return visualTier;
    }

    public int getVisualActivity() {
        return getLitRings() > 0 ? visualActivity : 0;
    }

    /** Legacy UI threshold; server-side data-pack overrides use the same level as the unified purchase path. */
    public int getResearchEntropyThreshold(int node) {
        if (node < 0 || node >= StarComputationRecipes.NODES.size()) return 0;
        if (level instanceof ServerLevel) {
            var recipe = StarResearchNodes.nodes(level).get(StarComputationResearchData.LEGACY_NODE_IDS.get(node));
            return recipe == null ? 0 : StarOrreryLayout.threshold(recipe.requiredLevel());
        }
        return StarOrreryLayout.threshold(STAR_ORRERY_NODE_LEVELS[node]);
    }

    public int getEffectiveLevel() {
        return upgrade.effectiveLevel(getLitRings());
    }

    public long getEffectiveEntropy() {
        // Physical rings above the certified ceiling never grant benefits or research budgets.
        return StarOrreryLayout.entropyForRings(getEffectiveLevel());
    }

    public int getCertifiedLevel() { return upgrade.certifiedLevel(); }
    public int getUpgradeTarget() { return upgrade.targetLevel(); }
    public double getUpgradeEnergy() { return upgrade.paidEnergy(); }
    public boolean isUpgradeRunning() { return upgrade.running(); }
    public double getUpgradeRate() { return upgrade.actualRate(); }
    public String getUpgradeStatus() {
        if (BuildingTransfer.restoring(this)) return "restoring";
        if (upgrade.migrationPending()) return "migration";
        if (!upgrade.running() && upgrade.targetLevel() != 0) return "paused";
        return upgradeStatus;
    }

    /** Menu permission checks precede this server-only, fresh-structure and fresh-rule check. */
    public boolean startUpgrade(int target, ServerPlayer player) {
        if (!(level instanceof ServerLevel) || player.level() != level || !player.mayBuild() || player.isSpectator()
                || !canInteract(player) || BuildingTransfer.restoring(this) || upgrade.running()
                || target < 1 || target > 9) return false;
        bindOwner(player);
        refreshStructureNow();
        if (upgrade.migrationPending()) { upgradeStatus = "migration"; return false; }
        if (!isFormed()) { upgradeStatus = "structure"; return false; }
        if (target <= 3) {
            if (getLitRings() != target - 1) return false;
            String blocked = StarOrreryAutoAssembly.assemble(this, target, player);
            if (blocked != null) { upgradeStatus = blocked; return false; }
            refreshStructureNow();
            upgradeStatus = getLitRings() >= target ? "complete" : "structure";
            saveChanges();
            return getLitRings() >= target;
        }
        if (target != upgrade.certifiedLevel() + 1) return false;
        var rule = StarComputationUpgrades.rule(level, target);
        if (rule != null && rule.recipe().autoBuild()) {
            var plan = StarOrreryAutoAssembly.plan(this, target, player);
            if (plan.blocked() != null) { upgradeStatus = plan.blocked(); return false; }
            if (plan.supplies().stream().anyMatch(supply -> supply.available() < supply.required() - supply.placed())) {
                upgradeStatus = "anchor_materials";
                return false;
            }
            if (!StarComputationUpgrades.evaluate(this, rule, player).passed()) {
                upgradeStatus = "conditions";
                return false;
            }
            if (rule.recipe().energy() > 0 && (!getMainNode().isActive() || getMainNode().getGrid() == null)) {
                upgradeStatus = "offline";
                return false;
            }
            String blocked = StarOrreryAutoAssembly.assemble(this, target, player);
            if (blocked != null) { upgradeStatus = blocked; return false; }
            refreshStructureNow();
        }
        String blocked = upgradeBlockReason(target, rule);
        if (blocked != null) { upgradeStatus = blocked; return false; }
        if (!upgrade.start(target)) return false;
        upgradeStatus = "running";
        if (rule.recipe().energy() == 0) tickUpgrade();
        saveChanges();
        return true;
    }

    public boolean pauseUpgrade() {
        if (!(level instanceof ServerLevel) || !upgrade.pause()) return false;
        upgradeStatus = "paused";
        saveChanges();
        return true;
    }

    @Nullable
    private String upgradeBlockReason(int target, @Nullable StarComputationUpgrades.Rule rule) {
        if (BuildingTransfer.restoring(this)) return "restoring";
        if (!isFormed() || getLitRings() < target) return "structure";
        if (rule == null) return "rule_missing";
        if (rule.recipe().energy() > 0 && (!getMainNode().isActive() || getMainNode().getGrid() == null)) return "offline";
        return StarComputationUpgrades.evaluate(this, rule).passed() ? null : "conditions";
    }

    private void tickUpgrade() {
        upgrade.resetRate();
        if (!upgrade.running()) return;
        int target = upgrade.targetLevel();
        var rule = StarComputationUpgrades.rule(level, target);
        String blocked = upgradeBlockReason(target, rule);
        if (blocked != null) {
            upgradeStatus = blocked;
            return;
        }
        double before = upgrade.paidEnergy();
        var result = upgrade.tick(rule.recipe().energy(), rule.recipe().aePerTick(),
                () -> upgradeBlockReason(target, rule) == null,
                amount -> getMainNode().getGrid() == null ? 0 : getMainNode().getGrid().getEnergyService()
                        .extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE));
        upgradeStatus = switch (result) {
            case COMPLETE -> "complete";
            case NO_POWER -> "energy";
            case BLOCKED -> "conditions";
            case PAUSED -> "paused";
            case RUNNING -> "running";
        };
        if (result == StarComputationUpgradeProgress.Result.COMPLETE) {
            // Never carry this tick's old-level overclock payment into a newly certified level.
            overclocked = false;
            paidTick = Long.MIN_VALUE;
            syncVisualState();
        }
        if (before != upgrade.paidEnergy() || result == StarComputationUpgradeProgress.Result.COMPLETE) saveChanges();
    }

    public int getAnchorCount() {
        int total = 0;
        for (int ring = 0; ring < getLitRings(); ring++) {
            total += StarOrreryRings.slotCount(ring);
        }
        return total;
    }

    /** Formed anchor count of one tier: 0 = base, 1 = quantum, 2 = terminal. */
    public int getAnchorCount(int tier) {
        return starOrreryTierCount(getLitRings(), tier);
    }

    /** Slots of one ring tier inside the verified outer prefix; never derived from a level. */
    static int starOrreryTierCount(int litRings, int tier) {
        if (tier < 0 || tier > 2) return 0;
        int total = 0;
        for (int ring = 0; ring < Math.clamp(litRings, 0, StarOrreryRings.RINGS); ring++) {
            if (StarOrreryLayout.tierOf(ring) == tier) {
                total += StarOrreryLayout.slotCount(ring);
            }
        }
        return total;
    }

    public StarComputationBenefits getBenefits() {
        return new StarComputationBenefits(getEffectiveLevel(), overclocked);
    }

    /** Bounded footprint of the foundation, core, verified/recovering rings and next candidate ring. */
    public Set<ChunkPos> getChunkLoadingChunks() {
        if (!isStarOrrery() || !isFormed() && !foundationRecovery) {
            return Set.of();
        }
        int rings = Math.min(StarOrreryRings.RINGS, 1 + Math.max(litRings, pendingLitRings));
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return StarOrreryRings.chunksFor(worldPosition, facing, rings);
    }

    public boolean isStarOrreryMember(BlockPos pos) {
        return managedAnchors.containsKey(pos);
    }

    /** True when this controller currently considers the whole ring part of its footprint. */
    public boolean mayOwnStarOrrerySlot(int ring, int slot) {
        if (!isStarOrrery()) {
            return false;
        }
        int rings = Math.max(Math.clamp(litRings, 0, StarOrreryRings.RINGS),
                Math.clamp(pendingLitRings, 0, StarOrreryRings.RINGS));
        return ring >= 0 && ring < rings && slot >= 0 && slot < StarOrreryRings.slotCount(ring);
    }

    /** Physical central body, foundation and owned rings; retired columns are never folded. */
    public Set<BlockPos> foldingMembers() {
        var members = new java.util.LinkedHashSet<BlockPos>();
        if (level == null) return Set.of();
        for (var pos : managedAnchors.keySet())
            if (level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof StarPositionAnchorBlock) members.add(pos);
        members.add(worldPosition);
        if (isStarOrrery()) {
            for (var member : StarOrreryCoreStructure.members()) {
                var pos = worldPosition.offset(member.offset());
                if (level.hasChunkAt(pos) && level.getBlockState(pos).is(coreBlock(member.material()))) members.add(pos);
            }
            var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            for (var member : StarOrreryFoundation.members()) {
                var pos = StarOrreryFoundation.position(worldPosition, facing, member);
                var expected = member.material() == StarOrreryFoundation.Material.PLINTH
                        ? ModContent.STAR_ORRERY_ENGRAVED_PLINTH.get() : ModContent.STAR_ORRERY_GILDED_PLINTH.get();
                if (level.hasChunkAt(pos) && level.getBlockState(pos).is(expected)) members.add(pos);
            }
        }
        return Set.copyOf(members);
    }

    public boolean isOverclocked() { return overclocked; }

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
        if (amount == 0) return true;
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
        return data.getUnlockMask(boundTeam);
    }

    /** Import paid legacy core bits first, then expose the complete permanent progress of this bound team only. */
    public Set<ResourceLocation> getUnlockedResearchIds() {
        getUnlockMask();
        return level instanceof ServerLevel server
                ? StarComputationResearchData.get(server).getUnlockedNodes(boundTeam) : Set.of();
    }

    /** Returns true only for a new, paid grant. Duplicate/invalid requests never extract information. */
    public boolean tryUnlockResearch(ServerPlayer player, ResourceLocation id) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || player.level() != level || isRemoved() || !player.mayBuild() || player.isSpectator()
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this
                || !level.mayInteract(player, worldPosition) || !canInteract(player)
                || boundTeam == null || !boundTeam.equals(FtbTeamOwnership.forPlayer(player))
                || BuildingTransfer.restoring(this) || !isFormed() || !getMainNode().isActive() || id == null) return false;
        // Never use StarComputationNetwork's union: borrowed authorization is not permanent team progress.
        var permanentUnlocks = getUnlockedResearchIds();
        var catalog = StarResearchNodes.catalog(server);
        if (!catalog.canUnlock(id, getBenefits().level(), permanentUnlocks)) return false;
        var node = catalog.nodes().get(id);
        if (!extractInformation(node.informationCost(), new PlayerSource(player))) return false;
        var data = StarComputationResearchData.get(server);
        if (!data.addUnlock(boundTeam, id)) return false;
        legacyUnlockMask |= data.getUnlockMask(boundTeam);
        saveChanges();
        return true;
    }

    /** Legacy index API only; the dynamic menu purchases by stable resource ID through tryUnlockResearch. */
    public boolean unlockRecipe(int index, ServerPlayer player) {
        if (level == null || level.isClientSide() || player.level() != level || !player.mayBuild() || player.isSpectator()
                || player.containerMenu == null || !(player.containerMenu instanceof StarComputationEngineMenu)
                || !isFormed() || !getMainNode().isActive() || !canInteract(player)) return false;
        if (index < 0 || index >= StarComputationRecipes.NODES.size()) return false;
        bindOwner(player);
        return tryUnlockResearch(player, StarComputationResearchData.LEGACY_NODE_IDS.get(index));
    }

    public boolean canInteract(Player player) {
        if (player.level() != level || isRemoved()) return false;
        if (player.distanceToSqr(worldPosition.getCenter()) <= 64
                || com.atir.molecularmanipulator.terminal.BuildingTerminal.canAccess(player, this)) return true;
        // Distant access is through a real ring member, never a nearby retired column.
        var pos = player.blockPosition();
        for (var member : managedAnchors.keySet()) {
            if (member.distSqr(pos) <= 64 && level.hasChunkAt(member)
                    && ownsStarOrreryMember(member, level.getBlockState(member))) return true;
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
    @Nullable public StarOrreryFoundation.Scan getFoundationScan() { return foundationScan; }
    @Nullable public StarOrreryCoreStructure.Scan getCoreScan() { return coreScan; }

    private static Block coreBlock(StarOrreryCoreStructure.Material material) {
        return material == StarOrreryCoreStructure.Material.CASING
                ? ModContent.STAR_COMPUTATION_ENGINE_CASING.get() : ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get();
    }

    @Nullable
    public static StarComputationEngineCoreBlockEntity findCore(Level level, BlockPos part) {
        if (!level.hasChunkAt(part)) return null;
        var state = level.getBlockState(part);
        if (state.getBlock() instanceof StarComputationEngineCoreBlock) {
            return level.getBlockEntity(part) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isFormed() ? core : null;
        }
        if ((state.getBlock() instanceof StarComputationEngineCasingBlock
                || state.getBlock() instanceof StarComputationObservationPortBlock)
                && state.getValue(StarComputationEngineCoreBlock.FORMED)) {
            for (var member : StarOrreryCoreStructure.members()) {
                if (!state.is(coreBlock(member.material()))) continue;
                var corePos = part.subtract(member.offset());
                if (level.hasChunkAt(corePos)
                        && level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core
                        && core.isFormed()) return core;
            }
            return null;
        }
        return state.getBlock() instanceof StarPositionAnchorBlock
                && state.getValue(StarPositionAnchorBlock.FORMED)
                && state.getValue(StarPositionAnchorBlock.STAR_ORRERY)
                ? findStarOrreryCore(level, part, state) : null;
    }

    /**
     * StarOrrery lookup first trusts the per-level ownership map, then verifies
     * candidate slots without loading any chunk. Facing and ring alone are never
     * enough to claim another engine's member.
     */
    @Nullable
    private static StarComputationEngineCoreBlockEntity findStarOrreryCore(Level level, BlockPos part, BlockState state) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int ring = state.getValue(StarPositionAnchorBlock.ANCHOR_POS);
        if (level instanceof ServerLevel server) {
            var owner = StarOrreryOwnership.owner(server, part);
            if (owner != null) {
                return server.hasChunkAt(owner)
                        && server.getBlockEntity(owner) instanceof StarComputationEngineCoreBlockEntity core
                        && core.ownsStarOrreryMember(part, state) ? core : null;
            }
        }
        for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
            var corePos = part.subtract(StarOrreryLayout.offset(facing, ring, slot));
            if (!level.hasChunkAt(corePos)) continue;
            if (level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core
                    && core.isFormed() && core.isStarOrrery()
                    && core.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing
                    && state.is(starOrreryAnchorFor(ring))
                    && (core.ownsStarOrreryMember(part, state)
                            || level.isClientSide() && core.mayOwnStarOrrerySlot(ring, slot))) {
                return core;
            }
        }
        return null;
    }

    private boolean ownsStarOrreryMember(BlockPos part, BlockState state) {
        var member = managedAnchors.get(part);
        return member != null && isFormed()
                && state.getBlock() instanceof StarPositionAnchorBlock
                && state.getValue(StarPositionAnchorBlock.FORMED) && StarPositionAnchorBlock.isStarOrrery(state)
                && getBlockState().getValue(HorizontalDirectionalBlock.FACING) == member.facing()
                && StarOrreryRings.slot(getBlockPos(), member.facing(), member.index(), member.slot()).equals(part)
                && starOrreryProbe(state, starOrreryAnchorFor(member.index()), member.facing(), member.index(), false)
                        == StarOrreryRings.Probe.MATCH;
    }

    /** Saved legacy formed flags cannot grant capabilities, research access or overclock before migration. */
    public boolean isFormed() {
        return isStarOrrery() && getBlockState().getValue(StarComputationEngineCoreBlock.FORMED)
                && (level != null && level.isClientSide || structureValidated);
    }

    public void serverTick() {
        upgrade.resetRate();
        if (BuildingTransfer.restoring(this)) return;
        if (level.getGameTime() % 20 == 0) refreshRemoteConnection();
        refundInformation();
        if (overclocked && StarComputationNetwork.best(getMainNode().getGrid()) == this) payOverclockForCurrentTick();
        if (overclocked && (!isFormed() || !getMainNode().isActive())) {
            overclocked = false;
            saveChanges();
        }
        if (++tickCounter >= CHECK_INTERVAL || !isStarOrrery() || upgrade.running()) {
            tickCounter = 0;
            refreshStructure();
        }
        tickUpgrade();
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
        if (!(getLevel() instanceof ServerLevel level)) return;

        var state = getBlockState();
        var migrated = migrateCoreState(state);
        if (migrated != state) {
            // No blockstate writes during chunk loading. Retain the AE node,
            // research, refund debt and team; discard all legacy formation credit.
            formed = false;
            litRings = 0;
            pendingLitRings = 0;
            starOrreryValidated = false;
            structureValidated = false;
            overclocked = false;
            needsStateSync = true;
            resetManagedAnchors(level);
            StarOrreryOwnership.release(level, getBlockPos());
            StarOrreryFoundationVisuals.setHidden(level, getBlockPos(), false);
            level.setBlock(getBlockPos(), migrated, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS);
            updateNetworkNeighbors(level, getBlockPos());
            saveChanges();
        }
        structureVisualsDirty = false;
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        coreScan = StarOrreryCoreStructure.scan(getBlockPos(), (pos, material) -> {
            if (!level.hasChunkAt(pos)) return StarOrreryCoreStructure.Probe.UNLOADED;
            var member = level.getBlockState(pos);
            if (!member.is(coreBlock(material))) return StarOrreryCoreStructure.Probe.OTHER;
            if (!member.getValue(StarComputationEngineCoreBlock.FORMED)
                    || !member.getValue(StarComputationEngineCasingBlock.INVISIBLE)) structureVisualsDirty = true;
            return StarOrreryCoreStructure.Probe.MATCH;
        });
        foundationScan = StarOrreryFoundation.scan(getBlockPos(), facing, (pos, material) -> {
            if (!level.hasChunkAt(pos)) return StarOrreryFoundation.Probe.UNLOADED;
            var expected = material == StarOrreryFoundation.Material.PLINTH
                    ? ModContent.STAR_ORRERY_ENGRAVED_PLINTH.get() : ModContent.STAR_ORRERY_GILDED_PLINTH.get();
            var foundation = level.getBlockState(pos);
            if (!foundation.is(expected)) return StarOrreryFoundation.Probe.OTHER;
            if (!foundation.getValue(com.atir.molecularmanipulator.block.StarOrreryFoundationBlock.FORMED)) {
                structureVisualsDirty = true;
            }
            return StarOrreryFoundation.Probe.MATCH;
        });
        boolean knownMissing = coreScan.resolved() && !coreScan.complete()
                || foundationScan.resolved() && !foundationScan.complete();
        if (!knownMissing && (!coreScan.resolved() || !foundationScan.resolved())) {
            // Unknown is not air. Retain recovery tickets and visuals, but never
            // expose membership or benefits before both body and base are verified.
            structureValidated = false;
            if (litRings > 0 || starOrreryValidated) setChanged();
            foundationRecovery = true;
            pendingLitRings = Math.max(pendingLitRings, litRings);
            litRings = 0;
            starOrreryValidated = false;
            syncVisualState();
            MultiblockChunkLoading.maintain(this);
            return;
        }

        boolean complete = coreScan.complete() && foundationScan.complete();
        structureValidated = complete;
        foundationRecovery = false;
        boolean changed = complete != formed;
        if (changed || needsStateSync || isFormed() != complete || complete && structureVisualsDirty) {
            needsStateSync = false;
            formed = complete;
            updateAllBlockStates();
            if (changed) setChanged();
        }
        refreshAnchors(level);
        MultiblockChunkLoading.maintain(this);
    }

    private void refreshAnchors(ServerLevel level) {
        if (!formed) {
            resetManagedAnchors(level);
            StarOrreryOwnership.release(level, getBlockPos());
            litRings = 0;
            pendingLitRings = 0;
            starOrreryValidated = true;
            syncVisualState();
            return;
        }
        refreshStarOrreryAnchors(level);
    }

    /**
     * Strict ring scan plus ownership claim. Only the verified outer prefix is
     * ever formed; an unresolved tail is dropped to the last actually verified
     * ring once the controller has validated at least once since loading.
     */
    private void refreshStarOrreryAnchors(ServerLevel level) {
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var corePos = getBlockPos();
        var scan = StarOrreryRings.scan(corePos, facing, (pos, ring) -> {
            var owner = StarOrreryOwnership.owner(level, pos);
            boolean ownedByOther = owner != null && !owner.equals(corePos) && level.hasChunkAt(owner);
            return starOrreryProbe(StarPositionAnchorBlock.loadedState(level, pos), starOrreryAnchorFor(ring),
                    facing, ring, ownedByOther);
        });
        int pending = Math.clamp(pendingLitRings, 0, StarOrreryRings.RINGS);
        if (!starOrreryValidated && pending > 0 && !scan.resolved() && scan.lit() < pending) {
            // A saved build is still restoring its tickets/chunks. Keep the saved
            // footprint, but do not grant any unverified benefit or dissolve it.
            litRings = 0;
            syncVisualState();
            return;
        }

        int lit = scan.lit();
        claimRings:
        for (int ring = 0; ring < lit; ring++) {
            for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                if (!StarOrreryOwnership.claim(level, corePos,
                        StarOrreryRings.slot(corePos, facing, ring, slot))) {
                    lit = ring;
                    break claimRings;
                }
            }
        }

        var desired = new HashSet<BlockPos>();
        for (int ring = 0; ring < lit; ring++) {
            boolean end = ring == lit - 1;
            for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                var pos = StarOrreryRings.slot(corePos, facing, ring, slot);
                var state = level.getBlockState(pos);
                var updated = StarPositionAnchorBlock.starOrreryState(state, facing, ring, end);
                var member = new AnchorMember(facing, ring, slot);
                var previousMember = managedAnchors.put(pos, member);
                if (updated != state) {
                    level.setBlock(pos, updated, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS);
                }
                // Publish verified membership before notifying cables. Reloaded
                // formed states also need a notification once ownership is rebuilt.
                if (updated != state || !member.equals(previousMember)) updateNetworkNeighbors(level, pos);
                StarPositionAnchorBlock.scheduleIntegrityCheck(level, pos, updated);
                desired.add(pos);
            }
        }

        var previous = managedAnchors.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            if (!desired.contains(entry.getKey())
                    && releaseManagedStarOrrery(level, entry.getKey(), entry.getValue())) {
                previous.remove();
            }
        }
        StarOrreryOwnership.retain(level, corePos, desired);
        if (litRings != lit || pendingLitRings != 0 || !starOrreryValidated) setChanged();
        litRings = lit;
        pendingLitRings = 0;
        starOrreryValidated = true;
        if (scan.resolved() && upgrade.migrateVerified(lit)) saveChanges();
        syncVisualState();
    }

    /** Expected anchor tier for a ring, independent of the block's tier display. */
    static StarOrreryRings.Probe starOrreryProbe(@Nullable BlockState state, Block expected,
            Direction facing, int ring, boolean ownedByOther) {
        if (state == null) {
            return StarOrreryRings.Probe.UNLOADED;
        }
        if (!state.is(expected)) {
            return StarOrreryRings.Probe.OTHER;
        }
        if (state.getValue(StarPositionAnchorBlock.FORMED)) {
            if (!state.getValue(StarPositionAnchorBlock.STAR_ORRERY)
                    || state.getValue(HorizontalDirectionalBlock.FACING) != facing
                    || state.getValue(StarPositionAnchorBlock.ANCHOR_POS) != ring
                    || ownedByOther) {
                return StarOrreryRings.Probe.OTHER;
            }
        } else if (ownedByOther) {
            return StarOrreryRings.Probe.OTHER;
        }
        return StarOrreryRings.Probe.MATCH;
    }

    static Block starOrreryAnchorFor(int ring) {
        return switch (StarOrreryRings.tierOf(ring)) {
            case 0 -> ModContent.REFERENCE_STAR_POSITION_ANCHOR.get();
            case 1 -> ModContent.QUANTUM_STAR_POSITION_ANCHOR.get();
            default -> ModContent.TERMINAL_STAR_POSITION_ANCHOR.get();
        };
    }

    /**
     * Releases one star_orrery slot. When its chunk is unloaded the membership is
     * still dropped (unverified state is not kept), and the anchor's own local
     * integrity check will unform the stale block when that chunk returns.
     */
    private boolean releaseManagedStarOrrery(ServerLevel level, BlockPos pos, AnchorMember member) {
        var owner = StarOrreryOwnership.owner(level, pos);
        if (owner != null && !owner.equals(getBlockPos())) {
            return true;
        }
        var state = StarPositionAnchorBlock.loadedState(level, pos);
        if (state != null && state.getBlock() instanceof StarPositionAnchorBlock
                && state.getValue(StarPositionAnchorBlock.FORMED)
                && state.getValue(StarPositionAnchorBlock.STAR_ORRERY)
                && state.getValue(HorizontalDirectionalBlock.FACING) == member.facing()
                && state.getValue(StarPositionAnchorBlock.ANCHOR_POS) == member.index()) {
            level.setBlock(pos, StarPositionAnchorBlock.unformedState(state), StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS);
            updateNetworkNeighbors(level, pos);
        }
        StarOrreryOwnership.releaseIfOwner(level, pos, getBlockPos());
        return true;
    }

    private void syncVisualState() {
        int nextTier = 0;
        int nextLit = 0;
        if (isFormed() && starOrreryValidated) {
            nextLit = Math.clamp(litRings, 0, StarOrreryRings.RINGS);
            int effective = getEffectiveLevel();
            nextTier = effective == 0 ? 0 : StarOrreryRings.tierOf(effective - 1) + 1;
        }
        int nextActivity = 0;
        if (nextLit > 0 && getMainNode().isActive()
                && StarComputationNetwork.best(getMainNode().getGrid()) == this) {
            nextActivity = overclocked && level != null && paidTick == level.getGameTime() ? 2 : 1;
        }
        if (visualTier != nextTier || syncedLitRings != nextLit || visualActivity != nextActivity) {
            visualTier = nextTier;
            syncedLitRings = nextLit;
            visualActivity = nextActivity;
            markForClientUpdate();
        }
    }

    private void resetManagedAnchors(Level level) {
        if (!(level instanceof ServerLevel server)) return;
        var previous = managedAnchors.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            if (releaseManagedStarOrrery(server, entry.getKey(), entry.getValue())) previous.remove();
        }
    }

    /** Hide verified central meshes for the orrery renderer without removing their blocks. */
    private void updateAllBlockStates() {
        var level = getLevel();
        if (level == null) {
            return;
        }

        if (level instanceof ServerLevel server) {
            StarOrreryFoundationVisuals.setHidden(server, getBlockPos(), isStarOrrery() && formed);
        }
        applyFormed(level, getBlockPos(), formed);
        for (var member : StarOrreryCoreStructure.members()) {
            var pos = getBlockPos().offset(member.offset());
            if (!level.hasChunkAt(pos)) continue;
            var block = level.getBlockState(pos).getBlock();
            if (block instanceof StarComputationEngineCasingBlock || block instanceof StarComputationObservationPortBlock) {
                applyFormed(level, pos, formed);
            }
        }
    }

    /** Restores loaded visual members without removing or refunding any player blocks. */
    public static void resetShell(Level level, BlockPos corePos) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(corePos) instanceof StarComputationEngineCoreBlockEntity core) {
            core.resetManagedAnchors(level);
        }
        if (level instanceof ServerLevel server) {
            StarOrreryOwnership.release(server, corePos);
            StarOrreryFoundationVisuals.setHidden(server, corePos, false);
        }
        restoreLegacyShell(level, corePos);
    }

    /** Used when the owning core is removed; no materials are deleted or refunded. */
    private static void restoreLegacyShell(Level level, BlockPos corePos) {
        for (var pos : BlockPos.betweenClosed(corePos.offset(-1, -1, -1), corePos.offset(1, 1, 1))) {
            if (level.hasChunkAt(pos)) restoreLegacyMember(level, pos);
        }
    }

    /** Saved shell flags are repaired only after a potential owner's state is known. */
    private static boolean deferShellRepair(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StarComputationEngineCasingBlock
                || state.getBlock() instanceof StarComputationObservationPortBlock)) return false;
        for (var member : StarOrreryCoreStructure.members()) {
            if (!state.is(coreBlock(member.material()))) continue;
            var candidate = pos.subtract(member.offset());
            if (!level.hasChunkAt(candidate)) return true;
            if (level.getBlockEntity(candidate) instanceof StarComputationEngineCoreBlockEntity core
                    && (core.isFormed() || core.needsStateSync || core.foundationRecovery)) return true;
        }
        return false;
    }

    private static void restoreLegacyMember(Level level, BlockPos pos) {
        var restored = restoredLegacyState(level.getBlockState(pos));
        if (restored != null) {
            level.setBlock(pos, restored, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS);
            updateNetworkNeighbors(level, pos);
        }
    }

    @SubscribeEvent
    public static void legacyChunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Chunk promotion can run before block entities are installed. Do not
        // rewrite any states until the level's safe post-tick phase.
        synchronized (LEGACY_VISUAL_CHUNKS) {
            LEGACY_VISUAL_CHUNKS.computeIfAbsent(level, ignored -> new HashSet<>()).add(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void restoreLegacyChunks(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<ChunkPos> chunks;
        synchronized (LEGACY_VISUAL_CHUNKS) {
            chunks = LEGACY_VISUAL_CHUNKS.remove(level);
        }
        if (chunks == null) return;
        for (var chunkPos : chunks) {
            var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) continue;
            var sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                var section = sections[sectionIndex];
                if (!section.maybeHas(state -> restoredLegacyState(state) != null)) continue;
                int baseY = level.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (restoredLegacyState(state) != null) {
                        var pos = chunkPos.getBlockAt(x, baseY + y, z);
                        if (!deferShellRepair(level, pos, state)) restoreLegacyMember(level, pos);
                    }
                }
            }
        }
    }

    private static void applyFormed(Level level, BlockPos pos, boolean formed) {
        if (!level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        BlockState updated = withFormed(state, formed);
        if (updated != null) level.setBlock(pos, updated, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS);
        // Also reconnect cached capabilities after reload when saved visual flags already match.
        updateNetworkNeighbors(level, pos);
    }

    private static void updateNetworkNeighbors(Level level, BlockPos pos) {
        level.invalidateCapabilities(pos);
        for (var direction : Direction.values()) {
            var adjacent = pos.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            var block = level.getBlockState(adjacent).getBlock();
            if (!(block instanceof StarPositionAnchorBlock || block instanceof StarComputationEngineCasingBlock
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
        boolean core = state.getBlock() instanceof StarComputationEngineCoreBlock;
        BlockState updated = state.setValue(StarComputationEngineCoreBlock.FORMED,
                formed && (!core || StarComputationEngineCoreBlock.isStarOrrery(state)));
        if (!core) updated = updated.setValue(StarComputationEngineCasingBlock.INVISIBLE, formed);
        return updated == state ? null : updated;
    }

    static BlockState migrateCoreState(BlockState state) {
        return StarComputationEngineCoreBlock.isStarOrrery(state) ? state
                : state.setValue(StarComputationEngineCoreBlock.STAR_ORRERY, true)
                        .setValue(StarComputationEngineCoreBlock.FORMED, false);
    }

    @Nullable
    static BlockState restoredLegacyState(BlockState state) {
        if (state.getBlock() instanceof StarPositionAnchorBlock
                && state.getValue(StarPositionAnchorBlock.FORMED)
                && !StarPositionAnchorBlock.isStarOrrery(state)) {
            return StarPositionAnchorBlock.unformedState(state);
        }
        return state.getBlock() instanceof StarComputationEngineCasingBlock
                || state.getBlock() instanceof StarComputationObservationPortBlock ? withFormed(state, false) : null;
    }

    /** BlockItem placement and AE2 dismantling both restore only the upgrade/owner payload, never saved formation. */
    public void importUpgradeItem(net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        wirelessLink.load(tag);
        if (!tag.contains(StarComputationUpgradeProgress.TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        upgrade.load(tag);
        if (tag.hasUUID("entropy_team")) boundTeam = tag.getUUID("entropy_team");
        saveChanges();
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            var tag = new CompoundTag();
            tag.put(StarComputationUpgradeProgress.TAG, upgrade.save());
            wirelessLink.save(tag);
            if (boundTeam != null) tag.putUUID("entropy_team", boundTeam);
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap data, Player player) {
        super.importSettings(mode, data, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM && level != null && !level.isClientSide()) {
            var tag = data.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            wirelessLink.load(tag);
            if (tag.contains(StarComputationUpgradeProgress.TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                upgrade.load(tag);
                if (tag.hasUUID("entropy_team")) boundTeam = tag.getUUID("entropy_team");
                saveChanges();
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        wirelessLink.save(tag);
        tag.putBoolean("formed", formed);
        tag.putBoolean("entropy_overclocked", overclocked);
        tag.putInt("entropy_unlocks", legacyUnlockMask);
        tag.putLong("entropy_refund", pendingInformationRefund);
        tag.putInt("entropy_visual_tier", visualTier);
        tag.putInt("star_orrery_lit_rings", litRings);
        tag.putInt("star_orrery_pending_rings", pendingLitRings);
        tag.putInt("star_orrery_layout_version", STAR_ORRERY_LAYOUT_VERSION);
        tag.put(StarComputationUpgradeProgress.TAG, upgrade.save());
        if (boundTeam != null) tag.putUUID("entropy_team", boundTeam);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(visualTier);
        data.writeByte(0); // Retired shell connection mask; retain the update-stream layout.
        data.writeByte(syncedLitRings);
        data.writeByte(visualActivity);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int nextTier = Math.clamp(data.readUnsignedByte(), 0, 3);
        data.readUnsignedByte(); // Retired shell connection mask.
        int nextLit = Math.clamp(data.readUnsignedByte(), 0, StarOrreryRings.RINGS);
        int nextActivity = Math.clamp(data.readUnsignedByte(), 0, 2);
        changed |= visualTier != nextTier || litRings != nextLit || visualActivity != nextActivity;
        visualTier = nextTier;
        litRings = nextLit;
        syncedLitRings = nextLit;
        visualActivity = nextActivity;
        starOrreryValidated = true;
        return changed;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        wirelessLink.load(tag);
        super.loadTag(tag, registries);
        formed = tag.getBoolean("formed");
        upgrade.load(tag);
        upgradeStatus = upgrade.running() ? "restoring" : upgrade.targetLevel() != 0 ? "paused" : "idle";
        overclocked = tag.getBoolean("entropy_overclocked");
        legacyUnlockMask = tag.getInt("entropy_unlocks") & StarComputationRecipes.ALL_UNLOCKS;
        pendingInformationRefund = Math.max(0, tag.getLong("entropy_refund"));
        visualTier = 0;
        boolean currentLayout = tag.getInt("star_orrery_layout_version") == STAR_ORRERY_LAYOUT_VERSION;
        int savedLit = currentLayout
                ? Math.clamp(tag.getInt("star_orrery_lit_rings"), 0, StarOrreryRings.RINGS) : 0;
        int savedPending = currentLayout
                ? Math.clamp(tag.getInt("star_orrery_pending_rings"), 0, StarOrreryRings.RINGS) : 0;
        // Saved rings are only a ticket-recovery hint. They never become a
        // benefit until this server has scanned them again.
        litRings = 0;
        pendingLitRings = Math.max(savedLit, savedPending);
        starOrreryValidated = false;
        foundationScan = null;
        coreScan = null;
        structureValidated = false;
        foundationRecovery = false;
        syncedLitRings = 0;
        visualActivity = 0;
        boundTeam = tag.hasUUID("entropy_team") ? tag.getUUID("entropy_team") : null;
    }
}
