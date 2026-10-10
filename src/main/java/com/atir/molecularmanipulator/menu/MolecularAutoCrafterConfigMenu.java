package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.InaccessibleSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.menu.locator.MenuLocators;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Detail menu opened from one of the standalone auto-crafter's pattern slots. */
public final class MolecularAutoCrafterConfigMenu extends AEBaseMenu {
    private static final String ACTION_LIMIT = "set_auto_craft_limit";
    private static final String ACTION_LIMIT_MODE = "toggle_auto_craft_limit_mode";
    private static final String ACTION_RESERVE = "set_auto_craft_reserve";
    private static final String ACTION_BACK = "return_to_auto_crafter";

    public static final MenuType<MolecularAutoCrafterConfigMenu> TYPE = ForgeMenuTypeFactory.create(MolecularManipulator.id("molecular_auto_crafter_config"), MolecularAutoCrafterConfigMenu::new, MolecularAutoCrafterBlockEntity.class);

    @GuiSync(0) public int selectedSlot = -1;
    @GuiSync(1) public MolecularAutoCrafter.AutoCraftState state = MolecularAutoCrafter.AutoCraftState.EMPTY;
    @GuiSync(2) public long outputLimit;
    @GuiSync(3) public int inputCount;
    @GuiSync(4) public long reserve0;
    @GuiSync(5) public long reserve1;
    @GuiSync(6) public long reserve2;
    @GuiSync(7) public long reserve3;
    @GuiSync(8) public long reserve4;
    @GuiSync(9) public long reserve5;
    @GuiSync(10) public long reserve6;
    @GuiSync(11) public long reserve7;
    @GuiSync(12) public long reserve8;
    @GuiSync(13) public boolean enabled;
    @GuiSync(14) public MolecularAutoCrafter.OutputLimitMode outputLimitMode =
            MolecularAutoCrafter.OutputLimitMode.DESTINATION;

    private final MolecularAutoCrafterBlockEntity machine;
    private final AppEngInternalInventory patternPreview = new AppEngInternalInventory(1);

    private MolecularAutoCrafterConfigMenu(int id, Inventory inventory,
            MolecularAutoCrafterBlockEntity machine) {
        super(TYPE, id, inventory, machine);
        this.machine = machine;
        registerClientAction(ACTION_LIMIT, Long.class, this::setLimit);
        registerClientAction(ACTION_LIMIT_MODE, this::toggleOutputLimitMode);
        registerClientAction(ACTION_RESERVE, ReserveRequest.class, this::setReserve);
        registerClientAction(ACTION_BACK, this::back);
        selectedSlot = machine.getMenuSelectedSlot();
        // The block entity's private inventory is not synced to the client.
        // A read-only menu slot supplies the exact pattern for this detail page.
        var preview = new InaccessibleSlot(patternPreview, 0);
        preview.x = 10;
        preview.y = 8;
        addSlot(preview, SlotSemantics.ENCODED_PATTERN);
        if (isServerSide()) refresh();
    }

    public MolecularAutoCrafterBlockEntity getMachine() {
        return machine;
    }

    public ItemStack getSelectedPattern() {
        return patternPreview.getStackInSlot(0);
    }

    public long getReserve(int index) {
        return switch (index) {
            case 0 -> reserve0; case 1 -> reserve1; case 2 -> reserve2; case 3 -> reserve3;
            case 4 -> reserve4; case 5 -> reserve5; case 6 -> reserve6; case 7 -> reserve7;
            case 8 -> reserve8; default -> 0;
        };
    }

    public void requestLimit(long value) {
        if (isClientSide()) sendClientAction(ACTION_LIMIT, Math.max(0, value));
    }

    public void requestOutputLimitModeToggle() {
        if (isClientSide()) sendClientAction(ACTION_LIMIT_MODE);
    }

    public void requestReserve(int index, long value) {
        if (isClientSide()) sendClientAction(ACTION_RESERVE,
                new ReserveRequest(index, Math.max(0, value)));
    }

    public void requestBack() {
        if (isClientSide()) sendClientAction(ACTION_BACK);
    }

    private void back() {
        if (isServerSide() && getPlayer() != null) {
            MenuOpener.open(MolecularAutoCrafterMenu.TYPE, getPlayer(),
                    MenuLocators.forBlockEntity(machine));
        }
    }

    private void setLimit(long value) {
        if (isServerSide() && getPlayer() != null && getPlayer().mayBuild()) {
            machine.getAutoCrafter().setOutputLimit(selectedSlot, Math.max(0, value));
            refresh();
        }
    }

    private void toggleOutputLimitMode() {
        if (isServerSide() && getPlayer() != null && getPlayer().mayBuild()) {
            machine.getAutoCrafter().toggleOutputLimitMode(selectedSlot);
            refresh();
        }
    }

    private void setReserve(ReserveRequest request) {
        if (isServerSide() && getPlayer() != null && getPlayer().mayBuild()
                && request != null && request.index >= 0 && request.index < MolecularAutoCrafter.MAX_INPUTS) {
            machine.getAutoCrafter().setProtection(selectedSlot, request.index, request.value);
            refresh();
        }
    }

    private void refresh() {
        if (selectedSlot < 0 || selectedSlot >= MolecularAutoCrafter.PATTERN_SLOTS) {
            state = MolecularAutoCrafter.AutoCraftState.EMPTY;
            inputCount = 0;
            enabled = false;
            outputLimitMode = MolecularAutoCrafter.OutputLimitMode.DESTINATION;
            patternPreview.setItemDirect(0, ItemStack.EMPTY);
            return;
        }
        var pattern = machine.getAutoCraftPatternInventory().getStackInSlot(selectedSlot);
        if (!ItemStack.matches(patternPreview.getStackInSlot(0), pattern)) {
            patternPreview.setItemDirect(0, pattern.copy());
        }
        var view = machine.getAutoCrafter().getView(selectedSlot);
        state = view.state();
        enabled = view.enabled();
        outputLimit = view.outputLimit();
        outputLimitMode = view.outputLimitMode();
        inputCount = Math.min(MolecularAutoCrafter.MAX_INPUTS, view.inputCount());
        var values = view.protections();
        reserve0 = values[0]; reserve1 = values[1]; reserve2 = values[2]; reserve3 = values[3];
        reserve4 = values[4]; reserve5 = values[5]; reserve6 = values[6]; reserve7 = values[7]; reserve8 = values[8];
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            refresh();
        }
        super.broadcastChanges();
    }

    public record ReserveRequest(int index, long value) {}
}
