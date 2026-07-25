package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Runs inside default-priority wrappers that reformat the final co-processor label.
@Mixin(value = CPUSelectionList.class, priority = 900, remap = false)
public abstract class CPUSelectionListInfoBarMixin {
    private static final String INFINITE_VALUE_TRANSLATION =
            "gui.molecularmanipulator.omni.infinite";

    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V",
            ordinal = 2))
    private void molecularmanipulator$restoreInfiniteParallelismLabel(
            InfoBar instance,
            String text,
            int color,
            float scale,
            int x,
            int y,
            Operation<Void> original,
            @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (cpu.coProcessors() == OmniComputationCoreBlockEntity.AE2_PARALLELISM_SENTINEL) {
            text = Component.translatable(INFINITE_VALUE_TRANSLATION).getString();
        }
        original.call(instance, text, color, scale, x, y);
    }
}
