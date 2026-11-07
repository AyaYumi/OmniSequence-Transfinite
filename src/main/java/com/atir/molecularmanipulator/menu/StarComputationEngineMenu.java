package com.atir.molecularmanipulator.menu;

import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipes;
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
    public static final int DEFAULT_PROCESSING_TICKS = 20 * 20;

    public static final MenuType<StarComputationEngineMenu> TYPE = MenuTypeBuilder
            .create(StarComputationEngineMenu::new, StarComputationEngineCoreBlockEntity.class)
            .withMenuTitle(core -> Component.translatable("block.molecularmanipulator.star_computation_engine_core"))
            .buildUnregistered(MolecularManipulator.id("star_computation_engine"));

    /** Compatibility view for the existing Screen; costs and save bits come from the shared definition. */
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
        INSUFFICIENT_INFORMATION, NOT_PERMITTED, REJECTED
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
        registerClientAction(ACTION_UNLOCK, Integer.class, this::unlockRecipe);
        registerClientAction(ACTION_RECLAIM, this::reclaimPackage);
    }

    public StarComputationBenefits benefits() {
        return new StarComputationBenefits(Math.clamp(benefitLevel, 0, 9), benefitsOverclocked);
    }

    public int levelThreshold(int level) {
        return starOrrery ? StarOrreryLayout.threshold(level) : StarComputationBenefits.threshold(level);
    }

    public long nodeThreshold(RecipeNode node) {
        if (!starOrrery) return node.entropy();
        return StarOrreryLayout.threshold(switch (node.index()) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 7;
            case 4 -> 9;
            default -> 0;
        });
    }

    public boolean canUnlock(RecipeNode node) {
        return canConfigure && formed && networkOnline && !node.unlocked(unlockMask)
                && effectiveEntropy >= nodeThreshold(node) && node.prerequisitesMet(unlockMask)
                && infoBalance >= node.informationCost();
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

    public void requestUnlock(int index) {
        if (isClientSide() && index >= 0 && index < NODES.size()) sendClientAction(ACTION_UNLOCK, index);
    }

    public void requestReclaim() {
        if (isClientSide()) sendClientAction(ACTION_RECLAIM);
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

    private void unlockRecipe(Integer index) {
        if (!isServerSide()) return;
        if (index == null || index < 0 || index >= NODES.size()) { finish(ActionResult.INVALID_NODE, -1); return; }
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, index); return; }
        var node = NODES.get(index);
        // Read the core again, never trust the client's displayed balance, mask or eligibility.
        int mask = core.getUnlockMask();
        if (node.unlocked(mask)) { finish(ActionResult.ALREADY_UNLOCKED, index); return; }
        if (!core.isFormed()) { finish(ActionResult.STRUCTURE_INCOMPLETE, index); return; }
        if (!core.getMainNode().isActive()) { finish(ActionResult.OFFLINE, index); return; }
        if (core.getEffectiveEntropy() < core.getResearchEntropyThreshold(index)) { finish(ActionResult.INSUFFICIENT_ENTROPY, index); return; }
        if (!node.prerequisitesMet(mask)) { finish(ActionResult.MISSING_PREREQUISITES, index); return; }
        if (core.getInfoBalance() < node.informationCost()) { finish(ActionResult.INSUFFICIENT_INFORMATION, index); return; }
        boolean paid = core.unlockRecipe(index, (ServerPlayer) getPlayer());
        finish(paid && node.unlocked(core.getUnlockMask()) ? ActionResult.UNLOCKED : ActionResult.REJECTED, index);
    }

    private void finish(ActionResult result, int node) {
        actionResult = result;
        resultNode = node;
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
            canConfigure = permitted();
            if (!canConfigure || reclaimArmedAt != Long.MIN_VALUE
                    && core.getLevel().getGameTime() - reclaimArmedAt > 60) reclaimArmedAt = Long.MIN_VALUE;
            reclaimArmed = reclaimArmedAt != Long.MIN_VALUE;
            var level = getPlayer().level();
            catalog = recipeCatalog.get(level.getRecipeManager(), level.registryAccess(), level.getGameTime());
        }
        super.broadcastChanges();
    }
}
