package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.OmniUiTheme;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;

/** Keep quantities below the model, on an opaque badge with stable contrast. */
final class StructureMaterialRenderer implements IIngredientRenderer<ItemStack> {
    static final StructureMaterialRenderer INSTANCE = new StructureMaterialRenderer();
    static final int WIDTH = 24;
    static final int HEIGHT = 27;
    static final int QUANTITY_TOP = 17;

    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override public void render(GuiGraphics graphics, ItemStack stack) {
        if (stack.isEmpty()) return;
        graphics.renderItem(stack, 4, 0);
        drawQuantity(graphics, Minecraft.getInstance().font, stack.getCount());
    }

    static void drawQuantity(GuiGraphics graphics, Font font, int required) {
        String count = Integer.toString(required);
        int width = font.width(count);
        float scale = Math.min(1, (WIDTH - 2.0F) / Math.max(1, width));
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0, 0, 200);
            graphics.fill(0, QUANTITY_TOP, WIDTH, HEIGHT, OmniUiTheme.QUANTITY_BACKGROUND);
            pose.translate(WIDTH / 2.0F, QUANTITY_TOP + 1, 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, count, -width / 2, 0, OmniUiTheme.HIGHLIGHT, false);
        } finally {
            pose.popPose();
        }
    }

    @Override public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
        var mc = Minecraft.getInstance();
        var lines = new ArrayList<>(stack.getTooltipLines(mc.player, flag));
        lines.add(Component.translatable("gui.molecularmanipulator.jei_required_count", stack.getCount()));
        return lines;
    }
}
