package com.atir.molecularmanipulator.menu;

import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipes;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes.Node;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Live engine telemetry and server-authoritative controls; the core owns every payment. */
public final class StarComputationEngineMenu extends BuildingMenu {
    private static final String ACTION_OVERCLOCK = "entropy_overclock";
    private static final String ACTION_REFRESH = "entropy_refresh";
    private static final String ACTION_UNLOCK = "entropy_unlock";
    private static final String ACTION_RECLAIM = "entropy_reclaim";
    private static final String ACTION_START_UPGRADE = "orrery_upgrade_start";
    private static final String ACTION_PAUSE_UPGRADE = "orrery_upgrade_pause";
    public static final int DEFAULT_PROCESSING_TICKS = 20 * 20;

    public static final MenuType<StarComputationEngineMenu> TYPE = ForgeMenuTypeFactory.create(
            MolecularManipulator.id("star_computation_engine"), StarComputationEngineMenu::new,
            StarComputationEngineCoreBlockEntity.class,
            core -> Component.translatable("block.molecularmanipulator.star_computation_engine_core"));

    /** Legacy five-node view for source compatibility; runtime definitions and order come from nodes(). */
    public record RecipeNode(int index, String id, long entropy, long informationCost, int prerequisiteMask) {
        public String translationKey() { return "gui.molecularmanipulator.star_computation.node." + id; }
        public boolean unlocked(int mask) { return (mask & (1 << index)) != 0; }
        public boolean prerequisitesMet(int mask) { return (mask & prerequisiteMask) == prerequisiteMask; }
        public List<ResourceLocation> recipeIds() { return StarComputationRecipes.recipeIds(index); }
    }

    public static final List<RecipeNode> NODES = StarComputationRecipes.NODES.stream()
            .map(node -> new RecipeNode(node.index(), node.id(), node.entropy(), node.informationCost(),
                    node.prerequisiteMask())).toList();
    public static final int ALL_UNLOCKS = StarComputationRecipes.ALL_UNLOCKS;

    public enum ActionResult {
        NONE, OVERCLOCK_ENABLED, OVERCLOCK_DISABLED, STRUCTURE_REFRESHED, UNLOCKED, ALREADY_UNLOCKED,
        INVALID_NODE, STRUCTURE_INCOMPLETE, OFFLINE, INSUFFICIENT_ENTROPY, MISSING_PREREQUISITES,
        INSUFFICIENT_INFORMATION, NOT_PERMITTED, REJECTED, UPGRADE_STARTED, UPGRADE_PAUSED
    }

    @GuiSync(0) public boolean formed;
    @GuiSync(1) public boolean networkOnline;
    @GuiSync(2) public long effectiveEntropy;
    @GuiSync(3) public int anchorCount;
    @GuiSync(4) public int benefitLevel;
    @GuiSync(5) public boolean benefitsOverclocked;
    @GuiSync(6) public long infoBalance;
    @GuiSync(7) public long infoConsumption;
    @GuiSync(8) public boolean overclocked;
    @GuiSync(9) public int unlockMask;
    @GuiSync(10) public ActionResult actionResult = ActionResult.NONE;
    @GuiSync(11) public int resultNode = -1;
    @GuiSync(12) public int resultVersion;
    @GuiSync(13) public boolean canConfigure;
    @GuiSync(14) public StarComputationRecipeCatalog catalog = StarComputationRecipeCatalog.EMPTY;
    @GuiSync(15) public boolean starOrrery;
    @GuiSync(16) public int litRings;
    @GuiSync(17) public int anchorsReference;
    @GuiSync(18) public int anchorsQuantum;
    @GuiSync(19) public int anchorsTerminal;
    @GuiSync(20) public boolean reclaimArmed;
    @GuiSync(21) public StarComputationUpgradeCatalog upgrades = StarComputationUpgradeCatalog.EMPTY;
    @GuiSync(22) public int certifiedLevel = 3;
    @GuiSync(23) public int upgradeTarget;
    @GuiSync(24) public double upgradeEnergy;
    @GuiSync(25) public boolean upgradeRunning;
    @GuiSync(26) public double upgradeRate;
    @GuiSync(27) public String upgradeStatus = "idle";
    @GuiSync(28) public StarResearchMenuNodes researchNodes = StarResearchMenuNodes.FALLBACK;

