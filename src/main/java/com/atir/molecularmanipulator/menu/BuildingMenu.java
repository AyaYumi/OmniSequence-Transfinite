package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import com.atir.molecularmanipulator.terminal.BuildingTerminalLocator;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/** Shared terminal navigation for the mod's existing building menus. Local block interaction is unchanged. */
public abstract class BuildingMenu extends AEBaseMenu {
    private static final String RETURN = "building_terminal_return";
    @GuiSync(30000) public boolean terminalAccess;

    protected BuildingMenu(MenuType<?> type, int id, Inventory inventory, Object host) {
        super(type, id, inventory, host);
        registerClientAction(RETURN, () -> {
            if (isServerSide() && getPlayer().containerMenu == this && getLocator() instanceof BuildingTerminalLocator locator
                    && locator.isValid(getPlayer())) {
                MenuOpener.open(BuildingTerminalMenu.TYPE, getPlayer(), MenuLocators.forInventorySlot(locator.terminalSlot()));
            }
        });
    }

    public void requestTerminal() { if (isClientSide()) sendClientAction(RETURN); }

    /** Keep terminal context across detail pages instead of reverting to a local, chunk-dependent locator. */
    protected final MenuHostLocator buildingLocator() {
        return getLocator() instanceof BuildingTerminalLocator ? getLocator() : MenuLocators.forBlockEntity(getBlockEntity());
    }

    @Override public boolean stillValid(Player player) {
        if (isServerSide() && getLocator() instanceof BuildingTerminalLocator locator && !locator.isValid(player)) return false;
        return super.stillValid(player);
    }

    @Override public void broadcastChanges() {
        if (isServerSide()) {
            terminalAccess = getLocator() instanceof BuildingTerminalLocator;
            if (getLocator() instanceof BuildingTerminalLocator locator && !locator.isValid(getPlayer())) {
                // Stop before AE2's identity check can load a now-unloaded target chunk.
                setValidMenu(false);
                return;
            }
        }
        super.broadcastChanges();
    }
}
