package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Omni names stay local; AppliedEnhancements owns all shared amount formatting. */
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
                && "gui.molecularmanipulator.omni.cpu_name".equals(translation.getKey())) {
            return Component.translatable(
                    "gui.molecularmanipulator.omni.cpu_name_short",
                    translation.getArgs());
        }
        return fullName;
    }

}
