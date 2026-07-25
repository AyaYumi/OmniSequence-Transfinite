package com.atir.molecularmanipulator.mixin;

import java.util.concurrent.Future;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingAmountMenuBridge;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingConfirmMenuBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuMixin implements LongCraftingConfirmMenuBridge {
    @Shadow
    private AEKey whatToCraft;

    @Shadow
    private int amount;

    @Shadow
    private Future<ICraftingPlan> job;

    @Shadow
    private ICraftingPlan result;

    @Shadow
    @Final
    private ISubMenuHost host;

    @Unique
    private long molecularmanipulator$longAmount;

    @Unique
    private boolean molecularmanipulator$usesLongAmount;

    @Override
    public boolean molecularmanipulator$planLong(AEKey what, long amount, CalculationStrategy strategy) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (this.job != null) {
            this.job.cancel(true);
        }
        this.result = null;
        menu.clearError();

        this.whatToCraft = what;
        this.amount = (int) Math.min(amount, Integer.MAX_VALUE);
        this.molecularmanipulator$longAmount = amount;
        this.molecularmanipulator$usesLongAmount = true;

        if (!(menu.getTarget() instanceof IActionHost actionHost)) {
            return false;
        }
        var node = actionHost.getActionableNode();
        if (node == null) {
            return false;
        }

        ICraftingSimulationRequester requester = () -> new PlayerSource(menu.getPlayer(), actionHost);
        this.job = node.getGrid().getCraftingService().beginCraftingCalculation(
                menu.getPlayer().level(),
                requester,
                what,
                amount,
                strategy);
        return true;
    }

    @Inject(method = "goBack", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$goBackWithLongAmount(CallbackInfo callback) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (!this.molecularmanipulator$usesLongAmount || menu.isClientSide()) {
            return;
        }

        menu.clearError();
        if (menu.getPlayer() instanceof ServerPlayer player
                && this.whatToCraft != null
                && menu.getLocator() != null) {
            MenuOpener.open(CraftAmountMenu.TYPE, player, menu.getLocator());
            if (player.containerMenu instanceof CraftAmountMenu amountMenu
                    && amountMenu instanceof LongCraftingAmountMenuBridge bridge) {
                bridge.molecularmanipulator$setWhatToCraftLong(
                        this.whatToCraft,
                        this.molecularmanipulator$longAmount);
                amountMenu.broadcastChanges();
            }
        } else {
            this.host.returnToMainMenu(menu.getPlayer(), menu);
        }
        callback.cancel();
    }

    @Inject(method = "replan", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$replanLongAmount(CallbackInfo callback) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (!this.molecularmanipulator$usesLongAmount || menu.isClientSide()) {
            return;
        }

        menu.clearError();
        if (this.whatToCraft == null || !molecularmanipulator$planLong(
                this.whatToCraft,
                this.molecularmanipulator$longAmount,
                CalculationStrategy.CRAFT_LESS)) {
            menu.goBack();
        }
        callback.cancel();
    }
}
