package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps unlimited Omni rows and tooltips consistent with AppliedEnhancements' 9.2E label. */
@Mixin(value = CPUSelectionList.class, remap = false)
public abstract class CPUSelectionListMixin {
    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/CPUSelectionList;getCpuName"
                    + "(Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry;)"
                    + "Lnet/minecraft/network/chat/Component;"))
    private Component molecularmanipulator$compactOmniCpuName(
            CPUSelectionList instance,
            CraftingStatusMenu.CraftingCpuListEntry cpu,
            Operation<Component> original) {
        var fullName = original.call(instance, cpu);
        if (cpu.storage() != OmniComputationCoreBlockEntity.INFINITE_STORAGE) {
            return fullName;
        }
        if (fullName.getContents() instanceof TranslatableContents translation
                && "gui.molecularmanipulator.nexus.cpu_name".equals(translation.getKey())) {
            return Component.translatable(
                    "gui.molecularmanipulator.nexus.cpu_name_short", translation.getArgs());
        }
        if (fullName.getContents() instanceof TranslatableContents translation
                && "gui.molecularmanipulator.omni.cpu_name".equals(translation.getKey())) {
            return Component.translatable(
                    "gui.molecularmanipulator.omni.cpu_name_short",
                    translation.getArgs());
        }
        return fullName;
    }

    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V", ordinal = 2))
    private void molecularmanipulator$unlimitedParallelismLabel(InfoBar bar, String text, int color,
            float scale, int x, int y, Operation<Void> original,
            @Local CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (molecularmanipulator$isUnlimitedOmniCpu(cpu)) text = "9.2E";
        original.call(bar, text, color, scale, x, y);
    }

    @WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/Tooltips;ofNumber(J)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent molecularmanipulator$unlimitedParallelismTooltip(long value,
            Operation<MutableComponent> original,
            @Local CraftingStatusMenu.CraftingCpuListEntry cpu) {
        return molecularmanipulator$isUnlimitedOmniCpu(cpu)
                ? Component.literal("9.2E").withStyle(Tooltips.NUMBER_TEXT)
                : original.call(value);
    }

    @Unique
    private static boolean molecularmanipulator$isUnlimitedOmniCpu(CraftingStatusMenu.CraftingCpuListEntry cpu) {
        // The execution adapter leaves room for AE2's +1. This saturated int is
        // an internal representation of long-sized parallelism, not a 2.1G limit.
        return cpu.storage() == OmniComputationCoreBlockEntity.INFINITE_STORAGE
                && cpu.coProcessors() >= Integer.MAX_VALUE - 1
                && cpu.name() != null && cpu.name().getContents() instanceof TranslatableContents translation
                && ("gui.molecularmanipulator.nexus.cpu_name".equals(translation.getKey())
                || "gui.molecularmanipulator.omni.cpu_name".equals(translation.getKey()));
    }
}
