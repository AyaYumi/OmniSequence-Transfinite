package com.atir.molecularmanipulator.client.gui.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** One transparent material sampled in layout space, never stretched to a panel's aspect ratio. */
final class OsqReflectionLayer {
    private static final int FIELD_WIDTH = 512, FIELD_HEIGHT = 640;
    private static final float LIGHT_STRENGTH = 1.25F;

    private OsqReflectionLayer() {}

    static void draw(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height,
            int layoutX, int layoutY, float opacity) {
        OsqSpriteRenderer.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, opacity * LIGHT_STRENGTH);
        try {
            OsqSpriteRenderer.drawReflection(graphics, sprite, FIELD_WIDTH, FIELD_HEIGHT,
                    layoutX, layoutY, x, y, width, height);
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
}
