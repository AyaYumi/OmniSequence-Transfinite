package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.InaccessibleSlot;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Configuration menu for the nine persistent passive-crafting slots. */
public final class MolecularAutoCrafterMenu extends AEBaseMenu {
    private static final String ACTION_SELECT = "select_auto_craft_slot";
    private static final String ACTION_TOGGLE = "toggle_auto_craft_slot";
    private static final String ACTION_LIMIT = "set_auto_craft_limit";
    private static final String ACTION_RESERVE = "set_auto_craft_reserve";
    private static final String ACTION_CONFIG = "open_auto_craft_config";
    private static final String ACTION_OUTPUT_MODE = "toggle_auto_craft_output_mode";
    private static final String ACTION_OUTPUT_SIDE = "toggle_auto_craft_output_side";
    private static final String ACTION_OUTPUT_PAGE = "auto_craft_output_page";

    public static final int GRID_X = 10;
    public static final int PATTERN_Y = 36;
    public static final int OUTPUT_Y = 88;
    public static final int INVENTORY_Y = 140;
    public static final int HOTBAR_Y = 198;

    public static final MenuType<MolecularAutoCrafterMenu> TYPE = ForgeMenuTypeFactory.create(MolecularManipulator.id("molecular_auto_crafter"), MolecularAutoCrafterMenu::new, MolecularAutoCrafterBlockEntity.class);

    @GuiSync(0) public int selectedSlot = -1;
    @GuiSync(1) public int enabledMask;
    @GuiSync(2) public MolecularAutoCrafter.AutoCraftState state = MolecularAutoCrafter.AutoCraftState.EMPTY;
    @GuiSync(3) public long outputLimit;
    @GuiSync(4) public int inputCount;
    @GuiSync(5) public long reserve0;
    @GuiSync(6) public long reserve1;
    @GuiSync(7) public long reserve2;
    @GuiSync(8) public long reserve3;
    @GuiSync(9) public long reserve4;
    @GuiSync(10) public long reserve5;
    @GuiSync(11) public long reserve6;
    @GuiSync(12) public long reserve7;
    @GuiSync(13) public long reserve8;
    @GuiSync(14) public long lastBatch;
    @GuiSync(15) public long totalCrafts;
    @GuiSync(16) public MolecularAutoCrafterBlockEntity.OutputMode outputMode =
            MolecularAutoCrafterBlockEntity.OutputMode.NETWORK;
    @GuiSync(17) public int outputSides;
    @GuiSync(18) public int outputPage;
    @GuiSync(19) public int outputPages = 1;

    private final MolecularAutoCrafterBlockEntity machine;
    private final AutoCrafterOutputView outputView = new AutoCrafterOutputView();

