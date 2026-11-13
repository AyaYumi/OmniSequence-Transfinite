package com.atir.molecularmanipulator.client.gui.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** One transparent material sampled in layout space, never stretched to a panel's aspect ratio. */
final class OsqReflectionLayer {
    private static final int TILE_WIDTH = 256, TILE_HEIGHT = 512;

    private OsqReflectionLayer() {}

    static void draw(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height,
            int layoutX, int layoutY, float opacity) {
        OsqSpriteRenderer.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        try {
            for (int dy = 0; dy < height;) {
                int v = Math.floorMod(layoutY + dy, TILE_HEIGHT);
                int h = Math.min(height - dy, TILE_HEIGHT - v);
                for (int dx = 0; dx < width;) {
                    int u = Math.floorMod(layoutX + dx, TILE_WIDTH);
                    int w = Math.min(width - dx, TILE_WIDTH - u);
                    OsqSpriteRenderer.drawReflection(graphics, sprite, TILE_WIDTH, TILE_HEIGHT,
                            u, v, x + dx, y + dy, w, h);
                    dx += w;
                }
                dy += h;
            }
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
}
