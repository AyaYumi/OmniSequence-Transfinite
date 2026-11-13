package com.atir.molecularmanipulator.client.gui.theme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared frames and a separate, layout-space light field, including live connected tab contours. */
final class OsqMaterialSurface {
    private static final int CARD_BORDER = 4, CARD_PAD = 3;
    private static final ResourceLocation REFLECTION = new ResourceLocation(
            OsqSprites.NS, "osq_light/common/glass_reflection");

    private OsqMaterialSurface() {}

    static void draw(GuiGraphics graphics, OsqLayout.Entry entry, ResourceLocation sprite, int width, int height) {
        if (entry.joinedTab != null) {
            joined(graphics, entry, sprite, width, height);
            return;
        }
        OsqSpriteRenderer.drawMaterial(graphics, sprite, width, height);
        int left = "window".equals(entry.material) ? 2 : entry.pad[0] + 1;
        int top = "window".equals(entry.material) ? 2 : entry.pad[1] + 1;
        int right = "window".equals(entry.material) ? 2 : entry.pad[2] + 1;
        int bottom = "window".equals(entry.material) ? 2 : entry.pad[3] + 1;
        var scaling = OsqSpriteRenderer.scaling(sprite);
        if ("nine_slice".equals(scaling.type())) {
            left = scaling.left(); top = scaling.top();
            right = scaling.right(); bottom = scaling.bottom();
        }
        reflect(graphics, entry, left, top, width - right, height - bottom);
    }

    private static void joined(GuiGraphics graphics, OsqLayout.Entry entry, ResourceLocation sprite,
            int width, int height) {
        var tab = entry.joinedTab;
        float hx = tab.x - CARD_PAD - entry.x, hy = tab.y - CARD_PAD - entry.y;
        int hw = Math.round(tab.w) + CARD_PAD * 2;
        float bodyY = tab.y + tab.h - CARD_PAD - entry.y;
        int bodyHeight = Math.round(height - bodyY);
        int headerHeight = Math.round(bodyY - hy) + CARD_BORDER * 2;
        // Draw only the exposed pieces. No bottom edge through the opening, no overlapping face fills,
        // and flush outer joins keep the same straight side rather than acquiring a second corner.
        OsqSpriteRenderer.drawMaterialRegion(graphics, sprite, 0, bodyY, width, bodyHeight,
                0, CARD_BORDER, width, bodyHeight);
        if (hx > 0) OsqSpriteRenderer.drawMaterialRegion(graphics, sprite, 0, bodyY, width, bodyHeight,
                0, 0, hx + CARD_BORDER, CARD_BORDER);
        if (hx + hw < width) OsqSpriteRenderer.drawMaterialRegion(graphics, sprite, 0, bodyY, width, bodyHeight,
                hx + hw - CARD_BORDER, 0, width, CARD_BORDER);
        OsqSpriteRenderer.drawMaterialRegion(graphics, sprite, hx, hy, hw, headerHeight,
                0, 0, hw, bodyY + CARD_BORDER - hy);
        reflect(graphics, entry, CARD_BORDER, bodyY + CARD_BORDER,
                width - CARD_BORDER, height - CARD_BORDER);
        reflect(graphics, entry, hx + CARD_BORDER, hy + CARD_BORDER,
                hx + hw - CARD_BORDER, bodyY + CARD_BORDER);
    }

    private static void reflect(GuiGraphics graphics, OsqLayout.Entry entry,
            float left, float top, float right, float bottom) {
        // Ordinary cards transmit the window's field; opaque features and popups sample that same field.
        if ("card".equals(entry.material)) return;
        int x = Math.round(left), y = Math.round(top);
        int width = Math.round(right) - x, height = Math.round(bottom) - y;
        if (width <= 0 || height <= 0) return;
        float originX = (entry.drawX == null ? entry.x : entry.drawX) - entry.pad[0];
        float originY = (entry.drawY == null ? entry.y : entry.drawY) - entry.pad[1];
        OsqReflectionLayer.draw(graphics, REFLECTION, x, y, width, height,
                Math.round(originX + left), Math.round(originY + top),
                "feature".equals(entry.material) ? .30f : 1);
    }
}
