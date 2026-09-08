package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchMenuState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Status and structure-management terminal; all material I/O lives in service ports. */
public final class MatterFabricationMenu extends AEBaseMenu {
    private static final String ACTION_REFRESH = "refresh_structure";
    private static final String ACTION_BUILD = "build_structure";
    private static final String ACTION_DISMANTLE = "dismantle_structure";
    private static final String ACTION_UPDATE_STRUCTURE = "update_structure";
    private static final String ACTION_RESEARCH = "toggle_research";
    private static final String ACTION_RESEARCH_PAGE = "research_page";
    private static final String ACTION_RESEARCH_SELECT = "select_research";

    public static final MenuType<MatterFabricationMenu> TYPE = ForgeMenuTypeFactory.create(MolecularManipulator.id("matter_fabrication"), MatterFabricationMenu::new, MatterFabricationBlockEntity.class);

    @GuiSync(10)
    public boolean formed;
    @GuiSync(11)
    public boolean networkOnline;
    @GuiSync(12)
    public int progress;
    @GuiSync(13)
    public int processingTime;
    @GuiSync(14)
    public double aePerTick;
    @GuiSync(15)
    public MatterFabricationBlockEntity.ProcessingState state =
            MatterFabricationBlockEntity.ProcessingState.STRUCTURE_INCOMPLETE;
    @GuiSync(16)
    public int totalParts;
    @GuiSync(17)
    public int correctParts;
    @GuiSync(18)
    public int missingParts;
    @GuiSync(19)
    public int conflictParts;
    @GuiSync(20)
    public boolean building;
    @GuiSync(21)
    public int operationProgress;
    @GuiSync(22)
    public boolean dismantling;
    @GuiSync(23)
    public int operationTotal;
    @GuiSync(24)
    public boolean legacyStructure;
    @GuiSync(25)
    public boolean updatingStructure;
    @GuiSync(26)
    public long quantumFrequency;
    @GuiSync(27)
    public MolecularCenterBlockEntity.QuantumLinkState quantumLinkState =
            MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    @GuiSync(28)
    public MatterResearchMenuState researchState = new MatterResearchMenuState("{\"completed\":[],\"tasks\":{}}");

    private long lastResearchSync = Long.MIN_VALUE;
    private ResourceLocation selectedResearch;
    private boolean researchVisible;

    private final MatterFabricationBlockEntity machine;

    private MatterFabricationMenu(int id, Inventory playerInventory, MatterFabricationBlockEntity machine) {
        super(TYPE, id, playerInventory, machine);
        this.machine = machine;
        createPlayerInventorySlots(playerInventory);
        var inventorySlots = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int index = 0; index < inventorySlots.size(); index++) {
            inventorySlots.get(index).x = 85 + index % 9 * 18;
            inventorySlots.get(index).y = 282 + index / 9 * 18;
        }
        var hotbarSlots = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int index = 0; index < hotbarSlots.size(); index++) {
            hotbarSlots.get(index).x = 85 + index * 18;
            hotbarSlots.get(index).y = 340;
        }
        var quantumSlot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                machine.getQuantumInventory(), 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MolecularCenterBlockEntity.isValidQuantumSingularity(stack) && super.mayPlace(stack);
            }
        };
        quantumSlot.x = 22;
        quantumSlot.y = 169;
        addSlot(quantumSlot, SlotSemantics.MACHINE_INPUT);
        registerClientAction(ACTION_REFRESH, this::refreshStructure);
        registerClientAction(ACTION_BUILD, this::buildStructure);
        registerClientAction(ACTION_DISMANTLE, this::dismantleStructure);
        registerClientAction(ACTION_UPDATE_STRUCTURE, this::updateStructure);
        registerClientAction(ACTION_RESEARCH, String.class, this::toggleResearch);
        registerClientAction(ACTION_RESEARCH_PAGE, Boolean.class, this::showResearchPage);
        registerClientAction(ACTION_RESEARCH_SELECT, String.class, this::selectResearch);
    }

    public MatterFabricationBlockEntity getMachine() {
        return machine;
    }

    public void showResearchPage(boolean visible) {
        researchVisible = visible;
        lastResearchSync = Long.MIN_VALUE;
        getSlots(SlotSemantics.MACHINE_INPUT).forEach(slot -> {
            if (slot instanceof appeng.menu.slot.AppEngSlot aeSlot) aeSlot.setActive(!visible);
        });
        if (isClientSide()) sendClientAction(ACTION_RESEARCH_PAGE, visible);
    }

    public void selectResearch(String id) {
        var parsed = ResourceLocation.tryParse(id);
        if (java.util.Objects.equals(selectedResearch, parsed)) return;
        selectedResearch = parsed;
        lastResearchSync = Long.MIN_VALUE;
        if (isClientSide()) sendClientAction(ACTION_RESEARCH_SELECT, id);
    }

    private void syncResearch() {
        researchState = new MatterResearchMenuState(machine.getResearch().clientState(machine,
                researchVisible ? selectedResearch : null));
    }

    public void toggleResearch(String researchId) {
        if (isClientSide()) {
            sendClientAction(ACTION_RESEARCH, researchId);
        } else if (getPlayer().mayBuild()) {
            var id = ResourceLocation.tryParse(researchId);
            if (id == null) return;
            if (machine.getResearch().hasTask(id)) {
                MatterResearchApi.setPaused(machine, researchId, !machine.getResearch().isPaused(id));
            } else MatterResearchApi.start(machine, researchId);
            syncResearch();
        }
    }

    public void requestRefresh() {
        if (isClientSide()) {
            sendClientAction(ACTION_REFRESH);
        }
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

    public void requestStructureUpdate() {
        if (isClientSide()) {
            sendClientAction(ACTION_UPDATE_STRUCTURE);
        }
    }

    private void refreshStructure() {
        if (!isClientSide()) {
            machine.refreshStructure();
        }
    }

    private void buildStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            machine.startBuild(player);
        }
    }

    private void dismantleStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            machine.startDismantle(player);
        }
    }

    private void updateStructure() {
        if (!isClientSide() && getPlayer() instanceof ServerPlayer player) {
            machine.startStructureUpdate(player);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var inspection = machine.getInspection();
            formed = machine.isStructureFormed();
            networkOnline = machine.isNetworkOnline();
            progress = machine.getProgress();
            processingTime = machine.getCurrentProcessingTime();
            aePerTick = machine.getCurrentAePerTick();
            state = machine.getProcessingState();
            totalParts = inspection.total();
            correctParts = inspection.correct();
            missingParts = inspection.missing();
            conflictParts = inspection.conflicts();
            building = machine.isBuilding();
            dismantling = machine.isDismantling();
            operationProgress = machine.getOperationProgress();
            operationTotal = machine.getOperationTotal();
            legacyStructure = machine.hasLegacyStructure();
            updatingStructure = machine.isUpdatingStructure();
            quantumFrequency = machine.getQuantumFrequency();
            quantumLinkState = machine.getQuantumLinkState();
            long now = machine.getLevel().getGameTime();
            if (lastResearchSync == Long.MIN_VALUE || now - lastResearchSync >= 5) {
                syncResearch();
                lastResearchSync = now;
            }
        }
        super.broadcastChanges();
    }
}
