package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CPUSelectionList.class, remap = false)
public abstract class CPUSelectionListMixin {
    @Unique
    private static final String molecularmanipulator$infiniteValueTranslation =
            "gui.molecularmanipulator.omni.infinite";

    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$formatInfiniteStorage(
            CraftingStatusMenu.CraftingCpuListEntry cpu,
            CallbackInfoReturnable<String> callback) {
        if (cpu.storage() == OmniComputationCoreBlockEntity.INFINITE_STORAGE) {
            callback.setReturnValue(Component.translatable(
                    molecularmanipulator$infiniteValueTranslation).getString());
        }
    }

    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"))
    private String molecularmanipulator$formatInfiniteParallelism(int value, Operation<String> original) {
        return value == OmniComputationCoreBlockEntity.AE2_PARALLELISM_SENTINEL
                ? Component.translatable(molecularmanipulator$infiniteValueTranslation).getString()
                : original.call(value);
    }

    @WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/Tooltips;ofNumber(J)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent molecularmanipulator$tooltipInfiniteParallelism(
            long value,
            Operation<MutableComponent> original) {
        return value == OmniComputationCoreBlockEntity.AE2_PARALLELISM_SENTINEL
                ? molecularmanipulator$infiniteTooltipValue()
                : original.call(value);
    }

    @WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/Tooltips;ofBytes(J)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent molecularmanipulator$tooltipInfiniteStorage(
            long value,
            Operation<MutableComponent> original) {
        return value == OmniComputationCoreBlockEntity.INFINITE_STORAGE
                ? molecularmanipulator$infiniteTooltipValue()
                : original.call(value);
    }

    private static MutableComponent molecularmanipulator$infiniteTooltipValue() {
        return Component.translatable(molecularmanipulator$infiniteValueTranslation)
                .withStyle(Tooltips.NUMBER_TEXT);
    }
}
