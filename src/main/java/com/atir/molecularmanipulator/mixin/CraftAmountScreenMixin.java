package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.atir.molecularmanipulator.network.LongCraftingRequestPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftAmountScreen.class, remap = false)
public abstract class CraftAmountScreenMixin {
    @Shadow
    @Final
    private Button next;

    @Shadow
    @Final
    private NumberEntryWidget amountToCraft;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void molecularmanipulator$enableLongAmounts(CraftAmountMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style, CallbackInfo callback) {
        this.amountToCraft.setMaxValue(Long.MAX_VALUE);
        ((NumberEntryWidgetAccessor) this.amountToCraft)
                .molecularmanipulator$getTextField()
                .setMaxLength(20);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void molecularmanipulator$validateLongAmount(CallbackInfo callback) {
        this.next.active = this.amountToCraft.getLongValue().orElse(0) > 0;
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$confirmLongAmount(CallbackInfo callback) {
        long amount = this.amountToCraft.getLongValue().orElse(0);
        if (amount > 0) {
            LongCraftingRequestPayload.sendToServer(new LongCraftingRequestPayload(
                    amount,
                    this.amountToCraft.startsWithEquals(),
                    CraftAmountScreen.hasShiftDown()));
        }
        callback.cancel();
    }
}
