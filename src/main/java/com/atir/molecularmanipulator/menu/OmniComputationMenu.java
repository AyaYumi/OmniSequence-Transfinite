package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class OmniComputationMenu extends AEBaseMenu {
    private static final String ACTION_BUILD = "omni_build";
    private static final String ACTION_DISMANTLE = "omni_dismantle";
    private static final String ACTION_REFRESH = "omni_refresh";
    private static final String ACTION_UPDATE_STRUCTURE = "omni_update_structure";
    private static final String ACTION_KEEP_LEGACY_STRUCTURE = "omni_keep_legacy_structure";

    public static final MenuType<OmniComputationMenu> TYPE = ForgeMenuTypeFactory.create(MolecularManipulator.id("omni_computation"), OmniComputationMenu::new, OmniComputationCoreBlockEntity.class);

    @GuiSync(0)
    public boolean formed;
    @GuiSync(1)
    public boolean networkOnline;
    @GuiSync(2)
    public int totalParts;
    @GuiSync(3)
    public int correctParts;
    @GuiSync(4)
    public int missingParts;
    @GuiSync(5)
    public int conflictParts;
    @GuiSync(6)
    public int activeJobs;
    @GuiSync(7)
    public int cpuLanes;
    @GuiSync(8)
    public boolean building;
    @GuiSync(9)
    public int buildProgress;
    @GuiSync(10)
    public int buildTotal;
    @GuiSync(11)
    public int activeMaterialCalculations;
    @GuiSync(12)
    public int completedMaterialCalculations;
    @GuiSync(14)
    public int lastMaterialCalculationMillis;
    @GuiSync(15)
    public long quantumFrequency;
    @GuiSync(16)
    public MolecularCenterBlockEntity.QuantumLinkState quantumLinkState =
            MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    @GuiSync(17)
    public boolean dismantling;
    @GuiSync(18)
    public boolean legacyStructure;
    @GuiSync(19)
    public boolean legacyStructureUpdateDismissed;
    @GuiSync(20)
    public int dismantlableBlocks;

    private final OmniComputationCoreBlockEntity core;

    private OmniComputationMenu(int id, Inventory playerInventory, OmniComputationCoreBlockEntity core) {
        super(TYPE, id, playerInventory, core);
        this.core = core;
        createPlayerInventorySlots(playerInventory);
        positionPlayerInventory();
        var quantumSlot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                core.getQuantumInventory(), 0);
        quantumSlot.x = 293;
        quantumSlot.y = 201;
        addSlot(quantumSlot, SlotSemantics.MACHINE_INPUT);
        registerClientAction(ACTION_BUILD, this::build);
        registerClientAction(ACTION_DISMANTLE, this::dismantle);
        registerClientAction(ACTION_REFRESH, this::refresh);
        registerClientAction(ACTION_UPDATE_STRUCTURE, this::updateStructure);
        registerClientAction(ACTION_KEEP_LEGACY_STRUCTURE, this::keepLegacyStructure);
    }

    public OmniComputationCoreBlockEntity getCore() {
        return core;
    }

    public void requestBuild() {
        if (isClientSide()) {
            sendClientAction(ACTION_BUILD);
        }
    }

    public void requestDismantle() {
        if (isClientSide()) {
            sendClientAction(ACTION_DISMANTLE);
        }
    }

    public void requestRefresh() {
        if (isClientSide()) {
            sendClientAction(ACTION_REFRESH);
        }
    }

    public void requestStructureUpdate() {
        if (isClientSide()) {
            sendClientAction(ACTION_UPDATE_STRUCTURE);
        }
    }

    public void requestKeepLegacyStructure() {
        if (isClientSide()) {
            sendClientAction(ACTION_KEEP_LEGACY_STRUCTURE);
        }
    }

    private void build() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            core.startBuild(player);
        }
    }

    private void dismantle() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            core.startDismantle(player);
        }
    }

    private void refresh() {
        if (!isClientSide()) {
            core.refreshStructureNow();
        }
    }

    private void updateStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            core.startStructureUpdate(player);
        }
    }

    private void keepLegacyStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            core.keepLegacyStructure(player);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var inspection = core.getInspection();
            formed = core.isStructureFormed();
            networkOnline = core.isNetworkOnline();
            totalParts = inspection.total();
            correctParts = inspection.correct();
            missingParts = inspection.missing();
            conflictParts = inspection.conflicts();
            activeJobs = core.getActiveJobCount();
            cpuLanes = core.getCpuLaneCount();
            building = core.isBuilding();
            dismantling = core.isDismantling();
            buildProgress = core.getBuildProgress();
            buildTotal = core.getBuildTotal();
            activeMaterialCalculations = core.getActiveMaterialCalculations();
            completedMaterialCalculations = core.getCompletedMaterialCalculations();
            lastMaterialCalculationMillis = core.getLastMaterialCalculationMillis();
            quantumFrequency = core.getQuantumFrequency();
            quantumLinkState = core.getQuantumLinkState();
            legacyStructure = core.hasLegacyStructure();
            legacyStructureUpdateDismissed = core.isLegacyStructureUpdateDismissed();
            dismantlableBlocks = core.getDismantlableBlocks();
        }
        super.broadcastChanges();
    }

    private void positionPlayerInventory() {
        var inventorySlots = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int index = 0; index < inventorySlots.size(); index++) {
            var slot = inventorySlots.get(index);
            slot.x = 85 + index % 9 * 18;
            slot.y = 282 + index / 9 * 18;
        }
        var hotbarSlots = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int index = 0; index < hotbarSlots.size(); index++) {
            var slot = hotbarSlots.get(index);
            slot.x = 85 + index * 18;
            slot.y = 340;
        }
    }
}