    private MolecularAutoCrafterMenu(int id, Inventory inventory,
            MolecularAutoCrafterBlockEntity machine) {
        super(TYPE, id, inventory, machine);
        this.machine = machine;
        this.selectedSlot = machine.getMenuSelectedSlot();
        createPlayerInventorySlots(inventory);
        layoutPlayerInventory();
        for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
            var pattern = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN,
                    machine.getAutoCraftPatternInventory(), slot);
            pattern.x = GRID_X + slot * 18;
            pattern.y = PATTERN_Y;
            addSlot(pattern, SlotSemantics.ENCODED_PATTERN);
        }
        var outputs = outputView.createMenuWrapper();
        for (int index = 0; index < AutoCrafterOutputView.SLOTS; index++) {
            var slot = new InaccessibleSlot(outputs, index);
            slot.x = GRID_X + (index % AutoCrafterOutputView.COLUMNS) * 18;
            slot.y = OUTPUT_Y + (index / AutoCrafterOutputView.COLUMNS) * 18;
            slot.setEmptyTooltip(() -> java.util.List.of(Component.translatable(
                    "gui.molecularmanipulator.auto_craft_output_buffer_hint")));
            addSlot(slot, SlotSemantics.MACHINE_OUTPUT);
        }
        registerClientAction(ACTION_SELECT, Integer.class, this::select);
        registerClientAction(ACTION_TOGGLE, Integer.class, this::toggle);
        registerClientAction(ACTION_LIMIT, Long.class, this::setLimit);
        registerClientAction(ACTION_RESERVE, ReserveRequest.class, this::setReserve);
        registerClientAction(ACTION_CONFIG, Integer.class, this::openConfig);
        registerClientAction(ACTION_OUTPUT_MODE, this::toggleOutputMode);
        registerClientAction(ACTION_OUTPUT_SIDE, Integer.class, this::toggleOutputSide);
        registerClientAction(ACTION_OUTPUT_PAGE, Integer.class, this::changeOutputPage);
    }

    public MolecularAutoCrafterBlockEntity getMachine() { return machine; }

    public java.util.List<appeng.menu.slot.AppEngSlot> getPatternSlots() {
        return getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(slot -> (appeng.menu.slot.AppEngSlot) slot).toList();
    }

    public ItemStack getSelectedPattern() {
        return selectedSlot >= 0 && selectedSlot < MolecularAutoCrafter.PATTERN_SLOTS
                ? machine.getAutoCraftPatternInventory().getStackInSlot(selectedSlot) : ItemStack.EMPTY;
    }

    private void layoutPlayerInventory() {
        var main = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int index = 0; index < main.size(); index++) {
            main.get(index).x = GRID_X + (index % 9) * 18;
            main.get(index).y = INVENTORY_Y + (index / 9) * 18;
        }
        var hotbar = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int index = 0; index < hotbar.size(); index++) {
            hotbar.get(index).x = GRID_X + index * 18;
            hotbar.get(index).y = HOTBAR_Y;
        }
    }

    public long getReserve(int index) {
        return switch (index) {
            case 0 -> reserve0; case 1 -> reserve1; case 2 -> reserve2; case 3 -> reserve3;
            case 4 -> reserve4; case 5 -> reserve5; case 6 -> reserve6; case 7 -> reserve7;
            case 8 -> reserve8; default -> 0;
        };
    }

    public void requestSelect(int slot) { if (isClientSide()) sendClientAction(ACTION_SELECT, slot); }
    public void requestToggle(int slot) { if (isClientSide()) sendClientAction(ACTION_TOGGLE, slot); }
    public void requestLimit(long value) { if (isClientSide()) sendClientAction(ACTION_LIMIT, Math.max(0, value)); }
    public void requestReserve(int index, long value) {
        if (isClientSide()) sendClientAction(ACTION_RESERVE, new ReserveRequest(index, Math.max(0, value)));
    }

    public void requestConfig(int slot) {
        if (isClientSide()) sendClientAction(ACTION_CONFIG, slot);
    }

    public void requestOutputModeToggle() {
        if (isClientSide()) sendClientAction(ACTION_OUTPUT_MODE);
    }

    public void requestOutputPage(int page) {
        if (isClientSide()) sendClientAction(ACTION_OUTPUT_PAGE, page);
    }

    private void changeOutputPage(int page) {
        if (!isServerSide()) return;
        outputPage = net.minecraft.util.Mth.clamp(page, 0, outputPages - 1);
        broadcastChanges();
    }

    public void requestOutputSideToggle(int side) {
        if (isClientSide() && side >= 0 && side < 6) {
            sendClientAction(ACTION_OUTPUT_SIDE, side);
        }
    }

    private void toggleOutputMode() {
        if (!isServerSide() || getPlayer() == null || !getPlayer().mayBuild()) return;
        machine.toggleOutputMode();
        broadcastChanges();
    }

    private void toggleOutputSide(int side) {
        if (!isServerSide() || getPlayer() == null || !getPlayer().mayBuild()
                || side < 0 || side >= 6) return;
        machine.toggleOutputSide(side);
        broadcastChanges();
    }

    private void openConfig(int slot) {
        if (!isServerSide() || getPlayer() == null || !getPlayer().mayBuild()
                || slot < 0 || slot >= MolecularAutoCrafter.PATTERN_SLOTS
                || machine.getAutoCrafter().getPatternInventory().getStackInSlot(slot).isEmpty()) return;
        machine.setMenuSelectedSlot(slot);
        MenuOpener.open(MolecularAutoCrafterConfigMenu.TYPE, getPlayer(),
                MenuLocators.forBlockEntity(machine));
    }

    private void select(int slot) {
        if (!isServerSide() || slot < 0 || slot >= MolecularAutoCrafter.PATTERN_SLOTS) return;
        selectedSlot = slot;
        refresh();
    }

    private void toggle(int slot) {
        if (!isServerSide() || getPlayer() == null || !getPlayer().mayBuild()
                || slot < 0 || slot >= MolecularAutoCrafter.PATTERN_SLOTS) return;
        var view = machine.getAutoCrafter().getView(slot);
        if (view.state() == MolecularAutoCrafter.AutoCraftState.EMPTY
                || view.state() == MolecularAutoCrafter.AutoCraftState.INVALID_PATTERN) return;
        machine.getAutoCrafter().setEnabled(slot, !view.enabled());
        selectedSlot = slot;
        refresh();
    }

    private void setLimit(long value) {
        if (!isServerSide() || getPlayer() == null || !getPlayer().mayBuild()) return;
        if (selectedSlot >= 0) machine.getAutoCrafter().setOutputLimit(selectedSlot, Math.max(0, value));
        refresh();
    }

    private void setReserve(ReserveRequest request) {
        if (!isServerSide() || getPlayer() == null || !getPlayer().mayBuild() || request == null
                || selectedSlot < 0 || request.index < 0 || request.index >= MolecularAutoCrafter.MAX_INPUTS) return;
        machine.getAutoCrafter().setProtection(selectedSlot, request.index, request.value);
        refresh();
    }

    private void refresh() {
        if (selectedSlot < 0) return;
        var view = machine.getAutoCrafter().getView(selectedSlot);
        outputLimit = view.outputLimit();
        state = view.state();
        inputCount = Math.min(MolecularAutoCrafter.MAX_INPUTS, view.inputCount());
        var values = view.protections();
        reserve0 = values[0]; reserve1 = values[1]; reserve2 = values[2]; reserve3 = values[3];
        reserve4 = values[4]; reserve5 = values[5]; reserve6 = values[6]; reserve7 = values[7]; reserve8 = values[8];
        lastBatch = view.lastBatch(); totalCrafts = view.totalCrafts();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            outputPage = outputView.refresh(machine.getBufferedAutoCraftOutputs(), outputPage);
            outputPages = outputView.pageCount();
            outputMode = machine.getOutputMode();
            outputSides = machine.getOutputSides();
            enabledMask = 0;
            for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
                if (machine.getAutoCrafter().getView(slot).enabled()) enabledMask |= 1 << slot;
            }
            if (selectedSlot >= 0 && !getSelectedPattern().isEmpty()) refresh();
            else { selectedSlot = -1; state = MolecularAutoCrafter.AutoCraftState.EMPTY; inputCount = 0; }
        }
        super.broadcastChanges();
    }

    public record ReserveRequest(int index, long value) {}
}
