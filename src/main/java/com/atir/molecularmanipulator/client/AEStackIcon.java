package com.atir.molecularmanipulator.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws an AE resource without turning it into a fake Minecraft item. */
public final class AEStackIcon {
    private AEStackIcon() {}

    public static void draw(GuiGraphics graphics, GenericStack stack, int x, int y) {
        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, stack.what());
        if (stack.amount() > 1) {
            var font = Minecraft.getInstance().font;
            var count = DisplayNumbers.compact(stack.amount());
            float scale = Math.min(1.0F, 16.0F / Math.max(1, font.width(count)));
            graphics.pose().pushPose();
            graphics.pose().translate(x + 16, y + 15, 200);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, count, -font.width(count), -8, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }
}
