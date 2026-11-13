package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Compact finite Omni CPU rows; AppliedEnhancements retains ownership of infinity labels. */
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

    // AE2 15.4.10 has three String/colour/scale additions in this method.
    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IF)V", ordinal = 1))
    private void molecularmanipulator$compactStorage(InfoBar bar, String text, int color,
            float scale, Operation<Void> original,
            @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (molecularmanipulator$isOmniCpu(cpu) && cpu.storage() >= 0 && cpu.storage() != Long.MAX_VALUE) {
            text = molecularmanipulator$storageLabel(cpu.storage());
        }
        original.call(bar, text, color, scale);
    }

    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IF)V", ordinal = 2))
    private void molecularmanipulator$compactParallelism(InfoBar bar, String text, int color,
            float scale, Operation<Void> original,
            @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (molecularmanipulator$isOmniCpu(cpu) && cpu.coProcessors() >= 0
                && cpu.coProcessors() != Integer.MAX_VALUE) {
            text = molecularmanipulator$parallelismLabel(cpu.coProcessors());
            if (text.length() == 4) {
                scale = Math.min(scale, 0.5F);
            }
        }
        original.call(bar, text, color, scale);
    }

    @Unique
    private static String molecularmanipulator$parallelismLabel(int processors) {
        if (processors < 1000) return Integer.toString(processors);
        int divisor;
        String unit;
        if (processors >= 1_000_000_000) {
            divisor = 1_000_000_000;
            unit = "G";
        } else if (processors >= 1_000_000) {
            divisor = 1_000_000;
            unit = "M";
        } else {
            divisor = 1_000;
            unit = "K";
        }
        return processors / divisor + unit;
    }

    @Unique
    private static boolean molecularmanipulator$isOmniCpu(CraftingStatusMenu.CraftingCpuListEntry cpu) {
        return cpu.name() != null && cpu.name().getContents() instanceof TranslatableContents translation
                && ("gui.molecularmanipulator.nexus.cpu_name".equals(translation.getKey())
                || "gui.molecularmanipulator.omni.cpu_name".equals(translation.getKey()));
    }

    @Unique
    private static String molecularmanipulator$storageLabel(long bytes) {
        // Binary storage units match AE2's tooltip. Unlike its 19.2.17 formatter,
        // this also handles finite values above TiB without running out of units.
        String[] units = {"", "k", "M", "G", "T", "P", "E"};
        int unit = 0;
        long divisor = 1;
        while (unit < units.length - 1 && bytes / divisor >= (unit == 0 ? 1024 : 1000)) {
            divisor *= 1024;
            unit++;
        }
        for (int precision = 2; ; precision--) {
            String digits = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(divisor), precision, RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString();
            if (digits.startsWith("0.")) digits = digits.substring(1);
            String label = digits + units[unit];
            if (label.length() <= 4 || precision == 0) return label;
        }
    }
}
