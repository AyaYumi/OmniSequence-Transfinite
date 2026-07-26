package com.atir.molecularmanipulator.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import java.util.List;

public final class MolecularManipulatorMenu extends AEBaseMenu {
    private static final String ACTION_SET_PAGE = "set_page";

    public static final MenuType<MolecularManipulatorMenu> TYPE = ForgeMenuTypeFactory.create(
            MolecularManipulator.id("molecular_manipulator"),
            MolecularManipulatorMenu::new,
            PatternProviderLogicHost.class);

    private final List<AppEngSlot> patternSlots;
    private int page;

    private MolecularManipulatorMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        createPlayerInventorySlots(playerInventory);

        var logic = host.getLogic();
        var patternInventory = logic.getPatternInv();
        for (int index = 0; index < patternInventory.size(); index++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN,
                    patternInventory, index), SlotSemantics.ENCODED_PATTERN);
        }

        var returnInventory = logic.getReturnInv().createMenuWrapper();
        for (int index = 0; index < PatternProviderReturnInventory.NUMBER_OF_SLOTS; index++) {
            if (index < returnInventory.size()) {
                addSlot(new AppEngSlot(returnInventory, index), SlotSemantics.STORAGE);
            }
        }

        this.patternSlots = getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(AppEngSlot.class::cast)
                .toList();
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::applyPage);
        applyPage(0);
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return Math.max(1, (patternSlots.size() + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
    }

    public List<AppEngSlot> getPatternSlots() {
        return patternSlots;
    }

    public void requestPage(int requestedPage) {
        int newPage = clampPage(requestedPage);
        if (newPage == page) {
            return;
        }
        applyPage(newPage);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, newPage);
        }
    }

    private void applyPage(int requestedPage) {
        page = clampPage(requestedPage);
        int firstSlot = page * MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE;
        int lastSlot = Math.min(patternSlots.size(), firstSlot + MolecularManipulatorBlockEntity.PATTERNS_PER_PAGE);
        for (int index = 0; index < patternSlots.size(); index++) {
            patternSlots.get(index).setSlotEnabled(index >= firstSlot && index < lastSlot);
        }
    }

    private int clampPage(int requestedPage) {
        return Math.max(0, Math.min(getPageCount() - 1, requestedPage));
    }
}
