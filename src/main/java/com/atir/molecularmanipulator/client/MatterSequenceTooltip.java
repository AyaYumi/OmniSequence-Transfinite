package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.config.MatterSequenceTooltipMode;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry.MatterValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class MatterSequenceTooltip {
    private MatterSequenceTooltip() {
    }

    @SubscribeEvent
    public static void addMatterSequenceTooltip(ItemTooltipEvent event) {
        var displayMode = ModConfig.MATTER_SEQUENCE_TOOLTIP_MODE.get();
        if (displayMode == MatterSequenceTooltipMode.DISABLED) {
            return;
        }

        var deconstruct = MatterSequenceRegistry.deconstructionOf(event.getItemStack());
        var rewrite = MatterSequenceRegistry.rewriteCostOf(event.getItemStack());
        if (deconstruct == null && rewrite == null) {
            return;
        }

        var tooltip = event.getToolTip();
        if (displayMode == MatterSequenceTooltipMode.HOLD_SHIFT
                && !Screen.hasShiftDown()) {
            tooltip.add(Component.translatable(
                    "tooltip.molecularmanipulator.matter_sequence_hold_shift",
                    Component.literal("Shift").withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.molecularmanipulator.matter_sequence")
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (deconstruct != null) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.deconstruct_output")
                    .withStyle(ChatFormatting.GRAY)
                    .append(sequenceSummary(deconstruct, "+")));
        }
        if (rewrite != null) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.rewrite_cost")
                    .withStyle(ChatFormatting.GRAY)
                    .append(sequenceSummary(rewrite, "-")));
        }
    }

    private static Component sequenceSummary(MatterValue value, String sign) {
        var result = Component.empty();
        appendSequence(result, "metal", value.metal(), sign, ChatFormatting.GRAY);
        appendSequence(result, "mineral", value.mineral(), sign, ChatFormatting.GOLD);
        appendSequence(result, "crystal", value.crystal(), sign, ChatFormatting.AQUA);
        appendSequence(result, "organic", value.organic(), sign, ChatFormatting.GREEN);
        return result;
    }

    private static void appendSequence(MutableComponent result, String type, long amount,
            String sign, ChatFormatting color) {
        if (amount <= 0) {
            return;
        }
        if (!result.getSiblings().isEmpty()) {
            result.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        }
        result.append(Component.literal(sign + formatAmount(amount) + " ")
                .withStyle(color));
        result.append(Component.translatable("tooltip.molecularmanipulator.sequence." + type)
                .withStyle(color));
    }

    private static String formatAmount(long amount) {
        if (amount >= 1_000_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fT", amount / 1_000_000_000_000.0);
        }
        if (amount >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", amount / 1_000.0);
        }
        return Long.toString(amount);
    }
}
