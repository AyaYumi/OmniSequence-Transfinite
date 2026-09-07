package com.atir.molecularmanipulator.integration.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;

/** Draw a single, bounded quantity label instead of the inventory-style shadowed count. */
final class StructureMaterialRenderer implements IIngredientRenderer<ItemStack> {
    static final StructureMaterialRenderer INSTANCE = new StructureMaterialRenderer();

    @Override public void render(GuiGraphics graphics, ItemStack stack) {
        graphics.renderItem(stack, 0, 0);
        if (stack.getCount() <= 1) return;
        var font = Minecraft.getInstance().font;
        String count = Integer.toString(stack.getCount());
        float scale = Math.min(1, 22.0F / font.width(count));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(16, 9, 200);
        pose.scale(scale, scale, 1);
        int width = font.width(count);
        graphics.drawString(font, count, -width, 0, 0xFFFFFFFF, false);
        pose.popPose();
    }

    @Override public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
        var mc = Minecraft.getInstance();
        var lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag));
        lines.add(Component.translatable("gui.molecularmanipulator.jei_required_count", stack.getCount()));
        return lines;
    }
}
