package com.atir.molecularmanipulator.mixin;

import java.util.Objects;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.slot.AppEngSlot;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingAmountMenuBridge;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingConfirmMenuBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CraftAmountMenu.class, remap = false)
public abstract class CraftAmountMenuMixin implements LongCraftingAmountMenuBridge {
    @Shadow
    private AEKey whatToCraft;

    @Shadow
    @Final
    private ISubMenuHost host;

    @Shadow
    @Final
    private AppEngSlot craftingItem;

    @Override
    public void molecularmanipulator$setWhatToCraftLong(AEKey whatToCraft, long initialAmount) {
        this.whatToCraft = Objects.requireNonNull(whatToCraft, "whatToCraft");
        this.craftingItem.set(GenericStack.wrapInItemStack(whatToCraft, initialAmount));
    }

    @Override
    public void molecularmanipulator$confirmLong(long amount, boolean craftMissingAmount, boolean autoStart) {
        var menu = (CraftAmountMenu) (Object) this;
        if (menu.isClientSide() || this.whatToCraft == null || amount <= 0) {
            return;
        }

        if (craftMissingAmount && menu.getTarget() instanceof IActionHost actionHost) {
            var node = actionHost.getActionableNode();
            if (node != null) {
                long existingAmount = node.getGrid().getStorageService()
                        .getCachedInventory()
                        .get(this.whatToCraft);
                amount = existingAmount >= amount ? 0 : amount - existingAmount;
            }
        }

        long maximumAmount = ModConfig.MAX_CRAFTING_ORDER_AMOUNT.get();
        if (amount > maximumAmount) {
            menu.getPlayer().sendSystemMessage(Component.translatable(
                    "message.molecularmanipulator.crafting_amount_too_large",
                    maximumAmount));
            return;
        }

        if (!(menu.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        var locator = menu.getLocator();
        if (amount <= 0) {
            this.host.returnToMainMenu(player, menu);
        } else if (locator != null) {
            MenuOpener.open(CraftConfirmMenu.TYPE, player, locator);
            if (player.containerMenu instanceof CraftConfirmMenu confirmMenu
                    && confirmMenu instanceof LongCraftingConfirmMenuBridge bridge) {
                confirmMenu.setAutoStart(autoStart);
                bridge.molecularmanipulator$planLong(
                        this.whatToCraft,
                        amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
                confirmMenu.broadcastChanges();
            }
        }
    }
}
