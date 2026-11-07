package com.atir.molecularmanipulator.client;

import appeng.api.stacks.AEFluidKey;
import appeng.client.gui.style.FluidBlitter;
import com.atir.molecularmanipulator.client.gui.theme.OsqPalette;
import net.minecraft.client.gui.GuiGraphics;

/** A real bottom-up tank surface; the scale stays above the fluid, not behind an item icon. */
final class OsqFluidGauge {
    private OsqFluidGauge() { }

    static int fillHeight(long amount, long capacity, int height) {
        if (amount <= 0 || capacity <= 0 || height <= 0) return 0;
        // Keep even a bucket in the very large buffer visible as one bottom pixel.
        return Math.max(1, (int) Math.ceil(Math.min(1, amount / (double) capacity) * height));
    }

    /** Three significant digits keep the readout readable inside a 34px gauge. */
    static String readout(long amount) {
        if (amount < 1000) return Long.toString(amount);
        return DisplayNumbers.compact(java.math.BigDecimal.valueOf(amount)
                .round(new java.math.MathContext(3, java.math.RoundingMode.HALF_UP)).doubleValue());
    }

    static void draw(GuiGraphics graphics, AEFluidKey fluid, long amount, long capacity,
            int x, int y, int width, int height, OsqPalette palette) {
        int innerWidth = width - 4, innerHeight = height - 4;
        int filled = fluid == null ? 0 : fillHeight(amount, capacity, innerHeight);
        int surface = y + height - 2 - filled;
        if (filled > 0) {
            graphics.flush();
            var texture = FluidBlitter.create(fluid);
            int sx = texture.getSrcX(), sy = texture.getSrcY();
            int sw = texture.getSrcWidth(), sh = texture.getSrcHeight();
            // Tile the actual animated/tinted fluid texture. Partial tiles are
            // cropped rather than stretching a 16px inventory icon into a tank.
            for (int dy = 0; dy < filled; dy += 16) for (int dx = 0; dx < innerWidth; dx += 16) {
                int w = Math.min(16, innerWidth - dx), h = Math.min(16, filled - dy);
                texture.copy().src(sx, sy, Math.round(sw * w / 16f), Math.round(sh * h / 16f))
                        .dest(x + 2 + dx, surface + dy, w, h).blit(graphics);
            }
        }
        for (int tick = 1; tick <= 4; tick++) {
            int yy = y + 2 + Math.round(innerHeight * tick / 5f);
            graphics.fill(x + width - 10, yy, x + width - 2, yy + 1,
                    yy >= surface ? palette.dark ? palette.ink : palette.invT : palette.ink3);
        }
    }
}
