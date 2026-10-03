package com.atir.molecularmanipulator.client;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Recipe display wrappers keep the underlying resource and full long quantity in hover text. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class AEStackTooltips {
    private AEStackTooltips() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = GenericStack.unwrapItemStack(event.getItemStack());
        if (stack == null) return;
        if (event.getToolTip().isEmpty()) event.getToolTip().add(stack.what().getDisplayName());
        else event.getToolTip().set(0, stack.what().getDisplayName());
        event.getToolTip().add(Component.translatable("gui.molecularmanipulator.jei_required_count",
                DisplayNumbers.exact(stack.amount())));
    }
}
