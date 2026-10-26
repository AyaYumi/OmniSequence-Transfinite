package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.NegativeEntropyEngineCoreBlockEntity;
import com.atir.molecularmanipulator.entropy.NegativeEntropyBenefits;
import com.atir.molecularmanipulator.entropy.NegativeEntropyRecipeCatalog;
import com.atir.molecularmanipulator.entropy.NegativeEntropyRecipes;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Live engine telemetry and server-authoritative controls; the core owns every payment. */
public final class NegativeEntropyEngineMenu extends AEBaseMenu {
    private static final String ACTION_OVERCLOCK = "entropy_overclock";
    private static final String ACTION_REFRESH = "entropy_refresh";
    private static final String ACTION_UNLOCK = "entropy_unlock";
    public static final int DEFAULT_PROCESSING_TICKS = 20 * 20;

    public static final MenuType<NegativeEntropyEngineMenu> TYPE = MenuTypeBuilder
            .create(NegativeEntropyEngineMenu::new, NegativeEntropyEngineCoreBlockEntity.class)
            .withMenuTitle(core -> Component.translatable("block.molecularmanipulator.negative_entropy_engine_core"))
            .buildUnregistered(MolecularManipulator.id("negative_entropy_engine"));

    /** Compatibility view for the existing Screen; costs and save bits come from the shared definition. */
    public record RecipeNode(int index, String id, long entropy, long informationCost, int prerequisiteMask) {
        public String translationKey() { return "gui.molecularmanipulator.entropy.node." + id; }
        public boolean unlocked(int mask) { return (mask & (1 << index)) != 0; }
        public boolean prerequisitesMet(int mask) { return (mask & prerequisiteMask) == prerequisiteMask; }
        public List<ResourceLocation> recipeIds() { return NegativeEntropyRecipes.recipeIds(index); }
    }

    public static final List<RecipeNode> NODES = NegativeEntropyRecipes.NODES.stream()
            .map(node -> new RecipeNode(node.index(), node.id(), node.entropy(), node.informationCost(),
                    node.prerequisiteMask())).toList();
    public static final int ALL_UNLOCKS = NegativeEntropyRecipes.ALL_UNLOCKS;

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
    @GuiSync(14) public NegativeEntropyRecipeCatalog catalog = NegativeEntropyRecipeCatalog.EMPTY;

    private final NegativeEntropyRecipeCatalog.ServerCache recipeCatalog = new NegativeEntropyRecipeCatalog.ServerCache();
    private final NegativeEntropyEngineCoreBlockEntity core;

    private NegativeEntropyEngineMenu(int id, Inventory playerInventory, NegativeEntropyEngineCoreBlockEntity core) {
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
    }

    public NegativeEntropyBenefits benefits() {
        return new NegativeEntropyBenefits(Math.clamp(benefitLevel, 0, 9), benefitsOverclocked);
    }

    public boolean canUnlock(RecipeNode node) {
        return canConfigure && formed && networkOnline && !node.unlocked(unlockMask)
                && effectiveEntropy >= node.entropy() && node.prerequisitesMet(unlockMask)
                && infoBalance >= node.informationCost();
    }

    public boolean canToggleOverclock() {
        return canConfigure && (overclocked || formed && networkOnline && benefitLevel > 0
                && infoBalance >= new NegativeEntropyBenefits(benefitLevel, true).infoPerTick());
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

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        // Formation gates opening and productive actions, not an already-open diagnostic view.
        // Keep it visible while nearby so damage, offline state and repairs can synchronize.
        return core.canInteract(player);
    }

    private boolean permitted() {
        return isServerSide() && getPlayer() instanceof ServerPlayer player && player.containerMenu == this
                && player.mayBuild() && !player.isSpectator() && !core.isRemoved()
                && player.level() == core.getLevel() && stillValid(player)
                && core.canInteract(player)
                && player.level().mayInteract(player, core.getBlockPos());
    }

    private void toggleOverclock() {
        if (!isServerSide()) return;
        if (!permitted()) { finish(ActionResult.NOT_PERMITTED, -1); return; }
        // Switching off remains possible after the structure or ME connection fails.
        boolean wasOverclocked = core.isOverclocked();
        if (!wasOverclocked) {
            if (!core.isFormed()) { finish(ActionResult.STRUCTURE_INCOMPLETE, -1); return; }
            if (!core.getMainNode().isActive()) { finish(ActionResult.OFFLINE, -1); return; }
            int level = NegativeEntropyBenefits.levelFor(core.getEffectiveEntropy());
            if (level == 0) { finish(ActionResult.INSUFFICIENT_ENTROPY, -1); return; }
            if (core.getInfoBalance() < new NegativeEntropyBenefits(level, true).infoPerTick()) {
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
        if (core.getEffectiveEntropy() < node.entropy()) { finish(ActionResult.INSUFFICIENT_ENTROPY, index); return; }
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
            networkOnline = core.getMainNode().isActive();
            effectiveEntropy = core.getEffectiveEntropy();
            anchorCount = core.getAnchorCount();
            var benefits = core.getBenefits();
            benefitLevel = benefits.level();
            benefitsOverclocked = benefits.overclocked();
            infoBalance = core.getInfoBalance();
            infoConsumption = core.getInfoConsumption();
            overclocked = core.isOverclocked();
            unlockMask = core.getUnlockMask() & ALL_UNLOCKS;
            canConfigure = permitted();
            var level = getPlayer().level();
            catalog = recipeCatalog.get(level.getRecipeManager(), level.registryAccess(), level.getGameTime());
        }
        super.broadcastChanges();
    }
}
