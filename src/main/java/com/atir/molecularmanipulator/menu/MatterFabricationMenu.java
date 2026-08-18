package com.atir.molecularmanipulator.menu;

import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OutputSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public final class MatterFabricationMenu extends AEBaseMenu {
    private static final String ACTION_REFRESH = "refresh_structure";
    private static final String ACTION_BUILD = "build_structure";
    public static final int INPUT_X = 42;
    public static final int INPUT_Y = 54;
    public static final int OUTPUT_X = 155;
    public static final int OUTPUT_Y = 63;
    public static final int PLAYER_X = 34;
    public static final int PLAYER_Y = 132;
    public static final int HOTBAR_Y = 190;

    public static final MenuType<MatterFabricationMenu> TYPE = MenuTypeBuilder
            .create(MatterFabricationMenu::new, MatterFabricationBlockEntity.class)
            .buildUnregistered(MolecularManipulator.id("matter_fabrication"));

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
    public int buildProgress;

    private final MatterFabricationBlockEntity machine;

    private MatterFabricationMenu(int id, Inventory playerInventory, MatterFabricationBlockEntity machine) {
        super(TYPE, id, playerInventory, machine);
        this.machine = machine;
        addMachineSlots();
        addPlayerSlots(playerInventory);
        registerClientAction(ACTION_REFRESH, this::refreshStructure);
        registerClientAction(ACTION_BUILD, this::buildStructure);
    }

    private void addMachineSlots() {
        var inventory = machine.getInternalInventory();
        for (int slot = 0; slot < MatterFabricationBlockEntity.INPUT_SLOTS; slot++) {
            var input = addSlot(new AppEngSlot(inventory, slot), SlotSemantics.MACHINE_INPUT);
            input.x = INPUT_X + slot % 2 * 18;
            input.y = INPUT_Y + slot / 2 * 18;
        }
        for (int slot = 0; slot < MatterFabricationBlockEntity.OUTPUT_SLOTS; slot++) {
            var output = addSlot(new OutputSlot(inventory,
                    MatterFabricationBlockEntity.INPUT_SLOTS + slot, Icon.BACKGROUND_PRIMARY_OUTPUT),
                    SlotSemantics.MACHINE_OUTPUT);
            output.x = OUTPUT_X + slot * 18;
            output.y = OUTPUT_Y;
        }
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int index = 0; index < 36; index++) {
            int inventoryIndex = index < 27 ? index + 9 : index - 27;
            int x = PLAYER_X + index % 9 * 18;
            int y = index < 27 ? PLAYER_Y + index / 9 * 18 : HOTBAR_Y;
            addSlot(new Slot(inventory, inventoryIndex, x, y),
                    index < 27 ? SlotSemantics.PLAYER_INVENTORY : SlotSemantics.PLAYER_HOTBAR);
        }
    }

    public MatterFabricationBlockEntity getMachine() {
        return machine;
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

    private void refreshStructure() {
        if (!isClientSide()) {
            machine.refreshStructure();
        }
    }

    private void buildStructure() {
        if (!isClientSide() && getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            machine.startBuild(player);
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
            buildProgress = machine.getBuildCursor();
        }
        super.broadcastChanges();
    }
}
