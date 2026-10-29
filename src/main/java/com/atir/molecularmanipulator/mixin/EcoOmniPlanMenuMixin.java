package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanMenu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanMenuAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses ECO's public merged methods; ECO fields are resolved only on the runtime menu. */
@Mixin(value = CraftConfirmMenu.class, priority = 900, remap = false)
public abstract class EcoOmniPlanMenuMixin {
    @Shadow private ICraftingPlan result;
    @Shadow private ICraftingCPU selectedCpu;
    @Shadow private IGrid getGrid() { throw new AssertionError(); }
    @Shadow private IActionSource getActionSrc() { throw new AssertionError(); }

    // This own synchronized flag also closes the client button if ECO's availability
    // field itself cannot be accessed. ECO CPU selections never set this flag.
    @Unique @GuiSync(32000)
    private boolean molecularmanipulator$omniMenuAccessFailed;

    // CPU cycling runs before AEBaseMenu synchronizes @GuiSync fields. Updating at
    // TAIL would send stale ECO-only availability and can strand the client button.
    @Inject(method = "broadcastChanges", at = @At(value = "INVOKE",
            target = "Lappeng/menu/AEBaseMenu;broadcastChanges()V"), require = 1)
    private void molecularmanipulator$syncOmniBigOrderCpu(CallbackInfo callback) {
        if (((CraftConfirmMenu) (Object) this).isClientSide()) return;
        molecularmanipulator$omniMenuAccessFailed = false;
        if (!EcoOmniPlanMenu.isOmni(selectedCpu)) return;
        var access = EcoOmniPlanMenuAccess.of(this);
        try {
            access.requireReady();
            access.available(this, EcoOmniPlanMenu.available(getGrid(), selectedCpu));
        } catch (EcoOmniPlanMenuAccess.Failure failure) {
            molecularmanipulator$failOmniMenuAccess(access, failure);
        }
    }

    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$includeOmniBigOrderCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> callback) {
        if (!EcoOmniPlanMenu.isOmni(cpu)) return;
        var access = EcoOmniPlanMenuAccess.of(this);
        try {
            access.requireReady();
            var confirmed = access.confirmed(this);
            if (confirmed != null && ECOBigOrderAdmission.allows(confirmed.status(), true)) {
                callback.setReturnValue(EcoOmniPlanMenu.available(getGrid(), cpu));
            }
        } catch (EcoOmniPlanMenuAccess.Failure failure) {
            callback.setReturnValue(false);
            if (EcoOmniPlanMenu.isOmni(selectedCpu)) molecularmanipulator$failOmniMenuAccess(access, failure);
            else access.report(failure);
        }
    }

    /** The original ECO method remains responsible for every non-Omni selection. */
    @Inject(method = "neoecoae$startBigOrder(Z)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$startOmniBigOrder(boolean forced, CallbackInfo callback) {
        if (!EcoOmniPlanMenu.isOmni(selectedCpu)) return;
        callback.cancel();
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide()) return;
        var access = EcoOmniPlanMenuAccess.of(this);
        try {
            access.requireReady();
            var outcome = EcoOmniPlanMenu.submit(getGrid(), selectedCpu, result, access.confirmed(this),
                    EcoOmniPlanMenu.resolveOwnedResult(result,
                            ((ECOCraftConfirmMenuMode) this).neoecoae$isEcoPlannerAvailable()),
                    access.options(this), forced, getActionSrc());
            menu.setAutoStart(false);
            if (!outcome.submitted().successful()) {
                access.diagnostic(this, "Omni: " + outcome.reason());
                menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(outcome.submitted());
                return;
            }
            result = null;
            access.clearConfirmed(this);
            access.diagnostic(this, "");
            menu.getHost().returnToMainMenu(menu.getPlayer(), menu);
        } catch (EcoOmniPlanMenuAccess.Failure failure) {
            molecularmanipulator$failOmniMenuAccess(access, failure);
        }
    }

    @Inject(method = "neoecoae$bigOrderCpuAvailable()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$closeUnavailableOmniButton(CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$omniMenuAccessFailed) callback.setReturnValue(false);
    }

    @Unique
    private void molecularmanipulator$failOmniMenuAccess(EcoOmniPlanMenuAccess access, Throwable failure) {
        molecularmanipulator$omniMenuAccessFailed = true;
        var menu = (CraftConfirmMenu) (Object) this;
        menu.setAutoStart(false);
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(CraftingSubmitResult.INCOMPLETE_PLAN);
        access.failClosed(this, failure);
    }
}