    private int upgradeCatalogTicks;
    private ResourceLocation resultResearchId;
    private long reclaimArmedAt = Long.MIN_VALUE;
    private final StarComputationRecipeCatalog.ServerCache recipeCatalog = new StarComputationRecipeCatalog.ServerCache();
    private final StarComputationEngineCoreBlockEntity core;

    private StarComputationEngineMenu(int id, Inventory playerInventory, StarComputationEngineCoreBlockEntity core) {
        super(TYPE, id, playerInventory, core);
        this.core = core;
        createPlayerInventorySlots(playerInventory);
        var inventory = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.get(i).x = 23 + i % 9 * 18;
            inventory.get(i).y = 213 + i / 9 * 18;
        }
        var hotbar = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int i = 0; i < hotbar.size(); i++) {
            hotbar.get(i).x = 23 + i * 18;
            hotbar.get(i).y = 271;
        }
        registerClientAction(ACTION_OVERCLOCK, this::toggleOverclock);
        registerClientAction(ACTION_REFRESH, this::refreshStructure);
        registerClientAction(ACTION_UNLOCK, String.class, this::unlockRecipe);
        registerClientAction(ACTION_RECLAIM, this::reclaimPackage);
        registerClientAction(ACTION_START_UPGRADE, Integer.class, this::startUpgrade);
        registerClientAction(ACTION_PAUSE_UPGRADE, this::pauseUpgrade);
    }

    public StarComputationBenefits benefits() {
        return new StarComputationBenefits(com.atir.molecularmanipulator.util.MathCompat.clamp(benefitLevel, 0, 9), benefitsOverclocked);
    }

    public int levelThreshold(int level) {
        return starOrrery ? StarOrreryLayout.threshold(level) : StarComputationBenefits.threshold(level);
    }

    public List<Node> nodes() {
        return researchNodes.nodes();
    }

    public Node node(int index) { return nodes().get(index); }

    public int indexOf(ResourceLocation id) {
        for (var node : nodes()) if (node.id().equals(id)) return node.index();
        return -1;
    }

    public long nodeThreshold(Node node) { return levelThreshold(node.requiredLevel()); }

    /** Legacy definitions identify stable IDs, not positions in the current list. Removed definitions have no gate. */
    public long nodeThreshold(RecipeNode node) {
        int index = indexOf(MolecularManipulator.id(node.id()));
        return index < 0 ? 0 : nodeThreshold(node(index));
    }

    public boolean canUnlock(RecipeNode node) {
        int index = indexOf(MolecularManipulator.id(node.id()));
        return index >= 0 && canUnlock(node(index));
    }

    public boolean canUnlock(Node node) { return unlockStatus(node) == ActionResult.NONE; }

    public ActionResult unlockStatus(Node node) {
        return unlockStatus(node, canConfigure, formed, networkOnline, benefitLevel, infoBalance);
    }

    /** Compatibility only: legacy masks are interpreted by ID, never as transient research-list indices. */
    public static ActionResult unlockStatus(Node node, boolean permitted, boolean formed, boolean online,
            int actualLevel, long information, int permanentMask) {
        if (node.unlocked(permanentMask)) return ActionResult.ALREADY_UNLOCKED;
        return unlockStatus(node, permitted, formed, online, actualLevel, information);
    }

    /** Shared display/preflight reasons. Only fresh server values may be used before calling the core purchase API. */
    public static ActionResult unlockStatus(Node node, boolean permitted, boolean formed, boolean online,
            int actualLevel, long information) {
        if (node.unlocked()) return ActionResult.ALREADY_UNLOCKED;
        if (!permitted) return ActionResult.NOT_PERMITTED;
        if (!node.valid()) return ActionResult.INVALID_NODE;
        if (!formed) return ActionResult.STRUCTURE_INCOMPLETE;
        if (!online) return ActionResult.OFFLINE;
        if (actualLevel < node.requiredLevel()) return ActionResult.INSUFFICIENT_ENTROPY;
        if (!node.prerequisitesMet()) return ActionResult.MISSING_PREREQUISITES;
        if (information < node.informationCost()) return ActionResult.INSUFFICIENT_INFORMATION;
        return ActionResult.NONE;
    }

    public boolean canToggleOverclock() {
        return canConfigure && (overclocked || formed && networkOnline && benefitLevel > 0
                && infoBalance >= new StarComputationBenefits(benefitLevel, true).infoPerTick());
    }

    public void requestToggleOverclock() {
        if (isClientSide()) sendClientAction(ACTION_OVERCLOCK);
    }

    public void requestRefresh() {
        if (isClientSide()) sendClientAction(ACTION_REFRESH);
    }

    /** Convenience for existing callers. Resolve the synced list now, then send only its stable node ID. */
    public void requestUnlock(int index) {
        if (isClientSide() && index >= 0 && index < nodes().size()) requestUnlock(node(index).id());
    }

    public void requestUnlock(ResourceLocation id) {
        if (isClientSide() && id != null) sendClientAction(ACTION_UNLOCK, id.toString());
    }

    public void requestReclaim() {
        if (isClientSide()) sendClientAction(ACTION_RECLAIM);
    }

    public void requestStartUpgrade(int level) {
        if (isClientSide() && level >= 1 && level <= 9) sendClientAction(ACTION_START_UPGRADE, level);
    }

    public void requestPauseUpgrade() {
        if (isClientSide()) sendClientAction(ACTION_PAUSE_UPGRADE);
    }

    private void startUpgrade(Integer level) {
        if (!isServerSide()) return;
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, -1); return; }
        upgradeCatalogTicks = 0;
        boolean started = level != null && core.startUpgrade(level, (ServerPlayer) getPlayer());
        finish(started ? ActionResult.UPGRADE_STARTED : ActionResult.REJECTED, -1);
    }

    private void pauseUpgrade() {
        if (!isServerSide()) return;
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, -1); return; }
        finish(core.pauseUpgrade() ? ActionResult.UPGRADE_PAUSED : ActionResult.REJECTED, -1);
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        // Formation gates opening and productive actions, not an already-open diagnostic view.
        // Keep it visible while nearby so damage, offline state and repairs can synchronize.
        return super.stillValid(player) && core.canInteract(player);
    }

    private boolean permitted() {
        return isServerSide() && getPlayer() instanceof ServerPlayer player && player.containerMenu == this
                && isValidMenu() && player.mayBuild() && !player.isSpectator() && !core.isRemoved()
                && player.level() == core.getLevel() && core.getLevel() != null
                && core.getLevel().hasChunkAt(core.getBlockPos())
                && core.getLevel().getBlockEntity(core.getBlockPos()) == core && stillValid(player)
                && core.canInteract(player)
                && player.level().mayInteract(player, core.getBlockPos());
    }

    private void reclaimPackage() {
        if (!isServerSide()) return;
        if (!permitted()) {
            reclaimArmedAt = Long.MIN_VALUE;
            finish(ActionResult.NOT_PERMITTED, -1);
            return;
        }
        long now = core.getLevel().getGameTime();
        if (reclaimArmedAt == Long.MIN_VALUE || now < reclaimArmedAt || now - reclaimArmedAt > 60) {
            reclaimArmedAt = now;
            finish(ActionResult.NONE, -1);
            return;
        }
        // Two distinct server requests, at least five ticks apart, within three seconds.
        if (now - reclaimArmedAt < 5) {
            finish(ActionResult.NONE, -1);
            return;
        }
        reclaimArmedAt = Long.MIN_VALUE;
        reclaimArmed = false;
        // seal refreshes the runtime level and owns the exact package, native contents and menu close.
        if (BuildingTransfer.seal((ServerPlayer) getPlayer(), core)) return;
        finish(ActionResult.REJECTED, -1);
    }

    private void toggleOverclock() {
        if (!isServerSide()) return;
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, -1); return; }
        // Switching off remains possible after the structure or ME connection fails.
        boolean wasOverclocked = core.isOverclocked();
        if (!wasOverclocked) {
            if (!core.isFormed()) { finish(ActionResult.STRUCTURE_INCOMPLETE, -1); return; }
            if (!core.getMainNode().isActive()) { finish(ActionResult.OFFLINE, -1); return; }
            int level = core.getBenefits().level();
            if (level == 0) { finish(ActionResult.INSUFFICIENT_ENTROPY, -1); return; }
            if (core.getInfoBalance() < new StarComputationBenefits(level, true).infoPerTick()) {
                finish(ActionResult.INSUFFICIENT_INFORMATION, -1); return;
            }
        }
        core.toggleOverclock();
        finish(core.isOverclocked() == wasOverclocked ? ActionResult.REJECTED
                : core.isOverclocked() ? ActionResult.OVERCLOCK_ENABLED : ActionResult.OVERCLOCK_DISABLED, -1);
    }

    private void refreshStructure() {
        if (!isServerSide()) return;
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, -1); return; }
        core.refreshStructureNow();
        finish(ActionResult.STRUCTURE_REFRESHED, -1);
    }

    private void unlockRecipe(String name) {
        if (!isServerSide()) return;
        var id = name == null ? null : ResourceLocation.tryParse(name);
        if (id == null) { finish(ActionResult.INVALID_NODE, -1); return; }
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, -1); return; }
        // Re-resolve the reload-aware directory and this bound team's full permanent set on EVERY request.
        // Never preflight against synchronized researchNodes, client eligibility, or network-borrowed research.
        var current = StarResearchMenuNodes.from(StarResearchNodes.catalog(core.getLevel()), core.getUnlockedResearchIds());
        int index = current.indexOf(id);
        if (index < 0) { finish(ActionResult.INVALID_NODE, -1); return; }
        var node = current.node(index);
        var status = unlockStatus(node, true, core.isFormed(), core.getMainNode().isActive(),
                core.getBenefits().level(), core.getInfoBalance());
        if (status != ActionResult.NONE) { finish(status, id); return; }
        // The core still owns permission, current graph validation, exact extraction and permanent grants.
        // A reload can reorder the list, but can never redirect this request to the new occupant of an old slot.
        boolean paid = core.tryUnlockResearch((ServerPlayer) getPlayer(), id);
        finish(paid && core.getUnlockedResearchIds().contains(id) ? ActionResult.UNLOCKED : ActionResult.REJECTED, id);
    }

    private void finish(ActionResult result, int node) {
        actionResult = result;
        resultResearchId = null;
        resultNode = node;
        resultVersion++;
        broadcastChanges();
    }

    private void finish(ActionResult result, ResourceLocation id) {
        actionResult = result;
        resultResearchId = id;
        resultVersion++;
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            formed = core.isFormed();
            starOrrery = core.isStarOrrery();
            litRings = core.getLitRings();
            networkOnline = core.getMainNode().isActive();
            effectiveEntropy = core.getEffectiveEntropy();
            anchorCount = core.getAnchorCount();
            anchorsReference = core.getAnchorCount(0);
            anchorsQuantum = core.getAnchorCount(1);
            anchorsTerminal = core.getAnchorCount(2);
            var benefits = core.getBenefits();
            benefitLevel = benefits.level();
            benefitsOverclocked = benefits.overclocked();
            infoBalance = core.getInfoBalance();
            infoConsumption = core.getInfoConsumption();
            overclocked = core.isOverclocked();
            unlockMask = core.getUnlockMask() & ALL_UNLOCKS;
            var directory = StarResearchNodes.catalog(core.getLevel());
            researchNodes = StarResearchMenuNodes.from(directory, core.getUnlockedResearchIds());
            // Retained feedback follows its ID through subsequent reloads, rather than naming another list entry.
            if (resultResearchId != null) resultNode = researchNodes.indexOf(resultResearchId);
            certifiedLevel = core.getCertifiedLevel();
            upgradeTarget = core.getUpgradeTarget();
            upgradeEnergy = core.getUpgradeEnergy();
            upgradeRunning = core.isUpgradeRunning();
            upgradeRate = core.getUpgradeRate();
            upgradeStatus = core.getUpgradeStatus();
            if (--upgradeCatalogTicks <= 0) {
                upgrades = StarComputationUpgrades.catalog(core, (ServerPlayer) getPlayer());
                upgradeCatalogTicks = 10;
            }
            canConfigure = permitted();
            if (!canConfigure || reclaimArmedAt != Long.MIN_VALUE
                    && core.getLevel().getGameTime() - reclaimArmedAt > 60) reclaimArmedAt = Long.MIN_VALUE;
            reclaimArmed = reclaimArmedAt != Long.MIN_VALUE;
            var level = core.getLevel();
            catalog = recipeCatalog.get(level, directory, level.getGameTime());
        }
        super.broadcastChanges();
    }
}
